package io.legado.app.ui.main.bookshelf

/**
 * D25 · 书架页下拉搜索浮层状态机（纯 JVM，不依赖 Compose / Android 框架，便于单测）。
 *
 * 口径（F-13）：搜索只做书架页下拉浮层，不再起独立搜索页；
 * 浮层输入即查本地书架，书源联网检索仍归 D1~D7，本状态机不碰网络。
 */
object ShelfSearch {

    enum class Stage { CLOSED, OPENED }

    data class State(
        val stage: Stage,
        val query: String,
    ) {
        val isOpen: Boolean get() = stage == Stage.OPENED
    }

    /** 初始态：收起、无关键词。 */
    val closed = State(Stage.CLOSED, "")

    /** 展开浮层；保留上次关键词，便于接续输入。 */
    fun open(state: State): State = state.copy(stage = Stage.OPENED)

    /** 收起浮层：清空关键词，下次打开是一次新搜索，不留陈旧查询。 */
    fun close(state: State): State = State(Stage.CLOSED, "")

    /** 输入即查：关键词原样落状态，结果由 [filter] 派生。 */
    fun updateQuery(state: State, query: String): State = state.copy(query = query)

    /**
     * 本地书架过滤：书名 / 作者包含关键词即命中，忽略大小写与首尾空白。
     * 关键词为空时原样返回全量（浮层引导态）；列表为空或无命中时返回空列表，由画面层给占位。
     */
    fun filter(books: List<ShelfBookUi>, query: String): List<ShelfBookUi> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return books
        return books.filter {
            it.title.lowercase().contains(key) || it.author.lowercase().contains(key)
        }
    }
}
