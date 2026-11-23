package io.legado.app.ui.globe.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import io.legado.app.ui.globe.core.terrain.CoverType
import io.legado.app.ui.globe.core.terrain.FbmNoise
import io.legado.app.ui.globe.core.terrain.TerrainSource
import kotlin.math.abs

/**
 * 程序化兜底纹理（GL 层，Android Bitmap）。
 *
 * 零外部数据依赖：无网、无 assets/map 时，用确定性噪声生成最小可用的
 * 昼夜 / 覆盖 / 高程 / 云层 / 夜景纹理，保证球体不空白、不崩溃。
 * 真实 Blue Marble / WorldCover / Black Marble 等高精纹理就位后，
 * 走同一套 `uploadTexture` 入口替换，渲染层无需改动。
 *
 * 注意：纹理生成与 GL 上传需真机 / 模拟器验证；噪声算法本身在 core 层可单测。
 */
object TextureFactory {

    private const val W = 512
    private const val H = 256

    /** 海陆 / 覆盖 / 高程判定与球面顶点位移共用同一套确定性地形源，避免纹理与隆起错位。 */
    private val terrain = TerrainSource()

    /** 云层 / 夜景金点专用噪声。 */
    private val noise = FbmNoise(seed = 20261123)

    private fun landMask(u: Double, v: Double): Double =
        if (terrain.landMaskUv(u, v)) 1.0 else 0.0

    fun dayTexture(): Bitmap {
        val px = IntArray(W * H)
        for (y in 0 until H) {
            val v = y.toDouble() / H
            val lat = 90.0 - v * 180.0
            for (x in 0 until W) {
                val u = x.toDouble() / W
                val land = landMask(u, v)
                val band = terrain.coverBandUv(u, v)
                val ice = abs(lat) > 66.5
                val color = when {
                    ice -> CoverType.ICE.hex
                    land < 0.5 -> CoverType.WATER.hex
                    band > 0.66 -> CoverType.FOREST.hex
                    band > 0.5 -> CoverType.GRASS.hex
                    band > 0.36 -> CoverType.SOIL.hex
                    else -> CoverType.DESERT.hex
                }
                px[y * W + x] = color
            }
        }
        return Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, W, 0, 0, W, H) }
    }

    /** 覆盖图：RGB 编码类型，Alpha 编码近岸浅海系数。 */
    fun coverTexture(): Bitmap {
        val px = IntArray(W * H)
        for (y in 0 until H) {
            val v = y.toDouble() / H
            val lat = 90.0 - v * 180.0
            for (x in 0 until W) {
                val u = x.toDouble() / W
                val land = landMask(u, v)
                val ice = abs(lat) > 66.5
                val type = when {
                    ice -> CoverType.ICE
                    land < 0.5 -> CoverType.WATER
                    else -> {
                        val band = terrain.coverBandUv(u, v)
                        when {
                            band > 0.66 -> CoverType.FOREST
                            band > 0.5 -> CoverType.GRASS
                            band > 0.36 -> CoverType.SOIL
                            else -> CoverType.DESERT
                        }
                    }
                }
                // 近岸：相邻像素有陆地则提高 alpha（浅海）
                val coast = if (type == CoverType.WATER) {
                    val u2 = ((u + 1.5 / W) % 1.0)
                    if (landMask(u2, v) > 0.5) 0.85 else 0.0
                } else 0.0
                val a = (coast * 255).toInt()
                px[y * W + x] = (a shl 24) or (type.r shl 16) or (type.g shl 8) or type.b
            }
        }
        return Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, W, 0, 0, W, H) }
    }

    /** 高程灰度图（兜底噪声；真实 SRTM 就位后替换）。 */
    fun heightTexture(): Bitmap {
        val px = IntArray(W * H)
        for (y in 0 until H) {
            val v = y.toDouble() / H
            for (x in 0 until W) {
                val u = x.toDouble() / W
                val land = landMask(u, v)
                val h = if (land < 0.5) 0 else (noise.fbm(u * 6.0, v * 3.0, 5) * 255).toInt()
                px[y * W + x] = (h shl 16) or (h shl 8) or h
            }
        }
        return Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, W, 0, 0, W, H) }
    }

    /** 云层：RGB 白，Alpha = 云量。 */
    fun cloudTexture(): Bitmap {
        val px = IntArray(W * H)
        for (y in 0 until H) {
            val v = y.toDouble() / H
            for (x in 0 until W) {
                val u = x.toDouble() / W
                val c = noise.fbm(u * 5.0 + 13.7, v * 2.5 + 7.1, 5)
                val a = ((c - 0.45).coerceIn(0.0, 1.0) * 255).toInt()
                px[y * W + x] = (a shl 24) or 0xFFFFFF
            }
        }
        return Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, W, 0, 0, W, H) }
    }

    /** 夜景：黑底 + 陆地上稀疏金点（Black Marble 兜底示意）。 */
    fun nightTexture(): Bitmap {
        val px = IntArray(W * H)
        for (y in 0 until H) {
            val v = y.toDouble() / H
            for (x in 0 until W) {
                val u = x.toDouble() / W
                val land = landMask(u, v)
                val sparkle = hash01(x * 31 + y * 7)
                val gold = if (land > 0.5 && sparkle > 0.985) 0xD4A54A else 0x000000
                px[y * W + x] = gold
            }
        }
        return Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, W, 0, 0, W, H) }
    }

    /** Bitmap → GL 纹理 id；失败返回 0，渲染层跳过该 pass。 */
    fun uploadTexture(bitmap: Bitmap?): Int {
        if (bitmap == null || bitmap.isRecycled) return 0
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return id
    }

    /** 供夜景金点使用的确定性哈希 [0,1)。 */
    private fun hash01(n: Int): Double {
        var h = n * 2654435761.toInt()
        h = h xor (h ushr 13)
        h *= 1597334677
        h = h xor (h ushr 16)
        return (h and 0x7fffffff).toDouble() / 0x7fffffff
    }
}
