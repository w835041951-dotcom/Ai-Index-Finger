package com.aiindexfinger.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Rect
import android.graphics.Path
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiindexfinger.MainActivity
import com.aiindexfinger.executor.AutomationDriver
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.executor.RunState
import com.aiindexfinger.executor.WorkflowExecutor
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.toRunRecord
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.matches
import com.aiindexfinger.model.Workflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AutomationAccessibilityService : AccessibilityService(), AutomationDriver {
    private val workflowExecutor by lazy { WorkflowExecutor(this) }
    private val runHistoryStore by lazy { RunHistoryStore(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var workflowJob: Job? = null
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
        notificationManager.cancel(RUNNING_NOTIFICATION_ID)
        currentStepId.value = null
        runningWorkflowId.value = null
        workflowStartedAtMillis.value = null
        mutableConnected.value = false
        super.onDestroy()
    }

    fun startWorkflow(workflow: Workflow): Boolean {
        if (workflowJob?.isActive == true) return false
        workflowJob = serviceScope.launch {
            val startedAtMillis = System.currentTimeMillis()
            runningWorkflowId.value = workflow.id
            workflowStartedAtMillis.value = startedAtMillis
            runningWorkflowName = workflow.name
            showRunningNotification(workflow.name)
            try {
                val result = workflowExecutor.run(workflow)
                val record = result.toRunRecord(workflow, startedAtMillis, System.currentTimeMillis())
                runHistoryStore.append(runHistoryStore.load(), record)
                latestRun.value = RunOutcome(result, record)
            } finally {
                runningWorkflowId.value = null
                workflowStartedAtMillis.value = null
                runningWorkflowName = null
                notificationManager.cancel(RUNNING_NOTIFICATION_ID)
            }
        }
        return true
    }

    fun stopWorkflow() {
        workflowJob?.cancel()
    }

    private fun showRunningNotification(workflowName: String, stepId: String? = null) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                RUNNING_CHANNEL_ID,
                "Workflow execution",
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
            .setContentTitle("Running $workflowName")
            .setContentText(stepId?.let { "Current step: $it" } ?: "Preparing workflow")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .addAction(
                android.app.Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Stop",
                    stopPendingIntent,
                ).build(),
            )
            .build()
        notificationManager.notify(RUNNING_NOTIFICATION_ID, notification)
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override suspend fun launchApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    override suspend fun click(selector: NodeSelector): Boolean {
        val node = findNode(selector) ?: return false
        return generateSequence(node) { it.parent }
            .firstOrNull { it.isClickable }
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ?: false
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
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("AI Index Finger", text))
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
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
        private const val RUNNING_CHANNEL_ID = "workflow_execution"
        private const val RUNNING_NOTIFICATION_ID = 1001
        private const val TAP_DURATION_MILLIS = 50L
        private val mutableConnected = MutableStateFlow(false)
        val connected = mutableConnected.asStateFlow()
        val observedNodes = MutableStateFlow<List<ObservedNode>>(emptyList())
        val currentStepId = MutableStateFlow<String?>(null)
        val runningWorkflowId = MutableStateFlow<String?>(null)
        val workflowStartedAtMillis = MutableStateFlow<Long?>(null)
        val latestRun = MutableStateFlow<RunOutcome?>(null)

        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set
    }
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
