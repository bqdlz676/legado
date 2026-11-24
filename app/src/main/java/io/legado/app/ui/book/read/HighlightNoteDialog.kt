package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.data.entities.BookHighlight
import io.legado.app.model.ReadBook
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.components.ToolTextField

/**
 * 批注 / 笔记编辑弹窗（C4A Compose 重写，Fragment 壳保留）。
 *
 * 保留既有构造口径 [HighlightNoteDialog] (highlight)，两个外部调用点
 * （阅读页 `ReadBookActivity`、目录 `HighlightFragment`）零改动；内部承载换成
 * ComposeView + 统一半屏 [InkHalfSheet]。
 *
 * 行为对齐旧版：章节名只读；正文摘录与备注可编辑；确定 → 回写 `bookText / note`
 * 并走 [ReadBook.updateHighlight]；删除 → [ReadBook.removeHighlight]；取消直接关闭。
 */
class HighlightNoteDialog() : DialogFragment() {

    constructor(highlight: BookHighlight) : this() {
        arguments = Bundle().apply {
            putParcelable(ARG_HIGHLIGHT, highlight)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        @Suppress("DEPRECATION")
        val highlight = arguments?.getParcelable<BookHighlight>(ARG_HIGHLIGHT)
        if (highlight == null) {
            // 缺参数（进程重建后 arguments 丢失等）直接关闭，不渲染空表单。
            dismissAllowingStateLoss()
            return ComposeView(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setContent {
                // 初始值先按上限收口：旧数据 / 外部导入若超长，进编辑器即截断，不带着超长草稿提交。
                var bookText by remember {
                    mutableStateOf(clampLength(highlight.bookText, MAX_BOOK_TEXT_LENGTH))
                }
                var note by remember {
                    mutableStateOf(clampLength(highlight.note, MAX_NOTE_LENGTH))
                }
                // 防重入：删除 / 确定只生效一次；快速连点不会重复写库或在已销毁视图上二次 dismiss。
                var submitted by remember { mutableStateOf(false) }

                InkHalfSheet(
                    visible = true,
                    onDismiss = { dismissAllowingStateLoss() },
                    title = getString(R.string.highlight_note),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 章节名为空时整行不渲染，不留空白占位行（空数据不空白，也不堆空槽）。
                            if (highlight.chapterName.isNotBlank()) {
                                BasicText(
                                    text = highlight.chapterName,
                                    style = TextStyle(
                                        color = ComponentTokens.textSecondary,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Serif,
                                    ),
                                )
                            }
                            LabeledField(
                                label = getString(R.string.content),
                                value = bookText,
                                maxLength = MAX_BOOK_TEXT_LENGTH,
                                onValueChange = { bookText = it },
                            )
                            LabeledField(
                                label = getString(R.string.note_content),
                                value = note,
                                maxLength = MAX_NOTE_LENGTH,
                                onValueChange = { note = it },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InkButton(
                                    getString(R.string.delete),
                                    variant = InkButtonVariant.GHOST,
                                ) {
                                    if (submitted) return@InkButton
                                    submitted = true
                                    ReadBook.removeHighlight(highlight)
                                    dismissAllowingStateLoss()
                                }
                                Spacer(Modifier.width(8.dp))
                                InkButton(
                                    getString(R.string.cancel),
                                    { dismissAllowingStateLoss() },
                                    variant = InkButtonVariant.GHOST,
                                )
                                Spacer(Modifier.width(8.dp))
                                InkButton(
                                    getString(R.string.ok),
                                    variant = InkButtonVariant.PRIMARY,
                                ) {
                                    if (submitted) return@InkButton
                                    submitted = true
                                    highlight.bookText = clampLength(bookText, MAX_BOOK_TEXT_LENGTH)
                                    highlight.note = clampLength(note, MAX_NOTE_LENGTH)
                                    ReadBook.updateHighlight(highlight)
                                    dismissAllowingStateLoss()
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        private const val ARG_HIGHLIGHT = "highlight"

        // 摘录上限：来自章节内选中片段，5000 字覆盖数段选文；超长粘贴 / 导入在此截断，
        // 避免单行 DB 记录膨胀与多行排版卡顿。适用条件：普通选文摘录，不承载整篇复制。
        const val MAX_BOOK_TEXT_LENGTH = 5000

        // 备注上限：备注是短评而非正文，1000 字足够；与皮肤名 80 / 128 的保守上限口径一致，
        // 防止长文误入备注列。适用条件：读书笔记 / 批注，非长文写作场景。
        const val MAX_NOTE_LENGTH = 1000

        // 按 UTF-16 code unit 截断；代理对边界极少出现在中文备注，此处不做 codePoint 回退。
        internal fun clampLength(input: String, max: Int): String =
            if (input.length > max) input.substring(0, max) else input
    }
}

/** 标签 + 计数 + 输入框：计数可见，超长在输入侧截断，不弹错、不崩。 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = ComponentTokens.stoneBlue,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = "${value.length}/$maxLength",
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
        ToolTextField(
            label = "",
            value = value,
            onChange = { raw ->
                onValueChange(
                    if (raw.length > maxLength) raw.substring(0, maxLength) else raw
                )
            },
            placeholder = label,
            multiLine = true,
        )
    }
}
