package io.legado.app.ui.map.globe.host
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.contract.SkyFrame
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkySpecColor
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.StarStore
import io.legado.app.ui.map.globe.astro.layer.AsterismGroup
import io.legado.app.ui.map.globe.astro.layer.AsterismLayer
import io.legado.app.ui.map.globe.astro.layer.ConstellationLayer
import io.legado.app.ui.map.globe.astro.layer.FieldAllocationLayer
import io.legado.app.ui.map.globe.astro.pick.StarPicker
import io.legado.app.ui.map.globe.astro.proj.StarProjector
import io.legado.app.ui.map.globe.render.FrameSnapshot
import io.legado.app.ui.map.globe.terrain.FbmNoise
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
/** 星空三 pass（§八）：pass1 [SkyBackdropRenderer] 离屏位图；pass2 [SkyLineRenderer] 线框；pass3 [SkyStarSpriteRenderer] sprite 批绘（4 档 atlas 1/3/5/9px）。跟随相机的 2D 投影背景，不做球壳网格；`SKY_PROJECTION_FAR=900` 只是深度常量。 */
object SkyCanvasRenderer {
    // 跨态状态面：默认值保证未接线 / 地球仪态行为不变，接线单按帧回填（§七）。
    @JvmField var lowPower: Boolean = false // 禁用：magLimit 封顶 6.5，银河/尘粒/流星关闭（§七.5）
    @JvmField var offline: Boolean = false // 离线：未缓存天区走低层密度底图（§七.8）
    @JvmField var loadingProgress: Float = -1f // 加载 0..1；<0 非加载；顶部 1px 金线（§七.4）
    @JvmField var errorHint: String? = null // 错误态一次性提示；null 无错误（§七.7）
    @JvmField var hoverId: Long = -1L // 悬停星 id（§七.2：1.3× 120ms，抬手回弹无残留）
    @JvmField var pressId: Long = -1L // 按下星 id（§七.3：60ms 1.6× → 1200ms 呼吸）
    @JvmField var pressStartMs: Long = 0L; @JvmField var showNames: Boolean = true // 长按空白显隐名称（§九.3），手势接线单绑
    var hotZones: List<HotZone> = emptyList() // 热区 ≥48dp（视觉 28dp），禁加 Compose 控件（§九.4）
        private set
    private val zoneBuffer = ArrayList<HotZone>() // 热区缓冲帧间复用，主循环零 new 列表（§十四.16）
    private val constellationLayer = ConstellationLayer()
    private val asterismLayer = AsterismLayer()
    private val fieldLayer = FieldAllocationLayer()
    private val projected = ProjectedStars()
    private var picker: StarPicker? = null
    private var backdrop: Bitmap? = null
    private var backdropKey: String? = null
    private val atlas = arrayOfNulls<Bitmap>(4)
    private val backdropRenderer = SkyBackdropRenderer()
    private val lineRenderer = SkyLineRenderer()
    private val starRenderer = SkyStarSpriteRenderer()
    /** 帧绘制入口；store 为 null / 占位态时回落种子口径程序化散点 + 示意横幅（§七.6）。 */
    fun draw(canvas: Canvas, snapshot: FrameSnapshot, widthPx: Float, heightPx: Float, labelPaint: Paint, store: StarStore? = null, density: Float = 1f, nowMs: Long = System.currentTimeMillis()) {
        val sky = snapshot.sky ?: return
        if (widthPx <= 0f || heightPx <= 0f) return
        var magLimit = sky.magLimit
        if (lowPower) magLimit = min(magLimit, SkyParams.MAG_BRIGHT_ENTITY)
        val placeholder = store == null || store.isPlaceholder
        val key = "${sky.mode}|${sky.cameraYawPitch?.yawDeg}|${sky.cameraYawPitch?.pitchDeg}|" +
            "${sky.skyBlend}|$widthPx|$heightPx|$lowPower|$offline"
        backdrop = backdropRenderer.render(backdrop, key, sky, widthPx, heightPx, lowPower, offline)
        canvas.drawBitmap(backdrop!!, 0f, 0f, null)
        projected.clear()
        projectStars(sky, store, placeholder, widthPx, heightPx, magLimit, projected)
        val p = picker ?: StarPicker(density).also { picker = it }
        p.rebuild(projected.x, projected.y, projected.mag, projected.id, projected.count)
        lineRenderer.render(canvas, sky, projected, constellationLayer, asterismLayer, fieldLayer, nowMs, pressStartMs)
        starRenderer.render(canvas, projected, ensureAtlas(), widthPx, heightPx, nowMs, lowPower, hoverId, pressId)
        renderOverlays(canvas, sky, widthPx, heightPx, labelPaint, density, placeholder)
    }
    private fun projectStars(sky: SkyFrame, store: StarStore?, placeholder: Boolean, w: Float, h: Float, magLimit: Float, out: ProjectedStars) {
        if (placeholder || store == null) { // 空态兜底：确定性散点，种子 9173 口径（§七.6）
            repeat(300) { i ->
                out.add(skyHash(i, 1).toFloat() * w, skyHash(i, 2).toFloat() * h,
                    2.0f + skyHash(i, 3).toFloat() * 4.5f, -1L - i.toLong())
            }
            return
        }
        val catalog = store.catalog
        val fov = if (sky.mode == SkyViewMode.SKY_EARTH) SkyParams.GROUND_VIEW_FOV_DEG else 90.0
        val focal = (min(w, h) / 2.0) / Math.tan(fov / 2.0 * PI / 180.0)
        val yaw = sky.cameraYawPitch?.yawDeg ?: 0.0; val pitch = sky.cameraYawPitch?.pitchDeg ?: 0.0
        val lat = sky.observerLatLon?.latDeg ?: 34.34; val lon = sky.observerLatLon?.lonDeg ?: 108.94
        val tmp = FloatArray(3) // 无定位默认长安示意，不弹权限墙（§十三.5）
        for (i in 0 until catalog.size) {
            val mag = catalog.magAt(i)
            if (mag > magLimit) continue // 连续裁剪，无整层突现（§七.1）
            val ok = StarProjector.project(sky.mode, catalog.raAt(i), catalog.decAt(i),
                yaw, pitch, focal, w / 2.0, h / 2.0, lat, lon, sky.siderealTimeDeg, tmp)
            if (ok) out.add(tmp[0], tmp[1], mag, catalog.idAt(i))
        }
    }
    private fun renderOverlays(canvas: Canvas, sky: SkyFrame, w: Float, h: Float, labelPaint: Paint, density: Float, placeholder: Boolean) {
        if (loadingProgress >= 0f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = skyArgb(GlobeTokens.brandGold)
            canvas.drawRect(0f, 0f, w * loadingProgress.coerceIn(0f, 1f), 1f, paint)
        }
        errorHint?.let {
            labelPaint.color = skyArgb(GlobeTokens.textAccent)
            labelPaint.textSize = SkyParams.SHEET_BODY_FONT_SP * density
            canvas.drawText(it, 16f * density, 40f * density, labelPaint)
        }
        if (placeholder) {
            labelPaint.color = skyArgb(GlobeTokens.textTertiary)
            labelPaint.textSize = SkyParams.SHEET_BODY_FONT_SP * density
            val msg = "星表未载，当前为示意星空"
            canvas.drawText(msg, w / 2f - labelPaint.measureText(msg) / 2f, h * 0.78f, labelPaint)
        }
        drawHotZones(canvas, sky, w, density, labelPaint)
        drawSelectedLabel(canvas, density, labelPaint)
    }
    /** 顶部「星官/星座」「天外/地上」：canvas 直画；热区 48dp，视觉 28dp（§九.4）。 */
    private fun drawHotZones(canvas: Canvas, sky: SkyFrame, w: Float, density: Float, paint: Paint) {
        val side = SkyParams.HIT_ZONE_SIDE_DP * density
        paint.textSize = SkyParams.STAR_LABEL_FONT_SP * density
        val cx = w / 2f
        paint.color = skyArgb(GlobeTokens.brandGold) // 默认星官（§〇.2）
        canvas.drawText("星官", cx - side, 24f * density, paint)
        paint.color = skyArgb(GlobeTokens.textTertiary)
        canvas.drawText("星座", cx + 8f * density, 24f * density, paint)
        val rx = w - side - 16f * density
        val earth = sky.mode == SkyViewMode.SKY_EARTH
        paint.color = skyArgb(if (!earth) GlobeTokens.brandGold else GlobeTokens.textTertiary)
        canvas.drawText("天外", rx, 24f * density, paint)
        paint.color = skyArgb(if (earth) GlobeTokens.brandGold else GlobeTokens.textTertiary)
        canvas.drawText("地上", rx + side, 24f * density, paint)
        zoneBuffer.clear()
        zoneBuffer.add(HotZone("mode-asterism", "星官", RectF(cx - side, 0f, cx, side)))
        zoneBuffer.add(HotZone("mode-constellation", "星座", RectF(cx, 0f, cx + side, side)))
        zoneBuffer.add(HotZone("view-heaven", "天外", RectF(rx, 0f, rx + side, side)))
        zoneBuffer.add(HotZone("view-earth", "地上", RectF(rx + side, 0f, rx + 2 * side, side)))
        hotZones = zoneBuffer
    }
    /** 选中星官名：14sp 竖排；字 brandGold、底 bgOverlay 0.45、边 1px brandCopper（§六.7）。 */
    private fun drawSelectedLabel(canvas: Canvas, density: Float, paint: Paint) {
        if (!showNames) return
        val group = asterismLayer.selectedGroup() ?: return
        paint.textSize = SkyParams.STAR_LABEL_FONT_SP * density
        val box = RectF(8f * density, 56f * density, 48f * density, (56 + group.name.length * 14).toFloat() * density)
        canvas.drawRect(box, Paint().apply { color = skyArgb(GlobeTokens.bgOverlay, SkyParams.ASTERISM_LABEL_BG_ALPHA_RATIO) })
        canvas.drawRect(box, Paint().apply { color = skyArgb(GlobeTokens.brandCopper); strokeWidth = 1f; style = Paint.Style.STROKE })
        var by = 64f * density
        group.name.forEach { ch ->
            paint.color = skyArgb(GlobeTokens.brandGold)
            canvas.drawText(ch.toString(), 16f * density, by, paint)
            by += SkyParams.STAR_LABEL_FONT_SP * density
        }
    }
    /** 4 档 atlas 预烘焙（1/3/5/9px）；光晕三层 1.0/0.35/0.12 倍率（§八.8），首用一次烘焙。 */
    private fun ensureAtlas(): Array<Bitmap> {
        if (atlas[0] != null) return atlas.requireNoNulls()
        floatArrayOf(1f, 3f, 5f, 9f).forEachIndexed { i, d ->
            val px = d.toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val shader = RadialGradient(px / 2f, px / 2f, px / 2f,
                intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(153, 255, 255, 255),
                    Color.argb(31, 255, 255, 255)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), Paint().apply { this.shader = shader })
            atlas[i] = bmp
        }
        return atlas.requireNoNulls()
    }
}
data class HotZone(val id: String, val label: String, val rect: RectF) // 画布热区：id 供接线单命中判定，rect 为 ≥48dp 透明外扩框
/** 帧间复用的投影星缓冲；扩容 2 倍拷贝，热路径不 new 列表（§十二.2）。 */
private class ProjectedStars {
    var count = 0
    var x = FloatArray(256); var y = FloatArray(256)
    var mag = FloatArray(256); var id = LongArray(256)
    fun clear() { count = 0 }
    fun add(px: Float, py: Float, m: Float, starId: Long) {
        if (count >= x.size) {
            val cap = x.size * 2
            x = x.copyOf(cap); y = y.copyOf(cap); mag = mag.copyOf(cap); id = id.copyOf(cap)
        }
        x[count] = px; y[count] = py; mag[count] = m; id[count] = starId; count++
    }
}
private class SkyBackdropRenderer { // pass1：星空底离屏位图，视角/缩放不变则整幅复用（§八.1）
    private val fbm = FbmNoise(20261128); private var cachedKey: String? = null
    fun render(cached: Bitmap?, key: String, sky: SkyFrame, w: Float, h: Float, lowPower: Boolean, offline: Boolean): Bitmap {
        if (cached != null && cached.width == w.toInt() && cached.height == h.toInt() && key == cachedKey) return cached
        cached?.recycle()
        val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val base = Paint(Paint.ANTI_ALIAS_FLAG) // 天顶 skyNightBase → 地平叠暖辉 0.10，禁纯黑（§八.6）
        base.color = skyArgb(GlobeTokens.skyNightBase); c.drawRect(0f, 0f, w, h, base)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG); glow.color = skyArgb(GlobeTokens.skyHorizonGlow)
        c.drawRect(0f, h * 0.55f, w, h, glow)
        if (!lowPower) drawGalaxy(c, w, h)
        drawDensity(c, w, h, sky.pyramidMix, offline || lowPower)
        drawGrid(c, sky, w, h)
        cachedKey = key
        return bmp
    }
    /** 银河：暗带掩膜 + 三层 fbm，opacity 0.25，核心暖白外围暖灰（§八.7）。 */
    private fun drawGalaxy(c: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (gx in 0..24) for (gy in 0..12) {
            val n = fbm.fbm(gx / 8.0, gy / 8.0, 3)
            if (n < 0.42) continue
            val px = gx / 24f * w; val py = h * 0.5f + (gy / 12f - 0.5f) * h * 0.35f
            paint.color = ((0.25f * n * 255f).toInt().coerceIn(0, 255) shl 24) or (SkySpecColor.STAR_ANCHOR_MID_RGB and 0xFFFFFF)
            c.drawRect(px - 2f, py - 2f, px + 2f, py + 2f, paint)
        }
    }
    /** 密度底图：当前层与相邻层 alpha 连续混合；离线 / 低配走低层（§八.1、§七.8）。 */
    private fun drawDensity(c: Canvas, w: Float, h: Float, pyramidMix: Float, lowerOnly: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val step = if (lowerOnly) 14 else 9
        for (gx in 0 until (w / step).toInt()) for (gy in 0 until (h / step).toInt()) {
            if (skyHash(gx * 31 + gy, 7) > 0.55) continue
            paint.color = skyArgb(GlobeTokens.textTertiary, (if (lowerOnly) 0.10f else 0.14f) * (1f - pyramidMix * 0.3f))
            val px = gx * step + skyHash(gx, gy).toFloat() * step; val py = gy * step + skyHash(gy, gx).toFloat() * step
            c.drawRect(px - 0.4f, py - 0.4f, px + 0.4f, py + 0.4f, paint)
        }
    }
    /** 参考网格：经纬 0.8px 灰 0.12；天赤道 1.0px 石青 0.30（基准线专用，切片-26 第五节声明）。 */
    private fun drawGrid(c: Canvas, sky: SkyFrame, w: Float, h: Float) {
        val focal = (min(w, h) / 2.0) / Math.tan(SkyParams.GROUND_VIEW_FOV_DEG / 2.0 * PI / 180.0)
        val tmp = FloatArray(3)
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skyArgb(GlobeTokens.textTertiary, SkyParams.GRID_LINE_ALPHA_RATIO)
            strokeWidth = SkyParams.GRID_LINE_WIDTH_PX
        }
        trace(c, sky, focal, w, h, tmp, grid, fixedDec = null)
        val eq = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skyArgb(GlobeTokens.stoneBlue, SkyParams.EQUATOR_LINE_ALPHA_RATIO)
            strokeWidth = SkyParams.EQUATOR_LINE_WIDTH_PX
        }
        trace(c, sky, focal, w, h, tmp, eq, fixedDec = 0.0)
        val ec = Paint(Paint.ANTI_ALIAS_FLAG).apply { // 黄道 1.0px brandGold 0.30（§六.9 基准线专用）
            color = skyArgb(GlobeTokens.brandGold, SkyParams.ECLIPTIC_LINE_ALPHA_RATIO)
            strokeWidth = SkyParams.ECLIPTIC_LINE_WIDTH_PX
        }
        traceEcliptic(c, sky, focal, w, h, tmp, ec)
    }
    /** 黄道：黄经 λ 按黄赤交角 23.44° 换算 RA/Dec 后采样投影。 */
    private fun traceEcliptic(c: Canvas, sky: SkyFrame, focal: Double, w: Float, h: Float, tmp: FloatArray, paint: Paint) {
        val eps = 23.44 * PI / 180.0
        var px = 0f; var py = 0f; var prev = false
        for (lam in 0 until 360 step 5) {
            val l = lam * PI / 180.0; val cl = cos(l); val sl = sin(l)
            val dec = asin(sl * sin(eps)) * 180.0 / PI
            val ra = (atan2(sl * cos(eps), cl) * 180.0 / PI + 360.0) % 360.0
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
        val decStart = if (fixedDec == null) -80 else 0
        val decEnd = if (fixedDec == null) 80 else 0
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
/** pass2：线框——星座 / 星官 / 分野边 / 选中光环，按样式分组描边（§八.2）。 */
private class SkyLineRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Path()
    fun render(canvas: Canvas, sky: SkyFrame, stars: ProjectedStars, constellation: ConstellationLayer, asterism: AsterismLayer, field: FieldAllocationLayer, nowMs: Long, pressStartMs: Long) {
        // §八.2：按样式收拢进单条 Path，每组一次描边（星座 / 星官各一条 Path）。
        val cPath = Path(); val aPath = Path()
        constellation.visibleSegments().forEach { seg -> // 星座参考线 0.8px / 灰 0.45（§六.6）
            val a = findPos(stars, seg.fromStarId) ?: return@forEach
            val b = findPos(stars, seg.toStarId) ?: return@forEach
            cPath.moveTo(a.first, a.second); cPath.lineTo(b.first, b.second)
        }
        paint.style = Paint.Style.STROKE
        paint.color = skyArgb(GlobeTokens.textTertiary, SkyParams.CONSTELLATION_LINE_ALPHA_RATIO)
        paint.strokeWidth = SkyParams.CONSTELLATION_LINE_WIDTH_PX
        canvas.drawPath(cPath, paint)
        val group = asterism.selectedGroup() // 星官内容线 1.0px / 金 0.55；选中组按 linkToNext 连线
        group?.members?.forEachIndexed { idx, member ->
            if (!member.linkToNext || idx == 0) return@forEachIndexed
            val prev = group.members.getOrNull(idx - 1) ?: return@forEachIndexed
            val a = findPos(stars, prev.starId) ?: return@forEachIndexed
            val b = findPos(stars, member.starId) ?: return@forEachIndexed
            aPath.moveTo(a.first, a.second); aPath.lineTo(b.first, b.second)
        }
        paint.color = skyArgb(GlobeTokens.brandGold, SkyParams.ASTERISM_LINE_ALPHA_RATIO)
        paint.strokeWidth = SkyParams.ASTERISM_LINE_WIDTH_PX
        canvas.drawPath(aPath, paint)
        val sel = sky.selectedId ?: return // 选中光环 colorGlow；呼吸 1200ms（§七.3、§十六.10）
        val pos = findPos(stars, sel) ?: return
        val breath = (1f + 0.12f * sin((nowMs - pressStartMs) * 2.0 * PI / SkyParams.FIELD_PULSE_MS)).toFloat()
        paint.color = skyArgb(GlobeTokens.colorGlow)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        ring.reset(); ring.addCircle(pos.first, pos.second, 10f * breath, Path.Direction.CW)
        canvas.drawPath(ring, paint)
        field.selectedRegion?.let { // 分野脉冲（§十一.1）；地球侧高亮入口缺口见层类交接说明
            ring.reset(); ring.addCircle(pos.first, pos.second, 16f * breath, Path.Direction.CW)
            canvas.drawPath(ring, paint)
        }
    }
    private fun findPos(stars: ProjectedStars, id: Long): Pair<Float, Float>? {
        for (i in 0 until stars.count) if (stars.id[i] == id) return stars.x[i] to stars.y[i]
        return null
    }
}
/** pass3：星点 sprite 批绘——4 档 atlas，星点禁逐帧圆斑（§八.3）。 */
private class SkyStarSpriteRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private lateinit var atlas: Array<Bitmap>
    fun render(canvas: Canvas, stars: ProjectedStars, atlas: Array<Bitmap>, w: Float, h: Float, nowMs: Long, lowPower: Boolean, hoverId: Long, pressId: Long) {
        this.atlas = atlas
        for (i in 0 until stars.count) {
            val mag = stars.mag[i]; var d = SkyParams.starDiameterPx(mag)
            if (stars.id[i] == pressId) d *= 1.6f else if (stars.id[i] == hoverId) d *= 1.3f
            val rgb = SkySpecColor.starColorFromMag(mag)
            var alpha = 1f
            if (mag < SkyParams.TWINKLE_MAG) { // 闪烁只给 mag<2.5：独立相位 0.6–1.4Hz ±8%（§八.8）
                val phase = skyHash(i, 11) * 2.0 * PI
                val freq = 0.6 + skyHash(i, 12) * 0.8
                alpha *= 1f + 0.08f * sin(2.0 * PI * freq * nowMs / 1000.0 + phase).toFloat()
            }
            paint.colorFilter = PorterDuffColorFilter((0xFF shl 24) or rgb, PorterDuff.Mode.SRC_IN)
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            val half = d / 2f
            rect.set(stars.x[i] - half, stars.y[i] - half, stars.x[i] + half, stars.y[i] + half)
            canvas.drawBitmap(atlas[tierOf(d)], null, rect, paint)
            if (mag < SkyParams.STAR_SPIKE_MAG) { // 四芒只给 mag<0.5（§八.8），同 pass 补短芒线
                paint.colorFilter = null
                paint.color = (0xFF shl 24) or rgb
                val r = d * 1.4f
                canvas.drawLine(stars.x[i] - r, stars.y[i], stars.x[i] + r, stars.y[i], paint)
                canvas.drawLine(stars.x[i], stars.y[i] - r, stars.x[i], stars.y[i] + r, paint)
            }
        }
        paint.colorFilter = null; paint.alpha = 255
        if (!lowPower) { drawDust(canvas, w, h); drawMeteor(canvas, w, h, nowMs) }
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
        val interval = SkyParams.METEOR_INTERVAL_MIN_MS + (skyHash(3, 31) * (SkyParams.METEOR_INTERVAL_MAX_MS - SkyParams.METEOR_INTERVAL_MIN_MS)).toLong()
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
// 文件级共享工具：token Long ARGB → Int；可选 alpha 比率覆盖（UI 色只走 token，不写裸 hex）。
private fun skyArgb(token: Long, alphaRatio: Float? = null): Int {
    val base = (token and 0xFFFFFFFFL).toInt()
    if (alphaRatio == null) return base
    val a = (alphaRatio.coerceIn(0f, 1f) * 255f).toInt()
    return (a shl 24) or (base and 0xFFFFFF)
}
/** 确定性哈希 → [0,1)；种子 9173 口径，单测可复现。 */
private fun skyHash(i: Int, salt: Int): Double {
    var h = i * 374761393 + salt * 668265263 + 9173 * 2246822519
    h = h xor (h ushr 13); h *= 1274126177; h = h xor (h ushr 16)
    return (h and Int.MAX_VALUE).toDouble() / Int.MAX_VALUE
}
