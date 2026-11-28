package io.legado.app.ui.map.globe.astro.contract

import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.data.SiderealTime
import io.legado.app.ui.map.globe.astro.proj.StarProjector
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 星空天象层 · 契约单验收（纯 JVM，只用 org.junit + kotlin.math，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖 §十四.5 DoD：纯 JVM 单测 ≥10、无 Android 依赖、投影 [StarProjector.project]
 * 零分配；度数范围（RA 归一化 / Dec 钳制）与 NaN / Infinity 两类各有断言。
 * 数值逐条对照 §五（尺寸线宽字号）、§六（内容色）、§十（连续缩放）、§十二（帧字段）、§十六（契约常量）。
 */
class SkyContractTest {

    private fun projectorOut(): FloatArray = FloatArray(3) { 7f }

    // ───────────────────── §十六 契约常量 ─────────────────────

    @Test
    fun `常量-投影深度与地球终点锚按登记值`() {
        // §十六.1：投影深度 900，遮挡排序基准，不是几何球半径。
        assertEquals(900.0, SkyParams.SKY_PROJECTION_FAR, 1e-9)
        // §十六.2 / §十.5：40px 只是 skyBlend 终点锚，不是开关。
        assertEquals(40f, SkyParams.SKY_ENTER_EARTH_DIAMETER_PX, 1e-6f)
        // §十.3：blend 0 一侧半径 200px，直径 400px（2×200 算术派生）。
        assertEquals(200f, SkyParams.EARTH_RADIUS_PX_FULL, 1e-6f)
        assertEquals(400f, SkyParams.EARTH_DIAMETER_PX_FULL, 1e-6f)
        // §十六.14：blend 达 1 的等效缩放标量。
        assertEquals(12.0f, SkyParams.SKY_BLEND_FULL_ZOOM, 1e-6f)
    }

    @Test
    fun `常量-星等极限金字塔层级与闪烁四芒阈值`() {
        // §十六.5/6：深度上限 11.5 拍板值；实体 / 密度分界 6.5。
        assertEquals(11.5f, SkyParams.MAG_DEPTH_LIMIT, 1e-6f)
        assertEquals(6.5f, SkyParams.MAG_BRIGHT_ENTITY, 1e-6f)
        // §十六.7：9 等 / 11.5 等两级金字塔。
        assertEquals(2, SkyParams.PYRAMID_LEVELS)
        // §十六.8/9：四芒 mag<0.5；闪烁 mag<2.5。
        assertEquals(0.5f, SkyParams.STAR_SPIKE_MAG, 1e-6f)
        assertEquals(2.5f, SkyParams.TWINKLE_MAG, 1e-6f)
    }

    @Test
    fun `常量-视角时长视场辉光与流星区间`() {
        assertEquals(600L, SkyParams.SKY_TRANSITION_MS)
        assertEquals(800L, SkyParams.VIEW_SWITCH_MS)
        assertEquals(1200L, SkyParams.FIELD_PULSE_MS)
        assertEquals(90_000L, SkyParams.METEOR_INTERVAL_MIN_MS)
        assertEquals(240_000L, SkyParams.METEOR_INTERVAL_MAX_MS)
        assertEquals(60.0, SkyParams.GROUND_VIEW_FOV_DEG, 1e-9)
        assertEquals(5.0, SkyParams.OBSERVER_HORIZON_GLOW_DEG, 1e-9)
    }

