package io.legado.app.ui.map.globe

import io.legado.app.ui.map.globe.contract.GlobeParams

/**
 * 落文 · 3D 地球仪规格常量收口（切片 17 / 18 / 19 / 20）。
 *
 * 为什么单独收口：切片里的几何半径、LOD 阈值、性能红线散落在四处，渲染层与纯逻辑层
 * 都要引用；若在 shader / 网格 / 输入层各自写魔法数字，调参时必然出现口径不一致。
 * 本文件只放从规格逐字对齐的数值，所有模块从这里取，不允许就地再写一遍。
 *
 * 〔单源边界〕2026-11-25 拍板四值（自转周期 / 球面半径 / 标记点直径 / 脉冲周期）唯一源在
 * [GlobeParams]；本对象只做转发或派生，不再持有独立字面量，避免契约层与本文件两处漂移。
 *
 * 纯 Kotlin：不引用任何 Android 类型，JVM 单测可直接断言。
 */
object GlobeConfig {

    // ───────────────────── 球体几何（切片 20：SphereGeometry(100, 128, 128)）─────────────────────
    /** 渲染球半径（世界单位）；转发 [GlobeParams.RADIUS_DP]，本处不另写字面量（单源在契约层）。 */
    val RADIUS: Float get() = GlobeParams.RADIUS_DP

    /** 地轴倾斜 23.5°（切片 19：光照计算基于倾斜后的球面法线方向）。 */
    const val AXIAL_TILT_DEG: Double = 23.5

    // ───────────────────── 大气层三层（切片 19 / 20）─────────────────────
    /** 内圈菲涅尔半透壳半径，宽度约 3 单位。 */
    const val ATMOS_INNER_RADIUS: Float = 100.5f

    /** 外圈极淡蓝光晕壳半径。 */
    const val ATMOS_OUTER_RADIUS: Float = 104f

    /** 外圈光晕透明度 0.08。 */
    const val ATMOS_GLOW_ALPHA: Float = 0.08f

    /** 近地面 0–15 单位高度内的简化 Rayleigh 散射层。 */
    const val RAYLEIGH_HEIGHT: Float = 15f

    // ───────────────────── 云层（切片 19 / 20）─────────────────────
    const val CLOUD_RADIUS: Float = 101f

    /** 云层自转速度 = 地球自转 ×0.9（风云与地面不同步）。 */
    const val CLOUD_SPEED_FACTOR: Float = 0.9f

    const val CLOUD_OPACITY: Float = 0.3f

    /** 云纹理 alpha > 0.5 视为厚云，向地面投阴影。 */
    const val CLOUD_SHADOW_ALPHA: Float = 0.5f

    /** 厚云区地面亮度 ×0.95。 */
    const val CLOUD_SHADOW_DARKEN: Float = 0.95f

    /** 云层遮暗地面色的不透明度 0.05。 */
    const val CLOUD_SHADOW_OPACITY: Float = 0.05f

    /** 云壳不需要高精度，64×64。 */
    const val CLOUD_SEGMENTS: Int = 64

    // ───────────────────── 星空（切片 19：半径 900，固定不动）─────────────────────
    const val STAR_RADIUS: Float = 900f
    const val STAR_COUNT: Int = 900

    // ───────────────────── 太阳（切片 19 / 20：直径 8，距离锁定 400，按 UTC 算位置）─────────────────────
    const val SUN_DISTANCE: Float = 400f
    const val SUN_DISC_DIAMETER: Float = 8f

    // ───────────────────── 昼夜 / 晨昏线（切片 19 / 20）─────────────────────
    /** 暗半球亮度系数 0.15。 */
    const val NIGHT_BRIGHTNESS: Float = 0.15f

    /** 晨昏渐变带宽 5°，不是硬切。 */
    const val TERMINATOR_BAND_DEG: Double = 5.0

    // ───────────────────── 雪线（切片 17 / 20：海拔 > 3500m 自动积雪）─────────────────────
    const val SNOWLINE_METERS: Double = 3500.0

    // ───────────────────── 极地冰盖（切片 19：|纬度| > 66.5° 且覆盖图 = 冰）─────────────────────
    const val POLAR_LAT_DEG: Double = 66.5
    const val ICE_SPECULAR: Float = 0.15f

    // ───────────────────── 海洋（切片 19 / 20）─────────────────────
    /** 离陆地 3 像素内的海洋格变浅海色。 */
    const val SHALLOW_BORDER_PX: Int = 3

    /** 海拔 ≤ 0 自动填海洋色，不用手动切水域。 */
    const val OCEAN_ELEVATION_METERS: Double = 0.0

    // ───────────────────── 交互（切片 19 / 20）─────────────────────
    /**
     * 自动旋转角速度（°/s）；由 [GlobeParams.ROTATION_PERIOD_MS] 派生：
     * 360° ÷ 周期秒数（= 360_000 / 周期 ms）。契约层登记周期，本处只换算速率，不另写 0.2 字面量。
     */
    val AUTO_ROTATE_DEG_PER_SEC: Double get() =
        360.0 / (GlobeParams.ROTATION_PERIOD_MS / 1000.0)

