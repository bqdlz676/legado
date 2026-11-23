package io.legado.app.ui.globe.core.terrain

import io.legado.app.ui.globe.core.GlobeConfig
import kotlin.math.abs

/**
 * 确定性地形采样源（纯 JVM，无 Android 依赖，可直接单测）。
 *
 * 把经纬度映射到与兜底程序化纹理同一套 fbm 种子的海陆 / 覆盖 / 高程；
 * 球面网格顶点位移与 TextureFactory 兜底纹理共用本类判定，避免纹理色块与地形隆起错位。
 *
 * 流水线：高程 + 覆盖色 → [TerrainClassifier] 分类（7 种地表 / 雪线 3500m / 极地冰盖 /
 * 森林 fbm 微凸起）→ 沿法线的世界单位位移，喂给球体顶点。
 */
class TerrainSource(private val noise: FbmNoise = FbmNoise(seed = 20261123)) {

    private val classifier = TerrainClassifier(noise)

    /** 经纬度 → 兜底纹理 UV：u ∈ [0,1) 经度，v ∈ [0,1) 纬度（v=0 北极）。 */
    fun uvOf(lat: Double, lng: Double): Pair<Double, Double> {
        val u = (lng + 180.0) / 360.0
        val v = ((90.0 - lat) / 180.0).coerceIn(0.0, 1.0)
        return u to v
    }

    /** UV → 海陆掩膜：低频 fbm 阈值，>0.52 为陆地（与兜底大陆轮廓一致）。 */
    fun landMaskUv(u: Double, v: Double): Boolean =
        noise.fbm(u * 4.0, v * 2.0, 5) > 0.52

    /** 经纬度 → 海陆掩膜。 */
    fun landMask(lat: Double, lng: Double): Boolean {
        val (u, v) = uvOf(lat, lng)
        return landMaskUv(u, v)
    }

    /** 覆盖 band 噪声（森林 / 草地 / 裸土 / 沙漠分级阈值用）。 */
    fun coverBandUv(u: Double, v: Double): Double = noise.fbm(u * 8.0, v * 4.0, 4)

    /** 兜底高程（米）：海下 -100m；陆地 0–6500m 由 fbm 决定（真实 SRTM 就位后替换）。 */
    fun elevationMeters(lat: Double, lng: Double): Double {
        if (!landMask(lat, lng)) return -100.0
        val (u, v) = uvOf(lat, lng)
        val h = noise.fbm(u * 6.0, v * 3.0, 5)
        return (h * 6500.0).coerceIn(0.0, 6500.0)
    }

    /** 覆盖类型（与 coverTexture 同套 band 阈值与极地冰盖规则）。 */
    fun coverType(lat: Double, lng: Double): CoverType {
        if (abs(lat) > GlobeConfig.POLAR_LAT_DEG) return CoverType.ICE
        if (!landMask(lat, lng)) return CoverType.WATER
        val (u, v) = uvOf(lat, lng)
        val band = coverBandUv(u, v)
        return when {
            band > 0.66 -> CoverType.FOREST
            band > 0.5 -> CoverType.GRASS
            band > 0.36 -> CoverType.SOIL
            else -> CoverType.DESERT
        }
    }

    /** 单顶点完整采样：分类 → 位移 / 雪线 / 冰盖 / 水体标记。 */
    fun sample(lat: Double, lng: Double): TerrainSample {
        val cover = coverType(lat, lng)
        return classifier.classify(
            elevationMeters = elevationMeters(lat, lng),
            coverRgb = cover.hex,
            lat = lat,
            lng = lng,
        )
    }
}
