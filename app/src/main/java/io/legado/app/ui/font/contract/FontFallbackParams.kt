package io.legado.app.ui.font.contract

/**
 * S-14 字体回退 · 契约层参数单源（平台无关层，纯数值与纯函数，不引用任何 Android / Compose 类型，
 * 普通 JVM 单测可直接断言）。
 *
 * 〔锚点原文〕docs/立项-补条款-第二批.md
 *  - 条文 2.1 缺字回退：正文汉字 Noto Serif SC 子集 → 系统衬线 serif → 系统默认字体；
 *    缺字不得渲染 .notdef 方框 / 豆腐块，单字缺字不得拖垮整段排版。
 *  - 条文 2.2 字体包加载失败：超时阈值推定默认 800ms（取首屏 <400ms 口径两倍留余量）；
 *    按 font-display: swap 先用系统字体排版，单枚子集失败隔离，不允许 FOIT 白屏等待。
 *
 * 〔本单边界〕本单只做契约层（常量 + 纯函数 + 纯 JVM 单测），不写字体加载器 / 子集解码接线；
 * 回退链顺序、800ms 阈值为工程推定，待观感复核拍板。
 */
object FontFallbackParams {

    /** 加载失败超时 800ms（补条款 2.2：加载回调异常或超时即按 swap 落系统字体，不阻塞首屏）。 */
    const val LOAD_TIMEOUT_MS: Long = 800L

    /** 缺字回退链，缺字时按链序回退（补条款 2.1：正文汉字 → 系统衬线 serif → 系统默认字体）。 */
    val FALLBACK_CHAIN: List<String> = listOf(
        "Noto Serif CJK SC",
        "Source Han Serif SC",
        "system-serif"
    )

    /** 最低字形覆盖率门槛：低于该覆盖率（缺字率高于 1 - 0.995 = 0.5%）即判定缺字超标需回退。 */
    const val MIN_GLYPH_COVERAGE: Float = 0.995f

    /** 加载失败后的重试次数上限（单枚子集失败隔离，重试不拖垮其余子集）。 */
    const val RETRY_LIMIT: Int = 1
}

/**
 * 解析最终字体链：首选字体在链首，其后按 [FontFallbackParams.FALLBACK_CHAIN] 顺序
 * 筛出 [available] 中真实存在的项，末尾必带 "system-serif" 兜底。
 * [preferred] 为空或 [available] 为空时不许抛异常，直接回退到 FALLBACK_CHAIN 里的兜底项。
 */
fun resolveFontChain(preferred: String, available: List<String>): List<String> {
    val chain = LinkedHashSet<String>()
    if (preferred.isNotBlank()) {
        chain.add(preferred.trim())
    }
    if (available.isEmpty()) {
        // available 未知 / 为空时无法筛可用集，直接返回 FALLBACK_CHAIN 兜底项，末尾仍为 system-serif。
        chain.addAll(FontFallbackParams.FALLBACK_CHAIN)
    } else {
        FontFallbackParams.FALLBACK_CHAIN.forEach { candidate ->
            if (candidate in available) chain.add(candidate)
        }
    }
    if (chain.lastOrNull() != "system-serif") {
        chain.add("system-serif")
    }
    return chain.toList()
}

/**
 * 是否触发缺字回退：
 *  - [missingGlyphCount] <= 0：无缺字，不回退；
 *  - 字体名为空且缺字数不为 0：视为首字体未命中，回退；
 *  - 已知总字数的调用方应按 [FontFallbackParams.MIN_GLYPH_COVERAGE] 门槛判定
 *    （缺字率 > 1 - 0.995 = 0.5% 即回退）；
 *  - 本函数仅持有缺字数、无总字数，缺字比例无法在内部计算，按「缺字即回退」处理
 *    （补条款 2.1 底线：不得渲染 notdef 豆腐块，不得拖垮整段排版）。
 */
fun shouldFallback(loadedFontName: String, missingGlyphCount: Int): Boolean {
    if (missingGlyphCount <= 0) return false
    if (loadedFontName.isBlank()) return true
    // 比例门槛需总字数方可计算；此处缺字数无法算比例，按缺字即回退兜底。
    return true
}
