package io.legado.app.ui.map.globe

import io.legado.app.ui.components.ComponentPalette

/**
 * 地球仪模块的颜色出口（切片 03 §12 色 token 铁律）。
 *
 * 为什么只在这里做颜色映射：模块内任何 UI 叠加层（标签、光针、信息卡、时间轴）
 * 都必须走 12 色 token，不允许在各文件里另写十六进制；阵营配色也收口到同一张表。
 * 地形 / 海洋的地表着色属于切片 17 / 19 明文规定的纹理色值，单独放在
 * [GlobeSpecSurface]，与 UI token 区分开，避免把「地貌数据色」误当成「界面配色」。
 */
object GlobeTokens {

    /** --bg-page 绢帛纸白。 */
    val bgPage: Long get() = ComponentPalette.BG_PAGE

    /** --bg-surface 卡片 / 竹简面。 */
    val bgSurface: Long get() = ComponentPalette.BG_SURFACE

    /** --bg-overlay 茶玻遮罩。 */
    val bgOverlay: Long get() = ComponentPalette.BG_OVERLAY

    /** --text-primary 正文墨色。 */
    val textPrimary: Long get() = ComponentPalette.TEXT_PRIMARY

    /** --text-secondary 副文茶褐。 */
    val textSecondary: Long get() = ComponentPalette.TEXT_SECONDARY

    /** --text-tertiary 辅助 / 占位灰褐。 */
    val textTertiary: Long get() = ComponentPalette.TEXT_TERTIARY

    /** --text-accent 朱砂强调。 */
    val textAccent: Long get() = ComponentPalette.TEXT_ACCENT

    /** --brand-gold 暖金（己方 / 都城 / 金粒子）。 */
    val brandGold: Long get() = ComponentPalette.BRAND_GOLD

    /** --brand-copper 古铜（盟友 / 郡邑铜柱）。 */
    val brandCopper: Long get() = ComponentPalette.BRAND_COPPER

    /** --color-link 链接朱砂。 */
    val colorLink: Long get() = ComponentPalette.COLOR_LINK

    /** --color-divider 细分隔线。 */
    val colorDivider: Long get() = ComponentPalette.COLOR_DIVIDER

    /** --color-glow 暖金辉光。 */
    val colorGlow: Long get() = ComponentPalette.COLOR_GLOW

    /** 石青（12 色外唯一增补）：敌方灰青势力色块取此色，与沙盘模块同口径。 */
    val stoneBlue: Long get() = ComponentPalette.STONE_BLUE
}

/**
 * 切片 17 / 19 明文规定的地表着色常量（地貌数据色，不是 UI 配色）。
 *
 * 为什么不和 12 色 token 混用：12 色 token 约束的是界面叠加层；这里的值来自规格
 * 表格本身（森林深绿、沙漠沙黄、海洋深蓝 / 浅海绿松石等），用于覆盖图解码最近邻
 * 匹配与海洋 shader 底色色号，属于渲染数据，改了就与规格对不上。
 */
object GlobeSpecSurface {

    // ───────────────────── 切片 17 覆盖类型参考色（覆盖图最近邻解码基准）─────────────────────
    const val FOREST: Int = 0x2D5A27
    const val GRASS: Int = 0x7A9A3A
    const val SOIL: Int = 0xA08060
    const val DESERT: Int = 0xD4B896
    const val SNOW: Int = 0xF0F0EE
    const val WATER: Int = 0x3C6E8A
    const val URBAN: Int = 0x8A7A6A

    /** 切片 19 极地冰盖：冷白微蓝。 */
    const val ICE: Int = 0xDCE8EE

    // ───────────────────── 切片 19 / 20 海洋三层底色色号 ─────────────────────
    /** 海底深蓝 #1B3A5C，球面底色。 */
    const val OCEAN_DEEP: Int = 0x1B3A5C

    /** 浅海 / 海岸绿松石 #3A7A8A，离陆地 3 像素内变浅。 */
    const val OCEAN_SHALLOW: Int = 0x3A7A8A
}
