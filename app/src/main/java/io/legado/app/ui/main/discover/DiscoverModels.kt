package io.legado.app.ui.main.discover

import io.legado.app.ui.card.BattleCardData
import io.legado.app.ui.card.ClassicBook
import io.legado.app.ui.card.HeroKind
import io.legado.app.ui.card.TopicCardData

/**
 * 落文 · 发现页模型映射层（B6-02，承接旧 `ui/main/explore/InkExploreModels.kt` 迁移）
 *
 * 职责边界：本文件只做「页面层入参 → 卡片族数据类」的字段搬运与兜底过滤，
 * 不碰 DAO / assets / 网络，不自建第二份数据源；卡片族数据类
 * （[BattleCardData] / [ClassicBook] / [TopicCardData]）只对齐不改签名。
 *
 * 判据锚 R3（逐字可回源）：
 * - docs/任务书-B6-发现页.md:103：「发现页三种卡片变体不得增减：战役横卡 / 典籍竖卡 / 专题通栏卡，另加马恩党史卡」
 * - docs/落文-规格切片/切片-03-设计token与四层框架.md:118：「三种卡片变体：战役横卡（暖金边+进度条）/典籍竖卡（书名+朝代）/专题通栏卡（标题+典故数）」
 *
 * 供源口径锚：
 * - docs/供源方案-B6发现页.md:175：「当前契约只认 4 字段，映射层不预判新列，避免私有字段约定与表结构批次对不上。」
 * - docs/拆分-B6发现页小单.md:83：卡片族数据类（BattleCardData:54 / ClassicBook:44 / TopicCardData:52）只对齐不改；映射层落在 discover 包、不进卡片族包。
 *
 * 同名消歧：卡片族 [ClassicBook] 与旧 explore 包同名异义模型不是同一类型；
 * 本文件只 import 卡片族包，不跨包引用旧 explore 模型。
 */

// ─────────────────────────── 页面层入参模型（discover 包内，不进卡片族包） ───────────────────────────

/**
 * 战役横卡入参（页面层编排侧从目录源适配后传入）。
 *
 * @param title 战役名；必填，空白视为坏条目过滤。
 * @param era 时期 / 对阵，映射副标题；缺省留空，卡片按空兜底。
 * @param context 一句题注，映射描述；缺省留空。
 * @param phases 阶段标签列表；非空时总阶段数取其长度（R16 三态第一优先）。
 * @param rounds 回合数兜底代理；[phases] 为空且大于 0 时取它作分母；双空 totalStage=0。
 */
data class BattleSource(
    val title: String,
    val era: String? = null,
    val context: String? = null,
    val phases: List<String> = emptyList(),
    val rounds: Int = 0,
)

/**
 * 典籍竖卡入参。
 *
 * @param title 书名；必填，空白视为坏条目过滤。
 * @param dynasty 朝代；Book 实体无朝代列（供源方案 §3.2），当前恒空，对应竖排列不渲染。
 * @param wordCount 字数注记；实体字段可空，缺省归一为空串。
 */
data class ClassicSource(
    val title: String,
    val dynasty: String? = null,
    val wordCount: String? = null,
)

/**
 * 专题通栏卡入参。
 *
 * @param title 专题名；必填，空白视为坏条目过滤。
 * @param preview 代表典故预览短句；缺省留空。
 * @param allusionCount 典故数量；负值兜底为 0，卡片金点条按 0 留空。
 */
data class TopicSource(
    val title: String,
    val preview: String? = null,
    val allusionCount: Int = 0,
)

/**
 * 马恩党史卡入参。卡片族无独立数据类，[HeroCardDraft] 为页面层传参草稿，
 * 字段与 [TopicHeroCard][io.legado.app.ui.card.TopicHeroCard] 形参一一对应。
 *
 * @param kind 马克思卡（Marx）或党史卡（Cpc）。
 * @param title 竖排主标题；必填，空白视为坏条目过滤。
 * @param subtitle 副标题；缺省留空。
 * @param yearStart 时间线左端年份；缺源留空，不画示例年份假卡。
 * @param yearEnd 时间线右端年份；缺源留空。
 */
data class HeroSource(
    val kind: HeroKind,
    val title: String,
    val subtitle: String? = null,
    val yearStart: String? = null,
    val yearEnd: String? = null,
)

/**
 * 生字本入口条目入参。只认既有契约四字段：token / bookName / chapterTitle / timestamp；
 * 不预判 mastered / review_count 等将来新列（供源方案:175）。新列落地后由数据批次
 * 驱动本结构追加字段，映射层不提前私有约定。
 */
data class NotebookEntrySource(
    val token: String,
    val bookName: String,
    val chapterTitle: String,
    val timestamp: Long,
)

