package io.legado.app.ui.map.globe.astro.layer

import io.legado.app.data.entities.ConstellationLineEntity

/**
 * 西方 88 星座连线段（§四.12 `constellation_line`）。
 *
 * 〔两层分表口径〕星官成员连线走 [AsterismLayer]，本类只收 IAU 星座参考连线；
 * 两套体系不混表、不共用一段线（§六.6：星座是辅助参考层，云灰最淡，不抢金）。
 *
 * @property fromStarId 线端恒星 `star.id`
 * @property toStarId 线端恒星 `star.id`
 * @property constellationId 三字母 IAU 缩写
 */
data class ConstellationSegment(
    val fromStarId: Long,
    val toStarId: Long,
    val constellationId: String,
)

/**
 * 星座名标签（§五.5：14sp 横排，拉丁名与中文名两行）。
 * 锚点为天球赤道坐标，绘制时经 `StarProjector` 投到屏幕；标签字号恒定，不随缩放变小。
 */
data class ConstellationLabel(
    val constellationId: String,
    val nameZh: String,
    val nameLa: String,
    val anchorRaDeg: Double,
    val anchorDecDeg: Double,
)

/**
 * 星座参考层（纯 JVM 几何持有者，不碰 Canvas）。
 *
 * 〔职责边界〕本类只存连线 / 标签几何与选中态；投影、描边、字号归 `SkyCanvasRenderer`。
 * 数据由数据单的 `StarStore` 关系路径喂入（DAO 未就位时回落空表，线框 pass 不崩）。
 */
class ConstellationLayer {

    private val segments = ArrayList<ConstellationSegment>()
    private val labels = ArrayList<ConstellationLabel>()

    /** 当前选中星座三字母缩写；null = 未选中，全量参考线常显。 */
    var selectedConstellation: String? = null
        private set

    /** 从 DAO 连线表装载；重复装载先清旧（§新逻辑先接好再删旧，不留双份）。 */
    fun loadLines(lines: List<ConstellationLineEntity>) {
        segments.clear()
        lines.forEach { line ->
            segments += ConstellationSegment(
                fromStarId = line.fromStarId,
                toStarId = line.toStarId,
                constellationId = line.constellationId,
            )
        }
    }

    fun loadLabels(labels: List<ConstellationLabel>) {
        this.labels.clear()
        this.labels += labels
    }

    /** 点连线 / 名高亮全组（§九.3）：选中后只留该座线段，其余参考线淡出。 */
    fun select(constellationId: String?) {
        selectedConstellation = constellationId
    }

    /** 本帧应绘制的线段：选中态收敛到单座，未选中态全量。 */
    fun visibleSegments(): List<ConstellationSegment> {
        val sel = selectedConstellation ?: return segments
        return segments.filter { it.constellationId == sel }
    }

    fun allSegments(): List<ConstellationSegment> = segments

    fun allLabels(): List<ConstellationLabel> = labels
}
