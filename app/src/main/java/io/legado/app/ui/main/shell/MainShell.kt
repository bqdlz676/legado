package io.legado.app.ui.main.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.motion.contract.motionDurationMs
import io.legado.app.ui.motion.contract.springSpecFor

/**
 * B2 主壳：三槽导航 + 磨砂底栏 + 槽位转场动画。
 *
 * 〔接线口径〕
 *  - 槽位顺序 / 数量由 [ShellTab.defaultOrder] 定死；可见槽位可按偏好过滤，但不增第四槽；
 *  - 转场时长与弹簧参数回 [motionDurationMs] / [springSpecFor]（PANEL 档，350ms），
 *    壳内不自造 stiffness / damping / tween 时长；reduce-motion 下时长归零、弹簧拉满；
 *  - 返回决策走 [ShellNavigation]：非书架回书架，书架回调 [onExit]（双击退出在宿主 Activity 判定）。
 *
 * @param state 壳态持有（当前槽位 / 幂等切换）
 * @param visibleTabs 可见槽位列表，默认三槽全显
 * @param badgeCount 未读角标落点计数（书架槽）
 * @param reduceMotion 系统 reduce-motion 开关
 * @param onExit 书架槽按返回时的退出回调（宿主做双击间隔判定）
 */
@Composable
fun MainShell(
    state: MainShellState,
    modifier: Modifier = Modifier,
    visibleTabs: List<ShellTab> = ShellTab.defaultOrder,
    badgeCount: Int = 0,
    reduceMotion: Boolean = false,
    onExit: () -> Unit,
) {
    BackHandler {
        when (ShellNavigation.backDecision(state.selectedTab)) {
            ShellBackDecision.NAVIGATE_BOOKSHELF ->
                state.select(ShellTab.BOOKSHELF)
            ShellBackDecision.EXIT -> onExit()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ShellPageTransition(
                current = state.selectedTab,
                reduceMotion = reduceMotion,
            )
        }
        ShellBottomBar(
            tabs = visibleTabs,
            selected = state.selectedTab,
            onSelect = state::select,
            badgeCount = badgeCount,
            reduceMotion = reduceMotion,
        )
    }
}

/**
 * 槽位转场：淡入淡出 + 横向微滑；弹簧 / 时长回 MotionSpec 单源。
 * composition 内不做重活，仅目标槽位切换时触发动画。
 */
@Composable
private fun ShellPageTransition(
    current: ShellTab,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val duration = motionDurationMs("PANEL", reduceMotion)
    val spec = springSpecFor("PANEL", reduceMotion)
    val slideSpring = spring<IntOffset>(
        dampingRatio = spec.dampingRatio,
        stiffness = spec.stiffness,
    )
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            (fadeIn(tween(duration)) + slideInHorizontally(slideSpring)) togetherWith
                (fadeOut(tween(duration)) + slideOutHorizontally(slideSpring))
        },
        label = "mainShellPage",
        modifier = modifier,
    ) { tab ->
        ShellPageSlot(tab, reduceMotion = reduceMotion)
    }
}
