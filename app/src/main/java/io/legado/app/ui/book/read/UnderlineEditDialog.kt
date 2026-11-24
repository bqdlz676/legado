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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.help.HighlightStyle.Underline
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import java.util.Locale

/**
 * 下划线 / 删除线粗细与间距编辑弹窗（方案 B：Dialog 壳 + Compose 内部承载）。
 *
 * 与 [ShadowEditDialog] 同口径：对外保留 [Callback] / [show]，宿主
 * [HighlightStyleDialog] 既有调用签名不变；内部承载换成 Compose 令牌体系。
 * 线型（kind）与颜色不在本面板编辑，沿用 [initial]。取值口径对齐旧版：
 * width ∈ [0, 10]，distance ∈ [0, 30]，提交前走 [Underline.normalized] 归一化。
 */
class UnderlineEditDialog : DialogFragment() {

    interface Callback {
        fun onUnderlineChanged(underline: Underline)
    }

    private val initialUnderline: Underline
        get() = Underline(
            kind = arguments?.getString(ARG_KIND)?.let {
                runCatching { io.legado.app.help.HighlightStyle.Kind.valueOf(it) }.getOrNull()
            } ?: Underline().kind,
            color = arguments?.getInt(ARG_COLOR) ?: Underline().color,
            width = arguments?.getFloat(ARG_WIDTH) ?: Underline().width,
            distance = arguments?.getFloat(ARG_DISTANCE) ?: Underline().distance
        ).normalized()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val initial = initialUnderline
        return ComposeView(requireContext()).apply {
            setContent {
                // 防重入：确认 / 取消只生效一次，快速连点不二次回调宿主。
                var submitted by remember { mutableStateOf(false) }
                UnderlineEditContent(
                    initial = initial,
                    onConfirm = { value ->
                        if (submitted) return@UnderlineEditContent
                        submitted = true
                        ((parentFragment as? Callback) ?: (activity as? Callback))
                            ?.onUnderlineChanged(value)
                        dismissAllowingStateLoss()
                    },
                    onDismiss = {
                        if (!submitted) {
                            submitted = true
                            dismissAllowingStateLoss()
                        }
                    },
                )
            }
        }
    }

    companion object {
        private const val ARG_KIND = "kind"
        private const val ARG_COLOR = "color"
        private const val ARG_WIDTH = "width"
        private const val ARG_DISTANCE = "distance"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager, underline: Underline) {
            UnderlineEditDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_KIND, underline.kind.name)
                    putInt(ARG_COLOR, underline.color)
                    putFloat(ARG_WIDTH, underline.width)
                    putFloat(ARG_DISTANCE, underline.distance)
                }
            }.show(fragmentManager, UnderlineEditDialog::class.simpleName)
        }
    }
}

@Composable
private fun UnderlineEditContent(
    initial: Underline,
    onConfirm: (Underline) -> Unit,
    onDismiss: () -> Unit,
) {
    // 草稿初值夹紧到合法区间；非有限值由 normalized() 在上游回落默认值。
    var width by remember {
        mutableFloatStateOf(initial.width.coerceIn(Underline.MIN_WIDTH, Underline.MAX_WIDTH))
    }
    var distance by remember {
        mutableFloatStateOf(initial.distance.coerceIn(Underline.MIN_DISTANCE, Underline.MAX_DISTANCE))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            text = stringRes(R.string.highlight_underline),
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 17.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
        )

        InkStyleSlider(
            label = stringRes(R.string.highlight_underline_width),
            value = width,
            min = Underline.MIN_WIDTH,
            max = Underline.MAX_WIDTH,
            valueText = formatHalf(width),
            onValueChange = { width = it },
        )
        InkStyleSlider(
            label = stringRes(R.string.highlight_underline_distance),
            value = distance,
            min = Underline.MIN_DISTANCE,
            max = Underline.MAX_DISTANCE,
            valueText = formatHalf(distance),
            onValueChange = { distance = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton(stringRes(R.string.cancel), onDismiss, variant = InkButtonVariant.GHOST)
            Spacer(Modifier.width(8.dp))
            InkButton(stringRes(R.string.ok), variant = InkButtonVariant.PRIMARY) {
                onConfirm(
                    initial.copy(width = width, distance = distance).normalized(),
                )
            }
        }
    }
}

private fun formatHalf(value: Float): String {
    val safe = value.takeIf { it.isFinite() } ?: 0f
    return String.format(Locale.getDefault(), "%.1f", safe)
}

@Composable
private fun stringRes(id: Int): String =
    androidx.compose.ui.platform.LocalContext.current.getString(id)
