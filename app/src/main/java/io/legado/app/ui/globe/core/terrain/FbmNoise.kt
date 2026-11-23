package io.legado.app.ui.globe.core.terrain

import kotlin.math.floor
import kotlin.math.sin

/**
 * 确定性 2D 值噪声 + fBm 分形叠加（纯 JVM，可单测复现）。
 *
 * 切片 17：森林格顶点 Y 额外 + 细碎噪声 `fbm(x,z) × 1.5`，
 * 形成树冠层不规则隆起；不画真树，只做绒布质感的微地形。
 * 沙漠用 sin 噪声叠加微波浪；噪声全部由种子决定，无随机态泄漏。
 */
class FbmNoise(private val seed: Int = 20261123) {

    /** 整数哈希 → [0,1)。 */
    private fun hash(ix: Int, iz: Int): Double {
        var h = ix * 374761393 + iz * 668265263 + seed * 2246822519
        h = h xor (h ushr 13)
        h = h * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff).toDouble() / 0x7fffffff
    }

    private fun smooth(t: Double): Double = t * t * (3.0 - 2.0 * t)

    fun valueNoise(x: Double, z: Double): Double {
        val ix = floor(x).toInt()
        val iz = floor(z).toInt()
        val fx = x - ix
        val fz = z - iz
        val a = hash(ix, iz)
        val b = hash(ix + 1, iz)
        val c = hash(ix, iz + 1)
        val d = hash(ix + 1, iz + 1)
        val ux = smooth(fx)
        val uz = smooth(fz)
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uz
    }

    /** fBm 叠加，输出 [0,1]。 */
    fun fbm(x: Double, z: Double, octaves: Int = 4): Double {
        var amp = 1.0
        var freq = 1.0
        var sum = 0.0
        var norm = 0.0
        repeat(octaves) {
            sum += amp * valueNoise(x * freq, z * freq)
            norm += amp
            amp *= 0.5
            freq *= 2.0
        }
        return sum / norm
    }

    /**
     * 森林树冠微凸起（世界单位）：fbm × 1.5，封顶 2.5 单位（切片 17：2–3 单位高）。
     */
    fun forestBump(x: Double, z: Double): Double {
        val v = fbm(x, z) * 1.5
        return v.coerceIn(0.0, 2.5)
    }

    /** 沙漠微波浪：sin 噪声叠加，幅度很小。 */
    fun desertWave(x: Double, z: Double): Double {
        return 0.4 * sin(x * 0.8 + seed) * sin(z * 0.7) + 0.3 * fbm(x * 2.0, z * 2.0)
    }

    companion object {
        /** 高程（米）→ 球面位移（世界单位）：7000m 对应约 2 单位，保持地形克制。 */
        fun elevationToUnits(elevationMeters: Double): Double =
            (elevationMeters / 7000.0).coerceIn(0.0, 1.0) * 2.0

        /** 沙漠/森林之外的平滑地表基准起伏。 */
        fun baseRelief(elevationMeters: Double): Double = elevationToUnits(elevationMeters)
    }
}
