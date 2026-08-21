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

    @Test
    fun `duplicates image click with a new id and unchanged template`() {
        val original = Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24)

        val duplicate = original.duplicateWithNewIds { "copy" }

        assertEquals(original.copy(id = "copy"), duplicate)
    }

    @Test
    fun `duplicates labels and jumps with new ids and unchanged control flow configuration`() {
        val label = Step.Label("label", "target")
        val jump = Step.JumpIf("jump", "target", Condition.Equals(Value.Literal("yes"), Value.Literal("yes")))

        assertEquals(label.copy(id = "label-copy"), label.duplicateWithNewIds { "label-copy" })
        assertEquals(jump.copy(id = "jump-copy"), jump.duplicateWithNewIds { "jump-copy" })
    }

    @Test
    fun `duplicates scroll until with a new id and unchanged stop configuration`() {
        val selector = NodeSelector("com.example", text = "List")
        val original = Step.ScrollUntil(
            "scroll-until",
            selector,
            ScrollDirection.Forward,
            ScrollUntilStopCondition.NodeDisappears(selector),
            maxScrolls = 8,
        )

        assertEquals(original.copy(id = "copy"), original.duplicateWithNewIds { "copy" })
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