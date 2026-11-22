package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 落文 · 纪年时间轴
 *
 * 一条横轴，九格刻点；当前这一年的刻点上有一圈缓慢旋转的金色粒子环，
 * 年份数字写在环心。手指拖动时刻度跟着走，松手吸附到最近的整十年。
 */
class InkTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val STEP = 50          // 每格 50 年
        private const val SLOTS = 9           // -4..+4
        private const val FRAME = 45L
    }

    var year: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var onYearChanged: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = d }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SERIF }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cGold = 0xFFD4A54A.toInt()
    private val cCopper = 0xFF8B6914.toInt()
    private val cInk = 0xFF1A1410.toInt()
    private val cDim = 0xFF9B9687.toInt()
    private val cVermilion = 0xFFC23B2E.toInt()

    private var downX = 0f
    private var dragOffset = 0f
    private var dragging = false

    init {
        setWillNotDraw(false)
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(104f).toInt())
    }

    private fun yearText(y: Int): String = if (y <= 0) "前${1 - y}" else "$y"

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val cx = w / 2f
        val axisY = dp(66f)
        val spacing = w / SLOTS

        // 横轴
        linePaint.color = 0x338B6914
        linePaint.strokeWidth = d
        canvas.drawLine(dp(14f), axisY, w - dp(14f), axisY, linePaint)

        val base = year
        for (i in -4..4) {
            val gy = snap(base, i)
            val x = cx + i * spacing + dragOffset
            if (x < dp(6f) || x > w - dp(6f)) continue
            if (i == 0) continue

            // 刻点
            dotPaint.color = cDim
            canvas.drawCircle(x, axisY, dp(1.6f), dotPaint)

            // 年份小字
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = dp(11f)
            textPaint.color = if (i == 0) cInk else cDim
            canvas.drawText(yearText(gy), x, axisY + dp(22f), textPaint)
            if (i == 0) {
                textPaint.textSize = dp(9f)
                textPaint.color = cVermilion
                canvas.drawText("元年", x, axisY + dp(35f), textPaint)
            }
        }

        // 当前年份：金色粒子环 + 大字
        val t = SystemClock.uptimeMillis() / 1000f
        val cy = axisY
        val r = dp(21f)
        for (k in 0 until 16) {
            val a = t * 1.6f + k * (Math.PI.toFloat() * 2f / 16f)
            val px = cx + cos(a.toDouble()).toFloat() * r
            val py = cy + sin(a.toDouble()).toFloat() * r * 0.42f
            val alpha = (90 + 80 * (0.5f + 0.5f * sin((t * 2f + k).toDouble()).toFloat())).toInt().coerceIn(40, 210)
            dotPaint.color = cGold
            dotPaint.alpha = alpha
            canvas.drawCircle(px, py, dp(1.5f), dotPaint)
        }
        dotPaint.alpha = 255

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(24f)
        textPaint.color = cInk
        canvas.drawText(yearText(year), cx, cy + dp(8f), textPaint)

        // 环下朱砂短线
        linePaint.color = cVermilion
        linePaint.strokeWidth = dp(1.8f)
        canvas.drawLine(cx - dp(14f), axisY + dp(31f), cx + dp(14f), axisY + dp(31f), linePaint)

        if (dragging) {
            textPaint.textSize = dp(9f)
            textPaint.color = cDim
            canvas.drawText("松手吸附整十年", cx, height.toFloat() - dp(6f), textPaint)
        }
        postInvalidateDelayed(FRAME)
    }

    private fun snap(base: Int, slot: Int): Int = base + slot * STEP

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                dragOffset = 0f
                dragging = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                dragOffset = event.x - downX
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val spacing = width.toFloat() / SLOTS
                val years = (-dragOffset / spacing) * STEP
                val snapped = (years / STEP).roundToInt() * STEP
                val target = year + snapped
                dragging = false
                dragOffset = 0f
                if (snapped != 0 && abs(snapped) >= 10) {
                    year = target
                    onYearChanged?.invoke(target)
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
