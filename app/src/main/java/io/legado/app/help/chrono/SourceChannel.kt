package io.legado.app.help.chrono

/**
 * 三源交叉渠道（对齐 CHGIS / 谭其骧《中国历史地图集》/ 遗址实测 / 文献推定）。
 *
 *  - [ARCHAEOLOGY] 遗址实测（考古遗址实测点，tier 1）
 *  - [ATLAS]       谭其骧《中国历史地图集》/ CHGIS 考证坐标（tier 2）
 *  - [TEXT]        文献推定 / ctext 地理注释（tier 3，精确到县，只画色块）
 *  - [REFERENCE]   今址参考层（现代同名异地对照，独立成层，不参与三源打分）
 */
enum class SourceChannel {
    ARCHAEOLOGY, ATLAS, TEXT, REFERENCE;

    companion object {
        /** 把资产表里的 source 原文归类到渠道；无法归类返回 null。 */
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
