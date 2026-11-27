package io.legado.app.ui.main.discover

import io.legado.app.ui.card.BattleCardData
import io.legado.app.ui.card.ClassicBook
import io.legado.app.ui.card.TopicCardData

/**
 * B6-03 · DiscoverViewModel 状态与数据源编排（承接旧 `ExploreViewModel` 能力边界）。
 *
 * 职责（docs/拆分-B6发现页小单.md:89-94）：
 * - 五区状态编排：战役 / 典籍 / 专题 / 马恩 / 生字本；
 * - 各区空列表 / 坏条目 / 离线分支状态位；
 * - 数据源只经既有只读入口（端口注入），本类不碰 DAO / assets / Context。
 *
 * 边界口径（docs/任务书-B6-发现页.md §3.4 :78-82；docs/供源方案-B6发现页.md §3.1~§3.5）：
 * - 战役源：内置 `map/campaigns.js` 目录元数据；进度位缺源按 0/0 兜底，页面层不自建第二份进度状态。
 * - 典籍源：书架 `books` 表；朝代 / 精选目录缺源，空字段不渲染，不预置公版书目清单。
 * - 专题源：典故词表；分组 catalog 缺源时整区空态，不临时分组猜测。
 * - 马恩源：仓内缺源，分区不渲染，不画示例年份假卡。
 * - 生字本源：既有 Entry 计数；mastered 掌握标记缺源，本类不挂掌握位、不伪造 mastered=1。
 *
 * 本类为纯 Kotlin 编排层，不引 Android / Compose / 路由库；生命周期持有薄壳由挂载单
 * （B6-12 EXPLORE 槽位接线）按需委托，本单不实现。
 *
 * 旧 `ui/main/explore/ExploreViewModel.kt` 的书源置顶 / 删除编排归书源域，不进本 VM。
 */

/** 分区状态位：骨架加载中 / 有内容 / 空列表 / 离线 / 出错。 */
enum class RegionStatus { LOADING, CONTENT, EMPTY, OFFLINE, ERROR }

/**
 * 分区不可变快照。
 *
 * @param status 分区当前状态位
 * @param items 已通过映射与坏条目过滤的卡片数据；顺序与源列表一致
 * @param dropped 被过滤的坏条目计数（映射层 `MapOutcome.dropped` 透传）
 * @param offline 数据源不可达位；true 时保留上一轮已展示内容，不空白不崩溃
 * @param errorMessage 异常摘要；无异常为空串
 */
data class RegionState<T>(
    val status: RegionStatus = RegionStatus.LOADING,
    val items: List<T> = emptyList(),
    val dropped: Int = 0,
    val offline: Boolean = false,
    val errorMessage: String = "",
)

// ─────────────────────────── 数据源端口（只读入口边界） ───────────────────────────

/**
 * 战役区数据源端口。真实适配层（挂载单落地）从内置 `campaigns.js` 目录元数据读取；
 * 端口只产出 B6-02 入参模型，编排侧经映射层转卡片数据。
 */
fun interface BattleSourcePort {
    fun load(): List<BattleSource>
}

/** 典籍区数据源端口。真实适配层从书架 `books` 表只读流读取。 */
fun interface ClassicSourcePort {
    fun load(): List<ClassicSource>
}

/** 专题区数据源端口。真实适配层从典故词表只读读取；分组 catalog 缺源返回空列表。 */
fun interface TopicSourcePort {
    fun load(): List<TopicSource>
}

/** 马恩党史区数据源端口。仓内缺源；适配层无条目时返回空列表，编排侧不造假卡。 */
fun interface HeroSourcePort {
    fun load(): List<HeroSource>
}

/** 生字本区数据源端口。真实适配层从既有生字本 Entry 只读列表读取，只认四字段契约。 */
fun interface NotebookSourcePort {
    fun load(): List<NotebookEntrySource>
}

/**
 * 发现页整页编排快照（五区不可变状态聚合）。
 */
data class DiscoverPageState(
    val battle: RegionState<BattleCardData>,
    val classic: RegionState<ClassicBook>,
    val topic: RegionState<TopicCardData>,
    val hero: RegionState<HeroCardDraft>,
    val notebook: RegionState<NotebookEntryCard>,
)

/**
 * 发现页编排 VM。
 *
 * @param battleSource 战役区只读端口
 * @param classicSource 典籍区只读端口
 * @param topicSource 专题区只读端口
 * @param heroSource 马恩党史区只读端口
 * @param notebookSource 生字本区只读端口
 */
