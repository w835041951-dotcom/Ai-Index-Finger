package com.aiindexfinger.model

enum class WorkflowStarterTemplate(
    val title: String,
    val description: String,
) {
    PauseThenHome(
        title = "Pause, then Home",
        description = "Wait five seconds, then return to the device Home screen.",
    ),
    RepeatWithPause(
        title = "Repeat with a pause",
        description = "Run a one-second pause three times and explore nested editing.",
    ),
    VariableDecision(
        title = "Variable and decision",
        description = "Set a variable and use true and false conditional branches.",
    ),
}

object WorkflowStarterTemplates {
    fun create(template: WorkflowStarterTemplate, newId: () -> String): Workflow = when (template) {
        WorkflowStarterTemplate.PauseThenHome -> Workflow(
            id = newId(),
            name = template.title,
            state = WorkflowState.Draft,
            steps = listOf(
                Step.Delay(newId(), 5_000),
                Step.GlobalAction(newId(), SystemAction.Home),
            ),
        )
        WorkflowStarterTemplate.RepeatWithPause -> Workflow(
            id = newId(),
            name = template.title,
            state = WorkflowState.Draft,
            steps = listOf(
                Step.Repeat(
                    id = newId(),
                    times = 3,
                    steps = listOf(Step.Delay(newId(), 1_000)),
                ),
            ),
        )
        WorkflowStarterTemplate.VariableDecision -> Workflow(
            id = newId(),
            name = template.title,
            state = WorkflowState.Draft,
            steps = listOf(
                Step.SetVariable(newId(), "mode", Value.Literal("demo")),
                Step.IfElse(
                    id = newId(),
                    condition = Condition.Equals(
                        Value.Variable("mode"),
                        Value.Literal("demo"),
                    ),
                    whenTrue = listOf(Step.Delay(newId(), 500)),
                    whenFalse = listOf(Step.Delay(newId(), 1_000)),
                ),
            ),
        )
    }
}
