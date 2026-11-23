package io.legado.app.ui.map.sandbox

/**
 * 落文 · 3D 沙盘规格常数收口（切片-06「3D 沙盘 · 氛围渲染」「3D 地图交互」）。
 *
 * 为什么单独收口：切片-06 给了一批硬上限（势力多边形 / 光柱 / 粒子 / 河流 / draw call）
 * 与交互数值（倾角、缩放距离、呼吸周期、信息卡时长），散落到渲染层必然出现「同一上限
 * 两套数字」。本文件只放从切片逐字对齐的数值，纯 Kotlin 无 Android 依赖，JVM 单测可直接引用。
 *
 * 单位口径：地形世界单位（光柱高度 / 雾气距离 / 相机距离）；dp / sp 留给 Compose 壳层换算。
 */
object SandboxConfig {

    // ─────────────────────────── 性能硬上限（切片-06 性能硬上限表）───────────────────────────

    /** 势力多边形同时渲染上限：≤12 个。 */
    const val MAX_POWER_SECTORS: Int = 12

    /** 都城光柱同时竖立上限：≤8 根，其余降级为地面亮点。 */
    const val MAX_CAPITAL_PILLARS: Int = 8

    /** 单根光柱金粉粒子下限（每柱 8-12 粒）。 */
    const val PILLAR_PARTICLES_MIN: Int = 8

    /** 单根光柱金粉粒子上限（每柱 8-12 粒）。 */
    const val PILLAR_PARTICLES_MAX: Int = 12

    /** 光柱粒子总数上限：12 粒 × 8 柱 = 96 粒。 */
    const val MAX_PILLAR_PARTICLES: Int = 96

    /** 河流管同时可见上限：≤8 条。 */
    const val MAX_RIVERS: Int = 8

    /** 城池标签：全部合并进 1 张 atlas 纹理，一次 draw call。 */
    const val LABEL_ATLAS_COUNT: Int = 1

    /** 整场景 draw call 上限：≤20。 */
    const val MAX_DRAW_CALLS: Int = 20

    // ─────────────────────────── 三级城池（切片-06 §二 古城池）───────────────────────────

    /** 都城光柱高度：12 单位。 */
    const val CAPITAL_PILLAR_HEIGHT: Double = 12.0

    /** 郡邑光柱高度：6 单位。 */
    const val COUNTY_PILLAR_HEIGHT: Double = 6.0

    /** 关隘 / 渡口不打柱，只画地面脉冲圆点。 */
    const val PASS_PILLAR_HEIGHT: Double = 0.0

    /** 光柱底部在地表上方浮起的高度：1 单位，不陷进地形。 */
    const val PILLAR_BASE_LIFT: Double = 1.0

    /** 关隘 / 渡口暖金脉冲呼吸周期：1200ms。 */
    const val PASS_PULSE_MS: Long = 1200L

    /** 城池标签字号：8sp（竖排小字，墨色 serif）。 */
    const val LABEL_TEXT_SP: Int = 8

    // ─────────────────────────── 势力色块（切片-06 §一 势力范围）───────────────────────────

    /** 己方势力填充不透明度：0.12。 */
    const val OWN_FILL_ALPHA: Float = 0.12f

    /** 敌方势力填充不透明度：0.10。 */
    const val ENEMY_FILL_ALPHA: Float = 0.10f

    /** 盟友势力填充不透明度：0.08。 */
    const val ALLY_FILL_ALPHA: Float = 0.08f

    /** 中立区域：无填充，只画边界。 */
    const val NEUTRAL_FILL_ALPHA: Float = 0f

    /** 势力边界虚线线宽：1px。 */
    const val POWER_BOUNDARY_WIDTH_PX: Int = 1

    /** 势力交界处渐变流动帧率：2fps（shader 慢速流动，不抢主循环）。 */
    const val BORDER_BLEND_FPS: Int = 2

    // ─────────────────────────── 河流（切片-06 §三 河流）───────────────────────────

    /** 河面反光带流动帧率：2fps。 */
    const val RIVER_FLOW_FPS: Int = 2

    /** 河道 Catmull-Rom 样条每段采样点数（渲染管带用，纯几何可测）。 */
    const val RIVER_SAMPLES_PER_SEGMENT: Int = 12

    // ─────────────────────────── 叠加效果（切片-06 §四 叠加效果）───────────────────────────

    /** 行军尾迹粒子数：8-12 粒渐隐。 */
    const val TRAIL_PARTICLES_MIN: Int = 8
    const val TRAIL_PARTICLES_MAX: Int = 12

    /** 雾气从地形边缘开始的距离：260 单位，制造远景朦胧。 */
    const val FOG_START_UNITS: Double = 260.0

    // ─────────────────────────── 视角操控（切片-06 §3D 地图交互）───────────────────────────

    /** 上下倾斜上限：55°。 */
    const val TILT_MAX_DEG: Double = 55.0

    /** 距地面最近缩放距离：90 单位。 */
    const val CAMERA_MIN_DISTANCE: Double = 90.0

    /** 距地面最远缩放距离：560 单位。 */
    const val CAMERA_MAX_DISTANCE: Double = 560.0

    /** 双击棋子后俯瞰视角倾角：45°。 */
    const val PIECE_FLYOVER_TILT_DEG: Double = 45.0

    // ─────────────────────────── 时间轴 / 推演播放（切片-06）───────────────────────────

    /** 自动播放每回合时长：1.2s。 */
    const val TURN_DURATION_MS: Long = 1200L

    /** 信息卡自动消失时长：3 秒，不遮挡地形。 */
    const val INFO_CARD_TTL_MS: Long = 3000L

    /** 拖拽预演虚线的临时意图标记存活时长（无新指令前保留）。 */
    const val PREVIEW_LINE_TTL_MS: Long = 60_000L

    // ─────────────────────────── 测距换算（切片-06：双棋子捏合显示距离 / 行军天数）───────────────────────────

    /** 一里约合公里数（沿用大地测量惯例：1 里 = 0.5 km）。 */
    const val KM_PER_LI: Double = 0.5

    /** 日行里数三档：30 / 50 / 80 里。 */
    const val DAILY_LI_SLOW: Int = 30
    const val DAILY_LI_MARCH: Int = 50
    const val DAILY_LI_FAST: Int = 80

    /** 地球半径（公里），haversine 测距用。 */
    const val EARTH_RADIUS_KM: Double = 6371.0
}
