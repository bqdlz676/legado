package io.legado.app.ui.main.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.rememberInkGesture
import io.legado.app.ui.widget.InkTimelineView

/**
 * 落文 · 详情半屏 Compose 内容槽（B1 重做）。
 *
 * 视觉口径对齐统一组件 [io.legado.app.ui.components.InkHalfSheet]：分区标题走石青 13sp 衬线，
 * 正文走墨色 16sp 衬线 / 26sp 行高，副题走茶褐 14sp；颜色全部取自 [ComponentTokens]，
 * 不引 MaterialTheme / ripple。文字可长按复制（对齐旧 body `setTextIsSelectable(true)`）。
 *
 * 纪年时间轴 [InkTimelineView] 是自绘交互 View（拖动吸附整十年 + 金色粒子环），
 * 经 [AndroidView] 桥接进 Compose；不重写为 Compose Canvas——其手势与帧循环属既有引擎件，
 * 重写属扩大战场（R57）。
 */
@Composable
fun DetailSheetContent(
    state: DetailSheetState,
    onTimelineYear: (Int) -> Unit,
    onAction: (DetailAction) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.subtitle.isNotBlank()) {
            BasicText(
                text = state.subtitle,
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 22.sp,
                ),
            )
        }

        state.sections.forEach { section ->
            when (section.kind) {
                DetailSectionKind.TIMELINE -> {
                    if (section.title.isNotBlank()) {
                        BasicText(
                            text = section.title,
                            style = TextStyle(
                                color = ComponentTokens.stoneBlue,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                            ),
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    TimelineBridge(
                        year = state.timelineYear,
                        onYearChanged = onTimelineYear,
                    )
                }

                DetailSectionKind.PLAIN -> SelectionContainer {
                    Column(Modifier.fillMaxWidth()) {
                        if (section.title.isNotBlank()) {
                            BasicText(
                                text = section.title,
                                style = TextStyle(
                                    color = ComponentTokens.stoneBlue,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif,
                                ),
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        section.lines.forEach { line ->
                            BasicText(
                                text = line,
                                style = TextStyle(
                                    color = ComponentTokens.textPrimary,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Serif,
                                    lineHeight = 26.sp,
                                ),
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        state.action?.let { action ->
            ActionLink(label = action.label, onClick = { onAction(action) })
        }
    }
}

/** 纪年时间轴桥：factory 建一次，update 同步年份与回调；回调回传宿主重开对应年份半屏。 */
@Composable
private fun TimelineBridge(year: Int?, onYearChanged: (Int) -> Unit) {
    AndroidView(
        factory = { ctx ->
            InkTimelineView(ctx).also { view -> view.onYearChanged = onYearChanged }
        },
        update = { view ->
            view.year = year ?: 0
            view.onYearChanged = onYearChanged
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
    )
}

/** 底部全屏动作行：古铜色 13sp 衬线，墨触手势无 ripple；点击后由宿主决定关闭半屏。 */
@Composable
private fun ActionLink(label: String, onClick: () -> Unit) {
    val gesture = rememberInkGesture(enabled = true, onClick = onClick)
    BasicText(
        text = label,
        style = TextStyle(
            color = ComponentTokens.brandCopper,
            fontSize = 13.sp,
            fontFamily = FontFamily.Serif,
        ),
        modifier = Modifier
            .padding(top = 18.dp, bottom = 4.dp)
            .then(gesture.gestureModifier),
    )
}
