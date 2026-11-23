package io.legado.app.ui.map.globe.astro

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 程序化星空（纯 JVM，确定性种子，无外部星图依赖）。
 *
 * 切片 19：半径 900 单位的巨大星空球包裹场景；恒星大小不一——
 * 大星圆形辉光点（3–5px），小星极细白点（1px），偶有几颗淡金色；
 * 星空固定不动，地球在里面自转。
 *
 * 为什么程序化而不是拉 Stellarium 星图：切片 20 允许「程序化生成或 Stellarium 星图」，
 * 离线兜底必须保证无网也有星空；种子固定后单测可复现星点数量与分布范围。
 */
data class Star(
    val direction: Vec3,
    val sizePx: Float,
    val gold: Boolean,
    val brightness: Float,
)

class StarField(
    val count: Int = GlobeConfig.STAR_COUNT,
    private val seed: Int = 9173,
) {
    val stars: List<Star> = ArrayList<Star>(count).apply {
        repeat(count) { i ->
            val u = hash(i, 1)
            val v = hash(i, 2)
            val w = hash(i, 3)
            // 均匀球面分布
            val z = 2.0 * u - 1.0
            val theta = 2.0 * PI * v
            val r = sqrt(1.0 - z * z)
            val dir = Vec3(r * cos(theta), z, r * sin(theta))

            val large = w > 0.85
            val size = if (large) 3f + hash(i, 4).toFloat() * 2f else 1f
            val gold = hash(i, 5) > 0.94
            val brightness = 0.55f + hash(i, 6).toFloat() * 0.45f
            add(Star(dir, size, gold, brightness))
        }
    }

    private fun hash(i: Int, salt: Int): Double {
        var h = i * 374761393 + salt * 668265263 + seed * 2246822519
        h = h xor (h ushr 13)
        h *= 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff).toDouble() / 0x7fffffff
    }
}
