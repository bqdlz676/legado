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
import androidx.compose.runtime.Composable // @Composable 注解必需；缺此 import 会 unresolved reference
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

    /**
     * 防重入闸：所有离开路径（确认 / 删除 / 取消 / 下滑关闭 / 遮罩点击 / 返回键）
     * 统一经 [dismissOnce] 单点出口；闸一旦置位，任何二次触发整段 no-op。
     * 放在 Fragment 字段而非 Compose remember：返回键监听挂在 Dialog 窗口层，
     * 拿不到 Compose 作用域内的 remember 状态，必须与 Compose 侧共用同一闸。
     */
    @Volatile
    private var submitted = false

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
                InkHalfSheet(
                    visible = true,
                    // 下滑关闭与遮罩点击（外部点击）在共享组件内都回调这同一个 onDismiss，
                    // 统一交给单点出口判闸，不在回调里再各自 dismiss。
                    onDismiss = { dismissOnce(false) },
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
                                hintFormat = TRUNCATED_HINT_FORMAT,
                                onValueChange = { bookText = it },
                            )
                            LabeledField(
                                label = getString(R.string.note_content),
                                value = note,
                                maxLength = MAX_NOTE_LENGTH,
                                hintFormat = TRUNCATED_HINT_FORMAT,
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
                                    dismissOnce(true) { ReadBook.removeHighlight(highlight) }
                                }
                                Spacer(Modifier.width(8.dp))
                                InkButton(
                                    getString(R.string.cancel),
                                    { dismissOnce(false) },
                                    variant = InkButtonVariant.GHOST,
                                )
                                Spacer(Modifier.width(8.dp))
                                InkButton(
                                    getString(R.string.ok),
                                    variant = InkButtonVariant.PRIMARY,
                                ) {
                                    dismissOnce(true) {
                                        highlight.bookText = clampLength(bookText, MAX_BOOK_TEXT_LENGTH)
                                        highlight.note = clampLength(note, MAX_NOTE_LENGTH)
                                        ReadBook.updateHighlight(highlight)
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 返回键也收敛到单点出口：Dialog 默认回退会绕过 Compose 回调直接 dismiss，
        // 这里吞掉 KEYCODE_BACK，交 [dismissOnce] 判闸；闸置位后再按返回键为 no-op。
        // 类型全部用全限定名，不新增 import（文件域红线：不动 import 区块）。
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.action == android.view.KeyEvent.ACTION_UP) dismissOnce(false)
                true
            } else {
                false
            }
        }
    }

    /**
     * 单点出口：为什么必须单点——写库副作用（[ReadBook.updateHighlight] /
     * [ReadBook.removeHighlight]）与 dismiss 只能发生一次；若某条离开路径绕过此闸
     * 直接 dismiss，快速连点确认+取消、下滑与返回键并发时就可能二次写库或二次回调宿主。
     *
     * @param commit true = 提交类动作（确认 / 删除）：进入闸后先跑 [commitAction] 写库；
     *               false = 纯离开（取消 / 下滑 / 遮罩点击 / 返回键）：无副作用，仅收尾关闭。
     * @param commitAction 提交类副作用；闸置位后的二次触发整段不执行（no-op）。
     */
    private fun dismissOnce(commit: Boolean, commitAction: (() -> Unit)? = null) {
        if (submitted) return  // 二次触发 no-op：不写库、不重复 dismiss。
        submitted = true
        if (commit) commitAction?.invoke()
        dismissAllowingStateLoss()
    }

    companion object {
        private const val ARG_HIGHLIGHT = "highlight"

        // 摘录上限：来自章节内选中片段，5000 字覆盖数段选文；超长粘贴 / 导入在此截断，
        // 避免单行 DB 记录膨胀与多行排版卡顿。适用条件：普通选文摘录，不承载整篇复制。
        const val MAX_BOOK_TEXT_LENGTH = 5000

        // 备注上限：备注是短评而非正文，1000 字足够；与皮肤名 80 / 128 的保守上限口径一致，
        // 防止长文误入备注列。适用条件：读书笔记 / 批注，非长文写作场景。
        const val MAX_NOTE_LENGTH = 1000

        // 截断提示文案集中在此常量：摘录 / 备注两个输入框共用，避免两处各写一份导致口径漂移。
        // %1$d = 本次粘贴被截掉的字数；%2$d = 当前字段上限字数。
        // 格式串里的 $ 必须转义：Kotlin 双引号串会把 $d 当成模板引用标识符 d，
        // 既报 unresolved reference 'd'，也让 const 初值不再是编译期常量。
        internal const val TRUNCATED_HINT_FORMAT = "已达 %2\$d 字上限，超出的 %1\$d 字未录入"

        // 按 UTF-16 code unit 截断；代理对边界极少出现在中文备注，此处不做 codePoint 回退。
        internal fun clampLength(input: String, max: Int): String =
            if (input.length > max) input.substring(0, max) else input
    }
}

/** 标签 + 计数 + 输入框：计数可见，超长在输入侧截断；首次截断给一次 inline 轻提示，不弹错、不崩。 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    maxLength: Int,
    hintFormat: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 截断提示在本弹窗会话内只出现一次：首次超长粘贴记下被截字数后 latch 为非空，
        // 后续再粘贴 / 删减不再重复骚扰；null = 本字段尚未发生过会话内截断。
        var hintDropped by remember { mutableStateOf<Int?>(null) }
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
        hintDropped?.let { dropped ->
            BasicText(
                text = hintFormat.format(dropped, maxLength),
                style = TextStyle(
                    color = ComponentTokens.brandCopper,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
        ToolTextField(
            label = "",
            value = value,
            onChange = { raw ->
                if (raw.length > maxLength && hintDropped == null) {
                    hintDropped = raw.length - maxLength
                }
                onValueChange(
                    if (raw.length > maxLength) raw.substring(0, maxLength) else raw
                )
            },
            placeholder = label,
            multiLine = true,
        )
    }
}
