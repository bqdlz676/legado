package io.legado.app.help.chrono

/**
 * 三源交叉渠道（切片-05 §「坐标可信度分层」）。
 *
 * 为什么单独枚举：置信度闸要数「去重后的学术渠道个数」，渠道必须先归类成有限集合，
 * 不能拿出处字符串现场比较，否则同义出处（谭图 / 地图集 / CHGIS）会被当成多源灌水。
 *
 * - [ARCHAEOLOGY] 遗址实测（考古遗址实测点，Tier1）
 * - [ATLAS]       谭其骧《中国历史地图集》/ CHGIS 考证坐标（Tier2）
 * - [TEXT]        文献推定 / 典籍地理注释（Tier3，精确到县，只画色块）
 * - [REFERENCE]   今址参考层（现代同名异地对照，独立成层，不参与三源打分）
 */
enum class SourceChannel {
    ARCHAEOLOGY, ATLAS, TEXT, REFERENCE;

    companion object {
        /** 把出处原文归类到渠道；归类不了返回 null（宁可少算一源，也不硬凑三源）。 */
        fun classify(raw: String?): SourceChannel? {
            val s = raw?.trim().orEmpty()
            if (s.isEmpty()) return null
            return when {
                s.contains("今址") || s.contains("参考层") -> REFERENCE
                s.contains("实测") || s.contains("遗址") -> ARCHAEOLOGY
                s.contains("CHGIS") || s.contains("谭其骧") || s.contains("地图集") || s.contains("考证") -> ATLAS
                s.contains("ctext") || s.contains("文献") || s.contains("推定") || s.contains("注释") -> TEXT
                else -> null
            }
        }
    }
}
