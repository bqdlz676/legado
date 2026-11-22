package io.legado.app.help.chrono

/**
 * 地名时空本体的纯 Kotlin 内核（不依赖 Android Context / Assets）。
 *
 * 设计要点：
 *  - 运行时由 [io.legado.app.help.PlaceTimeDict] 读完 place_entity.txt / place_name.txt
 *    后调用 [importRecords] 把资产表合并进来，按 id 覆盖种子；
 *  - 单测 / 无资产环境下用内置种子兜底，保证消歧链路不出现空白态；
 *  - 置信度沿用 schema 契约：high | medium | low；tier 1 实测→high，2 考证→medium，3 推定→low。
 */
object ChronoPlaces {

    data class PlaceSeed(
        val id: String,
        val name: String,
        val kind: Char,                 // 'P' 点 / 'A' 面
        val start: Int,
        val end: Int,
        val tier: Int,
        val aliases: List<String>,
        val confidence: String,
        val source: String,
        val note: String = "",
    ) {
        val isPoint: Boolean get() = kind == 'P'
        val span: Int get() = end - start
        fun inSpan(year: Int): Boolean = year in start..end
    }

    @Volatile
    private var records: List<PlaceSeed> = builtIn()

    /** 资产表灌入：同 id 覆盖种子，种子独有的条目保留兜底 */
    fun importRecords(all: List<PlaceSeed>) {
        if (all.isEmpty()) return
        val override = all.associateBy { it.id }
        val merged = records.map { override[it.id] ?: it } +
            all.filter { candidate -> records.none { it.id == candidate.id } }
        records = merged
    }

    fun all(): List<PlaceSeed> = records

    /** 按正名或别名取候选；查询名大小写/空白不敏感 */
    fun candidates(name: String): List<PlaceSeed> {
        val q = name.trim()
        if (q.isEmpty()) return emptyList()
        return records.filter { r -> r.name == q || q in r.aliases }
    }

    /** 仅供单测：还原内置种子 */
    fun resetForTest() {
        records = builtIn()
    }

    private fun builtIn(): List<PlaceSeed> = listOf(
        // —— 春秋十二诸侯都邑（种子，资产表载入后按 id 覆盖）——
        PlaceSeed("P001", "曲阜", 'P', -1043, -256, 2,
            listOf("鲁", "鲁国", "奄"), "medium", "谭其骧/CHGIS 考证", "鲁国都城，今山东曲阜"),
        PlaceSeed("P002", "临淄", 'P', -1046, -221, 2,
            listOf("齐", "齐国", "营丘"), "medium", "谭其骧/CHGIS 考证", "齐国都城，今山东淄博临淄区"),
        PlaceSeed("P003", "绛", 'P', -1035, -376, 2,
            listOf("晋", "晋国", "翼", "故绛"), "medium", "谭其骧/CHGIS 考证", "晋国都城，今山西翼城"),
        PlaceSeed("P004", "郢", 'P', -1115, -278, 2,
            listOf("楚", "楚国", "纪南城"), "medium", "谭其骧/CHGIS 考证", "楚国都城，今湖北荆州"),
        PlaceSeed("P005", "雍", 'P', -905, -383, 2,
            listOf("秦", "秦国", "雍城"), "medium", "谭其骧/CHGIS 考证", "秦国旧都，今陕西凤翔"),
        PlaceSeed("P006", "商丘", 'P', -1043, -286, 2,
            listOf("宋", "宋国", "睢阳"), "medium", "谭其骧/CHGIS 考证", "宋国都城，今河南商丘"),
        PlaceSeed("P007", "朝歌", 'P', -1042, -209, 2,
            listOf("卫", "卫国", "沫"), "medium", "谭其骧/CHGIS 考证", "卫国都城，今河南淇县"),
        PlaceSeed("P008", "新郑", 'P', -806, -375, 2,
            listOf("郑", "郑国", "郑韩故城"), "medium", "谭其骧/CHGIS 考证", "郑国都城，今河南新郑"),
        PlaceSeed("P009", "蓟", 'P', -1046, -222, 2,
            listOf("燕", "燕国"), "medium", "谭其骧/CHGIS 考证", "燕国都城，今北京"),
        // 晋国晚期都城（与绛同名「晋」，靠年份区间消歧）
        PlaceSeed("P045", "新田", 'P', -585, -376, 2,
            listOf("晋", "晋国"), "medium", "谭其骧/CHGIS 考证", "晋国晚期都城，今侯马"),
        // 东周王城（「周 / 东周」在春秋语境下指向洛邑）
        PlaceSeed("P042", "洛邑", 'P', -1036, 1911, 1,
            listOf("周", "东周", "洛阳", "成周", "雒阳", "河南"), "high", "遗址实测", "成周洛邑，今洛阳，东周王城"),
        // 晋地泛称（面实体，tier 3 文献推定）
        PlaceSeed("A012", "晋", 'A', -221, 1911, 3,
            listOf("并州", "山西", "三晋"), "low", "文献推定", "晋地/并州，山西地区泛称（示意色块）"),
        // 现代晋城：今址参考层，低置信度，不进默认命中
        PlaceSeed("P057", "晋城", 'P', 1912, 2024, 3,
            listOf("晋", "晋城"), "low", "今址参考", "现代山西晋城；晋都遗址在翼城/侯马，非此城"),
    )
}
