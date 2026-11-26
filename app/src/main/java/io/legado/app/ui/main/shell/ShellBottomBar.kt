package io.legado.app.ui.main.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.ComponentPalette
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkDivider
import io.legado.app.ui.components.InkDividerVariant
import io.legado.app.ui.components.rememberInkGesture

/**
 * B2 主壳磨砂底栏：三槽等宽排列，墨触手势（无 Material 涟漪），暖金顶线，导航栏内边距收口。
 *
 * 〔颜色 / 几何口径〕全部取 [ComponentTokens] / [ShellFrost]，不在底栏内联色值；
 * 选中暖金（brandGold）、未选中暗铜（brandCopper）；按压泛朱砂覆盖由 [rememberInkGesture] 快照解析。
 *
 * @param tabs 可见槽位（顺序固定 书架 → 发现 → 设置）
 * @param selected 当前选中槽位
 * @param onSelect 选中槽位回调（幂等判定在状态层，重复点当前槽不触发切换）
 * @param badgeCount 未读数角标落点（仅书架槽；0 不显示）
 * @param reduceMotion 系统 reduce-motion：按压泛色回落时长同步归零
 */
@Composable
fun ShellBottomBar(
    tabs: List<ShellTab>,
    selected: ShellTab,
    onSelect: (ShellTab) -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    reduceMotion: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shellFrostSurface()
            .navigationBarsPadding(),
    ) {
        // 暖金顶线：槽位区与内容区的视觉分界，线色取 token 金线档。
        InkDivider(variant = InkDividerVariant.GOLD_LINE, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ComponentTokens.bubbleGap),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                ShellTabItem(
                    tab = tab,
                    selected = tab == selected,
                    badgeCount = if (tab == ShellTab.BOOKSHELF) badgeCount else 0,
                    reduceMotion = reduceMotion,
                    // B8 音效位：Tab 切换 15ms 金石相叩（切片-23:23）；后端未落地前不接空回调。
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ShellTabItem(
    tab: ShellTab,
    selected: Boolean,
    badgeCount: Int,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gesture = rememberInkGesture(enabled = true, onClick = onClick)
    val overlayAlpha by animateFloatAsState(
        targetValue = gesture.interaction.accentOverlayAlpha,
        animationSpec = tween(
            durationMillis = ComponentPalette.DURATION_MICRO.takeUnless { reduceMotion } ?: 0,
            easing = ComponentTokens.cardEnterEasing,
        ),
        label = "shellTabOverlay",
    )
    // R7 双色令牌：选中暖金 brandGold、未选中暗铜 brandCopper（切片-24:8）；不内联 hex、不被朱砂 / 茶褐覆盖。
    val contentColor = if (selected) ComponentTokens.brandGold else ComponentTokens.brandCopper
    val itemShape = RoundedCornerShape(ComponentTokens.buttonRadius)

    Box(
        modifier = modifier
            .then(gesture.gestureModifier)
            .clip(itemShape)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Image(
                    painter = painterResource(if (selected) tab.iconOnRes else tab.iconOffRes),
                    contentDescription = stringResource(tab.labelRes),
                    colorFilter = ColorFilter.tint(contentColor),
                    modifier = Modifier.size(24.dp),
                )
                if (badgeCount > 0) {
                    BadgeDot(count = badgeCount)
                }
            }
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.text.BasicText(
                text = stringResource(tab.labelRes),
                style = TextStyle(
                    color = contentColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
        // 朱砂泛色覆盖层：按压 / 悬停快照透明度，120ms 回落（reduce-motion 归零）。
        if (overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(itemShape)
                    .background(ComponentTokens.textAccent.copy(alpha = overlayAlpha)),
            )
        }
    }
}

/** 未读角标：朱砂小圆点 + 计数；计数归数据层推送，壳内只承担落点显示。 */
@Composable
private fun BadgeDot(count: Int) {
    Box(
        modifier = Modifier
            .width(16.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(50))
            .background(ComponentTokens.textAccent),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = count.toString(),
            style = TextStyle(
                color = ComponentTokens.bgPage,
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif,
            ),
        )
    }
}
