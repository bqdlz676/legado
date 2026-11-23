package io.legado.app.ui.main.bookshelf

/**
 * 书架卡片纯展示模型（U4 独占）。
 *
 * Compose 层不碰 Room 实体；接入层把原始字段交给 [shelfBookUi] 换算成该模型，画面只认它。
 * 字段缺失一律走兜底文案，不允许空串崩到 UI。
 */
data class ShelfBookUi(
    val id: String,
    val title: String,
    val author: String,
    val chapterText: String,
    /** 阅读进度 0f..1f，驱动朱砂竖线 / 右缘水印 / 底部细线 */
    val progress: Float,
    /** 进度文案：未读 / 百分比 */
    val percentText: String,
    /** 是否最近新增（顶部横幅计数来源） */
    val isNew: Boolean,
)

/**
 * 书架组件状态机。
 *
 * 契约第四节：六态 + 第七态「离线」。任何一态都必须有可见兜底，不允许空白屏。
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

/** 进度 / 文案 / 新书标记的纯 JVM 换算，便于单测与 bench 复用。 */
object ShelfBookMath {

    fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    /** 章节口径进度：已读到的章序 / 总章数；总章数未知时回落 0（未读）。 */
    fun progressOf(durChapterIndex: Int, totalChapterNum: Int): Float {
        if (totalChapterNum <= 0) return 0f
        return ((durChapterIndex + 1).toFloat() / totalChapterNum).coerceIn(0f, 1f)
    }

    fun percentText(progress: Float): String =
        if (progress <= 0f) "未读" else "${(progress * 100).toInt()}%"

    fun isNew(
        latestChapterTime: Long,
        now: Long,
        windowMs: Long = ShelfTokens.NEW_BOOK_WINDOW_MS,
    ): Boolean = latestChapterTime in (now - windowMs)..Long.MAX_VALUE
}

/**
 * 展示模型工厂：进度、文案、新书标记全部在此换算，字段缺失走兜底。
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
    now: Long = System.currentTimeMillis(),
): ShelfBookUi {
    val titleText = title.takeIf { it.isNotBlank() } ?: "无名卷"
    val authorText = author.takeIf { it.isNotBlank() } ?: "佚名"
    val chapterText = chapterTitle?.takeIf { it.isNotBlank() } ?: "未起读"
    val progress = ShelfBookMath.progressOf(durChapterIndex, totalChapterNum)
    val fallbackId = id.ifBlank { "$titleText|$authorText" }
    return ShelfBookUi(
        id = fallbackId,
        title = titleText,
        author = authorText,
        chapterText = chapterText,
        progress = progress,
        percentText = ShelfBookMath.percentText(progress),
        isNew = ShelfBookMath.isNew(latestChapterTime, now),
    )
}

/** 书名竖排：逐字成行；超出 5 字收为「…」。书名即封面，无封面图。 */
fun verticalTitle(title: String): List<String> {
    val chars = title.trim().toCharArray().map { it.toString() }
    return if (chars.size <= 5) chars else chars.take(5) + "…"
}
