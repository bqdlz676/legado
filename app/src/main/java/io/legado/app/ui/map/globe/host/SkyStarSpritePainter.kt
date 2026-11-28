package io.legado.app.ui.map.globe.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.SparseArray
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkySpecColor
import kotlin.math.PI
import kotlin.math.sin

/**
 * pass3：星点 sprite 批绘——4 档 atlas（1/3/5/9px），星点禁逐帧圆斑（§八.3）。
 *
 * 〔Q-507 帧耗修红〕真资产 8920 颗全量上屏后，本层是整帧 / 过场峰值的主要热路径。
 * 三项不降观感的优化：
 *  - colorFilter 跨帧缓存复用：同色 rgb 只建一次 [PorterDuffColorFilter]，禁逐颗 new；
 *  - 屏外整段跳过：星点半幅 + 四芒外伸余量越出画布，不进着色 / drawBitmap 路径；
 *  - 帧间复用 [Paint] / [RectF]，热路径不 new 列表、不装箱。
 *
 * 像素口径与原逐颗滤镜实现逐位一致：同一 rgb、同一 atlas 档、同一 alpha、同一直径曲线；
 * 只去掉逐颗对象分配与屏外光栅调用，不改星数、不抬星等下限、不动 16/24ms 对照线。
 */
internal class SkyStarSpritePainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private lateinit var atlas: Array<Bitmap>
    /** rgb → 复用滤镜；键为 24-bit RGB，命中即取回，未命中才新建并缓存（Q-507 零逐颗分配）。 */
    private val filterCache = SparseArray<PorterDuffColorFilter>()

    fun render(
        canvas: Canvas,
        stars: ProjectedStars,
        atlas: Array<Bitmap>,
        w: Float,
        h: Float,
        nowMs: Long,
        lowPower: Boolean,
        hoverId: Long,
        pressId: Long,
        pressStartMs: Long,
    ) {
        this.atlas = atlas
        val count = stars.count
        for (i in 0 until count) {
            val mag = stars.mag[i]
            var d = SkyParams.starDiameterPx(mag)
            val id = stars.id[i]
            val held = id == pressId
            if (held && nowMs - pressStartMs >= PRESS_GATE_MS) { // 按下 60ms 门槛 → 1.6× + 1200ms 呼吸（§七.3）
                d *= 1.6f
                d *= (1f + 0.12f * sin(nowMs * 2.0 * PI / SkyParams.FIELD_PULSE_MS)).toFloat()
            } else if (id == hoverId) {
                d *= 1.3f // 触屏无 hover：按下前 120ms 预压（§七.2）
            }
            val x = stars.x[i]
            val y = stars.y[i]
            // 屏外裁剪：余量取四芒外伸 r=d*1.4；越界整段跳过，不建滤镜、不入光栅。
            val margin = d * 1.4f
            if (x < -margin || x > w + margin || y < -margin || y > h + margin) continue

            val bv = stars.bv[i]
            val rgb = if (bv.isNaN()) SkySpecColor.starColorFromMag(mag) else SkySpecColor.starColorFromBV(bv)
            var alpha = 1f
            if (mag < SkyParams.TWINKLE_MAG) { // 闪烁只给 mag<2.5：独立相位 0.6–1.4Hz ±8%（§八.8）
                val phase = skyHash(i, 11) * 2.0 * PI
                val freq = 0.6 + skyHash(i, 12) * 0.8
                alpha *= 1f + 0.08f * sin(2.0 * PI * freq * nowMs / 1000.0 + phase).toFloat()
            }
            paint.colorFilter = filterFor(rgb)
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            val half = d / 2f
            rect.set(x - half, y - half, x + half, y + half)
            canvas.drawBitmap(atlas[tierOf(d)], null, rect, paint)
            if (mag < SkyParams.STAR_SPIKE_MAG) { // 四芒只给 mag<0.5（§八.8），同 pass 补短芒线
                paint.colorFilter = null
                paint.color = (0xFF shl 24) or rgb
                val r = d * 1.4f
                canvas.drawLine(x - r, y, x + r, y, paint)
                canvas.drawLine(x, y - r, x, y + r, paint)
            }
        }
        paint.colorFilter = null
        paint.alpha = 255
        if (!lowPower) {
            drawDust(canvas, w, h)
            drawMeteor(canvas, w, h, nowMs)
        }
    }

    /** 取 rgb 复用滤镜；未命中才新建入缓存。滤镜对象跨帧存活，缓存键不随帧清空。 */
    private fun filterFor(rgb: Int): PorterDuffColorFilter {
        filterCache[rgb]?.let { return it }
        val filter = PorterDuffColorFilter((0xFF shl 24) or rgb, PorterDuff.Mode.SRC_IN)
        filterCache.put(rgb, filter)
        return filter
    }

    /** 尘粒 32 颗 1px alpha 0.15 缓慢漂移（§八.9）；位置速度全哈希确定，无随机态泄漏。 */
    private fun drawDust(canvas: Canvas, w: Float, h: Float) {
        paint.color = skyArgb(GlobeTokens.textTertiary, SkySpecColor.DUST_ALPHA_RATIO)
        repeat(32) { i ->
            val drift = (System.currentTimeMillis() / 1000.0) * 0.004 * (1 + skyHash(i, 23))
            val dx = ((skyHash(i, 21) + drift) % 1.0 * w).toFloat()
            val dy = (skyHash(i, 22) % 1.0 * h).toFloat()
            rect.set(dx - 0.5f, dy - 0.5f, dx + 0.5f, dy + 0.5f)
            canvas.drawBitmap(atlas[0], null, rect, paint)
        }
    }

    /** 流星 90–240s 间隔、1.2s 划 8–14°、尾迹 0.4s（§八.9）；确定性周期，便于测试注入。 */
    private fun drawMeteor(canvas: Canvas, w: Float, h: Float, nowMs: Long) {
        val interval = SkyParams.METEOR_INTERVAL_MIN_MS + (skyHash(3, 31) *
            (SkyParams.METEOR_INTERVAL_MAX_MS - SkyParams.METEOR_INTERVAL_MIN_MS)).toLong()
        val t = nowMs % interval
        if (t > 1200L) return
        val progress = t / 1200.0
        val hx = (0.2 + 0.6 * skyHash(5, 33) * w).toFloat()
        val hy = (0.25 + 0.3 * skyHash(6, 34) * h).toFloat()
        paint.color = (0xFF shl 24) or SkySpecColor.METEOR_HEAD_RGB
        paint.alpha = ((1.0 - progress) * 255).toInt()
        canvas.drawLine(hx - 0.12f * w * progress.toFloat(), hy + 0.06f * h * progress.toFloat(), hx, hy, paint)
        paint.alpha = 255
        rect.set(hx - 1.5f, hy - 1.5f, hx + 1.5f, hy + 1.5f)
        canvas.drawBitmap(atlas[1], null, rect, paint)
    }

    private fun tierOf(diameterPx: Float): Int = when {
        diameterPx <= 1.2f -> 0
        diameterPx <= 2.4f -> 1
        diameterPx <= 4f -> 2
        else -> 3
    }
}

/** 按下放大门槛 60ms：低于门槛只记预压，不进 1.6× 两阶段（§七.3）。 */
private const val PRESS_GATE_MS: Long = 60L
