package io.legado.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 落文组件库 · Compose 侧 token 收口。
 *
 * 只把 [ComponentPalette] 的纯数值转成 Compose 类型；**不包裹 MaterialTheme、不取 Material
 * colorScheme**，所有组件颜色显式从这里传入。字体契约：正文 / 标题走衬线，编号走等宽兜底，
 * 真正的 Noto Serif SC 子集由字体模块落地。
 */
object ComponentTokens {

    // ── 12 色 + 石青辅助色 ──

    val bgPage: Color = Color(ComponentPalette.BG_PAGE)
    val bgSurface: Color = Color(ComponentPalette.BG_SURFACE)
    val bgOverlay: Color = Color(ComponentPalette.BG_OVERLAY)
    val textPrimary: Color = Color(ComponentPalette.TEXT_PRIMARY)
    val textSecondary: Color = Color(ComponentPalette.TEXT_SECONDARY)
    val textTertiary: Color = Color(ComponentPalette.TEXT_TERTIARY)
    val textAccent: Color = Color(ComponentPalette.TEXT_ACCENT)
    val brandGold: Color = Color(ComponentPalette.BRAND_GOLD)
    val brandCopper: Color = Color(ComponentPalette.BRAND_COPPER)
    val colorLink: Color = Color(ComponentPalette.COLOR_LINK)
    val colorDivider: Color = Color(ComponentPalette.COLOR_DIVIDER)
    val colorGlow: Color = Color(ComponentPalette.COLOR_GLOW)
    val stoneBlue: Color = Color(ComponentPalette.STONE_BLUE)

    // ── 缓动 ──

    val cardEnterEasing: CubicBezierEasing = CubicBezierEasing(
        ComponentPalette.CARD_ENTER_EASING[0],
        ComponentPalette.CARD_ENTER_EASING[1],
        ComponentPalette.CARD_ENTER_EASING[2],
        ComponentPalette.CARD_ENTER_EASING[3],
    )

    val bubbleEasing: CubicBezierEasing = CubicBezierEasing(
        ComponentPalette.BUBBLE_EASING[0],
        ComponentPalette.BUBBLE_EASING[1],
        ComponentPalette.BUBBLE_EASING[2],
        ComponentPalette.BUBBLE_EASING[3],
    )

    val pageTurnEasing: CubicBezierEasing = CubicBezierEasing(
        ComponentPalette.PAGE_TURN_EASING[0],
        ComponentPalette.PAGE_TURN_EASING[1],
        ComponentPalette.PAGE_TURN_EASING[2],
        ComponentPalette.PAGE_TURN_EASING[3],
    )

    val maskEasing: CubicBezierEasing = CubicBezierEasing(
        ComponentPalette.MASK_EASING[0],
        ComponentPalette.MASK_EASING[1],
        ComponentPalette.MASK_EASING[2],
        ComponentPalette.MASK_EASING[3],
    )

    // ── 弹簧规格 ──

    fun <T> bubbleSpring() = spring<T>(
        dampingRatio = ComponentPalette.BUBBLE_SPRING_DAMPING,
        stiffness = ComponentPalette.BUBBLE_SPRING_STIFFNESS,
    )

    fun <T> sheetSpring() = spring<T>(
        dampingRatio = ComponentPalette.SHEET_SPRING_DAMPING,
        stiffness = ComponentPalette.SHEET_SPRING_STIFFNESS,
    )

    fun <T> revealSpring() = spring<T>(
        dampingRatio = ComponentPalette.REVEAL_SPRING_DAMPING,
        stiffness = ComponentPalette.REVEAL_SPRING_STIFFNESS,
    )

    // ── 尺寸 ──

    val cardRadius: Dp = ComponentPalette.CARD_RADIUS_DP.dp
    val buttonRadius: Dp = ComponentPalette.BUTTON_RADIUS_DP.dp
    val bubbleRadius: Dp = ComponentPalette.BUBBLE_RADIUS_DP.dp
    val sheetRadius: Dp = ComponentPalette.SHEET_RADIUS_DP.dp
    val sheetGrabberWidth: Dp = ComponentPalette.SHEET_GRABBER_WIDTH_DP.dp
    val sheetGrabberHeight: Dp = ComponentPalette.SHEET_GRABBER_HEIGHT_DP.dp
    val bubbleGap: Dp = ComponentPalette.BUBBLE_GAP_DP.dp
    val bubbleArrowWidth: Dp = ComponentPalette.BUBBLE_ARROW_WIDTH_DP.dp
    val bubbleArrowHeight: Dp = ComponentPalette.BUBBLE_ARROW_HEIGHT_DP.dp

    /** 墨触朱砂覆盖：悬浮 8% / 按下 16% */
    fun accentOverlay(pressed: Boolean, hovered: Boolean): Float = when {
        pressed -> ComponentPalette.PRESS_DOWN_ACCENT_ALPHA
        hovered -> ComponentPalette.PRESS_HOVER_ACCENT_ALPHA
        else -> 0f
    }

    /** 禁用态整体透明度 30% */
    val disabledAlpha: Float = ComponentPalette.DISABLED_OPACITY

    /** 弹簧默认刚度常量（暴露给需要 Spring.StiffnessMedium 对照的场景） */
    val springStiffnessMedium: Float = Spring.StiffnessMedium
}