class DiscoverViewModel(
    private val battleSource: BattleSourcePort,
    private val classicSource: ClassicSourcePort,
    private val topicSource: TopicSourcePort,
    private val heroSource: HeroSourcePort,
    private val notebookSource: NotebookSourcePort,
) {

    private val lock = Any()

    private var battleState: RegionState<BattleCardData> = RegionState()
    private var classicState: RegionState<ClassicBook> = RegionState()
    private var topicState: RegionState<TopicCardData> = RegionState()
    private var heroState: RegionState<HeroCardDraft> = RegionState()
    private var notebookState: RegionState<NotebookEntryCard> = RegionState()

    /** 全量刷新五区；单区失败只置本区离线 / 出错位，不阻断其余区。 */
    fun refresh() {
        refreshBattle()
        refreshClassic()
        refreshTopic()
        refreshHero()
        refreshNotebook()
    }

    /** 刷新战役区。 */
    fun refreshBattle() {
        val mapped = runCatching { battleSource.load() }.mapCatching { it.toBattleCards() }
        synchronized(lock) { battleState = applyRegion(battleState, mapped) }
    }

    /** 刷新典籍区。 */
    fun refreshClassic() {
        val mapped = runCatching { classicSource.load() }.mapCatching { it.toClassicBooks() }
        synchronized(lock) { classicState = applyRegion(classicState, mapped) }
    }

    /** 刷新专题区。 */
    fun refreshTopic() {
        val mapped = runCatching { topicSource.load() }.mapCatching { it.toTopicCards() }
        synchronized(lock) { topicState = applyRegion(topicState, mapped) }
    }

    /** 刷新马恩党史区。 */
    fun refreshHero() {
        val mapped = runCatching { heroSource.load() }.mapCatching { it.toHeroDrafts() }
        synchronized(lock) { heroState = applyRegion(heroState, mapped) }
    }

    /**
     * 刷新生字本区；只计数，不做掌握态判定。
     *
     * 坏条目口径（与其余四区同构）：`token` 空白视为坏条目，剔出入口卡计数并计入
     * [RegionState.dropped]；bookName / chapterTitle 缺省按空串兜底、timestamp 非正
     * 不预判新列，二者不计坏。本区恒带一张入口卡，空态由 count==0 表达，不是返回空列表。
     * 映射与过滤同四区一样包在 `mapCatching` 内：映射期异常不拖垮整区，进失败分支保留
     * 上一轮内容并置离线位。
     */
    fun refreshNotebook() {
        val mapped = runCatching { notebookSource.load() }
            .mapCatching { raw ->
                val good = raw.filter { it.token.trim().isNotBlank() }
                MapOutcome(
                    items = listOf(good.toNotebookEntryCard()),
                    dropped = raw.size - good.size,
                )
            }
        synchronized(lock) {
            val failure = mapped.exceptionOrNull()
            if (failure != null) {
                notebookState = offlineCopy(notebookState, failure)
            } else {
                val outcome = mapped.getOrThrow()
                val card = outcome.items.first()
                notebookState = RegionState(
                    status = if (card.count == 0) RegionStatus.EMPTY else RegionStatus.CONTENT,
                    items = outcome.items,
                    dropped = outcome.dropped,
                    offline = false,
                    errorMessage = "",
                )
            }
        }
    }

    /** 战役区快照。 */
    fun battleState(): RegionState<BattleCardData> = synchronized(lock) { battleState.copy() }

    /** 典籍区快照。 */
    fun classicState(): RegionState<ClassicBook> = synchronized(lock) { classicState.copy() }

    /** 专题区快照。 */
    fun topicState(): RegionState<TopicCardData> = synchronized(lock) { topicState.copy() }

    /** 马恩党史区快照。 */
    fun heroState(): RegionState<HeroCardDraft> = synchronized(lock) { heroState.copy() }

    /** 生字本区快照。 */
    fun notebookState(): RegionState<NotebookEntryCard> = synchronized(lock) { notebookState.copy() }

    /** 整页快照（五区聚合，供页面层一次性消费）。 */
    fun snapshot(): DiscoverPageState = synchronized(lock) {
        DiscoverPageState(
            battle = battleState.copy(),
            classic = classicState.copy(),
            topic = topicState.copy(),
            hero = heroState.copy(),
            notebook = notebookState.copy(),
        )
    }

    /**
     * 通用分区编排：成功路径按映射结果落内容 / 空态；失败路径保留上一轮内容并置离线位。
     *
     * @param current 上一轮分区快照（失败时作为兜底保留）
     * @param mapped 映射结果；成功携带 [MapOutcome]，失败携带异常
     */
    private fun <T> applyRegion(
        current: RegionState<T>,
        mapped: Result<MapOutcome<T>>,
    ): RegionState<T> {
        val failure = mapped.exceptionOrNull()
        if (failure != null) {
            return offlineCopy(current, failure)
        }
        val outcome = mapped.getOrThrow()
        val items = outcome.items.toList()
        return RegionState(
            status = if (items.isEmpty()) RegionStatus.EMPTY else RegionStatus.CONTENT,
            items = items,
            dropped = outcome.dropped,
            offline = false,
            errorMessage = "",
        )
    }

    /**
     * 失败兜底副本：置离线位与异常摘要；无旧内容置 OFFLINE，有旧内容置 ERROR 并保留
     * 上一轮 items，不空白不崩溃。五区失败分支共用，避免各自拷贝口径漂移。
     */
    private fun <T> offlineCopy(
        current: RegionState<T>,
        failure: Throwable,
    ): RegionState<T> = current.copy(
        offline = true,
        errorMessage = failure.message ?: "数据源不可达",
        status = if (current.items.isEmpty()) RegionStatus.OFFLINE else RegionStatus.ERROR,
    )
}
