package io.legado.app.ui.config.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.ComponentPalette
import io.legado.app.ui.components.ComponentTokens

/**
 * 设置页自绘控件：顶栏 / 分组标题 / 行容器 / 墨触开关 / 分隔线。
 *
 * 全部 foundation 原语自绘：不用 Material Switch / ListItem / Card / TopAppBar，
 * 不挂 ripple / elevation；按压反馈是朱砂 8%→16% 浸染，120ms 回落（切片-04 InkButton 同口径）。
 * 颜色只取 [ComponentTokens]，12 token 之外不造色值。
 */

/** 页面骨架：纸白底 + 自绘顶栏（返回 + 宋体标题）+ 内容槽。 */
@Composable
fun SettingScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack = onBack)
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = title,
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                ),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

/** 顶栏返回钮：朱砂竖线 + 向左箭头，最小点击区 48dp。 */
@Composable
private fun BackButton(onBack: () -> Unit) {
    val gesture = rememberInkPress(onClick = onBack)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .then(gesture.modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp, 20.dp)) {
            val stroke = ComponentTokens.textSecondary
            drawLine(
                color = stroke,
                start = Offset(size.width * 0.62f, size.height * 0.18f),
                end = Offset(size.width * 0.28f, size.height * 0.5f),
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                color = stroke,
                start = Offset(size.width * 0.28f, size.height * 0.5f),
                end = Offset(size.width * 0.62f, size.height * 0.82f),
                strokeWidth = 2.dp.toPx(),
            )
        }
        if (gesture.overlayAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(ComponentTokens.textAccent.copy(alpha = gesture.overlayAlpha)),
            )
        }
    }
}

/** 分组标题：14sp 石青，字距拉开，上下留呼吸。 */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        style = TextStyle(
            color = ComponentTokens.stoneBlue,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.5.sp,
        ),
    )
}

/** 行容器：最小高 56dp（≥48dp 点击目标），朱砂浸染按压反馈，底部暖灰细线。 */
@Composable
fun SettingRowShell(
    onClick: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gesture = rememberInkPress(enabled = enabled, onClick = onClick ?: {})
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(gesture.modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else ComponentTokens.disabledAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        content()
        if (gesture.overlayAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(ComponentTokens.textAccent.copy(alpha = gesture.overlayAlpha)),
            )
        }
    }
}

/** 行内标题 + 说明列（左侧主文）。 */
@Composable
fun RowTitleBlock(title: String, summary: String?, danger: Boolean = false) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
        BasicText(
            text = title,
            style = TextStyle(
                color = if (danger) ComponentTokens.textAccent else ComponentTokens.textPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = summary,
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
    }
}

/** 右侧取值文字（ValueRow 尾部）。 */
@Composable
fun RowValueText(text: String?, arrow: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!text.isNullOrBlank()) {
            BasicText(
                text = text,
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (arrow) {
            Canvas(Modifier.size(16.dp, 16.dp)) {
                drawLine(
                    color = ComponentTokens.textTertiary,
                    start = Offset(size.width * 0.35f, size.height * 0.3f),
                    end = Offset(size.width * 0.65f, size.height * 0.5f),
                    strokeWidth = 1.6.dp.toPx(),
                )
                drawLine(
                    color = ComponentTokens.textTertiary,
                    start = Offset(size.width * 0.65f, size.height * 0.5f),
                    end = Offset(size.width * 0.35f, size.height * 0.7f),
                    strokeWidth = 1.6.dp.toPx(),
                )
            }
        }
    }
}

/** 自绘墨触开关：暖金轨道 + 墨色滑块，spring 回弹；不用 Material Switch。 */
@Composable
fun InkSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        thumbOffset.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    val trackColor = when {
        !enabled -> ComponentTokens.textTertiary.copy(alpha = 0.35f)
        checked -> ComponentTokens.brandGold
        else -> ComponentTokens.textTertiary.copy(alpha = 0.35f)
    }
    Box(
        modifier = modifier
            .size(46.dp, 28.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .pointerInput(enabled) {
                detectTapGestures(enabled = enabled) { onCheckedChange(!checked) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbX = 4.dp + (38.dp - 20.dp) * thumbOffset.value
        Box(
            modifier = Modifier
                .padding(start = thumbX)
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) ComponentTokens.bgPage else ComponentTokens.bgSurface),
        )
    }
}

/** 分组间暖灰细线。 */
@Composable
fun SettingDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(ComponentTokens.colorDivider),
    )
}

/** 轻量墨触手势：悬浮 8% / 按下 16% 朱砂覆盖，120ms 回落；禁用态不响应。 */
@Composable
private fun rememberInkPress(enabled: Boolean = true, onClick: () -> Unit): InkPressState {
    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val overlayAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            pressed -> ComponentPalette.PRESS_DOWN_ACCENT_ALPHA
            hovered -> ComponentPalette.PRESS_HOVER_ACCENT_ALPHA
            else -> 0f
        },
        animationSpec = tween(ComponentPalette.DURATION_MICRO, easing = ComponentTokens.cardEnterEasing),
        label = "settingPress",
    ).value
    val modifier = Modifier.pointerInput(enabled) {
        detectTapGestures(
            enabled = enabled,
            onPress = {
                pressed = true
                val released = tryAwaitRelease()
                pressed = false
                if (released) onClick()
            },
            onTap = { },
        )
    }
    return remember(enabled, overlayAlpha) { InkPressState(modifier, overlayAlpha) }
}

private data class InkPressState(val modifier: Modifier, val overlayAlpha: Float)

/** 兜底基础文本（统一字体语义，避免散落 TextStyle）。 */
@Composable
fun BasicText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicText(text = text, style = style, modifier = modifier)
}

/** 纯色兜底盒占位（加载 / 空态外的装饰）。 */
@Composable
fun tokenColor(): Color = ComponentTokens.textPrimary
