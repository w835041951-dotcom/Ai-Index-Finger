package com.aiindexfinger.model

enum class SelectorRole {
    Click,
    RecordedClick,
    LongClick,
    InputText,
    ReadNodeText,
    Scroll,
    WaitForNode,
    NodeCondition,
}

data class SelectorUse(
    val stepId: String,
    val role: SelectorRole,
    val selector: NodeSelector,
)

data class LaunchTarget(
    val packageName: String,
    val intentAction: String? = null,
)

fun Workflow.selectorUses(): List<SelectorUse> = buildList {
    collectSelectorUses(steps)
}

fun Workflow.targetPackages(): Set<String> = buildSet {
    addAll(selectorUses().map { it.selector.packageName })
    addAll(launchPackages())
}

fun Workflow.launchPackages(): Set<String> = launchTargets().mapTo(linkedSetOf(), LaunchTarget::packageName)

fun Workflow.launchTargets(): Set<LaunchTarget> = buildSet {
    collectLaunchTargets(steps)
}

private fun MutableList<SelectorUse>.collectSelectorUses(steps: List<Step>) {
    steps.forEach { step ->
        when (step) {
            is Step.Click -> add(SelectorUse(step.id, SelectorRole.Click, step.selector))
            is Step.RecordedClick -> step.selector?.let {
                add(SelectorUse(step.id, SelectorRole.RecordedClick, it))
            }
            is Step.ImageClick -> Unit
            is Step.LongClick -> add(SelectorUse(step.id, SelectorRole.LongClick, step.selector))
            is Step.InputText -> add(SelectorUse(step.id, SelectorRole.InputText, step.selector))
            is Step.ReadNodeText -> add(SelectorUse(step.id, SelectorRole.ReadNodeText, step.selector))
            is Step.Scroll -> add(SelectorUse(step.id, SelectorRole.Scroll, step.selector))
            is Step.WaitForNode -> add(SelectorUse(step.id, SelectorRole.WaitForNode, step.selector))
            is Step.IfElse -> {
                (step.condition as? Condition.NodeExists)?.let { condition ->
                    add(SelectorUse(step.id, SelectorRole.NodeCondition, condition.selector))
                }
                collectSelectorUses(step.whenTrue)
                collectSelectorUses(step.whenFalse)
            }
            is Step.Repeat -> collectSelectorUses(step.steps)
            else -> Unit
        }
    }
}

private fun MutableSet<LaunchTarget>.collectLaunchTargets(steps: List<Step>) {
    steps.forEach { step ->
        when (step) {
            is Step.LaunchApp -> add(LaunchTarget(step.packageName, step.intentAction))
            is Step.ImageClick -> add(LaunchTarget(step.packageName))
            is Step.IfElse -> {
                collectLaunchTargets(step.whenTrue)
                collectLaunchTargets(step.whenFalse)
            }
            is Step.Repeat -> collectLaunchTargets(step.steps)
            else -> Unit
        }
    }
}
