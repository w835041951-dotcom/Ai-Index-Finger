package com.aiindexfinger.automation

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.graphics.Path
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.aiindexfinger.FloatingWorkflowEditorActivity
import com.aiindexfinger.MainActivity
import com.aiindexfinger.R
import com.aiindexfinger.executor.AutomationDriver
import com.aiindexfinger.executor.ImageClickResult
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.executor.RunState
import com.aiindexfinger.executor.WorkflowExecutor
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.toRunRecord
import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.RecordedBounds
import com.aiindexfinger.model.RecordedClickFallbackCause
import com.aiindexfinger.model.RecordedControl
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.StepListPath
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.matches
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.isReadyToRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.hypot

internal fun targetPackageIsVisible(
    targetPackage: String,
    activePackage: String?,
    windowPackages: Iterable<String>,
): Boolean = activePackage == targetPackage || targetPackage in windowPackages

internal fun accessibilityServiceFlags(existingFlags: Int): Int = existingFlags or
    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

internal data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal fun matchIsInsideTargetWindow(
    match: TemplateMatchResult.Unique,
    targetBounds: List<ScreenBounds>,
): Boolean = targetBounds.any { bounds ->
    match.centerX >= bounds.left && match.centerX < bounds.right &&
        match.centerY >= bounds.top && match.centerY < bounds.bottom
}

internal fun cropIsInsideTargetWindow(
    crop: ImageCropBounds,
    targetBounds: List<ScreenBounds>,
): Boolean = targetBounds.any { bounds ->
    crop.left >= bounds.left && crop.top >= bounds.top &&
        crop.right <= bounds.right && crop.bottom <= bounds.bottom
}

class AutomationAccessibilityService : AccessibilityService(), AutomationDriver {
    private val workflowExecutor by lazy { WorkflowExecutor(this) }
    private val runHistoryStore by lazy { RunHistoryStore(this) }
    private val overlayWindowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var workflowJob: Job? = null
    private var observationSettleJob: Job? = null
    private var recordingSnapshotJob: Job? = null
    private var screenCaptureSettleJob: Job? = null
    private var screenCaptureTimeoutJob: Job? = null
    private var screenCaptureRequestId = 0L
    private var runningWorkflowName: String? = null
    private var lastExternalAppPackage: String? = null
    private var homePackages: Set<String> = emptySet()
    private var elementMonitorView: View? = null
    private var elementInspectorView: View? = null
    private var elementPickView: View? = null
    private var elementInspectionCapture: RecordingHierarchyCapture? = null
    private var liveActionView: View? = null
    private var liveCoordinatePickView: View? = null
    private var liveImageCropView: View? = null
    private var liveImageCropMessageView: TextView? = null
    private var liveImageCaptureBitmap: Bitmap? = null
    private var liveImageCaptureJob: Job? = null
    private var liveActionLaunchJob: Job? = null
    private var floatingEditorLaunchJob: Job? = null
    private var floatingEditorRestoreView: View? = null
    private var liveImageTargetBounds: List<ScreenBounds> = emptyList()
    private var liveActionTargetPackage: String? = null
    private var liveActionStatusMessage: String? = null
    private val liveActionSession = LiveActionSession()
    private var swipeCaptureView: View? = null
    private var swipeInFlight = false
    private var finishAfterSwipe = false
    private var appPickerView: View? = null
    private var monitoredTargetPackage: String? = null
    private var lastRecordedLongClick: Pair<String, Long>? = null
    private val lastScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private var lastRecordedScroll: Triple<String, ScrollDirection, Long>? = null
    private var suppressScrollUntilMillis = Long.MIN_VALUE
    private var recordingWorkflowId: String? = null
    private var recordingListPath: StepListPath? = null
    private val recordedClickSession = RecordedClickSession(MAX_RECORDED_CLICKS)
    private val recordingTargetResolver = RecordingTargetResolver(
        maxSnapshots = MAX_RECORDING_WINDOW_SNAPSHOTS,
    )
    private val targetPreferences by lazy {
        getSharedPreferences(TARGET_PREFERENCES_NAME, MODE_PRIVATE)
    }

