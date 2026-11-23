package io.legado.app.ui.map.sandbox

/**
 * 关隘 / 峡谷标记（切片-06 §四）。
 *
 * chokePoints 标记的位置，地面画一道横向暗红短线，像隘口封住了路；
 * 短线朝向与通道走向垂直，[headingDeg] 为通道走向方位角，渲染层据此转 90° 画线。
 */
data class ChokePoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val headingDeg: Double,
)

/**
 * 战场遗址 / 古迹方钉（切片-06 §四 战场遗址）。
 *
 * sites 古迹在地面扎一枚墨色微小方钉，钉顶朱砂色；点它弹出战报回顾。
 * [report] 为战报回顾文案；空字符串时渲染层仍画钉，点击信息卡走「史料未载」兜底。
 */
data class SitePin(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val kind: String,
    val note: String,
    val report: String = "",
)

/**
 * 行军尾迹（切片-06 §四 行军路线）。
 *
 * 箭头尾部拖一段极淡的金色尾迹粒子，8-12 粒渐隐，像军队走过扬起的尘土；
 * 粒子位置沿路线尾部分布，透明度随序号递减，飘散由渲染层按帧推进。
 */
data class MarchTrail(
    val id: String,
    val points: List<LatLng>,
    val particleCount: Int,
) {

    init {
        require(particleCount in SandboxConfig.TRAIL_PARTICLES_MIN..SandboxConfig.TRAIL_PARTICLES_MAX) {
            "尾迹粒子数须在 ${SandboxConfig.TRAIL_PARTICLES_MIN}-${SandboxConfig.TRAIL_PARTICLES_MAX} 之间"
        }
    }

    /** 第 i 粒尾迹粒子在路线上的比例（越靠后越淡）。 */
    fun particleT(index: Int): Double {
        if (particleCount <= 1) return 1.0
        val k = index.toDouble() / (particleCount - 1)
        return 1.0 - k * 0.7 // 尾部粒子保留 30% 浓度，避免端头一下子断掉
    }
}
