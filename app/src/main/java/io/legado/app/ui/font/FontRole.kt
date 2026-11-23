package io.legado.app.ui.font

/** 编译期常量兜底：enum 构造先于 companion object 初始化，故把被 enum 引用的常量放顶层。 */
private const val BODY_SUBSET_BUDGET = 200L * 1024
private const val NUMBER_SUBSET_BUDGET = 20L * 1024
private const val LABEL_SANS_SIZE = 10

/**
 * U9 字体系统（规格切片-14「字体系统」逐字落地）。
 *
 * 规格原文分工：
 *  - 正文（宋体）：Noto Serif SC，woff2 subset，优先预载，font-display: swap；
 *  - 标题：思源宋体 SemiBold，首屏阻塞，swap，不允许 FOIT；
 *  - 注音小字：Noto Sans SC Light 10sp，延迟加载；
 *  - 数字/年份/年代：Inter（Tabular Nums），numbers only 极小子集；
 *  - 等宽（典籍编号、CBDB ID）：JetBrains Mono，按需加载；
 *  - CJK subset：unicode-range 分片，正文常用 2500 字首屏子集 ≤ 200KB。
 *
 * 本模块只收口资产契约、加载编排、缓存与兜底；不硬编码平台字体名，不在阅读层下载字体。
 * 颜色一律走语义 token，本层不出现颜色字面量；不引入 Material 组件。
 */

/** 加载策略（对应切片-14 各行「加载策略」列）。 */
enum class FontLoadStrategy {
    /** 正文：优先预载；子集未就绪期间兜底字体可见（swap，不出现不可见文本期）。 */
    PRELOAD_SWAP,

    /** 标题：首屏阻塞加载；仍以兜底字体 swap 过渡，不允许 FOIT。 */
    BLOCKING_TITLE,

    /** 注音小字：延迟加载，首屏不阻塞。 */
    LAZY,

    /** 数字 / 等宽：首次取用按需加载。 */
    ON_DEMAND,
}

/** 兜底字体族（内置子集缺失 / 加载失败 / 超体积时回落，不崩、不出空白态）。 */
enum class FontFallbackKind {
    /** 系统衬线（荣耀 WIN / HarmonyOS 的 serif 即 Noto Serif CJK 宋体类）。 */
    SERIF,

    /** 系统衬线加粗；系统缺粗重时由 Paint fakeBold 合成。 */
    SERIF_BOLD,

    /** 系统无衬线。 */
    SANS,

    /** 系统无衬线 + 等宽数字特性；子集就位前不强制 Inter。 */
    SANS_TABULAR,

    /** 系统等宽。 */
    MONO,
}

/**
 * 字体角色契约。[candidateAssets] 按优先级排列，命中目录内第一个存在的文件即加载。
 *
 * @param maxSubsetBytes 子集体积红线；超出即拒绝加载并走兜底（null = 本角色不设体积红线）。
 * @param sizeSp 规格给定的字号（仅注音小字 10sp）；其余角色字号由排版层按 token 决定。
 */
enum class FontRole(
    val strategy: FontLoadStrategy,
    val fallback: FontFallbackKind,
    val candidateAssets: List<String>,
    val maxSubsetBytes: Long?,
    val sizeSp: Int?,
    val specNote: String,
) {
    BODY_SERIF(
        strategy = FontLoadStrategy.PRELOAD_SWAP,
        fallback = FontFallbackKind.SERIF,
        candidateAssets = listOf(
            "NotoSerifSC-Regular.otf",
            "NotoSerifSC-Regular.ttf",
            "SourceHanSerifSC-Regular.otf",
            "SourceHanSerifSC-Regular.ttf",
        ),
        maxSubsetBytes = BODY_SUBSET_BUDGET,
        sizeSp = null,
        specNote = "正文宋体 Noto Serif SC；首屏常用子集 ≤ 200KB；font-display: swap",
    ),

    TITLE_SERIF(
        strategy = FontLoadStrategy.BLOCKING_TITLE,
        fallback = FontFallbackKind.SERIF_BOLD,
        candidateAssets = listOf(
            "NotoSerifSC-SemiBold.otf",
            "NotoSerifSC-SemiBold.ttf",
            "SourceHanSerifSC-SemiBold.otf",
            "SourceHanSerifSC-SemiBold.ttf",
        ),
        maxSubsetBytes = BODY_SUBSET_BUDGET,
        sizeSp = null,
        specNote = "标题思源宋体 SemiBold；首屏阻塞；swap 过渡，不允许 FOIT",
    ),

    LABEL_SANS(
        strategy = FontLoadStrategy.LAZY,
        fallback = FontFallbackKind.SANS,
        candidateAssets = listOf(
            "NotoSansSC-Light.otf",
            "NotoSansSC-Light.ttf",
        ),
        maxSubsetBytes = null,
        sizeSp = LABEL_SANS_SIZE,
        specNote = "注音小字 Noto Sans SC Light 10sp；延迟加载",
    ),

    NUMBER_TABULAR(
        strategy = FontLoadStrategy.ON_DEMAND,
        fallback = FontFallbackKind.SANS_TABULAR,
        candidateAssets = listOf(
            "Inter-TabularNumbers.otf",
            "Inter-TabularNumbers.ttf",
            "Inter-Nums.otf",
            "Inter-Nums.ttf",
        ),
        maxSubsetBytes = NUMBER_SUBSET_BUDGET,
        sizeSp = null,
        specNote = "数字/年份 Inter Tabular Nums；numbers only 极小子集",
    ),

    MONO(
        strategy = FontLoadStrategy.ON_DEMAND,
        fallback = FontFallbackKind.MONO,
        candidateAssets = listOf(
            "JetBrainsMono-Regular.otf",
            "JetBrainsMono-Regular.ttf",
        ),
        maxSubsetBytes = null,
        sizeSp = null,
        specNote = "等宽 JetBrains Mono；典籍编号 / CBDB ID；按需加载",
    );

    companion object {
        /** 正文首屏子集体积红线：≤ 200KB（切片-14 / 切片-08 性能红线）。 */
        const val BODY_SUBSET_BUDGET_BYTES: Long = 200L * 1024

        /** 正文首屏子集常用字数：2500 字（切片-14 / 切片-08）。 */
        const val BODY_SUBSET_COMMON_CHARS: Int = 2500

        /** 数字极小子集体积红线：20KB。 */
        const val NUMBER_SUBSET_BUDGET_BYTES: Long = 20L * 1024

        /** 注音小字字号（sp，切片-09 §3.1：注音/批注小字 10sp）。 */
        const val LABEL_SANS_SIZE_SP: Int = 10

        /** 内置资产目录（assets/ 下相对路径）。 */
        const val ASSET_DIR: String = "fonts"
    }
}
