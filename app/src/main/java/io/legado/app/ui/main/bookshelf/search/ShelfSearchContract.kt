package io.legado.app.ui.main.bookshelf.search

/**
 * S-13 书架下拉浮层 · 检索纯契约（纯 JVM，不 import 任何 Android / Compose 类型，
 * 普通 JVM 单测可直接实例化断言）。
 *
 * 〔锚点原文〕
 *  - `docs/落文-规格切片/切片-13-搜索页与字体.md:7`：「搜书名、作者、或正文中的任意一句」，
 *    「匹配文字片段用朱砂高亮」「纯文本列表，不配图」。
 *  - `docs/问题清单.md:142`（D25）：搜索做书架页下拉浮层，禁独立搜索页。
 *  - `docs/立项-冲突裁决.md` 第七节 B2：取书架页下拉浮层，独立 Search Activity 作废。
 *
 * 〔本单边界〕
 * 本文件只做本地书架条目的三路包含匹配、片段起止索引与排序；不碰网络、数据库、历史词、
 * 热门词与书源联网检索（归既有 D1~D7 链路）；浮层外观参数归 [SearchOverlayParams]。
 *
 * 〔归一化口径〕
 * 查询与字段只做首尾空白裁剪（trim，含全角空格 U+3000 等 Unicode 空白）与大小写归一
 * （lowercase）；不做拼音模糊、不做繁简转换、不压缩内部连续空白——规格未要求，多加即编造。
 */

/**
 * 书架检索轻量条目：只带检索所需的三个字段，不扩张成完整书模型。
 * 进度、封面、ID、章节文案等书架卡片字段归 S-10 的 `ShelfBookUi`，由接线单在入口处换算。
 */
data class ShelfItemLite(
    val title: String,
    val author: String,
    val bodySnippet: String,
)

/** 命中字段：书名 / 作者 / 正文片段。排序优先级即本枚举声明顺序（书名 > 作者 > 正文）。 */
enum class SearchField { TITLE, AUTHOR, BODY }

/**
 * 命中片段：[field] 命中路；[start] 含、[end] 不含，区间取自命中原字段字符串，
 * 调用方可用 `text.substring(start, end)` 切出待高亮的原文子串。
 */
data class HitSegment(
    val field: SearchField,
    val start: Int,
    val end: Int,
)

/**
 * 一条命中结果。
 *
 * @param item 命中的轻量条目本体。
 * @param rank 最高优先级命中路（书名命中即归书名组，即使同时命中作者 / 正文）。
 * @param segments 各命中路的命中片段；同一路只记首处命中，多处命中的展开由渲染层按需处理。
 */
data class SearchHit(
    val item: ShelfItemLite,
    val rank: SearchField,
    val segments: List<HitSegment>,
)

/**
 * 检索结果。[hits] 已按 书名命中 > 作者命中 > 正文命中 排序，同组按书名升序；
 * [hint] 永不为 null：有结果时为空串，无命中时给占位文案。
 */
data class SearchResult(
    val hits: List<SearchHit>,
    val hint: String,
)

/**
 * 无结果占位文案。规格未给无命中文案（切片-13:7 只写引导提示语），取默认占位；
 * 引导态提示语本身归 [SearchOverlayParams.PLACEHOLDER_TEXT]，不在此重复。
 */
const val NO_MATCH_HINT: String = "未找到相关书籍"

/**
 * 书架本地检索纯函数。
 *
 * 三路匹配：[ShelfItemLite.title] / [ShelfItemLite.author] / [ShelfItemLite.bodySnippet]
 * 任一包含归一化后的查询词即命中；命中片段带起止索引，供朱砂高亮切子串。
 *
 * 排序口径：书名命中 > 作者命中 > 正文命中；同组按书名升序（书名比较先归一大小写）。
 *
 * 兜底口径：
 *  - 查询为空或全空白 → 返回空结果，不抛异常；
 *  - 候选列表为空、任一字段为空 → 安全回落，不崩溃；
 *  - 非空查询但无命中 → 空列表 + [NO_MATCH_HINT]，[SearchResult.hint] 不为 null。
 */
fun searchBooks(query: String, items: List<ShelfItemLite>): SearchResult {
    val key = normalizeQuery(query)
    if (key.isEmpty()) return SearchResult(emptyList(), "")

    val hits = items.mapNotNull { item ->
        val titleSeg = locate(item.title, key, SearchField.TITLE)
        val authorSeg = locate(item.author, key, SearchField.AUTHOR)
        val bodySeg = locate(item.bodySnippet, key, SearchField.BODY)
        val segments = listOfNotNull(titleSeg, authorSeg, bodySeg)
        if (segments.isEmpty()) return@mapNotNull null

        val rank = when {
            titleSeg != null -> SearchField.TITLE
            authorSeg != null -> SearchField.AUTHOR
            else -> SearchField.BODY
        }
        SearchHit(item, rank, segments)
    }

    // 排序口径：先按命中字段优先级（书名 > 作者 > 正文），同组按书名升序。
    // "升序"明确取 Unicode 码点升序（不引拼音库，避免为排序引入第三方依赖与拼音多音字歧义）；
    // 因此"关键词乙"（乙 U+4E59）会排在"关键词甲"（甲 U+7532）之前——这是定义问题，不是 bug（K-17）。
    val ordered = hits.sortedWith(
        compareBy<SearchHit>(
            { rankWeight(it.rank) },
            { it.item.title.lowercase() },
        )
    )

    val hint = if (ordered.isEmpty()) NO_MATCH_HINT else ""
    return SearchResult(ordered, hint)
}

/** 查询归一化：首尾空白裁剪 + 大小写归一；其余差异不处理（见文件头口径注记）。 */
private fun normalizeQuery(raw: String): String = raw.trim().lowercase()

/** 命中路优先级权重：小者靠前；书名 0、作者 1、正文 2。 */
private fun rankWeight(field: SearchField): Int = when (field) {
    SearchField.TITLE -> 0
    SearchField.AUTHOR -> 1
    SearchField.BODY -> 2
}

/**
 * 在 [text] 中做大小写不敏感的包含匹配，返回首处命中的起止片段；未命中返回 null。
 * 索引基于原始 [text]，[end] 为不含端点，可直接 `substring(start, end)`。
 */
private fun locate(text: String, key: String, field: SearchField): HitSegment? {
    if (text.isEmpty() || key.isEmpty()) return null
    val idx = text.lowercase().indexOf(key)
    if (idx < 0) return null
    return HitSegment(field, idx, idx + key.length)
}

/*
 * 二阶交接注记（不进运行时逻辑，仅供后续接线单对照）：
 *  - 对 D25 导航 3 Tab：本纯函数只服务书架页下拉浮层；不起独立 Activity、不占导航图标位，
 *    与既有 `ui.main.bookshelf.ShelfSearch` 状态机同属书架浮层链路，接线时由状态机把输入词
 *    转成本函数入参；状态机的开合 / 清空逻辑不在本文件内重复实现。
 *  - 对 S-10 书架数据源：入参 [ShelfItemLite] 是三字段轻量投影，不读取 Room 实体；
 *    接入时由书架数据源把 `ShelfBookUi`（或原始 Book 实体）换算成 Lite 条目喂入，
 *    本函数不依赖书架卡片的进度 / 封面 / 章节文案等字段。
 *  - 对旧 `ui.main.search` 包：该包围绕已作废的独立搜索 Activity 构建，本单不改动、不引用；
 *    浮层最终是复用其高亮切分还是替换为本文件片段索引，由后续接线 / 收口单裁决。
 */