    /** 松手后惯性 0.8s 内衰减到 0。 */
    const val INERTIA_DECAY_SECONDS: Double = 0.8

    /** 松手角速度上限（°/s）：防点按 / 极快抖动折算出超远滑行，手感兜底钳制。 */
    const val INERTIA_MAX_VEL_DEG_PER_SEC: Double = 720.0

    /** 首帧拿不到实测拖动时长时，按一帧 1/60s 折算松手角速度的兜底帧长。 */
    const val INERTIA_FALLBACK_FRAME_SEC: Double = 1.0 / 60.0

    const val CAMERA_MIN_DISTANCE_RATIO: Float = 0.6f

    /**
     * 相机最远缩放比（球半径倍数）。§十.1：6.0R → 10.0R。
     * 为什么必须放宽：6R 时地球角径约 18.9°，60° 视场 / 1080px 下约 340px，
     * 缩不到 40px 的星点锚（§十.2）；10R 之后不再推相机，改由 `skyBlend` 连续标量收尾（§十.3）。
     */
    const val CAMERA_MAX_DISTANCE_RATIO: Float = 10.0f

    // ───────────────────── LOD 距离阈值（相机距离 / 球半径，切片 20 / 19）─────────────────────
    /** >3R → LOD0。 */
    const val LOD_FAR_RATIO: Float = 3.0f

    /** 1R–3R → LOD1；<1R → LOD2。 */
    const val LOD_NEAR_RATIO: Float = 1.0f

    /** LOD 切换 GPU morph 帧数（2–3 帧内完成）。 */
    const val LOD_MORPH_FRAMES: Int = 3

    // ───────────────────── 性能红线（切片 19 / 20 / 08）─────────────────────
    const val TARGET_FPS_MOBILE: Int = 55
    const val TARGET_FPS_DESKTOP: Int = 120

    /** 首次打开 2K 纹理本地缓存，≤800ms。 */
    const val FIRST_LOAD_MS: Int = 800

    /** 手机端 draw call 上限 15。 */
    const val MAX_DRAW_CALLS: Int = 15

    // ───────────────────── 标记点（契约层拍板值转发，唯一源见 GlobeParams）─────────────────────
    /** 标记点直径 dp；转发 [GlobeParams.MARKER_DOT_DIAMETER_DP]，渲染层不得另写尺寸字面量。 */
    val MARKER_DOT_DIAMETER_DP: Float get() = GlobeParams.MARKER_DOT_DIAMETER_DP

    /** 标记点脉冲周期 ms；转发 [GlobeParams.MARKER_PULSE_PERIOD_MS]，呼吸光晕相位按此周期取模。 */
    val MARKER_PULSE_PERIOD_MS: Long get() = GlobeParams.MARKER_PULSE_PERIOD_MS

    // ───────────────────── 战役回放（切片 18 / 20）─────────────────────
    /** 高圆弧凸起球面 1.5 单位，贴得紧又不被球面遮挡。 */
    const val ARC_LIFT_UNITS: Double = 1.5

    /** 势力多边形贴球壳半径，略高于地表。 */
    const val POWER_SHELL_RADIUS: Float = 100.2f

    /** 城池光针沿法线穿出高度 4 单位。 */
    const val NEEDLE_HEIGHT: Float = 4.0f

    /** 光针顶部金点半径。 */
    const val NEEDLE_TOP_DOT_RADIUS: Float = 0.9f

    /** 势力采样 128×128 球面网格。 */
    const val POWER_GRID_STEPS: Int = 128

    /** 每段大圆高圆弧细分采样数。 */
    const val ARC_SAMPLES_PER_SEGMENT: Int = 24

    /** 每条路线流动金色粒子数。 */
    const val PARTICLES_PER_ROUTE: Int = 12

    const val ARC_WIDTH: Float = 0.55f
    const val ARROW_HEAD_SIZE: Float = 1.6f

    /** 势力色块不透明度：己方暖金 0.12 / 敌方灰青 0.10 / 盟友铜色 0.08（切片 20）。 */
    const val POWER_OWN_ALPHA: Float = 0.12f
    const val POWER_ENEMY_ALPHA: Float = 0.10f
    const val POWER_ALLY_ALPHA: Float = 0.08f

    /** 日行里数三档：30 山地辎重 / 50 常行军 / 80 轻骑海路急行。 */
    const val DAILY_LI_SLOW: Int = 30
    const val DAILY_LI_MARCH: Int = 50
    const val DAILY_LI_FAST: Int = 80

    /** 1 里 = 0.5 km。 */
    const val KM_PER_LI: Double = 0.5
    const val EARTH_RADIUS_KM: Double = 6371.0
}
