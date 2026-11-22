package io.legado.app.ui.main.explore

/**
 * 落文 · 发现页 UI 模型（状态提升，不直接碰 DAO / assets）
 *
 * 主控 merge 时把数据层（campaigns.js / allusion.txt / 书库）映射成这三个列表即可，
 * 本子域只负责排版与动效。任何字段为空都由卡片与空态兜底，不抛异常、不留白。
 */

/** 战役精选横卡 */
data class BattleFeatured(
    val id: String,
    /** 战役名，如「长平之战」 */
    val title: String,
    /** 时期 / 对阵，如「战国 · 秦赵」 */
    val era: String,
    /** 地名，如「长平」 */
    val place: String,
    /** 一句题注 */
    val note: String,
    /** 公元前为负，公元后为正；未知填 0 */
    val year: Int = 0,
    /** campaigns/ 下的战役 key，如 changping；无沙盘留空 */
    val campaignKey: String = "",
    /** 阅读 / 推演进度：-1 表示未开始，0f..1f 为进度 */
    val progress: Float = -1f
)

/** 典籍竖排栅格卡 */
data class ClassicBook(
    val id: String,
    /** 书名（竖排逐字换行显示） */
    val title: String,
    /** 朝代，如「春秋」 */
    val dynasty: String,
    /** 体例 / 流派，如「编年」 */
    val genre: String,
    /** 阅读进度：-1 未读，0f..1f 进度 */
    val progress: Float = -1f
)

/** 典故专题通栏卡 */
data class AllusionTopic(
    val id: String,
    val title: String,
    /** 典故条数；未知传 0，卡片按 0 兜底显示 */
    val allusionCount: Int,
    /** 预览典故名（最多取 4 个上卡） */
    val preview: List<String> = emptyList()
)

/**
 * 发现页整体加载态。
 *
 * 六态 + 离线态：默认(Content) / 悬浮 / 按压 / 禁用 / 加载(Loading) / 空数据(Empty) / 离线(Offline)。
 * 悬浮、按压、禁用三态在卡片交互层处理；这里管整页的加载、空、错、离线。
 */
sealed interface ExploreLoadState {

    /** 首屏 / 分区骨架加载中 */
    object Loading : ExploreLoadState

    /** 有数据 */
    data class Content(
        val battles: List<BattleFeatured> = emptyList(),
        val classics: List<ClassicBook> = emptyList(),
        val topics: List<AllusionTopic> = emptyList()
    ) : ExploreLoadState

    /** 全部分区都没有数据（兜底空态，不空白不崩溃） */
    object Empty : ExploreLoadState

    /** 数据加载失败，带可重试入口 */
    data class Error(val message: String = "") : ExploreLoadState

    /** 网络断开：保留本地可展示内容，展示离线兜底文案 */
    object Offline : ExploreLoadState
}