    override fun onServiceConnected() {
        lastExternalAppPackage = targetPreferences.getString(LAST_EXTERNAL_PACKAGE_KEY, null)
        homePackages = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        ).mapTo(mutableSetOf()) { it.activityInfo.packageName }
        serviceInfo = serviceInfo.apply {
            flags = accessibilityServiceFlags(flags)
        }
        val previousInstance = instanceOwner.claim(this)
        if (previousInstance != null && previousInstance !== this) {
            previousInstance.retireForReplacement()
            resetVolatileSharedStateAfterReplacement()
        }
        mutableConnected.value = true
        if (FloatingWorkflowEditorActivity.hasCollapsedSession()) {
            showFloatingEditorRestoreControl()
        }
        serviceScope.launch {
            workflowExecutor.state.collect { state ->
                if (!isCurrentServiceInstance()) {
                    return@collect
                }
                val stepId = when (state) {
                    is RunState.Running -> state.stepId
                    is RunState.Paused -> state.stepId
                    RunState.Idle -> null
                }
                currentStepId.value = stepId
                debugPaused.value = state is RunState.Paused
                runningWorkflowName?.let { name -> showRunningNotification(name, stepId) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isCurrentServiceInstance()) return
        handleArmedScreenCapture(event)
        event ?: return
        val packageName = event.packageName?.toString()
            ?: event.source?.packageName?.toString()
            ?: windows.firstOrNull { it.id == event.windowId }?.root?.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return
        if (liveActionSession.isActive && packageName == liveActionTargetPackage &&
            liveActionView?.isAttachedToWindow != true && liveCoordinatePickView == null &&
            liveImageCropView == null && liveImageCaptureJob?.isActive != true
        ) {
            showLiveActionOverlay()
        }
        if (recordedClickSession.isActive() && packageName == monitoredTargetPackage &&
            elementMonitorView?.isAttachedToWindow != true
        ) {
            showRecordingOverlay()
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            packageName == monitoredTargetPackage &&
            elementMonitorView != null
        ) {
            recordedClickSession.closeAllTextBursts()
            showRecordingOverlay()
            recordingSnapshotJob?.cancel()
            recordingSnapshotJob = null
            val source = event.source?.toDescriptor()
                ?.takeIf { it.packageName == monitoredTargetPackage }
            if (source == null) {
                recordClickIssue(event, RecordingIssueReason.SourceUnavailable)
            } else {
                val resolved = recordingTargetResolver.resolve(
                    source = source,
                    packageName = packageName,
                    windowId = event.windowId,
                    eventTimeMillis = event.eventTime,
                )
                val targetNode = resolved?.node ?: source
                val selectorKey = resolved?.selector?.toString()
                val duplicateLongClick = lastRecordedLongClick?.let { (lastKey, lastTime) ->
                    selectorKey != null && selectorKey == lastKey &&
                        event.eventTime - lastTime <= LONG_CLICK_CLICK_SUPPRESSION_MILLIS
                } == true
                if (duplicateLongClick) return
                val recorded = recordClickTarget(
                    targetNode,
                    resolved?.selector,
                    allowRecommendedSelector = false,
                    fallbackCause = resolved?.fallbackCause,
                )
                if (!recorded) {
                    recordClickIssue(event, RecordingIssueReason.SourceInvalid)
                } else if (resolved?.selector == null) {
                    recordClickIssue(event, RecordingIssueReason.ControlNotUnique)
                }
            }
            scheduleRecordingSnapshot(packageName, event.windowId, event.eventTime)
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED &&
            packageName == monitoredTargetPackage &&
            elementMonitorView != null
        ) {
            recordedClickSession.closeAllTextBursts()
            recordingSnapshotJob?.cancel()
            recordingSnapshotJob = null
            val source = event.source?.toDescriptor()
                ?.takeIf { it.packageName == monitoredTargetPackage }
            val resolved = source?.let {
                recordingTargetResolver.resolve(
                    source = it,
                    packageName = packageName,
                    windowId = event.windowId,
                    eventTimeMillis = event.eventTime,
                    capability = RecordingNodeCapability.LongClick,
                )
            }
            val selector = resolved?.selector
            if (selector != null && resolved.node.longClickable) {
                if (recordedClickSession.recordStep(Step.LongClick(UUID.randomUUID().toString(), selector))) {
                    lastRecordedLongClick = selector.toString() to event.eventTime
                    updateRecordingOverlay()
                }
            } else {
                recordClickIssue(event, RecordingIssueReason.ControlNotUnique)
            }
            scheduleRecordingSnapshot(packageName, event.windowId, event.eventTime)
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            packageName == monitoredTargetPackage &&
            elementMonitorView != null
        ) {
            val sourceInfo = event.source
            val source = sourceInfo?.toDescriptor()
                ?.takeIf { it.packageName == monitoredTargetPackage }
            if (event.isPassword || sourceInfo?.isPassword == true) {
                recordClickIssue(event, RecordingIssueReason.SensitiveText)
            } else if (source != null) {
                val resolved = recordingTargetResolver.resolve(
                    source = source,
                    packageName = packageName,
                    windowId = event.windowId,
                    eventTimeMillis = event.eventTime,
                )
                val selector = resolved?.selector
                val text = sourceInfo?.text?.toString()
                    ?: event.text.lastOrNull()?.toString()
                    ?: "".takeIf { event.removedCount > 0 }
                if (selector != null && text != null) {
                    val key = listOf(packageName, event.windowId, selector).joinToString("|")
                    recordedClickSession.recordOrReplaceText(
                        key,
                        Step.InputText(
                            id = UUID.randomUUID().toString(),
                            selector = selector,
                            text = text,
                            inputMethod = TextInputMethod.SetText,
                        ),
                    )
                    updateRecordingOverlay()
                } else {
                    recordClickIssue(event, RecordingIssueReason.ControlNotUnique)
                }
            } else {
                recordClickIssue(event, RecordingIssueReason.SourceUnavailable)
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            packageName == monitoredTargetPackage &&
            elementMonitorView != null
        ) {
            if (event.eventTime <= suppressScrollUntilMillis) return
            recordedClickSession.closeAllTextBursts()
            val source = event.source?.toDescriptor()
                ?.takeIf { it.packageName == monitoredTargetPackage }
            val resolved = source?.let {
                recordingTargetResolver.resolve(
                    source = it,
                    packageName = packageName,
                    windowId = event.windowId,
                    eventTimeMillis = event.eventTime,
                    capability = RecordingNodeCapability.Scroll,
                )
            }
            val selector = resolved?.selector
            val key = selector?.let { listOf(packageName, event.windowId, it).joinToString("|") }
            val currentPosition = event.scrollX to event.scrollY
            val previousPosition = key?.let(lastScrollPositions::get)?.takeIf { it != currentPosition }
            if (key != null) lastScrollPositions[key] = currentPosition
            val deltaX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0
            val deltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
            val direction = when {
                deltaY > 0 || deltaX > 0 -> ScrollDirection.Forward
                deltaY < 0 || deltaX < 0 -> ScrollDirection.Backward
                previousPosition != null &&
                    (currentPosition.first > previousPosition.first || currentPosition.second > previousPosition.second) ->
                    ScrollDirection.Forward
                previousPosition != null &&
                    (currentPosition.first < previousPosition.first || currentPosition.second < previousPosition.second) ->
                    ScrollDirection.Backward
                else -> null
            }
            if (selector != null && key != null && direction != null && resolved.node.scrollable) {
                val duplicate = lastRecordedScroll?.let { (lastKey, lastDirection, lastTime) ->
                    lastKey == key && lastDirection == direction &&
                        event.eventTime - lastTime <= SCROLL_DUPLICATE_SUPPRESSION_MILLIS
                } == true
                if (!duplicate && recordedClickSession.recordStep(
                        Step.Scroll(UUID.randomUUID().toString(), selector, direction),
                    )
                ) {
                    lastRecordedScroll = Triple(key, direction, event.eventTime)
                    updateRecordingOverlay()
                }
            } else {
                recordClickIssue(event, RecordingIssueReason.ControlNotUnique)
            }
        }
        if (!isEligibleExternalPackage(packageName, applicationContext.packageName, homePackages)) {
            observationController.sourceUnavailable()
            return
        }
        if (packageWindowRoots(packageName, event.windowId).isEmpty()) {
            observationController.sourceUnavailable()
            return
        }
        if (packageName != lastExternalAppPackage &&
            packageManager.getLaunchIntentForPackage(packageName) != null
        ) {
            rememberExternalAppPackage(packageName)
        }
        if (packageName == monitoredTargetPackage && elementMonitorView != null &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                showRecordingOverlay()
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                recordingTargetResolver.markHierarchyMutation(packageName, event.windowId, event.eventTime)
            }
            captureRecordingSnapshot(packageName, event.windowId)
            scheduleRecordingSnapshot(packageName, event.windowId, event.eventTime)
        }
        if (!observationController.isObservationRequested) return
        observationSettleJob?.cancel()
        observationSettleJob = serviceScope.launch {
            delay(OBSERVATION_SETTLE_MILLIS)
            if (!observationController.isObservationRequested) return@launch
            captureObservedNodes(packageName, event.windowId)
        }
    }

    private fun captureObservedNodes(packageName: String, windowId: Int? = null) {
        val settledRoots = packageWindowRoots(packageName, windowId)
        if (settledRoots.isEmpty()) return
        val snapshot = mergeObservedNodeWindows(
            windows = settledRoots.map { root ->
                root.depthFirstSequence()
                    .mapNotNull { it.toDescriptor() }
                    .filter { it.packageName == packageName }
            },
            limit = MAX_OBSERVED_NODES,
        )
        if (snapshot != observedNodes.value) observedNodes.value = snapshot
    }

    private fun cancelPendingObservationCapture() {
        observationSettleJob?.cancel()
        observationSettleJob = null
    }

    override fun onInterrupt() {
        val ownedSharedState = isCurrentServiceInstance()
        stopWorkflow()
        stopElementMonitor(
            preservePendingSelector = !ownedSharedState,
            stopForegroundService = ownedSharedState,
        )
        stopLiveAction()
        if (ownedSharedState) clearScreenCapture()
    }

    override fun onDestroy() {
        val ownedSharedState = instanceOwner.release(this)
        stopElementMonitor(
            preservePendingSelector = true,
            stopForegroundService = ownedSharedState,
        )
        stopLiveAction()
        hideFloatingEditorRestoreControl()
        serviceScope.cancel()
        if (ownedSharedState) {
            cancelRunningNotification()
            currentStepId.value = null
            debugPaused.value = false
            runningWorkflowId.value = null
            workflowStartedAtMillis.value = null
            observationController.sourceDisconnected()
            clearScreenCapture()
            mutableConnected.value = false
        }
        super.onDestroy()
    }

    private fun isCurrentServiceInstance(): Boolean = instanceOwner.isCurrent(this)

    private fun retireForReplacement() {
        stopWorkflow()
        stopElementMonitor(
            preservePendingSelector = true,
            stopForegroundService = false,
        )
        stopLiveAction()
        floatingEditorLaunchJob?.cancel()
        floatingEditorLaunchJob = null
        hideFloatingEditorRestoreControl()
        cancelPendingObservationCapture()
        serviceScope.cancel()
    }

    private fun resetVolatileSharedStateAfterReplacement() {
        stopRecordingForeground()
        cancelRunningNotification()
        currentStepId.value = null
        debugPaused.value = false
        runningWorkflowId.value = null
        workflowStartedAtMillis.value = null
        overlayStatus.value = null
        observationController.sourceDisconnected()
        clearScreenCapture()
    }

    fun startWorkflow(workflow: Workflow, debug: Boolean = false): Boolean {
        if (!workflow.isReadyToRun()) return false
        if (workflowJob?.isActive == true) return false
        workflowJob = serviceScope.launch {
            val startedAtMillis = System.currentTimeMillis()
            runningWorkflowId.value = workflow.id
            workflowStartedAtMillis.value = startedAtMillis
            runningWorkflowName = workflow.name
            showRunningNotification(workflow.name)
            try {
                val execution = workflowExecutor.runWithDiagnostics(workflow, stepThrough = debug)
                val result = execution.result
                val record = result.toRunRecord(
                    workflow,
                    startedAtMillis,
                    System.currentTimeMillis(),
                    execution.diagnostics,
                )
                withContext(Dispatchers.IO + NonCancellable) {
                    runHistoryStore.append(record)
                }
                latestRun.value = RunOutcome(result, record)
            } finally {
                if (isCurrentServiceInstance()) {
                    runningWorkflowId.value = null
                    workflowStartedAtMillis.value = null
                    runningWorkflowName = null
                    cancelRunningNotification()
                }
            }
        }
        return true
    }

    fun stopWorkflow() {
        workflowJob?.cancel()
    }

    fun advanceWorkflow(): Boolean = workflowExecutor.advance()

    fun capturePreviousApp(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_requires_android_11))
            return false
        }
        if (screenCaptureState.value is ScreenCaptureState.Armed) return false
        clearScreenCapture()
        val targetPackage = lastExternalAppPackage
        val targetIntent = targetPackage?.let(packageManager::getLaunchIntentForPackage)
        if (targetPackage == null || targetIntent == null) {
            screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_no_previous_app))
            return false
        }
        val requestId = ++screenCaptureRequestId
        screenCaptureState.value = ScreenCaptureState.Armed
        screenCaptureTimeoutJob = serviceScope.launch {
            delay(SCREEN_CAPTURE_TIMEOUT_MILLIS)
            if (requestId == screenCaptureRequestId && screenCaptureState.value is ScreenCaptureState.Armed) {
                screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_timed_out))
                returnToEditor()
            }
        }
        try {
            startActivity(
                targetIntent.apply {
                    flags = targetAppLaunchFlags(flags)
                },
            )
        } catch (_: ActivityNotFoundException) {
            clearScreenCapture()
            clearExternalAppPackage()
            screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_previous_app_unavailable))
            return false
        } catch (_: SecurityException) {
            clearScreenCapture()
            clearExternalAppPackage()
            screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_previous_app_unavailable))
            return false
        }
        return true
    }

    fun startElementMonitor(workflowId: String, listPath: StepListPath): Boolean {
        val targetPackage = lastExternalAppPackage
            ?: targetPreferences.getString(LAST_EXTERNAL_PACKAGE_KEY, null)
        val targetIntent = targetPackage?.let(packageManager::getLaunchIntentForPackage)
        if (targetPackage == null || targetIntent == null) {
            overlayStatus.value = getString(R.string.element_monitor_no_target)
            return false
        }
        stopLiveAction()
        stopElementInspector()
        stopElementMonitor()
        monitoredTargetPackage = targetPackage
        recordingWorkflowId = workflowId
        recordingListPath = listPath
        recordedClickSession.start()
        lastRecordedLongClick = null
        lastScrollPositions.clear()
        lastRecordedScroll = null
        suppressScrollUntilMillis = Long.MIN_VALUE
        recordingTargetResolver.clear()
        startRecordingForeground(targetPackage)
        return try {
            if (!showRecordingOverlay() || !performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                stopElementMonitor()
                overlayStatus.value = getString(R.string.element_monitor_start_failed)
                return false
            }
            scheduleRecordingSnapshot(targetPackage, windowId = null, eventTimeMillis = null)
            true
        } catch (_: RuntimeException) {
            stopElementMonitor()
            overlayStatus.value = getString(R.string.element_monitor_start_failed)
            false
        }
    }

    fun startElementInspector(): Boolean {
        val targetPackage = lastExternalAppPackage
            ?: targetPreferences.getString(LAST_EXTERNAL_PACKAGE_KEY, null)
        val targetIntent = targetPackage?.let(packageManager::getLaunchIntentForPackage)
        if (targetPackage == null || targetIntent == null) {
            overlayStatus.value = getString(R.string.element_inspector_no_target)
            return false
        }
        stopLiveAction()
        stopElementMonitor()
        stopElementInspector()
        monitoredTargetPackage = targetPackage
        return try {
            if (!showElementInspectorOverlay() || !performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                stopElementInspector()
                overlayStatus.value = getString(R.string.element_inspector_start_failed)
                false
            } else {
                true
            }
        } catch (_: RuntimeException) {
            stopElementInspector()
            overlayStatus.value = getString(R.string.element_inspector_start_failed)
            false
        }
    }

    fun startLiveAction(workflowId: String, listPath: StepListPath): Boolean {
        val targetPackage = lastExternalAppPackage
            ?: targetPreferences.getString(LAST_EXTERNAL_PACKAGE_KEY, null)
        val targetIntent = targetPackage?.let(packageManager::getLaunchIntentForPackage)
        if (targetPackage == null || targetIntent == null) {
            overlayStatus.value = liveActionString(R.string.live_action_no_target)
            return false
        }
        stopElementMonitor()
        stopLiveAction()
        liveActionTargetPackage = targetPackage
        liveActionStatusMessage = null
        liveActionSession.start(workflowId, listPath)
        return try {
            startActivity(
                targetIntent.apply {
                    flags = targetAppLaunchFlags(flags)
                },
            )
            liveActionLaunchJob = serviceScope.launch {
                delay(LIVE_TARGET_LAUNCH_SETTLE_MILLIS)
                liveActionLaunchJob = null
                if (!liveActionSession.isActive || liveActionTargetPackage != targetPackage) return@launch
                if (liveActionView?.isAttachedToWindow != true && liveCoordinatePickView == null &&
                    liveImageCropView == null && liveImageCaptureJob?.isActive != true &&
                    !showLiveActionOverlay()
                ) {
                    overlayStatus.value = liveActionString(R.string.live_action_start_failed)
                    stopLiveAction()
                    returnToEditor()
                }
            }
            true
        } catch (_: RuntimeException) {
            stopLiveAction()
            overlayStatus.value = liveActionString(R.string.live_action_start_failed)
            false
        }
    }

    fun startFloatingWorkflowEditor(workflowId: String? = null): Boolean {
        val targetPackage = lastExternalAppPackage
            ?: targetPreferences.getString(LAST_EXTERNAL_PACKAGE_KEY, null)
        val targetIntent = targetPackage?.let(packageManager::getLaunchIntentForPackage)
        if (targetPackage == null || targetIntent == null) {
            overlayStatus.value = liveActionString(R.string.floating_editor_no_target)
            return false
        }
        floatingEditorLaunchJob?.cancel()
        return try {
            startActivity(targetIntent.apply { flags = targetAppLaunchFlags(flags) })
            floatingEditorLaunchJob = serviceScope.launch {
                delay(LIVE_TARGET_LAUNCH_SETTLE_MILLIS)
                floatingEditorLaunchJob = null
                if (!isCurrentServiceInstance()) return@launch
                captureObservedNodes(targetPackage)
                startActivity(
                    FloatingWorkflowEditorActivity.createIntent(
                        this@AutomationAccessibilityService,
                        workflowId,
                    ),
                )
            }
            true
        } catch (_: RuntimeException) {
            overlayStatus.value = liveActionString(R.string.floating_editor_start_failed)
            false
        }
    }

    fun showFloatingEditorRestoreControl(): Boolean {
        hideFloatingEditorRestoreControl()
        val restoreButton = Button(this).apply {
            text = liveActionString(R.string.floating_editor_restore)
            contentDescription = liveActionString(R.string.floating_editor_restore_description)
            setOnClickListener { restoreFloatingWorkflowEditor() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        return runCatching {
            overlayWindowManager.addView(restoreButton, params)
            floatingEditorRestoreView = restoreButton
            floatingEditorRestoreVisible.value = true
        }.isSuccess
    }

    fun restoreFloatingWorkflowEditor(): Boolean {
        val intent = FloatingWorkflowEditorActivity.returnIntent(this) ?: return false
        return runCatching { startActivity(intent) }.isSuccess
    }

    fun hideFloatingEditorRestoreControl() {
        floatingEditorRestoreView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        floatingEditorRestoreView = null
        floatingEditorRestoreVisible.value = false
    }

    private fun liveActionContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
        val appLocales = getSystemService(android.app.LocaleManager::class.java).applicationLocales
        if (appLocales.isEmpty) return this
        val configuration = android.content.res.Configuration(resources.configuration).apply {
            setLocales(appLocales)
        }
        return createConfigurationContext(configuration)
    }

    private fun liveActionString(resourceId: Int, vararg formatArgs: Any): String =
        liveActionContext().getString(resourceId, *formatArgs)

    private fun showLiveActionOverlay(): Boolean {
        liveActionView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        val candidate = liveActionSession.selectedCandidate
        val details = TextView(this).apply {
            text = liveActionStatusMessage ?: when (candidate) {
                is LiveActionCandidate.Coordinate -> liveActionString(
                    R.string.live_action_coordinate_selected,
                    candidate.x,
                    candidate.y,
                )
                is LiveActionCandidate.Image -> liveActionString(
                    R.string.live_action_image_selected,
                    candidate.templateWidth,
                    candidate.templateHeight,
                )
                null -> liveActionString(R.string.live_action_ready, liveActionTargetPackage.orEmpty())
            }
            setPadding(24, 16, 24, 12)
        }
        val coordinateButton = Button(this).apply {
            text = liveActionString(R.string.live_action_coordinate)
            contentDescription = liveActionString(R.string.live_action_coordinate_description)
            setOnClickListener { startLiveCoordinatePicker() }
        }
        val imageButton = Button(this).apply {
            text = liveActionString(R.string.live_action_image)
            contentDescription = liveActionString(R.string.live_action_image_description)
            isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            setOnClickListener { startLiveImageCapture() }
        }
        val confirmButton = Button(this).apply {
            text = liveActionString(R.string.live_action_add)
            contentDescription = liveActionString(R.string.live_action_add_description)
            isEnabled = candidate != null
            setOnClickListener { confirmLiveAction() }
        }
        val cancelButton = Button(this).apply {
            text = liveActionString(R.string.cancel)
            setOnClickListener { stopLiveAction(returnToEditor = true) }
        }
        val modeActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                coordinateButton,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                imageButton,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        val decisionActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                confirmButton,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                cancelButton,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F7F7.toInt())
            elevation = 12f
            addView(details)
            addView(modeActions)
            addView(decisionActions)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        return runCatching {
            overlayWindowManager.addView(panel, params)
            liveActionView = panel
            overlayStatus.value = null
        }.isSuccess
    }

    private fun startLiveCoordinatePicker() {
        if (!liveActionSession.isActive || liveCoordinatePickView != null || liveImageCropView != null ||
            liveImageCaptureJob?.isActive == true
        ) {
            return
        }
        cancelLiveActionLaunch()
        liveActionStatusMessage = null
        liveActionView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        liveActionView = null
        var selectionTouchStarted = false
        val picker = TextView(this).apply {
            text = liveActionString(R.string.live_action_coordinate_instruction)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x33000000)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        selectionTouchStarted = true
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!selectionTouchStarted) return@setOnTouchListener true
                        selectionTouchStarted = false
                        val x = event.rawX.toInt()
                        val y = event.rawY.toInt()
                        val targetPackage = liveActionTargetPackage
                        val isInsideTarget = targetPackage != null && targetPackageWindowBounds(targetPackage).any {
                            x >= it.left && x < it.right && y >= it.top && y < it.bottom
                        }
                        if (isInsideTarget) {
                            liveActionSession.select(LiveActionCandidate.Coordinate(x, y))
                            liveActionStatusMessage = null
                        } else {
                            liveActionStatusMessage = liveActionString(R.string.live_action_coordinate_outside_target)
                        }
                        stopLiveCoordinatePicker()
                        showLiveActionOverlay()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        selectionTouchStarted = false
                        stopLiveCoordinatePicker()
                        showLiveActionOverlay()
                        true
                    }
                    else -> true
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        runCatching {
            overlayWindowManager.addView(picker, params)
            liveCoordinatePickView = picker
        }.onFailure {
            showLiveActionOverlay()
            overlayStatus.value = liveActionString(R.string.live_action_start_failed)
        }
    }

    private fun startLiveImageCapture() {
        if (!liveActionSession.isActive || liveCoordinatePickView != null || liveImageCropView != null ||
            liveImageCaptureJob?.isActive == true
        ) {
            return
        }
        cancelLiveActionLaunch()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            liveActionStatusMessage = liveActionString(R.string.live_action_image_requires_android_11)
            showLiveActionOverlay()
            return
        }
        val targetPackage = liveActionTargetPackage ?: return
        if (!isTargetPackageVisible(targetPackage)) {
            liveActionStatusMessage = liveActionString(R.string.live_action_target_not_visible)
            showLiveActionOverlay()
            return
        }
        liveActionStatusMessage = null
        liveActionView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        liveActionView = null
        liveImageCaptureJob?.cancel()
        liveImageCaptureJob = serviceScope.launch {
            delay(LIVE_CAPTURE_OVERLAY_HIDE_MILLIS)
            if (!liveActionSession.isActive || liveActionTargetPackage != targetPackage) return@launch
            if (!isTargetPackageVisible(targetPackage)) {
                liveActionStatusMessage = liveActionString(R.string.live_action_target_not_visible)
                showLiveActionOverlay()
                return@launch
            }
            val bitmap = captureBitmapOnce()
            if (!liveActionSession.isActive || liveActionTargetPackage != targetPackage) {
                bitmap?.recycle()
                return@launch
            }
            if (bitmap == null) {
                liveActionStatusMessage = liveActionString(R.string.live_action_capture_failed)
                showLiveActionOverlay()
                return@launch
            }
            val targetBounds = targetPackageWindowBounds(targetPackage)
            if (targetBounds.isEmpty()) {
                bitmap.recycle()
                liveActionStatusMessage = liveActionString(R.string.live_action_target_not_visible)
                showLiveActionOverlay()
                return@launch
            }
            liveImageCaptureBitmap = bitmap
            liveImageTargetBounds = targetBounds
            if (!showLiveImageCropOverlay(bitmap)) {
                stopLiveImageCrop()
                liveActionStatusMessage = liveActionString(R.string.live_action_start_failed)
                showLiveActionOverlay()
            }
        }
    }

    private fun showLiveImageCropOverlay(bitmap: Bitmap): Boolean {
        val message = TextView(this).apply {
            text = liveActionString(R.string.live_action_image_crop_instruction)
            setPadding(24, 16, 24, 12)
        }
        val cancelButton = Button(this).apply {
            text = liveActionString(R.string.cancel)
            setOnClickListener {
                stopLiveImageCrop()
                showLiveActionOverlay()
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFF7F7F7.toInt())
            addView(
                message,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(cancelButton)
        }
        val cropView = LiveImageCropView(this, bitmap, ::selectLiveImageCrop).apply {
            contentDescription = liveActionString(R.string.live_action_image_crop_description)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(
                cropView,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        return runCatching {
            overlayWindowManager.addView(panel, params)
            liveImageCropView = panel
            liveImageCropMessageView = message
        }.isSuccess
    }

    private fun selectLiveImageCrop(bounds: ImageCropBounds) {
        val bitmap = liveImageCaptureBitmap ?: return
        if (!cropIsInsideTargetWindow(bounds, liveImageTargetBounds)) {
            liveImageCropMessageView?.text = liveActionString(R.string.live_action_image_outside_target)
            return
        }
        val crop = cropTemplate(bitmap, bounds)
        if (crop == null) {
            liveImageCropMessageView?.text = liveActionString(
                R.string.image_click_crop_too_small,
                Step.ImageClick.MIN_TEMPLATE_SIZE,
            )
            return
        }
        val encoded = try {
            encodeTemplatePng(crop)
        } finally {
            if (crop !== bitmap) crop.recycle()
        }
        if (encoded == null) {
            liveImageCropMessageView?.text = liveActionString(R.string.image_click_template_too_large)
            return
        }
        val targetPackage = liveActionTargetPackage ?: return
        liveActionSession.select(
            LiveActionCandidate.Image(
                packageName = targetPackage,
                templatePngBase64 = encoded.base64,
                templateWidth = encoded.width,
                templateHeight = encoded.height,
            ),
        )
        liveActionStatusMessage = null
        stopLiveImageCrop()
        showLiveActionOverlay()
    }

    private fun stopLiveImageCrop() {
        liveImageCropView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        liveImageCropView = null
        liveImageCropMessageView = null
        liveImageCaptureBitmap?.recycle()
        liveImageCaptureBitmap = null
        liveImageTargetBounds = emptyList()
    }

    private fun stopLiveCoordinatePicker() {
        liveCoordinatePickView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        liveCoordinatePickView = null
    }

    private fun confirmLiveAction() {
        val confirmed = liveActionSession.confirm() ?: return
        pendingOverlayAction.value = PendingOverlayAction.LiveAction(
            workflowId = confirmed.destination.workflowId,
            listPath = confirmed.destination.listPath,
            candidate = confirmed.candidate,
        )
        stopLiveAction(returnToEditor = true)
    }

    private fun cancelLiveActionLaunch() {
        liveActionLaunchJob?.cancel()
        liveActionLaunchJob = null
    }

    private fun stopLiveAction(returnToEditor: Boolean = false) {
        cancelLiveActionLaunch()
        liveImageCaptureJob?.cancel()
        liveImageCaptureJob = null
        stopLiveImageCrop()
        stopLiveCoordinatePicker()
        liveActionView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        liveActionView = null
        liveActionTargetPackage = null
        liveActionStatusMessage = null
        liveActionSession.cancel()
        if (returnToEditor) returnToEditor()
    }

    private fun showElementInspectorOverlay(inspection: ElementInspection? = null): Boolean {
        elementInspectorView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        val details = TextView(this).apply {
            text = inspection?.let(::formatElementInspection)
                ?: getString(R.string.element_inspector_ready, monitoredTargetPackage.orEmpty())
            setPadding(24, 16, 24, 12)
        }
        val pickButton = Button(this).apply {
            setText(R.string.element_inspector_pick)
            contentDescription = getString(R.string.element_inspector_pick_description)
            setOnClickListener { armElementPicker() }
        }
        val closeButton = Button(this).apply {
            setText(R.string.close)
            setOnClickListener { stopElementInspector(returnToEditor = true) }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pickButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
            inspection?.selector?.takeIf { inspection.canUseSelector }?.let { selector ->
                addView(
                    Button(this@AutomationAccessibilityService).apply {
                        setText(R.string.element_inspector_use_selector)
                        contentDescription = getString(R.string.element_inspector_use_selector_description)
                        setOnClickListener {
                            inspectedSelectorHandoff.publish(selector)
                            stopElementInspector(returnToEditor = true, preservePendingSelector = true)
                        }
                    },
                    LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            addView(closeButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F7F7.toInt())
            elevation = 12f
            addView(details)
            addView(actions)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        return runCatching {
            overlayWindowManager.addView(panel, params)
            elementInspectorView = panel
            overlayStatus.value = null
        }.isSuccess
    }

    private fun armElementPicker() {
        val targetPackage = monitoredTargetPackage ?: return
        val capture = mergeRecordingHierarchyCaptures(
            captures = packageWindowRoots(targetPackage).map { root ->
                root.toRecordingHierarchyNodes(MAX_RECORDING_SNAPSHOT_NODES)
            },
            limit = MAX_RECORDING_SNAPSHOT_NODES,
        )
        if (capture.nodes.isEmpty()) {
            showElementInspectorMessage(getString(R.string.element_inspector_hierarchy_unavailable))
            return
        }
        elementInspectionCapture = capture
        elementInspectorView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        elementInspectorView = null
        val picker = TextView(this).apply {
            text = getString(R.string.element_inspector_tap_instruction)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x22000000)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val inspection = elementInspectionCapture?.let {
                        inspectElementAt(it, event.rawX.toInt(), event.rawY.toInt())
                    }
                    stopElementPickOverlay()
                    if (inspection == null) {
                        showElementInspectorMessage(getString(R.string.element_inspector_no_element))
                    } else {
                        showElementInspectorOverlay(inspection)
                    }
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        runCatching {
            overlayWindowManager.addView(picker, params)
            elementPickView = picker
        }.onFailure {
            elementInspectionCapture = null
            showElementInspectorMessage(getString(R.string.element_inspector_start_failed))
        }
    }

    private fun showElementInspectorMessage(message: String) {
        showElementInspectorOverlay()
        (elementInspectorView as? LinearLayout)?.getChildAt(0)?.let { (it as? TextView)?.text = message }
    }

    private fun formatElementInspection(inspection: ElementInspection): String {
        val node = inspection.node
        val unavailable = getString(R.string.element_monitor_not_available)
        val reliability = when (inspection.selectorReliability) {
            ElementSelectorReliability.Unique -> getString(R.string.element_inspector_selector_unique)
            ElementSelectorReliability.Ambiguous -> getString(
                R.string.element_inspector_selector_ambiguous,
                inspection.selectorMatchCount,
            )
            ElementSelectorReliability.Unavailable -> getString(R.string.element_inspector_selector_unavailable)
            ElementSelectorReliability.HierarchyIncomplete ->
                getString(R.string.element_inspector_hierarchy_incomplete)
        }
        val ancestorCandidates = inspection.ancestorCandidates
            .joinToString(separator = "\n") { it.toString() }
            .ifBlank { unavailable }
        return getString(
            R.string.element_inspector_result,
            node.packageName,
            node.viewId ?: unavailable,
            node.text ?: unavailable,
            node.contentDescription ?: unavailable,
            node.className ?: unavailable,
            node.bounds,
            localizedBoolean(node.clickable),
            localizedBoolean(node.enabled),
            localizedBoolean(node.longClickable),
            localizedBoolean(node.scrollable),
            inspection.selector?.toString() ?: unavailable,
            reliability,
            ancestorCandidates,
        )
    }

    private fun localizedBoolean(value: Boolean): String = getString(
        if (value) R.string.value_yes else R.string.value_no,
    )

    private fun stopElementPickOverlay() {
        elementPickView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        elementPickView = null
        elementInspectionCapture = null
    }

    private fun stopElementInspector(
        returnToEditor: Boolean = false,
        preservePendingSelector: Boolean = false,
    ) {
        stopElementPickOverlay()
        elementInspectorView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        elementInspectorView = null
        monitoredTargetPackage = null
        if (!preservePendingSelector) inspectedSelectorHandoff.clear()
        if (returnToEditor) returnToEditor()
    }

    private fun showRecordingOverlay(): Boolean {
        elementMonitorView?.let { staleView ->
            runCatching { overlayWindowManager.removeView(staleView) }
        }
        val windowManager = overlayWindowManager
        val details = TextView(this).apply {
            text = getString(
                R.string.click_recording_choose_recent_app,
                monitoredTargetPackage.orEmpty(),
            )
            setPadding(24, 16, 24, 12)
        }
        val stopButton = Button(this).apply {
            setText(R.string.click_recording_stop)
        }
        val cancelButton = Button(this).apply {
            setText(R.string.cancel)
            setOnClickListener { cancelElementRecording(returnToEditor = true) }
        }
        val backButton = Button(this).apply {
            setText(R.string.record_action_back)
            setOnClickListener { recordSystemAction(SystemAction.Back, GLOBAL_ACTION_BACK) }
        }
        val homeButton = Button(this).apply {
            setText(R.string.record_action_home)
            setOnClickListener { recordSystemAction(SystemAction.Home, GLOBAL_ACTION_HOME) }
        }
        val swipeButton = Button(this).apply {
            setText(R.string.record_action_swipe)
            setOnClickListener { startSwipeCapture() }
        }
        val launchButton = Button(this).apply {
            setText(R.string.record_action_launch_app)
            setOnClickListener { showRecordingAppPicker() }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(stopButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
            addView(cancelButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        }
        val systemActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(backButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
            addView(homeButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
            addView(swipeButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
            addView(launchButton, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F7F7.toInt())
            elevation = 12f
            addView(
                details,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                actions,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                systemActions,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        stopButton.setOnClickListener { finishElementRecording() }
        panel.tag = ElementMonitorViews(details)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
        }
        return try {
            windowManager.addView(panel, params)
            elementMonitorView = panel
            overlayStatus.value = null
            true
        } catch (_: RuntimeException) {
            elementMonitorView = null
            false
        }
    }

    private fun recordSystemAction(action: SystemAction, globalAction: Int) {
        if (!recordedClickSession.isActive()) return
        recordedClickSession.closeAllTextBursts()
        if (performGlobalAction(globalAction) && recordedClickSession.recordStep(
                Step.GlobalAction(UUID.randomUUID().toString(), action),
            )
        ) {
            updateRecordingOverlay()
        }
    }

    private fun startSwipeCapture() {
        if (swipeCaptureView != null) return
        recordedClickSession.closeAllTextBursts()
        var startX = 0
        var startY = 0
        var startTime = 0L
        val captureView = TextView(this).apply {
            text = getString(R.string.record_swipe_instruction)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x66000000)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX.toInt()
                        startY = event.rawY.toInt()
                        startTime = event.eventTime
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val endX = event.rawX.toInt()
                        val endY = event.rawY.toInt()
                        val duration = (event.eventTime - startTime).coerceIn(1L, 10_000L)
                        if (hypot((endX - startX).toDouble(), (endY - startY).toDouble()) >=
                            MIN_RECORDED_SWIPE_DISTANCE_PX
                        ) {
                            swipeInFlight = true
                            text = getString(R.string.record_swipe_executing)
                            isEnabled = false
                            suppressScrollUntilMillis = android.os.SystemClock.uptimeMillis() + duration +
                                SWIPE_SCROLL_SUPPRESSION_PADDING_MILLIS
                            serviceScope.launch {
                                val succeeded = swipe(startX, startY, endX, endY, duration)
                                if (succeeded &&
                                    recordedClickSession.recordStep(
                                        Step.Swipe(
                                            UUID.randomUUID().toString(),
                                            startX,
                                            startY,
                                            endX,
                                            endY,
                                            duration,
                                        ),
                                    )
                                ) {
                                    updateRecordingOverlay()
                                }
                                swipeInFlight = false
                                stopSwipeCapture()
                                if (recordedClickSession.isActive()) showRecordingOverlay()
                                if (finishAfterSwipe) {
                                    finishAfterSwipe = false
                                    finishElementRecording()
                                }
                            }
                        } else {
                            stopSwipeCapture()
                            showRecordingOverlay()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        stopSwipeCapture()
                        showRecordingOverlay()
                        true
                    }
                    else -> true
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        runCatching {
            overlayWindowManager.addView(captureView, params)
            swipeCaptureView = captureView
        }
    }

    private fun stopSwipeCapture() {
        swipeCaptureView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        swipeCaptureView = null
    }

    private fun showRecordingAppPicker() {
        if (appPickerView != null) return
        recordedClickSession.closeAllTextBursts()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        LaunchableAppCatalog(this).load().forEach { app ->
            list.addView(
                Button(this).apply {
                    text = getString(R.string.record_launch_app_item, app.label, app.packageName)
                    setOnClickListener {
                        stopRecordingAppPicker()
                        serviceScope.launch {
                            val launched = launchApp(app.packageName, null)
                            if (launched) {
                                monitoredTargetPackage = app.packageName
                                rememberExternalAppPackage(app.packageName)
                                recordingTargetResolver.clear()
                                lastScrollPositions.clear()
                                lastRecordedScroll = null
                                startRecordingForeground(app.packageName)
                            }
                            if (launched &&
                                recordedClickSession.recordStep(
                                    Step.LaunchApp(UUID.randomUUID().toString(), app.packageName),
                                )
                            ) {
                                updateRecordingOverlay()
                            }
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        list.addView(
            Button(this).apply {
                setText(R.string.cancel)
                setOnClickListener { stopRecordingAppPicker() }
            },
        )
        val picker = ScrollView(this).apply {
            setBackgroundColor(0xFFF7F7F7.toInt())
            addView(list)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.7f).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        runCatching {
            overlayWindowManager.addView(picker, params)
            appPickerView = picker
        }
    }

    private fun stopRecordingAppPicker() {
        appPickerView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        appPickerView = null
    }

    fun stopElementMonitor(
        preservePendingSelector: Boolean = false,
        stopForegroundService: Boolean = true,
    ) {
        stopElementInspector(preservePendingSelector = preservePendingSelector)
        stopSwipeCapture()
        stopRecordingAppPicker()
        elementMonitorView?.let { view ->
            runCatching { overlayWindowManager.removeView(view) }
        }
        elementMonitorView = null
        monitoredTargetPackage = null
        recordingWorkflowId = null
        recordingListPath = null
        recordingSnapshotJob?.cancel()
        recordingSnapshotJob = null
        recordingTargetResolver.clear()
        recordedClickSession.cancel()
        if (stopForegroundService) stopRecordingForeground()
        swipeInFlight = false
        finishAfterSwipe = false
    }

    fun stopRecording() {
        if (recordingWorkflowId != null) finishElementRecording()
    }

    private fun recordClickTarget(
        node: ObservedNode,
        selector: NodeSelector? = null,
        allowRecommendedSelector: Boolean = true,
        fallbackCause: RecordedClickFallbackCause? = null,
    ): Boolean {
        val target = createRecordedClickTarget(
            node,
            selector,
            allowRecommendedSelector,
            fallbackCause,
        ) ?: return false
        if (!recordedClickSession.record(target)) return false
        updateRecordingOverlay()
        return true
    }

    private fun recordClickIssue(event: AccessibilityEvent, reason: RecordingIssueReason) {
        recordedClickSession.recordIssue(
            RecordingIssue(event.eventTime, event.packageName?.toString().orEmpty(), reason),
        )
        updateRecordingOverlay()
    }

    private fun updateRecordingOverlay() {
        (elementMonitorView?.tag as? ElementMonitorViews)?.details?.text = getString(
                R.string.click_recording_status,
                recordedClickSession.count,
                recordedClickSession.issueCount,
            )
        monitoredTargetPackage?.let { targetPackage ->
            startRecordingForeground(
                targetPackage,
                recordedClickSession.count,
                recordedClickSession.issueCount,
            )
        }
    }

    private fun scheduleRecordingSnapshot(
        packageName: String,
        windowId: Int?,
        eventTimeMillis: Long?,
    ) {
        recordingSnapshotJob?.cancel()
        recordingSnapshotJob = serviceScope.launch {
            delay(RECORDING_SNAPSHOT_SETTLE_MILLIS)
            captureRecordingSnapshot(packageName, windowId)
        }
    }

    private fun captureRecordingSnapshot(packageName: String, windowId: Int?) {
        if (monitoredTargetPackage != packageName || elementMonitorView == null) return
        val roots = packageWindowRoots(packageName, windowId)
        val targetRoots = if (windowId == null) roots else roots.take(1)
        targetRoots.forEach { root ->
            val hierarchy = root.toRecordingHierarchyNodes(MAX_RECORDING_SNAPSHOT_NODES)
            if (hierarchy.nodes.isNotEmpty()) {
                recordingTargetResolver.update(
                    RecordingHierarchySnapshot(
                        packageName = packageName,
                        windowId = root.windowId,
                        eventTimeMillis = android.os.SystemClock.uptimeMillis(),
                        nodes = hierarchy.nodes,
                        complete = hierarchy.complete,
                    ),
                )
            }
        }
    }

    private fun finishElementRecording() {
        if (swipeInFlight) {
            finishAfterSwipe = true
            return
        }
        val batch = recordedClickSession.finish()
        val workflowId = recordingWorkflowId
        val listPath = recordingListPath
        if (batch.actions.isNotEmpty() || batch.issues.isNotEmpty()) {
            if (workflowId != null && listPath != null) {
                pendingOverlayAction.value = PendingOverlayAction.RecordedClicks(
                    workflowId = workflowId,
                    listPath = listPath,
                    actions = batch.actions,
                    issues = batch.issues,
                )
            }
        }
        stopElementMonitor()
        returnToEditor()
    }

    private fun cancelElementRecording(returnToEditor: Boolean) {
        stopElementMonitor()
        if (returnToEditor) returnToEditor()
    }

    private fun rememberExternalAppPackage(packageName: String) {
        lastExternalAppPackage = packageName
        targetPreferences.edit().putString(LAST_EXTERNAL_PACKAGE_KEY, packageName).apply()
    }

    private fun clearExternalAppPackage() {
        lastExternalAppPackage = null
        targetPreferences.edit().remove(LAST_EXTERNAL_PACKAGE_KEY).apply()
    }

    fun clearScreenCapture() {
        screenCaptureRequestId++
        screenCaptureSettleJob?.cancel()
        screenCaptureSettleJob = null
        screenCaptureTimeoutJob?.cancel()
        screenCaptureTimeoutJob = null
        (screenCaptureState.value as? ScreenCaptureState.Ready)?.bitmap?.recycle()
        screenCaptureState.value = ScreenCaptureState.Idle
    }

    fun cancelPendingScreenCapture() {
        if (screenCaptureState.value is ScreenCaptureState.Armed) clearScreenCapture()
    }

    private fun handleArmedScreenCapture(event: AccessibilityEvent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (screenCaptureState.value !is ScreenCaptureState.Armed) return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != lastExternalAppPackage) return
        val requestId = screenCaptureRequestId
        screenCaptureSettleJob?.cancel()
        screenCaptureSettleJob = serviceScope.launch {
            delay(SCREEN_CAPTURE_SETTLE_MILLIS)
            if (requestId != screenCaptureRequestId || screenCaptureState.value !is ScreenCaptureState.Armed) {
                return@launch
            }
            if (!isTargetPackageVisible(packageName)) return@launch
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                        if (!isCurrentServiceInstance() || requestId != screenCaptureRequestId ||
                            screenCaptureState.value !is ScreenCaptureState.Armed
                        ) {
                            bitmap?.recycle()
                            return
                        }
                        screenCaptureTimeoutJob?.cancel()
                        if (bitmap == null) {
                            screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_unreadable))
                        } else {
                            screenCaptureState.value = ScreenCaptureState.Ready(
                                bitmap = bitmap,
                                nodes = snapshotCaptureNodes(),
                            )
                        }
                        returnToEditor()
                    }

                    override fun onFailure(errorCode: Int) {
                        if (!isCurrentServiceInstance() || requestId != screenCaptureRequestId ||
                            screenCaptureState.value !is ScreenCaptureState.Armed
                        ) {
                            return
                        }
                        screenCaptureTimeoutJob?.cancel()
                        screenCaptureState.value = ScreenCaptureState.Error(
                            getString(R.string.capture_failed, errorCode),
                        )
                        returnToEditor()
                    }
                },
            )
        }
    }

    private fun snapshotCaptureNodes(): List<CaptureNode> {
        var traversalOrder = 0
        val ownPackageName = applicationContext.packageName
        val targetPackageName = lastExternalAppPackage ?: return emptyList()
        return windows.asSequence()
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == targetPackageName }
            .flatMap { root -> root.depthFirstWithDepth() }
            .mapNotNull { (node, depth) ->
                val packageName = node.packageName?.toString() ?: return@mapNotNull null
                if (packageName == ownPackageName || packageName != targetPackageName) return@mapNotNull null
                val bounds = Rect().also(node::getBoundsInScreen)
                val (text, description) = sanitizedRecordedText(
                    isPassword = node.isPassword,
                    text = node.text.nonBlankString(),
                    contentDescription = node.contentDescription.nonBlankString(),
                )
                CaptureNode(
                    packageName = packageName,
                    viewId = node.viewIdResourceName,
                    text = text,
                    contentDescription = description,
                    className = node.className.nonBlankString(),
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    depth = depth,
                    traversalOrder = traversalOrder++,
                    clickable = node.isClickable,
                )
            }
            .take(MAX_CAPTURE_NODES)
            .toList()
    }

    private fun AccessibilityNodeInfo.depthFirstWithDepth(depth: Int = 0): Sequence<Pair<AccessibilityNodeInfo, Int>> =
        sequence {
            yield(this@depthFirstWithDepth to depth)
            for (index in 0 until childCount) {
                getChild(index)?.let { child -> yieldAll(child.depthFirstWithDepth(depth + 1)) }
            }
        }

    private fun returnToEditor() {
        startActivity(
            FloatingWorkflowEditorActivity.returnIntent(this)
                ?: Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                },
        )
    }

    private fun showRunningNotification(workflowName: String, stepId: String? = null) {
        if (!canPostNotifications()) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                RUNNING_CHANNEL_ID,
                getString(R.string.running_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val stopIntent = Intent(this, StopWorkflowReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextIntent = Intent(this, StopWorkflowReceiver::class.java).apply {
            putExtra(StopWorkflowReceiver.EXTRA_ADVANCE_WORKFLOW, true)
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            this,
            RUNNING_NOTIFICATION_ID + 1,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            RUNNING_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = android.app.Notification.Builder(this, RUNNING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.running_notification_title, workflowName))
            .setContentText(
                stepId?.let { getString(R.string.running_notification_step, it) }
                    ?: getString(R.string.running_notification_preparing),
            )
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
        if (debugPaused.value) {
            builder.addAction(
                android.app.Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_play),
                    getString(R.string.debug_next_step),
                    nextPendingIntent,
                ).build(),
            )
        }
        val notification = builder.addAction(
                android.app.Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    getString(R.string.stop),
                    stopPendingIntent,
                ).build(),
            )
            .build()
        notificationManager.notify(RUNNING_NOTIFICATION_ID, notification)
    }

    private fun startRecordingForeground(
        targetPackage: String,
        recordedCount: Int? = null,
        issueCount: Int? = null,
    ) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                RECORDING_CHANNEL_ID,
                getString(R.string.click_recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val stopIntent = Intent(this, StopWorkflowReceiver::class.java).apply {
            putExtra(StopWorkflowReceiver.EXTRA_STOP_RECORDING, true)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            RECORDING_NOTIFICATION_ID,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showControlsPendingIntent = recordingCommandPendingIntent(
            StopWorkflowReceiver.COMMAND_SHOW_CONTROLS,
            RECORDING_NOTIFICATION_ID,
        )
        val backPendingIntent = recordingCommandPendingIntent(
            StopWorkflowReceiver.COMMAND_RECORD_BACK,
            RECORDING_NOTIFICATION_ID + 1,
        )
        val homePendingIntent = recordingCommandPendingIntent(
            StopWorkflowReceiver.COMMAND_RECORD_HOME,
            RECORDING_NOTIFICATION_ID + 2,
        )
        val notification = android.app.Notification.Builder(this, RECORDING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.click_recording_notification_title))
            .setContentText(
                if (recordedCount != null && issueCount != null) {
                    getString(
                        R.string.click_recording_notification_status,
                        targetPackage,
                        recordedCount,
                        issueCount,
                    )
                } else {
                    getString(R.string.click_recording_notification_text, targetPackage)
                },
            )
            .setContentIntent(showControlsPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .addAction(
                android.app.Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                    getString(R.string.record_action_back),
                    backPendingIntent,
                ).build(),
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_view),
                    getString(R.string.record_action_home),
                    homePendingIntent,
                ).build(),
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    getString(R.string.stop),
                    stopPendingIntent,
                ).build(),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                RECORDING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(RECORDING_NOTIFICATION_ID, notification)
        }
    }

    private fun recordingCommandPendingIntent(command: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, StopWorkflowReceiver::class.java).apply {
            putExtra(StopWorkflowReceiver.EXTRA_RECORDING_COMMAND, command)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun performRecordingCommand(command: String) {
        if (!recordedClickSession.isActive() || recordingWorkflowId == null) return
        when (command) {
            StopWorkflowReceiver.COMMAND_SHOW_CONTROLS -> showRecordingOverlay()
            StopWorkflowReceiver.COMMAND_RECORD_BACK -> recordSystemAction(SystemAction.Back, GLOBAL_ACTION_BACK)
            StopWorkflowReceiver.COMMAND_RECORD_HOME -> recordSystemAction(SystemAction.Home, GLOBAL_ACTION_HOME)
        }
    }

    private fun stopRecordingForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun cancelRunningNotification() {
        if (canPostNotifications()) notificationManager.cancel(RUNNING_NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override suspend fun launchApp(packageName: String, intentAction: String?): Boolean {
        val launchIntent = intentAction?.let { Intent(it).setPackage(packageName) }
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        if (launchIntent.resolveActivity(packageManager) == null) return false
        val started = runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }.isSuccess
        if (!started) return false
        return awaitTargetPackageVisible(
            packageName = packageName,
            isVisible = { targetPackage ->
                rootInActiveWindow?.packageName?.toString() == targetPackage
            },
        )
    }

    override suspend fun click(selector: NodeSelector): Boolean {
        val node = findNode(selector) ?: return false
        return generateSequence(node) { it.parent }
            .firstOrNull { it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ?: false
    }

    override suspend fun clickImage(step: Step.ImageClick): ImageClickResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ImageClickResult.Unsupported
        if (!isTargetPackageVisible(step.packageName)) {
            return ImageClickResult.WrongPackage
        }
        val targetBounds = targetPackageWindowBounds(step.packageName)
        if (targetBounds.isEmpty()) return ImageClickResult.WrongPackage
        val template = decodeImageTemplate(step) ?: return ImageClickResult.MissingOrInvalidTemplate
        val screen = captureBitmapOnce() ?: run {
            template.recycle()
            return ImageClickResult.CaptureFailed
        }
        return try {
            when (val match = withContext(Dispatchers.Default) {
                val matchingContext = currentCoroutineContext()
                matchTemplate(
                    screen.toLumaImage(),
                    template.toLumaImage(),
                    step.minimumScorePermille,
                    step.ambiguityMarginPermille,
                    step.scaleTolerancePermille,
                    checkCancellation = { matchingContext.ensureActive() },
                )
            }) {
                is TemplateMatchResult.Unique -> if (!matchIsInsideTargetWindow(match, targetBounds)) {
                    ImageClickResult.NoMatch
                } else if (tap(match.centerX, match.centerY)) {
                    ImageClickResult.Clicked(match.scorePermille)
                } else {
                    ImageClickResult.GestureFailed
                }
                TemplateMatchResult.NoMatch -> ImageClickResult.NoMatch
                TemplateMatchResult.Ambiguous -> ImageClickResult.Ambiguous
            }
        } finally {
            screen.recycle()
            template.recycle()
        }
    }

    private fun isTargetPackageVisible(packageName: String): Boolean = targetPackageIsVisible(
        targetPackage = packageName,
        activePackage = rootInActiveWindow?.packageName?.toString(),
        windowPackages = windows.mapNotNull { it.root?.packageName?.toString() },
    )

    private fun targetPackageWindowBounds(packageName: String): List<ScreenBounds> = buildList {
        windows.asSequence()
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == packageName }
            .forEach { root ->
                val bounds = Rect().also(root::getBoundsInScreen)
                if (!bounds.isEmpty) add(ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
        rootInActiveWindow
            ?.takeIf { it.packageName?.toString() == packageName }
            ?.let { root ->
                val bounds = Rect().also(root::getBoundsInScreen)
                val screenBounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                if (!bounds.isEmpty && screenBounds !in this) add(screenBounds)
            }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureBitmapOnce(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        screenshot.hardwareBuffer.close()
                    }
                    if (continuation.isActive) continuation.resume(bitmap) else bitmap?.recycle()
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
    }

    private fun Bitmap.toLumaImage(): LumaImage {
        val colors = IntArray(width * height)
        getPixels(colors, 0, width, 0, 0, width, height)
        val luma = ByteArray(colors.size)
        colors.forEachIndexed { index, color ->
            val red = color shr 16 and 0xff
            val green = color shr 8 and 0xff
            val blue = color and 0xff
            luma[index] = ((red * 77 + green * 150 + blue * 29) shr 8).toByte()
        }
        return LumaImage(width, height, luma)
    }

    override suspend fun longClick(selector: NodeSelector): Boolean {
        val node = findNode(selector) ?: return false
        return generateSequence(node) { it.parent }
            .firstOrNull { it.isLongClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            ?: false
    }

    override suspend fun scroll(selector: NodeSelector, direction: ScrollDirection): Boolean {
        val node = findNode(selector) ?: return false
        val action = when (direction) {
            ScrollDirection.Forward -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.Backward -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return generateSequence(node) { it.parent }
            .firstOrNull { candidate ->
                candidate.isScrollable || candidate.actionList.any { it.id == action }
            }
            ?.performAction(action)
            ?: false
    }

    override suspend fun inputText(
        selector: NodeSelector,
        text: String,
        method: TextInputMethod,
    ): Boolean {
        val node = findNode(selector) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return when (method) {
            TextInputMethod.SetText -> {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            TextInputMethod.Paste -> {
                val clipboardManager = getSystemService(ClipboardManager::class.java)
                ClipboardTransaction(
                    AndroidClipboardAdapter(
                        clipboardManager,
                        getString(R.string.temporary_input_clip_label),
                    ),
                ).paste(text) {
                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
            }
        }
    }

    override suspend fun readNodeAttribute(selector: NodeSelector, attribute: NodeAttribute): String? {
        val node = findNode(selector) ?: return null
        return when (attribute) {
            NodeAttribute.TextOrDescription -> node.text.nonBlankString()
                ?: node.contentDescription.nonBlankString()
            NodeAttribute.Text -> node.text.nonBlankString()
            NodeAttribute.ContentDescription -> node.contentDescription.nonBlankString()
            NodeAttribute.ViewId -> node.viewIdResourceName?.takeIf { it.isNotBlank() }
            NodeAttribute.ClassName -> node.className.nonBlankString()
        }
    }

    private fun CharSequence?.nonBlankString(): String? = this?.toString()?.takeIf { it.isNotBlank() }

    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long,
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return dispatchPath(path, durationMillis)
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatchPath(path, TAP_DURATION_MILLIS)
    }

    private suspend fun dispatchPath(path: Path, durationMillis: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMillis))
            .build()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
        if (!dispatchGesture(gesture, callback, null) && continuation.isActive) {
            continuation.resume(false)
        }
        }

    override suspend fun performSystemAction(action: SystemAction): Boolean {
        val globalAction = when (action) {
            SystemAction.Back -> GLOBAL_ACTION_BACK
            SystemAction.Home -> GLOBAL_ACTION_HOME
            SystemAction.Recents -> GLOBAL_ACTION_RECENTS
        }
        return performGlobalAction(globalAction)
    }

    override suspend fun nodeExists(selector: NodeSelector): Boolean = findNode(selector) != null

    fun countMatches(selector: NodeSelector): Int {
        return selectorRoots(selector)
            .flatMap { root -> root.depthFirstSequence() }
            .count { node -> node.matches(selector) }
    }

    private fun findNode(selector: NodeSelector): AccessibilityNodeInfo? {
        return selectorRoots(selector)
            .flatMap { root -> root.depthFirstSequence() }
                .filter { node -> node.matches(selector) }
                .drop(selector.matchIndex)
                .firstOrNull()
    }

    private fun selectorRoots(selector: NodeSelector): Sequence<AccessibilityNodeInfo> =
        if (selectorUsesActiveWindow(selector.packageName)) {
            rootInActiveWindow?.let { sequenceOf(it) } ?: emptySequence()
        } else {
            packageWindowRoots(selector.packageName).asSequence()
        }

    private fun packageWindowRoots(
        packageName: String,
        preferredWindowId: Int? = null,
    ): List<AccessibilityNodeInfo> {
        val interactiveWindows = windows
        val roots = linkedMapOf<Int, AccessibilityNodeInfo>()
        fun addRoot(root: AccessibilityNodeInfo?) {
            if (root?.packageName?.toString() == packageName) roots.putIfAbsent(root.windowId, root)
        }
        if (preferredWindowId != null) {
            addRoot(interactiveWindows.firstOrNull { it.id == preferredWindowId }?.root)
        }
        addRoot(rootInActiveWindow)
        interactiveWindows.forEach { addRoot(it.root) }
        return roots.values.toList()
    }

    private fun AccessibilityNodeInfo.depthFirstSequence(): Sequence<AccessibilityNodeInfo> = sequence {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(this@depthFirstSequence)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            yield(node)
            for (index in node.childCount - 1 downTo 0) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
    }

    private fun AccessibilityNodeInfo.toRecordingHierarchyNodes(
        limit: Int,
    ): RecordingHierarchyCapture {
        val result = mutableListOf<RecordingHierarchyNode>()
        val pending = ArrayDeque<Pair<AccessibilityNodeInfo, Int?>>()
        pending.addLast(this to null)
        while (pending.isNotEmpty() && result.size < limit) {
            val (node, parentIndex) = pending.removeLast()
            val descriptor = node.toDescriptor()
            val currentIndex = if (descriptor != null) {
                result.size.also { result += RecordingHierarchyNode(descriptor, parentIndex) }
            } else {
                parentIndex
            }
            for (index in node.childCount - 1 downTo 0) {
                node.getChild(index)?.let { child -> pending.addLast(child to currentIndex) }
            }
        }
        return RecordingHierarchyCapture(result, complete = pending.isEmpty())
    }

    private fun AccessibilityNodeInfo.matches(selector: NodeSelector): Boolean {
        val targetMatches =
            (selector.packageName.isBlank() || packageName?.toString() == selector.packageName) &&
            selector.viewId.matchesIfPresent(viewIdResourceName) &&
            selector.text.matchesIfPresent(text?.toString(), selector.textMatchMode) &&
            selector.contentDescription.matchesIfPresent(
                contentDescription?.toString(),
                selector.contentDescriptionMatchMode,
            ) &&
            selector.className.matchesIfPresent(className?.toString())
        return targetMatches && selector.ancestor?.let { hasMatchingAncestor(it) } != false
    }

    private fun AccessibilityNodeInfo.hasMatchingAncestor(selector: AncestorSelector): Boolean {
        var ancestor = parent
        while (ancestor != null) {
            if (selector.matches(ancestor.toMatchSnapshot())) return true
            ancestor = ancestor.parent
        }
        return false
    }

    private fun AccessibilityNodeInfo.toMatchSnapshot(): NodeMatchSnapshot = NodeMatchSnapshot(
        viewId = viewIdResourceName,
        text = text?.toString(),
        contentDescription = contentDescription?.toString(),
        className = className?.toString(),
    )

    private fun String?.matchesIfPresent(actual: String?): Boolean = this == null || this == actual

    private fun String?.matchesIfPresent(actual: String?, mode: TextMatchMode): Boolean =
        mode.matches(this, actual)

    private fun AccessibilityNodeInfo.toDescriptor(): ObservedNode? {
        val packageName = packageName?.toString() ?: return null
        val viewId = viewIdResourceName
        val (text, description) = sanitizedRecordedText(
            isPassword = isPassword,
            text = text?.toString()?.takeIf { it.isNotBlank() },
            contentDescription = contentDescription?.toString()?.takeIf { it.isNotBlank() },
        )
        val className = className?.toString()?.takeIf { it.isNotBlank() }
        val bounds = Rect().also(::getBoundsInScreen)
        return ObservedNode(
            packageName = packageName,
            viewId = viewId,
            text = text,
            contentDescription = description,
            className = className,
            bounds = bounds.flattenToString(),
            clickable = isClickable,
            enabled = isEnabled,
            longClickable = isLongClickable,
            scrollable = isScrollable,
        )
    }

    companion object {
        private const val MAX_OBSERVED_NODES = 1_000
        private const val MAX_CAPTURE_NODES = 1_000
        private const val OBSERVATION_SETTLE_MILLIS = 300L
        private const val SCREEN_CAPTURE_SETTLE_MILLIS = 450L
        private const val SCREEN_CAPTURE_TIMEOUT_MILLIS = 15_000L
        private const val MAX_RECORDED_CLICKS = 1_000
        private const val MAX_RECORDING_SNAPSHOT_NODES = 1_000
        private const val MAX_RECORDING_WINDOW_SNAPSHOTS = 8
        private const val RECORDING_SNAPSHOT_SETTLE_MILLIS = 120L
        private const val LONG_CLICK_CLICK_SUPPRESSION_MILLIS = 350L
        private const val SCROLL_DUPLICATE_SUPPRESSION_MILLIS = 250L
        private const val SWIPE_SCROLL_SUPPRESSION_PADDING_MILLIS = 300L
        private const val MIN_RECORDED_SWIPE_DISTANCE_PX = 24.0
        private const val LIVE_CAPTURE_OVERLAY_HIDE_MILLIS = 200L
        private const val LIVE_TARGET_LAUNCH_SETTLE_MILLIS = 450L
        private const val TARGET_PREFERENCES_NAME = "automation_target"
        private const val LAST_EXTERNAL_PACKAGE_KEY = "last_external_package"
        private const val RUNNING_CHANNEL_ID = "workflow_execution"
        private const val RUNNING_NOTIFICATION_ID = 1001
        private const val RECORDING_CHANNEL_ID = "click_recording"
        private const val RECORDING_NOTIFICATION_ID = 1002
        private const val TAP_DURATION_MILLIS = 50L
        private val mutableConnected = MutableStateFlow(false)
        val connected = mutableConnected.asStateFlow()
        val observedNodes = MutableStateFlow<List<ObservedNode>>(emptyList())
        val screenCaptureState = MutableStateFlow<ScreenCaptureState>(ScreenCaptureState.Idle)
        val pendingOverlayAction = MutableStateFlow<PendingOverlayAction?>(null)
        val overlayStatus = MutableStateFlow<String?>(null)
        val floatingEditorRestoreVisible = MutableStateFlow(false)
        private val inspectedSelectorHandoff = InspectedSelectorHandoff()
        val inspectedSelector = inspectedSelectorHandoff.selector
        private val observationController = AccessibilityObservationController(
            onObservationEnded = {
                instance?.cancelPendingObservationCapture()
                observedNodes.value = emptyList()
            },
            onSourceUnavailable = {
                instance?.cancelPendingObservationCapture()
            },
        )
        val currentStepId = MutableStateFlow<String?>(null)
        val debugPaused = MutableStateFlow(false)
        val runningWorkflowId = MutableStateFlow<String?>(null)
        val workflowStartedAtMillis = MutableStateFlow<Long?>(null)
        val latestRun = MutableStateFlow<RunOutcome?>(null)

        private val instanceOwner = CurrentInstanceOwner<AutomationAccessibilityService>()
        val instance: AutomationAccessibilityService?
            get() = instanceOwner.get()

        internal fun acquireObservationLease(): AutoCloseable =
            observationController.acquire()

        fun discardScreenCapture() {
            instance?.clearScreenCapture() ?: run {
                (screenCaptureState.value as? ScreenCaptureState.Ready)?.bitmap?.recycle()
                screenCaptureState.value = ScreenCaptureState.Idle
            }
        }

        fun cancelPendingScreenCapture() {
            instance?.cancelPendingScreenCapture() ?: run {
                if (screenCaptureState.value is ScreenCaptureState.Armed) {
                    screenCaptureState.value = ScreenCaptureState.Idle
                }
            }
        }

        fun consumeInspectedSelector(): NodeSelector? = inspectedSelectorHandoff.consume()

        fun consumePendingOverlayAction(action: PendingOverlayAction) {
            if (pendingOverlayAction.value == action) pendingOverlayAction.value = null
        }
    }
}

internal fun selectorUsesActiveWindow(packageName: String): Boolean = packageName.isBlank()

internal suspend fun awaitTargetPackageVisible(
    packageName: String,
    isVisible: (String) -> Boolean,
    maxChecks: Int = 50,
    pollIntervalMillis: Long = 100L,
): Boolean {
    repeat(maxChecks) { checkIndex ->
        if (isVisible(packageName)) return true
        if (checkIndex < maxChecks - 1) delay(pollIntervalMillis)
    }
    return false
}

private data class ElementMonitorViews(
    val details: TextView,
)

internal fun sanitizedRecordedText(
    isPassword: Boolean,
    text: String?,
    contentDescription: String?,
): Pair<String?, String?> = if (isPassword) null to null else text to contentDescription

internal fun targetAppLaunchFlags(initialFlags: Int): Int =
    (initialFlags or Intent.FLAG_ACTIVITY_NEW_TASK) and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT.inv()

internal fun isEligibleExternalPackage(
    packageName: String,
    ownPackageName: String,
    homePackages: Set<String>,
): Boolean = packageName != ownPackageName &&
    packageName != "com.android.systemui" &&
    packageName !in homePackages

internal fun createRecordedClickTarget(
    node: ObservedNode,
    recoveredSelector: NodeSelector? = null,
    allowRecommendedSelector: Boolean = true,
    fallbackCause: RecordedClickFallbackCause? = null,
): RecordedClickTarget? {
    if (!node.enabled) return null
    val values = node.bounds.split(' ').mapNotNull(String::toIntOrNull)
    if (values.size != 4) return null
    val bounds = runCatching { RecordedBounds(values[0], values[1], values[2], values[3]) }.getOrNull()
        ?: return null
    return RecordedClickTarget(
        x = bounds.left + (bounds.right - bounds.left) / 2,
        y = bounds.top + (bounds.bottom - bounds.top) / 2,
        selector = recoveredSelector ?: SelectorRecommendations.candidates(node)
            .firstOrNull()
            ?.takeIf { allowRecommendedSelector },
        fallbackCause = fallbackCause,
        control = RecordedControl(
            packageName = node.packageName,
            viewId = node.viewId,
            text = node.text,
            contentDescription = node.contentDescription,
            className = node.className,
            bounds = bounds,
            clickable = node.clickable,
            enabled = node.enabled,
            longClickable = node.longClickable,
            scrollable = node.scrollable,
        ),
    )
}

data class RecordedClickTarget(
    val x: Int,
    val y: Int,
    val selector: NodeSelector?,
    val fallbackCause: RecordedClickFallbackCause? = null,
    val control: RecordedControl,
)

internal class RecordedClickSession(private val capacity: Int) {
    private var recording = false
    private val actions = mutableListOf<RecordedAction>()
    private val issues = mutableListOf<RecordingIssue>()
    private val textActionIndexes = mutableMapOf<String, Int>()
    private var activeTextKey: String? = null

    val count: Int get() = actions.size
    val issueCount: Int get() = issues.size
    fun isActive(): Boolean = recording

    init {
        require(capacity > 0) { "Recording capacity must be positive" }
    }

    fun start() {
        actions.clear()
        issues.clear()
        textActionIndexes.clear()
        activeTextKey = null
        recording = true
    }

    fun record(target: RecordedClickTarget): Boolean {
        return recordAction(RecordedAction.Click(target))
    }

    fun recordStep(step: Step): Boolean = recordAction(RecordedAction.ExistingStep(step))

    fun recordOrReplaceText(key: String, step: Step.InputText): Boolean {
        if (!recording) return false
        if (activeTextKey != key) {
            closeTextBurst(activeTextKey)
            activeTextKey = key
        }
        val existingIndex = textActionIndexes[key]
        if (existingIndex != null) {
            actions[existingIndex] = RecordedAction.ExistingStep(step)
            return true
        }
        if (actions.size >= capacity) return false
        textActionIndexes[key] = actions.size
        actions += RecordedAction.ExistingStep(step)
        return true
    }

    fun closeTextBurst(key: String?) {
        if (key != null) {
            textActionIndexes.remove(key)
            if (activeTextKey == key) activeTextKey = null
        }
    }

    fun closeAllTextBursts() {
        textActionIndexes.clear()
        activeTextKey = null
    }

    private fun recordAction(action: RecordedAction): Boolean {
        if (!recording || actions.size >= capacity) return false
        actions += action
        return true
    }

    fun recordIssue(issue: RecordingIssue): Boolean {
        if (!recording || issues.size >= capacity) return false
        issues += issue
        return true
    }

    fun finish(): RecordedClickBatch {
        if (!recording) return RecordedClickBatch(emptyList(), emptyList())
        recording = false
        return RecordedClickBatch(actions.toList(), issues.toList()).also {
            actions.clear()
            issues.clear()
            textActionIndexes.clear()
            activeTextKey = null
        }
    }

    fun cancel() {
        recording = false
        actions.clear()
        issues.clear()
        textActionIndexes.clear()
        activeTextKey = null
    }
}

data class RecordedClickBatch(
    val actions: List<RecordedAction>,
    val issues: List<RecordingIssue>,
)

sealed interface RecordedAction {
    data class Click(val target: RecordedClickTarget) : RecordedAction
    data class ExistingStep(val step: Step) : RecordedAction
}

sealed interface PendingOverlayAction {
    data class RecordedClicks(
        val workflowId: String,
        val listPath: StepListPath,
        val actions: List<RecordedAction>,
        val issues: List<RecordingIssue>,
    ) : PendingOverlayAction

    data class LiveAction(
        val workflowId: String,
        val listPath: StepListPath,
        val candidate: LiveActionCandidate,
    ) : PendingOverlayAction
}

sealed interface ScreenCaptureState {
    data object Idle : ScreenCaptureState
    data object Armed : ScreenCaptureState
    data class Ready(val bitmap: Bitmap, val nodes: List<CaptureNode>) : ScreenCaptureState
    data class Error(val message: String) : ScreenCaptureState
}

data class RunOutcome(
    val result: RunResult,
    val record: RunRecord,
)

data class ObservedNode(
    val packageName: String,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: String,
    val clickable: Boolean,
    val enabled: Boolean,
    val longClickable: Boolean = false,
    val scrollable: Boolean = false,
)

internal fun Sequence<ObservedNode>.distinctObservedNodes(): Sequence<ObservedNode> = distinctBy {
    it.snapshotIdentity()
}

internal fun mergeObservedNodeWindows(
    windows: List<Sequence<ObservedNode>>,
    limit: Int,
): List<ObservedNode> {
    require(limit >= 0) { "Node limit cannot be negative" }
    val iterators = windows.mapIndexed { windowIndex, nodes -> windowIndex to nodes.iterator() }.toMutableList()
    val identities = mutableSetOf<Pair<Int, List<String?>>>()
    val result = mutableListOf<ObservedNode>()
    while (iterators.isNotEmpty() && result.size < limit) {
        var index = 0
        while (index < iterators.size && result.size < limit) {
            val (windowIndex, iterator) = iterators[index]
            if (!iterator.hasNext()) {
                iterators.removeAt(index)
                continue
            }
            val node = iterator.next()
            if (identities.add(windowIndex to node.snapshotIdentity())) result += node
            index += 1
        }
    }
    return result
}

private fun ObservedNode.snapshotIdentity(): List<String?> =
    listOf(viewId, text, contentDescription, className, bounds)
