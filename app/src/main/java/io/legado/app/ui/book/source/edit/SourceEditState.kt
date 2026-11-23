package io.legado.app.ui.book.source.edit

import io.legado.app.data.entities.BookSource
import io.legado.app.ui.components.ToastMessage

/**
 * 落文 · 书源编辑域纯状态模型（Compose 状态提升；不含任何 View / Adapter / Binding）。
 *
 * 七个分组标签页与旧编辑页一一对应：基本 / 搜索 / 发现 / 详情 / 目录 / 正文 / 段评。
 * 每个字段保留旧 [io.legado.app.ui.widget.text.EditEntity] 的 key 与文案资源，
 * 装配时按 key 回填到 BookSource 与各规则实体。
 */
enum class EditTab(val titleRes: Int) {
    BASE(io.legado.app.R.string.source_tab_base),
    SEARCH(io.legado.app.R.string.source_tab_search),
    EXPLORE(io.legado.app.R.string.source_tab_find),
    INFO(io.legado.app.R.string.source_tab_info),
    TOC(io.legado.app.R.string.source_tab_toc),
    CONTENT(io.legado.app.R.string.source_tab_content),
    REVIEW(io.legado.app.R.string.source_tab_review);

    companion object {
        val entries: List<EditTab> = values().toList()
    }
}

/** 单个可编辑字段：key 对应规则回填路径，labelRes 为旧页文案，value 为原始输入。 */
data class EditField(
    val key: String,
    val labelRes: Int,
    val value: String?,
)

/** 选项面板：旧页 Spinner + 六个 CheckBox 收口为纯数据。 */
data class EditOptions(
    val enabled: Boolean = true,
    val enabledExplore: Boolean = false,
    val enabledCookieJar: Boolean = false,
    val enabledReview: Boolean = false,
    val eventListener: Boolean = false,
    val customButton: Boolean = false,
    /** 0 文本 1 音频 2 图片 3 文件 4 视频（与 R.array.book_type 顺序一致）。 */
    val typeIndex: Int = 0,
)

data class SourceEditUiState(
    val source: BookSource? = null,
    val fields: Map<EditTab, List<EditField>> = emptyMap(),
    val options: EditOptions = EditOptions(),
    val errors: Map<String, String> = emptyMap(),
    val autoComplete: Boolean = false,
    val loading: Boolean = true,
    val message: String? = null,
    val toasts: List<ToastMessage> = emptyList(),
    /** 最近聚焦的字段（插入 URL 参数 / 分组 / 文件落点）；null 表示尚无焦点。 */
    val activeField: Pair<EditTab, String>? = null,
) {
    fun field(tab: EditTab, key: String): EditField? = fields[tab]?.find { it.key == key }
}
