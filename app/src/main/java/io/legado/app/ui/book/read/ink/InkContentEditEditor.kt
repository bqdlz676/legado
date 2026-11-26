package io.legado.app.ui.book.read.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.book.read.ContentEditProjection
import io.legado.app.ui.components.ComponentTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 内容编辑面板 · 正文编辑区与位置进度条。
 *
 * 〔职责边界〕本件只装正文录入（[TextFieldValue] 选区保留、命中段暖金底纹标注、
 * 纯文本模式可见串回填映射）与编辑器滚动比例进度条；纯文本映射口径走
 * [InkContentEditRules.projectEdit]，本件不另造前后缀对齐算法。
 * 切分理由：正文录入与滚动进度共用同一 [ScrollState]，内聚于一簇；
 * 与标题 / 搜索输入、底部动作区无状态耦合，独立成件便于单独阅读与后续维护。
 */

/**
 * 正文编辑区：纯文本模式经 [ContentEditProjection] 隐藏图片标记，改动回填保留隐藏标记的草稿。
 * 命中高亮：非纯文本模式下，搜索命中段叠暖金 35% 底纹（对照旧版 BackgroundColorSpan 35% 透明度）；
 * 纯文本模式可见串与原文偏移不一致，暂不叠高亮，避免错位标色。
 */
@Composable
internal fun EditorBody(state: InkContentEditState, scrollState: ScrollState) {
    val projection = remember(state.content, state.plainText) {
        if (state.plainText) ContentEditProjection(state.content) else null
    }
    val displayed = projection?.text ?: state.content

    val annotated = remember(displayed, state.matches, state.plainText) {
        if (state.plainText || state.matches.isEmpty()) {
            AnnotatedString(displayed)
        } else {
            buildAnnotatedString {
                append(displayed)
                state.matches.forEach { range ->
                    val end = (range.last + 1).coerceAtMost(displayed.length)
                    if (range.first in 0..end) {
                        addStyle(
                            SpanStyle(background = ComponentTokens.brandGold.copy(alpha = 0.35f)),
                            range.first,
                            end,
                        )
                    }
                }
            }
        }
    }

    // 本地持有 TextFieldValue：选区随输入保留；正文外部变化（加载 / 切纯文本 / 命中刷新）时
    // 回填注解文本并保留当前选区，避免每次重组把光标归零到 0。
    var fieldValue by remember { mutableStateOf(TextFieldValue(annotated)) }
    LaunchedEffect(annotated) {
        fieldValue = TextFieldValue(
            annotated,
            selection = fieldValue.selection,
            composition = fieldValue.composition,
        )
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { field ->
            fieldValue = field
            val newDisplay = field.text
            val proj = projection
            if (proj == null) {
                state.content = newDisplay
            } else {
                // 映射失败兜底：前后缀对不齐时按可见串整段回写；隐藏标记可能丢失，登记为未验证项。
                val mapped = InkContentEditRules.projectEdit(proj, displayed, newDisplay)
                state.content = mapped ?: newDisplay
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        textStyle = TextStyle(
            color = ComponentTokens.textPrimary,
            fontSize = 16.sp,
            fontFamily = FontFamily.Serif,
            lineHeight = 26.sp,
        ),
        cursorBrush = SolidColor(ComponentTokens.brandGold),
    )
}

/** 位置进度条：映射编辑器滚动比例；拖动 / 点按回卷到对应位置。 */
@Composable
internal fun PositionBar(scrollState: ScrollState) {
    // ScrollState.value 仅公开读、写 setter 私有：定位走 suspend scrollTo，拖拽走公开
    // dispatchRawDelta（内部按 0..maxValue 夹取），不反射、不 @Suppress、不砍拖拽能力。
    val scope = rememberCoroutineScope()
    val fraction = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue
    } else 0f

    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .pointerInput(scrollState.maxValue) {
                    detectTapGestures { offset ->
                        if (scrollState.maxValue > 0) {
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            val target = (f * scrollState.maxValue).roundToInt()
                            scope.launch { scrollState.scrollTo(target) }
                        }
                    }
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        // delta 单位即滚动像素，与 ScrollState.value 同口径，不再做 dp 换算；
                        // dispatchRawDelta 是公开写入入口，消费掉的增量由它内部夹取回卷。
                        if (scrollState.maxValue > 0) {
                            scrollState.dispatchRawDelta(delta)
                        }
                    },
                    orientation = Orientation.Horizontal,
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    color = ComponentTokens.colorDivider,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = ComponentTokens.brandGold,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width * fraction, size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}
