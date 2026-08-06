package com.aiindexfinger.executor

import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.RecordedBounds
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.RecordedControl
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.ValidationIssueCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkflowExecutorTest {
    @Test
    fun `step through starts paused and advances one action at a time`() = runTest {
        val driver = FakeDriver()
        val executor = WorkflowExecutor(driver)
        val workflow = Workflow(
            id = "debug",
            name = "Debug",
            steps = listOf(Step.Click("first", selector), Step.Click("second", selector)),
        )

        val execution = async { executor.runWithDiagnostics(workflow, stepThrough = true) }
        runCurrent()
        assertEquals(RunState.Paused("debug", "first"), executor.state.value)
        assertEquals(0, driver.clickCount)

        assertTrue(executor.advance())
        runCurrent()
        assertEquals(1, driver.clickCount)
        assertEquals(RunState.Paused("debug", "second"), executor.state.value)

        assertTrue(executor.advance())
        assertEquals(RunResult.Completed, execution.await().result)
        assertEquals(2, driver.clickCount)
    }

    @Test
    fun `advance outside a paused debug session is rejected`() {
        assertEquals(false, WorkflowExecutor(FakeDriver()).advance())
    }

    @Test
    fun `duplicate advance cannot preauthorize the next step`() = runTest {
        val driver = FakeDriver()
        val executor = WorkflowExecutor(driver)
        val workflow = Workflow(
            id = "debug",
            name = "Debug",
            steps = listOf(Step.Click("first", selector), Step.Click("second", selector)),
        )
        val execution = async { executor.runWithDiagnostics(workflow, stepThrough = true) }
        runCurrent()

        assertTrue(executor.advance())
        assertEquals(false, executor.advance())
        runCurrent()

        assertEquals(1, driver.clickCount)
        assertEquals(RunState.Paused("debug", "second"), executor.state.value)
        execution.cancelAndJoin()
    }

    @Test
    fun `cancelling while paused performs no driver action`() = runTest {
        val driver = FakeDriver()
        val executor = WorkflowExecutor(driver)
        val workflow = Workflow(
            id = "debug",
            name = "Debug",
            steps = listOf(Step.Click("click", selector)),
        )
        val execution = async { executor.runWithDiagnostics(workflow, stepThrough = true) }
        runCurrent()

        execution.cancelAndJoin()

        assertEquals(0, driver.clickCount)
        assertEquals(RunState.Idle, executor.state.value)
    }

    @Test
    fun `step through pauses through nested condition and repeat order`() = runTest {
        val driver = FakeDriver()
        val executor = WorkflowExecutor(driver)
        val workflow = Workflow(
            id = "nested-debug",
            name = "Nested debug",
            steps = listOf(
                Step.IfElse(
                    id = "condition",
                    condition = Condition.Equals(Value.Literal("yes"), Value.Literal("yes")),
                    whenTrue = listOf(
                        Step.Repeat(
                            id = "repeat",
                            times = 2,
                            steps = listOf(Step.Click("nested-click", selector)),
                        ),
                    ),
                ),
            ),
        )
        val execution = async { executor.runWithDiagnostics(workflow, stepThrough = true) }
        runCurrent()

        val pausedOrder = mutableListOf<String>()
        repeat(4) {
            pausedOrder += assertIs<RunState.Paused>(executor.state.value).stepId
            assertTrue(executor.advance())
            runCurrent()
        }

        assertEquals(listOf("condition", "repeat", "nested-click", "nested-click"), pausedOrder)
        assertEquals(RunResult.Completed, execution.await().result)
        assertEquals(2, driver.clickCount)
    }

    @Test
    fun `forwards a package scoped intent action`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "direct-settings",
            name = "Direct Settings",
            steps = listOf(
                Step.LaunchApp(
                    "launch",
                    "com.android.settings",
                    intentAction = "android.settings.LOCATION_SOURCE_SETTINGS",
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals("com.android.settings", driver.lastLaunchPackage)
        assertEquals("android.settings.LOCATION_SOURCE_SETTINGS", driver.lastIntentAction)
    }

    @Test
    fun `draft workflow is rejected before driver actions`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "draft",
            name = "Draft",
            steps = listOf(Step.Click("click", selector)),
            state = com.aiindexfinger.model.WorkflowState.Draft,
        )

        assertEquals(
            ValidationIssueCode.DraftWorkflow,
            assertIs<RunResult.NotReady>(WorkflowExecutor(driver).run(workflow)).issue.code,
        )
        assertEquals(0, driver.clickCount)
    }

    @Test
    fun `invalid ready workflow is rejected before driver actions`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "invalid-ready",
            name = "Invalid ready",
            steps = listOf(
                Step.Click("duplicate", selector),
                Step.Click("duplicate", selector),
            ),
            state = com.aiindexfinger.model.WorkflowState.Ready,
        )

        assertEquals(
            ValidationIssueCode.DuplicateStepId,
            assertIs<RunResult.NotReady>(WorkflowExecutor(driver).run(workflow)).issue.code,
        )
        assertEquals(0, driver.clickCount)
    }
    private val selector = NodeSelector(
        packageName = "com.example.target",
        viewId = "com.example.target:id/submit",
    )

    @Test
    fun `executes variables conditions and repeats`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "workflow-1",
            name = "Example",
            steps = listOf(
                Step.SetVariable("set", "mode", Value.Literal("ready")),
                Step.IfElse(
                    id = "condition",
                    condition = Condition.Equals(Value.Variable("mode"), Value.Literal("ready")),
                    whenTrue = listOf(
                        Step.Repeat("repeat", times = 2, steps = listOf(Step.Click("click", selector))),
                    ),
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(2, driver.clickCount)
    }

    @Test
    fun `diagnostics record nested steps in start order without user values`() = runTest {
        var nanos = 0L
        val workflow = Workflow(
            id = "diagnostics",
            name = "Diagnostics",
            steps = listOf(
                Step.SetVariable("set-secret", "secret", Value.Literal("private-value")),
                Step.Repeat("repeat", times = 2, steps = listOf(Step.Click("click", selector))),
            ),
        )

        val execution = WorkflowExecutor(FakeDriver(), nanoTime = { nanos.also { nanos += 2_000_000 } })
            .runWithDiagnostics(workflow)

        assertEquals(RunResult.Completed, execution.result)
        assertEquals(listOf("set-secret", "repeat", "click", "click"), execution.diagnostics
            .sortedBy { it.sequence }
            .map { it.stepId })
        assertTrue(execution.diagnostics.all { it.durationMillis >= 0 })
        assertTrue(execution.diagnostics.all { it.attemptCount == 1 })
        assertTrue(execution.diagnostics.none { it.toString().contains("private-value") })
    }

    @Test
    fun `inputs the latest variable value`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "variable-input",
            name = "Variable input",
            steps = listOf(
                Step.SetVariable("set", "message", Value.Literal("hello")),
                Step.InputText(
                    "input",
                    selector,
                    text = "",
                    variableName = "message",
                    inputMethod = TextInputMethod.Paste,
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals("hello", driver.lastInputText)
        assertEquals(TextInputMethod.Paste, driver.lastInputMethod)
    }

    @Test
    fun `renders a variable template for later input`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "template-input",
            name = "Template input",
            steps = listOf(
                Step.SetVariable("set-id", "orderId", Value.Literal("42")),
                Step.SetVariable(
                    "set-message",
                    "message",
                    Value.Template("Order-${'$'}{orderId}"),
                ),
                Step.InputText("input", selector, text = "", variableName = "message"),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals("Order-42", driver.lastInputText)
    }

    @Test
    fun `reads node text into a variable for later input`() = runTest {
        val driver = FakeDriver(nodeTextResult = "captured text")
        val workflow = Workflow(
            id = "read-node-text",
            name = "Read node text",
            steps = listOf(
                Step.ReadNodeText("read", selector, "captured", NodeAttribute.ViewId),
                Step.InputText("input", selector, text = "", variableName = "captured"),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals("captured text", driver.lastInputText)
        assertEquals(NodeAttribute.ViewId, driver.lastReadAttribute)
    }

    @Test
    fun `node condition executes false branch when target is absent`() = runTest {
        val driver = FakeDriver(nodeExistsResult = false)
        val workflow = Workflow(
            id = "node-condition",
            name = "Node condition",
            steps = listOf(
                Step.IfElse(
                    id = "if-node",
                    condition = Condition.NodeExists(selector),
                    whenTrue = listOf(Step.Click("click", selector)),
                    whenFalse = listOf(Step.GlobalAction("back", SystemAction.Back)),
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(0, driver.clickCount)
        assertEquals(1, driver.systemActionCount)
    }

    @Test
    fun `contains condition executes the true branch`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "contains-condition",
            name = "Contains condition",
            steps = listOf(
                Step.SetVariable("set", "status", Value.Literal("Order 123 ready")),
                Step.IfElse(
                    id = "if-contains",
                    condition = Condition.Equals(
                        Value.Variable("status"),
                        Value.Literal("123"),
                        ComparisonOperator.Contains,
                    ),
                    whenTrue = listOf(Step.Click("click", selector)),
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.clickCount)
    }

    @Test
    fun `template compared with variable executes the true branch`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "template-variable-condition",
            name = "Template variable condition",
            steps = listOf(
                Step.SetVariable("set-id", "orderId", Value.Literal("42")),
                Step.SetVariable("set-expected", "expected", Value.Literal("Order-42")),
                Step.IfElse(
                    id = "if-template-variable",
                    condition = Condition.Equals(
                        Value.Template("Order-${'$'}{orderId}"),
                        Value.Variable("expected"),
                    ),
                    whenTrue = listOf(Step.Click("click", selector)),
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.clickCount)
    }

    @Test
    fun `rejects a second concurrent run`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val executor = WorkflowExecutor(FakeDriver(clickGate = gate))
        val workflow = Workflow(
            id = "workflow-1",
            name = "Example",
            steps = listOf(Step.Click("click", selector)),
        )

        val first = async { executor.run(workflow) }
        testScheduler.runCurrent()

        assertEquals(RunResult.AlreadyRunning, executor.run(workflow))
        gate.complete(Unit)
        assertEquals(RunResult.Completed, first.await())
    }

    @Test
    fun `cancellation records result and restores idle state`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val executor = WorkflowExecutor(FakeDriver(clickGate = gate))
        val workflow = Workflow(
            id = "workflow-1",
            name = "Example",
            steps = listOf(Step.Click("click", selector)),
        )
        var result: RunResult? = null

        val running = launch { result = executor.run(workflow) }
        testScheduler.runCurrent()
        running.cancelAndJoin()

        assertEquals(RunResult.Cancelled, result)
        assertEquals(RunState.Idle, executor.state.value)
    }

    @Test
    fun `reports the failing step`() = runTest {
        val workflow = Workflow(
            id = "workflow-1",
            name = "Example",
            steps = listOf(Step.Click("click", selector)),
        )

        val result = WorkflowExecutor(FakeDriver(clickResult = false)).run(workflow)

        assertIs<RunResult.Failed>(result)
        assertEquals("click", result.stepId)
    }

    @Test
    fun `reports default timeout as a step failure`() = runTest {
        val workflow = Workflow(
            id = "timeout",
            name = "Timeout",
            defaultStepTimeoutMillis = 50,
            steps = listOf(Step.Delay("delay", durationMillis = 100)),
        )

        val result = WorkflowExecutor(FakeDriver()).run(workflow)

        assertIs<RunResult.Failed>(result)
        assertEquals("delay", result.stepId)
        assertEquals(ExecutionErrorCode.StepTimedOut, result.error.code)
    }

    @Test
    fun `step timeout overrides workflow default`() = runTest {
        val workflow = Workflow(
            id = "override",
            name = "Override",
            defaultStepTimeoutMillis = 50,
            steps = listOf(Step.Delay("delay", durationMillis = 100, timeoutMillis = 200)),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(FakeDriver()).run(workflow))
    }

    @Test
    fun `continue policy advances after timeout`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "continue-timeout",
            name = "Continue timeout",
            defaultStepTimeoutMillis = 50,
            steps = listOf(
                Step.Delay("delay", durationMillis = 100, failurePolicy = FailurePolicy.Continue),
                Step.GlobalAction("back", SystemAction.Back),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.systemActionCount)
    }

    @Test
    fun `retries a failed step and then succeeds`() = runTest {
        val driver = FakeDriver(failClicksBeforeSuccess = 2)
        val workflow = Workflow(
            id = "retry",
            name = "Retry",
            steps = listOf(
                Step.Click(
                    id = "click",
                    selector = selector,
                    failurePolicy = FailurePolicy.Retry(attempts = 2, delayMillis = 0),
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(3, driver.clickCount)
    }

    @Test
    fun `continues after an ignored failure`() = runTest {
        val driver = FakeDriver(clickResult = false)
        val workflow = Workflow(
            id = "continue",
            name = "Continue",
            steps = listOf(
                Step.Click("click", selector, failurePolicy = FailurePolicy.Continue),
                Step.GlobalAction("back", SystemAction.Back),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.systemActionCount)
    }

    @Test
    fun `container retry handles a nested step failure`() = runTest {
        val driver = FakeDriver(failClicksBeforeSuccess = 1)
        val workflow = Workflow(
            id = "nested-retry",
            name = "Nested retry",
            steps = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 1,
                    steps = listOf(Step.Click("click", selector)),
                    failurePolicy = FailurePolicy.Retry(attempts = 1, delayMillis = 0),
                ),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(2, driver.clickCount)
    }

    @Test
    fun `container continue handles a nested step failure`() = runTest {
        val driver = FakeDriver(clickResult = false)
        val workflow = Workflow(
            id = "nested-continue",
            name = "Nested continue",
            steps = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 1,
                    steps = listOf(Step.Click("click", selector)),
                    failurePolicy = FailurePolicy.Continue,
                ),
                Step.GlobalAction("back", SystemAction.Back),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.systemActionCount)
    }

    @Test
    fun `executes a long click`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "long-click",
            name = "Long click",
            steps = listOf(Step.LongClick("hold", selector)),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.longClickCount)
    }

    @Test
    fun `executes a coordinate tap`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "tap",
            name = "Tap",
            steps = listOf(Step.Tap("tap", x = 120, y = 340)),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(120 to 340, driver.lastTap)
    }

    @Test
    fun `recorded control click does not use saved coordinates`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "recorded-control",
            name = "Recorded control",
            steps = listOf(recordedClick(RecordedClickTargetMode.Control)),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.clickCount)
        assertEquals(null, driver.lastTap)
    }

    @Test
    fun `recorded coordinate click does not use saved selector`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "recorded-coordinates",
            name = "Recorded coordinates",
            steps = listOf(recordedClick(RecordedClickTargetMode.Coordinates)),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(0, driver.clickCount)
        assertEquals(120 to 340, driver.lastTap)
    }

    @Test
    fun `failed recorded control click does not fall back to coordinates`() = runTest {
        val driver = FakeDriver(clickResult = false)
        val workflow = Workflow(
            id = "recorded-control-failure",
            name = "Recorded control failure",
            steps = listOf(recordedClick(RecordedClickTargetMode.Control)),
        )

        assertIs<RunResult.Failed>(WorkflowExecutor(driver).run(workflow))
        assertEquals(null, driver.lastTap)
    }

    @Test
    fun `scrolls a selected node backward`() = runTest {
        val driver = FakeDriver()
        val workflow = Workflow(
            id = "scroll",
            name = "Scroll",
            steps = listOf(Step.Scroll("scroll", selector, ScrollDirection.Backward)),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(ScrollDirection.Backward, driver.lastScrollDirection)
    }

    @Test
    fun `continues after an ignored scroll failure`() = runTest {
        val driver = FakeDriver(scrollResult = false)
        val workflow = Workflow(
            id = "continue-scroll",
            name = "Continue scroll",
            steps = listOf(
                Step.Scroll(
                    "scroll",
                    selector,
                    ScrollDirection.Forward,
                    failurePolicy = FailurePolicy.Continue,
                ),
                Step.GlobalAction("back", SystemAction.Back),
            ),
        )

        assertEquals(RunResult.Completed, WorkflowExecutor(driver).run(workflow))
        assertEquals(1, driver.systemActionCount)
    }

    @Test
    fun `wait for disappearance completes when node is absent`() = runTest {
        val workflow = Workflow(
            id = "disappear",
            name = "Wait for disappearance",
            steps = listOf(Step.WaitForNode("wait", selector, mustExist = false)),
        )

        assertEquals(
            RunResult.Completed,
            WorkflowExecutor(FakeDriver(nodeExistsResult = false)).run(workflow),
        )
    }

    @Test
    fun `stops when actual step execution budget is exceeded`() = runTest {
        val workflow = Workflow(
            id = "budget",
            name = "Budget",
            steps = listOf(
                Step.Repeat(
                    id = "repeat",
                    times = 5,
                    steps = listOf(Step.Delay("delay", 0)),
                ),
            ),
        )

        val result = WorkflowExecutor(FakeDriver(), maxExecutedSteps = 3).run(workflow)

        assertIs<RunResult.Failed>(result)
        assertEquals(ExecutionErrorCode.ExecutionLimitExceeded, result.error.code)
        assertEquals("3", result.error.arguments["limit"])
    }

    @Test
    fun `image click completes only when driver reports a click`() = runTest {
        val workflow = imageClickWorkflow()

        assertEquals(
            RunResult.Completed,
            WorkflowExecutor(FakeDriver(imageClickResult = ImageClickResult.Clicked(975))).run(workflow),
        )
    }

    @Test
    fun `image click no match and ambiguity fail without coordinate fallback`() = runTest {
        listOf(
            ImageClickResult.NoMatch to ExecutionErrorCode.ImageTemplateNotFound,
            ImageClickResult.Ambiguous to ExecutionErrorCode.ImageTemplateAmbiguous,
        ).forEach { (driverResult, expectedCode) ->
            val driver = FakeDriver(imageClickResult = driverResult)

            val result = WorkflowExecutor(driver).run(imageClickWorkflow())

            assertIs<RunResult.Failed>(result)
            assertEquals(expectedCode, result.error.code)
            assertEquals(null, driver.lastTap)
        }
    }

    private fun imageClickWorkflow() = Workflow(
        id = "image-click",
        name = "Image click",
        steps = listOf(
            Step.ImageClick(
                id = "image",
                packageName = "com.example.target",
                templatePngBase64 = "a".repeat(16),
                templateWidth = 12,
                templateHeight = 12,
            ),
        ),
    )

    private fun recordedClick(targetMode: RecordedClickTargetMode) = Step.RecordedClick(
        id = "recorded",
        x = 120,
        y = 340,
        selector = selector,
        control = RecordedControl(
            packageName = "com.example.target",
            viewId = "com.example.target:id/submit",
            className = "android.widget.Button",
            bounds = RecordedBounds(100, 300, 140, 380),
            clickable = true,
            enabled = true,
            longClickable = false,
            scrollable = false,
        ),
        targetMode = targetMode,
    )

    private class FakeDriver(
        private val clickResult: Boolean = true,
        private val clickGate: CompletableDeferred<Unit>? = null,
        private val failClicksBeforeSuccess: Int = 0,
        private val nodeExistsResult: Boolean = true,
        private val nodeTextResult: String? = "node text",
        private val imageClickResult: ImageClickResult = ImageClickResult.Clicked(1_000),
        private val scrollResult: Boolean = true,
    ) : AutomationDriver {
        var clickCount = 0
        var longClickCount = 0
        var lastTap: Pair<Int, Int>? = null
        var lastScrollDirection: ScrollDirection? = null
        var lastInputText: String? = null
        var lastInputMethod: TextInputMethod? = null
        var lastReadAttribute: NodeAttribute? = null
        var systemActionCount = 0
        var lastLaunchPackage: String? = null
        var lastIntentAction: String? = null

        override suspend fun launchApp(packageName: String, intentAction: String?): Boolean {
            lastLaunchPackage = packageName
            lastIntentAction = intentAction
            return true
        }

        override suspend fun inputText(
            selector: NodeSelector,
            text: String,
            method: TextInputMethod,
        ): Boolean {
            lastInputText = text
            lastInputMethod = method
            return true
        }

        override suspend fun readNodeAttribute(
            selector: NodeSelector,
            attribute: NodeAttribute,
        ): String? {
            lastReadAttribute = attribute
            return nodeTextResult
        }

        override suspend fun longClick(selector: NodeSelector): Boolean {
            longClickCount++
            return true
        }

        override suspend fun tap(x: Int, y: Int): Boolean {
            lastTap = x to y
            return true
        }

        override suspend fun scroll(selector: NodeSelector, direction: ScrollDirection): Boolean {
            lastScrollDirection = direction
            return scrollResult
        }

        override suspend fun swipe(
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            durationMillis: Long,
        ) = true

        override suspend fun performSystemAction(action: SystemAction): Boolean {
            systemActionCount++
            return true
        }

        override suspend fun click(selector: NodeSelector): Boolean {
            clickGate?.await()
            clickCount++
            return clickResult && clickCount > failClicksBeforeSuccess
        }

        override suspend fun clickImage(step: Step.ImageClick): ImageClickResult = imageClickResult

        override suspend fun nodeExists(selector: NodeSelector) = nodeExistsResult
    }
}