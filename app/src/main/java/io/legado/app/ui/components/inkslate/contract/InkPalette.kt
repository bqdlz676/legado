package io.legado.app.ui.components.inkslate.contract

import io.legado.app.ui.components.ComponentPalette

/**
 * S-12 画板 InkSlate · 六色预设 → 全站 token 单源映射（切片-12 §8.2 逐字落地）。
 *
 * 颜色预设原文：朱砂 · 墨灰 · 花青 · 藤黄 · 暖金 · 石青。
 *
 * 六色全部收口到既有单源 [ComponentPalette] token；本文件**不新增任何 hex 常数**。
 * token 表没有对应语义色的条目，[fallback] = true：先给就近 token 临时映射并写明理由，
 * 待回切片-03「颜色 Token 系统」补登记独立语义 token 后再切回。
 */
enum class InkPalette(
    val label: String,
    val token: Long,
    /** true = token 表无同语义色，当前为就近临时映射，需回 §颜色 Token 系统补登记 */
    val fallback: Boolean,
    val note: String,
) {

    /** 朱砂：--text-accent 朱砂强调，token 表直接对应 */
    VERMILION("朱砂", ComponentPalette.TEXT_ACCENT, false, "token 直接对应"),

    /**
     * 墨灰：token 表无「墨灰」语义色。
     * 就近 [ComponentPalette.TEXT_TERTIARY] 灰褐——它是 token 表唯一灰调中性色；
     * TEXT_PRIMARY 近黑属正文墨色，作灰笔会与墨色撞色，故不取。
     */
    INK_GRAY(
        "墨灰",
        ComponentPalette.TEXT_TERTIARY,
        true,
        "缺墨灰语义色，需回切片-03 颜色 Token 系统补登记；就近 TEXT_TERTIARY 灰褐（唯一灰调中性色）",
    ),

    /**
     * 花青：token 表无「花青」语义色。
     * 就近 [ComponentPalette.STONE_BLUE]：花青与石青同属传统矿物青系，
     * token 表仅这一支青系；临时复用会与石青预设同色，补登记后需拆开。
     */
    INDIGO(
        "花青",
        ComponentPalette.STONE_BLUE,
        true,
        "缺花青语义色，需回切片-03 颜色 Token 系统补登记；就近 STONE_BLUE 矿物青系（临时与石青同色）",
    ),

    /**
     * 藤黄：token 表无「藤黄」语义色。
     * 就近 [ComponentPalette.BRAND_COPPER] 古铜：同属黄褐矿物系；
     * BRAND_GOLD 已被暖金预设占用，不能二次借用。
     */
    GAMBOGE(
        "藤黄",
        ComponentPalette.BRAND_COPPER,
        true,
        "缺藤黄语义色，需回切片-03 颜色 Token 系统补登记；就近 BRAND_COPPER 黄褐矿物系（BRAND_GOLD 已归暖金）",
    ),

    /** 暖金：--brand-gold，token 表直接对应 */
    WARM_GOLD("暖金", ComponentPalette.BRAND_GOLD, false, "token 直接对应"),

    /** 石青：STONE_BLUE（Toast 提示态同款），token 表直接对应 */
    STONE_BLUE("石青", ComponentPalette.STONE_BLUE, false, "token 直接对应");

    companion object {
        /** §8.2 六色总数，供单测与原文对账 */
        const val SPEC_COUNT: Int = 6

        /** 底部色条全部六色，顺序固定为 §8.2 原文顺序 */
        val bar: List<InkPalette> get() = entries

        /** 需回 token 系统补登记独立语义色的条目（fallback = true） */
        val pendingRegistration: List<InkPalette> get() = entries.filter { it.fallback }
    }
}
