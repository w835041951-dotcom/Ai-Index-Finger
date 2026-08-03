package com.aiindexfinger.executor

import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowLimits
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.readinessIssues
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
    suspend fun launchApp(packageName: String, intentAction: String? = null): Boolean
    suspend fun click(selector: NodeSelector): Boolean
    suspend fun clickImage(step: Step.ImageClick): ImageClickResult
    suspend fun longClick(selector: NodeSelector): Boolean
    suspend fun tap(x: Int, y: Int): Boolean
    suspend fun scroll(selector: NodeSelector, direction: ScrollDirection): Boolean
    suspend fun inputText(selector: NodeSelector, text: String, method: TextInputMethod): Boolean
    suspend fun readNodeAttribute(selector: NodeSelector, attribute: NodeAttribute): String?
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMillis: Long): Boolean
    suspend fun performSystemAction(action: SystemAction): Boolean
    suspend fun nodeExists(selector: NodeSelector): Boolean
}

sealed interface ImageClickResult {
    data class Clicked(val scorePermille: Int) : ImageClickResult
    data object Unsupported : ImageClickResult
    data object WrongPackage : ImageClickResult
    data object MissingOrInvalidTemplate : ImageClickResult
    data object NoMatch : ImageClickResult
    data object Ambiguous : ImageClickResult
    data object CaptureFailed : ImageClickResult
    data object GestureFailed : ImageClickResult
}

sealed interface RunState {
    data object Idle : RunState
    data class Running(val workflowId: String, val stepId: String?) : RunState
}

sealed interface RunResult {
    data object Completed : RunResult
    data object AlreadyRunning : RunResult
    data class NotReady(val issue: ValidationIssue) : RunResult
    data object Cancelled : RunResult
    data class Failed(val stepId: String, val error: ExecutionError) : RunResult
}

data class ExecutionError(
    val code: ExecutionErrorCode,
    val arguments: Map<String, String> = emptyMap(),
)

enum class ExecutionErrorCode {
    StepFailed,
    StepTimedOut,
    ExecutionLimitExceeded,
    TargetNotClickable,
    ImageClickUnsupported,
    ImageClickWrongPackage,
    ImageTemplateInvalid,
    ImageTemplateNotFound,
    ImageTemplateAmbiguous,
    ScreenCaptureFailed,
    ImageGestureFailed,
    SystemActionFailed,
    TargetNotScrollable,
    AppLaunchFailed,
    TargetNotLongClickable,
    UndefinedVariable,
    TextInputFailed,
    MissingNodeAttribute,
    SwipeFailed,
    TapFailed,
}

data class RunExecution(
    val result: RunResult,
    val diagnostics: List<StepExecutionDiagnostic>,
)

data class StepExecutionDiagnostic(
    val sequence: Long,
    val stepId: String,
    val durationMillis: Long,
    val attemptCount: Int,
    val outcome: StepExecutionOutcome,
)

enum class StepExecutionOutcome {
    Completed,
    ContinuedAfterFailure,
    Failed,
    Cancelled,
}

