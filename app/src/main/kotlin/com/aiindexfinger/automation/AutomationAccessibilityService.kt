package com.aiindexfinger.automation

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.Path
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.Display
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
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
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlin.coroutines.resume

class AutomationAccessibilityService : AccessibilityService(), AutomationDriver {
    private val workflowExecutor by lazy { WorkflowExecutor(this) }
    private val runHistoryStore by lazy { RunHistoryStore(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var workflowJob: Job? = null
    private var screenCaptureSettleJob: Job? = null
    private var screenCaptureTimeoutJob: Job? = null
    private var screenCaptureRequestId = 0L
    private var runningWorkflowName: String? = null

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        mutableConnected.value = true
        instance = this
        serviceScope.launch {
            workflowExecutor.state.collect { state ->
                val stepId = (state as? RunState.Running)?.stepId
                currentStepId.value = stepId
                runningWorkflowName?.let { name -> showRunningNotification(name, stepId) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handleArmedScreenCapture(event)
        if (!observationController.isObservationRequested) return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        val root = rootInActiveWindow ?: return
        observedNodes.value = root.depthFirstSequence()
            .mapNotNull { it.toDescriptor() }
            .distinctBy { listOf(it.viewId, it.text, it.contentDescription, it.className) }
            .take(MAX_OBSERVED_NODES)
            .toList()
    }

    override fun onInterrupt() {
        stopWorkflow()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        serviceScope.cancel()
        cancelRunningNotification()
        currentStepId.value = null
        runningWorkflowId.value = null
        workflowStartedAtMillis.value = null
        observationController.sourceDisconnected()
        clearScreenCapture()
        mutableConnected.value = false
        super.onDestroy()
    }

    fun startWorkflow(workflow: Workflow): Boolean {
        if (!workflow.isReadyToRun()) return false
        if (workflowJob?.isActive == true) return false
        workflowJob = serviceScope.launch {
            val startedAtMillis = System.currentTimeMillis()
            runningWorkflowId.value = workflow.id
            workflowStartedAtMillis.value = startedAtMillis
            runningWorkflowName = workflow.name
            showRunningNotification(workflow.name)
            try {
                val execution = workflowExecutor.runWithDiagnostics(workflow)
                val result = execution.result
                val record = result.toRunRecord(
                    workflow,
                    startedAtMillis,
                    System.currentTimeMillis(),
                    execution.diagnostics,
                )
                runHistoryStore.append(runHistoryStore.load(), record)
                latestRun.value = RunOutcome(result, record)
            } finally {
                runningWorkflowId.value = null
                workflowStartedAtMillis.value = null
                runningWorkflowName = null
                cancelRunningNotification()
            }
        }
        return true
    }

    fun stopWorkflow() {
        workflowJob?.cancel()
    }

    fun capturePreviousApp(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_requires_android_11))
            return false
        }
        if (screenCaptureState.value is ScreenCaptureState.Armed) return false
        clearScreenCapture()
        val requestId = ++screenCaptureRequestId
        screenCaptureState.value = ScreenCaptureState.Armed
        screenCaptureTimeoutJob = serviceScope.launch {
            delay(SCREEN_CAPTURE_TIMEOUT_MILLIS)
            if (requestId == screenCaptureRequestId && screenCaptureState.value is ScreenCaptureState.Armed) {
                screenCaptureState.value = ScreenCaptureState.Error(getString(R.string.capture_timed_out))
                returnToEditor()
            }
        }
        return true
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

    private fun handleArmedScreenCapture(event: AccessibilityEvent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (screenCaptureState.value !is ScreenCaptureState.Armed) return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        val requestId = screenCaptureRequestId
        screenCaptureSettleJob?.cancel()
        screenCaptureSettleJob = serviceScope.launch {
            delay(SCREEN_CAPTURE_SETTLE_MILLIS)
            if (requestId != screenCaptureRequestId || screenCaptureState.value !is ScreenCaptureState.Armed) {
                return@launch
            }
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
                        if (requestId != screenCaptureRequestId || screenCaptureState.value !is ScreenCaptureState.Armed) {
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
                        if (requestId != screenCaptureRequestId || screenCaptureState.value !is ScreenCaptureState.Armed) {
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
        return windows.asSequence()
            .mapNotNull { it.root }
            .flatMap { root -> root.depthFirstWithDepth() }
            .mapNotNull { (node, depth) ->
                val packageName = node.packageName?.toString() ?: return@mapNotNull null
                val bounds = Rect().also(node::getBoundsInScreen)
                CaptureNode(
                    packageName = packageName,
                    viewId = node.viewIdResourceName,
                    text = node.text.nonBlankString(),
                    contentDescription = node.contentDescription.nonBlankString(),
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
            Intent(this, MainActivity::class.java).apply {
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
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            RUNNING_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(this, RUNNING_CHANNEL_ID)
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
            .addAction(
                android.app.Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    getString(R.string.stop),
                    stopPendingIntent,
                ).build(),
            )
            .build()
        notificationManager.notify(RUNNING_NOTIFICATION_ID, notification)
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
        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }.isSuccess
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
        if (rootInActiveWindow?.packageName?.toString() != step.packageName) {
            return ImageClickResult.WrongPackage
        }
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
                    checkCancellation = { matchingContext.ensureActive() },
                )
            }) {
                is TemplateMatchResult.Unique -> if (tap(match.centerX, match.centerY)) {
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

    private fun decodeImageTemplate(step: Step.ImageClick): Bitmap? = runCatching {
        val bytes = Base64.getDecoder().decode(step.templatePngBase64)
        if (bytes.size > MAX_TEMPLATE_PNG_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth != step.templateWidth || bounds.outHeight != step.templateHeight) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?.takeIf { it.width == step.templateWidth && it.height == step.templateHeight }
    }.getOrNull()

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
        return windows.asSequence()
            .filter { it.root?.packageName?.toString() == selector.packageName }
            .mapNotNull { it.root }
            .flatMap { root -> root.depthFirstSequence() }
            .count { node -> node.matches(selector) }
    }

    private fun findNode(selector: NodeSelector): AccessibilityNodeInfo? {
        return windows.asSequence()
            .filter { it.root?.packageName?.toString() == selector.packageName }
            .mapNotNull { it.root }
            .flatMap { root -> root.depthFirstSequence() }
                .filter { node -> node.matches(selector) }
                .drop(selector.matchIndex)
                .firstOrNull()
    }

    private fun AccessibilityNodeInfo.depthFirstSequence(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@depthFirstSequence)
        for (index in 0 until childCount) {
            getChild(index)?.let { child -> yieldAll(child.depthFirstSequence()) }
        }
    }

    private fun AccessibilityNodeInfo.matches(selector: NodeSelector): Boolean {
        return packageName?.toString() == selector.packageName &&
            selector.viewId.matchesIfPresent(viewIdResourceName) &&
            selector.text.matchesIfPresent(text?.toString(), selector.textMatchMode) &&
            selector.contentDescription.matchesIfPresent(
                contentDescription?.toString(),
                selector.contentDescriptionMatchMode,
            ) &&
            selector.className.matchesIfPresent(className?.toString())
    }

    private fun String?.matchesIfPresent(actual: String?): Boolean = this == null || this == actual

    private fun String?.matchesIfPresent(actual: String?, mode: TextMatchMode): Boolean =
        mode.matches(this, actual)

    private fun AccessibilityNodeInfo.toDescriptor(): ObservedNode? {
        val packageName = packageName?.toString() ?: return null
        val viewId = viewIdResourceName
        val text = text?.toString()?.takeIf { it.isNotBlank() }
        val description = contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val className = className?.toString()?.takeIf { it.isNotBlank() }
        if (viewId == null && text == null && description == null) return null
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
        private const val MAX_OBSERVED_NODES = 200
        private const val MAX_CAPTURE_NODES = 1_000
        private const val SCREEN_CAPTURE_SETTLE_MILLIS = 450L
        private const val SCREEN_CAPTURE_TIMEOUT_MILLIS = 15_000L
        private const val MAX_TEMPLATE_PNG_BYTES = 96 * 1024
        private const val RUNNING_CHANNEL_ID = "workflow_execution"
        private const val RUNNING_NOTIFICATION_ID = 1001
        private const val TAP_DURATION_MILLIS = 50L
        private val mutableConnected = MutableStateFlow(false)
        val connected = mutableConnected.asStateFlow()
        val observedNodes = MutableStateFlow<List<ObservedNode>>(emptyList())
        val screenCaptureState = MutableStateFlow<ScreenCaptureState>(ScreenCaptureState.Idle)
        private val observationController = AccessibilityObservationController {
            observedNodes.value = emptyList()
        }
        val currentStepId = MutableStateFlow<String?>(null)
        val runningWorkflowId = MutableStateFlow<String?>(null)
        val workflowStartedAtMillis = MutableStateFlow<Long?>(null)
        val latestRun = MutableStateFlow<RunOutcome?>(null)

        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set

        internal fun acquireObservationLease(): AutoCloseable =
            observationController.acquire()

        fun discardScreenCapture() {
            instance?.clearScreenCapture() ?: run {
                (screenCaptureState.value as? ScreenCaptureState.Ready)?.bitmap?.recycle()
                screenCaptureState.value = ScreenCaptureState.Idle
            }
        }
    }
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
