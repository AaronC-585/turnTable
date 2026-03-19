package com.turntable.barcodescanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Overlays a scan window (rounded square) with a line through the center.
 * In portrait: horizontal line. In landscape: vertical line.
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFF3a7bd5.toInt()
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF3a7bd5.toInt()
    }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x80000000.toInt()
    }

    private val frameRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val size = minOf(w, h) * 0.65f
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        frameRect.set(left, top, left + size, top + size)
        val cornerRadius = size * 0.08f

        // Dimmed area: four rects around the scan window (top, bottom, left, right)
        canvas.drawRect(0f, 0f, w, top, dimPaint)
        canvas.drawRect(0f, top + size, w, h, dimPaint)
        canvas.drawRect(0f, top, left, top + size, dimPaint)
        canvas.drawRect(left + size, top, w, top + size, dimPaint)

        // Scan window frame
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)

        // Line through center: horizontal in portrait, vertical in landscape
        val cx = frameRect.centerX()
        val cy = frameRect.centerY()
        if (h >= w) {
            // Portrait: horizontal line
            canvas.drawLine(frameRect.left, cy, frameRect.right, cy, linePaint)
        } else {
            // Landscape: vertical line
            canvas.drawLine(cx, frameRect.top, cx, frameRect.bottom, linePaint)
        }
    }
}
