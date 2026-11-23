package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 落文 · 书架底部金色微粒场（切片-10：卡片下方悬浮 15-20 粒极淡金色微粒，缓慢随机飘动）。
 *
 * 微粒只在页面下半部缓慢上浮、左右微摆，暖金核心 + 外层微光；
 * 数量固定、轨迹由种子决定，不随重组抖动；低端机靠单条无限动画驱动，不逐粒开协程。
 */
@Composable
fun GoldParticleField(
    modifier: Modifier = Modifier,
    count: Int = ShelfTokens.PARTICLE_DEFAULT,
) {
    val motes = remember(count) { ShelfParticles.seedGoldMotes(count) }
    val transition = rememberInfiniteTransition(label = "shelf-gold-field")
    val loop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = ShelfTokens.cardReveal),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shelf-gold-loop",
    )

    Canvas(modifier = modifier) {
        motes.forEach { mote ->
            val pos = ShelfParticles.goldMoteAt(mote, loop, size.width, size.height)
            // 每条粒子用自己的相位错开，避免整屏同起同落
            val twinkle = 0.35f + 0.65f * sin(
                (loop * 2f * PI.toFloat() + mote.phase * 9f).toDouble(),
            ).toFloat()
            // 上浮到顶部附近渐隐，避免循环重启时硬跳
            val fadeTop = (1f - pos.y / size.height).coerceIn(0f, 1f)
            val alpha = mote.alpha * twinkle.coerceIn(0.15f, 1f) * (1f - fadeTop * 0.6f)
            val radius = mote.radiusDp.dp.toPx()

            // 外层微光
            drawCircle(
                color = ShelfTokens.colorGlow,
                radius = radius * 2.6f,
                center = androidx.compose.ui.geometry.Offset(pos.x, pos.y),
                alpha = alpha * 0.55f,
            )
            // 暖金核心
            drawCircle(
                color = ShelfTokens.brandGold,
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(pos.x, pos.y),
                alpha = alpha,
            )
        }
    }
}
