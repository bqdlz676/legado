package io.legado.app.ui.map.globe.scene

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.GlobeSpecSurface

/**
 * 海洋三层叠加规格（纯 JVM 描述对象，切片 19 / 20）。
 *
 *  1. 底：深蓝 #1B3A5C 球面底色；
 *  2. 镜面高光：太阳方向 × 法线，一道白色镜面亮斑，位置随太阳移动；
 *  3. 浅海 / 海岸：离陆地 3 像素内的海洋格变浅绿松石 #3A7A8A，模拟珊瑚礁。
 *
 * 不需要重型 Water shader，一个 fragment shader 叠加三层即可；
 * 海拔 ≤ 0 的区域自动填海洋色，不用手动切水域。
 */
data class OceanSpec(
    val deepColor: Int = GlobeSpecSurface.OCEAN_DEEP,
    val shallowColor: Int = GlobeSpecSurface.OCEAN_SHALLOW,
    val shallowBorderPx: Int = GlobeConfig.SHALLOW_BORDER_PX,
    val oceanElevationMeters: Double = GlobeConfig.OCEAN_ELEVATION_METERS,
)
