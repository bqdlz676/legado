package io.legado.app.ui.map.globe.terrain

import io.legado.app.ui.map.globe.GlobeConfig
import kotlin.math.abs

/**
 * 地表覆盖分类器（纯 JVM，无 Android 依赖，可直接单测）。
 *
 * 判定优先级（高 → 低）：
 *  1. 海拔 ≤ 0 → 水域（切片 20：高度图上海拔 ≤ 0 自动填海洋色，不用手动切水域）；
 *  2. 海拔 > 3500m → 雪线覆盖，不读覆盖图（切片 17 / 20）；
 *  3. |纬度| > 66.5° 且覆盖图 = 冰 → 极地冰盖（切片 19，从覆盖图读，非高程算）；
 *  4. 其余按覆盖图像素最近邻归类。
 *
 * 为什么雪线优先级高于覆盖图：切片 17 明确雪面「不是从覆盖图读，是从高程图自动算」，
 * 即使森林区海拔越过 3500m 也要积雪，保证秦岭 / 祁连山顶终年积雪。
 */
class TerrainClassifier(private val noise: FbmNoise = FbmNoise()) {

    fun classify(
        elevationMeters: Double,
        coverRgb: Int,
        lat: Double,
        lng: Double,
    ): TerrainSample {
        val r = (coverRgb ushr 16) and 0xFF
        val g = (coverRgb ushr 8) and 0xFF
        val b = coverRgb and 0xFF
        val nearest = CoverType.nearest(r, g, b)

        val cover: CoverType
        val isSnow: Boolean
        val isWater: Boolean
        val isIce: Boolean

        when {
            elevationMeters <= GlobeConfig.OCEAN_ELEVATION_METERS -> {
                cover = CoverType.WATER; isWater = true; isSnow = false; isIce = false
            }
            elevationMeters > GlobeConfig.SNOWLINE_METERS -> {
                cover = CoverType.SNOW; isSnow = true; isWater = false; isIce = false
            }
            abs(lat) > GlobeConfig.POLAR_LAT_DEG && nearest == CoverType.ICE -> {
                cover = CoverType.ICE; isIce = true; isSnow = false; isWater = false
            }
            else -> {
                cover = nearest
                isSnow = cover == CoverType.SNOW
                isWater = cover == CoverType.WATER
                isIce = false
            }
        }

        val displacement = displacementFor(cover, elevationMeters, lat, lng)
        val specular = when {
            isIce -> GlobeConfig.ICE_SPECULAR
            isWater -> 0.6f
            isSnow -> 0.25f
            else -> 0.05f
        }

        return TerrainSample(
            cover = cover,
            elevationMeters = elevationMeters,
            lat = lat,
            lng = lng,
            displacementUnits = displacement.toFloat(),
            colorRgb = cover.hex,
            isSnow = isSnow,
            isWater = isWater,
            isIce = isIce,
            specular = specular,
        )
    }

    private fun displacementFor(
        cover: CoverType,
        elevationMeters: Double,
        lat: Double,
        lng: Double,
    ): Double {
        val base = FbmNoise.baseRelief(elevationMeters)
        return when (cover) {
            // 森林：fbm ×1.5 树冠微凸起（切片 17）
            CoverType.FOREST -> base + noise.forestBump(lat, lng)
            // 沙漠：sin 噪声微波浪
            CoverType.DESERT -> base + noise.desertWave(lat, lng)
            // 水域贴球面，不起伏
            CoverType.WATER -> 0.0
            // 雪面 / 冰盖保留高程基准起伏
            CoverType.SNOW, CoverType.ICE -> base
            else -> base
        }
    }
}