class WorkflowExecutor(
    private val driver: AutomationDriver,
    private val maxExecutedSteps: Long = WorkflowLimits.MAX_EXECUTED_STEPS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val runMutex = Mutex()
    private val mutableState = MutableStateFlow<RunState>(RunState.Idle)

    val state: StateFlow<RunState> = mutableState.asStateFlow()

    suspend fun run(workflow: Workflow): RunResult = runWithDiagnostics(workflow).result

    suspend fun runWithDiagnostics(workflow: Workflow): RunExecution {
        workflow.readinessIssues().firstOrNull()?.let { issue ->
            return RunExecution(RunResult.NotReady(issue), emptyList())
        }
        if (!runMutex.tryLock()) return RunExecution(RunResult.AlreadyRunning, emptyList())

        val context = ExecutionContext(workflow, nanoTime = nanoTime)
        return try {
            executeSteps(workflow.steps, context)
            RunExecution(RunResult.Completed, context.diagnostics.toList())
        } catch (_: CancellationException) {
            RunExecution(RunResult.Cancelled, context.diagnostics.toList())
        } catch (failure: StepFailure) {
            RunExecution(
                RunResult.Failed(failure.stepId, failure.error),
                context.diagnostics.toList(),
            )
        } finally {
            mutableState.value = RunState.Idle
            runMutex.unlock()
        }
    }

    private suspend fun executeSteps(steps: List<Step>, context: ExecutionContext) {
        for (step in steps) {
            mutableState.value = RunState.Running(context.workflow.id, step.id)
            val sequence = context.nextDiagnosticSequence++
            val startedAtNanos = nanoTime()
            try {
                val policyResult = executeWithPolicy(step, context)
                context.recordDiagnostic(
                    step = step,
                    sequence = sequence,
                    startedAtNanos = startedAtNanos,
                    attemptCount = policyResult.attemptCount,
                    outcome = policyResult.outcome,
                )
            } catch (cancelled: CancellationException) {
                context.recordDiagnostic(step, sequence, startedAtNanos, 1, StepExecutionOutcome.Cancelled)
                throw cancelled
            } catch (failure: StepFailure) {
                context.recordDiagnostic(
                    step,
                    sequence,
                    startedAtNanos,
                    failure.attemptCount,
                    StepExecutionOutcome.Failed,
                )
                throw failure
            }
        }
    }

    private suspend fun executeWithPolicy(step: Step, context: ExecutionContext): PolicyExecutionResult {
        val retry = step.failurePolicy as? FailurePolicy.Retry
        val attempts = retry?.attempts?.plus(1) ?: 1

        repeat(attempts) { attempt ->
            context.executedSteps++
            if (context.executedSteps > maxExecutedSteps) {
                throw StepFailure(
                    step.id,
                    ExecutionError(
                        ExecutionErrorCode.ExecutionLimitExceeded,
                        mapOf("limit" to maxExecutedSteps.toString()),
                    ),
                )
            }
            try {
                val timeoutMillis = step.timeoutMillis ?: context.workflow.defaultStepTimeoutMillis
                withTimeout(timeoutMillis) { executeStep(step, context) }
                return PolicyExecutionResult(attempt + 1, StepExecutionOutcome.Completed)
            } catch (error: TimeoutCancellationException) {
                if (attempt + 1 < attempts) {
                    delay(retry?.delayMillis ?: 0)
                } else if (step.failurePolicy is FailurePolicy.Continue) {
                    return PolicyExecutionResult(attempt + 1, StepExecutionOutcome.ContinuedAfterFailure)
                } else {
                    throw StepFailure(
                        step.id,
                        ExecutionError(ExecutionErrorCode.StepTimedOut),
                        attempt + 1,
                        error,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (attempt + 1 < attempts) {
                    delay(retry?.delayMillis ?: 0)
                } else if (step.failurePolicy is FailurePolicy.Continue) {
                    return PolicyExecutionResult(attempt + 1, StepExecutionOutcome.ContinuedAfterFailure)
                } else {
                    val executionError = when (error) {
                        is StepFailure -> error.error
                        is ExecutionFailure -> error.error
                        else -> ExecutionError(ExecutionErrorCode.StepFailed)
                    }
                    throw StepFailure(step.id, executionError, attempt + 1, error)
                }
            }
        }
        error("Execution policy completed without a result")
    }

    private suspend fun executeStep(step: Step, context: ExecutionContext) {
        when (step) {
            is Step.Click -> requireSuccess(driver.click(step.selector), ExecutionErrorCode.TargetNotClickable)
            is Step.RecordedClick -> when (step.targetMode) {
                RecordedClickTargetMode.Control -> requireSuccess(
                    driver.click(requireNotNull(step.selector)),
                    ExecutionErrorCode.TargetNotClickable,
                )
                RecordedClickTargetMode.Coordinates -> requireSuccess(
                    driver.tap(step.x, step.y),
                    ExecutionErrorCode.TapFailed,
                )
            }
            is Step.ImageClick -> when (driver.clickImage(step)) {
                is ImageClickResult.Clicked -> Unit
                ImageClickResult.Unsupported -> fail(ExecutionErrorCode.ImageClickUnsupported)
                ImageClickResult.WrongPackage -> fail(ExecutionErrorCode.ImageClickWrongPackage)
                ImageClickResult.MissingOrInvalidTemplate -> fail(ExecutionErrorCode.ImageTemplateInvalid)
                ImageClickResult.NoMatch -> fail(ExecutionErrorCode.ImageTemplateNotFound)
                ImageClickResult.Ambiguous -> fail(ExecutionErrorCode.ImageTemplateAmbiguous)
                ImageClickResult.CaptureFailed -> fail(ExecutionErrorCode.ScreenCaptureFailed)
                ImageClickResult.GestureFailed -> fail(ExecutionErrorCode.ImageGestureFailed)
            }
            is Step.Delay -> delay(step.durationMillis)
            is Step.GlobalAction ->
                requireSuccess(driver.performSystemAction(step.action), ExecutionErrorCode.SystemActionFailed)
            is Step.IfElse -> {
                val branch = if (evaluate(step.condition, context)) step.whenTrue else step.whenFalse
                executeSteps(branch, context)
            }
            is Step.Repeat -> repeat(step.times) { executeSteps(step.steps, context) }
            is Step.Scroll ->
                requireSuccess(driver.scroll(step.selector, step.direction), ExecutionErrorCode.TargetNotScrollable)
            is Step.LaunchApp ->
                requireSuccess(driver.launchApp(step.packageName, step.intentAction), ExecutionErrorCode.AppLaunchFailed)
            is Step.LongClick ->
                requireSuccess(driver.longClick(step.selector), ExecutionErrorCode.TargetNotLongClickable)
            is Step.InputText -> {
                val text = step.variableName?.let { variableName ->
                    context.variables[variableName]
                        ?: fail(
                            ExecutionErrorCode.UndefinedVariable,
                            mapOf("variableName" to variableName),
                        )
                } ?: step.text
                requireSuccess(
                    driver.inputText(step.selector, text, step.inputMethod),
                    ExecutionErrorCode.TextInputFailed,
                )
            }
            is Step.ReadNodeText -> {
                context.variables[step.variableName] = driver.readNodeAttribute(step.selector, step.attribute)
                    ?: fail(
                        ExecutionErrorCode.MissingNodeAttribute,
                        mapOf("attribute" to step.attribute.name),
                    )
            }
            is Step.SetVariable -> context.variables[step.name] = resolve(step.value, context)
            is Step.Swipe -> requireSuccess(
                driver.swipe(step.startX, step.startY, step.endX, step.endY, step.durationMillis),
                ExecutionErrorCode.SwipeFailed,
            )
            is Step.Tap -> requireSuccess(driver.tap(step.x, step.y), ExecutionErrorCode.TapFailed)
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
            ?: fail(ExecutionErrorCode.UndefinedVariable, mapOf("variableName" to value.name))
        is Value.Template -> value.template.renderTemplate { variableName ->
            context.variables[variableName]
                ?: fail(ExecutionErrorCode.UndefinedVariable, mapOf("variableName" to variableName))
        }
    }

    private fun requireSuccess(success: Boolean, code: ExecutionErrorCode) {
        if (!success) fail(code)
    }

    private fun fail(code: ExecutionErrorCode, arguments: Map<String, String> = emptyMap()): Nothing =
        throw ExecutionFailure(ExecutionError(code, arguments))

    private data class ExecutionContext(
        val workflow: Workflow,
        val nanoTime: () -> Long,
        val variables: MutableMap<String, String> = mutableMapOf(),
        val diagnostics: MutableList<StepExecutionDiagnostic> = mutableListOf(),
        var executedSteps: Long = 0,
        var nextDiagnosticSequence: Long = 0,
    ) {
        fun recordDiagnostic(
            step: Step,
            sequence: Long,
            startedAtNanos: Long,
            attemptCount: Int,
            outcome: StepExecutionOutcome,
        ) {
            if (diagnostics.size >= MAX_DIAGNOSTIC_EVENTS) return
            diagnostics += StepExecutionDiagnostic(
                sequence = sequence,
                stepId = step.id,
                durationMillis = ((nanoTime() - startedAtNanos).coerceAtLeast(0) / 1_000_000),
                attemptCount = attemptCount,
                outcome = outcome,
            )
        }
    }

    private data class PolicyExecutionResult(
        val attemptCount: Int,
        val outcome: StepExecutionOutcome,
    )

    private class StepFailure(
        stepId: String,
        val error: ExecutionError,
        val attemptCount: Int = 1,
        cause: Throwable? = null,
    ) :
        IllegalStateException(error.code.name, cause) {
        val stepId: String = stepId
    }

    private class ExecutionFailure(val error: ExecutionError) : IllegalStateException(error.code.name)

    private companion object {
        const val NODE_POLL_INTERVAL_MILLIS = 100L
        const val MAX_DIAGNOSTIC_EVENTS = 1_000
    }
}