    @Test
    fun `常量-线宽字号间距圆角逐条命中规格`() {
        // §五.3：星座 0.8px/0.45；星官 1.0px/0.55；分野 1.5px 虚线 6/gap 4。
        assertEquals(0.8f, SkyParams.CONSTELLATION_LINE_WIDTH_PX, 1e-6f)
        assertEquals(0.45f, SkyParams.CONSTELLATION_LINE_ALPHA_RATIO, 1e-6f)
        assertEquals(1.0f, SkyParams.ASTERISM_LINE_WIDTH_PX, 1e-6f)
        assertEquals(0.55f, SkyParams.ASTERISM_LINE_ALPHA_RATIO, 1e-6f)
        assertEquals(1.5f, SkyParams.FIELD_BORDER_WIDTH_PX, 1e-6f)
        assertEquals(6f, SkyParams.FIELD_BORDER_DASH_PX, 1e-6f)
        assertEquals(4f, SkyParams.FIELD_BORDER_GAP_PX, 1e-6f)
        // §五.4：网格 0.8 / 黄道 1.0 / 天赤道 1.0；§六.9 alpha 0.12 / 0.30 / 0.30。
        assertEquals(0.8f, SkyParams.GRID_LINE_WIDTH_PX, 1e-6f)
        assertEquals(0.12f, SkyParams.GRID_LINE_ALPHA_RATIO, 1e-6f)
        assertEquals(1.0f, SkyParams.ECLIPTIC_LINE_WIDTH_PX, 1e-6f)
        assertEquals(0.30f, SkyParams.ECLIPTIC_LINE_ALPHA_RATIO, 1e-6f)
        assertEquals(1.0f, SkyParams.EQUATOR_LINE_WIDTH_PX, 1e-6f)
        assertEquals(0.30f, SkyParams.EQUATOR_LINE_ALPHA_RATIO, 1e-6f)
        // §五.5：字号全部 ≥14sp；Sheet 标题 16sp。
        assertEquals(14f, SkyParams.STAR_LABEL_FONT_SP, 1e-6f)
        assertEquals(14f, SkyParams.CONSTELLATION_NAME_FONT_SP, 1e-6f)
        assertEquals(16f, SkyParams.SHEET_TITLE_FONT_SP, 1e-6f)
        assertEquals(14f, SkyParams.SHEET_BODY_FONT_SP, 1e-6f)
        assertEquals(14f, SkyParams.SHEET_SOURCE_FONT_SP, 1e-6f)
        // §五.6/7：标签距 8dp、热区 48dp、Sheet 圆角 20dp、印框 2dp、列表 8dp、三段 30/60/90%、阈值 0.12。
        assertEquals(8f, SkyParams.LABEL_GAP_DP, 1e-6f)
        assertEquals(48f, SkyParams.HIT_ZONE_SIDE_DP, 1e-6f)
        assertEquals(20f, SkyParams.SHEET_RADIUS_DP, 1e-6f)
        assertEquals(2f, SkyParams.STAR_NAME_BOX_RADIUS_DP, 1e-6f)
        assertEquals(8f, SkyParams.LIST_SPACING_DP, 1e-6f)
        assertEquals(0.30f, SkyParams.SHEET_DETENT_FRACTION_1, 1e-6f)
        assertEquals(0.60f, SkyParams.SHEET_DETENT_FRACTION_2, 1e-6f)
        assertEquals(0.90f, SkyParams.SHEET_DETENT_FRACTION_3, 1e-6f)
        assertEquals(0.12f, SkyParams.SHEET_CLOSE_THRESHOLD_RATIO, 1e-6f)
    }

    // ───────────────────── §五.1 星点直径 ─────────────────────

    @Test
    fun `星点直径-五档锚点逐条命中规格`() {
        assertEquals(9f, SkyParams.starDiameterPx(-1.5f), 1e-6f)
        assertEquals(5f, SkyParams.starDiameterPx(0f), 1e-6f)
        assertEquals(3f, SkyParams.starDiameterPx(2f), 1e-6f)
        assertEquals(1.5f, SkyParams.starDiameterPx(4.5f), 1e-6f)
        assertEquals(1.0f, SkyParams.starDiameterPx(6.5f), 1e-6f)
        // >6.5 金字塔层：0.8px 不再小，用 alpha 补偿。
        assertEquals(0.8f, SkyParams.starDiameterPx(8f), 1e-6f)
        assertEquals(0.8f, SkyParams.starDiameterPx(11.5f), 1e-6f)
    }

    @Test
    fun `星点直径-区间单调不增且金字塔层零点八兜底`() {
        val samples = floatArrayOf(-3f, -1.5f, 0f, 2f, 4.5f, 6.5f, 8f, 11.5f)
        val sizes = samples.map { SkyParams.starDiameterPx(it) }
        for (i in 1 until sizes.size) {
            assertTrue("直径应随星等不增：${sizes[i - 1]} -> ${sizes[i]}", sizes[i] <= sizes[i - 1] + 1e-6f)
        }
        // 暗于 6.5 一律不低于 0.8px。
        assertTrue(SkyParams.starDiameterPx(99f) >= 0.8f - 1e-6f)
    }

