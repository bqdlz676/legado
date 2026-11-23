package io.legado.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 工具页共用骨架（词典 / 替换 / 自动任务 / 文件四域）。
 *
 * 设计口径（切片-03 / 切片-04 / 切片-15）：
 * - 颜色只取 [ComponentTokens]，本子域不内联任何 hex，不引 MaterialTheme / ripple / elevation / linear；
 * - 所有可点元素触达区 ≥ 48dp；墨触反馈走 [rememberInkGesture] 的朱砂覆盖，120ms 回落；
 * - 标题衬线、标签黑体；开关弹簧回弹复用 [ComponentTokens.revealSpring]。
 *
 * 本文件只放四域复用的页面骨架与基础行控件，业务编排归各域 Screen。
 */

/** 顶栏 / 选择栏动作项；[danger] 为朱砂警示色（删除类）。 */
data class ToolAction(
    val label: String,
    val onClick: () -> Unit,
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

/** 整页骨架：宣纸底 + 顶栏 + 内容列。 */
@Composable
fun ToolPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<ToolAction> = emptyList(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
    ) {
        Column(Modifier.fillMaxSize()) {
            ToolTopBar(title = title, onBack = onBack, actions = actions)
            content()
        }
    }
}

/** 顶栏：返回箭头（← 自绘文字）+ 衬线标题 + 右侧动作；触达区均 ≥ 48dp。 */
@Composable
fun ToolTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<ToolAction> = emptyList(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolGlyphButton(label = "←", contentDescription = "back", onClick = onBack)
        Spacer(Modifier.width(4.dp))
        androidx.compose.foundation.text.BasicText(
            text = title,
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 17.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.weight(1f),
        )
        actions.forEach { action ->
            ToolActionButton(action)
        }
    }
}

/** 文字形动作按钮：最小 48dp 宽高，禁用态压 30% 透明度。 */
@Composable
fun ToolActionButton(action: ToolAction, modifier: Modifier = Modifier) {
    val gesture = rememberInkGesture(enabled = action.enabled, onClick = action.onClick)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .then(gesture.gestureModifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = action.label,
            style = TextStyle(
                color = when {
                    !action.enabled -> ComponentTokens.textTertiary
                    action.danger -> ComponentTokens.textAccent
                    else -> ComponentTokens.textSecondary
                },
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
            ),
        )
    }
}

/** 纯字形按钮（返回 / 关闭等），48dp 方形触达区。 */
@Composable
fun ToolGlyphButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val gesture = rememberInkGesture(enabled = enabled, onClick = onClick)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .then(gesture.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) ComponentTokens.textPrimary else ComponentTokens.textTertiary,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/** 墨触开关：轨道 + 弹簧滑块；不用 Material Switch。 */
@Composable
fun InkSwitch(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = ComponentTokens.revealSpring<androidx.compose.ui.unit.Dp>(),
        label = "inkSwitchKnob",
    )
    val trackColor = when {
        !enabled -> ComponentTokens.textTertiary
        checked -> ComponentTokens.brandGold
        else -> ComponentTokens.colorDivider
    }
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onToggle(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset)
                .size(26.dp)
                .clip(RoundedCornerShape(50))
                .background(ComponentTokens.bgPage),
        )
    }
}

/** 标签 + 输入框：绢帛底、云灰细线、最小 48dp 高；多行时上限随行数增长。 */
@Composable
fun ToolTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    multiLine: Boolean = false,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            androidx.compose.foundation.text.BasicText(
                text = label,
                style = TextStyle(
                    color = ComponentTokens.stoneBlue,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = !multiLine,
            textStyle = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(ComponentTokens.textAccent),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(ComponentTokens.cardRadius))
                        .background(ComponentTokens.bgSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (value.isEmpty() && placeholder.isNotBlank()) {
                        androidx.compose.foundation.text.BasicText(
                            text = placeholder,
                            style = TextStyle(
                                color = ComponentTokens.textTertiary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Serif,
                            ),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** 分组小标题（石青 14sp），用于编辑页分区。 */
@Composable
fun ToolSectionLabel(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = TextStyle(
            color = ComponentTokens.stoneBlue,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
        ),
        modifier = modifier.padding(vertical = 8.dp),
    )
}

/** 列表行容器：≥ 48dp 高、墨触反馈、可选选中朱砂描边；支持长按回调。 */
@Composable
fun ToolRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        val gesture = rememberInkGesture(enabled = enabled && onClick != null, onClick = onClick ?: {})
        gesture.gestureModifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ComponentTokens.cardRadius))
            .background(
                if (selected) ComponentTokens.colorGlow else ComponentTokens.bgSurface,
            )
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content()
    }
}

/** 列表竖排间距工具：条目之间统一 8dp。 */
val toolListArrangement: Arrangement.Vertical
    get() = Arrangement.spacedBy(8.dp)
