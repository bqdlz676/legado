package io.legado.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 落文 · 胶囊开关
 *
 * 不用 SwitchCompat。关＝枯色底、拇指在左；开＝朱砂底、拇指滑到右，
 * 250ms 走 InkEase.spring，带一点过冲。
 */
class InkSwitchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    var checked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!initializing) animateTo(if (value) 1f else 0f)
        }

    var onCheckedChanged: ((Boolean) -> Unit)? = null

    private var pos = 0f
    private var anim: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F1E6.toInt() }
    private val rect = RectF()

    private val trackOff = 0xFFD9CFBA.toInt()
    private val trackOn = 0xFFC23B2E.toInt()

    /** 首次绑定不发动画，直接落位 */
    fun setInitial(v: Boolean) {
        anim?.cancel()
        initializing = true
        checked = v
        initializing = false
        pos = if (v) 1f else 0f
        invalidate()
    }

    private var initializing = false

    private fun animateTo(target: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(pos, target).apply {
            duration = InkDur.CARD
            interpolator = InkEase.spring
            addUpdateListener {
                pos = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(42f).toInt(), dp(23f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)

        // 底色在两色之间过渡
        val c = blend(trackOff, trackOn, pos.coerceIn(0f, 1f))
        track.color = c
        canvas.drawRoundRect(rect, r, r, track)

        val thR = r - dp(2.4f)
        val cx = dp(2.4f) + thR + (w - dp(4.8f) - thR * 2) * pos.coerceIn(0f, 1f)
        canvas.drawCircle(cx, r, thR, thumb)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val rr = (ar + (br - ar) * t).toInt()
        val gg = (ag + (bg - ag) * t).toInt()
        val bl = (ab + (bb - ab) * t).toInt()
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bl
    }

    init {
        isClickable = true
        setOnClickListener {
            checked = !checked
            onCheckedChanged?.invoke(checked)
        }
    }
}