    // ───────────────────── §十 连续缩放 ─────────────────────

    @Test
    fun `skyBlend-直径映射端点闭合中段对称越界钳制`() {
        // blend 0：直径 400px；blend 1：40px 终点锚；中段 220px → 0.5，反向对称。
        assertEquals(0f, SkyParams.skyBlendFromDiameter(400f), 1e-6f)
        assertEquals(1f, SkyParams.skyBlendFromDiameter(40f), 1e-6f)
        assertEquals(0.5f, SkyParams.skyBlendFromDiameter(220f), 1e-6f)
        // 越界钳到 [0,1]，无开关式跳变。
        assertEquals(0f, SkyParams.skyBlendFromDiameter(500f), 1e-6f)
        assertEquals(1f, SkyParams.skyBlendFromDiameter(10f), 1e-6f)
    }

    @Test
    fun `magLimit-随blend单调递增端点六点五到十一点五`() {
        assertEquals(6.5f, SkyParams.magLimitFromBlend(0f), 1e-6f)
        assertEquals(11.5f, SkyParams.magLimitFromBlend(1f), 1e-6f)
        assertEquals(9.0f, SkyParams.magLimitFromBlend(0.5f), 1e-6f)
        val samples = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val limits = samples.map { SkyParams.magLimitFromBlend(it) }
        for (i in 1 until limits.size) {
            assertTrue("magLimit 应随 blend 单调递增：${limits[i - 1]} -> ${limits[i]}", limits[i] >= limits[i - 1] - 1e-6f)
        }
        // 越界 blend 钳回端点。
        assertEquals(6.5f, SkyParams.magLimitFromBlend(-1f), 1e-6f)
        assertEquals(11.5f, SkyParams.magLimitFromBlend(2f), 1e-6f)
    }

    // ───────────────────── §十二.1 帧快照 ─────────────────────

    @Test
    fun `SkyFrame-默认值九字段齐全选中id可空`() {
        val frame = SkyFrame()
        assertEquals(SkyViewMode.SKY_HEAVEN, frame.mode)
        assertEquals(0f, frame.skyBlend, 1e-6f)
        assertNull(frame.cameraYawPitch)
        assertNull(frame.observerLatLon)
        assertEquals(0.0, frame.siderealTimeDeg, 1e-9)
        assertEquals(6.5f, frame.magLimit, 1e-6f)
        assertEquals(0f, frame.pyramidMix, 1e-6f)
        assertNull(frame.selectedId)
        assertEquals(0f, frame.horizonDipDeg, 1e-6f)
    }

    @Test
    fun `SkyFrame-装配后不可变且选中id按标量携带`() {
        val frame = SkyFrame(
            mode = SkyViewMode.SKY_EARTH,
            skyBlend = 0.75f,
            cameraYawPitch = CameraYawPitch(135.0, -12.0),
            observerLatLon = ObserverLatLon(34.7, 108.9),
            siderealTimeDeg = 210.0,
            magLimit = 9.0f,
            pyramidMix = 0.4f,
            selectedId = 42L,
            horizonDipDeg = 2.5f,
        )
        assertEquals(42L, frame.selectedId)
        // 数据类按值相等：关闭 Sheet 前后快照可逐位比对（§十四.12）。
        val copy = frame.copy()
        assertEquals(frame, copy)
    }

    // ───────────────────── §六 内容色 ─────────────────────

    @Test
    fun `SkySpecColor-四锚与流星头部色值`() {
        assertEquals(0xD8E4F0, SkySpecColor.STAR_ANCHOR_EARLY_RGB)
        assertEquals(0xFFFDF6, SkySpecColor.STAR_ANCHOR_MID_RGB)
        assertEquals(0xFFEED8, SkySpecColor.STAR_ANCHOR_LATE_RGB)
        assertEquals(0xFFB37A, SkySpecColor.STAR_ANCHOR_VERY_LATE_RGB)
        assertEquals(0xFFFDF6, SkySpecColor.METEOR_HEAD_RGB)
        // §六.15：银河核心 0.10 / 叠金 0.06 / 外围 0.04 / 尘粒 0.15。
        assertEquals(0.10f, SkySpecColor.GALAXY_CORE_ALPHA_RATIO, 1e-6f)
        assertEquals(0.06f, SkySpecColor.GALAXY_CORE_GOLD_ALPHA_RATIO, 1e-6f)
        assertEquals(0.04f, SkySpecColor.GALAXY_HALO_ALPHA_RATIO, 1e-6f)
        assertEquals(0.15f, SkySpecColor.DUST_ALPHA_RATIO, 1e-6f)
    }

