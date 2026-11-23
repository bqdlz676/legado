package io.legado.app.ui.main.chrono

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 纪年页毛玻璃底栏 + 升起面板（微交互契约第 3 项）。
 *
 *  - 底栏 338×64dp 胶囊（圆角 32dp），背面地图实时模糊 18px、亮度压到 0.62，
 *    叠深色半透明 + 白色渐变，1px 浅边 + 顶部内高光；
 *  - 点日期图标 → 400dp 毛玻璃面板升起（干支时辰对照），面板同地图实时模糊，底栏上移让位；
 *  - 下滑超过 1/3 或快速下甩收起，否则弹回；落回原位带 10dp 过冲回弹；
 *  - 实时模糊源依赖沙盘模块，未就绪走 [ChronoBackdropRegistry] 占位源，机制先跑通。
 */
@Composable
fun ChronoBottomDock(
    state: ChronoViewState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val panelHeightPx = with(density) { ChronoTokens.PANEL_HEIGHT_DP.dp.toPx() }
    val barGapPx = panelHeightPx + with(density) { 12.dp.toPx() }

    val machine = remember { PanelLiftStateMachine(panelHeightPx = panelHeightPx) }
    val progress = remember { Animatable(0f) }
    val overshoot = remember { Animatable(0f) }

    LaunchedEffect(machine.phase) {
        when (machine.phase) {
            PanelPhase.RISING -> progress.animateTo(
                1f,
                spring(
                    dampingRatio = ChronoTokens.SPRING_DAMPING,
                    stiffness = ChronoTokens.SPRING_STIFFNESS,
                ),
            )
            PanelPhase.FALLING -> {
                progress.animateTo(
                    0f,
                    spring(
                        dampingRatio = ChronoTokens.SPRING_DAMPING,
                        stiffness = ChronoTokens.SPRING_STIFFNESS,
                    ),
                )
                machine.onSettled()
                if (machine.overshootPx > 0f) {
                    overshoot.snapTo(0f)
                    overshoot.animateTo(
                        machine.overshootPx,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f),
                    )
                    overshoot.animateTo(0f, spring(stiffness = 300f))
                    machine.consumeOvershoot()
                }
            }
            PanelPhase.OPEN, PanelPhase.CLOSED -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 遮罩：面板升起时压暗背后页面
        if (progress.value > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ChronoTokens.bgOverlay.copy(alpha = 0.45f * progress.value))
                    .pointerInput(machine) {
                        detectTapGestures { machine.close() }
                    },
            )
        }

        // 升起面板
        ChronoGlassPanel(
            state = state,
            progress = progress.value,
            panelHeightPx = panelHeightPx,
            onDrag = { delta -> machine.onDrag(delta) },
            onDragStart = { machine.beginDrag() },
            onRelease = { velocityPxPerMs -> machine.release(velocityPxPerMs) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 底栏（随面板上移让位；关闭落回叠加过冲）
        ChronoGlassBar(
            state = state,
            liftPx = machine.barLiftPx(barGapPx) - overshoot.value,
            onClick = { machine.toggle() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// —— 底栏胶囊 ——

@Composable
private fun ChronoGlassBar(
    state: ChronoViewState,
    liftPx: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val pressAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) ChronoTokens.PRESS_ALPHA else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, visibilityThreshold = 0.01f),
        label = "bar-press",
    )

    Box(
        modifier
            .padding(bottom = 18.dp)
            .offset(y = with(LocalDensity.current) { (-liftPx).toDp() })
            .width(ChronoTokens.BAR_WIDTH_DP.dp)
            .height(ChronoTokens.BAR_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(ChronoTokens.BAR_RADIUS_DP.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick()
                    },
                )
            },
    ) {
        GlassBackdrop(Modifier.fillMaxSize())

        // 深色半透明 + 顶部白色渐变
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            ChronoTokens.bgOverlay.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )
        // 朱砂按压泛色 8%，120ms 回落（spring 刚度对齐微交互时长）
        Box(
            Modifier
                .fillMaxSize()
                .background(ChronoTokens.textAccent.copy(alpha = pressAlpha.value)),
        )

        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DateIcon(Modifier.size(22.dp))
            BasicText(
                text = "${state.ganzhi} · 日 ${state.dayGanzhi}",
                style = TextStyle(
                    color = ChronoTokens.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                ),
            )
            BasicText(
                text = state.yearLabel.ifEmpty { "年代未载" },
                style = TextStyle(color = ChronoTokens.textTertiary, fontSize = 11.sp),
            )
        }

        // 1px 浅边 + 顶部内高光
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(ChronoTokens.BAR_RADIUS_DP.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(ChronoTokens.BAR_RADIUS_DP.dp))
                .drawBehind {
                    drawRect(
                        color = Color.White.copy(alpha = 0.18f),
                        size = Size(size.width, 1f),
                    )
                },
        )
    }
}

