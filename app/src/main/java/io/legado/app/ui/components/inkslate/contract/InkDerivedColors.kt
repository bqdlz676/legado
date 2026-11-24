package io.legado.app.ui.components.inkslate.contract

import kotlin.math.roundToInt

/**
 * S-12 派生语义色集中声明（docs/落文-模块契约索引.md §1.3 逐字落地）。
 *
 * 〔为什么派生而不是再登记基础色〕
 * 12 色 token 冻结表没有墨灰 / 花青 / 藤黄三个独立语义色；§1.3 裁决：这三色以
 * [InkPalette] 既有就近 token 为基做**明度偏移**派生，集中在本对象声明，禁止在
 * 组件各处散落裸 hex。本文件不新增第 13 个基础色。
 *
 * 〔派生公式（ColorUtils 式纯算术，逐通道，alpha 高位字节原样保留）〕
 * - 降明度（暗化）：c' = round( c × (1 − ratio) )，通道向 0 收拢；
 * - 提明度（亮化）：c' = round( c + (255 − c) × ratio )，通道向 255 收拢；
 * - ratio 为明度偏移比例，R / G / B 三通道同一比例；不做色相偏移（花青保持青绿，
 *   忌偏蓝紫；藤黄暖黄不发土）。
 *
 * 〔与 §1.3 原文对账〕
 * - 墨灰 = text-tertiary 降明度 18%（约 #80796E）；
 * - 花青 = stone-blue 提明度 6%（约 #41766F）；
 * - 藤黄 = brand-copper 提明度 12%（约 #A57D1A）。
 * 「约」字说明：通道四舍五入后与原文近似值允许 1 档偏差；单测断言的是公式可复现，
 * 不是锁定某个十六进制近似值。
 */
object InkDerivedColors {

    /** 墨灰降明度比例：18%（§1.3） */
    const val INK_GRAY_DARKEN_RATIO: Float = 0.18f

    /** 花青提明度比例：6%（§1.3） */
    const val INDIGO_LIGHTEN_RATIO: Float = 0.06f

    /** 藤黄提明度比例：12%（§1.3） */
    const val GAMBOGE_LIGHTEN_RATIO: Float = 0.12f

    /** 墨灰：以 [InkPalette.INK_GRAY] 基础 token 降明度 18% 派生 */
    val inkGray: Long = darken(InkPalette.INK_GRAY.token, INK_GRAY_DARKEN_RATIO)

    /** 花青：以 [InkPalette.INDIGO] 基础 token 提明度 6% 派生 */
    val indigo: Long = lighten(InkPalette.INDIGO.token, INDIGO_LIGHTEN_RATIO)

    /** 藤黄：以 [InkPalette.GAMBOGE] 基础 token 提明度 12% 派生 */
    val gamboge: Long = lighten(InkPalette.GAMBOGE.token, GAMBOGE_LIGHTEN_RATIO)

    /**
     * 降明度纯算术：每通道向 0 收拢 ratio 比例；alpha 高位字节原样保留。
     * 公式：c' = round( c × (1 − ratio) )。
     */
    fun darken(token: Long, ratio: Float): Long {
        val factor = 1f - ratio
        return transformChannel(token) { channel ->
            (channel * factor).roundToInt().coerceIn(0, 255)
        }
    }

    /**
     * 提明度纯算术：每通道向 255 收拢 ratio 比例；alpha 高位字节原样保留。
     * 公式：c' = round( c + (255 − c) × ratio )。
     */
    fun lighten(token: Long, ratio: Float): Long {
        return transformChannel(token) { channel ->
            (channel + (255 - channel) * ratio).roundToInt().coerceIn(0, 255)
        }
    }

    private inline fun transformChannel(token: Long, crossinline f: (Int) -> Int): Long {
        val alpha = (token shr 24) and 0xFFL
        val r = f(((token shr 16) and 0xFFL).toInt())
        val g = f(((token shr 8) and 0xFFL).toInt())
        val b = f((token and 0xFFL).toInt())
        return (alpha shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /**
     * 按工具 / 色名取六色对应 ARGB（Int）。
     *
     * 匹配口径：先按 [InkPalette.label] 中文名匹配，再按枚举名忽略大小写匹配；
     * 墨灰 / 花青 / 藤黄取本对象派生色，朱砂 / 暖金 / 石青取基础 token。
     * 未知工具名回退默认墨灰（[inkGray]），不抛异常。
     */
    fun derivedInkColor(tool: String): Int {
        val preset = InkPalette.entries.firstOrNull {
            it.label == tool || it.name.equals(tool.trim(), ignoreCase = true)
        }
        val color = when (preset) {
            InkPalette.INK_GRAY -> inkGray
            InkPalette.INDIGO -> indigo
            InkPalette.GAMBOGE -> gamboge
            null -> inkGray
            else -> preset.token
        }
        return (color and 0xFFFFFFFFL).toInt()
    }
}
