package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowSearchTest {
    private val selector = NodeSelector(
        packageName = "com.example.shop",
        viewId = "com.example.shop:id/checkout",
        text = "Checkout",
    )

    @Test
    fun `matches name package selector and nested action`() {
        val workflow = Workflow(
            id = "shopping",
            name = "Morning purchase",
            steps = listOf(
                Step.Repeat("repeat", 2, listOf(Step.LongClick("hold", selector))),
            ),
        )

        assertTrue(workflow.matchesSearch("morning"))
        assertTrue(workflow.matchesSearch("example.shop checkout"))
        assertTrue(workflow.matchesSearch("long click"))
        assertFalse(workflow.matchesSearch("banking"))
    }

    @Test
    fun `does not index entered text or literal values`() {
        val workflow = Workflow(
            id = "private",
            name = "Private workflow",
            steps = listOf(
                Step.InputText("input", selector, "secret-value"),
                Step.SetVariable("set", "token", Value.Literal("hidden-literal")),
            ),
        )

        assertFalse(workflow.matchesSearch("secret-value"))
        assertFalse(workflow.matchesSearch("hidden-literal"))
        assertTrue(workflow.matchesSearch("token"))
    }
}