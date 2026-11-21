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
import kotlin.math.min
import kotlin.random.Random

/**
 * 启动页水墨：宣纸底上一团墨晕开，边缘分三层不同速度扩散，底部一道墨线收束。
 * 固定米色纸底，不随深浅主题切换，保证古籍气质。
 */
class SplashInkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private class Fibre(val y: Float, val x0: Float, val x1: Float, val a: Float)

    private val fibres = ArrayList<Fibre>()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val paperPaint = Paint()
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fibrePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paperPaint.color = PAPER
        inkPaint.color = INK
        fibrePaint.color = FIBRE
        fibrePaint.strokeWidth = 0.8f
        rulePaint.color = INK
        rulePaint.strokeWidth = 1.2f * density
        glowPaint.color = GOLD
        rimPaint.color = GOLD
        rimPaint.strokeWidth = 1.0f * density
        buildFibres()
    }

    private fun buildFibres() {
        val rnd = Random(1979)
        repeat(90) {
            fibres += Fibre(
                y = rnd.nextFloat(),
                x0 = rnd.nextFloat() * 0.35f,
                x1 = 0.65f + rnd.nextFloat() * 0.35f,
                a = 0.02f + rnd.nextFloat() * 0.045f
            )
        }
    }

    fun start(duration: Long = 1200L) {
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                progress = it.animatedValue as Float
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

    private fun ease(from: Float, to: Float): Float =
        ((progress - from) / (to - from)).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(PAPER)

        // 纸纹：极淡横丝，只在中段出现
        val fb = ease(0.10f, 0.85f)
        if (fb > 0f) {
            for (f in fibres) {
                fibrePaint.alpha = (f.a * fb * 255).toInt().coerceIn(0, 255)
                canvas.drawLine(f.x0 * w, f.y * h, f.x1 * w, f.y * h, fibrePaint)
            }
        }

        val cx = w / 2f
        val cy = h * 0.45f
        val base = min(w, h)

        // 暖金光：像纸背后透上来的一层灯
        val kg = ease(0.0f, 0.62f)
        if (kg > 0f) {
            val r = base * (0.50f + 0.34f * kg)
            glowPaint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    withAlpha(GOLD, (0.14f * kg * 255).toInt()),
                    withAlpha(GOLD, (0.05f * kg * 255).toInt()),
                    0
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, glowPaint)
        }
        glowPaint.shader = null

        // 三层墨晕：大团慢、核心快，叠出不规则边缘感
        val layers = floatArrayOf(0.72f, 0.46f, 0.26f)
        val starts = floatArrayOf(0.02f, 0.12f, 0.24f)
        for (i in layers.indices) {
            val k = ease(starts[i], starts[i] + 0.58f)
            if (k <= 0f) continue
            val r = base * layers[i] * k
            val core = ((0.30f + 0.42f * k) * 255).toInt()
            inkPaint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    withAlpha(INK, core),
                    withAlpha(INK, (core * 0.55f).toInt()),
                    withAlpha(INK, 0)
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, inkPaint)

            // 墨晕外圈一道极细金边，光从墨里透出来
            if (i == 2) {
                rimPaint.alpha = (0.22f * k * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, r * 1.006f, rimPaint)
            }
        }
        inkPaint.shader = null

        // 底部墨线：从中心向两端展开
        val kl = ease(0.46f, 0.95f)
        if (kl > 0f) {
            val half = w * 0.16f * kl
            rulePaint.alpha = (0.5f * kl * 255).toInt().coerceIn(0, 255)
            canvas.drawLine(cx - half, cy + base * 0.30f, cx + half, cy + base * 0.30f, rulePaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    companion object {
        private const val PAPER = 0xFFF4EDDD.toInt()
        private const val INK = 0xFF2A2320.toInt()
        private const val FIBRE = 0xFF6B5F4E.toInt()
        private const val GOLD = 0xFFC9A063.toInt()
    }
}
