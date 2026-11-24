package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.help.HighlightStyle.Shadow
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 高亮阴影 / 辉光参数编辑弹窗（方案 B：Dialog 壳 + Compose 内部承载）。
 *
 * 为什么保留 Fragment 壳：宿主 [HighlightStyleDialog] 是 XML `BottomSheetDialogFragment`，
 * 自身没有 Compose 树；旧队半成品把本件改成裸 `@Composable Sheet` 后，宿主仍走
 * `Companion.show(fragmentManager, shadow)` + `Callback` 接口，断链 4 处（show 入口、
 * Callback 接口、visible 状态提升、Compose 树托管），超过止损阈值 3 处，故按 B 方案
 * 回退 Dialog 形态：对外 API（[Callback] / [show]）与宿主调用签名逐字一致，内部视图
 * 换成 Compose 令牌体系，不保留旧 XML Binding 路径。
 *
 * 取值口径：radius ∈ [0, 10]，dx / dy ∈ [-10, 10]，步进 0.5；
 * 展示文案 radius 用 `%.1f`，偏移用 `%+.1f`。阴影颜色不在本面板编辑，沿用 [initial] 的 color。
 */
class ShadowEditDialog : DialogFragment() {

    interface Callback {
        fun onShadowChanged(shadow: Shadow)
    }

    private val initialShadow: Shadow
        get() = Shadow(
            radius = arguments?.getFloat(ARG_RADIUS) ?: Shadow().radius,
            dx = arguments?.getFloat(ARG_DX) ?: Shadow().dx,
            dy = arguments?.getFloat(ARG_DY) ?: Shadow().dy,
            color = arguments?.getInt(ARG_COLOR) ?: Shadow().color
        ).normalized()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val initial = initialShadow
        return ComposeView(requireContext()).apply {
            setContent {
                // 防重入：确认 / 取消只生效一次；快速连点不会二次回调宿主或在已销毁视图上重复 dismiss。
                var submitted by remember { mutableStateOf(false) }
                ShadowEditContent(
                    initial = initial,
                    onConfirm = { value ->
                        if (submitted) return@ShadowEditContent
                        submitted = true
                        // 宿主以 childFragmentManager 打开，parentFragment 即样式面板；
                        // 兜底再取 activity，避免极端挂载顺序下回调丢失导致静默无响应。
                        ((parentFragment as? Callback) ?: (activity as? Callback))
                            ?.onShadowChanged(value)
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
        private const val ARG_RADIUS = "radius"
        private const val ARG_DX = "dx"
        private const val ARG_DY = "dy"
        private const val ARG_COLOR = "color"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager, shadow: Shadow) {
            ShadowEditDialog().apply {
                arguments = Bundle().apply {
                    putFloat(ARG_RADIUS, shadow.radius)
                    putFloat(ARG_DX, shadow.dx)
                    putFloat(ARG_DY, shadow.dy)
                    putInt(ARG_COLOR, shadow.color)
                }
            }.show(fragmentManager, ShadowEditDialog::class.simpleName)
        }
    }
}

@Composable
private fun ShadowEditContent(
    initial: Shadow,
    onConfirm: (Shadow) -> Unit,
    onDismiss: () -> Unit,
) {
    // 草稿初值先夹紧到合法区间；NaN / Infinite 由 normalized() 在上游兜底为 0。
    var radius by remember { mutableFloatStateOf(initial.radius.coerceIn(0f, 10f)) }
    var dx by remember { mutableFloatStateOf(initial.dx.coerceIn(-10f, 10f)) }
    var dy by remember { mutableFloatStateOf(initial.dy.coerceIn(-10f, 10f)) }

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
                    ).normalized(),
                )
            }
        }
    }
}

/**
 * 样式参数滑杆（阴影 / 下划线面板共用，同包 internal）。
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
    // fraction 二次夹紧：外部传入越界值（旧存档 / 异常恢复）也不会让拇指跑出轨道。
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

/** 0.5 步进吸附：旧 SeekBar 进度为值的两倍，折算后保留半格粒度；非有限值回落 0，不抛异常。 */
private fun snapHalf(value: Float): Float {
    if (!value.isFinite()) return 0f
    return (value * 2f).roundToInt() / 2f
}

private fun formatHalf(value: Float, signed: Boolean): String {
    val safe = value.takeIf { it.isFinite() } ?: 0f
    val format = if (signed) "%+.1f" else "%.1f"
    return String.format(Locale.getDefault(), format, safe)
}

@Composable
private fun stringRes(id: Int): String =
    androidx.compose.ui.platform.LocalContext.current.getString(id)
