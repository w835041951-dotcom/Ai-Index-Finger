package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepDuplicatorTest {
    @Test
    fun `duplicates nested structure with entirely new ids`() {
        val original = Step.IfElse(
            id = "condition",
            condition = Condition.Equals(Value.Literal("a"), Value.Literal("a")),
            whenTrue = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 2,
                    steps = listOf(Step.Delay("delay", 100)),
                ),
            ),
            whenFalse = listOf(Step.GlobalAction("back", SystemAction.Back)),
        )
        var nextId = 0

        val duplicate = original.duplicateWithNewIds { "new-${nextId++}" }

        assertEquals(original.shape(), duplicate.shape())
        assertTrue(original.ids().intersect(duplicate.ids()).isEmpty())
        assertEquals(4, duplicate.ids().size)
    }

    private fun Step.ids(): Set<String> = when (this) {
        is Step.IfElse -> setOf(id) + (whenTrue + whenFalse).flatMap { it.ids() }
        is Step.Repeat -> setOf(id) + steps.flatMap { it.ids() }
        else -> setOf(id)
    }

    private fun Step.shape(): String = when (this) {
        is Step.IfElse -> "if(${whenTrue.joinToString { it.shape() }})(${whenFalse.joinToString { it.shape() }})"
        is Step.Repeat -> "repeat:$times(${steps.joinToString { it.shape() }})"
        else -> this::class.simpleName.orEmpty()
    }
}