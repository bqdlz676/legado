package io.legado.app.ui.map.globe.terrain

/**
 * 单顶点地形采样结果（纯 JVM）。
 *
 * 流水线（切片 17 实现段）：
 *  1. 从高程图取 Y 值；
 *  2. 从覆盖图取覆盖类型；
 *  3. 覆盖类型 → 顶点色；森林格加 fbm 微凸起；雪线从高程自动算。
 */
data class TerrainSample(
    val cover: CoverType,
    val elevationMeters: Double,
    val lat: Double,
    val lng: Double,
    /** 沿球面法线的位移（世界单位）。 */
    val displacementUnits: Float,
    /** 顶点色参考值 0xRRGGBB（地貌数据色）。 */
    val colorRgb: Int,
    val isSnow: Boolean,
    val isWater: Boolean,
    val isIce: Boolean,
    /** 镜面反射系数（极地冰盖 0.15，切片 19）。 */
    val specular: Float,
)
