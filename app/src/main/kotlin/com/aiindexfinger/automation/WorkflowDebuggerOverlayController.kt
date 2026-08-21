package com.aiindexfinger.automation

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aiindexfinger.R
import kotlin.math.abs

internal enum class DebuggerOverlayEdge {
    Start,
    End,
}

internal fun debuggerOverlayEdgeFor(rawX: Int, screenWidth: Int): DebuggerOverlayEdge =
    if (rawX < screenWidth / 2) DebuggerOverlayEdge.Start else DebuggerOverlayEdge.End

internal fun clampDebuggerOverlayY(
    candidate: Int,
    screenHeight: Int,
    panelHeight: Int,
    margin: Int,
): Int = candidate.coerceIn(
    margin,
    (screenHeight - panelHeight - margin).coerceAtLeast(margin),
)

internal data class WorkflowDebuggerOverlayState(
    val workflowName: String,
    val stepName: String?,
    val paused: Boolean,
)

internal class WorkflowDebuggerOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onNext: () -> Unit,
    private val onStop: () -> Unit,
) {
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var titleView: TextView? = null
    private var statusView: TextView? = null
    private var stepView: TextView? = null
    private var nextButton: Button? = null
    private var savedEdge = DebuggerOverlayEdge.End
    private var savedY: Int? = null

    fun show(state: WorkflowDebuggerOverlayState): Boolean {
        if (rootView != null) {
            update(state)
            return true
        }

        val title = TextView(context).apply {
            maxLines = 1
            maxWidth = dp(320)
            ellipsize = TextUtils.TruncateAt.END
            textSize = 16f
        }
        val status = TextView(context).apply {
            maxWidth = dp(320)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            textSize = 13f
        }
        val step = TextView(context).apply {
            maxLines = 2
            maxWidth = dp(320)
            ellipsize = TextUtils.TruncateAt.END
            textSize = 13f
        }
        val next = Button(context).apply {
            setText(R.string.debug_next_step)
            contentDescription = context.getString(R.string.debug_overlay_next_description)
            setOnClickListener { onNext() }
        }
        val stop = Button(context).apply {
            setText(R.string.stop)
            contentDescription = context.getString(R.string.debug_overlay_stop_description)
            setOnClickListener { onStop() }
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(
                next,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                stop,
                LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(240)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = dp(10).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(resolveThemeColor(android.R.attr.colorBackground, Color.WHITE))
                setStroke(
                    dp(1),
                    resolveThemeColor(android.R.attr.textColorSecondary, 0x66000000),
                )
            }
            contentDescription = context.getString(R.string.debug_overlay_pane_description)
            addView(
                title,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                status,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) },
            )
            addView(
                step,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(6)
                },
            )
            addView(
                actions,
                LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or when (savedEdge) {
                DebuggerOverlayEdge.Start -> Gravity.START
                DebuggerOverlayEdge.End -> Gravity.END
            }
            x = dp(12)
            y = savedY ?: dp(96)
        }
        installDragHandle(title, panel, params)

        return runCatching {
            windowManager.addView(panel, params)
            rootView = panel
            layoutParams = params
            titleView = title
            statusView = status
            stepView = step
            nextButton = next
            update(state)
            panel.post {
                if (savedY == null) {
                    centerVertically(panel, params)
                } else {
                    params.y = clampY(params.y, panel)
                    rememberPosition(params)
                    updateLayout(panel, params)
                }
            }
        }.isSuccess
    }

    fun update(state: WorkflowDebuggerOverlayState) {
        titleView?.text = context.getString(R.string.debug_overlay_title, state.workflowName)
        statusView?.text = context.getString(
            if (state.paused) R.string.debug_overlay_paused else R.string.debug_overlay_running,
        )
        stepView?.text = state.stepName?.let {
            context.getString(R.string.debug_overlay_current_step, it)
        } ?: context.getString(R.string.running_preparing_first_step)
        nextButton?.visibility = if (state.paused) View.VISIBLE else View.INVISIBLE
    }

    fun hide() {
        layoutParams?.let(::rememberPosition)
        rootView?.let { view -> runCatching { windowManager.removeView(view) } }
        rootView = null
        layoutParams = null
        titleView = null
        statusView = null
        stepView = null
        nextButton = null
    }

    fun isVisible(): Boolean = rootView != null

    private fun installDragHandle(
        handle: View,
        panel: View,
        params: WindowManager.LayoutParams,
    ) {
        handle.contentDescription = context.getString(R.string.debug_overlay_move_description)
        handle.isClickable = true
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startRawX
                    val deltaY = event.rawY - startRawY
                    moved = moved || abs(deltaX) >= dp(4) || abs(deltaY) >= dp(4)
                    params.x = if (isRightEdge(params)) {
                        (startX - deltaX.toInt()).coerceAtLeast(0)
                    } else {
                        (startX + deltaX.toInt()).coerceAtLeast(0)
                    }
                    params.y = clampY(startY + deltaY.toInt(), panel)
                    updateLayout(panel, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved && event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    snapToEdge(event.rawX, panel, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun centerVertically(panel: View, params: WindowManager.LayoutParams) {
        params.y = clampY((screenBounds().height() - panel.height) / 2, panel)
        rememberPosition(params)
        updateLayout(panel, params)
    }

    private fun snapToEdge(rawX: Float, panel: View, params: WindowManager.LayoutParams) {
        savedEdge = debuggerOverlayEdgeFor(rawX.toInt(), screenBounds().width())
        params.gravity = Gravity.TOP or when (savedEdge) {
            DebuggerOverlayEdge.Start -> Gravity.START
            DebuggerOverlayEdge.End -> Gravity.END
        }
        params.x = dp(12)
        params.y = clampY(params.y, panel)
        rememberPosition(params)
        updateLayout(panel, params)
    }

    private fun rememberPosition(params: WindowManager.LayoutParams) {
        savedEdge = if (isRightEdge(params)) DebuggerOverlayEdge.End else DebuggerOverlayEdge.Start
        savedY = params.y
    }

    private fun updateLayout(panel: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.updateViewLayout(panel, params) }
    }

    private fun isRightEdge(params: WindowManager.LayoutParams): Boolean =
        params.gravity and Gravity.END == Gravity.END

    private fun clampY(candidate: Int, panel: View): Int {
        val bounds = screenBounds()
        return clampDebuggerOverlayY(
            candidate = candidate,
            screenHeight = bounds.height(),
            panelHeight = panel.height,
            margin = dp(8),
        )
    }

    private fun screenBounds(): Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds
    } else {
        @Suppress("DEPRECATION")
        Rect(0, 0, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
    }

    private fun resolveThemeColor(attribute: Int, fallback: Int): Int {
        val value = android.util.TypedValue()
        if (!context.theme.resolveAttribute(attribute, value, true)) return fallback
        if (value.resourceId != 0) return runCatching { context.getColor(value.resourceId) }.getOrDefault(fallback)
        return value.data
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}