package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.sin

/**
 * 书架氛围层：极淡的金色微粒缓慢上浮。
 *
 * 不接收触摸（clickable=false / focusable=false），纯装饰；
 * 不可见时自动停帧，不空转耗电。
 */
class InkDustView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density
    private val rand = Random(20261121L)

    private class Dust(
        val x: Float,
        val y: Float,
        val r: Float,
        val speed: Float,
        val phase: Float,
        val alpha: Int
    )

    private val dusts = ArrayList<Dust>(18)
    private var startMs = 0L
    private var running = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4A54A.toInt()
        style = Paint.Style.FILL
    }

    init {
        isClickable = false
        isFocusable = false
        var i = 0
        while (i < 18) {
            dusts.add(
                Dust(
                    x = rand.nextFloat(),
                    y = rand.nextFloat(),
                    r = (0.8f + rand.nextFloat() * 1.6f) * d,
                    speed = 0.006f + rand.nextFloat() * 0.014f,
                    phase = rand.nextFloat() * 6.28f,
                    alpha = 40 + rand.nextInt(50)
                )
            )
            i++
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        running = visibility == VISIBLE
        if (running) {
            startMs = System.currentTimeMillis()
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running) return
        val now = System.currentTimeMillis()
        val t = (now - startMs) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        for (du in dusts) {
            val y = ((du.y - t * du.speed) % 1f + 1f) % 1f
            val x = du.x + sin((t * 0.7f + du.phase).toDouble()).toFloat() * 0.012f
            paint.alpha = (du.alpha * (0.55f + 0.45f * sin((t * 1.3f + du.phase).toDouble()).toFloat()))
                .toInt().coerceIn(0, 255)
            canvas.drawCircle(x * w, y * h, du.r, paint)
        }
        paint.alpha = 255
        postInvalidateOnAnimation()
    }
}
