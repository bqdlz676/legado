package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 书名右侧的朱砂进度竖线：整条是未读的云灰，已读部分由顶部向下染成朱砂。
 * 线长即进度，不需要再画一条横向进度条。
 */
class InkProgressLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density

    var ratio: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (next != field) {
                field = next
                invalidate()
            }
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x339B9687
    }
    private val readPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC23B2E.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val stroke = 2f * d
        val cx = w / 2f
        val r = stroke / 2f
        canvas.drawRoundRect(cx - r, 0f, cx + r, h, r, r, trackPaint)
        val filled = h * ratio
        if (filled > 0.5f) {
            canvas.drawRoundRect(cx - r, 0f, cx + r, filled, r, r, readPaint)
        }
    }
}
