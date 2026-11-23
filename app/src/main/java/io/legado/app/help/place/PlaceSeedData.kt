package io.legado.app.help.place

/**
 * 内置种子地名（模块 D3）。
 *
 * 为什么只放少量种子：规格 §升格段「宁可 5000 条有出处，不要 9 万条没来路」。
 * 全量 CHGIS 矢量 / 考证点由后续资产解析层经 [PlaceDirectory.register] 灌入，
 * 种子只保证无资产环境下消歧链路不出现空白态，并覆盖切片-05 验收用例。
 *
 * 坐标口径：
 * - Tier1 取考古遗址实测点；
 * - Tier2 取谭其骧图集 / CHGIS 考证位置，标出处；
 * - Tier3 只推定到县，不给经纬度、不产点位，只给县界色块；
 * - 今址参考层独立成层，恒 low，不进默认命中。
 */
internal object PlaceSeedData {

    fun entities(): List<PlaceEntity> = buildList {
        // ---------- Tier1 遗址实测 ----------

        add(
            PlaceEntity(
                placeId = "P001",
                kind = PlaceKind.POINT,
                canonicalName = "曲阜",
                fromYear = -1043, toYear = -256,
                tier = PlaceTier.T1_SITE,
                channels = setOf(PlaceChannel.ARCHAEOLOGY),
                source = PlaceSource.Archaeology("曲阜鲁国故城遗址"),
                adminLevel = "国都", superior = "鲁",
                lng = 116.99, lat = 35.59,
                note = "鲁国都，今山东曲阜",
                aliases = listOf("鲁", "鲁国", "奄"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P002",
                kind = PlaceKind.POINT,
                canonicalName = "临淄",
                fromYear = -859, toYear = -221,
                tier = PlaceTier.T1_SITE,
                channels = setOf(PlaceChannel.ARCHAEOLOGY),
                source = PlaceSource.Archaeology("临淄齐国故城遗址"),
                adminLevel = "国都", superior = "齐",
                lng = 118.31, lat = 36.81,
                note = "齐国都，今山东淄博临淄区",
                aliases = listOf("齐", "齐国", "营丘"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P008",
                kind = PlaceKind.POINT,
                canonicalName = "新郑",
                fromYear = -806, toYear = -375,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·先秦卷·郑图"),
                adminLevel = "国都", superior = "郑",
                lng = 113.74, lat = 34.40,
                note = "春秋郑都、战国韩都，今河南新郑",
                aliases = listOf("郑", "郑国", "新郑", "郑韩故城"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P011",
                kind = PlaceKind.POINT,
                canonicalName = "长安",
                fromYear = -202, toYear = 8,
                tier = PlaceTier.T1_SITE,
                channels = setOf(PlaceChannel.ARCHAEOLOGY),
                source = PlaceSource.Archaeology("汉长安城遗址"),
                adminLevel = "国都", superior = "西汉",
                lng = 108.87, lat = 34.27,
                note = "西汉都城，今陕西西安西北",
                aliases = listOf("西京"),
            )
        )

        // ---------- Tier2 谭其骧 / CHGIS 考证 ----------

        add(
            PlaceEntity(
                placeId = "P003",
                kind = PlaceKind.POINT,
                canonicalName = "绛",
                fromYear = -746, toYear = -585,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·春秋卷·晋图"),
                adminLevel = "国都", superior = "晋",
                lng = 111.70, lat = 35.73,
                note = "晋献公迁都绛（故绛），今山西翼城东南",
                aliases = listOf("晋", "晋国", "翼", "故绛"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P045",
                kind = PlaceKind.POINT,
                canonicalName = "新田",
                fromYear = -585, toYear = -376,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·春秋卷·晋图"),
                adminLevel = "国都", superior = "晋",
                lng = 111.37, lat = 35.62,
                note = "晋景公十五年迁新田（新绛），今山西侯马",
                aliases = listOf("晋", "晋国", "新绛"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P-JIN",
                kind = PlaceKind.POLYGON,
                canonicalName = "晋国",
                fromYear = -1033, toYear = -376,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·先秦卷·晋图"),
                adminLevel = "诸侯国", superior = "周",
                regionLabel = "今山西西南部及河南北部一带",
                note = "周初封唐，叔虞始封；春秋霸主，三家分晋后亡",
                aliases = listOf("晋", "晋邦"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P042",
                kind = PlaceKind.POINT,
                canonicalName = "洛邑",
                fromYear = -1036, toYear = 1911,
                tier = PlaceTier.T1_SITE,
                channels = setOf(PlaceChannel.ARCHAEOLOGY, PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.Archaeology("东周王城遗址"),
                adminLevel = "国都", superior = "周",
                lng = 112.45, lat = 34.62,
                note = "成周洛邑，东周王城，今河南洛阳",
                aliases = listOf("洛邑", "周", "东周", "洛阳", "成周", "雒阳", "河南"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P021",
                kind = PlaceKind.POINT,
                canonicalName = "咸阳",
                fromYear = -350, toYear = -206,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·战国卷·秦图"),
                adminLevel = "国都", superior = "秦",
                lng = 108.72, lat = 34.33,
                note = "秦都，今陕西咸阳东北",
                aliases = listOf("秦"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P022",
                kind = PlaceKind.POINT,
                canonicalName = "邯郸",
                fromYear = -386, toYear = -222,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·战国卷·赵图"),
                adminLevel = "国都", superior = "赵",
                lng = 114.54, lat = 36.61,
                note = "赵都，今河北邯郸",
                aliases = listOf("赵", "赵国"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P023",
                kind = PlaceKind.POINT,
                canonicalName = "大梁",
                fromYear = -361, toYear = -225,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·战国卷·魏图"),
                adminLevel = "国都", superior = "魏",
                lng = 114.31, lat = 34.80,
                note = "魏惠王迁都大梁，今河南开封",
                aliases = listOf("魏", "魏国", "开封"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P024",
                kind = PlaceKind.POINT,
                canonicalName = "郢",
                fromYear = -689, toYear = -278,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·先秦卷·楚图"),
                adminLevel = "国都", superior = "楚",
                lng = 112.24, lat = 30.35,
                note = "楚都，今湖北荆州江陵",
                aliases = listOf("楚", "楚国", "郢都", "江陵"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P025",
                kind = PlaceKind.POINT,
                canonicalName = "成都",
                fromYear = -311, toYear = 316,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·秦汉卷·蜀郡图"),
                adminLevel = "郡治",
                lng = 104.07, lat = 30.67,
                note = "蜀地中心，今四川成都",
                aliases = listOf("蜀", "蜀郡"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P026",
                kind = PlaceKind.POINT,
                canonicalName = "会稽",
                fromYear = -490, toYear = -222,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·先秦卷·越图"),
                adminLevel = "国都", superior = "越",
                lng = 120.58, lat = 30.00,
                note = "越都，今浙江绍兴",
                aliases = listOf("越", "越国"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P027",
                kind = PlaceKind.POINT,
                canonicalName = "蓟",
                fromYear = -330, toYear = -222,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·战国卷·燕图"),
                adminLevel = "国都", superior = "燕",
                lng = 116.40, lat = 39.90,
                note = "燕都，今北京西南",
                aliases = listOf("燕", "燕国", "蓟城"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P028",
                kind = PlaceKind.POINT,
                canonicalName = "商丘",
                fromYear = -1040, toYear = -286,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·先秦卷·宋图"),
                adminLevel = "国都", superior = "宋",
                lng = 115.65, lat = 34.44,
                note = "宋都，今河南商丘",
                aliases = listOf("宋", "宋国"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P029",
                kind = PlaceKind.POINT,
                canonicalName = "宛",
                fromYear = -680, toYear = -221,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·先秦卷·楚图"),
                adminLevel = "邑",
                lng = 112.53, lat = 33.00,
                note = "楚邑，今河南南阳",
                aliases = listOf("南阳"),
            )
        )
        add(
            PlaceEntity(
                placeId = "P030",
                kind = PlaceKind.POINT,
                canonicalName = "晋阳",
                fromYear = -497, toYear = -376,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT),
                source = PlaceSource.TanAtlas("谭图·春秋卷·晋图"),
                adminLevel = "邑", superior = "晋",
                lng = 112.55, lat = 37.87,
                note = "赵卿邑，今山西太原西南",
                aliases = listOf("太原"),
            )
        )

        // ---------- Tier3 文献推定：只色块，不给点位 ----------

        add(
            PlaceEntity(
                placeId = "P050",
                kind = PlaceKind.POLYGON,
                canonicalName = "长勺",
                fromYear = -684, toYear = -684,
                tier = PlaceTier.T3_TEXT,
                channels = setOf(PlaceChannel.TEXT),
                source = PlaceSource.ClassicText("《左传·庄公十年》"),
                adminLevel = "邑",
                regionLabel = "今山东莱芜东北一带",
                radiusKm = 20.0,
                note = "长勺之战所在地，文献推定到县，不产点位",
            )
        )
        add(
            PlaceEntity(
                placeId = "P051",
                kind = PlaceKind.POLYGON,
                canonicalName = "鹿上",
                fromYear = -639, toYear = -639,
                tier = PlaceTier.T3_TEXT,
                channels = setOf(PlaceChannel.TEXT),
                source = PlaceSource.ClassicText("《左传·僖公二十一年》"),
                adminLevel = "邑",
                regionLabel = "今安徽阜阳南一带",
                radiusKm = 20.0,
                note = "鹿上之盟所在地，文献推定到县，不产点位",
            )
        )
        add(
            PlaceEntity(
                placeId = "P-JINZHOU",
                kind = PlaceKind.POLYGON,
                canonicalName = "晋州",
                fromYear = 396, toYear = 1117,
                tier = PlaceTier.T3_TEXT,
                channels = setOf(PlaceChannel.TEXT),
                source = PlaceSource.ClassicText("《魏书·地形志》《元和郡县志》"),
                adminLevel = "州",
                regionLabel = "今山西临汾一带",
                radiusKm = 30.0,
                note = "北魏至唐宋晋州，与春秋晋国同名异地，文献推定到县",
                aliases = listOf("晋"),
            )
        )

        // ---------- 今址参考层：恒 low，不进默认命中 ----------

        add(
            PlaceEntity(
                placeId = "P057",
                kind = PlaceKind.POINT,
                canonicalName = "晋城",
                fromYear = 1912, toYear = 2024,
                tier = PlaceTier.T2_ATLAS,
                channels = setOf(PlaceChannel.REFERENCE),
                source = PlaceSource.ModernReference("今山西晋城市"),
                adminLevel = "现代市",
                lng = 112.85, lat = 35.49,
                note = "现代同名异地，仅作今址对照，独立成层；晋都遗址在翼城/侯马，非此城",
                aliases = listOf("晋", "晋城"),
                refLayer = true,
            )
        )
    }

    /** `place_name` 表：随年代改名的名称快照。 */
    fun nameRows(): List<PlaceNameRow> = listOf(
        PlaceNameRow("成周", "P042", -770, -256, "东周", PlaceSource.TanAtlas("谭图·先秦卷·周图")),
        PlaceNameRow("雒阳", "P042", 25, 220, "东汉", PlaceSource.TanAtlas("谭图·东汉卷·司隶图")),
        PlaceNameRow("西京", "P011", -202, 8, "西汉", PlaceSource.Archaeology("汉长安城遗址")),
    )
}
