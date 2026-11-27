package io.legado.app.ui.map.globe.astro.data

/**
 * 地方恒星时推导（§十二.1、§十四.2；纯 JVM，无 Android / Compose 依赖，可直接单测）。
 *
 * 公式口径（IAU 1982/2000 常见近似）：
 *   GMST(°) = 280.46061837 + 360.98564736629 · d
 *   LST(°)  = GMST + λ（东经为正），归一到 [0, 360)
 * 其中 d = 自 J2000.0（2000-01-01 12:00:00 UTC，JD 2451545.0）起的儒略日数。
 *
 * 〔为什么放数据层〕恒星时是天文纯计算，不持有帧 / 渲染状态；门面帧驱动每帧喂 UTC 毫秒，
 * 本函数只做标量换算，零对象分配，便于三时刻手算对照单测（误差 < 0.01°）。
 */
object SiderealTime {

    /** J2000.0 历元（2000-01-01 12:00:00 UTC）对应的 Unix 毫秒。 */
    private const val J2000_EPOCH_MILLIS: Long = 946_728_000_000L

    /** 历元时刻 GMST 近似常数（度）。 */
    private const val GMST_AT_EPOCH_DEG: Double = 280.46061837

    /** 恒星日相对太阳日的日角速度（度 / 天）：360° 加上岁差 / 速率修正项。 */
    private const val GMST_RATE_DEG_PER_DAY: Double = 360.98564736629

    /** 一天毫秒数。 */
    private const val MILLIS_PER_DAY: Double = 86_400_000.0

    /**
     * 由 UTC 时刻与观测点经度推地方恒星时（度，[0,360)）。
     * @param utcMillis UTC 毫秒时间戳
     * @param lonDeg 观测点东经（度；西经取负值）
     */
    fun localSiderealTimeDeg(utcMillis: Long, lonDeg: Double): Double {
        val days = (utcMillis - J2000_EPOCH_MILLIS) / MILLIS_PER_DAY
        val gmst = GMST_AT_EPOCH_DEG + GMST_RATE_DEG_PER_DAY * days
        return ((gmst + lonDeg) % 360.0 + 360.0) % 360.0
    }
}
