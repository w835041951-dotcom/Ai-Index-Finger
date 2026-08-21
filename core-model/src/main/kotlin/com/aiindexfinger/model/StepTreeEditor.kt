package com.aiindexfinger.model

enum class StepBranch {
    RepeatBody,
    IfTrue,
    IfFalse,
}

data class StepListSegment(
    val containerId: String,
    val branch: StepBranch,
)

data class StepListPath(
    val segments: List<StepListSegment> = emptyList(),
) {
    fun child(containerId: String, branch: StepBranch): StepListPath =
        copy(segments = segments + StepListSegment(containerId, branch))
}

data class StepPath(
    val parent: StepListPath,
    val index: Int,
)

fun List<Step>.uniquePathTo(stepId: String): StepPath? {
    val matches = mutableListOf<StepPath>()
    collectPathsTo(stepId, StepListPath(), matches)
    return matches.singleOrNull()
}

private fun List<Step>.collectPathsTo(
    stepId: String,
    parent: StepListPath,
    matches: MutableList<StepPath>,
) {
    forEachIndexed { index, step ->
        if (step.id == stepId) matches += StepPath(parent, index)
        when (step) {
            is Step.Repeat -> step.steps.collectPathsTo(
                stepId,
                parent.child(step.id, StepBranch.RepeatBody),
                matches,
            )
            is Step.IfElse -> {
                step.whenTrue.collectPathsTo(
                    stepId,
                    parent.child(step.id, StepBranch.IfTrue),
                    matches,
                )
                step.whenFalse.collectPathsTo(
                    stepId,
                    parent.child(step.id, StepBranch.IfFalse),
                    matches,
                )
            }
            else -> Unit
        }
    }
}

fun List<Step>.stepsAt(path: StepListPath): List<Step> =
    if (path.segments.isEmpty()) this else updateAndRead(path).second

fun List<Step>.stepAt(path: StepPath): Step =
    stepsAt(path.parent).getOrNull(path.index)
        ?: throw IllegalArgumentException("Step index ${path.index} is outside the target list")

fun List<Step>.insertStep(path: StepListPath, index: Int, step: Step): List<Step> =
    updateStepsAt(path) { target ->
        require(index in 0..target.size) { "Insert index $index is outside the target list" }
        target.toMutableList().apply { add(index, step) }
    }

fun List<Step>.replaceStep(path: StepPath, step: Step): List<Step> =
    updateStepsAt(path.parent) { target ->
        require(path.index in target.indices) { "Step index ${path.index} is outside the target list" }
        target.toMutableList().apply { this[path.index] = step }
    }

fun List<Step>.removeStep(path: StepPath): List<Step> =
    updateStepsAt(path.parent) { target ->
        require(path.index in target.indices) { "Step index ${path.index} is outside the target list" }
        target.toMutableList().apply { removeAt(path.index) }
    }

fun List<Step>.moveStep(path: StepPath, destinationIndex: Int): List<Step> =
    updateStepsAt(path.parent) { target ->
        require(path.index in target.indices) { "Step index ${path.index} is outside the target list" }
        require(destinationIndex in target.indices) {
            "Destination index $destinationIndex is outside the target list"
        }
        target.toMutableList().apply {
            add(destinationIndex, removeAt(path.index))
        }
    }

fun List<Step>.moveStepRange(
    path: StepListPath,
    startIndex: Int,
    endIndex: Int,
    destinationIndexAfterRemoval: Int,
): List<Step> = updateStepsAt(path) { target ->
    val range = target.checkedRange(startIndex, endIndex)
    val remaining = target.filterIndexed { index, _ -> index !in startIndex..endIndex }
    require(destinationIndexAfterRemoval in 0..remaining.size) {
        "Range destination index $destinationIndexAfterRemoval is outside the target list"
    }
    remaining.toMutableList().apply { addAll(destinationIndexAfterRemoval, range) }
}

fun List<Step>.wrapRangeInRepeat(
    path: StepListPath,
    startIndex: Int,
    endIndex: Int,
    repeatId: String,
    times: Int,
): List<Step> {
    require(repeatId.isNotBlank() && !containsStepId(repeatId)) {
        "Repeat ID must be non-blank and unique"
    }
    return updateStepsAt(path) { target ->
        val range = target.checkedRange(startIndex, endIndex)
        val repeat = Step.Repeat(repeatId, times, range)
        target.take(startIndex) + repeat + target.drop(endIndex + 1)
    }
}

