package io.legado.app.core.chronology

/**
 * 落文 · 时空纪年 / 同名消歧引擎 —— 三源交叉置信度模型。
 *
 * 三源（学术渠道，互相独立，逐条交叉）：
 *  - [SourceChannel.ARCHAEOLOGY] 遗址实测（考古遗址实测点）
 *  - [SourceChannel.ATLAS]       谭其骧《中国历史地图集》/ CHGIS 考证坐标
 *  - [SourceChannel.TEXT]        文献推定 / ctext 地理注释（精确到县，只画色块）
 *  - [SourceChannel.REFERENCE]   今址参考层（现代同名异地对照，独立成层，不参与三源打分）
 *
 * 置信度分级（锁定规则，不可改）：
 *  - 3/3 三源一致 → [Confidence.HIGH]，默认命中；
 *  - 2/3 两源一致 → [Confidence.MEDIUM]，标「待校」；
 *  - ≤1/3 单源或来源冲突 → [Confidence.LOW]，标「待考·来源冲突」，不进默认命中；
 *  - 今址参考层一律 LOW，且 [Confidence.canDefault] = false。
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

enum class Confidence(
    val wire: String,
    /** 展示标签：high 不标；medium 标「待校」；low 标「待考·来源冲突」。 */
    val label: String,
    /** 是否允许进默认命中：只有 high / medium 可以；low 一律不进。 */
    val canDefault: Boolean,
) {
    HIGH("high", "", true),
    MEDIUM("medium", "待校", true),
    LOW("low", "待考·来源冲突", false);

    companion object {
        fun of(raw: String?): Confidence = when (raw?.trim()?.lowercase()) {
            "high" -> HIGH
            "medium" -> MEDIUM
            else -> LOW
        }

        /**
         * 三源交叉打分。
         * @param channels   该实体汇集到的去重学术渠道（实体行 + 名称快照行）
         * @param refLayer   是否今址参考层
         * @param tier       坐标层级 1 实测 / 2 考证 / 3 推定（tier1 遗址点按三源一致处理）
         */
        fun grade(channels: Set<SourceChannel>, refLayer: Boolean, tier: Int): Confidence {
            if (refLayer || SourceChannel.REFERENCE in channels) return LOW
            val scholarly = channels - SourceChannel.REFERENCE
            return when {
                tier == 1 || SourceChannel.ARCHAEOLOGY in scholarly -> HIGH
                scholarly.size >= 3 -> HIGH
                scholarly.size == 2 -> MEDIUM
                else -> LOW
            }
        }
    }
}
