package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 半屏 Sheet 顶端的拖拽金杠：一根 4dp 的暖金横杆，两端各一粒亮金圆点。
 * 不是 Material 的圆角灰条 —— 这根杆本身就是"可以往下拽"的暗示。
 */
class InkGrabberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4A54A.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8C77A.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val barH = 4f * d
        val cy = h / 2f
        val inset = 9f * d
        val r = barH / 2f
        canvas.drawRoundRect(inset, cy - r, w - inset, cy + r, r, r, barPaint)
        val dotR = 2.4f * d
        canvas.drawCircle(4.5f * d, cy, dotR, dotPaint)
        canvas.drawCircle(w - 4.5f * d, cy, dotR, dotPaint)
    }
}