    @Test
    fun `SkySpecColor-B-V分段插值锚点命中`() {
        assertEquals(SkySpecColor.STAR_ANCHOR_EARLY_RGB, SkySpecColor.starColorFromBV(-1.0))
        assertEquals(SkySpecColor.STAR_ANCHOR_MID_RGB, SkySpecColor.starColorFromBV(0.60))
        assertEquals(SkySpecColor.STAR_ANCHOR_LATE_RGB, SkySpecColor.starColorFromBV(1.20))
        assertEquals(SkySpecColor.STAR_ANCHOR_VERY_LATE_RGB, SkySpecColor.starColorFromBV(2.0))
        // 插值结果不越出两端锚点色域。
        val mid = SkySpecColor.starColorFromBV(0.15)
        val early = SkySpecColor.STAR_ANCHOR_EARLY_RGB
        val midAnchor = SkySpecColor.STAR_ANCHOR_MID_RGB
        assertTrue((mid shr 16 and 0xFF) >= (early shr 16 and 0xFF))
        assertTrue((mid shr 16 and 0xFF) <= (midAnchor shr 16 and 0xFF))
    }

    @Test
    fun `SkySpecColor-无BV按星等映射纸白到暖白`() {
        // §六.14：亮星纸白、暗星暖白，不猜色温；>6.5 不转橙。
        assertEquals(SkySpecColor.STAR_ANCHOR_MID_RGB, SkySpecColor.starColorFromMag(0f))
        assertEquals(SkySpecColor.STAR_ANCHOR_LATE_RGB, SkySpecColor.starColorFromMag(6.5f))
        val warm = SkySpecColor.starColorFromMag(3.25f)
        assertTrue(warm != SkySpecColor.STAR_ANCHOR_VERY_LATE_RGB)
        // 「更暖」的语义是绿/蓝分量单调下降、红分量不动（纸白 #FFFDF6 → 暖白 #FFEED8），
        // 不是整色值大小比较；这里按通道断言，避免把配色方向写反。
        val midRgb = SkySpecColor.STAR_ANCHOR_MID_RGB
        assertTrue((warm shr 8 and 0xFF) <= (midRgb shr 8 and 0xFF))
        assertTrue((warm and 0xFF) <= (midRgb and 0xFF))
    }

    // ───────────────────── §八 / §十四.5 投影 ─────────────────────

    @Test
    fun `投影-赤道前方星点落屏幕中心可见`() {
        val out = projectorOut()
        val ok = StarProjector.project(
            SkyViewMode.SKY_HEAVEN,
            raDeg = 90.0, decDeg = 0.0,
            yawDeg = 0.0, pitchDeg = 0.0,
            focalPx = 100.0, cx = 540.0, cy = 960.0,
            latDeg = 0.0, lonDeg = 0.0, lstDeg = 0.0,
            out,
        )
        assertTrue(ok)
        assertEquals(540f, out[0], 1e-3f)
        assertEquals(960f, out[1], 1e-3f)
        assertTrue(out[2] > 0f)
    }

    @Test
    fun `投影-背面半球不可见`() {
        val out = projectorOut()
        val ok = StarProjector.project(
            SkyViewMode.SKY_HEAVEN,
            raDeg = 270.0, decDeg = 0.0,
            yawDeg = 0.0, pitchDeg = 0.0,
            focalPx = 100.0, cx = 540.0, cy = 960.0,
            latDeg = 0.0, lonDeg = 0.0, lstDeg = 0.0,
            out,
        )
        assertFalse(ok)
    }

