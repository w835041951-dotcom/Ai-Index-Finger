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

    private fun diagnosticRow(index: Int): String = context.getString(
        R.string.execution_diagnostic_row,
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