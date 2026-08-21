package com.aiindexfinger.model

fun Step.withExecutionSettings(timeoutMillis: Long?, failurePolicy: FailurePolicy): Step {
    require(timeoutMillis == null || timeoutMillis > 0) { "Step timeout must be positive" }
    return when (this) {
        is Step.Click -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.RecordedClick -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.ImageClick -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.Delay -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.GlobalAction -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.IfElse -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.Label -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.JumpIf -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.InputText -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.LaunchApp -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.LongClick -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.ReadNodeText -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.Repeat -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.SetVariable -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.Scroll -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.ScrollUntil -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.Swipe -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.Tap -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
        is Step.WaitForNode -> copy(timeoutMillis = timeoutMillis, failurePolicy = failurePolicy)
    }
}