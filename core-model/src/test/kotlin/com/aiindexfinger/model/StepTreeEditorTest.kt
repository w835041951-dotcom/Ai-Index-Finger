package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepTreeEditorTest {
    @Test
    fun `resolves unique top level and nested step paths`() {
        val steps = listOf(
            Step.Delay("top", 1),
            Step.Repeat(
                "repeat",
                1,
                listOf(
                    Step.IfElse(
                        "if",
                        Condition.Equals(Value.Literal("a"), Value.Literal("a")),
                        whenTrue = listOf(Step.Delay("nested", 1)),
                    ),
                ),
            ),
        )

        assertEquals(StepPath(StepListPath(), 0), steps.uniquePathTo("top"))
        assertEquals(
            StepPath(
                StepListPath()
                    .child("repeat", StepBranch.RepeatBody)
                    .child("if", StepBranch.IfTrue),
                0,
            ),
            steps.uniquePathTo("nested"),
        )
    }

    @Test
    fun `returns null for missing or duplicate step IDs`() {
        val steps = listOf(
            Step.Delay("same", 1),
            Step.Repeat("repeat", 1, listOf(Step.Delay("same", 1))),
        )

        assertNull(steps.uniquePathTo("missing"))
        assertNull(steps.uniquePathTo("same"))
    }
    private val root = StepListPath()
    private val falseBranch = root.child("condition", StepBranch.IfFalse)
    private val nestedRepeat = falseBranch.child("repeat", StepBranch.RepeatBody)

    @Test
    fun `inserts into empty true and false branches`() {
        val tree = listOf(
            Step.IfElse(
                id = "condition",
                condition = Condition.Equals(Value.Literal("a"), Value.Literal("a")),
                whenTrue = emptyList(),
                whenFalse = emptyList(),
            ),
        )

        val withTrue = tree.insertStep(
            root.child("condition", StepBranch.IfTrue),
            0,
            Step.Delay("true-delay", 10),
        )
        val withBoth = withTrue.insertStep(
            root.child("condition", StepBranch.IfFalse),
            0,
            Step.Delay("false-delay", 20),
        )

        assertEquals("true-delay", withBoth.stepsAt(root.child("condition", StepBranch.IfTrue)).single().id)
        assertEquals("false-delay", withBoth.stepsAt(falseBranch).single().id)
    }

    @Test
    fun `updates a leaf two levels deep without changing ids or sibling branch`() {
        val tree = nestedTree()
        val originalIds = tree.ids()
        val originalTrueBranch = tree.stepsAt(root.child("condition", StepBranch.IfTrue))

        val updated = tree.replaceStep(
            StepPath(nestedRepeat, 0),
            Step.Delay("nested-delay", 999),
        )

        assertEquals(999, (updated.stepAt(StepPath(nestedRepeat, 0)) as Step.Delay).durationMillis)
        assertEquals(originalIds, updated.ids())
        assertEquals(originalTrueBranch, updated.stepsAt(root.child("condition", StepBranch.IfTrue)))
    }

    @Test
    fun `duplicates nested subtree with new descendant ids`() {
        val tree = nestedTree()
        var nextId = 0

        val updated = tree.duplicateStep(StepPath(falseBranch, 0)) { "copy-${nextId++}" }
        val branch = updated.stepsAt(falseBranch)

        assertEquals(2, branch.size)
        assertTrue(branch[0].ids().intersect(branch[1].ids()).isEmpty())
        assertEquals(branch[0].shape(), branch[1].shape())
    }

    @Test
    fun `moves and removes only nested siblings`() {
        val tree = nestedTree().insertStep(
            nestedRepeat,
            1,
            Step.GlobalAction("home", SystemAction.Home),
        )

        val moved = tree.moveStep(StepPath(nestedRepeat, 1), 0)
        val removed = moved.removeStep(StepPath(nestedRepeat, 1))

        assertEquals(listOf("home", "nested-delay"), moved.stepsAt(nestedRepeat).map { it.id })
        assertEquals(listOf("home"), removed.stepsAt(nestedRepeat).map { it.id })
        assertEquals(listOf("true-action"), removed.stepsAt(root.child("condition", StepBranch.IfTrue)).map { it.id })
    }

    @Test
    fun `rejects missing containers incompatible branches and invalid indexes`() {
        val tree = nestedTree()

        assertFailsWith<IllegalArgumentException> {
            tree.stepsAt(root.child("missing", StepBranch.IfTrue))
        }
        assertFailsWith<IllegalArgumentException> {
            tree.stepsAt(root.child("condition", StepBranch.RepeatBody))
        }
        assertFailsWith<IllegalArgumentException> {
            tree.removeStep(StepPath(nestedRepeat, 5))
        }
        assertFailsWith<IllegalArgumentException> {
            tree.moveStep(StepPath(nestedRepeat, 0), 2)
        }
    }

    private fun nestedTree(): List<Step> = listOf(
        Step.IfElse(
            id = "condition",
            condition = Condition.Equals(Value.Literal("a"), Value.Literal("a")),
            whenTrue = listOf(Step.GlobalAction("true-action", SystemAction.Back)),
            whenFalse = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 2,
                    steps = listOf(Step.Delay("nested-delay", 100)),
                ),
            ),
        ),
    )

    private fun List<Step>.ids(): Set<String> = flatMap { it.ids() }.toSet()

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