package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * 落文 · 宣纸颗粒底盘
 *
 * 全页铺一层 3%–6% 的白噪，128px 一张共享 tile，平铺不打折扣。
 * 放在内容下面当纸底，不挡点击。
 */
class InkGrainView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        paint.shader = makeShader()
        paint.alpha = 255
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        private var shader: BitmapShader? = null

        @Synchronized
        private fun makeShader(): BitmapShader {
            shader?.let { return it }
            val size = 128
            val px = IntArray(size * size)
            val rnd = Random(20261122)
            for (i in px.indices) {
                val v = 255
                val a = rnd.nextInt(0, 16)
                px[i] = Color.argb(a, v, v, v)
            }
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, size, 0, 0, size, size)
            val s = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            shader = s
            return s
        }
    }
}
