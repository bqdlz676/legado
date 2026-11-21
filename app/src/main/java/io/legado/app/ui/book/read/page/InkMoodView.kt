package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * 落文 · 章节氛围层
 *
 * 不是插图，是粒子：按章节名里的字（战 / 水 / 火 / 雪）换一套粒子形态，
 * 只用极淡的墨色粒子在页面左右留白里勾勒出一点若有若无的轮廓。
 * 正文区域留空，粒子不进字里。
 */
class InkMoodView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val PARTICLE = 48
        private const val FRAME = 60L
    }

    private var mood = 0
    private var seed = 11
    private var lastFrame = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val u = FloatArray(PARTICLE)
    private val phase = FloatArray(PARTICLE)
    private val size = FloatArray(PARTICLE)

    private val cInk = 0xFF3D3426.toInt()
    private val cWater = 0xFF2F5D73.toInt()
    private val cFire = 0xFFA8471F.toInt()
    private val cSnow = 0xFF8C9BA5.toInt()

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
        rebuild(11)
    }

    fun setChapter(title: CharSequence?) {
        val t = title?.toString().orEmpty()
        val m = when {
            t.contains("战") || t.contains("军") || t.contains("伐") || t.contains("兵") -> 1
            t.contains("水") || t.contains("江") || t.contains("河") || t.contains("渡") || t.contains("海") -> 2
            t.contains("火") || t.contains("焚") || t.contains("烧") || t.contains("赤壁") -> 3
            t.contains("雪") || t.contains("寒") || t.contains("霜") || t.contains("冰") -> 4
            else -> 0
        }
        if (m != mood) {
            mood = m
            rebuild(m * 17 + 7)
            invalidate()
        }
    }

    private fun rebuild(s: Int) {
        seed = s
        val r = Random(seed)
        for (i in 0 until PARTICLE) {
            u[i] = r.nextFloat()
            phase[i] = r.nextFloat()
            size[i] = 1.4f + r.nextFloat() * 2.4f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mood == 0) return
        val t = SystemClock.uptimeMillis() / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        paint.strokeWidth = d

        when (mood) {
            1 -> drawWar(canvas, t, w, h, d)
            2 -> drawWater(canvas, t, w, h, d)
            3 -> drawFire(canvas, t, w, h, d)
            4 -> drawSnow(canvas, t, w, h, d)
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastFrame >= FRAME) {
            lastFrame = now
            postInvalidateDelayed(FRAME)
        } else {
            postInvalidateDelayed(FRAME - (now - lastFrame))
        }
    }

    /** 左右留白里的 x：0..1 映射到页面边缘两条带子 */
    private fun edgeX(uu: Float, w: Float): Float {
        val band = w * 0.13f
        return if (uu < 0.5f) (uu * 2f) * band else w - ((uu - 0.5f) * 2f) * band
    }

    /** 战：远处烽燧，左右各三座，粒子堆成细直的塔影 */
    private fun drawWar(canvas: Canvas, t: Float, w: Float, h: Float, d: Float) {
        paint.color = cInk
        for (i in 0 until PARTICLE) {
            val tower = (i / 8) % 6
            val baseX = edgeX((tower + 0.5f) / 6f, w)
            val x = baseX + sin(t * 0.4f + phase[i] * 6f) * 1.5f * d
            val y = h * 0.42f + (i % 8) * 9f * d + sin(t * 0.5f + i) * 2f * d
            paint.alpha = 26 + (i % 3) * 8
            canvas.drawCircle(x, y, size[i] * d * 0.9f, paint)
        }
    }

    /** 水：一道横向波痕贴着边缘，慢慢起伏 */
    private fun drawWater(canvas: Canvas, t: Float, w: Float, h: Float, d: Float) {
        paint.color = cWater
        for (i in 0 until PARTICLE) {
            val x = edgeX(u[i], w)
            val y = h * 0.62f + sin(x * 0.03f + t * 0.7f) * 10f * d + phase[i] * 6f * d
            paint.alpha = 22 + (i % 4) * 5
            canvas.drawCircle(x, y, size[i] * d * 0.8f, paint)
        }
    }

    /** 火：从底部缓缓升起的余烬 */
    private fun drawFire(canvas: Canvas, t: Float, w: Float, h: Float, d: Float) {
        paint.color = cFire
        for (i in 0 until PARTICLE) {
            val x = edgeX(u[i], w) + sin(t * 0.9f + i) * 3f * d
            val p = (t * 0.18f + phase[i]) % 1f
            val y = h * 0.86f - p * h * 0.34f
            paint.alpha = (30 * (1f - p)).toInt().coerceAtLeast(6)
            canvas.drawCircle(x, y, size[i] * d * 0.7f, paint)
        }
    }

    /** 雪：从顶端慢慢落的细粒 */
    private fun drawSnow(canvas: Canvas, t: Float, w: Float, h: Float, d: Float) {
        paint.color = cSnow
        for (i in 0 until PARTICLE) {
            val x = edgeX(u[i], w) + sin(t * 0.5f + phase[i] * 8f) * 4f * d
            val p = (t * 0.1f + phase[i]) % 1f
            val y = p * h * 0.9f
            paint.alpha = 18 + (i % 3) * 6
            canvas.drawCircle(x, y, size[i] * d * 0.6f, paint)
        }
    }
}
