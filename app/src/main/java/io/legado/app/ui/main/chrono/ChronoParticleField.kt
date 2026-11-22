package io.legado.app.ui.main.chrono

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 落文 · 纪年页金色粒子场（纯 JVM，无 Android / Compose 依赖，可直接跑单测与 bench）。
 *
 * 空间层口径（纪年时间轴页）：particle field 金色粒子环 + 流动粒子数字。
 *  - 环粒：绕纪年中枢缓行的暖金环，明暗呼吸；
 *  - 流粒：自下而上缓缓升起的暖金余烬，模拟流动数字的光屑；
 *  - 总数 ≤ [ChronoTokens.PARTICLE_CAP]（环 48 + 流 32），帧内零分配。
 *
 * 渲染层只读本类导出的位置 / 透明度，不自行推算运动。
 */
class ChronoParticleField(
    private val seed: Long = DEFAULT_SEED,
) {

    class RingParticle {
        var x = 0f
        var y = 0f
        var size = 0f
        var alpha = 0f
        var angle = 0f
        var angularSpeed = 0f
        var radiusFrac = 0f
        var twinklePhase = 0f
    }

    class FlowParticle {
        var x = 0f
        var y = 0f
        var size = 0f
        var alpha = 0f
        var speed = 0f
        var driftPhase = 0f
        var life = 0f
    }

    val ring: List<RingParticle>
    val flow: List<FlowParticle>

    init {
        val rnd = Random(seed)
        ring = buildList(ChronoTokens.RING_COUNT) {
            val p = RingParticle()
            p.angle = rnd.nextFloat() * 6.2831f
            p.angularSpeed = 0.12f + rnd.nextFloat() * 0.18f
            p.radiusFrac = 0.82f + rnd.nextFloat() * 0.18f
            p.size = 1.0f + rnd.nextFloat() * 1.6f
            p.twinklePhase = rnd.nextFloat() * 6.2831f
            add(p)
        }
        flow = buildList(ChronoTokens.FLOW_COUNT) {
            val p = FlowParticle()
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.speed = 0.02f + rnd.nextFloat() * 0.05f
            p.size = 0.8f + rnd.nextFloat() * 1.4f
            p.driftPhase = rnd.nextFloat() * 6.2831f
            p.life = rnd.nextFloat()
            add(p)
        }
    }

    /**
     * 推进一帧。
     * @param tMs 自页面起的毫秒数（外部时钟驱动）
     * @param widthPx / heightPx 画布尺寸（px）
     * @param density 屏幕密度
     */
    fun update(tMs: Long, widthPx: Float, heightPx: Float, density: Float) {
        val w = widthPx.coerceAtLeast(1f)
        val h = heightPx.coerceAtLeast(1f)
        val cx = w * 0.5f
        val cy = h * 0.30f
        val baseRadius = minOf(w, h) * 0.20f
        val tSec = tMs / 1000f

        for (p in ring) {
            val a = p.angle + tSec * p.angularSpeed
            val r = baseRadius * p.radiusFrac
            p.x = cx + cos(a.toDouble()).toFloat() * r
            p.y = cy + sin(a.toDouble()).toFloat() * r * 0.62f
            val twinkle = 0.55f + 0.45f * sin((tSec * 1.6f + p.twinklePhase).toDouble()).toFloat()
            p.alpha = 0.35f * twinkle
            p.size = (1.0f + twinkle * 0.6f) * density
        }

        for (p in flow) {
            // 生命循环：自下而上升起，到头回到底部
            p.life += p.speed / 10f
            if (p.life > 1f) p.life -= 1f
            p.y = h * (1f - p.life)
            p.x = (p.x + sin((tSec * 0.8f + p.driftPhase).toDouble()).toFloat() * 0.01f)
                .coerceIn(0f, 1f) * w
            p.alpha = 0.30f * sin(p.life * Math.PI).toFloat()
            p.size = p.size * density
        }
    }

    companion object {
        const val DEFAULT_SEED: Long = 20261122L
    }
}
