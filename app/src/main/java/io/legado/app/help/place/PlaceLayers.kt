package io.legado.app.help.place

/**
 * 18 套朝代图层（切片-05 §「扩到 18 套」表格，年代区间与核心变化逐字照抄）。
 *
 * 注意：部分图层年代相互交叠（如西晋 265~316 落在三国 220~280 内、
 * 隋 581~618 落在南北朝 420~589 内），这是原表如此，不做「去重修正」；
 * 下游按年代取层时允许命中多层，由消歧规则再排序。
 *
 * @param era        图层名（逐字照抄原表）
 * @param fromYear   起年（负为公元前）
 * @param toYear     止年（负为公元前）
 * @param changeNote 原表「核心变化」列：新增 / 已有
 */
data class PlaceLayer(
    val era: String,
    val fromYear: Int,
    val toYear: Int,
    val changeNote: String,
) {
    fun contains(year: Int): Boolean = year in fromYear..toYear

    fun overlaps(range: IntRange): Boolean = fromYear <= range.last && toYear >= range.first
}

/** 18 套图层的内置目录。区间数值与切片-05 表格逐字一致，改动前先回原文核对。 */
object PlaceLayers {

    val ALL: List<PlaceLayer> = listOf(
        PlaceLayer("西周", -1046, -771, "新增"),
        PlaceLayer("春秋", -770, -476, "已有"),
        PlaceLayer("战国", -475, -221, "已有"),
        PlaceLayer("秦", -221, -206, "新增"),
        PlaceLayer("西汉", -202, 8, "已有"),
        PlaceLayer("东汉", 25, 220, "新增"),
        PlaceLayer("三国", 220, 280, "已有"),
        PlaceLayer("西晋", 265, 316, "新增"),
        PlaceLayer("东晋/十六国", 317, 420, "新增"),
        PlaceLayer("南北朝", 420, 589, "新增"),
        PlaceLayer("隋", 581, 618, "新增"),
        PlaceLayer("唐", 618, 907, "已有"),
        PlaceLayer("五代十国", 907, 960, "新增"),
        PlaceLayer("北宋/辽", 960, 1127, "已有"),
        PlaceLayer("南宋/金/西夏", 1127, 1279, "新增"),
        PlaceLayer("元", 1271, 1368, "新增"),
        PlaceLayer("明", 1368, 1644, "新增"),
        PlaceLayer("清", 1644, 1912, "新增"),
    )

    /** 给定公元年命中的全部图层（交叠年份可能返回多层）；无命中返回空列表。 */
    fun at(year: Int): List<PlaceLayer> = ALL.filter { it.contains(year) }

    /** 给定年代区间相交的全部图层。 */
    fun overlaps(range: IntRange): List<PlaceLayer> = ALL.filter { it.overlaps(range) }
}
