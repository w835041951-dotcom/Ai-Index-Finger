package com.aiindexfinger.executor

import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowLimits
import com.aiindexfinger.model.evaluate
import com.aiindexfinger.model.renderTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

interface AutomationDriver {
    suspend fun launchApp(packageName: String): Boolean
    suspend fun click(selector: NodeSelector): Boolean
    suspend fun longClick(selector: NodeSelector): Boolean
    suspend fun tap(x: Int, y: Int): Boolean
    suspend fun scroll(selector: NodeSelector, direction: ScrollDirection): Boolean
    suspend fun inputText(selector: NodeSelector, text: String, method: TextInputMethod): Boolean
    suspend fun readNodeAttribute(selector: NodeSelector, attribute: NodeAttribute): String?
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMillis: Long): Boolean
    suspend fun performSystemAction(action: SystemAction): Boolean
    suspend fun nodeExists(selector: NodeSelector): Boolean
}

sealed interface RunState {
    data object Idle : RunState
    data class Running(val workflowId: String, val stepId: String?) : RunState
}

sealed interface RunResult {
    data object Completed : RunResult
    data object AlreadyRunning : RunResult
    data object Cancelled : RunResult
    data class Failed(val stepId: String, val message: String) : RunResult
}

class WorkflowExecutor(
    private val driver: AutomationDriver,
    private val maxExecutedSteps: Long = WorkflowLimits.MAX_EXECUTED_STEPS,
) {
    private val runMutex = Mutex()
    private val mutableState = MutableStateFlow<RunState>(RunState.Idle)

    val state: StateFlow<RunState> = mutableState.asStateFlow()

    suspend fun run(workflow: Workflow): RunResult {
        if (!runMutex.tryLock()) return RunResult.AlreadyRunning

        return try {
            val context = ExecutionContext(workflow)
            executeSteps(workflow.steps, context)
            RunResult.Completed
        } catch (_: CancellationException) {
            RunResult.Cancelled
        } catch (failure: StepFailure) {
            RunResult.Failed(failure.stepId, failure.message ?: "Step failed")
        } finally {
            mutableState.value = RunState.Idle
            runMutex.unlock()
        }
    }

    private suspend fun executeSteps(steps: List<Step>, context: ExecutionContext) {
        for (step in steps) {
            mutableState.value = RunState.Running(context.workflow.id, step.id)
            executeWithPolicy(step, context)
        }
    }

    private suspend fun executeWithPolicy(step: Step, context: ExecutionContext) {
        val retry = step.failurePolicy as? FailurePolicy.Retry
        val attempts = retry?.attempts?.plus(1) ?: 1

        repeat(attempts) { attempt ->
            context.executedSteps++
            if (context.executedSteps > maxExecutedSteps) {
                throw StepFailure(step.id, "Workflow exceeded $maxExecutedSteps step executions")
            }
            try {
                val timeoutMillis = step.timeoutMillis ?: context.workflow.defaultStepTimeoutMillis
                withTimeout(timeoutMillis) { executeStep(step, context) }
                return
            } catch (error: TimeoutCancellationException) {
                if (attempt + 1 < attempts) {
                    delay(retry?.delayMillis ?: 0)
                } else if (step.failurePolicy is FailurePolicy.Continue) {
                    return
                } else {
                    throw StepFailure(step.id, "Step timed out", error)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (attempt + 1 < attempts) {
                    delay(retry?.delayMillis ?: 0)
                } else if (step.failurePolicy is FailurePolicy.Continue) {
                    return
                } else {
                    throw StepFailure(step.id, error.message ?: "Step failed", error)
                }
            }
        }
    }

    private suspend fun executeStep(step: Step, context: ExecutionContext) {
        when (step) {
            is Step.Click -> check(driver.click(step.selector)) { "Target node was not clickable" }
            is Step.Delay -> delay(step.durationMillis)
            is Step.GlobalAction -> check(driver.performSystemAction(step.action)) { "System action failed" }
            is Step.IfElse -> {
                val branch = if (evaluate(step.condition, context)) step.whenTrue else step.whenFalse
                executeSteps(branch, context)
            }
            is Step.Repeat -> repeat(step.times) { executeSteps(step.steps, context) }
            is Step.Scroll -> check(driver.scroll(step.selector, step.direction)) { "Target node was not scrollable" }
            is Step.LaunchApp -> check(driver.launchApp(step.packageName)) { "App could not be launched" }
            is Step.LongClick -> check(driver.longClick(step.selector)) { "Target node was not long-clickable" }
            is Step.InputText -> {
                val text = step.variableName?.let { variableName ->
                    context.variables[variableName] ?: error("Variable '$variableName' is not defined")
                } ?: step.text
                check(driver.inputText(step.selector, text, step.inputMethod)) { "Text input failed" }
            }
            is Step.ReadNodeText -> {
                context.variables[step.variableName] = driver.readNodeAttribute(step.selector, step.attribute)
                    ?: error("Target node does not provide ${step.attribute.label()}")
            }
            is Step.SetVariable -> context.variables[step.name] = resolve(step.value, context)
            is Step.Swipe -> check(
                driver.swipe(step.startX, step.startY, step.endX, step.endY, step.durationMillis),
            ) { "Swipe failed" }
            is Step.Tap -> check(driver.tap(step.x, step.y)) { "Tap failed" }
            is Step.WaitForNode -> waitForNode(step, context)
        }
    }

    private suspend fun waitForNode(step: Step.WaitForNode, context: ExecutionContext) {
        val timeoutMillis = step.timeoutMillis ?: context.workflow.defaultStepTimeoutMillis
        withTimeout(timeoutMillis) {
            while (driver.nodeExists(step.selector) != step.mustExist) delay(NODE_POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun evaluate(condition: Condition, context: ExecutionContext): Boolean = when (condition) {
        is Condition.Equals -> condition.operator.evaluate(
            resolve(condition.left, context),
            resolve(condition.right, context),
        )
        is Condition.NodeExists -> driver.nodeExists(condition.selector)
    }

    private fun resolve(value: Value, context: ExecutionContext): String = when (value) {
        is Value.Literal -> value.value
        is Value.Variable -> context.variables[value.name]
            ?: error("Variable '${value.name}' is not defined")
        is Value.Template -> value.template.renderTemplate { variableName ->
            context.variables[variableName] ?: error("Variable '$variableName' is not defined")
        }
    }

    private fun NodeAttribute.label(): String = when (this) {
        NodeAttribute.TextOrDescription -> "text or description"
        NodeAttribute.Text -> "text"
        NodeAttribute.ContentDescription -> "content description"
        NodeAttribute.ViewId -> "resource ID"
        NodeAttribute.ClassName -> "class name"
    }

    private data class ExecutionContext(
        val workflow: Workflow,
        val variables: MutableMap<String, String> = mutableMapOf(),
        var executedSteps: Long = 0,
    )

    private class StepFailure(stepId: String, message: String, cause: Throwable? = null) :
        IllegalStateException(message, cause) {
        val stepId: String = stepId
    }

    private companion object {
        const val NODE_POLL_INTERVAL_MILLIS = 100L
    }
}
