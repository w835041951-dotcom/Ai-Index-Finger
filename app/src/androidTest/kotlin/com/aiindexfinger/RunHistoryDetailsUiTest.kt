package com.aiindexfinger

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.data.RunHistoryDestination
import com.aiindexfinger.data.RunImageClickDiagnostic
import com.aiindexfinger.data.RunImageClickSelectionMode
import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.data.RunStepDiagnostic
import com.aiindexfinger.data.RunStepOutcome
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunHistoryDetailsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun longDiagnosticsScrollToFinalEntryWhileActionsStayAvailable() {
        val workflow = Workflow(
            id = "history-workflow",
            name = "History workflow",
            steps = listOf(Step.Delay("delay", 100)),
        )
        val diagnostics = List(DIAGNOSTIC_COUNT) { index ->
            RunStepDiagnostic(
                sequence = index.toLong(),
                stepId = diagnosticStepId(index),
                durationMillis = index.toLong(),
                attemptCount = 1,
                outcome = RunStepOutcome.Completed,
            )
        }
        val record = RunRecord(
            id = "long-history",
            workflowId = workflow.id,
            workflowName = workflow.name,
            startedAtMillis = 1,
            durationMillis = 1_000,
            status = RunStatus.Completed,
            diagnostics = diagnostics,
        )
        var openedWorkflows = 0
        var dismissals = 0

        composeRule.setContent {
            MaterialTheme {
                RunRecordDetailsDialog(
                    record = record,
                    destination = RunHistoryDestination(workflow, null),
                    onDismiss = { dismissals += 1 },
                    onOpenWorkflow = { _, _ -> openedWorkflows += 1 },
                )
            }
        }

        val firstRow = diagnosticRow(0)
        val finalRow = diagnosticRow(DIAGNOSTIC_COUNT - 1)
        composeRule.onNodeWithText(firstRow).assertIsDisplayed()
        composeRule.onNodeWithText(finalRow).assertIsNotDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.close)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.run_history_open_workflow)).assertIsDisplayed()

        composeRule.onNodeWithText(finalRow).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.close)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.run_history_open_workflow))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, openedWorkflows)
            assertEquals(0, dismissals)
        }
    }

    @Test
    fun failedRecordCanRetryCurrentWorkflowVersion() {
        val workflow = Workflow(
            id = "retry-workflow",
            name = "Retry workflow",
            steps = listOf(Step.Delay("delay", 100)),
        )
        val record = RunRecord(
            id = "failed-history",
            workflowId = workflow.id,
            workflowName = workflow.name,
            startedAtMillis = 1,
            durationMillis = 1_000,
            status = RunStatus.Failed,
            failedStepId = "delay",
        )
        var retriedWorkflow: Workflow? = null

        composeRule.setContent {
            MaterialTheme {
                RunRecordDetailsDialog(
                    record = record,
                    destination = RunHistoryDestination(workflow, null),
                    onDismiss = {},
                    onOpenWorkflow = { _, _ -> },
                    onRetry = { retriedWorkflow = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.run_history_retry_current_version))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(workflow, retriedWorkflow)
        }
    }

    @Test
    fun imageClickDiagnosticShowsUnknownModeAndPartialExecutionDetails() {
        val diagnostic = RunStepDiagnostic(
            sequence = 0,
            stepId = "image",
            durationMillis = 100,
            attemptCount = 1,
            outcome = RunStepOutcome.ContinuedAfterFailure,
            imageClick = RunImageClickDiagnostic(
                selectionMode = RunImageClickSelectionMode.Unknown,
                candidateCount = 100,
                candidatesTruncated = true,
                bestScorePermille = null,
                bestScalePermille = null,
                plannedClickCount = 20,
                completedClickCount = 4,
                failedClickIndex = 5,
                retrySuppressed = true,
            ),
        )
        val record = RunRecord(
            id = "image-history",
            workflowId = "workflow",
            workflowName = "Image history",
            startedAtMillis = 1,
            durationMillis = 100,
            status = RunStatus.CompletedWithWarnings,
            diagnostics = listOf(diagnostic),
        )
        val candidateCount = context.getString(
            R.string.image_click_diagnostic_candidates_truncated,
            100,
        )
        val summary = context.getString(
            R.string.image_click_diagnostic_summary_no_best,
            context.getString(R.string.image_click_selection_unknown),
            candidateCount,
            4,
            20,
        )

        composeRule.setContent {
            MaterialTheme {
                RunRecordDetailsDialog(
                    record = record,
                    destination = null,
                    onDismiss = {},
                    onOpenWorkflow = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(summary).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.image_click_diagnostic_failed_click, 5),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.image_click_diagnostic_retry_suppressed),
        ).assertIsDisplayed()
    }

    private fun diagnosticRow(index: Int): String = context.resources.getQuantityString(
        R.plurals.execution_diagnostic_row,
        1,
        diagnosticStepId(index),
        context.getString(R.string.execution_outcome_completed),
        index.toLong(),
        1,
    )

    private fun diagnosticStepId(index: Int) = "diagnostic-${index.toString().padStart(2, '0')}"

    private companion object {
        const val DIAGNOSTIC_COUNT = 60
    }
}