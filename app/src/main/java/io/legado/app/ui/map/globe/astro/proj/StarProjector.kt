package io.legado.app.ui.map.globe.astro.proj

import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 赤道 / 地平 → 屏幕的纯函数投影（契约层，零分配）。
 *
 * 〔模型〕天球是跟随相机的 2D 投影背景，不做球壳网格（§〇.9、§八.4）；星点方向由
 * RA/Dec + 恒星时 + 相机姿态算出屏幕坐标。软渲染下 2D 投影比球壳省一个量级运算。
 *
 * 〔零分配口径（§十四.5）〕入参全标量；结果写调用方复用的 [out]，返回 [Boolean]；
 * 函数体内不创建对象 / 装箱 / 列表，不拼接 String，不另出第二套 API。
 *
 * 〔健壮性〕`raDeg` 接受任一实数并归一化到 [0,360)；`decDeg` 超出 ±90 一律钳制到 ±90；
 * 任一输入为 `NaN` / `Infinity` 返回 false，且不写脏值到 [out]。
 *
 * 〔双视角同一入口〕按 [mode] 分支：[SkyViewMode.SKY_HEAVEN] 直接用赤道坐标；
 * [SkyViewMode.SKY_EARTH] 先用观测点纬度 + 地方恒星时转地平坐标，再投同一屏幕。
 */
object StarProjector {

    private const val DEG2RAD: Double = PI / 180.0

    /**
     * 投影一颗星到屏幕。
     *
     * @param mode 视角：天外赤道坐标 / 地上地平坐标
     * @param raDeg 赤经（度，任一实数，内部归一化到 [0,360)）
     * @param decDeg 赤纬（度，超出 ±90 钳制）
     * @param yawDeg 相机偏航（度）
     * @param pitchDeg 相机俯仰（度）
     * @param focalPx 透视焦距（px，须 >0）
     * @param cx 屏幕中心 x（px）
     * @param cy 屏幕中心 y（px）
     * @param latDeg 观测点纬度（度，仅地上观用）
     * @param lonDeg 观测点经度（度，仅地上观登记；地方恒星时已含经度改正）
     * @param lstDeg 地方恒星时（度，仅地上观用）
     * @param out 复用的 `FloatArray(3)`：`out[0]`=屏幕 x px，`out[1]`=屏幕 y px，`out[2]`=深度（>0 正面）
     * @return true 点位在前方可见；false 时 [out] 保持调用方原值不动
     */
    fun project(
        mode: SkyViewMode,
        raDeg: Double,
        decDeg: Double,
        yawDeg: Double,
        pitchDeg: Double,
        focalPx: Double,
        cx: Double,
        cy: Double,
        latDeg: Double,
        lonDeg: Double,
        lstDeg: Double,
        out: FloatArray,
    ): Boolean {
        // 健壮性：任一非有限输入 → false，且不触碰 out（不写脏值）。
        if (!raDeg.isFinite() || !decDeg.isFinite() || !yawDeg.isFinite() ||
            !pitchDeg.isFinite() || !focalPx.isFinite() || !cx.isFinite() ||
            !cy.isFinite() || !latDeg.isFinite() || !lonDeg.isFinite() || !lstDeg.isFinite()
        ) return false
        if (focalPx <= 0.0) return false
        if (out.size < 3) return false

        val ra = normalize360(raDeg)
        val dec = decDeg.coerceIn(-90.0, 90.0)

        // 单位方向向量（局部球面系：Y 过天极；RA=0 指向 +Z 平面）。
        val decR = dec * DEG2RAD
        val raR = ra * DEG2RAD
        val cosDec = cos(decR)
        var vx = cosDec * cos(raR)
        var vy = sin(decR)
        var vz = cosDec * sin(raR)

        if (mode == SkyViewMode.SKY_EARTH) {
            // 赤道 → 地平：时角 H = LST − RA；先换高度角 / 方位角，再落到水平系（东 / 上 / 北）。
            val h = normalize360(lstDeg - ra) * DEG2RAD
            val lat = latDeg.coerceIn(-90.0, 90.0) * DEG2RAD
            val sinDec = sin(decR)
            val sinLat = sin(lat)
            val cosLat = cos(lat)
            val sinAlt = sinDec * sinLat + cosDec * cosLat * cos(h)
            val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
            val cosAlt = cos(alt)
            val sinAz: Double
            val cosAz: Double
            if (cosAlt > 1e-12) {
                sinAz = -cosDec * sin(h) / cosAlt
                cosAz = (sinDec * cosLat - cosDec * sinLat * cos(h)) / cosAlt
            } else {
                // 天顶 / 天底附近方位角退化：归正北向，避免除零。
                sinAz = 0.0
                cosAz = 1.0
            }
            val az = atan2(sinAz, cosAz)
            vx = cos(alt) * sin(az) // 东
            vy = sin(alt) // 上
            vz = cos(alt) * cos(az) // 北
        }

        // 相机姿态：先偏航（绕 Y）后俯仰（绕 X），与 GlobeProjection 同一顺序。
        val yaw = yawDeg * DEG2RAD
        val pitch = pitchDeg * DEG2RAD
        val cyw = cos(yaw)
        val syw = sin(yaw)
        val x1 = vx * cyw + vz * syw
        val z1 = -vx * syw + vz * cyw
        val cpi = cos(pitch)
        val spi = sin(pitch)
        val y2 = vy * cpi - z1 * spi
        val z2 = vy * spi + z1 * cpi

        // 背面半球不输出可见点；深度留在 out[2] 供排序调用方判断。
        if (z2 <= 1e-9) return false

        out[0] = (cx + focalPx * x1 / z2).toFloat()
        out[1] = (cy - focalPx * y2 / z2).toFloat()
        out[2] = z2.toFloat()
        return true
    }

    /** 角度归一化到 [0,360)；输入须已通过有限性检查。 */
    private fun normalize360(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0.0) d += 360.0
        return d
    }
}
