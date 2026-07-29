package com.aiindexfinger.model

enum class SelectorRole {
    Click,
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

fun Workflow.selectorUses(): List<SelectorUse> = buildList {
    collectSelectorUses(steps)
}

fun Workflow.targetPackages(): Set<String> = buildSet {
    addAll(selectorUses().map { it.selector.packageName })
    addAll(launchPackages())
}

fun Workflow.launchPackages(): Set<String> = buildSet {
    collectLaunchPackages(steps)
}

private fun MutableList<SelectorUse>.collectSelectorUses(steps: List<Step>) {
    steps.forEach { step ->
        when (step) {
            is Step.Click -> add(SelectorUse(step.id, SelectorRole.Click, step.selector))
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

private fun MutableSet<String>.collectLaunchPackages(steps: List<Step>) {
    steps.forEach { step ->
        when (step) {
            is Step.LaunchApp -> add(step.packageName)
            is Step.IfElse -> {
                collectLaunchPackages(step.whenTrue)
                collectLaunchPackages(step.whenFalse)
            }
            is Step.Repeat -> collectLaunchPackages(step.steps)
            else -> Unit
        }
    }
}
