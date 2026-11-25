package io.legado.app.help.artifact

import io.legado.app.help.bubble.contract.Kind

/**
 * D16 名物库十七类枚举（锚点卡 `docs/锚点/D16-名物库.md` §二参数表；
 * 原文 `docs/落文-原文/落文-原文-09-名物库完整方案.md:11`）。
 *
 * 为什么 [wire] 持中文原文字面值：类别名必须与 09:11 逐字一致，不另造英文对外暴露；
 * 序号仅为清点用途，类别名不许改写、不许增删（判据三-1）。
 *
 * 冲突待裁（不擅裁）：[ALLUSION]「典故」在 09:11 被列入十七类；而
 * `docs/问题清单.md` D16 行按 F-07 剔除典故（典故属 Allusion Provider）。
 * 本枚举按锚点卡逐字保留 17 项，冲突出处写在条目注释里，裁决回填前不做并类或拆类。
 */
enum class ArtifactCategory(
    /** 中文类别名，与 09:11 逐字对齐。 */
    val wire: String,
    /** 落库 schema 表名；复用既有表时为既有表名，典故暂不挂表。 */
    val schemaTable: String?,
    /** 兜底气泡类型；空数据回退时按类别映射到冻结 [Kind] 八值之一。 */
    val fallbackKind: Kind,
) {

    /** 1 人物（既有 person 表，走 D2 人物原 Provider 路径，本层不双轨）。 */
    PERSON("人物", "person", Kind.PERSON),

    /** 2 地名（既有 place 表，走 D3 地名原 Provider 路径，本层不双轨）。 */
    PLACE("地名", "place", Kind.PLACE),

    /** 3 草木（既有 plant 表；PlantProvider 草木 800 种，09:29）。 */
    PLANT("草木", "plant", Kind.WORD),

    /** 4 鸟兽（既有 animal 表；AnimalProvider 鸟兽 600 种，09:29）。 */
    ANIMAL("鸟兽", "animal", Kind.WORD),

    /** 5 药名（既有 medicine 表；MedicineProvider 药名 1800 种，09:29）。 */
    MEDICINE("药名", "medicine", Kind.WORD),

    /** 6 名剑（artifact 表，type=sword）。 */
    SWORD("名剑", "artifact", Kind.WORD),

    /** 7 名马（artifact 表，type=horse）。 */
    HORSE("名马", "artifact", Kind.WORD),

    /** 8 名寺(古刹)（architecture 表，type=temple）。 */
    TEMPLE("名寺(古刹)", "architecture", Kind.WORD),

    /** 9 名道观（architecture 表，type=daoist）。 */
    DAOIST("名道观", "architecture", Kind.WORD),

    /** 10 名楼(台阁)（architecture 表，type=tower / pavilion）。 */
    TOWER("名楼(台阁)", "architecture", Kind.WORD),

    /** 11 名琴(乐器)（artifact 表，type=instrument）。 */
    INSTRUMENT("名琴(乐器)", "artifact", Kind.WORD),

    /** 12 名鼎(礼器)（artifact 表，type=vessel）。 */
    VESSEL("名鼎(礼器)", "artifact", Kind.WORD),

    /** 13 星宿(天文)（astronomy 表）。 */
    ASTRONOMY("星宿(天文)", "astronomy", Kind.WORD),

    /** 14 名酒(名食)（food 表）。 */
    FOOD("名酒(名食)", "food", Kind.WORD),

    /** 15 名锦(名瓷/服饰)（artifact 表，type=textile）。 */
    TEXTILE("名锦(名瓷/服饰)", "artifact", Kind.WORD),

    /**
     * 16 典故（冲突待裁：09:11 列入 vs 问题清单 D16 行按 F-07 剔除，典故属 Allusion Provider）。
     * 按锚点卡保留此项；schemaTable 暂为 null，裁决回填前不挂 D16 数据表。
     */
    ALLUSION("典故", null, Kind.ALLUSION),

    /** 17 山川风物（独立 landscape 表，不混 artifact；09:23/37）。 */
    LANDSCAPE("山川风物", "landscape", Kind.WORD),
    ;

    companion object {

        /** 十七类逐字 wire 列表，供清点与单测逐字对照。 */
        val WIRES: List<String> = entries.map { it.wire }

        /** 按中文类别名反查；未登记返回 null（未知类别走兜底，不抛异常）。 */
        fun byWire(wire: String): ArtifactCategory? = entries.firstOrNull { it.wire == wire }
    }
}
