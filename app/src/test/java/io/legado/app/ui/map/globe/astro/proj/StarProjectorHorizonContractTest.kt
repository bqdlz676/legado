package io.legado.app.ui.map.globe.astro.proj

import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * 地上观赤道→地平换算三时刻契约断言（判据 2；§十四.2，误差 ≤1°）。
 *
 * 口径：观测点长安（34.34°N / 108.94°E）；天狼 / 织女在 2026-11-28 12/16/20 UTC
 * 三个时刻的手算高度角 / 方位角取自二次验收报告 §判据 2。本用例不经手写地平公式对拍，
 * 而是走 [StarProjector.project]（SKY_EARTH）投影出屏幕坐标，再由透视反演恢复
 * 高度角 / 方位角，与手算值比对；投影公式改坏时恢复值必然偏离手算值超过 1°。
 *
 * 〔背面半球兜底〕地平线下或朝北背离相机的星在恒等相机下被深度剔除；依次试 yaw=0/180
 * 两个对立朝向，必有一个正面可见；两视角都不可见才判失败（施工图 2.3 不许空白态）。
 */
class StarProjectorHorizonContractTest {

    private companion object {
        const val OBS_LAT_DEG: Double = 34.34
        const val OBS_LON_DEG: Double = 108.94

        /** 契约容差：规格 §十四.2 高度角 / 方位角误差 ≤1°。 */
        const val TOLERANCE_DEG: Double = 1.0

        const val FOCAL_PX: Double = 1000.0
        const val CX: Double = 500.0
        const val CY: Double = 500.0
    }

    /** 手算对拍用例：星名 / J2000 赤经° / 赤纬° / 地方恒星时° / 期望高度角° / 期望方位角°。 */
    private data class HorizonCase(
        val star: String,
        val raDeg: Double,
        val decDeg: Double,
        val lstDeg: Double,
        val expectedAltDeg: Double,
        val expectedAzDeg: Double,
    )

    private val cases = listOf(
        // 时刻 A：12:00 UTC，LST=356.3429°。
        HorizonCase("天狼", 101.287215, -16.716116, 356.3429, -21.48, 96.06),
        HorizonCase("织女", 279.234600, 38.783692, 356.3429, 29.80, 298.88),
        // 时刻 B：16:00 UTC，LST=56.5072°。
        HorizonCase("天狼", 101.287215, -16.716116, 56.5072, 23.52, 132.63),
        HorizonCase("织女", 279.234600, 38.783692, 56.5072, -6.86, 327.81),
        // 时刻 C：20:00 UTC，LST=116.6715°。
        HorizonCase("天狼", 101.287215, -16.716116, 116.6715, 36.89, 198.52),
        HorizonCase("织女", 279.234600, 38.783692, 116.6715, -15.11, 14.00),
    )

    @Test
    fun `三时刻天狼织女 投影反演高度方位角与手算值偏差不超1度`() {
        for (c in cases) {
            val recovered = recoverAltAz(c.raDeg, c.decDeg, c.lstDeg)
            assertNotNull("${c.star} 双视角均被深度剔除，投影不可恢复", recovered)
            val (alt, az) = recovered!!

            val altDiff = abs(alt - c.expectedAltDeg)
            val azDiff = circularAzDiff(az, c.expectedAzDeg)
            // 改坏推演：若 projector 地平换算改坏——时角 H 取成 RA−LST、
            // sinAlt / cosAz 符号颠倒、或赤纬钳制错位——恢复出的 alt / az 会偏离手算值，
            // 下面两条 assertEquals 在对应星例上变红。
            assertEquals(
                "${c.star} 高度角偏差", c.expectedAltDeg, alt, TOLERANCE_DEG,
            )
            assertEquals(
                "${c.star} 方位角偏差", c.expectedAzDeg, az, TOLERANCE_DEG,
            )
            // 安全阀：上面容差断言已含 1° 带；这里显式记录实际偏差供验收回源。
            require(altDiff <= TOLERANCE_DEG && azDiff <= TOLERANCE_DEG) {
                "${c.star} dAlt=$altDiff dAz=$azDiff"
            }
        }
    }

    /**
     * 走 [StarProjector.project] 投影后由透视关系反演本地东 / 上 / 北分量，
     * 恢复高度角与方位角（方位角自北向顺时针，东向为正，与投影器同一口径）。
     * @return (高度角°, 方位角°[0,360))；双视角均不可见时返回 null。
     */
    private fun recoverAltAz(ra: Double, dec: Double, lst: Double): Pair<Double, Double>? {
        val out = FloatArray(3)
        for (yawDeg in doubleArrayOf(0.0, 180.0)) {
            val ok = StarProjector.project(
                SkyViewMode.SKY_EARTH,
                raDeg = ra,
                decDeg = dec,
                yawDeg = yawDeg,
                pitchDeg = 0.0,
                focalPx = FOCAL_PX,
                cx = CX,
                cy = CY,
                latDeg = OBS_LAT_DEG,
                lonDeg = OBS_LON_DEG,
                lstDeg = lst,
                out = out,
            )
            if (!ok) continue

            val dx = (out[0] - CX) / FOCAL_PX
            val dy = (CY - out[1]) / FOCAL_PX
            val z1 = 1.0 / sqrt(dx * dx + dy * dy + 1.0)
            val x1 = dx * z1
            val up = dy * z1
            val east: Double
            val north: Double
            if (yawDeg < 90.0) {
                east = x1
                north = z1
            } else {
                east = -x1
                north = -z1
            }
            val alt = Math.toDegrees(asin(up.coerceIn(-1.0, 1.0)))
            val azRaw = Math.toDegrees(atan2(east, north))
            val az = ((azRaw % 360.0) + 360.0) % 360.0
            return alt to az
        }
        return null
    }

    /** 方位角圆周距离（0..180）。 */
    private fun circularAzDiff(a: Double, b: Double): Double {
        val d = ((a - b) % 360.0 + 360.0) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
