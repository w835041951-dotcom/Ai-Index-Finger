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
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

interface AutomationDriver {
    suspend fun launchApp(packageName: String, intentAction: String? = null): Boolean
    suspend fun clickNode(selector: NodeSelector): NodeActionResult
    suspend fun clickImage(step: Step.ImageClick): ImageClickResult
    suspend fun longClickNode(selector: NodeSelector): NodeActionResult
    suspend fun tap(x: Int, y: Int): GestureActionResult
    suspend fun scrollNode(selector: NodeSelector, direction: ScrollDirection): NodeActionResult
    suspend fun inputTextNode(
        selector: NodeSelector,
        text: String,
        method: TextInputMethod,
    ): NodeActionResult
    suspend fun readNode(selector: NodeSelector, attribute: NodeAttribute): NodeReadResult
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long,
    ): GestureActionResult
    suspend fun performSystemAction(action: SystemAction): Boolean
    suspend fun nodeExists(selector: NodeSelector): Boolean
}

enum class NodeActionResult {
    Succeeded,
    TargetNotFound,
    ActionFailed,
    ClipboardUnavailable,
}

sealed interface GestureActionResult {
    data object Succeeded : GestureActionResult
    data class CoordinatesOutOfBounds(
        val displayWidth: Int,
        val displayHeight: Int,
    ) : GestureActionResult
    data object ActionFailed : GestureActionResult
}

sealed interface NodeReadResult {
    data class Value(val value: String) : NodeReadResult
    data object TargetNotFound : NodeReadResult
    data object AttributeMissing : NodeReadResult
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
    data class Paused(val workflowId: String, val stepId: String) : RunState
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
    TargetNotFound,
    UndefinedVariable,
    TextInputFailed,
    MissingNodeAttribute,
    SwipeFailed,
    TapFailed,
    CoordinatesOutOfBounds,
    ClipboardUnavailable,
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
    val error: ExecutionError? = null,
    val failedStepId: String? = null,
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
    private var stepGate: Channel<Unit>? = null

    val state: StateFlow<RunState> = mutableState.asStateFlow()

    suspend fun run(workflow: Workflow): RunResult = runWithDiagnostics(workflow).result

