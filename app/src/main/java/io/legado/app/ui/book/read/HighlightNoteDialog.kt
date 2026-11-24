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
import androidx.compose.foundation.layout.height
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
 * 批注 / 笔记编辑（C4A Compose 重写）。
 *
 * 旧版是 `BaseDialogFragment` + `dialog_highlight_note.xml` 视图绑定；本版保留 Fragment 壳与
 * 既有构造口径（[HighlightNoteDialog] (highlight)），两个外部调用点（阅读页、书签目录）
 * 零改动；内部承载换成 [ComposeView] + 统一半屏 [InkHalfSheet]。
 *
 * 行为对齐旧版：
 * - 章节名为只读展示；正文摘录与备注可编辑；
 * - 确定 → 回写 `bookText / note` 并走 [ReadBook.updateHighlight]；
 * - 删除 → [ReadBook.removeHighlight]；取消直接关闭。
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
            dismissAllowingStateLoss()
            return ComposeView(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setContent {
                var bookText by remember { mutableStateOf(highlight.bookText) }
                var note by remember { mutableStateOf(highlight.note) }
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
                            ToolTextField(
                                label = getString(R.string.content),
                                value = bookText,
                                onChange = { bookText = it },
                                multiLine = true,
                            )
                            ToolTextField(
                                label = getString(R.string.note_content),
                                value = note,
                                onChange = { note = it },
                                multiLine = true,
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
                                    highlight.bookText = bookText
                                    highlight.note = note
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
    }
}
