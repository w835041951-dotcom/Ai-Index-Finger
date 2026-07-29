package com.aiindexfinger.executor

import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkflowExecutorTest {
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
            RunResult.NotReady("Workflow is saved as a draft"),
            WorkflowExecutor(driver).run(workflow),
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
            RunResult.NotReady("Step ID is duplicated"),
            WorkflowExecutor(driver).run(workflow),
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
        assertEquals("Step timed out", result.message)
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
        assertEquals("Workflow exceeded 3 step executions", result.message)
    }

    private class FakeDriver(
        private val clickResult: Boolean = true,
        private val clickGate: CompletableDeferred<Unit>? = null,
        private val failClicksBeforeSuccess: Int = 0,
        private val nodeExistsResult: Boolean = true,
        private val nodeTextResult: String? = "node text",
    ) : AutomationDriver {
        var clickCount = 0
        var longClickCount = 0
        var lastTap: Pair<Int, Int>? = null
        var lastScrollDirection: ScrollDirection? = null
        var lastInputText: String? = null
        var lastInputMethod: TextInputMethod? = null
        var lastReadAttribute: NodeAttribute? = null
        var systemActionCount = 0

        override suspend fun launchApp(packageName: String) = true

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
            return true
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

        override suspend fun nodeExists(selector: NodeSelector) = nodeExistsResult
    }
}