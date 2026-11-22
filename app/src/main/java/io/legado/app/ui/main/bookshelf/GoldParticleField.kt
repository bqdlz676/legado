package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 落文 · 底部金色微粒场。
 *
 * 微粒只在页面下半部缓慢上浮、左右微摆，暖金核心 + 外层微光，
 * 数量固定、轨迹由种子决定，不随重组抖动；低端机靠单条无限动画驱动，不逐粒开协程。
 */
@Composable
fun GoldParticleField(
    modifier: Modifier = Modifier,
    count: Int = 26,
) {
    val particles = remember(count) { seedParticles(count) }
    val transition = rememberInfiniteTransition(label = "gold-particle-field")
    val loop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LuowenShelf.cardReveal),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gold-particle-loop",
    )

    Canvas(modifier = modifier) {
        particles.forEach { p ->
            // 每条粒子用自己的相位错开，避免整屏同起同落
            val t = (loop + p.phase) % 1f
            val yRatio = (p.baseY - t * p.rise).coerceIn(0.05f, 1f)
            val xRatio = p.baseX + sin(t * 2f * PI.toFloat() + p.phase * 2f * PI.toFloat()) * 0.03f
            val cx = size.width * xRatio
            val cy = size.height * yRatio
            val radius = p.radiusDp.dp.toPx()
            val twinkle = 0.35f + 0.65f * sin(t * 2f * PI.toFloat() + p.phase * 9f)
            // 上浮到顶部附近渐隐，避免循环重启时硬跳
            val fadeTop = (1f - yRatio).coerceIn(0f, 1f)
            val alpha = p.alpha * twinkle.coerceIn(0.15f, 1f) * (1f - fadeTop * 0.6f)

            // 外层微光
            drawCircle(
                color = LuowenShelf.colorGlow,
                radius = radius * 2.6f,
                center = Offset(cx, cy),
                alpha = alpha * 0.55f,
            )
            // 暖金核心
            drawCircle(
                color = LuowenShelf.brandGold,
                radius = radius,
                center = Offset(cx, cy),
                alpha = alpha,
            )
        }
    }
}

private data class Particle(
    val baseX: Float,
    val baseY: Float,
    val rise: Float,
    val phase: Float,
    val radiusDp: Float,
    val alpha: Float,
)

/** 种子化伪随机：固定序列，重组与旋转屏幕后轨迹一致。 */
private fun seedParticles(count: Int): List<Particle> {
    var seed = 20261122L
    fun next(): Float {
        seed = (seed * 6364136223846793005L + 1442695040888963407L) and Long.MAX_VALUE
        return (seed ushr 33).toFloat() / Long.MAX_VALUE.toFloat()
    }
    return List(count) {
        Particle(
            baseX = next(),
            baseY = 0.55f + next() * 0.45f,
            rise = 0.12f + next() * 0.22f,
            phase = next(),
            radiusDp = 1.0f + next() * 2.0f,
            alpha = 0.35f + next() * 0.45f,
        )
    }
}
