package io.legado.app.ui.book.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.HighlightStyle.Underline
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import java.util.Locale

/**
 * 下划线 / 删除线粗细与间距编辑（C4A Compose 重写）。
 *
 * 旧版是 `BaseDialogFragment` + 两个 XML SeekBar，回调走 `parentFragment as? Callback`。
 * 本版由样式面板 [HighlightStyleDialog] 的 Compose 树直接托管；线型（kind）与颜色
 * 不在本面板编辑，沿用 [initial]。取值口径对齐旧版：width ∈ [0, 10]，distance ∈ [0, 30]，
 * 提交前走 [Underline.normalized] 归一化，超界值收敛回合法区间。
 */
@Composable
internal fun UnderlineEditSheet(
    visible: Boolean,
    initial: Underline,
    onConfirm: (Underline) -> Unit,
    onDismiss: () -> Unit,
) {
    var width by remember { mutableFloatStateOf(initial.width.coerceIn(Underline.MIN_WIDTH, Underline.MAX_WIDTH)) }
    var distance by remember {
        mutableFloatStateOf(initial.distance.coerceIn(Underline.MIN_DISTANCE, Underline.MAX_DISTANCE))
    }
    LaunchedEffect(visible) {
        if (visible) {
            width = initial.width.coerceIn(Underline.MIN_WIDTH, Underline.MAX_WIDTH)
            distance = initial.distance.coerceIn(Underline.MIN_DISTANCE, Underline.MAX_DISTANCE)
        }
    }
    if (!visible) return

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

private fun formatHalf(value: Float): String =
    String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.platform.LocalContext.current.getString(id)
