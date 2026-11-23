package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.components.ComponentPalette

/**
 * 四渡赤水推演壳颜色出口（§1.5-B 12 色 token 铁律）。
 *
 * 为什么只在这里做颜色映射：壳层任何底图 / 线路 / 棋子 / 信息卡都必须走 token，
 * 不允许在各 Composable 里另写十六进制；势力配色收口到同一张表。
 * 纯 JVM 引擎层只持 ARGB Long，本文件再向 Compose 做类型转换。
 */
object CrossingTokens {

    /** --bg-page 绢帛纸白。 */
    val bgPage: Long get() = ComponentPalette.BG_PAGE

    /** --bg-surface 绢帛面 / 底图丘陵底色。 */
    val bgSurface: Long get() = ComponentPalette.BG_SURFACE

    /** --bg-overlay 茶玻遮罩。 */
    val bgOverlay: Long get() = ComponentPalette.BG_OVERLAY

    /** --text-primary 墨色正文。 */
    val textPrimary: Long get() = ComponentPalette.TEXT_PRIMARY

    /** --text-secondary 副文茶褐。 */
    val textSecondary: Long get() = ComponentPalette.TEXT_SECONDARY

    /** --text-tertiary 次要灰褐。 */
    val textTertiary: Long get() = ComponentPalette.TEXT_TERTIARY

    /** --text-accent 朱砂（遭遇 / 警示 / 降级标识）。 */
    val textAccent: Long get() = ComponentPalette.TEXT_ACCENT

    /** --brand-gold 暖金（己方棋子 / 渡口金柱 / 行军主线）。 */
    val brandGold: Long get() = ComponentPalette.BRAND_GOLD

    /** --brand-copper 古铜（盟友 / 山脊线 / 次要路线）。 */
    val brandCopper: Long get() = ComponentPalette.BRAND_COPPER

    /** --color-link 链接朱砂。 */
    val colorLink: Long get() = ComponentPalette.COLOR_LINK

    /** --color-divider 细分隔线。 */
    val colorDivider: Long get() = ComponentPalette.COLOR_DIVIDER

    /** --color-glow 暖金辉光（渡口光柱 / 棋子外圈微光）。 */
    val colorGlow: Long get() = ComponentPalette.COLOR_GLOW

    /** 石青（12 色外唯一增补）：敌方灰青棋子 / 河面。 */
    val stoneBlue: Long get() = ComponentPalette.STONE_BLUE

    // ── 势力填充透明度（§1.5-E 明文口径） ─────────────────────

    /** 己方势力填充透明度 0.12。 */
    const val OWN_FILL_ALPHA: Float = 0.12f

    /** 敌方势力填充透明度 0.10。 */
    const val ENEMY_FILL_ALPHA: Float = 0.10f

    /** 盟友势力填充透明度 0.08。 */
    const val ALLY_FILL_ALPHA: Float = 0.08f
}
