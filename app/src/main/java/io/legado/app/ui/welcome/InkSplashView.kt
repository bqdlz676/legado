package io.legado.app.ui.welcome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * 启动页整段编排，单 View 自绘，不依赖任何图片资源。
 *
 * 0.00-0.40 墨滴坠落
 * 0.40-0.70 触底炸开 44 粒，悬停
 * 0.70-1.00 粒子被吸回中心
 * 1.00-1.40 竖排「落文」从墨迹里渗出（自下而上显影）
 * 1.20-1.60 水墨远山轮廓浮起，山腰起雾
 * 1.40-1.80 暖金线自左向右扫过，停在「文」末笔，留一点金
 * 1.80-2.40 标语淡入
 * 2.40-2.80 整体淡出，交给下一屏
 */
class InkSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    // 时间轴（0-1 归一，总时长由 start() 的 duration 决定）
    private val tDrop = 0.14f
    private val tBurst = 0.25f
    private val tGather = 0.36f
    private val tWrite = 0.50f
    private val tHill = 0.57f
    private val tGold = 0.64f
    private val tSlogan = 0.82f
    private val tFade = 0.86f

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A1410.toInt()
        style = Paint.Style.FILL
    }
    private val inkSoft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC1A1410.toInt()
        style = Paint.Style.FILL
    }
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4A54A.toInt()
        style = Paint.Style.FILL
    }
    private val copper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8B6914.toInt()
    }
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A4048.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEFE8D8.toInt()
        textSize = dp(64f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val sloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9B9687.toInt()
        textSize = dp(12f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14FFFFFF
        style = Paint.Style.FILL
    }

    private val rand = Random(20261121L)
    private class Spark(
        val ang: Float,
        val speed: Float,
        val size: Float,
        val life: Float,
        val drift: Float
    )

    private val sparks = ArrayList<Spark>(48)
    private val noise = FloatArray(600) // x,y 成对

    private var progress = 0f
    private var animator: ValueAnimator? = null

    // 文字位置（竖排两字）
    private var titleX = 0f
    private var titleBaseY = 0f
    private var lineH = 0f

    init {
        var i = 0
        while (i < 44) {
            sparks.add(
                Spark(
                    ang = rand.nextFloat() * Math.PI.toFloat() * 2f,
                    speed = dp(20f + rand.nextFloat() * 70f),
                    size = dp(1.6f + rand.nextFloat() * 4.2f),
                    life = 0.5f + rand.nextFloat(),
                    drift = rand.nextFloat() * 2f - 1f
                )
            )
            i++
        }
        i = 0
        while (i < noise.size) {
            noise[i] = rand.nextFloat()
            noise[i + 1] = rand.nextFloat()
            i += 2
        }
    }

    fun start(durationMs: Long = 2800L) {
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        titleX = w / 2f
        titleBaseY = h * 0.44f
        lineH = dp(78f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = progress

        // 纯黑底 + 极淡颗粒
        canvas.drawColor(Color.BLACK)
        var i = 0
        while (i < noise.size) {
            canvas.drawCircle(noise[i] * w, noise[i + 1] * h, dp(0.7f), noisePaint)
            i += 2
        }

        val cx = w / 2f
        val cy = titleBaseY - lineH * 0.5f

        // 1. 墨滴坠落
        if (p < tBurst) {
            val t = (p / tDrop).coerceIn(0f, 1f)
            val dropY = h * 0.12f + (cy - h * 0.12f) * (t * t)
            val r = dp(4f + 7f * t)
            // 拖尾
            val trail = Paint(ink).apply {
                shader = LinearGradient(
                    cx, dropY - dp(70f) * t, cx, dropY,
                    0x001A1410, 0xCC1A1410.toInt(), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(cx - r * 0.45f, dropY - dp(70f) * t, cx + r * 0.45f, dropY, trail)
            canvas.drawCircle(cx, dropY, r, ink)
        }

        // 2. 炸开 + 悬停 + 回聚
        if (p >= tBurst) {
            val burst = ((p - tBurst) / (tWrite - tBurst)).coerceIn(0f, 1f)
            // 0-0.55 炸开衰减，0.55-1 回聚
            val outT = (burst / 0.55f).coerceIn(0f, 1f)
            val backT = ((burst - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val easeOut = 1f - (1f - outT) * (1f - outT)
            val easeBack = backT * backT * (3f - 2f * backT)
            for (s in sparks) {
                val rad = s.speed * easeOut * (1f - easeBack)
                val jitter = dp(1.4f) * sin((p * 40f + s.ang).toDouble()).toFloat() * (1f - backT)
                val x = cx + cos(s.ang) * rad + jitter + s.drift * dp(2f) * backT
                val y = cy + sin(s.ang) * rad * 0.8f + jitter
                val alpha = (255f * (1f - backT)).toInt().coerceIn(0, 255)
                inkSoft.alpha = alpha
                canvas.drawCircle(x, y, s.size * (1f - 0.35f * backT), inkSoft)
            }
            inkSoft.alpha = 255
        }

        // 3. 竖排「落文」渗出：自下而上显影
        if (p >= tGather) {
            val t = ((p - tGather) / (tGold - tGather)).coerceIn(0f, 1f)
            val chars = charArrayOf('落', '文')
            val reveal = t * (chars.size + 0.35f)
            for (idx in chars.indices) {
                val appear = (reveal - idx).coerceIn(0f, 1f)
                if (appear <= 0f) continue
                val y = titleBaseY - (chars.size - 1 - idx) * lineH
                val save = canvas.save()
                // 每字自下而上揭开
                val clipTop = y - titlePaint.textSize * 0.92f
                val clipBottom = clipTop + titlePaint.textSize * 0.92f * appear + dp(20f)
                canvas.clipRect(cx - dp(80f), y - titlePaint.textSize * 1.1f, cx + dp(80f), clipBottom)
                titlePaint.alpha = (255 * appear).toInt().coerceIn(0, 255)
                canvas.drawText(chars[idx].toString(), cx, y, titlePaint)
                canvas.restoreToCount(save)
            }
            titlePaint.alpha = 255
        }

        // 4. 水墨远山（只勾线）与山腰雾
        if (p >= tHill) {
            val t = ((p - tHill) / (tGold + 0.12f - tHill)).coerceIn(0f, 1f)
            val hillY = h * 0.70f
            hillPaint.alpha = (120 * t).toInt().coerceIn(0, 160)
            for (layer in 0..1) {
                val path = Path()
                val base = hillY + layer * dp(26f)
                val amp = dp(18f) + layer * dp(10f)
                path.moveTo(-w * 0.05f, base)
                var k = 0
                while (k <= 6) {
                    val fx = w * 0.05f + (w * 0.9f) * k / 6f
                    val fy = base - amp * (if (k % 2 == 0) 1f else 0.45f)
                    val px = w * 0.05f + (w * 0.9f) * (k + 1) / 6f
                    val py = base - amp * (if (k % 2 == 0) 0.35f else 1f)
                    path.quadTo(fx, fy, px, py)
                    k += 2
                }
                canvas.drawPath(path, hillPaint)
            }
            // 雾：横向拉长的柔和亮带
            val fog = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, hillY - dp(10f), 0f, hillY + dp(40f),
                    0x00EFE8D8, (34 * t).toInt().coerceIn(0, 40) shl 24 or 0xEFE8D8,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, hillY - dp(10f), w, hillY + dp(40f), fog)
        }

        // 5. 暖金线扫过（停在「文」末笔）
        if (p >= tGold) {
            val t = ((p - tGold) / (tSlogan - tGold)).coerceIn(0f, 1f)
            val ease = DecelerateInterpolator().getInterpolation(t)
            val startX = w * 0.08f
            val endX = titleX + dp(30f)
            val gx = startX + (endX - startX) * ease
            val gy = titleBaseY + dp(6f)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = dp(1f)
                color = 0xFFD4A54A.toInt()
            }
            canvas.drawLine(startX, gy, gx, gy, linePaint)
            // 扫过处的字面提亮
            if (ease > 0.2f) {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        gx, gy - dp(6f), dp(56f),
                        intArrayOf(0x40D4A54A, 0x00D4A54A),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(gx, gy - dp(6f), dp(56f), glow)
            }
            // 前端一点
            canvas.drawCircle(gx, gy, dp(2.2f), gold)
            if (t >= 1f) {
                // 停在末笔，留一颗金点，慢慢呼吸
                val breath = 0.6f + 0.4f * sin((p * 22f).toDouble()).toFloat()
                gold.alpha = (200 * breath).toInt().coerceIn(0, 255)
                canvas.drawCircle(endX, gy, dp(2.6f), gold)
                gold.alpha = 255
            }
        }

        // 6. 标语
        if (p >= tSlogan) {
            val t = ((p - tSlogan) / (tFade - tSlogan)).coerceIn(0f, 1f)
            sloganPaint.alpha = (200 * t).toInt().coerceIn(0, 255)
            canvas.drawText("读其文，知其人，通其时。", cx, h * 0.80f, sloganPaint)
            sloganPaint.alpha = 255
        }

        // 7. 收尾淡出
        if (p >= tFade) {
            val t = ((p - tFade) / (1f - tFade)).coerceIn(0f, 1f)
            val fadePaint = Paint().apply { color = Color.BLACK }
            fadePaint.alpha = (255 * t).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, fadePaint)
        }
    }
}