fun List<Step>.unwrapRepeat(path: StepPath): List<Step> = updateStepsAt(path.parent) { target ->
    val repeat = target.getOrNull(path.index) as? Step.Repeat
        ?: throw IllegalArgumentException("Step at index ${path.index} is not a repeat")
    target.take(path.index) + repeat.steps + target.drop(path.index + 1)
}

fun List<Step>.renameLabel(path: StepPath, name: String): List<Step> {
    val label = stepAt(path) as? Step.Label
        ?: throw IllegalArgumentException("Step at index ${path.index} is not a label")
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "Label name must not be blank" }
    return updateStepsAt(path.parent) { target ->
        require(
            target.withIndex().none { (index, step) ->
                index != path.index && step is Step.Label && step.name == trimmedName
            },
        ) { "Label '$trimmedName' already exists in this list" }
        target.mapIndexed { index, step ->
            when {
                index == path.index -> label.copy(name = trimmedName)
                step is Step.JumpIf && step.targetLabel == label.name -> step.copy(targetLabel = trimmedName)
                else -> step
            }
        }
    }
}

fun List<Step>.duplicateStep(path: StepPath, newId: () -> String): List<Step> =
    updateStepsAt(path.parent) { target ->
        val source = target.getOrNull(path.index)
            ?: throw IllegalArgumentException("Step index ${path.index} is outside the target list")
        target.toMutableList().apply {
            add(path.index + 1, source.duplicateWithNewIds(newId))
        }
    }

private fun List<Step>.checkedRange(startIndex: Int, endIndex: Int): List<Step> {
    require(startIndex in indices) { "Range start index $startIndex is outside the target list" }
    require(endIndex in startIndex until size) { "Range end index $endIndex is outside the target list" }
    return subList(startIndex, endIndex + 1)
}

private fun List<Step>.containsStepId(stepId: String): Boolean = any { step ->
    step.id == stepId || when (step) {
        is Step.Repeat -> step.steps.containsStepId(stepId)
        is Step.IfElse -> step.whenTrue.containsStepId(stepId) || step.whenFalse.containsStepId(stepId)
        else -> false
    }
}

private fun List<Step>.updateStepsAt(
    path: StepListPath,
    transform: (List<Step>) -> List<Step>,
): List<Step> {
    if (path.segments.isEmpty()) return transform(this)
    val segment = path.segments.first()
    val containerIndexes = indices.filter { index -> this[index].id == segment.containerId }
    require(containerIndexes.isNotEmpty()) { "Container '${segment.containerId}' was not found" }
    require(containerIndexes.size == 1) { "Container '${segment.containerId}' is ambiguous" }
    val containerIndex = containerIndexes.single()
    val remainingPath = StepListPath(path.segments.drop(1))
    val container = this[containerIndex]
    val updatedContainer = container.updateBranch(segment.branch, remainingPath, transform)
    return toMutableList().apply { this[containerIndex] = updatedContainer }
}

private fun Step.updateBranch(
    branch: StepBranch,
    remainingPath: StepListPath,
    transform: (List<Step>) -> List<Step>,
): Step = when {
    this is Step.Repeat && branch == StepBranch.RepeatBody ->
        copy(steps = steps.updateStepsAt(remainingPath, transform))
    this is Step.IfElse && branch == StepBranch.IfTrue ->
        copy(whenTrue = whenTrue.updateStepsAt(remainingPath, transform))
    this is Step.IfElse && branch == StepBranch.IfFalse ->
        copy(whenFalse = whenFalse.updateStepsAt(remainingPath, transform))
    else -> throw IllegalArgumentException("Step '$id' does not contain branch $branch")
}

private fun List<Step>.updateAndRead(path: StepListPath): Pair<List<Step>, List<Step>> {
    var resolved: List<Step>? = null
    val unchanged = updateStepsAt(path) { target ->
        resolved = target
        target
    }
    return unchanged to requireNotNull(resolved)
}
