package io.legado.app.ui.components.inkslate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * U7 画板画布（切片-12 §8.1 / §8.3）。
 *
 * - 底图层（BASE）先绘，墨迹层（INK）后绘；
 * - 画笔 / 直线 / 箭头 / 矩形 / 圆 / 橡皮全部 Canvas 自绘，文字笔迹以覆盖层文本落点渲染；
 * - 压感模拟：采样速度换算压感（慢速粗、快速细），指针自带压力时优先采用；
 * - 双击画布 = 撤销（§8.3）；
 * - 不包裹 MaterialTheme、不用 Material ripple、颜色只走 [ComponentTokens] / [InkPreset]。
 *
 * PNG 位图编码与 WebView 预热 / 复用属宿主层职责（§8.3 由 Application 侧 WebView 池落地），
 * 本组件只产出笔迹模型与 SVG 标记（[InkSlateSvg]）。
 */
@Composable
fun InkSlateCanvas(
    state: InkSlateState,
    onState: (InkSlateState) -> Unit,
    modifier: Modifier = Modifier,
    onTextRequest: (StrokePoint) -> Unit = {},
) {
    val currentState = rememberUpdatedState(state)

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    val latest = currentState.value
                    if (latest.canvasWidth != size.width.toFloat() ||
                        latest.canvasHeight != size.height.toFloat()
                    ) {
                        onState(latest.setCanvasSize(size.width.toFloat(), size.height.toFloat()))
                    }
                }
                .pointerInput(state.tool, state.layer) {
                    var lastPoint: Offset? = null
                    var lastTapMs = 0L

                    awaitPointerEventScope {
                        loop@ while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val position = change.position
                            val now = change.uptimeMillis
                            val latest = currentState.value

                            when (event.type) {
                                PointerEventType.Press -> {
                                    val doubleTap = now - lastTapMs <= InkSlatePalette.DOUBLE_TAP_MS
                                    lastTapMs = now
                                    if (doubleTap) {
                                        // 双击画布 = 撤销
                                        onState(latest.undo())
                                        lastPoint = null
                                        change.consume()
                                        continue@loop
                                    }
                                    lastPoint = position
                                    when (latest.tool) {
                                        InkTool.ERASER -> onState(latest.eraseAt(position.x, position.y))
                                        InkTool.TEXT -> onTextRequest(
                                            StrokePoint(position.x, position.y, 1f),
                                        )
                                        else -> {
                                            val pressure = resolvePressure(change.pressure, 0f, 0f)
                                            onState(latest.beginStroke(position.x, position.y, pressure))
                                        }
                                    }
                                    change.consume()
                                }

                                PointerEventType.Move -> {
                                    val prev = lastPoint
                                    val velocity = if (prev != null) hypot(
                                        position.x - prev.x,
                                        position.y - prev.y,
                                    ) else 0f
                                    when (latest.tool) {
                                        InkTool.ERASER -> onState(
                                            latest.eraseAt(position.x, position.y),
                                        )
                                        InkTool.TEXT -> Unit
                                        else -> {
                                            val pressure = resolvePressure(
                                                change.pressure, velocity, velocity,
                                            )
                                            onState(
                                                latest.extendStroke(position.x, position.y, pressure),
                                            )
                                        }
                                    }
                                    lastPoint = position
                                    change.consume()
                                }

                                PointerEventType.Release -> {
                                    if (latest.tool != InkTool.ERASER && latest.tool != InkTool.TEXT) {
                                        onState(latest.finishStroke())
                                    }
                                    lastPoint = null
                                    change.consume()
                                }

                                else -> Unit
                            }
                        }
                    }
                },
        ) {
            drawSlate(state)
        }

        // 文字笔迹覆盖层：按落点渲染文本
        state.strokes.filter { it.tool == InkTool.TEXT && !it.text.isNullOrBlank() }
            .forEach { stroke ->
                val point = stroke.points.firstOrNull() ?: return@forEach
                androidx.compose.foundation.text.BasicText(
                    text = stroke.text!!,
                    style = TextStyle(
                        color = Color(stroke.color),
                        fontSize = (stroke.width * 3).sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                point.x.toInt(),
                                point.y.toInt(),
                            )
                        },
                )
            }
    }
}

/** 压感收口：指针压力有效（>0）时采用，否则按采样速度模拟。 */
private fun resolvePressure(pointerPressure: Float, velocity: Float, reference: Float): Float {
    if (pointerPressure in 0.05f..1f) return pointerPressure.coerceIn(
        InkPressure.MIN_PRESSURE, InkPressure.MAX_PRESSURE,
    )
    return InkPressure.simulate(velocity, reference)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlate(
    state: InkSlateState,
) {
    // 底图层先绘，墨迹层后绘
    (InkLayer.entries).forEach { layer ->
        state.strokes.filter { it.layer == layer }.forEach { drawStroke(it) }
        state.currentStroke?.takeIf { it.layer == layer }?.let { drawStroke(it) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: Stroke) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color)
    when (stroke.tool) {
        InkTool.LINE, InkTool.ARROW -> {
            val a = stroke.points.first()
            val b = stroke.points.last()
            drawLine(
                color = color,
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = stroke.width,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            if (stroke.tool == InkTool.ARROW) drawArrowHead(a, b, color, stroke.width)
        }

        InkTool.RECT -> {
            val a = stroke.points.first()
            val b = stroke.points.last()
            val path = Path()
            path.moveTo(a.x, a.y)
            path.lineTo(b.x, a.y)
            path.lineTo(b.x, b.y)
            path.lineTo(a.x, b.y)
            path.close()
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke.width))
        }

        InkTool.CIRCLE -> {
            val a = stroke.points.first()
            val b = stroke.points.last()
            val cx = (a.x + b.x) / 2f
            val cy = (a.y + b.y) / 2f
            val r = hypot(b.x - a.x, b.y - a.y) / 2f
            drawCircle(color, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke.width))
        }

        InkTool.BRUSH -> drawBrush(stroke, color)

        InkTool.ERASER, InkTool.TEXT -> Unit
    }
}

/** 画笔：折线串联采样点，点径随压感起伏；线段补接避免断点。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBrush(
    stroke: Stroke,
    color: Color,
) {
    val points = stroke.points
    if (points.size == 1) {
        val p = points.first()
        drawCircle(color, radius = stroke.width * p.pressure / 2f, center = Offset(p.x, p.y))
        return
    }
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val width = stroke.width * ((a.pressure + b.pressure) / 2f)
        drawLine(
            color = color,
            start = Offset(a.x, a.y),
            end = Offset(b.x, b.y),
            strokeWidth = width,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    a: StrokePoint,
    b: StrokePoint,
    color: Color,
    width: Float,
) {
    val angle = atan2(b.y - a.y, b.x - a.x)
    val head = 10f + width * 2f
    val left = angle + Math.PI * 5 / 6
    val right = angle - Math.PI * 5 / 6
    val lx = b.x + head * cos(left).toFloat()
    val ly = b.y + head * sin(left).toFloat()
    val rx = b.x + head * cos(right).toFloat()
    val ry = b.y + head * sin(right).toFloat()
    drawLine(color, Offset(lx, ly), Offset(b.x, b.y), width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, Offset(rx, ry), Offset(b.x, b.y), width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}
