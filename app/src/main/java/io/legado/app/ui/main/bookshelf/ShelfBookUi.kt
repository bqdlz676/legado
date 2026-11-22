package io.legado.app.ui.main.bookshelf

/**
 * 书架卡片的纯展示模型（模块 9 独占）。
 *
 * Compose 层不依赖任何旧版 Fragment / Adapter / Room 实体，接入层（主控 merge 的
 * Fragment / ViewModel）把数据字段逐一交给 [shelfBookUi] 换算成该模型，画面只认它。
 */
data class ShelfBookUi(
    val id: String,
    val title: String,
    val author: String,
    /** 阅读进度 0f..1f，驱动朱砂竖线高度 */
    val progress: Float,
    /** 进度文案：未读 / 百分比 */
    val percentText: String,
    /** 最近章节文案 */
    val chapterText: String,
    /** 是否最近新增（顶部横幅「发现 N 本新书」计数来源） */
    val isNew: Boolean,
    val coverUrl: String? = null,
)

/**
 * 书架组件状态机。
 *
 * 契约第四节：组件 6 态 + 第 7 态「离线」。
 * 六态 = [CONTENT] / [INIT_LOADING] / [REFRESHING] / [EMPTY] / [ERROR] / [DISABLED]，
 * 第七态 [OFFLINE] 单列；任何一态都必须有可见兜底，不允许空白屏。
 */
enum class ShelfState {
    /** 书架有内容 */
    CONTENT,

    /** 首帧加载中（骨架） */
    INIT_LOADING,

    /** 下拉 / 后台刷新中（保留已有内容，顶部细金线） */
    REFRESHING,

    /** 书架为空 */
    EMPTY,

    /** 读取失败，可重试 */
    ERROR,

    /** 被禁用 / 权限缺失等不可用态 */
    DISABLED,

    /** 离线：仅展示本地缓存，提示不可联网更新 */
    OFFLINE,
}

/** 新增判定窗口：7 天内入库视为新书 */
private const val NEW_BOOK_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000

/**
 * 展示模型工厂：进度、文案、新书标记全部在此换算，字段缺失走兜底，不允许空串崩到 UI。
 * 接入层只负责把原始字段传进来，不接触卡片渲染。
 */
fun shelfBookUi(
    id: String,
    title: String,
    author: String,
    durChapterIndex: Int,
    totalChapterNum: Int,
    chapterTitle: String?,
    latestChapterTime: Long,
    coverUrl: String? = null,
    now: Long = System.currentTimeMillis(),
): ShelfBookUi {
    val total = totalChapterNum.coerceAtLeast(0)
    val progress = if (total > 0) {
        ((durChapterIndex + 1).toFloat() / total).coerceIn(0f, 1f)
    } else 0f
    val percentText = when {
        progress <= 0f -> "未读"
        else -> "${(progress * 100).toInt()}%"
    }
    val chapterText = chapterTitle?.takeIf { it.isNotBlank() } ?: "未起读"
    val authorText = author.takeIf { it.isNotBlank() } ?: "佚名"
    val titleText = title.takeIf { it.isNotBlank() } ?: "无名卷"
    val isNew = latestChapterTime in (now - NEW_BOOK_WINDOW_MS)..Long.MAX_VALUE
    return ShelfBookUi(
        id = id.ifBlank { "$titleText|$authorText" },
        title = titleText,
        author = authorText,
        progress = progress,
        percentText = percentText,
        chapterText = chapterText,
        isNew = isNew,
        coverUrl = coverUrl?.takeIf { it.isNotBlank() },
    )
}

/** 书名竖排：逐字成行，超出 5 字收为「…」。 */
fun verticalTitle(title: String): List<String> {
    val chars = title.trim().toCharArray().map { it.toString() }
    return if (chars.size <= 5) chars else chars.take(5) + "…"
}
