package io.legado.app.ui.book.read.ink

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 磨砂毛玻璃修饰符（切片-03 四层框架 · 绢帛面；阅读页气泡 / Sheet 背板复用）。
 *
 * 三档口径（低端机 / 版本分别兜底，禁止用纯透明冒充磨砂）：
 *  - API 31+ 且非低端：[BlurEffect] 层模糊 + 半透明绢帛底；
 *  - API 26-30：无模糊能力，改半透明绢帛底 + 固定种子噪点颗粒叠层模拟磨砂；
 *  - 低端档位：退近纯色绢帛（alpha 0.94），不做模糊与噪点叠层，保帧预算。
 *
 * 边界说明：Android RenderEffect 模糊的是图层自身内容，并非截取背后背景做 backdrop blur；
 * 真正的背景截获模糊需共享 Sheet 层统一实现，此处不伪造 backdrop 采样。
 */
fun Modifier.frostedSurface(
    shape: Shape,
    lowSpec: Boolean = InkSpec.isLowSpec(),
): Modifier {
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surfaceAlpha = when {
        lowSpec -> InkTokens.FROST_SURFACE_ALPHA_LOW_SPEC
        modern -> InkTokens.FROST_SURFACE_ALPHA_MODERN
        else -> InkTokens.FROST_SURFACE_ALPHA_LEGACY
    }
    val surface = InkTokens.bgSurface.copy(alpha = surfaceAlpha)

    return this
        .clip(shape)
        .then(
            if (modern && !lowSpec) {
                Modifier.graphicsLayer {
                    val radius = InkTokens.FROST_BLUR_RADIUS_DP.toPx()
                    renderEffect = BlurEffect(
                        radiusX = radius,
                        radiusY = radius,
                        edgeTreatment = TileMode.Clamp,
                    )
                }
            } else {
                Modifier
            },
        )
        .background(surface, shape)
        .then(
            if (!modern && !lowSpec) Modifier.frostGrain() else Modifier,
        )
}

/** API 26-30 兜底磨砂：固定种子噪点颗粒，跨组合稳定不闪烁。 */
private fun Modifier.frostGrain(): Modifier = this.drawBehind {
    val rand = Random(FROST_NOISE_SEED)
    val grid = InkTokens.FROST_NOISE_GRID_DP.dp.toPx().coerceAtLeast(1f)
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            if (rand.nextFloat() < GRAIN_PROBABILITY) {
                val alpha = InkTokens.FROST_NOISE_ALPHA * rand.nextFloat()
                drawCircle(
                    color = InkTokens.textPrimary.copy(alpha = alpha),
                    radius = 1f + 0.6f * rand.nextFloat(),
                    center = androidx.compose.ui.geometry.Offset(x + grid / 2f, y + grid / 2f),
                )
            }
            x += grid
        }
        y += grid
    }
}

private const val FROST_NOISE_SEED: Long = 90210317L
private const val GRAIN_PROBABILITY: Float = 0.28f
