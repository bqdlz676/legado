package io.legado.app.ui.globe.core

/**
 * 落文 · 地球仪渲染核心 —— 规格常量收口（切片 17 / 19 / 20）
 *
 * 本文件只放从规格切片逐字对齐的数值；渲染层与纯逻辑层都从这里取，
 * 不允许在 shader / mesh / 输入层里散落魔法数字。
 *
 * 纯 Kotlin：无 Android 类型依赖，JVM 单测可直接引用。
 */
object GlobeConfig {

    // ---- 球体几何（切片 20：SphereGeometry(100, 128, 128)）----
    const val RADIUS: Float = 100f

    // ---- 地轴倾斜（切片 19：23.5°，光照基于倾斜后法线）----
    const val AXIAL_TILT_DEG: Double = 23.5

    // ---- 大气层三层（切片 19 / 20）----
    const val ATMOS_INNER_RADIUS: Float = 100.5f   // 菲涅尔半透壳，宽约 3 单位
    const val ATMOS_OUTER_RADIUS: Float = 104f     // 极淡蓝光晕壳
    const val ATMOS_GLOW_ALPHA: Float = 0.08f      // 外圈光晕透明度
    const val RAYLEIGH_HEIGHT: Float = 15f         // 近地面 0–15 单位简化散射

    // ---- 云层（切片 19 / 20）----
    const val CLOUD_RADIUS: Float = 101f
    const val CLOUD_SPEED_FACTOR: Float = 0.9f     // 云层自转 = 地球自转 ×0.9
    const val CLOUD_OPACITY: Float = 0.3f
    const val CLOUD_SHADOW_ALPHA: Float = 0.5f     // 云纹理 alpha > 0.5 投阴影
    const val CLOUD_SHADOW_DARKEN: Float = 0.95f   // 地面亮度 ×0.95
    const val CLOUD_SHADOW_OPACITY: Float = 0.05f  // 切片 19：遮暗地面色 opacity 0.05
    const val CLOUD_SEGMENTS: Int = 64             // 云壳不需要高精度

    // ---- 星空（切片 19：半径 900，固定不动）----
    const val STAR_RADIUS: Float = 900f
    const val STAR_COUNT: Int = 900

    // ---- 太阳（切片 19 / 20：直径 8，距离锁定 400，按 UTC 算位置）----
    const val SUN_DISTANCE: Float = 400f
    const val SUN_DISC_DIAMETER: Float = 8f

    // ---- 昼夜 / 晨昏线（切片 19 / 20）----
    const val NIGHT_BRIGHTNESS: Float = 0.15f      // 暗半球亮度系数
    const val TERMINATOR_BAND_DEG: Double = 5.0    // 晨昏渐变带宽，不是硬切

    // ---- 雪线（切片 17 / 20：海拔 > 3500m 自动积雪）----
    const val SNOWLINE_METERS: Double = 3500.0

    // ---- 极地冰盖（切片 19：|纬度| > 66.5° 且覆盖图 = 冰）----
    const val POLAR_LAT_DEG: Double = 66.5
    const val ICE_SPECULAR: Float = 0.15f

    // ---- 海洋（切片 19 / 20）----
    val OCEAN_DEEP: Int = 0x1B3A5C
    val OCEAN_SHALLOW: Int = 0x3A7A8A
    const val SHALLOW_BORDER_PX: Int = 3           // 离陆地 3 像素内变浅海色
    const val OCEAN_ELEVATION_METERS: Double = 0.0 // 海拔 ≤ 0 自动填海洋色

    // ---- 交互（切片 19 / 20）----
    const val AUTO_ROTATE_DEG_PER_SEC: Double = 0.2
    const val INERTIA_DECAY_SECONDS: Double = 0.8  // 松手惯性 0.8s 衰减到 0
    const val CAMERA_MIN_DISTANCE_RATIO: Float = 0.6f
    const val CAMERA_MAX_DISTANCE_RATIO: Float = 6.0f

    // ---- LOD 距离阈值（按相机到球心距离 / 球半径，切片 20 / 19）----
    const val LOD_FAR_RATIO: Float = 3.0f          // >3R → LOD0
    const val LOD_NEAR_RATIO: Float = 1.0f         // 1R–3R → LOD1；<1R → LOD2
    const val LOD_MORPH_FRAMES: Int = 3            // 2–3 帧内完成 morph

    // ---- 性能红线（切片 19 / 20 / 08）----
    const val TARGET_FPS_MOBILE: Int = 55
    const val TARGET_FPS_DESKTOP: Int = 120
    const val FIRST_LOAD_MS: Int = 800
    const val MAX_DRAW_CALLS: Int = 15

    // ---- 东方美学 12 色 token（切片 03；UI 叠加层 / 信息面板使用，不用于天空大片靛蓝紫）----
    const val TOKEN_BG_PAGE: Int = 0xF7F1E6
    const val TOKEN_BG_SURFACE: Int = 0xEFE8D8
    const val TOKEN_TEXT_PRIMARY: Int = 0x1A1410
    const val TOKEN_TEXT_SECONDARY: Int = 0x5C4F3A
    const val TOKEN_TEXT_TERTIARY: Int = 0x9B9687
    const val TOKEN_TEXT_ACCENT: Int = 0xC23B2E
    const val TOKEN_BRAND_GOLD: Int = 0xD4A54A
    const val TOKEN_BRAND_COPPER: Int = 0x8B6914
    const val TOKEN_COLOR_LINK: Int = 0xC23B2E

    // 暖金辉光 rgba(212,165,74,0.3)
    const val GLOW_RED: Int = 212
    const val GLOW_GREEN: Int = 165
    const val GLOW_BLUE: Int = 74
    const val GLOW_ALPHA: Float = 0.3f
}
