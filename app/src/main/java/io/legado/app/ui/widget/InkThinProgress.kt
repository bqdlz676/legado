package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 落文 · 细横线进度
 *
 * 一条 1dp 云灰底线，已读部分压一条 1.4dp 朱砂线。没有 Material 的 track / indicator。
 */
class InkThinProgress @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1A1A1410
        strokeWidth = d
    }
    private val readPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC23B2E.toInt()
        strokeWidth = 1.4f * d
    }

    var ratio: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = height - d * 1.2f
        canvas.drawLine(0f, y, width.toFloat(), y, trackPaint)
        if (ratio > 0f) {
            canvas.drawLine(0f, y, width * ratio, y, readPaint)
        }
    }
}
