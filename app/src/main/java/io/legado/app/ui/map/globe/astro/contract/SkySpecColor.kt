package io.legado.app.ui.map.globe.astro.contract

import kotlin.math.roundToInt

/**
 * 天文内容色（数据色，非 UI 控件配色）（§六.2、§六.12-15）。
 *
 * 〔为什么独立于 12 色 token〕星色来自 B-V 物理量 / 星等映射，与地貌纹理色同构——
 * 是渲染数据，不是界面配色；**禁用于任何 UI 控件**（按钮 / Sheet / 标签底色一律走 token）。
 *
 * 〔硬禁（§六.16）〕禁纯黑 `#000000`、禁纯白 `#FFFFFF`、禁 Material 默认色、禁第 14 基础色。
 * 本对象是 `astro/` 包内唯一允许出现裸色值的位置（§六.17）；其余文件命中裸 hex 即 FAIL。
 *
 * 〔无 B-V 口径（§六.14）〕查不到色指数的星按星等映射“纸白→暖白”，不猜色温；
 * 颜色 token（`brandGold` / `textTertiary` 等）由接线单在 `GlobeTokens` 侧解析，
 * 本对象只登记 RGB 数据色与 alpha 比率，不持有 token 实例、不依赖 Compose。
 */
object SkySpecColor {

    // ───────────────────── B-V 四锚（RGB，不透明）：早型 → 中间 → 晚型 → 极晚 ─────────────────────

    /** 早型星（低饱和月白 `#D8E4F0`）；去蓝化后只作全天约 5% 点缀（§六.13）。 */
    const val STAR_ANCHOR_EARLY_RGB: Int = 0xD8E4F0

    /** 中间型（纸白 `#FFFDF6`）。 */
    const val STAR_ANCHOR_MID_RGB: Int = 0xFFFDF6

    /** 晚型星（暖白 `#FFEED8`）。 */
    const val STAR_ANCHOR_LATE_RGB: Int = 0xFFEED8

    /** 极晚型（橙暖 `#FFB37A`）。 */
    const val STAR_ANCHOR_VERY_LATE_RGB: Int = 0xFFB37A

    /** 流星头部色（纸白 `#FFFDF6`，§六.15）。 */
    const val METEOR_HEAD_RGB: Int = 0xFFFDF6

    // ───────────────────── 银河 / 尘粒 alpha 比率（§六.15）─────────────────────

    /** 银河暖白核心 alpha 0.10（叠 `#FFFDF6` 纸白）。 */
    const val GALAXY_CORE_ALPHA_RATIO: Float = 0.10f

    /** 银河核心叠暖金 alpha 0.06（`brandGold`，token 由接线单解析）。 */
    const val GALAXY_CORE_GOLD_ALPHA_RATIO: Float = 0.06f

    /** 银河外围暖灰 alpha 0.04；冷蓝外围已取消，改暖灰 `textTertiary`。 */
    const val GALAXY_HALO_ALPHA_RATIO: Float = 0.04f

    /** 尘粒 alpha 0.15（`textTertiary`）。 */
    const val DUST_ALPHA_RATIO: Float = 0.15f

    // ───────────────────── B-V 分段锚点（典型色指数，天文学家口径）─────────────────────

    /** 早型分段起点 B-V ≈ −0.30。 */
    private const val BV_EARLY: Double = -0.30

    /** 中间型分段点 B-V ≈ 0.60。 */
    private const val BV_MID: Double = 0.60

    /** 晚型分段点 B-V ≈ 1.20。 */
    private const val BV_LATE: Double = 1.20

    /** 极晚型分段点 B-V ≈ 1.70。 */
    private const val BV_VERY_LATE: Double = 1.70

    /**
     * B-V 连续插值四锚（§六.12）。越界钳到两端；非有限值回落纸白锚。
     * 无 B-V 时禁止调用本函数——走 [starColorFromMag]，不猜色温（§六.14）。
     */
    fun starColorFromBV(bv: Double): Int {
        if (!bv.isFinite()) return STAR_ANCHOR_MID_RGB
        return when {
            bv <= BV_EARLY -> STAR_ANCHOR_EARLY_RGB
            bv >= BV_VERY_LATE -> STAR_ANCHOR_VERY_LATE_RGB
            bv < BV_MID -> lerpRgb(
                STAR_ANCHOR_EARLY_RGB,
                STAR_ANCHOR_MID_RGB,
                ((bv - BV_EARLY) / (BV_MID - BV_EARLY)).toFloat(),
            )
            bv < BV_LATE -> lerpRgb(
                STAR_ANCHOR_MID_RGB,
                STAR_ANCHOR_LATE_RGB,
                ((bv - BV_MID) / (BV_LATE - BV_MID)).toFloat(),
            )
            else -> lerpRgb(
                STAR_ANCHOR_LATE_RGB,
                STAR_ANCHOR_VERY_LATE_RGB,
                ((bv - BV_LATE) / (BV_VERY_LATE - BV_LATE)).toFloat(),
            )
        }
    }

    /**
     * 无 B-V 星按星等映射：亮星纸白、暗星暖白，不猜色温（§六.14）。
     * 亮端取 [STAR_ANCHOR_MID_RGB]，暗端取 [STAR_ANCHOR_LATE_RGB]；
     * 暗于 6.5 等不再转橙，颜色让位于 alpha 补偿。非有限输入回落纸白锚。
     */
    fun starColorFromMag(mag: Float): Int {
        if (!mag.isFinite()) return STAR_ANCHOR_MID_RGB
        val t = (
            mag.coerceIn(SkyParams.MAG_ANCHOR_0, SkyParams.MAG_ANCHOR_6_5) -
                SkyParams.MAG_ANCHOR_0
            ) / (SkyParams.MAG_ANCHOR_6_5 - SkyParams.MAG_ANCHOR_0)
        return lerpRgb(STAR_ANCHOR_MID_RGB, STAR_ANCHOR_LATE_RGB, t)
    }

    /** RGB 三通道分段线性插值；t 钳 [0,1]，结果不越出两端锚点色域。 */
    private fun lerpRgb(a: Int, b: Int, tIn: Float): Int {
        val t = tIn.coerceIn(0f, 1f)
        val ar = a shr 16 and 0xFF
        val ag = a shr 8 and 0xFF
        val ab = a and 0xFF
        val br = b shr 16 and 0xFF
        val bg = b shr 8 and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * t).roundToInt()
        val g = (ag + (bg - ag) * t).roundToInt()
        val bl = (ab + (bb - ab) * t).roundToInt()
        return (r shl 16) or (g shl 8) or bl
    }
}
