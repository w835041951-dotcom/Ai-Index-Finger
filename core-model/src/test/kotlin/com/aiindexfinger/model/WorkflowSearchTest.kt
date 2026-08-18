package com.aiindexfinger.model

import java.util.Locale
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

    @Test
    fun `matches chinese step keywords for search`() {
        val workflow = Workflow(
            id = "zh-search",
            name = "中文搜索",
            steps = listOf(
                Step.LaunchApp("launch", "com.example.music"),
                Step.Delay("wait", 500),
                Step.Scroll("scroll", selector, ScrollDirection.Forward),
            ),
        )

        assertTrue(workflow.matchesSearch("打开 应用"))
        assertTrue(workflow.matchesSearch("等待 延迟"))
        assertTrue(workflow.matchesSearch("滚动"))
    }

    @Test
    fun `matches full width query text after normalization`() {
        val workflow = Workflow(
            id = "width-normalization",
            name = "Checkout flow",
            steps = listOf(Step.Click("click", selector)),
        )

        assertTrue(workflow.matchesSearch("ｃｈｅｃｋｏｕｔ"))
        assertTrue(workflow.matchesSearch("ｃｏｍ．ｅｘａｍｐｌｅ．ｓｈｏｐ"))
    }

    @Test
    fun `matches search consistently under Turkish locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale("tr", "TR"))
        try {
            val workflow = Workflow(
                id = "locale-stability",
                name = "ID Search",
                steps = listOf(Step.Click("click", selector)),
            )

            assertTrue(workflow.matchesSearch("ID"))
            assertTrue(workflow.matchesSearch("COM.EXAMPLE.SHOP"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `indexes ancestor selector attributes`() {
        val withAncestor = selector.copy(
            ancestor = AncestorSelector(
                viewId = "com.example.shop:id/container",
                text = "Checkout Pane",
            ),
        )
        val workflow = Workflow(
            id = "ancestor-search",
            name = "Ancestor",
            steps = listOf(Step.Click("click", withAncestor)),
        )

        assertTrue(workflow.matchesSearch("container"))
        assertTrue(workflow.matchesSearch("checkout pane"))
    }

    @Test
    fun `indexes template variables inside condition equals`() {
        val workflow = Workflow(
            id = "condition-template",
            name = "Condition template",
            steps = listOf(
                Step.IfElse(
                    id = "if",
                    condition = Condition.Equals(
                        left = Value.Template("Hello ${'$'}{userName}"),
                        right = Value.Literal("ok"),
                    ),
                    whenTrue = listOf(Step.Delay("wait", 100)),
                ),
            ),
        )

        assertTrue(workflow.matchesSearch("userName"))
    }
}