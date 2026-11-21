package io.legado.app.ui.welcome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.legado.app.constant.Theme
import io.legado.app.help.config.ThemeConfig
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 启动页：散落星点飞向中心结成图谱，相邻节点连线依次点亮。
 * 纯 View 实现，不依赖 Compose。
 */
class SplashStarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private class Star(
        val sx: Float, val sy: Float,
        val ang: Float, val rad: Float,
        val size: Float, val delay: Float
    )

    private val stars = ArrayList<Star>()
    private var progress = 0f
    private var lineProgress = 0f
    private var animator: ValueAnimator? = null

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val isDark: Boolean =
        runCatching { ThemeConfig.getTheme() == Theme.Dark }.getOrDefault(true)

    init {
        buildStars()
        nodePaint.color = if (isDark) 0xFFD6E4FF.toInt() else 0xFF4C5F80.toInt()
        haloPaint.color = if (isDark) 0x40A8C8FF.toInt() else 0x30FFFFFF.toInt()
        linePaint.color = if (isDark) 0xFF5B8CFF.toInt() else 0xFF93A6C4.toInt()
        linePaint.strokeWidth = 1.1f * density
        corePaint.color = if (isDark) 0xFF7FB4FF.toInt() else 0xFF6F86A8.toInt()
    }

    private fun buildStars() {
        val rnd = Random(20260101)
        val n = 13
        for (i in 0 until n) {
            val ang = (i * 2f * Math.PI.toFloat() / n) + (rnd.nextFloat() - 0.5f) * 0.32f
            stars += Star(
                sx = rnd.nextFloat(),
                sy = rnd.nextFloat(),
                ang = ang,
                rad = 0.20f + rnd.nextFloat() * 0.14f,
                size = 1.5f + rnd.nextFloat() * 2.0f,
                delay = rnd.nextFloat() * 0.22f
            )
        }
        stars.sortBy { it.ang }
    }

    fun start(duration: Long = 1200L) {
        animator?.cancel()
        progress = 0f
        lineProgress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                progress = it.animatedValue as Float
                lineProgress = ((progress - 0.34f) / 0.40f).coerceIn(0f, 1f)
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

    /** 带延迟的 ease-out：每颗星错峰起飞 */
    private fun eased(p: Float, delay: Float): Float {
        val span = (1f - delay).coerceAtLeast(0.001f)
        val t = ((p - delay) / span).coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t)
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h * 0.45f
        val base = min(w, h)

        fun tx(s: Star): Float = cx + cos(s.ang) * s.rad * base
        fun ty(s: Star): Float = cy + sin(s.ang) * s.rad * base

        // 中心辉光
        if (progress > 0.55f) {
            val k = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val r = base * (0.34f + 0.10f * k)
            corePaint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    withAlpha(corePaint.color, (0.55f * k * 255).toInt()),
                    0x00000000
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, corePaint)
        }

        // 连线：在节点之下先画
        if (lineProgress > 0f) {
            for (i in stars.indices) {
                val a = stars[i]
                val b = stars[(i + 1) % stars.size]
                val pa = eased(progress, a.delay)
                val pb = eased(progress, b.delay)
                if (pa <= 0f || pb <= 0f) continue

                val ax = lerp(a.sx * w, tx(a), pa)
                val ay = lerp(a.sy * h, ty(a), pa)
                val bx = lerp(b.sx * w, tx(b), pb)
                val by = lerp(b.sy * h, ty(b), pb)

                linePaint.alpha = (255 * lineProgress * 0.85f).toInt().coerceIn(0, 255)
                canvas.drawLine(ax, ay, bx, by, linePaint)
            }
        }

        // 星点
        for (s in stars) {
            val p = eased(progress, s.delay)
            if (p <= 0f) continue
            val x = lerp(s.sx * w, tx(s), p)
            val y = lerp(s.sy * h, ty(s), p)
            val r = s.size * density

            haloPaint.alpha = (255 * p * 0.55f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, r * 3.2f, haloPaint)

            nodePaint.alpha = (255 * p).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, r, nodePaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}
