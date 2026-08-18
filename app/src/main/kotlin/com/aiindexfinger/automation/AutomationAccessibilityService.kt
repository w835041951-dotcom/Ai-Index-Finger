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
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.aiindexfinger.localizedName
import com.aiindexfinger.executor.AutomationDriver
import com.aiindexfinger.executor.ImageClickResult
import com.aiindexfinger.executor.GestureActionResult
import com.aiindexfinger.executor.NodeActionResult
import com.aiindexfinger.executor.NodeReadResult
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.executor.RunState
import com.aiindexfinger.executor.WorkflowExecutor
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.RunStepLocation
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.data.toRunRecord
import com.aiindexfinger.data.withControlNotificationCancellation
import com.aiindexfinger.data.uniqueRunLocationTo
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
import com.aiindexfinger.scheduler.ScheduleNotificationReadiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.hypot

internal const val RUNNING_NOTIFICATION_CHANNEL_ID = "workflow_execution"
internal const val EXTRA_RUN_RECORD_ID = "com.aiindexfinger.extra.RUN_RECORD_ID"

internal enum class WorkflowStartResult {
    Started,
    NotReady,
    AlreadyRunning,
    ControlsUnavailable,
    ServiceUnavailable,
}

internal class WorkflowJobOwnership {
    private var owner: Job? = null

    @Synchronized
    fun isOccupied(): Boolean = owner != null

    @Synchronized
    fun claim(candidate: Job): Boolean {
        if (owner != null) return false
        owner = candidate
        return true
    }

    @Synchronized
    fun current(): Job? = owner

    @Synchronized
    fun owns(candidate: Job): Boolean = owner === candidate

    @Synchronized
    fun release(candidate: Job): Boolean {
        if (owner !== candidate) return false
        owner = null
        return true
    }
}

internal fun startWorkflowWatchdogIfOwned(
    ownership: WorkflowJobOwnership,
    owner: Job,
    watchdog: Job,
): Boolean {
    if (!ownership.owns(owner)) {
        watchdog.cancel()
        return false
    }
    return watchdog.start()
}

internal fun runningControlsAvailable(
    readiness: ScheduleNotificationReadiness,
    notificationActive: Boolean,
): Boolean = readiness == ScheduleNotificationReadiness.Ready && notificationActive

