package io.legado.app.help.person

/**
 * 人物置信分层（规格设计规格 §2.1 + 历史附件 §2.1 人物系统）：
 *
 * | 层 | 来源 | 质量 | 用途 |
 * |---|---|---|---|
 * | [L1] | 手校核心库 + 王公纪年 | 生卒 / 在位已核 | 主力体验，默认展示 |
 * | [L2] | CBDB 多源核验 / 两源一致（待校） | 可信 | 补充展示，可进默认候选 |
 * | [L3] | CBDB 单源孤证，生卒缺 | 存疑 | 只进候选池，不默认命中 |
 *
 * 分层口径与 [PersonProfile.tier] 一一对应；[canDefault] 为 false 的层
 * 永远不会被 [PersonDirectory] 选为无上下文默认命中项，只能经候选切换显式定位。
 *
 * 三源置信（总纲 L82）与分层是两条轴：tier 管「来源桶 + 能否默认命中」，
 * [reviewTag] 管「三源交叉的展示标注」——high 不标；medium 2/3 标「待校」
 * （仍可默认命中，但展示必须挂待校标识）；low ≤1/3 标「待考·来源冲突」，不进默认命中。
 */
enum class ConfidenceTier(
    val wire: String,
    val label: String,
    val canDefault: Boolean,
) {
    L1("L1", "手校核心", true),
    L2("L2", "多源核验", true),
    L3("L3", "孤证存疑", false);

    companion object {
        /**
         * 资产 confidence 列（high / medium / low）+ 有无生卒 → 分层；解析层统一走这里。
         * 口径（总纲 L82）：high 3/3 → [L1]；medium 2/3 → [L2]（待校，仍可默认命中）；
         * low ≤1/3 → [L3]（不进默认命中）；无置信列时按生卒兜底：有生卒 [L2]，生卒缺 [L3]。
         */
        fun fromConfidence(confidence: String?, hasYears: Boolean): ConfidenceTier = when {
            confidence == "high" -> L1
            confidence == "medium" -> L2
            confidence == "low" -> L3
            hasYears -> L2
            else -> L3
        }

        /**
         * 三源置信对应的展示标注：high / 无置信列 → null（不标）；
         * medium 2/3 →「待校」；low ≤1/3 →「待考·来源冲突」。
         * 标签只影响展示，不改变 [canDefault]：medium 标待校后仍可默认命中。
         */
        fun reviewTag(confidence: String?): String? = when (confidence?.trim()?.lowercase()) {
            "high" -> null
            "medium" -> "待校"
            "low" -> "待考·来源冲突"
            else -> null
        }

        /** 旧同名分组资产里的 L2 / L3 标记；缺省按孤证 L3 兜底。 */
        fun fromAssetMark(mark: String?): ConfidenceTier =
            if (mark?.trim() == "L2") L2 else L3
    }
}
