package io.legado.app.ui.widget

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View

/**
 * 落文 · 墨触（InkButton 基类）
 *
 * 全站不用 Material ripple。所有可点元素统一一种反馈：
 * 按下 → 整块泛朱砂 8%（120ms），松手 → 回落。
 * 不是水波纹向外扩散，是「纸被指腹压出一点朱色」。
 *
 * 用法：view.inkPress()   —— 挂到 foreground 上，不覆盖 background、不抢点击。
 */
fun View.inkPress(radiusDp: Float = 0f) {
    foreground = InkPressDrawable(radiusDp, resources.displayMetrics.density)
}

class InkPressDrawable(
    private val radiusDp: Float = 0f,
    private val density: Float = 2.75f
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC23B2E.toInt() }
    private val rect = RectF()
    private var alphaLevel = 0f
    private var animator: ValueAnimator? = null

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val pressed = state.contains(android.R.attr.state_pressed)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(alphaLevel, if (pressed) 0.08f else 0f).apply {
            duration = InkDur.MICRO / 2
            interpolator = if (pressed) InkEase.pageTurn else InkEase.scrim
            addUpdateListener {
                alphaLevel = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        if (alphaLevel <= 0f) return
        paint.alpha = (alphaLevel * 255f).toInt().coerceIn(0, 255)
        val r = radiusDp * density
        if (r > 0f) canvas.drawRoundRect(rect, r, r, paint) else canvas.drawRect(rect, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