/** 马恩党史卡页面层传参草稿；B6-05 分区壳逐字段交 [TopicHeroCard][io.legado.app.ui.card.TopicHeroCard]。 */
data class HeroCardDraft(
    val kind: HeroKind,
    val title: String,
    val subtitle: String,
    val yearStart: String,
    val yearEnd: String,
)

/**
 * 批量映射结果。
 *
 * @param items 成功转换的卡片数据；顺序与入参一致。
 * @param dropped 被过滤的坏条目计数；坏条目不抛异常、不拖垮整区，计数上报给编排侧。
 */
data class MapOutcome<R>(
    val items: List<R>,
    val dropped: Int,
) {
    /** 成功列表是否为空；空态分支由编排侧 / 空态卡承接，映射层不空白不崩溃。 */
    val isEmpty: Boolean get() = items.isEmpty()
}

// ─────────────────────────── 四类卡片转换 ───────────────────────────

/** 战役入参 → 战役横卡数据；watchedStage 缺源恒 0，分母三态：phases 长度 → rounds → 0。 */
fun BattleSource.toBattleCard(): BattleCardData {
    val safeTitle = requireNonBlank(title)
    val totalStage = when {
        phases.isNotEmpty() -> phases.size
        rounds > 0 -> rounds
        else -> 0
    }
    return BattleCardData(
        title = safeTitle,
        subtitle = era.orEmpty().trim(),
        description = context.orEmpty().trim(),
        watchedStage = 0,
        totalStage = totalStage,
    )
}

/** 典籍入参 → 典籍竖卡数据；朝代 / 字数缺源归一为空串，空字段由卡片竖排列不渲染分支承接。 */
fun ClassicSource.toClassicBook(): ClassicBook = ClassicBook(
    title = requireNonBlank(title),
    dynasty = dynasty.orEmpty().trim(),
    wordCount = wordCount.orEmpty().trim(),
)

/** 专题入参 → 专题横条卡数据；典故数负值兜底为 0。 */
fun TopicSource.toTopicCard(): TopicCardData = TopicCardData(
    title = requireNonBlank(title),
    preview = preview.orEmpty().trim(),
    allusionCount = allusionCount.coerceAtLeast(0),
)

/** 马恩党史入参 → 页面层传参草稿；年份缺源留空，不补示例年份。 */
fun HeroSource.toHeroDraft(): HeroCardDraft = HeroCardDraft(
    kind = kind,
    title = requireNonBlank(title),
    subtitle = subtitle.orEmpty().trim(),
    yearStart = yearStart.orEmpty().trim(),
    yearEnd = yearEnd.orEmpty().trim(),
)

// ─────────────────────────── 批量安全映射（坏条目过滤并计数） ───────────────────────────

/**
 * 逐条安全转换：单条 [transform] 抛异常即跳过该条并计入 [MapOutcome.dropped]，
 * 不向上抛出、不中断整区；入参为空安全回落空结果。纯 JVM，可直接单测。
 */
fun <S, R> List<S>.mapSafely(transform: (S) -> R): MapOutcome<R> {
    val items = ArrayList<R>(size)
    var dropped = 0
    for (raw in this) {
        runCatching { transform(raw) }
            .onSuccess(items::add)
            .onFailure { dropped++ }
    }
    return MapOutcome(items, dropped)
}

/** 战役分区批量映射。 */
fun List<BattleSource>.toBattleCards(): MapOutcome<BattleCardData> =
    mapSafely { it.toBattleCard() }

/** 典籍分区批量映射。 */
fun List<ClassicSource>.toClassicBooks(): MapOutcome<ClassicBook> =
    mapSafely { it.toClassicBook() }

/** 专题分区批量映射。 */
fun List<TopicSource>.toTopicCards(): MapOutcome<TopicCardData> =
    mapSafely { it.toTopicCard() }

/** 马恩党史分区批量映射。 */
fun List<HeroSource>.toHeroDrafts(): MapOutcome<HeroCardDraft> =
    mapSafely { it.toHeroDraft() }

/**
 * 生字本入口卡计数模型。N 取条目总数（R20）；掌握态 mastered 缺源，
 * 本结构不挂掌握标记位，不在页面层伪造 mastered=1。
 */
data class NotebookEntryCard(val count: Int)

/** 生字本条目 → 入口卡计数；只计数，不做掌握态判定。 */
fun List<NotebookEntrySource>.toNotebookEntryCard(): NotebookEntryCard =
    NotebookEntryCard(count = size)

// ─────────────────────────── 内部兜底 ───────────────────────────

/** 标题必填校验：空白视为坏条目，抛 [IllegalArgumentException] 由批量映射过滤计数。 */
private fun requireNonBlank(raw: String): String {
    val trimmed = raw.trim()
    require(trimmed.isNotBlank()) { "卡片标题缺失，坏条目跳过" }
    return trimmed
}
