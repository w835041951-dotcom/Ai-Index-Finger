package com.aiindexfinger.model

enum class WorkflowStarterTemplate {
    PauseThenHome,
    RepeatWithPause,
    VariableDecision,
}

object WorkflowStarterTemplates {
    fun create(
        template: WorkflowStarterTemplate,
        name: String = template.defaultName,
        newId: () -> String,
    ): Workflow = when (template) {
        WorkflowStarterTemplate.PauseThenHome -> Workflow(
            id = newId(),
            name = name,
            state = WorkflowState.Draft,
            steps = listOf(
                Step.Delay(newId(), 5_000),
                Step.GlobalAction(newId(), SystemAction.Home),
            ),
        )
        WorkflowStarterTemplate.RepeatWithPause -> Workflow(
            id = newId(),
            name = name,
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
            name = name,
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

private val WorkflowStarterTemplate.defaultName: String
    get() = when (this) {
        WorkflowStarterTemplate.PauseThenHome -> "Pause, then go Home"
        WorkflowStarterTemplate.RepeatWithPause -> "Repeated pause"
        WorkflowStarterTemplate.VariableDecision -> "Variable decision"
    }
