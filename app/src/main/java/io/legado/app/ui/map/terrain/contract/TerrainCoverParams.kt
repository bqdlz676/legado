package io.legado.app.ui.map.terrain.contract

/**
 * S-17 地表覆盖层 · 契约层参数单源（平台无关层，纯数值，不引用任何 Android / Compose 类型，
 * 普通 JVM 单测可直接断言）。
 *
 * 〔锚点原文〕docs/落文-规格切片/切片-17-地表覆盖层.md
 *  - :7 同分辨率土地覆盖 PNG（512×512），每像素颜色代表一种地表类型
 *  - :11-17 七类覆盖颜色映射表；:15 雪线海拔 >3500m 由高程自动算
 *  - :28 森林格顶点 Y 叠加 fbm(x,z) × 1.5 噪声微凸起
 *
 * 〔本单边界〕本单只做契约层（参数 + 纯函数 + 纯 JVM 单测），不写 PNG 解码 / 顶点色接线；
 * 渲染层消费本对象前，标 [待拍板] 的数值须经观感复核。
 */
object TerrainCoverParams {

    /** alpha 通道下限：覆盖叠加透明度的钳制下界，定义性边界（0.0）。 */
    const val MIN_ALPHA: Float = 0.0f

    /** alpha 通道上限：覆盖叠加透明度的钳制上界，定义性边界（1.0）。 */
    const val MAX_ALPHA: Float = 1.0f

    /** 基底透明度：无高程抬升、无纬度压暗时的地表覆盖底色。 // [待拍板] 切片-17 未给基底透明度数值。 */
    const val BASE_ALPHA: Float = 0.12f

    /** 高程暖色增益：归一化高程越高，覆盖层向暖金抬升的透明度增量系数。 // [待拍板] 切片-17 未给增益系数。 */
    const val GOLD_BOOST: Float = 0.55f

    /** 高纬冷色抑制：归一化纬度越高，覆盖层透明度被压暗的系数。 // [待拍板] 切片-17 未给抑制系数。 */
    const val COLD_SUPPRESS: Float = 0.25f

    /** 山脊轮廓描边宽度 dp。 // [待拍板] 切片-17 未给描边宽度数值。 */
    const val RIDGE_STROKE_DP: Int = 1

    /** 绢帛底纹瓦片相对地形网格的缩放倍数。 // [待拍板] 切片-17 未给纸纹缩放倍数。 */
    const val PAPER_TILE_SCALE: Float = 2.0f

    /** 谷底覆盖层透明度：低洼阴影面单独使用的基准透明度。 // [待拍板] 切片-17 未给谷底透明度数值。 */
    const val VALLEY_ALPHA: Float = 0.18f
}

/**
 * 地表覆盖层透明度纯函数：[elevation] 与 [latitude] 均按归一化 0..1 口径传入；
 * NaN 一律按 0 处理，其余入参先钳到 0..1，再按
 * `BASE_ALPHA + elevation * GOLD_BOOST - latitude * COLD_SUPPRESS` 计算，
 * 最终钳制回 [TerrainCoverParams.MIN_ALPHA, TerrainCoverParams.MAX_ALPHA]；不抛异常。
 */
fun terrainCoverAlpha(elevation: Float, latitude: Float): Float {
    val e = if (elevation.isNaN()) 0f else elevation.coerceIn(0f, 1f)
    val lat = if (latitude.isNaN()) 0f else latitude.coerceIn(0f, 1f)
    val alpha = TerrainCoverParams.BASE_ALPHA +
        e * TerrainCoverParams.GOLD_BOOST -
        lat * TerrainCoverParams.COLD_SUPPRESS
    return alpha.coerceIn(TerrainCoverParams.MIN_ALPHA, TerrainCoverParams.MAX_ALPHA)
}