@Composable
private fun DateIcon(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = ChronoTokens.brandGold
        drawCircle(stroke, radius = size.minDimension * 0.36f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))
        drawLine(
            color = stroke,
            start = Offset(size.width * 0.28f, size.height * 0.38f),
            end = Offset(size.width * 0.72f, size.height * 0.38f),
            strokeWidth = 1.2f,
        )
    }
}

// —— 升起面板 ——

@Composable
private fun ChronoGlassPanel(
    state: ChronoViewState,
    progress: Float,
    panelHeightPx: Float,
    onDrag: (deltaDownPx: Float) -> Unit,
    onDragStart: () -> Unit,
    onRelease: (velocityPxPerMs: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0.01f) return
    val density = LocalDensity.current
    // 该 Compose 档未暴露 VelocityTracker；用相邻两帧位移/时间差估算 px/ms 松手速度
    var lastDragTime by remember { androidx.compose.runtime.mutableStateOf(0L) }
    var releaseVelocity by remember { androidx.compose.runtime.mutableStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            .height(with(density) { panelHeightPx.toDp() })
            .offset(y = with(density) { ((1f - progress) * panelHeightPx).toDp() })
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        lastDragTime = 0L
                        releaseVelocity = 0f
                        onDragStart()
                    },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        val now = change.uptimeMillis
                        if (lastDragTime != 0L) {
                            val dt = (now - lastDragTime).coerceAtLeast(1L)
                            releaseVelocity = delta.y / dt
                        }
                        lastDragTime = now
                        onDrag(delta.y)
                    },
                    onDragEnd = { onRelease(releaseVelocity) },
                    onDragCancel = { onRelease(0f) },
                )
            },
    ) {
        GlassBackdrop(Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(ChronoTokens.bgOverlay.copy(alpha = 0.5f)),
        )

        // 拖拽指示金杠
        Box(
            Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ChronoTokens.brandGold.copy(alpha = 0.6f)),
        )

        BasicText(
            text = "干支时辰对照 · ${state.dayGanzhi}日",
            style = TextStyle(
                color = ChronoTokens.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 10.dp),
        )

        val table = remember(state.dayGanzhi) { ChronoPresenter.shichenTable(state.dayGanzhi) }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(table) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ChronoTokens.colorDivider)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        "${row.hourGanzhi}${row.zhi}时",
                        style = TextStyle(color = ChronoTokens.textPrimary, fontSize = 14.sp, fontFamily = FontFamily.Serif),
                    )
                    BasicText(
                        row.range,
                        style = TextStyle(color = ChronoTokens.textTertiary, fontSize = 12.sp),
                    )
                }
            }
        }
    }
}

// —— 共享背面：实时模糊源占位机制 ——

@Composable
private fun GlassBackdrop(modifier: Modifier) {
    val snapshot = remember(ChronoBackdropRegistry.current) {
        runCatching { ChronoBackdropRegistry.current.snapshot() }.getOrNull()
    }
    Box(modifier.blur(ChronoTokens.BAR_BLUR_PX.dp)) {
        if (snapshot != null) {
            Image(
                bitmap = snapshot.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            // 亮度压到 0.62：叠一层 38% 深色
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - ChronoTokens.BAR_BRIGHTNESS)))
        } else {
            // 占位暗底暖光渐变：沙盘实时源接入前的兜底，机制（模糊/压暗/叠色）一致
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            ChronoTokens.brandCopper.copy(alpha = 0.35f),
                            ChronoTokens.bgDeep.copy(alpha = 0.85f),
                        ),
                    ),
                ),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - ChronoTokens.BAR_BRIGHTNESS)))
        }
    }
}
