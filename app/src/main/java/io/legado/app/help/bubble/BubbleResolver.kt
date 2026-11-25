package io.legado.app.help.bubble

import io.legado.app.help.allusion.AllusionAnchorProvider
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.dict.ChineseDict
import android.content.Context
import java.util.LinkedHashMap

/**
 * 气泡系统唯一解析入口（规格切片-02 §2「识别优先级」）。
 *
 * 优先级链（顺序不可调；同位置多命中取更高优先级；同优先级取更长词）：
 *  1. 纪年 / 干支 / 朔望 —— [date]，正则先扫，最快；
 *  2. 典故 —— [allusion]，2~4 字，已下划线优先；
 *  3. 人名 —— [person]，档案 / 别名印证，同名按锚点消歧；
 *  4. 地名 —— [place]，按 [ContextAnchor] 年代选层；
 *  5. 多字词 —— [word]，2~5 字，长词优先；
 *  6. 单字 —— [char]，字义 + 说文 + 用例；
 *  7. Missing —— [missing] 兜底，允许联网检索 / 加入生字本。
 *
 * 性能（规格 §5 / 切片-08）：冷启动首触气泡 ≤ 300ms（含加载），热路径 ≤ 80ms。
 * 命中结果按「段落 + 位置 + 锚点年」进 LRU 缓存；Provider 内部只走索引 / 二分查表，
 * 不整表线性扫。任何一路异常都被兜住，最终至少返回一张 Missing 气泡，不留空白态、不崩溃。
 *
 * @param date     纪年 / 干支 / 朔望 Provider
 * @param allusion 典故 Provider
 * @param person   人名 Provider
 * @param place    地名 Provider
 * @param word     多字词 Provider
 * @param char     单字 Provider
 * @param missing  兜底 Provider
 * @param cacheSize 热路径缓存条数（够一章反复点按）
 */
class BubbleResolverImpl(
    private val date: BubbleProvider,
    private val allusion: BubbleProvider,
    private val person: BubbleProvider,
    private val place: BubbleProvider,
    private val word: BubbleProvider,
    private val char: BubbleProvider,
    private val missing: BubbleProvider,
    private val cacheSize: Int = 64,
) {

    private val cache = object : LinkedHashMap<String, BubbleContent>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BubbleContent>?): Boolean =
            size > cacheSize
    }

    /**
     * 点击段落 [paragraph] 的第 [pos] 个字，按优先级产出气泡。
     * [anchor] 为当前书 / 章 / 候选年代锚点，供同名消歧与按年代选层。
     * 冻结签名返回可空；实现侧永不返回 null——全空时由 Missing 兜出一张气泡。
     */
    fun resolve(paragraph: String, pos: Int, anchor: ContextAnchor = ContextAnchor): BubbleContent? {
        if (paragraph.isEmpty()) return missing("", anchor)

        val safePos = pos.takeIf { it in paragraph.indices } ?: paragraph.indices.lastOrNull() ?: -1
        val tokenFallback = paragraph.getOrNull(safePos)?.toString().orEmpty()

        val key = cacheKey(paragraph, safePos, anchor)
        synchronized(cache) { cache[key]?.let { return it } }

        val result = runCatching { dispatch(paragraph, safePos, anchor) }
            .getOrNull()
            ?: missing(tokenFallback, anchor)

        synchronized(cache) { cache[key] = result }
        return result
    }

    /** 非段落场景（搜索栏 / 生字本回看 / 候选切换）按整词走 Provider 查询链；全空返回 null。 */
    fun queryToken(token: String, anchor: ContextAnchor = ContextAnchor): BubbleContent? {
        val clean = BubbleSupport.cleanToken(token)
        if (clean.isEmpty()) return null
        return safe { date.query(clean, anchor) }
            ?: safe { allusion.query(clean, anchor) }
            ?: safe { person.query(clean, anchor) }
            ?: safe { place.query(clean, anchor) }
            ?: safe { word.query(clean, anchor) }
            ?: safe { char.query(clean, anchor) }
    }

    /** 清空热路径缓存（翻章 / 换书 / 锚点变更时调用）。 */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    // ---------- 内部 ----------

    private fun dispatch(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos < 0) return null
        safe { date.hitAt(paragraph, pos, anchor) }?.let { return it }
        safe { allusion.matchAllusion(paragraph, pos, anchor) }?.let { return it }
        safe { person.hitAt(paragraph, pos, anchor) }?.let { return it }
        safe { place.hitAt(paragraph, pos, anchor) }?.let { return it }
        safe { word.matchWord(paragraph, pos, anchor) }?.let { return it }
        safe { char.hitAt(paragraph, pos, anchor) }?.let { return it }
        return null
    }

    private fun missing(token: String, anchor: ContextAnchor): BubbleContent =
        missing.query(token, anchor) ?: BubbleContent(
            kind = BubbleKind.MISSING,
            token = token.ifBlank { "此处" },
            phonetic = null,
            headline = token.ifBlank { "此处" },
            body = "本地字词典未收录，可联网检索，或先收入生字本待日后复查。",
            detail = "未命中任何字典 / 人物 / 地名 / 纪年条目；此处不强行释义。",
            actions = emptyList(),
            candidates = emptyList(),
            source = "本地词库未命中",
            allowWebSearch = true,
            allowNotebook = true,
        )

    private inline fun safe(block: () -> BubbleContent?): BubbleContent? =
        runCatching(block).getOrNull()

    private fun cacheKey(paragraph: String, pos: Int, anchor: ContextAnchor): String =
        "${paragraph.hashCode()}:${paragraph.length}:$pos:${anchor.getYear()}:${anchor.state}"
}

/**
 * 默认装配：真实骨架 Provider。后续数据表接入时只替换各 Provider 内部实现，
 * 优先级编排与本入口不动。典故槽与多字词槽共用 [WordProvider]，由其内部
 * [BubbleProvider.matchAllusion] / [BubbleProvider.matchWord] 分流出典故事与普通词。
 */
val DefaultBubbleResolver = BubbleResolverImpl(
    date = DateProvider,
    allusion = WordProvider,
    person = PersonProvider,
    place = PlaceProvider,
    word = WordProvider,
    char = CharProvider,
    missing = MissingProvider,
)

/** 全局唯一入口对象，委托给默认装配。 */
object BubbleResolver {

    /** 预热：注入应用 Context 并触达各字典惰性加载（App 启动协程里调用，失败不阻启动）。 */
    fun preload(context: Context) {
        BubbleEnv.preload(context)
        runCatching { ChineseDict.preload() }
        runCatching { Chronology.ensureLoaded(context) }
        // 预热后显式重绑人物消歧入口：防止遗留扁平层后注册把共享总线的 personProvider 覆盖掉。
        runCatching { PersonProvider.bindAnchorProvider() }
        // 预热后显式重绑典故消歧入口：与人物路同口径，防对象初始化时序晚于首查导致总线空缺。
        runCatching { AllusionAnchorProvider.bindAnchorProvider() }
        // D2 / D3 / D4 为纯内存种子，首次查询时惰性建索引；此处仅占位便于后续资产预载扩展。
    }

    fun resolve(paragraph: String, pos: Int, anchor: ContextAnchor = ContextAnchor): BubbleContent? =
        DefaultBubbleResolver.resolve(paragraph, pos, anchor)

    fun queryToken(token: String, anchor: ContextAnchor = ContextAnchor): BubbleContent? =
        DefaultBubbleResolver.queryToken(token, anchor)

    fun clearCache() = DefaultBubbleResolver.clearCache()
}
