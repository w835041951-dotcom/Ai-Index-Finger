package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedNodeSnapshotTest {
    @Test
    fun repeatedControlsAtDifferentPositionsRemainDistinct() {
        val first = node(bounds = "0 0 100 100")
        val second = node(bounds = "0 100 100 200")

        val result = sequenceOf(first, second).distinctObservedNodes().toList()

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun exactDuplicateControlIsRemoved() {
        val node = node(bounds = "0 0 100 100")

        val result = sequenceOf(node, node).distinctObservedNodes().toList()

        assertEquals(listOf(node), result)
    }

    @Test
    fun windowMergeSharesBudgetAcrossLargeAndSmallWindows() {
        val largeWindow = sequence {
            repeat(10) { index -> yield(node(bounds = "$index 0 ${index + 1} 1")) }
        }
        val dialogNode = node(bounds = "100 100 200 200")

        val result = mergeObservedNodeWindows(
            windows = listOf(largeWindow, sequenceOf(dialogNode)),
            limit = 3,
        )

        assertEquals(listOf(node(bounds = "0 0 1 1"), dialogNode, node(bounds = "1 0 2 1")), result)
    }

    @Test
    fun windowMergeKeepsIdenticalControlsFromDifferentWindows() {
        val repeated = node(bounds = "0 0 100 100")

        val result = mergeObservedNodeWindows(
            windows = listOf(sequenceOf(repeated), sequenceOf(repeated)),
            limit = 2,
        )

        assertEquals(listOf(repeated, repeated), result)
    }

    @Test
    fun windowMergeRemovesDuplicatesWithinOneWindow() {
        val duplicate = node(bounds = "0 0 100 100")
        val unique = node(bounds = "100 0 200 100")

        val result = mergeObservedNodeWindows(
            windows = listOf(sequenceOf(duplicate, duplicate, unique)),
            limit = 2,
        )

        assertEquals(listOf(duplicate, unique), result)
    }

    private fun node(bounds: String) = ObservedNode(
        packageName = "com.example.target",
        viewId = null,
        text = "Delete",
        contentDescription = null,
        className = "android.widget.Button",
        bounds = bounds,
        clickable = true,
        enabled = true,
    )
}