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

    @Test
    fun `rejects nested paths through duplicate container ids`() {
        val steps = listOf(
            Step.Repeat("duplicate", 1, listOf(Step.Delay("first", 1))),
            Step.Repeat("duplicate", 1, listOf(Step.Delay("second", 1))),
        )

        assertFailsWith<IllegalArgumentException> {
            steps.stepsAt(root.child("duplicate", StepBranch.RepeatBody))
        }
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
    fun `wraps a same-container range in repeat without replacing descendant ids`() {
        val tree = listOf(
            Step.Delay("before", 1),
            Step.Delay("first", 2),
            Step.GlobalAction("second", SystemAction.Back),
            Step.Delay("after", 3),
        )

        val wrapped = tree.wrapRangeInRepeat(root, 1, 2, "repeat", 3)

        assertEquals(listOf("before", "repeat", "after"), wrapped.map { it.id })
        assertEquals(
            listOf("first", "second"),
            (wrapped[1] as Step.Repeat).steps.map { it.id },
        )
        assertEquals(
            StepPath(root.child("repeat", StepBranch.RepeatBody), 1),
            wrapped.uniquePathTo("second"),
        )
    }

    @Test
    fun `unwraps repeat in place and preserves its child order`() {
        val tree = listOf(
            Step.Delay("before", 1),
            Step.Repeat(
                "repeat",
                2,
                listOf(Step.Delay("first", 2), Step.GlobalAction("second", SystemAction.Home)),
            ),
            Step.Delay("after", 3),
        )

        val unwrapped = tree.unwrapRepeat(StepPath(root, 1))

        assertEquals(listOf("before", "first", "second", "after"), unwrapped.map { it.id })
        assertEquals(StepPath(root, 2), unwrapped.uniquePathTo("second"))
    }

    @Test
    fun `moves a same-container range to its final destination index`() {
        val tree = listOf(
            Step.Delay("one", 1),
            Step.Delay("two", 2),
            Step.Delay("three", 3),
            Step.Delay("four", 4),
        )

        val moved = tree.moveStepRange(root, 1, 2, 0)

        assertEquals(listOf("two", "three", "one", "four"), moved.map { it.id })
    }

    @Test
    fun `moves a range forward to the end using index after removal`() {
        val tree = listOf(
            Step.Delay("one", 1),
            Step.Delay("two", 2),
            Step.Delay("three", 3),
            Step.Delay("four", 4),
        )

        val moved = tree.moveStepRange(root, 0, 1, destinationIndexAfterRemoval = 2)

        assertEquals(listOf("three", "four", "one", "two"), moved.map { it.id })
        assertEquals(tree.map { it.id }, tree.moveStepRange(root, 1, 2, destinationIndexAfterRemoval = 1).map { it.id })
    }

    @Test
    fun `renames a label and every same-scope jump target atomically`() {
        val tree = listOf(
            Step.Label("label", "retry"),
            Step.JumpIf("jump", "retry"),
            Step.Repeat("repeat", 1, listOf(Step.JumpIf("nested-jump", "retry"))),
            Step.IfElse(
                "if",
                Condition.Equals(Value.Literal("yes"), Value.Literal("yes")),
                whenTrue = listOf(Step.JumpIf("branch-jump", "retry")),
            ),
        )

        val renamed = tree.renameLabel(StepPath(root, 0), "again")

        assertEquals("again", (renamed[0] as Step.Label).name)
        assertEquals("again", (renamed[1] as Step.JumpIf).targetLabel)
        assertEquals("retry", ((renamed[2] as Step.Repeat).steps.single() as Step.JumpIf).targetLabel)
        assertEquals(
            "retry",
            ((renamed[3] as Step.IfElse).whenTrue.single() as Step.JumpIf).targetLabel,
        )
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
        assertFailsWith<IllegalArgumentException> {
            tree.wrapRangeInRepeat(root, 0, 1, "repeat", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            tree.wrapRangeInRepeat(root, 0, 0, "condition", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            tree.wrapRangeInRepeat(falseBranch, 0, 0, "condition", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            tree.wrapRangeInRepeat(falseBranch, 0, 0, "true-action", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            listOf(Step.Delay("only", 1)).unwrapRepeat(StepPath(root, 0))
        }
        assertFailsWith<IllegalArgumentException> {
            tree.moveStepRange(root, 0, 0, destinationIndexAfterRemoval = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            emptyList<Step>().moveStepRange(root, 0, 0, destinationIndexAfterRemoval = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            listOf(Step.Label("first", "one"), Step.Label("second", "two"))
                .renameLabel(StepPath(root, 0), "two")
        }
        assertFailsWith<IllegalArgumentException> {
            listOf(Step.Label("label", "one")).renameLabel(StepPath(root, 0), " ")
        }
        assertFailsWith<IllegalArgumentException> {
            listOf(Step.Delay("delay", 1)).renameLabel(StepPath(root, 0), "name")
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