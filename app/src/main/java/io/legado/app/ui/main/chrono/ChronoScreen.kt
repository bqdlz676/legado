package io.legado.app.ui.main.chrono

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.chrono.Chronology
import java.time.LocalDate

/**
 * 落文 · 纪年时间轴页（ui/main/chrono 独占）。
 *
 * 纯展示层：纪年换算走 core [Chronology]，本层只做对照行渲染、左右切年与边界兜底。
 * 四层口径：
 *  - 材质：gradient mesh 暗底暖光渐变；
 *  - 动效：scroll snap 年份吸附切换、spring 年份数字金粉粒子重组；
 *  - 空间：particle field 金色粒子环 + 流动粒子数字；
 *  - 组件：Timeline（政权行 + 年份节点 + 左右箭头）+ 毛玻璃底栏。
 *
 * 空数据 / 年份缺失不空白：保留干支与当日时辰，展示「世系未载」兜底行。
 *
 * @param initialYear 起始公元年；null 取当日公历
 */
@Composable
fun ChronoScreenHost(
    initialYear: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var year by remember { mutableStateOf<Int?>(initialYear ?: today.year) }

    LaunchedEffect(Unit) {
        runCatching { Chronology.ensureLoaded(context) }
    }

    val rows = remember(year) {
        runCatching { Chronology.timelineRows(year ?: today.year) }.getOrDefault(emptyList())
    }
    val state = remember(year, rows) { ChronoPresenter.build(year, rows, today) }

    ChronoScreen(
        state = state,
        onShiftYear = { delta ->
            year = ChronoPresenter.shiftYear(year, delta, today)
        },
        modifier = modifier,
    )
}

@Composable
fun ChronoScreen(
    state: ChronoViewState,
    onShiftYear: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val field = remember { ChronoParticleField() }

    // 粒子场帧时钟
    var ms by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.elapsedRealtimeNanos()
        while (true) {
            withFrameNanos {
                ms = (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L
            }
        }
    }

    // 年份数字金粉重组：切年时 spring 回弹一次
    val yearPulse = remember { Animatable(1f) }
    LaunchedEffect(state.year) {
        yearPulse.snapTo(0.82f)
        yearPulse.animateTo(
            1f,
            spring(dampingRatio = ChronoTokens.SPRING_DAMPING, stiffness = ChronoTokens.SPRING_STIFFNESS),
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(ChronoTokens.bgWarm, ChronoTokens.bgDeep),
                    center = Offset(0.2f, 0.1f),
                    radius = 900f,
                ),
            ),
    ) {
        // 金色粒子环 + 流动粒子数字（背景层，不遮挡文字）
        Canvas(Modifier.fillMaxSize()) {
            field.update(ms, size.width, size.height, density)
            for (p in field.ring) {
                drawCircle(
                    color = ChronoTokens.brandGold.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(p.x, p.y),
                )
            }
            for (p in field.flow) {
                drawCircle(
                    color = ChronoTokens.brandGold.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(p.x, p.y),
                )
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            HeaderBlock(state, yearPulse.value, Modifier.padding(top = 48.dp))

            YearSwitcher(
                onPrev = { onShiftYear(-1) },
                onNext = { onShiftYear(1) },
                modifier = Modifier.padding(top = 18.dp),
            )

            Spacer(Modifier.height(20.dp))

            ChronoTimeline(
                nodes = listOf(ChronoNode(state = state, selected = true)),
                modifier = Modifier.weight(1f),
            )
        }

        // 底部毛玻璃底栏 + 升起面板
        ChronoBottomDock(state = state, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HeaderBlock(
    state: ChronoViewState,
    pulse: Float,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(120.dp)) {
            val ring = ChronoTokens.brandGold.copy(alpha = 0.35f)
            drawCircle(ring, radius = size.minDimension * 0.46f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            drawCircle(ChronoTokens.colorGlow, radius = size.minDimension * 0.30f)
        }
        BasicText(
            text = state.ganzhi,
            style = TextStyle(
                color = ChronoTokens.textPrimary,
                fontSize = 34.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.graphicsLayer(scaleX = pulse, scaleY = pulse),
        )
        BasicText(
            text = state.yearLabel,
            style = TextStyle(color = ChronoTokens.textSecondary, fontSize = 14.sp),
            modifier = Modifier.padding(top = 6.dp),
        )
        BasicText(
            text = state.agoText,
            style = TextStyle(color = ChronoTokens.textTertiary, fontSize = 12.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun YearSwitcher(onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton("前一年", onPrev)
        BasicText(
            text = "同年诸侯对照",
            style = TextStyle(color = ChronoTokens.textTertiary, fontSize = 12.sp),
        )
        ArrowButton("后一年", onNext)
    }
}

@Composable
private fun ArrowButton(label: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (pressed) ChronoTokens.textAccent.copy(alpha = ChronoTokens.PRESS_ALPHA)
                else ChronoTokens.bgSurface,
            )
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onClick()
                    },
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = ChronoTokens.textSecondary, fontSize = 13.sp, fontFamily = FontFamily.Serif),
        )
    }
}
