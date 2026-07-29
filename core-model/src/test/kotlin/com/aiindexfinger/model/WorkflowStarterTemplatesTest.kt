package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowStarterTemplatesTest {
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
    }

    private fun Workflow.ids(): Set<String> = setOf(id) + steps.flatMap { it.ids() }

    private fun Step.ids(): Set<String> = when (this) {
        is Step.IfElse -> setOf(id) + (whenTrue + whenFalse).flatMap { it.ids() }
        is Step.Repeat -> setOf(id) + steps.flatMap { it.ids() }
        else -> setOf(id)
    }
}