    @Test
    fun `投影-赤经任意实数归一化到三百六十等价`() {
        // 度数范围：370° ≡ 10°；-270° ≡ 90°；同一方向投影结果逐位一致。
        val a = projectorOut()
        val b = projectorOut()
        val c = projectorOut()
        val okA = StarProjector.project(SkyViewMode.SKY_HEAVEN, 90.0, 0.0, 0.0, 0.0,
            100.0, 540.0, 960.0, 0.0, 0.0, 0.0, a)
        val okB = StarProjector.project(SkyViewMode.SKY_HEAVEN, 450.0, 0.0, 0.0, 0.0,
            100.0, 540.0, 960.0, 0.0, 0.0, 0.0, b)
        val okC = StarProjector.project(SkyViewMode.SKY_HEAVEN, -270.0, 0.0, 0.0, 0.0,
            100.0, 540.0, 960.0, 0.0, 0.0, 0.0, c)
        assertTrue(okA && okB && okC)
        assertArrayEquals(a, b, 1e-4f)
        assertArrayEquals(a, c, 1e-4f)
    }

    @Test
    fun `投影-赤纬越界钳制到正负九十`() {
        // 度数范围：120° 钳到 90°；-120° 钳到 -90°，与显式极点输出一致。
        val pole = projectorOut()
        val over = projectorOut()
        val south = projectorOut()
        val southOver = projectorOut()
        val okPole = StarProjector.project(SkyViewMode.SKY_HEAVEN, 0.0, 90.0, 0.0, 0.0,
            100.0, 540.0, 960.0, 0.0, 0.0, 0.0, pole)
        val okOver = StarProjector.project(SkyViewMode.SKY_HEAVEN, 0.0, 120.0, 0.0, 0.0,
            100.0, 540.0, 960.0, 0.0, 0.0, 0.0, over)
        val okSouth = StarProjector.project(SkyViewMode.SKY_HEAVEN, 0.0, -90.0, 0.0, 0.0,
            100.0, 540.0, 960.0, 0.0, 0.0, 0.0, south)
        val okSouthOver = StarProjector.project(SkyViewMode.SKY_HEAVEN, 0.0, -120.0, 0.0, 0.0,
            100.0, 540.0, 960.0, 0.0, 0.0, 0.0, southOver)
        assertEquals(okPole, okOver)
        assertArrayEquals(pole, over, 1e-4f)
        assertEquals(okSouth, okSouthOver)
        assertArrayEquals(south, southOver, 1e-4f)
    }

    @Test
    fun `投影-NaN与Infinity返回false且不写脏值`() {
        // NaN 赤经：false，out 保持调用方原值。
        val outNaN = projectorOut()
        val okNaN = StarProjector.project(SkyViewMode.SKY_HEAVEN, Double.NaN, 0.0,
            0.0, 0.0, 100.0, 540.0, 960.0, 0.0, 0.0, 0.0, outNaN)
        assertFalse(okNaN)
        assertArrayEquals(projectorOut(), outNaN, 1e-6f)

        // Infinity 赤纬：false，out 不写脏值。
        val outInf = projectorOut()
        val okInf = StarProjector.project(SkyViewMode.SKY_HEAVEN, 90.0, Double.POSITIVE_INFINITY,
            0.0, 0.0, 100.0, 540.0, 960.0, 0.0, 0.0, 0.0, outInf)
        assertFalse(okInf)
        assertArrayEquals(projectorOut(), outInf, 1e-6f)

        // NaN 焦距：false。
        val outFocal = projectorOut()
        val okFocal = StarProjector.project(SkyViewMode.SKY_HEAVEN, 90.0, 0.0,
            0.0, 0.0, Double.NaN, 540.0, 960.0, 0.0, 0.0, 0.0, outFocal)
        assertFalse(okFocal)
        assertArrayEquals(projectorOut(), outFocal, 1e-6f)
    }

    @Test
    fun `投影-地上观地平换算前方可见`() {
        // 赤道上观测点（lat=0）看南天：H=0（上中天）、dec=−45° → 高度角 45°、正南方。
        // 相机朝南（yaw=180）时该星在正前方，落在屏幕中心正上方 40px（focal=100，tan45°=1）。
        // 注：dec=0 在 lat=0 的上中天是**天顶**（alt=90°），方位角退化，不能拿它当「南方地平」用例。
        val out = projectorOut()
        val ok = StarProjector.project(
            SkyViewMode.SKY_EARTH,
            raDeg = 0.0, decDeg = -45.0,
            yawDeg = 180.0, pitchDeg = 0.0,
            focalPx = 100.0, cx = 540.0, cy = 960.0,
            latDeg = 0.0, lonDeg = 0.0, lstDeg = 0.0,
            out,
        )
        assertTrue(ok)
        assertTrue(out[2] > 0f)
        assertEquals(540f, out[0], 1e-2f)
        assertEquals(860f, out[1], 1e-2f)
    }

