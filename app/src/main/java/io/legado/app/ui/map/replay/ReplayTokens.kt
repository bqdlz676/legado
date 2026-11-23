package io.legado.app.ui.map.replay

import io.legado.app.ui.components.ComponentPalette

/**
 * 战役回放模块颜色出口（切片-03 §12 色 token 铁律 + 切片-07 阵营 / 地形配色）。
 *
 * 为什么只在这里做颜色映射：棋子、箭头、冲击环、信息卡、时间轴都必须走 12 色 token，
 * 不允许在各文件另写十六进制。纯 JVM 层持有 ARGB Long，Compose 壳层再转 Color。
 * 切片-07 点名的绢帛暖黑 / 山顶米褐 / 谷底青灰不在 12 色内，由 [ReplaySurface] 用
 * token 压暗 / 混色派生，不直接写地貌色号。
 */
object ReplayTokens {

    val bgPage: Long get() = ComponentPalette.BG_PAGE
    val bgSurface: Long get() = ComponentPalette.BG_SURFACE
    val bgOverlay: Long get() = ComponentPalette.BG_OVERLAY
    val textPrimary: Long get() = ComponentPalette.TEXT_PRIMARY
    val textSecondary: Long get() = ComponentPalette.TEXT_SECONDARY
    val textTertiary: Long get() = ComponentPalette.TEXT_TERTIARY
    val textAccent: Long get() = ComponentPalette.TEXT_ACCENT
    val brandGold: Long get() = ComponentPalette.BRAND_GOLD
    val brandCopper: Long get() = ComponentPalette.BRAND_COPPER
    val colorLink: Long get() = ComponentPalette.COLOR_LINK
    val colorDivider: Long get() = ComponentPalette.COLOR_DIVIDER
    val colorGlow: Long get() = ComponentPalette.COLOR_GLOW
    val stoneBlue: Long get() = ComponentPalette.STONE_BLUE

    /**
     * 顶栏浮字半透明墨色：墨色 token 压到 0.7 alpha（切片-07 §顶栏 rgba(26,20,16,0.7)）。
     * 不另写色号，由 textPrimary 派生透明度。
     */
    val headerInk: Long get() = ColorMath.withAlpha(textPrimary, (0.7f * 255).toInt())

    /** 己方（红军 / 金朱）棋子主色。 */
    fun ownColor(): Long = brandGold

    /**
     * 敌方（蓝军 / 灰青）棋子主色：复用石青。
     * 登记说明：ComponentPalette 原始语义为 Toast 提示态；本模块在战役地图场景下把它
     * 兼作「敌方阵营」色（石青低饱和墨调，与己方暖金、盟友古铜区分），属同色不同场景复用，
     * 不新增 hex、不改调色板主 token 清单。
     */
    fun enemyColor(): Long = stoneBlue

    /** 盟友棋子主色：古铜。 */
    fun allyColor(): Long = brandCopper
}

/**
 * 切片-07「绢帛军图」地形明暗派生色。
 *
 * 契约只放行 12 色 token；地形三档明暗（绢帛底 / 山顶受光 / 谷底阴影）全部由 token
 * 混色派生：暗底压墨色，山顶向纸白提亮古铜，谷底把石青压进墨色。渲染层不得另立 hex。
 */
object ReplaySurface {

    /** 深沉绢帛底色（偏暖黑）：墨色压暗。 */
    val silkBase: Long get() = ColorMath.darken(ReplayTokens.textPrimary, 0.86f)

    /** 山顶受光面米褐：古铜向纸白提亮。 */
    val summitBeige: Long get() =
        ColorMath.mix(ReplayTokens.brandCopper, ReplayTokens.bgPage, 0.55f)

    /** 谷底阴影面青灰：石青压进墨色。 */
    val valleySlate: Long get() =
        ColorMath.mix(ReplayTokens.stoneBlue, ReplayTokens.textPrimary, 0.45f)
}

/**
 * ARGB 颜色派生工具（纯 JVM，与沙盘模块各自收口，避免跨模块耦合）。
 */
object ColorMath {

    fun alphaOf(argb: Long): Int = ((argb ushr 24) and 0xFF).toInt()
    fun redOf(argb: Long): Int = ((argb ushr 16) and 0xFF).toInt()
    fun greenOf(argb: Long): Int = ((argb ushr 8) and 0xFF).toInt()
    fun blueOf(argb: Long): Int = (argb and 0xFF).toInt()

    fun mix(a: Long, b: Long, t: Float): Long {
        val k = t.coerceIn(0f, 1f)
        val ar = redOf(a); val ag = greenOf(a); val ab = blueOf(a); val aa = alphaOf(a)
        val br = redOf(b); val bg = greenOf(b); val bb = blueOf(b); val ba = alphaOf(b)
        return pack(
            (aa + (ba - aa) * k).toInt(),
            (ar + (br - ar) * k).toInt(),
            (ag + (bg - ag) * k).toInt(),
            (ab + (bb - ab) * k).toInt(),
        )
    }

    fun darken(argb: Long, factor: Float): Long {
        val f = factor.coerceIn(0f, 1f)
        return pack(
            alphaOf(argb),
            (redOf(argb) * f).toInt(),
            (greenOf(argb) * f).toInt(),
            (blueOf(argb) * f).toInt(),
        )
    }

    fun withAlpha(argb: Long, alpha: Int): Long {
        val a = alpha.coerceIn(0, 255)
        return (a.toLong() shl 24) or (redOf(argb).toLong() shl 16) or
            (greenOf(argb).toLong() shl 8) or blueOf(argb).toLong()
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Long =
        (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}
