package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText

/**
 * 落文 · 竖排书名卡片。
 *
 * - 竖排书名 + 朱砂进度竖线 + 暖金微光阴影 + 宣纸颗粒底；
 * - 入场 scroll reveal，按顺位 stagger 浮入（350ms）；
 * - 按下 / 选中 spring 前倾 3D 旋转 5°；
 * - 长按删除：卡片碎成墨滴飞散后回调 [onDelete]；
 * - [parallax] 由列表滚动驱动，做 camera 微前倾。
 */
@Composable
fun ShelfBookCard(
    book: ShelfBookUi,
    index: Int,
    parallax: Float,
    onOpen: (ShelfBookUi) -> Unit,
    onDelete: (ShelfBookUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    // —— 入场浮入：stagger 逐个浮入 350ms ——
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(book.id, index) {
        kotlinx.coroutines.delay(index.coerceAtMost(10) * LuowenShelf.STAGGER_MS)
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = LuowenShelf.CARD_REVEAL_MS.toInt(),
                easing = LuowenShelf.cardReveal,
            ),
        )
    }

    // —— 按下墨触：朱砂 0→8%，120ms 回落 ——
    var pressed by remember { mutableStateOf(false) }
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) LuowenShelf.PRESS_ALPHA else 0f,
        animationSpec = tween(LuowenShelf.MICRO_MS.toInt()),
        label = "card-press",
    )

    // —— 选中前倾：spring 带回弹 ——
    val tiltY by animateFloatAsState(
        targetValue = if (pressed) 5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "card-tilt-y",
    )
    val tiltX by animateFloatAsState(
        targetValue = if (pressed) -2f else parallax * 2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "card-tilt-x",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card-scale",
    )

    // —— 删除碎墨 ——
    var shattering by remember { mutableStateOf(false) }
    val shatter = remember { Animatable(0f) }
    val droplets = remember(book.id) { seedDroplets(14) }

    LaunchedEffect(shattering) {
        if (shattering) {
            shatter.animateTo(
                targetValue = 1f,
                animationSpec = tween(LuowenShelf.SHATTER_MS.toInt()),
            )
            onDelete(book)
        }
    }

    val cardShape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current
    val revealDy = with(density) { 26.dp.toPx() }

    Box(
        modifier = modifier
            .aspectRatio(0.66f)
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                ambientColor = LuowenShelf.colorGlow,
                spotColor = LuowenShelf.colorGlow,
                clip = false,
            )
            .clip(cardShape)
            .background(LuowenShelf.bgSurface)
            .drawPaperGrain(book.id.hashCode())
            .graphicsLayer(
                rotationY = tiltY,
                rotationX = tiltX,
                scaleX = scale,
                scaleY = scale,
                alpha = reveal.value * (1f - shatter.value),
                translationY = (1f - reveal.value) * revealDy,
            )
            .pointerInput(book.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onOpen(book) },
                    onLongPress = { shattering = true },
                )
            },
    ) {
        // 宣纸颗粒 + 底纹已在 drawPaperGrain；此处排内容
        ShelfCardContent(book = book, modifier = Modifier.fillMaxSize())

        // 朱砂按下泛色
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LuowenShelf.textAccent.copy(alpha = pressAlpha)),
        )

        // 碎墨滴层
        if (shattering) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawInkDroplets(droplets, shatter.value)
            }
        }
    }
}

@Composable
private fun ShelfCardContent(book: ShelfBookUi, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 新书朱砂印
        if (book.isNew) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(LuowenShelf.textAccent, RoundedCornerShape(50)),
                )
                BasicText(
                    text = "新",
                    style = androidx.compose.ui.text.TextStyle(
                        color = LuowenShelf.textAccent,
                        fontSize = 10.sp,
                        fontFamily = LuowenShelf.serifBody,
                    ),
                )
            }
        }

        // 竖排书名（居中，占卡片主视觉）
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            verticalTitle(book.title).forEach { ch ->
                BasicText(
                    text = ch,
                    style = androidx.compose.ui.text.TextStyle(
                        color = LuowenShelf.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = LuowenShelf.titleWeight,
                        fontFamily = LuowenShelf.serifTitle,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                    ),
                )
            }
        }

        // 作者 + 进度文案
        BasicText(
            text = book.author,
            style = androidx.compose.ui.text.TextStyle(
                color = LuowenShelf.textSecondary,
                fontSize = 11.sp,
                fontFamily = LuowenShelf.serifBody,
            ),
        )
        BasicText(
            text = book.percentText,
            style = androidx.compose.ui.text.TextStyle(
                color = LuowenShelf.textTertiary,
                fontSize = 10.sp,
                fontFamily = LuowenShelf.serifBody,
            ),
        )
    }

    // 朱砂进度竖线：贴卡片右缘，高度按进度
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 5.dp)
            .width(3.dp)
            .height(72.dp)
            .background(LuowenShelf.colorDivider, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(book.progress.coerceIn(0f, 1f))
                .background(LuowenShelf.textAccent, RoundedCornerShape(50)),
        )
    }
    }
}

/** 宣纸颗粒：种子化细点 + 一枚淡金底纹环，重组不漂移。 */
private fun Modifier.drawPaperGrain(seed: Int): Modifier = this.drawBehind {
    var s = seed.toLong() and Long.MAX_VALUE
    fun next(): Float {
        s = (s * 6364136223846793005L + 1442695040888963407L) and Long.MAX_VALUE
        return (s ushr 33).toFloat() / Long.MAX_VALUE.toFloat()
    }
    repeat(36) {
        val x = next() * size.width
        val y = next() * size.height
        val r = 0.5f + next() * 0.8f
        drawCircle(
            color = LuowenShelf.textPrimary,
            radius = r,
            center = Offset(x, y),
            alpha = 0.035f,
        )
    }
    // 淡金底纹环（无封面时的印章式兜底）
    drawCircle(
        color = LuowenShelf.brandGold,
        radius = size.minDimension * 0.30f,
        center = Offset(size.width / 2f, size.height * 0.42f),
        alpha = 0.10f,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f),
    )
}

private data class Droplet(
    val dirX: Float,
    val dirY: Float,
    val dist: Float,
    val radius: Float,
    val ink: Boolean,
)

private fun seedDroplets(count: Int): List<Droplet> {
    var seed = 97531L
    fun next(): Float {
        seed = (seed * 2862933555777941757L + 3037000493L) and Long.MAX_VALUE
        return (seed ushr 33).toFloat() / Long.MAX_VALUE.toFloat()
    }
    return List(count) {
        val angle = next() * 2f * kotlin.math.PI.toFloat()
        Droplet(
            dirX = kotlin.math.cos(angle),
            dirY = kotlin.math.sin(angle) - 0.25f,
            dist = 0.18f + next() * 0.32f,
            radius = 2f + next() * 5f,
            ink = next() > 0.25f,
        )
    }
}

private fun DrawScope.drawInkDroplets(droplets: List<Droplet>, progress: Float) {
    droplets.forEach { d ->
        val cx = size.width / 2f + d.dirX * d.dist * size.width * progress
        val cy = size.height / 2f + d.dirY * d.dist * size.height * progress
        val radius = d.radius * (1f - progress * 0.6f)
        val color = if (d.ink) LuowenShelf.textPrimary else LuowenShelf.textAccent
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(cx, cy),
            alpha = (1f - progress).coerceIn(0f, 1f),
        )
    }
}
