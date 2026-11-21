package io.legado.app.ui.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.random.Random

/**
 * 落文纸面。
 *
 * 不是一块纯色：底色之上压四层 —— 纸浆的纵向受光、左上角烛光的暖晕、
 * 右下角的墨色暗角、以及一张 128px 的宣纸颗粒。全部用 shader 与一张
 * 共享噪点位图绘制，翻页时零分配。
 *
 * 深色底（夜间模式）自动把颗粒翻成白噪、烛光减弱，不会糊成一片。
 */
class InkPaperDrawable @JvmOverloads constructor(
    private val baseColor: Int = PAPER
) : Drawable() {

    companion object {
        const val PAPER = 0xFFF7F1E6.toInt()
        private const val TILE = 128

        /** 全 App 共用的一张噪点，保证每一页的纸纹是同一张纸。 */
        private var noiseDark: Bitmap? = null
        private var noiseLight: Bitmap? = null

        private fun luminanceOf(color: Int): Float {
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        private fun noise(light: Boolean): Bitmap {
            val cached = if (light) noiseLight else noiseDark
            if (cached != null && !cached.isRecycled) return cached
            val bmp = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
            val px = IntArray(TILE * TILE)
            val rnd = Random(20261121)
            for (i in px.indices) {
                // 稀疏而非满屏：约 12% 的像素落墨，alpha 6-17，肉眼是一层纸浆而不是雪花屏
                px[i] = if (rnd.nextInt(100) < 12) {
                    val a = 6 + rnd.nextInt(12)
                    if (light) Color.argb(a, 255, 250, 238) else Color.argb(a, 26, 20, 16)
                } else {
                    0
                }
            }
            bmp.setPixels(px, 0, TILE, 0, 0, TILE, TILE)
            if (light) noiseLight = bmp else noiseDark = bmp
            return bmp
        }
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var builtW = 0
    private var builtH = 0

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        if (b.width() != builtW || b.height() != builtH) build(b.width(), b.height())
        canvas.drawRect(b, fillPaint)
        canvas.drawRect(b, sheenPaint)
        canvas.drawRect(b, candlePaint)
        canvas.drawRect(b, vignettePaint)
        canvas.drawRect(b, grainPaint)
    }

    private fun build(w: Int, h: Int) {
        builtW = w
        builtH = h
        val light = luminanceOf(baseColor) > 0.55f
        val rect = Rect(0, 0, w, h)

        fillPaint.color = baseColor

        // 纸浆受光：上沿微微发亮，下沿压一层墨，像一张斜放在案上的纸
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                if (light) 0x14FFFFFF else 0x0CFFFFFF,
                0x00000000,
                if (light) 0x0E1A1410 else 0x22000000
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )

        // 左上角烛光（2800K 暖调），光源落在画面外，只把斜照的一面提亮
        val warm = if (light) 0x2EFFDFA8 else 0x1AFFC978
        candlePaint.shader = RadialGradient(
            -w * 0.08f, -h * 0.10f, max(w, h) * 1.02f,
            intArrayOf(warm, 0x00FFDFA8),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // 右下角暗角，极轻；正文压得住，纸感才出得来
        vignettePaint.shader = RadialGradient(
            w * 0.52f, h * 0.46f, max(w, h) * 0.92f,
            intArrayOf(0x00000000, 0x00000000, if (light) 0x181A1410 else 0x26000000),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )

        val bmp = noise(light)
        val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        // 固定偏移：同一本书的每一页、每一次重绘，颗粒位置都不跳
        shader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(37f, 61f) })
        grainPaint.shader = shader
    }

    override fun setAlpha(alpha: Int) {
        // 纸面永远全不透明，透明度交给上层 bgAlpha 控制
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
