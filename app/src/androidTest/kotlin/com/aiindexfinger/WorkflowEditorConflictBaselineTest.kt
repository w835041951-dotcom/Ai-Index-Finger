package com.aiindexfinger

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.StepListPath
import com.aiindexfinger.model.StepPath
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowEditorConflictBaselineTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun canonicalUpdateDoesNotReplaceOpenEditorsConflictBaseline() {
        val baseline = Workflow(id = "workflow", name = "Baseline", steps = emptyList())
        val newer = baseline.copy(name = "Saved elsewhere")
        var persistedBaseline by mutableStateOf<Workflow?>(baseline)
        var submittedExpected: Workflow? = null

        composeRule.setContent {
            MaterialTheme {
                WorkflowEditor(
                    workflow = baseline,
                    persistedBaseline = persistedBaseline,
                    onTest = {},
                    onBack = {},
                    onSave = { expected, _ -> submittedExpected = expected },
                )
            }
        }

        composeRule.runOnIdle { persistedBaseline = newer }
        composeRule.onNodeWithText("Save draft").performClick()

        composeRule.runOnIdle { assertEquals(baseline, submittedExpected) }
    }

    @Test
    fun arbitraryComparisonConditionRoundTripsWithoutChangingItsStep() {
        val condition = Condition.Equals(
            left = Value.Template("Order-${'$'}{orderId}"),
            right = Value.Variable("expected"),
            operator = ComparisonOperator.NotEquals,
        )
        val comparison = Step.IfElse(
            id = "comparison",
            condition = condition,
            whenTrue = listOf(Step.Delay("true-delay", 100)),
            whenFalse = listOf(Step.Delay("false-delay", 200)),
            timeoutMillis = 3_000,
            failurePolicy = FailurePolicy.Retry(attempts = 3, delayMillis = 250),
        )
        val workflow = Workflow(
            id = "workflow",
            name = "Comparison",
            steps = listOf(comparison),
            state = WorkflowState.Draft,
        )
        var submitted: Workflow? = null

        composeRule.setContent {
            MaterialTheme {
                WorkflowEditor(
                    workflow = workflow,
                    onTest = {},
                    onBack = {},
                    onSave = { _, candidate -> submitted = candidate },
                )
            }
        }

        composeRule.onNodeWithTag(stepOperationTag("comparison", "edit"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Condition settings").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Save draft").performClick()

        composeRule.runOnIdle { assertEquals(workflow, submitted) }
    }

    @Test
    fun inputTextRoundTripsCompleteSelectorAndInputSettings() {
        assertStepRoundTrips(
            Step.InputText(
                id = "input",
                selector = completeSelector(),
                text = "retained fallback",
                variableName = " message ",
                inputMethod = TextInputMethod.Paste,
                timeoutMillis = 2_500,
                failurePolicy = FailurePolicy.Continue,
            ),
        )
    }

    @Test
    fun readNodeTextRoundTripsCompleteSelectorAndReadSettings() {
        assertStepRoundTrips(
            Step.ReadNodeText(
                id = "read",
                selector = completeSelector(),
                variableName = " captured ",
                attribute = NodeAttribute.ContentDescription,
                timeoutMillis = 2_500,
                failurePolicy = FailurePolicy.Continue,
            ),
        )
    }

    @Test
    fun scrollRoundTripsCompleteSelectorAndDirection() {
        assertStepRoundTrips(
            Step.Scroll(
                id = "scroll",
                selector = completeSelector(),
                direction = ScrollDirection.Backward,
                timeoutMillis = 2_500,
                failurePolicy = FailurePolicy.Continue,
            ),
        )
    }

    @Test
    fun waitForNodeRoundTripsCompleteSelectorAndWaitSettings() {
        assertStepRoundTrips(
            Step.WaitForNode(
                id = "wait",
                selector = completeSelector(),
                mustExist = false,
                timeoutMillis = 2_500,
                failurePolicy = FailurePolicy.Continue,
            ),
        )
    }

    @Test
    fun nodeConditionRoundTripsCompleteSelectorAndBranches() {
        assertStepRoundTrips(
            Step.IfElse(
                id = "condition",
                condition = Condition.NodeExists(completeSelector()),
                whenTrue = listOf(Step.Delay("true-delay", 100)),
                whenFalse = listOf(Step.Delay("false-delay", 200)),
                timeoutMillis = 2_500,
                failurePolicy = FailurePolicy.Continue,
            ),
        )
    }

    @Test
    fun manualSelectorEntryPointsStayEnabledWithoutObservedNodes() {
        val workflow = Workflow(
            id = "manual-selectors",
            name = "Manual selectors",
            steps = listOf(Step.Delay("delay", 100)),
            state = WorkflowState.Draft,
        )

        composeRule.setContent {
            MaterialTheme {
                WorkflowEditor(
                    workflow = workflow,
                    onTest = {},
                    onBack = {},
                    onSave = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Read element attribute")
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithText("Element exists condition")
            .performScrollTo()
            .assertIsEnabled()
    }

    private fun assertStepRoundTrips(step: Step) {
        val workflow = Workflow(
            id = "workflow-${step.id}",
            name = "Selector round trip",
            steps = listOf(step),
            state = WorkflowState.Draft,
        )
        var submitted: Workflow? = null

        composeRule.setContent {
            MaterialTheme {
                WorkflowEditor(
                    workflow = workflow,
                    initialEditingStepPath = StepPath(StepListPath(), 0),
                    onTest = {},
                    onBack = {},
                    onSave = { _, candidate -> submitted = candidate },
                )
            }
        }

        composeRule.onNodeWithText("Save").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Save draft").performClick()

        composeRule.runOnIdle { assertEquals(workflow, submitted) }
    }

    private fun completeSelector() = NodeSelector(
        packageName = "com.example",
        viewId = "com.example:id/title",
        text = "Order",
        textMatchMode = TextMatchMode.Contains,
        contentDescription = "Order status",
        contentDescriptionMatchMode = TextMatchMode.Contains,
        className = "android.widget.TextView",
        matchIndex = 7,
        ancestor = AncestorSelector(
            viewId = "com.example:id/card",
            text = "Account",
            textMatchMode = TextMatchMode.Contains,
            contentDescription = "Primary account",
            contentDescriptionMatchMode = TextMatchMode.Contains,
            className = "android.view.ViewGroup",
        ),
    )
}