    // ───────────────────── §十四.2 三时刻 alt/az ≤1° 手算对拍 ─────────────────────

    private companion object {
        const val HORIZON_TOLERANCE_DEG: Double = 1.0
        const val HORIZON_FOCAL_PX: Double = 1000.0
        const val HORIZON_CX: Double = 500.0
        const val HORIZON_CY: Double = 500.0
    }

    /** 手算对拍用例：时刻名 / 赤经° / 地方恒星时° / 赤纬° / 相机偏航° / 期望高度角° / 期望方位角°。 */
    private data class HorizonHandCase(
        val tag: String,
        val raDeg: Double,
        val lstDeg: Double,
        val decDeg: Double,
        val yawDeg: Double,
        val expectedAltDeg: Double,
        val expectedAzDeg: Double,
    )

    @Test
    fun `投影-格林尼治上中天三时刻高度方位角手算对拍`() {
        // 观测点格林尼治：φ=0°N，λ=0°；三颗目标均取 RA=LST，时角 H = LST − RA = 0（上中天）。
        // 闭式（与 StarProjector.kt:81,86,92-93 同一口径）：
        //   H = LST − RA = 0
        //   sin alt = sin dec·sin φ + cos dec·cos φ·cos H，φ=0 → sin alt = cos dec → alt = 90° − |dec|
        //   sin az  = −cos dec·sin H / cos alt = 0
        //   cos az  = (sin dec·cos φ − cos dec·sin φ·cos H)/cos alt = sin dec / cos alt
        // 时刻一：dec=+30° → alt=60°；sin dec=+0.5，cos alt=cos60°=0.5 → cos az=+1 → az=0°（正北）。
        // 时刻二：dec=−30° → alt=60°；sin dec=−0.5，cos alt=0.5 → cos az=−1 → az=180°（正南）。
        // 时刻三：dec=+60° → alt=30°；sin dec=+0.8660，cos alt=cos30°=0.8660 → cos az=+1 → az=0°（正北）。
        // 改坏推演：时角取反、sinAlt/cosAz 符号颠倒或赤纬钳制错位时，反演 alt/az 必偏离
        // 下列期望值超过 1° 容差，两条 assertEquals 对应星例变红。
        val cases = listOf(
            HorizonHandCase("时刻一", raDeg = 0.0, lstDeg = 0.0, decDeg = 30.0,
                yawDeg = 0.0, expectedAltDeg = 60.0, expectedAzDeg = 0.0),
            HorizonHandCase("时刻二", raDeg = 120.0, lstDeg = 120.0, decDeg = -30.0,
                yawDeg = 180.0, expectedAltDeg = 60.0, expectedAzDeg = 180.0),
            HorizonHandCase("时刻三", raDeg = 250.0, lstDeg = 250.0, decDeg = 60.0,
                yawDeg = 0.0, expectedAltDeg = 30.0, expectedAzDeg = 0.0),
        )
        for (c in cases) {
            val recovered = recoverHorizonAltAz(c)
            assertNotNull("${c.tag} 投影被深度剔除，地平坐标不可恢复", recovered)
            val (alt, az) = recovered!!
            assertEquals("${c.tag} 高度角偏差", c.expectedAltDeg, alt, HORIZON_TOLERANCE_DEG)
            assertEquals("${c.tag} 方位角偏差", c.expectedAzDeg, az, HORIZON_TOLERANCE_DEG)
        }
    }