internal fun runningNotificationReadiness(context: Context): ScheduleNotificationReadiness {
    val notificationManager = context.getSystemService(NotificationManager::class.java).also { manager ->
        manager.createNotificationChannel(
            NotificationChannel(
                RUNNING_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.running_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
    val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val channelImportance = notificationManager
        .getNotificationChannel(RUNNING_NOTIFICATION_CHANNEL_ID)
        ?.importance
        ?: NotificationManager.IMPORTANCE_NONE
    return com.aiindexfinger.scheduler.scheduleNotificationReadiness(
        runtimePermissionGranted,
        notificationManager.areNotificationsEnabled(),
        channelImportance,
    )
}

internal fun openRunningNotificationSettings(
    context: Context,
    readiness: ScheduleNotificationReadiness,
): Boolean {
    val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    val channelSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, RUNNING_NOTIFICATION_CHANNEL_ID)
    }
    val applicationDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
    val intents = if (readiness == ScheduleNotificationReadiness.ChannelDisabled) {
        listOf(channelSettings, appSettings, applicationDetails)
    } else {
        listOf(appSettings, applicationDetails)
    }
    return intents.any { intent ->
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.isSuccess
    }
}

internal fun targetPackageIsVisible(
    targetPackage: String,
    activePackage: String?,
    windowPackages: Iterable<String>,
): Boolean = activePackage == targetPackage || targetPackage in windowPackages

internal fun accessibilityServiceFlags(existingFlags: Int): Int = existingFlags or
    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal fun gesturePointsAreInsideDisplay(
    bounds: ScreenBounds,
    vararg points: ScreenPoint,
): Boolean = points.all { point ->
    point.x >= bounds.left && point.x < bounds.right &&
        point.y >= bounds.top && point.y < bounds.bottom
}

private data class CapturedScreen(
    val bitmap: Bitmap,
    val screenBounds: ScreenBounds,
)

private data class TargetWindowSnapshot(
    val windowId: Int,
    val bounds: ScreenBounds,
)

internal fun pointIsInsideTargetWindow(
    point: ScreenPoint,
    targetBounds: List<ScreenBounds>,
): Boolean = targetBounds.any { bounds ->
    point.x >= bounds.left && point.x < bounds.right &&
        point.y >= bounds.top && point.y < bounds.bottom
}

internal fun matchIsInsideTargetWindow(
    match: TemplateMatchResult.Unique,
    targetBounds: List<ScreenBounds>,
): Boolean = pointIsInsideTargetWindow(ScreenPoint(match.centerX, match.centerY), targetBounds)

internal fun mapMatchToTargetScreen(
    match: TemplateMatchResult.Unique,
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenBounds: ScreenBounds,
    targetBounds: List<ScreenBounds>,
    templateWidth: Int = match.width,
    templateHeight: Int = match.height,
    templateClickX: Int? = null,
    templateClickY: Int? = null,
): ScreenPoint? {
    if (match.width <= 0 || match.height <= 0) return null
    val left = match.centerX - match.width / 2
    val top = match.centerY - match.height / 2
    val footprint = ImageCropBounds(
        left = left,
        top = top,
        right = left + match.width,
        bottom = top + match.height,
    )
    if (mapBitmapCropToTargetScreen(
            crop = footprint,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            screenBounds = screenBounds,
            targetBounds = targetBounds,
        ) == null
    ) return null
    val matchPoint = mapTemplateClickToMatch(
        match = match,
        templateWidth = templateWidth,
        templateHeight = templateHeight,
        templateClickX = templateClickX,
        templateClickY = templateClickY,
    ) ?: return null
    return mapBitmapPointToScreen(
        point = matchPoint,
        bitmapWidth = bitmapWidth,
        bitmapHeight = bitmapHeight,
        screenBounds = screenBounds,
    )?.takeIf { pointIsInsideTargetWindow(it, targetBounds) }
}

internal fun mapTemplateClickToMatch(
    match: TemplateMatchResult.Unique,
    templateWidth: Int,
    templateHeight: Int,
    templateClickX: Int?,
    templateClickY: Int?,
): ScreenPoint? {
    if (templateClickX == null && templateClickY == null) {
        return ScreenPoint(match.centerX, match.centerY)
    }
    if (templateClickX == null || templateClickY == null ||
        templateWidth <= 0 || templateHeight <= 0 || match.width <= 0 || match.height <= 0 ||
        templateClickX !in 0 until templateWidth || templateClickY !in 0 until templateHeight
    ) return null
    val matchLeft = match.centerX - match.width / 2
    val matchTop = match.centerY - match.height / 2
    return ScreenPoint(
        x = matchLeft + scaleTemplateCoordinate(templateClickX, templateWidth, match.width),
        y = matchTop + scaleTemplateCoordinate(templateClickY, templateHeight, match.height),
    )
}

private fun scaleTemplateCoordinate(coordinate: Int, sourceSize: Int, targetSize: Int): Int {
    if (sourceSize == 1 || targetSize == 1) return 0
    val denominator = sourceSize - 1
    return ((coordinate.toLong() * (targetSize - 1) + denominator / 2) / denominator).toInt()
}

internal fun mapBitmapCropToTargetScreen(
    crop: ImageCropBounds,
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenBounds: ScreenBounds,
    targetBounds: List<ScreenBounds>,
): ScreenBounds? = mapBitmapCropToScreen(
    crop = crop,
    bitmapWidth = bitmapWidth,
    bitmapHeight = bitmapHeight,
    screenBounds = screenBounds,
)?.takeIf { cropIsInsideTargetWindow(it, targetBounds) }

internal fun cropIsInsideTargetWindow(
    crop: ImageCropBounds,
    targetBounds: List<ScreenBounds>,
): Boolean = targetBounds.any { bounds ->
    crop.left >= bounds.left && crop.top >= bounds.top &&
        crop.right <= bounds.right && crop.bottom <= bounds.bottom
}

internal fun cropIsInsideTargetWindow(
    crop: ScreenBounds,
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
    private val workflowJobOwnership = WorkflowJobOwnership()
    private var observationSettleJob: Job? = null
    private var recordingSnapshotJob: Job? = null
    private var screenCaptureSettleJob: Job? = null
    private var screenCaptureTimeoutJob: Job? = null
    private var screenCaptureRequestId = 0L
    private var runningWorkflowName: String? = null
    private var runningWorkflow: Workflow? = null
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
    private var liveImageCaptureScreenBounds: ScreenBounds? = null
    private var liveImageCaptureJob: Job? = null
    private var liveActionLaunchJob: Job? = null
    private var floatingEditorLaunchJob: Job? = null
    private var floatingEditorRestoreView: View? = null
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
                val stepLocation = stepId?.let { id ->
                    runningWorkflow?.let { workflow -> runningStepLocation(workflow, id) }
                }
                currentStepLocation.value = stepLocation
                debugPaused.value = state is RunState.Paused
                runningWorkflowName?.let { name ->
                    val stepPosition = stepLocation
                        ?.localizedName(this@AutomationAccessibilityService)
                    if (!showRunningNotification(name, stepPosition)) stopWorkflow()
                }
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
            val resolved = source?.let {
                recordingTargetResolver.resolve(
                    source = it,
                    packageName = packageName,
                    windowId = event.windowId,
                    eventTimeMillis = event.eventTime,
                )
            }
            val selector = resolved?.selector
            val key = selector?.let {
                listOf(packageName, event.windowId, it).joinToString("|")
            }
            if (event.isPassword || sourceInfo?.isPassword == true) {
                if (key == null) {
                    recordedClickSession.discardActiveTextBurst()
                } else {
                    recordedClickSession.discardTextBurst(key)
                }
                recordClickIssue(event, RecordingIssueReason.SensitiveText)
            } else if (source != null) {
                val text = sourceInfo?.text?.toString()
                    ?: event.text.lastOrNull()?.toString()
                    ?: "".takeIf { event.removedCount > 0 }
                if (selector != null && text != null) {
                    recordedClickSession.recordOrReplaceText(
                        requireNotNull(key),
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
            currentStepLocation.value = null
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
        currentStepLocation.value = null
        debugPaused.value = false
        runningWorkflowId.value = null
        workflowStartedAtMillis.value = null
        overlayStatus.value = null
        observationController.sourceDisconnected()
        clearScreenCapture()
    }

    fun startWorkflow(workflow: Workflow, debug: Boolean = false): Boolean =
        startWorkflowDetailed(workflow, debug) == WorkflowStartResult.Started

    @Synchronized
    internal fun startWorkflowDetailed(
        workflow: Workflow,
        debug: Boolean = false,
    ): WorkflowStartResult {
        if (!workflow.isReadyToRun()) return WorkflowStartResult.NotReady
        if (workflowJobOwnership.isOccupied()) return WorkflowStartResult.AlreadyRunning
        if (!isCurrentServiceInstance()) return WorkflowStartResult.ServiceUnavailable
        currentStepLocation.value = null
        debugPaused.value = false
        if (!showRunningNotification(workflow.name)) return WorkflowStartResult.ControlsUnavailable
        val startedAtMillis = System.currentTimeMillis()
        runningWorkflowId.value = workflow.id
        workflowStartedAtMillis.value = startedAtMillis
        runningWorkflowName = workflow.name
        runningWorkflow = workflow
        var controlNotificationUnavailable = false
        val keepResultNotification = AtomicBoolean(false)
        lateinit var pendingJob: Job
        pendingJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            val execution = workflowExecutor.runWithDiagnostics(workflow, stepThrough = debug)
            val result = execution.result
            val record = result.toRunRecord(
                workflow,
                startedAtMillis,
                System.currentTimeMillis(),
                execution.diagnostics,
            ).withControlNotificationCancellation(result, controlNotificationUnavailable)
            val outcome = withContext(Dispatchers.IO + NonCancellable) {
                persistRunOutcome(result, record) { runHistoryStore.append(it) }
            }
            if (isCurrentServiceInstance() && workflowJobOwnership.owns(pendingJob)) {
                latestRun.value = outcome
                if (record.status == RunStatus.Failed ||
                    record.status == RunStatus.CompletedWithWarnings
                ) {
                    keepResultNotification.set(showWorkflowResultNotification(
                        record,
                        historyAvailable = !outcome.historyWriteFailed,
                    ))
                }
            }
        }
        val controlWatchdog = serviceScope.launch(start = CoroutineStart.LAZY) {
            while (true) {
                delay(RUNNING_NOTIFICATION_WATCHDOG_MILLIS)
                if (!runningControlNotificationIsAvailable()) {
                    controlNotificationUnavailable = true
                    pendingJob.cancel()
                    return@launch
                }
            }
        }
        check(workflowJobOwnership.claim(pendingJob))
        pendingJob.invokeOnCompletion {
            finishWorkflowJob(pendingJob, controlWatchdog, keepResultNotification.get())
        }
        if (!pendingJob.start()) {
            finishWorkflowJob(pendingJob, controlWatchdog, keepResultNotification = false)
            return WorkflowStartResult.ServiceUnavailable
        }
        startWorkflowWatchdogIfOwned(workflowJobOwnership, pendingJob, controlWatchdog)
        return WorkflowStartResult.Started
    }

    @Synchronized
    private fun finishWorkflowJob(
        completedJob: Job,
        controlWatchdog: Job,
        keepResultNotification: Boolean,
    ) {
        if (!workflowJobOwnership.release(completedJob)) return
        controlWatchdog.cancel()
        runningWorkflowName = null
        runningWorkflow = null
        if (isCurrentServiceInstance()) {
            currentStepLocation.value = null
            debugPaused.value = false
            runningWorkflowId.value = null
            workflowStartedAtMillis.value = null
            if (!keepResultNotification) cancelRunningNotification()
        }
    }

    fun stopWorkflow() {
        workflowJobOwnership.current()?.cancel()
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
        if (!startRecordingForeground(targetPackage)) {
            stopElementMonitor()
            overlayStatus.value = getString(R.string.element_monitor_start_failed)
            return false
        }
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
                    candidate.templateClickX ?: candidate.templateWidth / 2,
                    candidate.templateClickY ?: candidate.templateHeight / 2,
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
        var pendingSelection: ScreenPoint? = null
        val picker = TextView(this).apply {
            text = liveActionString(R.string.live_action_coordinate_instruction)
            contentDescription = liveActionString(R.string.live_action_coordinate_picker_accessibility)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x33000000)
            setOnClickListener {
                val targetPackage = liveActionTargetPackage
                val targetBounds = targetPackage?.let(::targetPackageWindowBounds).orEmpty()
                val selection = pendingSelection ?: largestWindowCenter(targetBounds)
                pendingSelection = null
                selectionTouchStarted = false
                if (selection != null) {
                    val isInsideTarget = targetBounds.any {
                        selection.x >= it.left && selection.x < it.right &&
                            selection.y >= it.top && selection.y < it.bottom
                    }
                    if (isInsideTarget) {
                        liveActionSession.select(
                            LiveActionCandidate.Coordinate(selection.x, selection.y),
                        )
                        liveActionStatusMessage = null
                    } else {
                        liveActionStatusMessage = liveActionString(
                            R.string.live_action_coordinate_outside_target,
                        )
                    }
                }
                stopLiveCoordinatePicker()
                showLiveActionOverlay()
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        selectionTouchStarted = true
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!selectionTouchStarted) return@setOnTouchListener true
                        selectionTouchStarted = false
                        pendingSelection = ScreenPoint(event.rawX.toInt(), event.rawY.toInt())
                        view.performClick()
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
            val capture = captureScreenOnce()
            if (!liveActionSession.isActive || liveActionTargetPackage != targetPackage) {
                capture?.bitmap?.recycle()
                return@launch
            }
            if (capture == null) {
                liveActionStatusMessage = liveActionString(R.string.live_action_capture_failed)
                showLiveActionOverlay()
                return@launch
            }
            val bitmap = capture.bitmap
            val targetBounds = targetPackageWindowBounds(targetPackage)
            if (targetBounds.isEmpty()) {
                bitmap.recycle()
                liveActionStatusMessage = liveActionString(R.string.live_action_target_not_visible)
                showLiveActionOverlay()
                return@launch
            }
            liveImageCaptureBitmap = bitmap
            liveImageCaptureScreenBounds = capture.screenBounds
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
        val fullBitmapCrop = ImageCropBounds(0, 0, bitmap.width, bitmap.height)
        val accessibilityCrop = liveImageCaptureScreenBounds?.let { screenBounds ->
            liveActionTargetPackage?.let { targetPackage ->
                targetPackageWindowBounds(targetPackage)
                    .mapNotNull { targetBounds ->
                        mapScreenBoundsToBitmapCrop(
                            targetBounds,
                            bitmap.width,
                            bitmap.height,
                            screenBounds,
                        )
                    }
                    .maxByOrNull { bounds ->
                        (bounds.right - bounds.left).toLong() * (bounds.bottom - bounds.top)
                    }
            }
        }?.let(::centeredSupportedTemplateCrop)
            ?: centeredSupportedTemplateCrop(fullBitmapCrop)
            ?: return false
        val cropView = LiveImageCropView(
            this,
            bitmap,
            accessibilityCrop,
            ::selectLiveImageCrop,
        ).apply {
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

    private fun selectLiveImageCrop(bounds: ImageCropBounds, templateClickPoint: ScreenPoint) {
        val bitmap = liveImageCaptureBitmap ?: return
        val capturedScreenBounds = liveImageCaptureScreenBounds ?: return
        val targetPackage = liveActionTargetPackage ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            currentScreenBounds() != capturedScreenBounds ||
            !isTargetPackageVisible(targetPackage)
        ) {
            liveImageCropMessageView?.text = liveActionString(R.string.live_action_capture_failed)
            return
        }
        val screenCrop = mapBitmapCropToTargetScreen(
            crop = bounds,
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            screenBounds = capturedScreenBounds,
            targetBounds = targetPackageWindowBounds(targetPackage),
        )
        if (screenCrop == null) {
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
        liveActionSession.select(
            LiveActionCandidate.Image(
                packageName = targetPackage,
                templatePngBase64 = encoded.base64,
                templateWidth = encoded.width,
                templateHeight = encoded.height,
                templateClickX = templateClickPoint.x,
                templateClickY = templateClickPoint.y,
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
        liveImageCaptureScreenBounds = null
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
        var pendingSelection: ScreenPoint? = null
        val picker = TextView(this).apply {
            text = getString(R.string.element_inspector_tap_instruction)
            contentDescription = getString(R.string.element_inspector_picker_accessibility)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x22000000)
            setOnClickListener {
                val selection = pendingSelection
                pendingSelection = null
                val inspection = selection?.let { point ->
                    elementInspectionCapture?.let {
                        inspectElementAt(it, point.x, point.y)
                    }
                }
                stopElementPickOverlay()
                when {
                    selection == null -> showElementInspectorOverlay()
                    inspection == null -> showElementInspectorMessage(
                        getString(R.string.element_inspector_no_element),
                    )
                    else -> showElementInspectorOverlay(inspection)
                }
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP -> {
                        pendingSelection = ScreenPoint(event.rawX.toInt(), event.rawY.toInt())
                        view.performClick()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        pendingSelection = null
                        stopElementPickOverlay()
                        showElementInspectorOverlay()
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
        val privacyNotice = TextView(this).apply {
            text = getString(R.string.click_recording_privacy_notice)
            setPadding(24, 0, 24, 12)
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
                privacyNotice,
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
            contentDescription = getString(R.string.record_swipe_accessibility)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x66000000)
            setOnClickListener {
                if (!swipeInFlight) {
                    stopSwipeCapture()
                    if (recordedClickSession.isActive()) showRecordingOverlay()
                }
            }
            setOnTouchListener { view, event ->
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
                                val succeeded = swipe(startX, startY, endX, endY, duration) ==
                                    GestureActionResult.Succeeded
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
                            view.performClick()
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
                            val ready = launched && startRecordingForeground(app.packageName)
                            if (ready) {
                                monitoredTargetPackage = app.packageName
                                rememberExternalAppPackage(app.packageName)
                                recordingTargetResolver.clear()
                                lastScrollPositions.clear()
                                lastRecordedScroll = null
                            } else if (launched) {
                                stopElementMonitor()
                                overlayStatus.value = getString(R.string.element_monitor_start_failed)
                            }
                            if (ready &&
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
            if (!startRecordingForeground(
                targetPackage,
                recordedClickSession.count,
                recordedClickSession.issueCount,
            )) {
                stopElementMonitor()
                overlayStatus.value = getString(R.string.element_monitor_start_failed)
            }
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
        if (screenCaptureState.value !is ScreenCaptureState.Idle) clearScreenCapture()
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
            val requestedScreenBounds = currentScreenBounds()
            if (requestedScreenBounds == null) {
                screenCaptureTimeoutJob?.cancel()
                screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_unreadable))
                returnToEditor()
                return@launch
            }
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = copyScreenshotBitmap(screenshot)
                        if (!isCurrentServiceInstance() || requestId != screenCaptureRequestId ||
                            screenCaptureState.value !is ScreenCaptureState.Armed
                        ) {
                            bitmap?.recycle()
                            return
                        }
                        screenCaptureTimeoutJob?.cancel()
                        val screenBounds = currentScreenBounds()
                        val targetBounds = targetPackageWindowBounds(packageName)
                        if (bitmap == null || screenBounds != requestedScreenBounds ||
                            !captureGeometryIsCompatible(bitmap.width, bitmap.height, requestedScreenBounds) ||
                            !isTargetPackageVisible(packageName) || targetBounds.isEmpty()
                        ) {
                            bitmap?.recycle()
                            screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_unreadable))
                        } else {
                            screenCaptureState.value = ScreenCaptureState.Ready(
                                bitmap = bitmap,
                                nodes = snapshotCaptureNodes(),
                                screenBounds = screenBounds,
                                targetPackage = packageName,
                                targetBounds = targetBounds,
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

    private fun showRunningNotification(workflowName: String, stepPosition: String? = null): Boolean {
        if (runningNotificationReadiness(this) != ScheduleNotificationReadiness.Ready) return false
        val stopPendingIntent = notificationCommandPendingIntent(NotificationCommand.StopWorkflow)
        val nextPendingIntent = notificationCommandPendingIntent(NotificationCommand.AdvanceWorkflow)
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            RUNNING_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = android.app.Notification.Builder(this, RUNNING_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.running_notification_title, workflowName))
            .setContentText(
                stepPosition?.let { getString(R.string.running_notification_step, it) }
                    ?: getString(R.string.running_notification_preparing),
            )
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopPendingIntent)
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
        return androidOperationSucceeded {
            notificationManager.notify(RUNNING_NOTIFICATION_ID, notification)
        }
    }

    private fun runningControlNotificationIsAvailable(): Boolean = try {
        runningControlsAvailable(
            readiness = runningNotificationReadiness(this),
            notificationActive = notificationManager.activeNotifications.any { notification ->
                notification.id == RUNNING_NOTIFICATION_ID
            },
        )
    } catch (_: Exception) {
        false
    }

    private fun showWorkflowResultNotification(
        record: RunRecord,
        historyAvailable: Boolean,
    ): Boolean {
        if (runningNotificationReadiness(this) != ScheduleNotificationReadiness.Ready) return false
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (historyAvailable) putExtra(EXTRA_RUN_RECORD_ID, record.id)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            RUN_RESULT_NOTIFICATION_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(this, RUNNING_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.workflow_result_notification_title, record.workflowName))
            .setContentText(
                getString(
                    if (historyAvailable) {
                        R.string.workflow_result_notification_text
                    } else {
                        R.string.workflow_result_notification_history_not_saved
                    },
                ),
            )
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .build()
        return androidOperationSucceeded {
            notificationManager.notify(RUNNING_NOTIFICATION_ID, notification)
        }
    }

    private fun startRecordingForeground(
        targetPackage: String,
        recordedCount: Int? = null,
        issueCount: Int? = null,
    ): Boolean = androidOperationSucceeded {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                RECORDING_CHANNEL_ID,
                getString(R.string.click_recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val stopPendingIntent = notificationCommandPendingIntent(NotificationCommand.StopRecording)
        val showControlsPendingIntent = notificationCommandPendingIntent(
            NotificationCommand.ShowRecordingControls,
        )
        val backPendingIntent = notificationCommandPendingIntent(NotificationCommand.RecordBack)
        val homePendingIntent = notificationCommandPendingIntent(NotificationCommand.RecordHome)
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
            .setDeleteIntent(stopPendingIntent)
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

    private fun notificationCommandPendingIntent(command: NotificationCommand): PendingIntent {
        val identity = notificationCommandIdentity(command)
        val intent = Intent(this, StopWorkflowReceiver::class.java).setAction(identity.action)
        return PendingIntent.getBroadcast(
            this,
            identity.requestCode,
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
        notificationManager.cancel(RUNNING_NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override suspend fun launchApp(packageName: String, intentAction: String?): Boolean {
        val target = normalizedLaunchTarget(packageName, intentAction) ?: return false
        val launcherIntent = when (launchIntentStrategy(target)) {
            LaunchIntentStrategy.PackageManagerFrontDoor ->
                packageManager.getLaunchIntentForPackage(target.packageName)
            is LaunchIntentStrategy.PackageScopedAction -> null
        }
        val launchIntent = launchTargetIntent(target, launcherIntent) ?: return false
        val started = runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }.isSuccess
        if (!started) return false
        return awaitTargetPackageVisible(
            packageName = target.packageName,
            isVisible = ::isTargetPackageVisible,
        )
    }

    suspend fun click(selector: NodeSelector): Boolean =
        clickNode(selector) == NodeActionResult.Succeeded

    override suspend fun clickNode(selector: NodeSelector): NodeActionResult {
        val node = findNode(selector) ?: return NodeActionResult.TargetNotFound
        val succeeded = generateSequence(node) { it.parent }
            .firstOrNull { it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ?: false
        return if (succeeded) NodeActionResult.Succeeded else NodeActionResult.ActionFailed
    }

    override suspend fun clickImage(step: Step.ImageClick): ImageClickResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ImageClickResult.Unsupported
        if (!isTargetPackageVisible(step.packageName)) {
            return ImageClickResult.WrongPackage
        }
        val targetWindows = targetPackageWindows(step.packageName)
        if (targetWindows.isEmpty()) return ImageClickResult.WrongPackage
        val template = decodeImageTemplate(step) ?: return ImageClickResult.MissingOrInvalidTemplate
        val capture = captureScreenOnce() ?: run {
            template.recycle()
            return ImageClickResult.CaptureFailed
        }
        return try {
            val matchingTargetWindows = targetPackageWindows(step.packageName)
            when {
                !isTargetPackageVisible(step.packageName) -> ImageClickResult.WrongPackage
                currentScreenBounds() != capture.screenBounds ||
                    matchingTargetWindows.toSet() != targetWindows.toSet() -> ImageClickResult.CaptureFailed
                else -> {
                    val searchRegions = matchingTargetWindows.mapNotNull { targetWindow ->
                        mapScreenBoundsToBitmapCrop(
                            bounds = targetWindow.bounds,
                            bitmapWidth = capture.bitmap.width,
                            bitmapHeight = capture.bitmap.height,
                            screenBounds = capture.screenBounds,
                        )
                    }
                    when (val match = withContext(Dispatchers.Default) {
                        val matchingContext = currentCoroutineContext()
                        val checkCancellation = { matchingContext.ensureActive() }
                        matchTemplate(
                            capture.bitmap.toLumaImage(checkCancellation),
                            template.toLumaImage(checkCancellation),
                            step.minimumScorePermille,
                            step.ambiguityMarginPermille,
                            step.scaleTolerancePermille,
                            searchRegions = searchRegions,
                            checkCancellation = checkCancellation,
                        )
                    }) {
                        is TemplateMatchResult.Unique -> {
                            val currentTargetWindows = targetPackageWindows(step.packageName)
                            when {
                                !isTargetPackageVisible(step.packageName) -> ImageClickResult.WrongPackage
                                currentScreenBounds() != capture.screenBounds ||
                                    currentTargetWindows.toSet() != matchingTargetWindows.toSet() -> {
                                    ImageClickResult.CaptureFailed
                                }
                                else -> {
                                    val point = mapMatchToTargetScreen(
                                        match = match,
                                        bitmapWidth = capture.bitmap.width,
                                        bitmapHeight = capture.bitmap.height,
                                        screenBounds = capture.screenBounds,
                                        targetBounds = currentTargetWindows.map(TargetWindowSnapshot::bounds),
                                        templateWidth = step.templateWidth,
                                        templateHeight = step.templateHeight,
                                        templateClickX = step.templateClickX,
                                        templateClickY = step.templateClickY,
                                    )
                                    if (point == null) {
                                        ImageClickResult.NoMatch
                                    } else if (tap(point.x, point.y) == GestureActionResult.Succeeded) {
                                        ImageClickResult.Clicked(match.scorePermille)
                                    } else {
                                        ImageClickResult.GestureFailed
                                    }
                                }
                            }
                        }
                        TemplateMatchResult.NoMatch -> ImageClickResult.NoMatch
                        TemplateMatchResult.Ambiguous -> ImageClickResult.Ambiguous
                    }
                }
            }
        } finally {
            capture.bitmap.recycle()
            template.recycle()
        }
    }

    private fun isTargetPackageVisible(packageName: String): Boolean = targetPackageIsVisible(
        targetPackage = packageName,
        activePackage = rootInActiveWindow?.packageName?.toString(),
        windowPackages = windows.mapNotNull { it.root?.packageName?.toString() },
    )

    private fun targetPackageWindowBounds(packageName: String): List<ScreenBounds> = buildList {
        addAll(targetPackageWindows(packageName).map(TargetWindowSnapshot::bounds).distinct())
    }

    private fun targetPackageWindows(packageName: String): List<TargetWindowSnapshot> = buildList {
        windows.asSequence().forEach { window ->
            val root = window.root ?: return@forEach
            if (root.packageName?.toString() == packageName) {
                val bounds = Rect().also(root::getBoundsInScreen)
                if (!bounds.isEmpty) {
                    add(
                        TargetWindowSnapshot(
                            windowId = window.id,
                            bounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        ),
                    )
                }
            }
        }
        rootInActiveWindow
            ?.takeIf { it.packageName?.toString() == packageName }
            ?.let { root ->
                val bounds = Rect().also(root::getBoundsInScreen)
                val snapshot = TargetWindowSnapshot(
                    windowId = root.windowId,
                    bounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                )
                if (!bounds.isEmpty && snapshot !in this) add(snapshot)
            }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun currentScreenBounds(): ScreenBounds? = runCatching {
        val bounds = overlayWindowManager.maximumWindowMetrics.bounds
        if (bounds.isEmpty) null else ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun copyScreenshotBitmap(screenshot: ScreenshotResult): Bitmap? {
        val hardwareBitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
        return try {
            hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            hardwareBitmap?.recycle()
            screenshot.hardwareBuffer.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreenOnce(): CapturedScreen? {
        val requestedScreenBounds = currentScreenBounds() ?: return null
        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = copyScreenshotBitmap(screenshot)
                        val capture = bitmap?.takeIf {
                            currentScreenBounds() == requestedScreenBounds &&
                                captureGeometryIsCompatible(it.width, it.height, requestedScreenBounds)
                        }?.let { CapturedScreen(it, requestedScreenBounds) }
                        if (capture == null) bitmap?.recycle()
                        if (capture == null) {
                            if (continuation.isActive) continuation.resume(null)
                        } else if (continuation.isActive) {
                            continuation.resume(capture) { _, cancelledCapture, _ ->
                                cancelledCapture.bitmap.recycle()
                            }
                        } else {
                            capture.bitmap.recycle()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }
    }

    private fun Bitmap.toLumaImage(checkCancellation: () -> Unit = {}): LumaImage {
        val row = IntArray(width)
        val luma = ByteArray(width * height)
        for (y in 0 until height) {
            checkCancellation()
            getPixels(row, 0, width, 0, y, width, 1)
            row.forEachIndexed { x, color ->
                val red = color shr 16 and 0xff
                val green = color shr 8 and 0xff
                val blue = color and 0xff
                luma[y * width + x] = ((red * 77 + green * 150 + blue * 29) shr 8).toByte()
            }
        }
        return LumaImage(width, height, luma)
    }

    suspend fun longClick(selector: NodeSelector): Boolean =
        longClickNode(selector) == NodeActionResult.Succeeded

    override suspend fun longClickNode(selector: NodeSelector): NodeActionResult {
        val node = findNode(selector) ?: return NodeActionResult.TargetNotFound
        val succeeded = generateSequence(node) { it.parent }
            .firstOrNull { it.isLongClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            ?: false
        return if (succeeded) NodeActionResult.Succeeded else NodeActionResult.ActionFailed
    }

    suspend fun scroll(selector: NodeSelector, direction: ScrollDirection): Boolean =
        scrollNode(selector, direction) == NodeActionResult.Succeeded

    override suspend fun scrollNode(
        selector: NodeSelector,
        direction: ScrollDirection,
    ): NodeActionResult {
        val node = findNode(selector) ?: return NodeActionResult.TargetNotFound
        val action = when (direction) {
            ScrollDirection.Forward -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.Backward -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val succeeded = generateSequence(node) { it.parent }
            .firstOrNull { candidate ->
                candidate.isScrollable || candidate.actionList.any { it.id == action }
            }
            ?.performAction(action)
            ?: false
        return if (succeeded) NodeActionResult.Succeeded else NodeActionResult.ActionFailed
    }

    suspend fun inputText(
        selector: NodeSelector,
        text: String,
        method: TextInputMethod,
    ): Boolean = inputTextNode(selector, text, method) == NodeActionResult.Succeeded

    override suspend fun inputTextNode(
        selector: NodeSelector,
        text: String,
        method: TextInputMethod,
    ): NodeActionResult {
        val node = findNode(selector) ?: return NodeActionResult.TargetNotFound
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return when (method) {
            TextInputMethod.SetText -> {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    NodeActionResult.Succeeded
                } else {
                    NodeActionResult.ActionFailed
                }
            }
            TextInputMethod.Paste -> {
                val clipboardManager = getSystemService(ClipboardManager::class.java)
                when (ClipboardTransaction(
                    AndroidClipboardAdapter(
                        clipboardManager,
                        getString(R.string.temporary_input_clip_label),
                    ),
                ).pasteResult(text) {
                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }) {
                    ClipboardPasteResult.Succeeded -> NodeActionResult.Succeeded
                    ClipboardPasteResult.ClipboardUnavailable -> NodeActionResult.ClipboardUnavailable
                    ClipboardPasteResult.ActionFailed -> NodeActionResult.ActionFailed
                }
            }
        }
    }

    suspend fun readNodeAttribute(selector: NodeSelector, attribute: NodeAttribute): String? =
        (readNode(selector, attribute) as? NodeReadResult.Value)?.value

    override suspend fun readNode(selector: NodeSelector, attribute: NodeAttribute): NodeReadResult {
        val node = findNode(selector) ?: return NodeReadResult.TargetNotFound
        val value = when (attribute) {
            NodeAttribute.TextOrDescription -> node.text.nonBlankString()
                ?: node.contentDescription.nonBlankString()
            NodeAttribute.Text -> node.text.nonBlankString()
            NodeAttribute.ContentDescription -> node.contentDescription.nonBlankString()
            NodeAttribute.ViewId -> node.viewIdResourceName?.takeIf { it.isNotBlank() }
            NodeAttribute.ClassName -> node.className.nonBlankString()
        }
        return value?.let(NodeReadResult::Value) ?: NodeReadResult.AttributeMissing
    }

    private fun CharSequence?.nonBlankString(): String? = this?.toString()?.takeIf { it.isNotBlank() }

    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long,
    ): GestureActionResult {
        val bounds = currentGestureBounds()
        if (!gesturePointsAreInsideDisplay(
                bounds,
                ScreenPoint(startX, startY),
                ScreenPoint(endX, endY),
            )
        ) {
            return GestureActionResult.CoordinatesOutOfBounds(
                bounds.right - bounds.left,
                bounds.bottom - bounds.top,
            )
        }
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return if (dispatchPath(path, durationMillis)) {
            GestureActionResult.Succeeded
        } else {
            GestureActionResult.ActionFailed
        }
    }

    override suspend fun tap(x: Int, y: Int): GestureActionResult {
        val bounds = currentGestureBounds()
        if (!gesturePointsAreInsideDisplay(bounds, ScreenPoint(x, y))) {
            return GestureActionResult.CoordinatesOutOfBounds(
                bounds.right - bounds.left,
                bounds.bottom - bounds.top,
            )
        }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return if (dispatchPath(path, TAP_DURATION_MILLIS)) {
            GestureActionResult.Succeeded
        } else {
            GestureActionResult.ActionFailed
        }
    }

    private fun currentGestureBounds(): ScreenBounds {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            currentScreenBounds()?.let { return it }
        }
        val metrics = resources.displayMetrics
        return ScreenBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
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
        if (selectorUsesActiveWindow(selector.packageName.trim())) {
            rootInActiveWindow?.let { sequenceOf(it) } ?: emptySequence()
        } else {
            packageWindowRoots(selector.packageName.trim()).asSequence()
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
        val selectorPackage = selector.packageName.trim()
        val targetMatches =
            (selectorPackage.isBlank() || packageName?.toString() == selectorPackage) &&
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
        private const val RUNNING_NOTIFICATION_ID = 1001
        private const val RUN_RESULT_NOTIFICATION_REQUEST_CODE = 1002
        private const val RUNNING_NOTIFICATION_WATCHDOG_MILLIS = 1_000L
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
        val currentStepLocation = MutableStateFlow<RunStepLocation?>(null)
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
                (screenCaptureState.value as? ScreenCaptureState.Ready)?.bitmap?.recycle()
                screenCaptureState.value = ScreenCaptureState.Idle
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
    pollIntervalMillis: Long = 100L,
): Boolean {
    while (!isVisible(packageName)) {
        delay(pollIntervalMillis)
    }
    return true
}

private data class ElementMonitorViews(
    val details: TextView,
)

internal fun sanitizedRecordedText(
    isPassword: Boolean,
    text: String?,
    contentDescription: String?,
): Pair<String?, String?> = if (isPassword) null to null else text to contentDescription

internal fun androidOperationSucceeded(operation: () -> Unit): Boolean = try {
    operation()
    true
} catch (_: Exception) {
    false
}

internal fun runningStepLocation(workflow: Workflow, stepId: String): RunStepLocation? =
    workflow.steps.uniqueRunLocationTo(stepId)

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
    private val actionTextKeys = mutableListOf<String?>()
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
        actionTextKeys.clear()
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
        actionTextKeys += key
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

    fun discardActiveTextBurst() {
        val key = activeTextKey ?: return
        discardTextBurst(key)
    }

    fun discardTextBurst(key: String) {
        val index = textActionIndexes.remove(key)
            ?: actionTextKeys.indexOfLast { it == key }.takeIf { it >= 0 }
        if (activeTextKey == key) activeTextKey = null
        if (index != null) {
            actions.removeAt(index)
            actionTextKeys.removeAt(index)
            textActionIndexes.replaceAll { _, existingIndex ->
                if (existingIndex > index) existingIndex - 1 else existingIndex
            }
        }
    }

    private fun recordAction(action: RecordedAction): Boolean {
        if (!recording || actions.size >= capacity) return false
        actions += action
        actionTextKeys += null
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
            actionTextKeys.clear()
            activeTextKey = null
        }
    }

    fun cancel() {
        recording = false
        actions.clear()
        issues.clear()
        textActionIndexes.clear()
        actionTextKeys.clear()
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
    data class Ready(
        val bitmap: Bitmap,
        val nodes: List<CaptureNode>,
        val screenBounds: ScreenBounds,
        val targetPackage: String,
        val targetBounds: List<ScreenBounds>,
    ) : ScreenCaptureState
    data class Error(val message: String) : ScreenCaptureState
}

data class RunOutcome(
    val result: RunResult,
    val record: RunRecord,
    val historyWriteFailed: Boolean = false,
)

internal fun persistRunOutcome(
    result: RunResult,
    record: RunRecord,
    persist: (RunRecord) -> Unit,
): RunOutcome = RunOutcome(
    result = result,
    record = record,
    historyWriteFailed = runCatching { persist(record) }.isFailure,
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
