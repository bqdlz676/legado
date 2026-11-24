package io.legado.app.ui.book.read

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.HighlightStyle.Shadow
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 高亮阴影 / 辉光参数编辑（C4A Compose 重写）。
 *
 * 旧版是 `BaseDialogFragment` + XML SeekBar 三件套，回调走 `parentFragment as? Callback`。
 * 本版不再挂 Fragment 事务：由样式面板 [HighlightStyleDialog] 的 Compose 树直接托管本半屏，
 * 确认结果经 [onConfirm] 原样回传；阴影颜色不在本面板编辑，沿用 [initial] 中的 color。
 *
 * 取值口径与旧版逐字对齐：radius ∈ [0, 10]，dx / dy ∈ [-10, 10]，步进 0.5；
 * 展示文案 radius 用 `%.1f`，偏移用 `%+.1f`（旧 formatHalf 口径）。
 */
@Composable
internal fun ShadowEditSheet(
    visible: Boolean,
    initial: Shadow,
    onConfirm: (Shadow) -> Unit,
    onDismiss: () -> Unit,
) {
    // 每次弹起时以 initial 复位本地草稿；关闭后草稿不残留（§5.4 关闭后状态不变）。
    var radius by remember { mutableFloatStateOf(initial.radius.coerceIn(0f, 10f)) }
    var dx by remember { mutableFloatStateOf(initial.dx.coerceIn(-10f, 10f)) }
    var dy by remember { mutableFloatStateOf(initial.dy.coerceIn(-10f, 10f)) }
    LaunchedEffect(visible) {
        if (visible) {
            radius = initial.radius.coerceIn(0f, 10f)
            dx = initial.dx.coerceIn(-10f, 10f)
            dy = initial.dy.coerceIn(-10f, 10f)
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
            text = stringRes(R.string.highlight_shadow),
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 17.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
        )

        InkStyleSlider(
            label = stringRes(R.string.highlight_shadow_radius),
            value = radius,
            min = 0f,
            max = 10f,
            valueText = formatHalf(radius, signed = false),
            onValueChange = { radius = it },
        )
        InkStyleSlider(
            label = stringRes(R.string.highlight_shadow_offset_x),
            value = dx,
            min = -10f,
            max = 10f,
            valueText = formatHalf(dx, signed = true),
            onValueChange = { dx = it },
        )
        InkStyleSlider(
            label = stringRes(R.string.highlight_shadow_offset_y),
            value = dy,
            min = -10f,
            max = 10f,
            valueText = formatHalf(dy, signed = true),
            onValueChange = { dy = it },
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
                    initial.copy(
                        radius = snapHalf(radius),
                        dx = snapHalf(dx),
                        dy = snapHalf(dy),
                    ),
                )
            }
        }
    }
}

/**
 * 样式参数滑杆（阴影 / 下划线面板共用）。
 *
 * 纯手势自绘：轨道 + 暖金拇指，无 Material SeekBar / ripple；拖动只改本地草稿，
 * 松手不回写宿主，确认按钮统一提交。步进 0.5 与旧 SeekBar 双倍进度口径一致。
 */
@Composable
internal fun InkStyleSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = (max - min).takeIf { it > 0f } ?: 1f
    val fraction = ((value - min) / range).coerceIn(0f, 1f)
    val thumbHalfPx = with(LocalDensity.current) { 8.dp.toPx() }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = valueText,
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(min, max) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val width = size.width.toFloat().takeIf { it > 0f }
                            ?: return@detectHorizontalDragGestures
                        val next = fraction + dragAmount / width
                        onValueChange(snapHalf(min + next.coerceIn(0f, 1f) * range))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 轨道：绢帛底；已走段暖金。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ComponentTokens.bgSurface),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ComponentTokens.brandGold),
            )
            // 拇指：暖金圆点，按比例落在轨道上；墨触无 ripple。
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            ((fraction * size.width) - thumbHalfPx).roundToInt(),
                            0,
                        )
                    }
                    .size(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ComponentTokens.brandGold),
            )
        }
    }
}

/** 0.5 步进吸附：旧 SeekBar 进度为值的两倍，折算后保留半格粒度。 */
private fun snapHalf(value: Float): Float {
    if (!value.isFinite()) return 0f
    return (value * 2f).roundToInt() / 2f
}

private fun formatHalf(value: Float, signed: Boolean): String {
    val format = if (signed) "%+.1f" else "%.1f"
    return String.format(Locale.getDefault(), format, value)
}

@Composable
private fun stringRes(id: Int): String = LocalContext.current.getString(id)
