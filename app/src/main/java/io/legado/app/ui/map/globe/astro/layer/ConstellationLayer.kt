package io.legado.app.ui.map.globe.astro.layer

import io.legado.app.data.entities.ConstellationLineEntity
import io.legado.app.ui.map.globe.astro.data.SkyNameIndex

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

    /** 星点 ↔ 星座 code 双向反查索引（判据 3 · N1；装载期随线段重建）。 */
    private var nameIndex: SkyNameIndex = SkyNameIndex.empty()

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
        rebuildNameIndex()
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

    /** 由线段端点重建 code ↔ starId 双向索引；同一星座端点去重归集。 */
    private fun rebuildNameIndex() {
        val byCode = HashMap<String, MutableList<Long>>()
        segments.forEach { seg ->
            byCode.getOrPut(seg.constellationId) { ArrayList() }
                .add(seg.fromStarId)
                .add(seg.toStarId)
        }
        nameIndex = SkyNameIndex.build(byCode.mapValues { it.value.distinct() })
    }

    /** 星表主键 → 所属 IAU 星座三字母缩写；未收录返回 null（N1 反查入口）。 */
    fun constellationOfStar(starId: Long): String? = nameIndex.codeOf(starId)

    /** 星座 code → 成员星主键去重列表；未收录返回空列表。 */
    fun starsOfConstellation(code: String): List<Long> = nameIndex.membersOf(code)
}
