package io.legado.app.ui.globe.core.replay

import io.legado.app.ui.globe.core.GlobeConfig

/**
 * 战役阵营 / 势力配色（纯 JVM，切片 18）。
 *
 * 切片 18：「多军路线同时出现，颜色按战役阵营区分（亚历山大=暖金、波斯=暗铜，
 * 跟沙盘配色一张表）。」「罗马帝国 → 暖金色半透明；波斯 → 暗铜；蒙古 → 朱砂红；
 * 其余 9 套同色系。」
 *
 * 颜色全部取自切片 03 的 12 色 token 与既有沙盘阵营色，不引入新的高饱和靛蓝紫。
 */
object FactionPalette {

    /** 阵营 id → #RRGGBB。 */
    val FACTION_COLORS: Map<String, String> = mapOf(
        // 切片 18 点名的两组
        "macedon" to "#D4A54A",      // 亚历山大 / 马其顿 —— 暖金
        "persia" to "#8B6914",       // 波斯 —— 暗铜
        // 势力图层（切片 18：罗马暖金半透明、波斯暗铜、蒙古朱砂红，其余同色系）
        "rome" to "#D4A54A",
        "mongol" to "#C23B2E",       // 蒙古 —— 朱砂红
        "carthage" to "#B05A3C",
        "crusader" to "#3C5C6E",
        "viking" to "#5C6A8A",
        "spain" to "#8A5C4C",
        "arab" to "#4C8A6A",
        "ottoman" to "#3C7A5C",
        "byzantine" to "#4C6E9A",
        "frankish" to "#7A4B8A",
        "british" to "#3B5C6E",
        "egypt" to "#8B5A3C",
        "seljuk" to "#6A7A4C",
    )

    /** 未知阵营兜底：暖金辉光色。 */
    val FALLBACK: String = "#D4A54A"

    fun colorOf(factionId: String): String = FACTION_COLORS[factionId] ?: FALLBACK

    /** #RRGGBB → 0..1 RGB 三元组；非法串回退暖金。 */
    fun rgb(hex: String): Triple<Float, Float, Float> {
        val clean = hex.removePrefix("#")
        val parsed = clean.toIntOrNull(16) ?: return Triple(
            GlobeConfig.GLOW_RED / 255f,
            GlobeConfig.GLOW_GREEN / 255f,
            GlobeConfig.GLOW_BLUE / 255f,
        )
        val r = ((parsed shr 16) and 0xFF) / 255f
        val g = ((parsed shr 8) and 0xFF) / 255f
        val b = (parsed and 0xFF) / 255f
        return Triple(r, g, b)
    }

    /** 0..255 整数通道（GL 层 / atlas 兜底用）。 */
    fun channels(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#")
        val parsed = clean.toIntOrNull(16) ?: return Triple(
            GlobeConfig.GLOW_RED, GlobeConfig.GLOW_GREEN, GlobeConfig.GLOW_BLUE,
        )
        return Triple((parsed shr 16) and 0xFF, (parsed shr 8) and 0xFF, parsed and 0xFF)
    }
}
