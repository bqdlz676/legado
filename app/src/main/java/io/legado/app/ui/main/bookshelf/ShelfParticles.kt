package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.components.ShelfParams
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 二维点（纯 JVM 几何，便于单测断言位置边界）。 */
data class Pt(val x: Float, val y: Float)

/** 底部金色微粒：轨迹由种子决定，重组 / 旋转屏幕后不漂移。 */
data class GoldMote(
    val baseX: Float,
    val baseY: Float,
    val rise: Float,
    val phase: Float,
    val radiusDp: Float,
    val alpha: Float,
)

/** 碎墨滴：方向 / 距离 / 半径 / 墨色或朱砂。 */
data class InkDroplet(
    val dirX: Float,
    val dirY: Float,
    val dist: Float,
    val radius: Float,
    val ink: Boolean,
)

/** 书名爆开的字粒：每字一粒，带方向与距离。 */
data class TitleMote(
    val ch: Char,
    val dirX: Float,
    val dirY: Float,
    val dist: Float,
    val size: Float,
)

/** 种子化 LCG：固定序列，单测可复现，重组不抖动。 */
internal class SeededRng(seed: Long) {
    private var state = seed and Long.MAX_VALUE

    fun next(): Float {
        state = (state * 6364136223846793005L + 1442695040888963407L) and Long.MAX_VALUE
        return (state ushr 33).toFloat() / Long.MAX_VALUE.toFloat()
    }
}

/** 书架粒子几何（纯 JVM，Compose 只负责把点位画出来）。 */
object ShelfParticles {

    /** 微粒数量收口在切片-10 的 15-20 粒区间，越界一律夹回。 */
    fun clampCount(count: Int): Int =
        count.coerceIn(ShelfParams.PARTICLE_MIN, ShelfParams.PARTICLE_MAX)

    fun seedGoldMotes(count: Int, seed: Long = 20261123L): List<GoldMote> {
        val rng = SeededRng(seed)
        return List(clampCount(count)) {
            GoldMote(
                baseX = rng.next(),
                baseY = 0.55f + rng.next() * 0.45f,
                rise = 0.12f + rng.next() * 0.22f,
                phase = rng.next(),
                radiusDp = 1.0f + rng.next() * 2.0f,
                alpha = 0.30f + rng.next() * 0.40f,
            )
        }
    }

    /** t ∈ 0..1 循环相位；返回 [0,width]×[0,height] 内的位置，不越界。 */
    fun goldMoteAt(mote: GoldMote, t: Float, width: Float, height: Float): Pt {
        val cycle = (t + mote.phase) % 1f
        val yRatio = (mote.baseY - cycle * mote.rise).coerceIn(0.05f, 1f)
        val sway = sin(
            (cycle * 2f * PI.toFloat() + mote.phase * 2f * PI.toFloat()).toDouble(),
        ).toFloat() * 0.03f
        val xRatio = (mote.baseX + sway).coerceIn(0f, 1f)
        return Pt(xRatio * width, yRatio * height)
    }

    fun seedInkDroplets(count: Int, seed: Long = 97531L): List<InkDroplet> {
        val rng = SeededRng(seed)
        return List(count) {
            val angle = rng.next() * 2f * PI.toFloat()
            InkDroplet(
                dirX = cos(angle.toDouble()).toFloat(),
                dirY = sin(angle.toDouble()).toFloat() - 0.25f,
                dist = 0.18f + rng.next() * 0.32f,
                radius = 2f + rng.next() * 5f,
                ink = rng.next() > 0.25f,
            )
        }
    }

    fun dropletAt(droplet: InkDroplet, progress: Float, width: Float, height: Float): Pt =
        Pt(
            width / 2f + droplet.dirX * droplet.dist * width * progress,
            height / 2f + droplet.dirY * droplet.dist * height * progress,
        )

    fun seedTitleMotes(title: String, seed: Long = 4242L): List<TitleMote> {
        val rng = SeededRng(seed + title.length)
        return title.trim().toCharArray().map { ch ->
            val angle = rng.next() * 2f * PI.toFloat()
            TitleMote(
                ch = ch,
                dirX = cos(angle.toDouble()).toFloat(),
                dirY = sin(angle.toDouble()).toFloat() - 0.2f,
                dist = 0.15f + rng.next() * 0.30f,
                size = 0.7f + rng.next() * 0.5f,
            )
        }
    }

    fun titleMoteAt(mote: TitleMote, progress: Float, width: Float, height: Float): Pt =
        Pt(
            width / 2f + mote.dirX * mote.dist * width * progress,
            height / 2f + mote.dirY * mote.dist * height * progress,
        )
}
