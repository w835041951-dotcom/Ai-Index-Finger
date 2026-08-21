package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkflowStarterTemplatesTest {
    @Test
    fun `catalog has exactly ten categories with ten distinct examples each`() {
        val catalog = WorkflowStarterTemplates.catalog

        assertEquals(100, catalog.size)
        assertEquals(10, WorkflowExampleCategory.entries.size)
        assertEquals(
            WorkflowExampleCategory.entries.associateWith { 10 },
            catalog.groupingBy(WorkflowExample::category).eachCount(),
        )
        assertEquals(catalog.size, catalog.map(WorkflowExample::id).toSet().size)
        assertEquals(catalog.size, catalog.map(WorkflowExample::learningPurposeId).toSet().size)
    }

    @Test
    fun `catalog metadata is stable deterministic and bilingual`() {
        val firstRead = WorkflowStarterTemplates.catalog.map { example -> example.metadataSignature() }
        val secondRead = WorkflowStarterTemplates.catalog.map { example -> example.metadataSignature() }

        assertEquals(firstRead, secondRead)
        assertEquals(
            WorkflowExampleCategory.entries.flatMap { category -> List(10) { category } },
            WorkflowStarterTemplates.catalog.map(WorkflowExample::category),
        )
        WorkflowStarterTemplates.catalog.forEach { example ->
            assertTrue(example.id.matches(Regex("[a-z0-9_-]+")), example.id)
            assertTrue(example.learningPurposeId.matches(Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)+")), example.learningPurposeId)
            assertFalse(example.learningPurposeId.matches(Regex(".*(?:lesson|example|示例)[_-]?[0-9]+.*")), example.learningPurposeId)
            assertTrue(example.titleResourceKey.matches(Regex("[a-z][a-z0-9_]*")), example.titleResourceKey)
            assertTrue(example.descriptionResourceKey.matches(Regex("[a-z][a-z0-9_]*")), example.descriptionResourceKey)
            assertTrue(example.titleResourceKey.endsWith("_title"), example.titleResourceKey)
            assertTrue(example.descriptionResourceKey.endsWith("_description"), example.descriptionResourceKey)
            assertTrue(example.searchKeywordsEnglish.size >= 2)
            assertTrue(example.searchKeywordsSimplifiedChinese.isNotEmpty())
            assertTrue(example.searchKeywordsEnglish.all { it.isNotBlank() })
            assertTrue(example.searchKeywordsSimplifiedChinese.all { it.isNotBlank() })
            assertEquals(
                example.searchKeywordsEnglish.map(String::lowercase),
                example.searchKeywordsEnglish,
            )
            assertFalse(example.compatibility.autoRunAllowed)
            assertTrue(example.compatibility.requiredCapabilities.isNotEmpty())
            assertTrue(example.compatibility.minimumSchemaVersion <= Workflow.CURRENT_SCHEMA_VERSION)
        }
    }

    @Test
    fun `every catalog example creates a nonempty validator clean explicit draft`() {
        WorkflowStarterTemplates.catalog.forEach { example ->
            var nextId = 0

            val workflow = example.create { "${example.id}-${nextId++}" }

            assertEquals(WorkflowState.Draft, workflow.state, example.id)
            assertTrue(workflow.steps.isNotEmpty(), example.id)
            assertTrue(WorkflowValidator.validate(workflow).isEmpty(), example.id)
            assertEquals(workflow.ids().size, nextId, example.id)
            workflow.steps.flatten().filterIsInstance<Step.Repeat>().forEach { repeat ->
                assertTrue(repeat.times in 1..10, example.id)
                assertTrue(repeat.steps.isNotEmpty(), example.id)
            }
        }
    }

    @Test
    fun `examples in each category remain distinct after numeric parameters are erased`() {
        WorkflowStarterTemplates.catalog.groupBy(WorkflowExample::category).forEach { (category, examples) ->
            val concepts = examples.map { example ->
                var nextId = 0
                example.create { "ignored-${nextId++}" }.conceptSignature()
            }
            assertEquals(concepts.size, concepts.toSet().size, category.name)
        }

        WorkflowStarterTemplates.catalog.forEach { example ->
            var firstId = 0
            var secondId = 0
            val first = example.create { "first-${firstId++}" }
            val second = example.create { "second-${secondId++}" }

            assertTrue(first.ids().intersect(second.ids()).isEmpty(), example.id)
            assertNotEquals(first.id, second.id, example.id)
            assertEquals(first.structuralSignature(), second.structuralSignature(), example.id)
        }
    }

    @Test
    fun `app specific examples require configuration and use obvious placeholders`() {
        WorkflowStarterTemplates.catalog.forEach { example ->
            var nextId = 0
            val workflow = example.create { "id-${nextId++}" }
            val selectors = workflow.steps.flatten().mapNotNull { it.selectorOrNull() }

            if (selectors.isEmpty()) {
                assertFalse(example.compatibility.requiresConfiguration, example.id)
            } else {
                assertTrue(example.compatibility.requiresConfiguration, example.id)
                assertTrue(selectors.all { it.packageName == WorkflowStarterTemplates.PLACEHOLDER_PACKAGE })
                assertTrue(selectors.all { it.viewId?.startsWith(WorkflowStarterTemplates.PLACEHOLDER_VIEW_ID) == true })
            }
        }
    }

    @Test
    fun `catalog excludes sensitive literals and consequential actions`() {
        val prohibited = listOf(
            "credential", "password", "passcode", "payment", "purchase", "delete",
            "send", "submit", "凭据", "密码", "支付", "购买", "删除", "发送", "提交",
        )

        WorkflowStarterTemplates.catalog.forEach { example ->
            var nextId = 0
            val workflow = example.create { "id-${nextId++}" }
            val searchable = (example.metadataSignature() + workflow.structuralSignature()).lowercase()

            prohibited.forEach { literal ->
                assertFalse(literal in searchable, "${example.id} contains prohibited literal '$literal'")
            }
            assertTrue(workflow.steps.flatten().none { it is Step.InputText }, example.id)
            assertTrue(workflow.steps.flatten().none { it is Step.Click || it is Step.LongClick }, example.id)
        }
    }

    @Test
    fun `catalog filtering supports localized text keywords categories and no results`() {
        val fundamentals = WorkflowStarterTemplates.catalog.first { example ->
            example.category == WorkflowExampleCategory.Fundamentals
        }
        val navigation = WorkflowStarterTemplates.catalog.first { example ->
            example.category == WorkflowExampleCategory.Navigation
        }
        val searchable = listOf(
            SearchableWorkflowExample(fundamentals, "Single pause", "Learn delay basics", "Fundamentals"),
            SearchableWorkflowExample(navigation, "Go home", "Open the Home screen", "Navigation"),
        )

        assertEquals(listOf(fundamentals), filterWorkflowExamples(searchable, "delay", null).map { it.example })
        assertEquals(listOf(navigation), filterWorkflowExamples(searchable, "主页", null).map { it.example })
        assertEquals(
            listOf(navigation),
            filterWorkflowExamples(searchable, "", WorkflowExampleCategory.Navigation).map { it.example },
        )
        assertTrue(filterWorkflowExamples(searchable, "does not exist", null).isEmpty())
    }

    @Test
    fun `every template creates a clean explicit draft`() {
        WorkflowStarterTemplate.entries.forEach { template ->
            var nextId = 0

            val workflow = WorkflowStarterTemplates.create(template) { "id-${nextId++}" }

            assertEquals(WorkflowState.Draft, workflow.state)
            assertTrue(WorkflowValidator.validate(workflow).isEmpty(), template.name)
            assertEquals(workflow.ids().size, nextId)
        }
    }

    @Test
    fun `creating the same template twice shares no ids`() {
        var firstId = 0
        var secondId = 0
        val first = WorkflowStarterTemplates.create(WorkflowStarterTemplate.VariableDecision) {
            "first-${firstId++}"
        }
        val second = WorkflowStarterTemplates.create(WorkflowStarterTemplate.VariableDecision) {
            "second-${secondId++}"
        }

        assertTrue(first.ids().intersect(second.ids()).isEmpty())
    }

    @Test
    fun `templates expose expected teaching structures`() {
        var nextId = 0
        val repeat = WorkflowStarterTemplates.create(WorkflowStarterTemplate.RepeatWithPause) {
            "repeat-${nextId++}"
        }
        val decision = WorkflowStarterTemplates.create(WorkflowStarterTemplate.VariableDecision) {
            "decision-${nextId++}"
        }

        assertTrue(repeat.steps.single() is Step.Repeat)
        assertTrue(decision.steps[0] is Step.SetVariable)
        val condition = decision.steps[1] as Step.IfElse
        assertTrue(condition.whenTrue.isNotEmpty())
        assertTrue(condition.whenFalse.isNotEmpty())
        assertEquals(500, (condition.whenTrue.single() as Step.Delay).durationMillis)
        assertEquals(1_000, (condition.whenFalse.single() as Step.Delay).durationMillis)
    }

    private fun Workflow.ids(): Set<String> = setOf(id) + steps.flatMap { it.ids() }

    private fun Step.ids(): Set<String> = when (this) {
        is Step.IfElse -> setOf(id) + (whenTrue + whenFalse).flatMap { it.ids() }
        is Step.Repeat -> setOf(id) + steps.flatMap { it.ids() }
        else -> setOf(id)
    }

    private fun WorkflowExample.metadataSignature(): String = listOf(
        id,
        category.name,
        learningPurposeId,
        titleResourceKey,
        descriptionResourceKey,
        searchKeywordsEnglish.joinToString("|"),
        searchKeywordsSimplifiedChinese.joinToString("|"),
        compatibility.minimumSchemaVersion.toString(),
        compatibility.requiredCapabilities.sortedBy(Enum<*>::name).joinToString("|"),
        compatibility.requiresConfiguration.toString(),
        compatibility.autoRunAllowed.toString(),
    ).joinToString(";")

    private fun Workflow.structuralSignature(): String = steps.joinToString(";") { it.structuralSignature() }

    private fun Step.structuralSignature(): String = when (this) {
        is Step.LaunchApp -> "launch:$packageName"
        is Step.Click -> "click:${selector.signature()}"
        is Step.RecordedClick -> "recorded_click:$x:$y:${selector?.signature()}:$control:$targetMode"
        is Step.ImageClick -> "image:$packageName:$templateWidth:$templateHeight:$minimumScorePermille:$ambiguityMarginPermille"
        is Step.LongClick -> "long_click:${selector.signature()}"
        is Step.InputText -> "input:${selector.signature()}:$text:$variableName:$inputMethod"
        is Step.ReadNodeText -> "read:${selector.signature()}:$variableName:$attribute"
        is Step.Swipe -> "swipe:$startX:$startY:$endX:$endY:$durationMillis"
        is Step.Scroll -> "scroll:${selector.signature()}:$direction"
        is Step.ScrollUntil -> "scroll_until:${selector.signature()}:$direction:$stopCondition:$maxScrolls"
        is Step.Tap -> "tap:$x:$y"
        is Step.GlobalAction -> "global:$action"
        is Step.WaitForNode -> "wait:${selector.signature()}:$mustExist"
        is Step.Delay -> "delay:$durationMillis:${failurePolicy.signature()}"
        is Step.SetVariable -> "set:$name:$value"
        is Step.IfElse -> "if:$condition:${whenTrue.signature()}:${whenFalse.signature()}"
        is Step.Label -> "label:$name"
        is Step.JumpIf -> "jump:$targetLabel:$condition"
        is Step.Repeat -> "repeat:$times:${steps.signature()}"
    }

    private fun List<Step>.signature(): String = joinToString(",", prefix = "[", postfix = "]") {
        it.structuralSignature()
    }

    private fun FailurePolicy.signature(): String = when (this) {
        FailurePolicy.Stop -> "stop"
        FailurePolicy.Continue -> "continue"
        is FailurePolicy.Retry -> "retry:$attempts:$delayMillis"
    }

    private fun NodeSelector.signature(): String =
        "$packageName:$viewId:$text:$textMatchMode:$contentDescription:$contentDescriptionMatchMode:$className:$matchIndex"

    private fun Step.selectorOrNull(): NodeSelector? = when (this) {
        is Step.Click -> selector
        is Step.RecordedClick -> selector
        is Step.LongClick -> selector
        is Step.InputText -> selector
        is Step.ReadNodeText -> selector
        is Step.Scroll -> selector
        is Step.WaitForNode -> selector
        is Step.IfElse -> (condition as? Condition.NodeExists)?.selector
        else -> null
    }

    private fun Workflow.conceptSignature(): String = steps.joinToString(";") { it.conceptSignature() }

    private fun Step.conceptSignature(): String = when (this) {
        is Step.LaunchApp -> "launch"
        is Step.Click -> "click"
        is Step.RecordedClick -> "recorded_click:$targetMode"
        is Step.ImageClick -> "image"
        is Step.LongClick -> "long_click"
        is Step.InputText -> "input:$inputMethod"
        is Step.ReadNodeText -> "read:$attribute"
        is Step.Swipe -> "swipe"
        is Step.Scroll -> "scroll:$direction"
        is Step.ScrollUntil -> "scroll_until:$direction:${stopCondition::class.simpleName}"
        is Step.Tap -> "tap"
        is Step.GlobalAction -> "global:$action"
        is Step.WaitForNode -> "wait:$mustExist"
        is Step.Delay -> "delay:${failurePolicy.conceptSignature()}"
        is Step.SetVariable -> "set:${value::class.simpleName}"
        is Step.IfElse -> "if:${condition.conceptSignature()}:${whenTrue.conceptSignature()}:${whenFalse.conceptSignature()}"
        is Step.Label -> "label"
        is Step.JumpIf -> "jump:${condition?.conceptSignature().orEmpty()}"
        is Step.Repeat -> "repeat:${steps.conceptSignature()}"
    }

    private fun List<Step>.conceptSignature(): String = joinToString(",", prefix = "[", postfix = "]") {
        it.conceptSignature()
    }

    private fun FailurePolicy.conceptSignature(): String = when (this) {
        FailurePolicy.Stop -> "stop"
        FailurePolicy.Continue -> "continue"
        is FailurePolicy.Retry -> "retry"
    }

    private fun Condition.conceptSignature(): String = when (this) {
        is Condition.Equals -> "compare:$operator"
        is Condition.NodeExists -> "node_exists"
    }

    private fun List<Step>.flatten(): List<Step> = flatMap { step ->
        listOf(step) + when (step) {
            is Step.IfElse -> (step.whenTrue + step.whenFalse).flatten()
            is Step.Repeat -> step.steps.flatten()
            else -> emptyList()
        }
    }
}