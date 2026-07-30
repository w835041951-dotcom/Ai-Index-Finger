package com.aiindexfinger.model

fun Step.duplicateWithNewIds(newId: () -> String): Step = when (this) {
    is Step.Click -> copy(id = newId())
    is Step.ImageClick -> copy(id = newId())
    is Step.Delay -> copy(id = newId())
    is Step.GlobalAction -> copy(id = newId())
    is Step.IfElse -> copy(
        id = newId(),
        whenTrue = whenTrue.map { it.duplicateWithNewIds(newId) },
        whenFalse = whenFalse.map { it.duplicateWithNewIds(newId) },
    )
    is Step.InputText -> copy(id = newId())
    is Step.LaunchApp -> copy(id = newId())
    is Step.LongClick -> copy(id = newId())
    is Step.ReadNodeText -> copy(id = newId())
    is Step.Repeat -> copy(
        id = newId(),
        steps = steps.map { it.duplicateWithNewIds(newId) },
    )
    is Step.SetVariable -> copy(id = newId())
    is Step.Scroll -> copy(id = newId())
    is Step.Swipe -> copy(id = newId())
    is Step.Tap -> copy(id = newId())
    is Step.WaitForNode -> copy(id = newId())
}