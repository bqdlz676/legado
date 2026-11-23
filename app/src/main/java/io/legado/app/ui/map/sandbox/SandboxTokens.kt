package io.legado.app.ui.map.sandbox

import io.legado.app.ui.components.ComponentPalette

/**
 * 沙盘模块颜色出口（切片-03 §12 色 token 铁律 + 切片-06 势力 / 城池配色）。
 *
 * 为什么只在这里做颜色映射：模块内任何 UI 叠加层（势力色块、光柱、标签、尾迹、信息卡）
 * 都必须走 12 色 token，不允许在各文件里另写十六进制；阵营配色收口到同一张表。
 * 纯 JVM 层持有 ARGB Long，Compose 壳层再一次性转成 Color，核心不引 Android 类型。
 */
object SandboxTokens {

    /** --bg-page 绢帛纸白。 */
    val bgPage: Long get() = ComponentPalette.BG_PAGE

    /** --bg-surface 卡片 / 绢帛面。 */
    val bgSurface: Long get() = ComponentPalette.BG_SURFACE

    /** --bg-overlay 茶玻遮罩。 */
    val bgOverlay: Long get() = ComponentPalette.BG_OVERLAY

    /** --text-primary 墨色（标签字 / 遗址方钉钉身）。 */
    val textPrimary: Long get() = ComponentPalette.TEXT_PRIMARY

    /** --text-secondary 副文茶褐。 */
    val textSecondary: Long get() = ComponentPalette.TEXT_SECONDARY

    /** --text-tertiary 灰褐（中立边界 / 未选中阶段点）。 */
    val textTertiary: Long get() = ComponentPalette.TEXT_TERTIARY

    /** --text-accent 朱砂（被占领城池光柱 / 遗址钉顶 / 关隘短线 / 冲突环）。 */
    val textAccent: Long get() = ComponentPalette.TEXT_ACCENT

    /** --brand-gold 暖金（己方势力 / 都城光柱 / 金粉 / 选中圆环）。 */
    val brandGold: Long get() = ComponentPalette.BRAND_GOLD

    /** --brand-copper 古铜（盟友势力 / 郡邑光柱 / 敌方边界暗铜）。 */
    val brandCopper: Long get() = ComponentPalette.BRAND_COPPER

    /** --color-link 链接朱砂。 */
    val colorLink: Long get() = ComponentPalette.COLOR_LINK

    /** --color-divider 细分隔线。 */
    val colorDivider: Long get() = ComponentPalette.COLOR_DIVIDER

    /** --color-glow 暖金辉光（尾迹 / 光晕）。 */
    val colorGlow: Long get() = ComponentPalette.COLOR_GLOW

    /** 石青（12 色外唯一增补）：敌方灰青势力色块取此色。 */
    val stoneBlue: Long get() = ComponentPalette.STONE_BLUE

    /**
     * 势力角色 → 填充色 token。
     * 切片-06 §一：己方暖金 / 敌方灰青 / 盟友铜色 / 中立无填充。
     */
    fun fillFor(role: FactionRole): Long = when (role) {
        FactionRole.OWN -> brandGold
        FactionRole.ENEMY -> stoneBlue
        FactionRole.ALLY -> brandCopper
        FactionRole.NEUTRAL -> textTertiary
    }

    /** 势力角色 → 边界色 token。切片-06：己方金虚线 / 敌方暗铜虚线 / 盟友铜色 / 中立灰褐。 */
    fun boundaryFor(role: FactionRole): Long = when (role) {
        FactionRole.OWN -> brandGold
        FactionRole.ENEMY -> brandCopper
        FactionRole.ALLY -> brandCopper
        FactionRole.NEUTRAL -> textTertiary
    }
}

/**
 * ARGB 颜色派生工具（纯 JVM）。
 *
 * 为什么不直接写地形色十六进制：契约只放行 12 色 token；切片-07 要求的绢帛暖黑 /
 * 山顶米褐 / 谷底青灰等地貌明暗，全部由 token 做压暗 / 提亮 / 混色派生，不另立色值。
 */
object ColorMath {

    /** 取出 ARGB 的 alpha 通道（0..255）。 */
    fun alphaOf(argb: Long): Int = ((argb ushr 24) and 0xFF).toInt()

    fun redOf(argb: Long): Int = ((argb ushr 16) and 0xFF).toInt()
    fun greenOf(argb: Long): Int = ((argb ushr 8) and 0xFF).toInt()
    fun blueOf(argb: Long): Int = (argb and 0xFF).toInt()

    /** 按通道混色：t=0 返回 a，t=1 返回 b；alpha 取两者线性插值。 */
    fun mix(a: Long, b: Long, t: Float): Long {
        val k = t.coerceIn(0f, 1f)
        val ar = redOf(a); val ag = greenOf(a); val ab = blueOf(a); val aa = alphaOf(a)
        val br = redOf(b); val bg = greenOf(b); val bb = blueOf(b); val ba = alphaOf(b)
        val r = (ar + (br - ar) * k).toInt()
        val g = (ag + (bg - ag) * k).toInt()
        val bl = (ab + (bb - ab) * k).toInt()
        val al = (aa + (ba - aa) * k).toInt()
        return pack(al, r, g, bl)
    }

    /** 压暗：factor<1 通道向 0 收；alpha 保持不变。 */
    fun darken(argb: Long, factor: Float): Long {
        val f = factor.coerceIn(0f, 1f)
        return pack(
            alphaOf(argb),
            (redOf(argb) * f).toInt(),
            (greenOf(argb) * f).toInt(),
            (blueOf(argb) * f).toInt(),
        )
    }

    /** 提亮：向纸白色靠拢，t 为靠拢比例。 */
    fun lighten(argb: Long, toward: Long, t: Float): Long = mix(argb, toward, t)

    /** 替换 alpha 通道（0..255），RGB 保持不变。 */
    fun withAlpha(argb: Long, alpha: Int): Long {
        val a = alpha.coerceIn(0, 255)
        return (a.toLong() shl 24) or (redOf(argb).toLong() shl 16) or
            (greenOf(argb).toLong() shl 8) or blueOf(argb).toLong()
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Long =
        (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

/**
 * 势力角色（切片-06 §一：己方 / 敌方 / 盟友 / 中立）。
 * 中立无填充只画边界；其余三态各自有填充透明度与边界样式，数值见 [SandboxConfig]。
 */
enum class FactionRole {
    OWN,
    ENEMY,
    ALLY,
    NEUTRAL,
}
