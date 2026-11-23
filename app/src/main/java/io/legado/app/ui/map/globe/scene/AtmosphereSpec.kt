package io.legado.app.ui.map.globe.scene

import io.legado.app.ui.map.globe.GlobeConfig

/**
 * 大气层 + 云层渲染规格（纯 JVM 描述对象，切片 19 / 20）。
 *
 * 大气层三层：
 *  - 内圈：半径 100.5 半透明白壳，菲涅尔 shader——正对镜头完全透明，边缘泛淡蓝白光，宽约 3 单位；
 *  - 外圈：半径 104 极淡蓝光晕，透明度 0.08，辉光散射，边缘虚入深空；
 *  - 底层：地面以上 0–15 单位极淡散射蓝（Rayleigh 简化），远处地面比近处略偏蓝微灰。
 *
 * 大气不是贴图，是实时 shader；地球旋转时大气跟着地球走，不跟相机走。
 */
data class AtmosphereSpec(
    val innerRadius: Float = GlobeConfig.ATMOS_INNER_RADIUS,
    val outerRadius: Float = GlobeConfig.ATMOS_OUTER_RADIUS,
    val outerGlowAlpha: Float = GlobeConfig.ATMOS_GLOW_ALPHA,
    val rayleighHeight: Float = GlobeConfig.RAYLEIGH_HEIGHT,
)

/**
 * 云层规格（切片 19 / 20）：半径 101 半透明白壳，独立 0.9 倍速旋转，
 * opacity 0.3；厚云区偏亮，薄云区几乎不可见；云 alpha > 0.5 时向地面投 0.95 亮度阴影。
 */
data class CloudSpec(
    val radius: Float = GlobeConfig.CLOUD_RADIUS,
    val speedFactor: Float = GlobeConfig.CLOUD_SPEED_FACTOR,
    val opacity: Float = GlobeConfig.CLOUD_OPACITY,
    val shadowAlpha: Float = GlobeConfig.CLOUD_SHADOW_ALPHA,
    val shadowDarken: Float = GlobeConfig.CLOUD_SHADOW_DARKEN,
    val shadowOpacity: Float = GlobeConfig.CLOUD_SHADOW_OPACITY,
    val segments: Int = GlobeConfig.CLOUD_SEGMENTS,
)
