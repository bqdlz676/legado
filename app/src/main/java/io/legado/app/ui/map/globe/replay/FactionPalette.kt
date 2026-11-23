package io.legado.app.ui.map.globe.replay

import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.replay.power.PowerRelation

/**
 * 战役阵营 / 势力配色（纯 JVM，切片 18）。
 *
 * 切片 18：「多军路线同时出现，颜色按战役阵营区分（亚历山大=暖金、波斯=暗铜，
 * 跟沙盘配色一张表）。」「罗马帝国 → 暖金色半透明；波斯 → 暗铜；蒙古 → 朱砂红；
 * 其余 9 套同色系。」
 *
 * 颜色全部取自切片 03 的 12 色 token：己方暖金、盟友古铜、敌方/中立灰褐，
 * 蒙古取朱砂强调；不引入 token 之外的高饱和靛蓝紫。
 */
object FactionPalette {

    /** 阵营 id → token ARGB（Long，与 ComponentPalette 同口径）。 */
    private val FACTION_TOKENS: Map<String, Long> = mapOf(
        // 切片 18 点名的两组
        "macedon" to GlobeTokens.brandGold,   // 亚历山大 / 马其顿 —— 暖金
        "persia" to GlobeTokens.brandCopper,  // 波斯 —— 暗铜
        // 势力图层（切片 18：罗马暖金半透明、波斯暗铜、蒙古朱砂红，其余同色系）
        "rome" to GlobeTokens.brandGold,
        "mongol" to GlobeTokens.textAccent,   // 蒙古 —— 朱砂红
        "carthage" to GlobeTokens.brandCopper,
        "crusader" to GlobeTokens.textTertiary,
        "viking" to GlobeTokens.textTertiary,
        "spain" to GlobeTokens.brandCopper,
        "arab" to GlobeTokens.brandCopper,
        "ottoman" to GlobeTokens.brandCopper,
        "byzantine" to GlobeTokens.textTertiary,
        "frankish" to GlobeTokens.textTertiary,
        "british" to GlobeTokens.textTertiary,
        "egypt" to GlobeTokens.brandCopper,
        "seljuk" to GlobeTokens.textTertiary,
    )

    /** 未知阵营兜底：暖金。 */
    val FALLBACK: Long get() = GlobeTokens.brandGold

    /** 阵营 id → token ARGB Long。 */
    fun tokenOf(factionId: String): Long = FACTION_TOKENS[factionId] ?: FALLBACK

    /**
     * 势力角色 → 填充 token（切片 20：己方暖金 / 敌方灰青 / 盟友铜色 / 中立灰褐），
     * 与沙盘模块 fillFor 同一张口径。
     */
    fun roleToken(relation: PowerRelation): Long = when (relation) {
        PowerRelation.OWN -> GlobeTokens.brandGold
        PowerRelation.ENEMY -> GlobeTokens.stoneBlue
        PowerRelation.ALLY -> GlobeTokens.brandCopper
        PowerRelation.NEUTRAL -> GlobeTokens.textTertiary
    }

    /** 势力角色 → #RRGGBB。 */
    fun roleHex(relation: PowerRelation): String = hex(roleToken(relation))

    /** 阵营 id → #RRGGBB 字符串（渲染层 / atlas 兜底用）。 */
    fun hexOf(factionId: String): String = hex(tokenOf(factionId))

    /** ARGB Long → #RRGGBB，去掉 alpha 通道。 */
    fun hex(argb: Long): String {
        val rgb = (argb and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')
        return "#$rgb"
    }

    /** #RRGGBB → 0..1 RGB 三元组；非法串回退暖金。 */
    fun rgb(hex: String): Triple<Float, Float, Float> {
        val parsed = parse(hex) ?: run {
            val fb = tokenOf("")
            return Triple(
                ((fb shr 16) and 0xFF) / 255f,
                ((fb shr 8) and 0xFF) / 255f,
                (fb and 0xFF) / 255f,
            )
        }
        return Triple(
            ((parsed shr 16) and 0xFF) / 255f,
            ((parsed shr 8) and 0xFF) / 255f,
            (parsed and 0xFF) / 255f,
        )
    }

    /** 0..255 整数通道（GL 层 / atlas 兜底用）。 */
    fun channels(hex: String): Triple<Int, Int, Int> {
        val parsed = parse(hex) ?: run {
            val fb = tokenOf("")
            return Triple(((fb shr 16) and 0xFF).toInt(), ((fb shr 8) and 0xFF).toInt(), (fb and 0xFF).toInt())
        }
        return Triple((parsed shr 16) and 0xFF, (parsed shr 8) and 0xFF, parsed and 0xFF)
    }

    private fun parse(hex: String): Int? {
        val clean = hex.removePrefix("#")
        return clean.toIntOrNull(16)
    }
}
