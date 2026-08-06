package com.aiindexfinger.automation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

internal class LiveImageCropView(
    context: Context,
    private val bitmap: Bitmap,
    private val onCropSelected: (ImageCropBounds) -> Unit,
) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 200, 87)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }
    private var dragStartX: Float? = null
    private var dragStartY: Float? = null
    private var dragEndX: Float? = null
    private var dragEndY: Float? = null

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || bitmap.isRecycled) return
        val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val displayedWidth = bitmap.width * scale
        val displayedHeight = bitmap.height * scale
        val left = (width - displayedWidth) / 2f
        val top = (height - displayedHeight) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + displayedWidth, top + displayedHeight),
            bitmapPaint,
        )
        val startX = dragStartX
        val startY = dragStartY
        val endX = dragEndX
        val endY = dragEndY
        if (startX != null && startY != null && endX != null && endY != null) {
            canvas.drawRect(
                minOf(startX, endX),
                minOf(startY, endY),
                maxOf(startX, endX),
                maxOf(startY, endY),
                selectionPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mapToBitmap(event.x, event.y) == null) return true
                dragStartX = event.x
                dragStartY = event.y
                dragEndX = event.x
                dragEndY = event.y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragStartX != null && mapToBitmap(event.x, event.y) != null) {
                    dragEndX = event.x
                    dragEndY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val start = dragStartX?.let { x -> dragStartY?.let { y -> mapToBitmap(x, y) } }
                val end = mapToBitmap(event.x, event.y)
                resetSelection()
                performClick()
                if (start != null && end != null) {
                    onCropSelected(
                        ImageCropBounds(
                            left = minOf(start.x, end.x),
                            top = minOf(start.y, end.y),
                            right = (maxOf(start.x, end.x) + 1).coerceAtMost(bitmap.width),
                            bottom = (maxOf(start.y, end.y) + 1).coerceAtMost(bitmap.height),
                        ),
                    )
                }
            }
            MotionEvent.ACTION_CANCEL -> resetSelection()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun mapToBitmap(x: Float, y: Float): ScreenPoint? = mapFitCenterTapToScreen(
        tapX = x,
        tapY = y,
        containerWidth = width,
        containerHeight = height,
        imageWidth = bitmap.width,
        imageHeight = bitmap.height,
    )

    private fun resetSelection() {
        dragStartX = null
        dragStartY = null
        dragEndX = null
        dragEndY = null
        invalidate()
    }
}