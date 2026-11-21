package io.legado.app.ui.welcome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * 启动页扫光：一道极淡的斜向光带从左侧掠过，只走一次，不循环。
 * 叠在水墨之上，透明铺满，不拦截触摸。
 */
class SplashShineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var p = 0f
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun start(delayMs: Long = 0L, duration: Long = 640L) {
        animator?.cancel()
        p = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            startDelay = delayMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                p = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || p <= 0f || p >= 1f) return

        val band = w * 0.26f
        val x = -band + (w + band * 2f) * p
        paint.shader = LinearGradient(
            x, h * 0.08f, x + band, h * 0.84f,
            intArrayOf(0x00FFFFFF, 0x16FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }
}
