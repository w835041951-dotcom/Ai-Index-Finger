package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidator
import com.aiindexfinger.model.isReadyToRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWorkflowPacksTest {
    @Test
    fun systemAndSelfTestPacksInstallTwentyFiveRunnableWorkflowsInFourFolders() {
        var library = WorkflowLibrary()
        val packs = listOf(
            Triple(SettingsWorkflowPack.definition, "Settings", List(15) { "Settings $it" }),
            Triple(ClockWorkflowPack.definition, "Clock", List(5) { "Clock $it" }),
            Triple(FilesWorkflowPack.definition, "Files", List(3) { "Files $it" }),
            Triple(
                AiIndexFingerSelfTestPack.definition,
                "AI Index Finger",
                listOf("Verify workflow home", "Verify observation and runtime"),
            ),
        )
        packs.forEach { (pack, folderName, names) ->
            library = pack.install(library, folderName, names).library
        }

        assertEquals(25, library.workflows.size)
        assertEquals(25, library.workflows.map { it.id }.distinct().size)
        assertEquals(setOf("Settings", "Clock", "Files", "AI Index Finger"), library.folders.map { it.name }.toSet())
        assertTrue(library.workflows.all { it.state == WorkflowState.Ready })
        assertTrue(library.workflows.all { it.isReadyToRun() })
        assertTrue(library.workflows.all { WorkflowValidator.validate(it).isEmpty() })
        assertTrue(
            library.workflows
                .filterNot { it.id in AiIndexFingerSelfTestPack.definition.workflowIds }
                .flatMap { it.steps }
                .filterIsInstance<Step.WaitForNode>()
                .all { it.selector.viewId?.startsWith("${it.selector.packageName}:id/") == true },
        )
        val homeSelfTest = library.workflows.single {
            it.id == AiIndexFingerSelfTestPack.VERIFY_HOME_WORKFLOW_ID
        }
        assertEquals(1, homeSelfTest.steps.size)
        assertEquals(
            AiIndexFingerSelfTestPack.HOME_MARKER_TEXT,
            homeSelfTest.steps.filterIsInstance<Step.WaitForNode>().single().selector.text,
        )
        assertEquals(
            "com.aiindexfinger",
            homeSelfTest.steps.filterIsInstance<Step.WaitForNode>().single().selector.packageName,
        )
        val runtimeSelfTest = library.workflows.single {
            it.id == AiIndexFingerSelfTestPack.VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID
        }
        val selfTestSteps = listOf(homeSelfTest, runtimeSelfTest).flatMap { it.steps.flatten() }
        assertTrue(selfTestSteps.any { it is Step.ReadNodeText })
        assertTrue(selfTestSteps.any { it is Step.IfElse })
        assertTrue(selfTestSteps.any { it is Step.Repeat })
        assertTrue(selfTestSteps.none { it.isProhibitedSelfTestStep() })

        val validationSteps = library.workflows
            .filter { workflow ->
                workflow.id == ClockWorkflowPack.SET_VALIDATION_ALARM_TIME_WORKFLOW_ID ||
                    workflow.id == ClockWorkflowPack.SET_VALIDATION_ALARM_SOUND_WORKFLOW_ID
            }
            .flatMap { it.steps.flatten() }
        assertTrue(validationSteps.any { it is Step.LaunchApp })
        assertTrue(validationSteps.any { it is Step.WaitForNode })
        assertTrue(validationSteps.any { it is Step.Click })
        assertTrue(validationSteps.any { it is Step.InputText })
        assertTrue(validationSteps.any { it is Step.ReadNodeText })
        assertTrue(validationSteps.any { it is Step.IfElse })
        assertTrue(validationSteps.any { it is Step.GlobalAction })
        assertTrue(validationSteps.none { it is Step.Tap || it is Step.Swipe || it is Step.ImageClick })
        assertTrue(
            validationSteps.filterIsInstance<Step.LaunchApp>()
                .all { it.packageName == "com.google.android.deskclock" },
        )
    }

    @Test
    fun clockPackUpgradeAddsOnlyValidationWorkflowsAndPreservesExistingClockEdits() {
        val legacyDefinition = ClockWorkflowPack.definition.copy(
            templates = ClockWorkflowPack.definition.templates.take(3),
        )
        val legacy = legacyDefinition.install(
            WorkflowLibrary(),
            "Clock",
            listOf("Open Clock", "Verify time", "Verify date"),
        ).library
        val customized = legacy.copy(
            workflows = legacy.workflows.map { workflow ->
                if (workflow.id == ClockWorkflowPack.OPEN_WORKFLOW_ID) {
                    workflow.copy(name = "User Clock", state = WorkflowState.Draft)
                } else {
                    workflow
                }
            },
        )

        val upgraded = ClockWorkflowPack.definition.install(
            customized,
            "Clock",
            List(5) { "Clock $it" },
        )

        assertEquals(2, upgraded.addedWorkflowCount)
        assertEquals(5, upgraded.library.workflows.size)
        assertEquals(
            customized.workflows.single { it.id == ClockWorkflowPack.OPEN_WORKFLOW_ID },
            upgraded.library.workflows.single { it.id == ClockWorkflowPack.OPEN_WORKFLOW_ID },
        )
        assertEquals(ClockWorkflowPack.FOLDER_ID, upgraded.library.folders.single().id)
    }

    @Test
    fun allPacksAreIdempotentAndPreserveExistingWorkflows() {
        val packs = listOf(
            SettingsWorkflowPack.definition,
            ClockWorkflowPack.definition,
            FilesWorkflowPack.definition,
            AiIndexFingerSelfTestPack.definition,
        )
        var library = WorkflowLibrary()
        packs.forEach { pack ->
            library = pack.install(library, pack.id, List(pack.workflowIds.size) { "Name $it" }).library
        }
        val customized = library.copy(
            workflows = library.workflows.mapIndexed { index, workflow ->
                if (index == 0) workflow.copy(name = "User edit", state = WorkflowState.Draft) else workflow
            },
        )

        var reinstalled = customized
        var added = 0
        packs.forEach { pack ->
            val result = pack.install(reinstalled, pack.id, List(pack.workflowIds.size) { "Name $it" })
            reinstalled = result.library
            added += result.addedWorkflowCount
        }

        assertEquals(0, added)
        assertEquals(customized, reinstalled)
    }

    private fun List<Step>.flatten(): List<Step> = flatMap { step ->
        listOf(step) + when (step) {
            is Step.IfElse -> (step.whenTrue + step.whenFalse).flatten()
            is Step.Repeat -> step.steps.flatten()
            else -> emptyList()
        }
    }

    private fun Step.isProhibitedSelfTestStep(): Boolean = when (this) {
        is Step.LaunchApp,
        is Step.Click,
        is Step.ImageClick,
        is Step.LongClick,
        is Step.InputText,
        is Step.Swipe,
        is Step.Scroll,
        is Step.Tap,
        is Step.GlobalAction,
        -> true
        else -> false
    }

}