    /**
     * 走 [StarProjector.project]（SKY_EARTH）投影后由透视关系反演东 / 上 / 北分量，
     * 恢复高度角与方位角（方位角自北向顺时针，东向为正，与投影器同一口径）。
     * @return (高度角°, 方位角°[0,360))；背面不可见时返回 null。
     */
    private fun recoverHorizonAltAz(c: HorizonHandCase): Pair<Double, Double>? {
        val out = FloatArray(3)
        val ok = StarProjector.project(
            SkyViewMode.SKY_EARTH,
            raDeg = c.raDeg,
            decDeg = c.decDeg,
            yawDeg = c.yawDeg,
            pitchDeg = 0.0,
            focalPx = HORIZON_FOCAL_PX,
            cx = HORIZON_CX,
            cy = HORIZON_CY,
            latDeg = 0.0,
            lonDeg = 0.0,
            lstDeg = c.lstDeg,
            out = out,
        )
        if (!ok) return null
        val dx = (out[0] - HORIZON_CX) / HORIZON_FOCAL_PX
        val dy = (HORIZON_CY - out[1]) / HORIZON_FOCAL_PX
        val z = 1.0 / sqrt(dx * dx + dy * dy + 1.0)
        val x1 = dx * z
        val up = dy * z
        val east: Double
        val north: Double
        if (c.yawDeg < 90.0) {
            east = x1
            north = z
        } else {
            east = -x1
            north = -z
        }
        val alt = Math.toDegrees(asin(up.coerceIn(-1.0, 1.0)))
        val azRaw = Math.toDegrees(atan2(east, north))
        val az = ((azRaw % 360.0) + 360.0) % 360.0
        return alt to az
    }

    // ───────────────────── §十四.2 观测点注入链路（injectedUtc → effectiveUtc → LST → 投影） ─────────────────────

    @Test
    fun `观测点注入链路-注入时刻经帧快照喂入投影与纯函数直算一致`() {
        // 链路：GlobeController.setObservationTime 写 injectedUtcMillis → nextFrame 取
        // effectiveUtc = 注入值（帧墙钟传 0 兜底，证明不跟墙钟）→ SiderealTime 推 LST →
        // 帧快照 observerLatLon/siderealTimeDeg → StarProjector 以帧内观测点与 LST 投影。
        // GlobeController 为纯 JVM 门面（GlobeController.kt:22 注释：不引用 Android/Compose 类型），
        // JVM 单测可直接实例化，无需 Android 上下文；本条只走既有 API，不改门面结构。
        val controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_EARTH)
        controller.setObserver(0.0, 0.0) // 格林尼治 φ=0°N, λ=0°
        val injectedUtc = Instant.parse("2026-11-28T16:00:00Z").toEpochMilli()
        controller.setObservationTime(injectedUtc)

        val snapshot = controller.nextFrame(0.016, 0L)
        val frame = assertNotNull(snapshot.sky)
        val obs = assertNotNull(frame.observerLatLon)
        assertEquals(0.0, obs.latDeg, 1e-9)
        assertEquals(0.0, obs.lonDeg, 1e-9)

        // effectiveUtc → LST：帧内 LST 必须等于按注入时刻直算的地方恒星时。
        val expectedLst = SiderealTime.localSiderealTimeDeg(injectedUtc, obs.lonDeg)
        assertEquals(expectedLst, frame.siderealTimeDeg, 1e-9)

        // LST → 投影：取 dec=+30° 春分方向星（保证正面可见），分别用帧内观测点/LST 与
        // 纯函数直算值投影，两次屏幕结果逐位一致；链路任一环节断接，下方数组比对变红。
        val outFrame = FloatArray(3)
        val okFrame = StarProjector.project(
            SkyViewMode.SKY_EARTH,
            raDeg = 0.0, decDeg = 30.0,
            yawDeg = 0.0, pitchDeg = 0.0,
            focalPx = HORIZON_FOCAL_PX, cx = HORIZON_CX, cy = HORIZON_CY,
            latDeg = obs.latDeg, lonDeg = obs.lonDeg, lstDeg = frame.siderealTimeDeg,
            out = outFrame,
        )
        assertTrue(okFrame)
        val outDirect = FloatArray(3)
        val okDirect = StarProjector.project(
            SkyViewMode.SKY_EARTH,
            raDeg = 0.0, decDeg = 30.0,
            yawDeg = 0.0, pitchDeg = 0.0,
            focalPx = HORIZON_FOCAL_PX, cx = HORIZON_CX, cy = HORIZON_CY,
            latDeg = 0.0, lonDeg = 0.0, lstDeg = expectedLst,
            out = outDirect,
        )
        assertTrue(okDirect)
        assertArrayEquals(outDirect, outFrame, 1e-4f)
    }
}
