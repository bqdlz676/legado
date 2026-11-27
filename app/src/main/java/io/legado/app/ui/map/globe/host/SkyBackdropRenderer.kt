package io.legado.app.ui.map.globe.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.contract.SkyFrame
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkySpecColor
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.proj.StarProjector
import io.legado.app.ui.map.globe.terrain.FbmNoise
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 地上观地平以下压暗覆盖比率（§八.11）；夜空底二次叠压，不写裸黑。 */
internal const val GROUND_DARKEN_RATIO: Float = 0.55f

/** pass1：星空底离屏位图，视角/缩放不变则整幅复用（§八.1）。独立成文件给主渲染器留行数预算。 */
internal class SkyBackdropRenderer {
    private val fbm = FbmNoise(20261128); private var cachedKey: String? = null
    fun render(cached: Bitmap?, key: String, sky: SkyFrame, w: Float, h: Float, lowPower: Boolean, offline: Boolean): Bitmap {
        if (cached != null && cached.width == w.toInt() && cached.height == h.toInt() && key == cachedKey) return cached
        cached?.recycle()
        val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val base = Paint(Paint.ANTI_ALIAS_FLAG) // 天顶 skyNightBase → 地平叠暖辉 0.10，禁纯黑（§八.6）
        base.color = skyArgb(GlobeTokens.skyNightBase); c.drawRect(0f, 0f, w, h, base)
        drawHorizon(c, sky, w, h)
        if (!lowPower) drawGalaxy(c, w, h)
        drawDensity(c, w, h, sky.pyramidMix, offline || lowPower)
        drawGrid(c, sky, w, h)
        cachedKey = key
        return bmp
    }
    /** 地上观：地平线下压暗、上方 5° 辉光带（§八.11）；天外沿用底部暖辉渐变（§八.6）。 */
    private fun drawHorizon(c: Canvas, sky: SkyFrame, w: Float, h: Float) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG); glow.color = skyArgb(GlobeTokens.skyHorizonGlow)
        if (sky.mode != SkyViewMode.SKY_EARTH) { c.drawRect(0f, h * 0.55f, w, h, glow); return }
        val focal = (min(w, h) / 2.0) / Math.tan(SkyParams.GROUND_VIEW_FOV_DEG / 2.0 * PI / 180.0)
        val pitch = (sky.cameraYawPitch?.pitchDeg ?: 0.0) * PI / 180.0
        val hy = (h / 2.0 + focal * Math.tan(pitch)).toFloat(); val bandH = (focal * Math.tan(SkyParams.OBSERVER_HORIZON_GLOW_DEG * PI / 180.0)).toFloat()
        c.drawRect(0f, (hy - bandH).coerceAtLeast(0f), w, hy, glow)
        val dark = Paint(Paint.ANTI_ALIAS_FLAG); dark.color = skyArgb(GlobeTokens.skyNightBase, GROUND_DARKEN_RATIO)
        c.drawRect(0f, hy, w, h, dark)
    }
    /** 银河：暗带掩膜 + 三层 fbm，opacity 0.25，核心暖白外围暖灰（§八.7）。 */
    private fun drawGalaxy(c: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (gx in 0..24) for (gy in 0..12) {
            val n = fbm.fbm(gx / 8.0, gy / 8.0, 3); if (n < 0.42) continue
            val px = gx / 24f * w; val py = h * 0.5f + (gy / 12f - 0.5f) * h * 0.35f
            paint.color = ((0.25f * n * 255f).toInt().coerceIn(0, 255) shl 24) or (SkySpecColor.STAR_ANCHOR_MID_RGB and 0xFFFFFF)
            c.drawRect(px - 2f, py - 2f, px + 2f, py + 2f, paint)
        }
    }
    /** 密度底图：当前层与相邻层 alpha 连续混合；离线 / 低配走低层（§八.1、§七.8）。 */
    private fun drawDensity(c: Canvas, w: Float, h: Float, pyramidMix: Float, lowerOnly: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG); val step = if (lowerOnly) 14 else 9
        for (gx in 0 until (w / step).toInt()) for (gy in 0 until (h / step).toInt()) {
            if (skyHash(gx * 31 + gy, 7) > 0.55) continue
            paint.color = skyArgb(GlobeTokens.textTertiary, (if (lowerOnly) 0.10f else 0.14f) * (1f - pyramidMix * 0.3f))
            val px = gx * step + skyHash(gx, gy).toFloat() * step; val py = gy * step + skyHash(gy, gx).toFloat() * step
            c.drawRect(px - 0.4f, py - 0.4f, px + 0.4f, py + 0.4f, paint)
        }
    }
    /** 参考网格：经纬 0.8px 灰 0.12；天赤道 1.0px 石青 0.30（基准线专用，切片-26 第五节声明）。 */
    private fun drawGrid(c: Canvas, sky: SkyFrame, w: Float, h: Float) {
        val focal = (min(w, h) / 2.0) / Math.tan(SkyParams.GROUND_VIEW_FOV_DEG / 2.0 * PI / 180.0); val tmp = FloatArray(3)
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skyArgb(GlobeTokens.textTertiary, SkyParams.GRID_LINE_ALPHA_RATIO); strokeWidth = SkyParams.GRID_LINE_WIDTH_PX }
        trace(c, sky, focal, w, h, tmp, grid, fixedDec = null)
        val eq = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skyArgb(GlobeTokens.stoneBlue, SkyParams.EQUATOR_LINE_ALPHA_RATIO); strokeWidth = SkyParams.EQUATOR_LINE_WIDTH_PX }
        trace(c, sky, focal, w, h, tmp, eq, fixedDec = 0.0)
        val ec = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skyArgb(GlobeTokens.brandGold, SkyParams.ECLIPTIC_LINE_ALPHA_RATIO); strokeWidth = SkyParams.ECLIPTIC_LINE_WIDTH_PX } // 黄道 1.0px brandGold 0.30（§六.9 基准线专用）
        traceEcliptic(c, sky, focal, w, h, tmp, ec)
    }
    /** 黄道：黄经 λ 按黄赤交角 23.44° 换算 RA/Dec 后采样投影。 */
    private fun traceEcliptic(c: Canvas, sky: SkyFrame, focal: Double, w: Float, h: Float, tmp: FloatArray, paint: Paint) {
        val eps = 23.44 * PI / 180.0
        var px = 0f; var py = 0f; var prev = false
        for (lam in 0 until 360 step 5) {
            val l = lam * PI / 180.0; val cl = cos(l); val sl = sin(l)
            val dec = asin(sl * sin(eps)) * 180.0 / PI; val ra = (atan2(sl * cos(eps), cl) * 180.0 / PI + 360.0) % 360.0
            val ok = StarProjector.project(sky.mode, ra, dec, 0.0, 0.0, focal,
                w / 2.0, h / 2.0, 34.34, 108.94, sky.siderealTimeDeg, tmp)
            if (!ok) { prev = false; continue }
            if (prev) c.drawLine(px, py, tmp[0], tmp[1], paint)
            px = tmp[0]; py = tmp[1]; prev = true
        }
    }
    /** 通用网格采样；fixedDec 非空 = 天赤道纬线（遍历 RA），否则遍历 12 条经线。 */
    private fun trace(c: Canvas, sky: SkyFrame, focal: Double, w: Float, h: Float, tmp: FloatArray, paint: Paint, fixedDec: Double?) {
        val raStep = if (fixedDec == null) 30 else 5
        val decStart = if (fixedDec == null) -80 else 0; val decEnd = if (fixedDec == null) 80 else 0
        val decStep = if (fixedDec == null) 10 else 1
        for (ra in 0 until 360 step raStep) {
            var px = 0f; var py = 0f; var prev = false
            for (dec in decStart..decEnd step decStep) {
                val ok = StarProjector.project(sky.mode, ra.toDouble(), fixedDec ?: dec.toDouble(),
                    0.0, 0.0, focal, w / 2.0, h / 2.0, 34.34, 108.94, sky.siderealTimeDeg, tmp)
                if (!ok) { prev = false; continue }
                if (prev) c.drawLine(px, py, tmp[0], tmp[1], paint)
                px = tmp[0]; py = tmp[1]; prev = true
            }
        }
    }
}