    suspend fun runWithDiagnostics(
        workflow: Workflow,
        stepThrough: Boolean = false,
    ): RunExecution {
        workflow.readinessIssues().firstOrNull()?.let { issue ->
            return RunExecution(RunResult.NotReady(issue), emptyList())
        }
        if (!runMutex.tryLock()) return RunExecution(RunResult.AlreadyRunning, emptyList())

        val context = ExecutionContext(workflow, nanoTime = nanoTime)
        stepGate = Channel<Unit>(Channel.CONFLATED).takeIf { stepThrough }
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
            stepGate?.close()
            stepGate = null
            mutableState.value = RunState.Idle
            runMutex.unlock()
        }
    }

    fun advance(): Boolean {
        val paused = mutableState.value as? RunState.Paused ?: return false
        val running = RunState.Running(paused.workflowId, paused.stepId)
        if (!mutableState.compareAndSet(paused, running)) return false
        val sent = stepGate?.trySend(Unit)?.isSuccess == true
        if (!sent) mutableState.compareAndSet(running, paused)
        return sent
    }

    private suspend fun executeSteps(steps: List<Step>, context: ExecutionContext) {
        for (step in steps) {
            stepGate?.let { gate ->
                mutableState.value = RunState.Paused(context.workflow.id, step.id)
                gate.receive()
            }
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
                    error = policyResult.error,
                    failedStepId = policyResult.failedStepId,
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
                    failure.error,
                    failure.stepId,
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
                val completed = withTimeoutOrNull(timeoutMillis) {
                    executeStep(step, context)
                    true
                }
                if (completed == null) throw OwnedStepTimeout()
                return PolicyExecutionResult(attempt + 1, StepExecutionOutcome.Completed)
            } catch (error: OwnedStepTimeout) {
                if (attempt + 1 < attempts) {
                    delay(retry?.delayMillis ?: 0)
                } else if (step.failurePolicy is FailurePolicy.Continue) {
                    return PolicyExecutionResult(
                        attempt + 1,
                        StepExecutionOutcome.ContinuedAfterFailure,
                        ExecutionError(ExecutionErrorCode.StepTimedOut),
                        step.id,
                    )
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
                val executionError = when (error) {
                    is StepFailure -> error.error
                    is ExecutionFailure -> error.error
                    else -> ExecutionError(ExecutionErrorCode.StepFailed)
                }
                val failedStepId = (error as? StepFailure)?.stepId ?: step.id
                if (attempt + 1 < attempts) {
                    delay(retry?.delayMillis ?: 0)
                } else if (step.failurePolicy is FailurePolicy.Continue) {
                    return PolicyExecutionResult(
                        attempt + 1,
                        StepExecutionOutcome.ContinuedAfterFailure,
                        executionError,
                        failedStepId,
                    )
                } else {
                    throw StepFailure(failedStepId, executionError, attempt + 1, error)
                }
            }
        }
        error("Execution policy completed without a result")
    }

    private suspend fun executeStep(step: Step, context: ExecutionContext) {
        when (step) {
            is Step.Click -> requireNodeAction(
                driver.clickNode(step.selector),
                ExecutionErrorCode.TargetNotClickable,
            )
            is Step.RecordedClick -> when (step.targetMode) {
                RecordedClickTargetMode.Control -> requireNodeAction(
                    driver.clickNode(requireNotNull(step.selector)),
                    ExecutionErrorCode.TargetNotClickable,
                )
                RecordedClickTargetMode.Coordinates -> requireGestureAction(
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
            is Step.GlobalAction -> if (!driver.performSystemAction(step.action)) {
                fail(
                    ExecutionErrorCode.SystemActionFailed,
                    mapOf("action" to step.action.name),
                )
            }
            is Step.IfElse -> {
                val branch = if (evaluate(step.condition, context)) step.whenTrue else step.whenFalse
                executeSteps(branch, context)
            }
            is Step.Repeat -> repeat(step.times) { executeSteps(step.steps, context) }
            is Step.Scroll -> requireNodeAction(
                driver.scrollNode(step.selector, step.direction),
                ExecutionErrorCode.TargetNotScrollable,
                mapOf("direction" to step.direction.name),
            )
            is Step.LaunchApp -> if (!driver.launchApp(step.packageName, step.intentAction)) {
                fail(
                    ExecutionErrorCode.AppLaunchFailed,
                    buildMap {
                        put("packageName", step.packageName)
                        step.intentAction?.let { put("intentAction", it) }
                    },
                )
            }
            is Step.LongClick -> requireNodeAction(
                driver.longClickNode(step.selector),
                ExecutionErrorCode.TargetNotLongClickable,
            )
            is Step.InputText -> {
                val text = step.variableName?.let { variableName ->
                    context.variables[variableName]
                        ?: fail(
                            ExecutionErrorCode.UndefinedVariable,
                            mapOf("variableName" to variableName),
                        )
                } ?: step.text
                requireNodeAction(
                    driver.inputTextNode(step.selector, text, step.inputMethod),
                    ExecutionErrorCode.TextInputFailed,
                    mapOf("inputMethod" to step.inputMethod.name),
                )
            }
            is Step.ReadNodeText -> {
                when (val result = driver.readNode(step.selector, step.attribute)) {
                    is NodeReadResult.Value -> context.variables[step.variableName] = result.value
                    NodeReadResult.TargetNotFound -> fail(ExecutionErrorCode.TargetNotFound)
                    NodeReadResult.AttributeMissing -> fail(
                        ExecutionErrorCode.MissingNodeAttribute,
                        mapOf("attribute" to step.attribute.name),
                    )
                }
            }
            is Step.SetVariable -> context.variables[step.name] = resolve(step.value, context)
            is Step.Swipe -> requireGestureAction(
                driver.swipe(step.startX, step.startY, step.endX, step.endY, step.durationMillis),
                ExecutionErrorCode.SwipeFailed,
            )
            is Step.Tap -> requireGestureAction(driver.tap(step.x, step.y), ExecutionErrorCode.TapFailed)
            is Step.WaitForNode -> waitForNode(step)
        }
    }

    private suspend fun waitForNode(step: Step.WaitForNode) {
        while (driver.nodeExists(step.selector) != step.mustExist) delay(NODE_POLL_INTERVAL_MILLIS)
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

    private fun requireGestureAction(result: GestureActionResult, failureCode: ExecutionErrorCode) {
        when (result) {
            GestureActionResult.Succeeded -> Unit
            GestureActionResult.ActionFailed -> fail(failureCode)
            is GestureActionResult.CoordinatesOutOfBounds -> fail(
                ExecutionErrorCode.CoordinatesOutOfBounds,
                mapOf(
                    "displayWidth" to result.displayWidth.toString(),
                    "displayHeight" to result.displayHeight.toString(),
                ),
            )
        }
    }

    private fun requireNodeAction(
        result: NodeActionResult,
        actionFailureCode: ExecutionErrorCode,
        arguments: Map<String, String> = emptyMap(),
    ) {
        when (result) {
            NodeActionResult.Succeeded -> Unit
            NodeActionResult.TargetNotFound -> fail(ExecutionErrorCode.TargetNotFound)
            NodeActionResult.ActionFailed -> fail(actionFailureCode, arguments)
            NodeActionResult.ClipboardUnavailable -> fail(ExecutionErrorCode.ClipboardUnavailable)
        }
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
            error: ExecutionError? = null,
            failedStepId: String? = null,
        ) {
            val diagnostic = StepExecutionDiagnostic(
                sequence = sequence,
                stepId = step.id,
                durationMillis = ((nanoTime() - startedAtNanos).coerceAtLeast(0) / 1_000_000),
                attemptCount = attemptCount,
                outcome = outcome,
                error = error,
                failedStepId = failedStepId,
            )
            if (diagnostics.size < MAX_DIAGNOSTIC_EVENTS) {
                diagnostics += diagnostic
                return
            }
            val incomingPriority = diagnosticPriority(outcome)
            val replaceIndex = diagnostics.indices
                .filter { diagnosticPriority(diagnostics[it].outcome) < incomingPriority }
                .maxByOrNull { it }
            if (replaceIndex != null) {
                diagnostics[replaceIndex] = diagnostic
            }
        }

        private fun diagnosticPriority(outcome: StepExecutionOutcome): Int = when (outcome) {
            StepExecutionOutcome.Completed -> 0
            StepExecutionOutcome.ContinuedAfterFailure -> 1
            StepExecutionOutcome.Failed,
            StepExecutionOutcome.Cancelled -> 2
        }
    }

    private data class PolicyExecutionResult(
        val attemptCount: Int,
        val outcome: StepExecutionOutcome,
        val error: ExecutionError? = null,
        val failedStepId: String? = null,
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

    private class OwnedStepTimeout : Exception()

    private companion object {
        const val NODE_POLL_INTERVAL_MILLIS = 100L
        const val MAX_DIAGNOSTIC_EVENTS = 1_000
    }
}
