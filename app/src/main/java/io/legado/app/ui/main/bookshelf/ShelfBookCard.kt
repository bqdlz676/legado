package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.ShelfParams
import kotlinx.coroutines.delay

/**
 * 落文 · 竖排书名卡片（切片-10）。
 *
 * - 竖排书名即封面（无封面图），书名右侧一道朱砂竖线，线长 = 阅读进度比例；
 * - 右缘淡墨水印阅读进度；底部细线进度：朱砂 = 已读，云灰 = 未读；作者 / 章节一行小字；
 * - 阴影 = 暖金色微光溢出（0 0 12px rgba(212,165,74,.3)），不用黑灰阴影、不用 Material elevation；
 * - 触摸卡片：spring 前倾 3D 旋转 5°，微光由暖金转为朱砂；
 * - 点进卡片：卡片朝你飞来放大，书名字散成字粒后回调翻开正文；
 * - 右滑删除：卡片碎成墨滴散开后回调删除；
 * - 入场 scroll reveal，按顺位 stagger 浮入 350ms；[parallax] 由列表滚动驱动微前倾。
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
        delay(index.coerceAtMost(10) * ShelfTokens.STAGGER_MS)
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ShelfTokens.CARD_REVEAL_MS.toInt(),
                easing = ShelfTokens.cardReveal,
            ),
        )
    }

    // —— 按下墨触：朱砂 0→8%，120ms 回落（不用 Material ripple）——
    var pressed by remember { mutableStateOf(false) }
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) ShelfTokens.PRESS_ALPHA else 0f,
        animationSpec = tween(ShelfTokens.MICRO_MS.toInt()),
        label = "shelf-card-press",
    )

    // —— 前倾：spring 带回弹；按下前倾 5°，松开回正 ——
    val tiltY by animateFloatAsState(
        targetValue = if (pressed) ShelfParams.CARD_TILT_DEGREES.toFloat() else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "shelf-card-tilt-y",
    )
    val tiltX by animateFloatAsState(
        targetValue = if (pressed) -2f else parallax * 2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "shelf-card-tilt-x",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shelf-card-scale",
    )

    // —— 点进：飞来放大 + 书名爆字粒，收口后回调翻开 ——
    var opening by remember { mutableStateOf(false) }
    val openProgress = remember { Animatable(0f) }
    val titleMotes = remember(book.id) { ShelfParticles.seedTitleMotes(book.title) }
    LaunchedEffect(opening) {
        if (opening) {
            openProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(ShelfTokens.PAGE_MS.toInt(), easing = ShelfTokens.cardReveal),
            )
            onOpen(book)
        }
    }

    // —— 删除：碎墨滴飞散，消散后回调删除 ——
    var shattering by remember { mutableStateOf(false) }
    val shatter = remember { Animatable(0f) }
    val droplets = remember(book.id) { ShelfParticles.seedInkDroplets(14) }
    LaunchedEffect(shattering) {
        if (shattering) {
            shatter.animateTo(
                targetValue = 1f,
                animationSpec = tween(ShelfTokens.SHATTER_MS.toInt()),
            )
            onDelete(book)
        }
    }

    val density = LocalDensity.current
    val revealDy = with(density) { 26.dp.toPx() }
    val glowPad = with(density) { ShelfParams.SHADOW_BLUR_DP.toFloat().dp.toPx() }
    // 按下微光由暖金转为朱砂（切片-10：阴影从暖金变为朱砂）
    val glowColor = if (pressed) ShelfTokens.textAccent else ShelfTokens.colorGlow
    val cardShape = RoundedCornerShape(ShelfTokens.CARD_RADIUS_DP.dp)

    Box(
        modifier = modifier
            .aspectRatio(0.66f)
            .padding(ShelfParams.SHADOW_BLUR_DP.toFloat().dp),
    ) {
        // 暖金 / 朱砂微光溢出：Canvas 自绘三层叠晕，不用 elevation 阴影
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val inset = glowPad * 0.4f
                    repeat(3) { layer ->
                        val expand = glowPad * (1f + layer * 0.5f)
                        drawRoundRect(
                            color = glowColor,
                            topLeft = Offset(-expand, -expand + ShelfTokens.GLOW_OVERFLOW_DP.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(
                                size.width + expand * 2f,
                                size.height + expand * 2f,
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                ShelfTokens.CARD_RADIUS_DP.dp.toPx() + inset,
                            ),
                            alpha = 0.34f - layer * 0.1f,
                        )
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(ShelfTokens.bgSurface)
                    .drawPaperGrain(book.id.hashCode())
                    .graphicsLayer(
                        rotationY = tiltY,
                        rotationX = tiltX,
                        scaleX = scale * (1f + openProgress.value * 0.15f),
                        scaleY = scale * (1f + openProgress.value * 0.15f),
                        alpha = reveal.value * (1f - shatter.value),
                        translationY = (1f - reveal.value) * revealDy,
                        cameraDistance = density.density * 12f,
                    )
                    .pointerInput(book.id) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { opening = true },
                            onLongPress = { shattering = true },
                        )
                    }
                    .pointerInput(book.id) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = 0f },
                            onHorizontalDrag = { _, delta -> dragX += delta },
                            onDragEnd = {
                                // 右滑删除：超过阈值即碎墨，否则回位
                                if (dragX > 120.dp.toPx()) shattering = true else dragX = 0f
                            },
                            onDragCancel = { dragX = 0f },
                        )
                    },
            ) {
                ShelfCardContent(book = book, modifier = Modifier.fillMaxSize())

                // 朱砂按下泛色
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ShelfTokens.textAccent.copy(alpha = pressAlpha)),
                )

                // 书名爆开的字粒层
                if (opening) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        titleMotes.forEach { mote ->
                            val pos = ShelfParticles.titleMoteAt(
                                mote, openProgress.value, size.width, size.height,
                            )
                            drawCircle(
                                color = ShelfTokens.textPrimary,
                                radius = mote.size * 3f * (1f - openProgress.value * 0.5f),
                                center = Offset(pos.x, pos.y),
                                alpha = (1f - openProgress.value).coerceIn(0f, 1f),
                            )
                        }
                    }
                }

                // 碎墨滴层
                if (shattering) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        droplets.forEach { droplet ->
                            val pos = ShelfParticles.dropletAt(
                                droplet, shatter.value, size.width, size.height,
                            )
                            val radius = droplet.radius * (1f - shatter.value * 0.6f)
                            drawCircle(
                                color = if (droplet.ink) ShelfTokens.textPrimary else ShelfTokens.textAccent,
                                radius = radius,
                                center = Offset(pos.x, pos.y),
                                alpha = (1f - shatter.value).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfCardContent(book: ShelfBookUi, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 书名区：竖排书名 + 书名右侧朱砂进度竖线；右缘淡墨水印百分比
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 竖排书名（书名即封面，无封面图）
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        verticalTitle(book.title).forEach { ch ->
                            BasicText(
                                text = ch,
                                style = TextStyle(
                                    color = ShelfTokens.textPrimary,
                                    fontSize = ShelfParams.TITLE_FONT_SIZE_SP.toFloat().sp,
                                    fontWeight = ShelfTokens.titleWeight,
                                    fontFamily = ShelfTokens.serifTitle,
                                    textAlign = TextAlign.Center,
                                    lineHeight = (ShelfParams.TITLE_FONT_SIZE_SP.toFloat() + 2f).sp,
                                ),
                            )
                        }
                    }
                    // 朱砂进度竖线：线长 = 阅读进度比例；余段留云灰轨道
                    Box(
                        modifier = Modifier
                            .width(ShelfParams.PROGRESS_LINE_WIDTH_DP.toFloat().dp)
                            .height(72.dp)
                            .background(ShelfTokens.colorDivider, RoundedCornerShape(50)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(book.progress.coerceIn(0f, 1f))
                                .background(ShelfTokens.textAccent, RoundedCornerShape(50)),
                        )
                    }
                }

                // 右缘淡墨水印：大号淡墨百分比，压在书名之后
                BasicText(
                    text = book.percentText,
                    style = TextStyle(
                        color = ShelfTokens.textPrimary.copy(alpha = 0.06f),
                        fontSize = 28.sp,
                        fontWeight = ShelfTokens.titleWeight,
                        fontFamily = ShelfTokens.serifTitle,
                    ),
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            // 作者 / 章节一行小字（云灰 11sp，锚点 S-10：TEXT_TERTIARY #9B9687）
            BasicText(
                text = "${book.author} · ${book.chapterText}",
                style = TextStyle(
                    color = ShelfTokens.textTertiary,
                    fontSize = ShelfParams.AUTHOR_FONT_SIZE_SP.toFloat().sp,
                    fontFamily = ShelfTokens.serifBody,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // 云灰 1px 线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ShelfTokens.DIVIDER_HEIGHT_DP.dp)
                    .background(ShelfTokens.colorDivider),
            )

            // 底部细线进度：朱砂 = 已读，云灰 = 未读；右端百分比小字
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ShelfTokens.BOTTOM_LINE_HEIGHT_DP.dp)
                        .background(ShelfTokens.colorDivider, RoundedCornerShape(50)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.progress.coerceIn(0f, 1f))
                            .background(ShelfTokens.textAccent, RoundedCornerShape(50)),
                    )
                }
                BasicText(
                    text = book.percentText,
                    style = TextStyle(
                        color = ShelfTokens.textTertiary,
                        fontSize = ShelfTokens.CHAPTER_SP.sp,
                        fontFamily = ShelfTokens.serifBody,
                    ),
                )
            }
        }
    }
}

/** 宣纸颗粒：种子化细点 + 一枚淡金底纹环，重组不漂移。 */
private fun Modifier.drawPaperGrain(seed: Int): Modifier = this.drawBehind {
    val rng = SeededRng(seed.toLong())
    repeat(36) {
        val x = rng.next() * size.width
        val y = rng.next() * size.height
        val radius = 0.5f + rng.next() * 0.8f
        drawCircle(
            color = ShelfTokens.textPrimary,
            radius = radius,
            center = Offset(x, y),
            alpha = 0.035f,
        )
    }
    // 淡金底纹环（无封面时的印章式兜底）
    drawCircle(
        color = ShelfTokens.brandGold,
        radius = size.minDimension * 0.30f,
        center = Offset(size.width / 2f, size.height * 0.42f),
        alpha = 0.10f,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f),
    )
}
