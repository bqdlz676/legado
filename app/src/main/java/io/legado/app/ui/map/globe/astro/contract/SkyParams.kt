package io.legado.app.ui.map.globe.astro.contract

/**
 * 星空天象层 · 参数单源（纯 JVM，无 Android / Compose 依赖，普通 JVM 单测可直接断言）。
 *
 * 〔规格出处〕`docs/星空天象层-实施规格-合并定稿.md` §三.1、§五、§十、§十六。
 * 〔为什么集中一处〕相机上限、星等极限、尺寸线宽字号、动效时长只在本对象登记；
 * 渲染 / 数据 / 接线各层只引用本表，不在画面里另写魔法数字，避免契约层与画面三处口径漂移。
 * 〔本单边界〕本对象只登记数值与三条连续纯函数；token 颜色解析、相机 6R→10R 放宽、
 * `FrameSnapshot` 装配均属接线单域，本单不改任何既有文件。
 *
 * 〔单位后缀约定〕`_PX` 屏幕像素 / `_DP` 密度无关像素 / `_SP` 字号 / `_DEG` 角度 /
 * `_RATIO` 无量纲比率 / `_MS` 毫秒 / `_LEVELS` 级数。
 */
object SkyParams {

    // ─────────────────────────── §十六 投影与缩放锚点 ───────────────────────────

    /**
     * 投影深度常量（§十六.1）：遮挡排序基准，不是几何球半径（§八.5）。
     * 天球是跟随相机的 2D 投影背景，不做球壳网格（§〇.9）。
     */
    const val SKY_PROJECTION_FAR: Double = 900.0

    /**
     * `skyBlend` 终点锚：地球视觉直径 40px（§十六.2、§十.5）。
     * 为什么只是锚不是开关：它是 blend=1 一侧的连续坐标点，到达该值之前星空 alpha
     * 已连续升起；不存在“到 40px 才切星空”的硬阈值（§十.4）。
     */
    const val SKY_ENTER_EARTH_DIAMETER_PX: Float = 40f

    /** blend=0 一侧地球视觉半径 200px（§十.3：地球视觉半径 200px→40px 连续缩）。 */
    const val EARTH_RADIUS_PX_FULL: Float = 200f

    /** blend=0 一侧地球视觉直径 400px：由半径 200px 算术派生（2×200），非新设计值。 */
    const val EARTH_DIAMETER_PX_FULL: Float = 400f

    /** 星空淡入与地球缩点共用时长 600ms（§十六.3），与回放节奏一致。 */
    const val SKY_TRANSITION_MS: Long = 600L

    /** 天外 ↔ 地上相机飞行时长 800ms（§十六.4、§八.12），spring 收尾，禁 linear。 */
    const val VIEW_SWITCH_MS: Long = 800L

    /** `skyBlend` 达 1 的等效缩放标量 12.0（§十六.14）：给连续过渡留可见段。 */
    const val SKY_BLEND_FULL_ZOOM: Float = 12.0f

    // ─────────────────────────── §十六 星等深度 ───────────────────────────

    /** 深度上限 11.5 等：用户拍板值，写死不许改（§十六.5）。 */
    const val MAG_DEPTH_LIMIT: Float = 11.5f

    /** 实体星与密度层分界 6.5 等：肉眼极限 = Tycho 覆盖起点（§十六.6）。 */
    const val MAG_BRIGHT_ENTITY: Float = 6.5f

    /** 金字塔级数 2：9 等、11.5 等两级，级间 alpha 线性混合实现无档感（§十六.7）。 */
    const val PYRAMID_LEVELS: Int = 2

    /** 四芒阈值：只给 mag < 0.5 的最亮约 16 颗（§十六.8、§八.8）。 */
    const val STAR_SPIKE_MAG: Float = 0.5f

    /** 闪烁阈值：只给 mag < 2.5；暗星闪烁会成噪点（§十六.9、§八.8）。 */
    const val TWINKLE_MAG: Float = 2.5f

    // ─────────────────────────── §十六 动效与天象计时 ───────────────────────────

    /** 分野脉冲周期 1200ms：与 `MARKER_PULSE_PERIOD_MS` 同值，同一脉冲语言（§十六.10）。 */
    const val FIELD_PULSE_MS: Long = 1200L

    /** 流星随机间隔下限 90s（§十六.11），区间便于测试注入。 */
    const val METEOR_INTERVAL_MIN_MS: Long = 90_000L

    /** 流星随机间隔上限 240s（§十六.11）。 */
    const val METEOR_INTERVAL_MAX_MS: Long = 240_000L

    /** 地上观舒适视场 60°：再宽边缘畸变（§十六.12、§八.11）。 */
    const val GROUND_VIEW_FOV_DEG: Double = 60.0

    /** 地平上方暖辉带宽 5°：与晨昏线渐变带同一语言（§十六.13、§八.11）。 */
    const val OBSERVER_HORIZON_GLOW_DEG: Double = 5.0

    // ─────────────────────────── §五.1 星点直径锚点（屏幕 px，非 dp）───────────────────────────

    /** 星点为什么用 px：光点不是控件，px 才能在任何 dpi 下保持同一张星图观感（§五.2）。 */

    /** 亮端锚星等 −1.5（§五.1）。 */
    const val MAG_ANCHOR_VERY_BRIGHT: Float = -1.5f

    /** mag ≤ −1.5 星点直径 9px（§五.1）。 */
    const val STAR_DIAMETER_VERY_BRIGHT_PX: Float = 9f

    /** 锚星等 0（§五.1）。 */
    const val MAG_ANCHOR_0: Float = 0f

    /** mag = 0 星点直径 5px（§五.1）。 */
    const val STAR_DIAMETER_0_PX: Float = 5f

    /** 锚星等 2（§五.1）。 */
    const val MAG_ANCHOR_2: Float = 2f

    /** mag = 2 星点直径 3px（§五.1）。 */
    const val STAR_DIAMETER_2_PX: Float = 3f

    /** 锚星等 4.5（§五.1）。 */
    const val MAG_ANCHOR_4_5: Float = 4.5f

    /** mag = 4.5 星点直径 1.5px（§五.1）。 */
    const val STAR_DIAMETER_4_5_PX: Float = 1.5f

    /** 锚星等 6.5（§五.1）。 */
    const val MAG_ANCHOR_6_5: Float = 6.5f

    /** mag = 6.5 星点直径 1px（§五.1）。 */
    const val STAR_DIAMETER_6_5_PX: Float = 1.0f

    /** 金字塔层直径下限 0.8px：>6.5 不再变小，用 alpha 补偿（§五.1）。 */
    const val STAR_DIAMETER_FLOOR_PX: Float = 0.8f

    // ─────────────────────────── §五.3/4 线宽与分层 alpha ───────────────────────────

    /** 星座连线宽 0.8px（§五.3）：辅助参考层，最淡，不抢金。 */
    const val CONSTELLATION_LINE_WIDTH_PX: Float = 0.8f

    /** 星座连线 alpha 0.45（§五.3），取 `textTertiary` 云灰。 */
    const val CONSTELLATION_LINE_ALPHA_RATIO: Float = 0.45f

    /** 星官连线宽 1.0px（§五.3）：内容层，比重于星座线。 */
    const val ASTERISM_LINE_WIDTH_PX: Float = 1.0f

    /** 星官连线 alpha 0.55（§五.3），取 `brandGold` 暖金。 */
    const val ASTERISM_LINE_ALPHA_RATIO: Float = 0.55f

    /** 分野边界宽 1.5px（§五.3）：联动锚，最重一线。 */
    const val FIELD_BORDER_WIDTH_PX: Float = 1.5f

    /** 分野边虚线 dash 6px（§五.3）。 */
    const val FIELD_BORDER_DASH_PX: Float = 6f

    /** 分野边虚线 gap 4px（§五.3）。 */
    const val FIELD_BORDER_GAP_PX: Float = 4f

    /** 赤经 / 赤纬网格宽 0.8px（§五.4）。 */
    const val GRID_LINE_WIDTH_PX: Float = 0.8f

    /** 网格 alpha 0.12（§六.9），取 `textTertiary`。 */
    const val GRID_LINE_ALPHA_RATIO: Float = 0.12f

    /** 黄道宽 1.0px（§五.4）。 */
    const val ECLIPTIC_LINE_WIDTH_PX: Float = 1.0f

    /** 黄道 alpha 0.30（§六.9），取 `brandGold`。 */
    const val ECLIPTIC_LINE_ALPHA_RATIO: Float = 0.30f

    /** 天赤道宽 1.0px（§五.4）：基准线专用。 */
    const val EQUATOR_LINE_WIDTH_PX: Float = 1.0f

    /** 天赤道 alpha 0.30（§六.9），取 `stoneBlue`，用途须在切片-26 声明。 */
    const val EQUATOR_LINE_ALPHA_RATIO: Float = 0.30f

    // ─────────────────────────── §五.5 字号（全部 ≥14sp）───────────────────────────

    /** 星官名 / 宿名字号 14sp 竖排（§五.5）。 */
    const val STAR_LABEL_FONT_SP: Float = 14f

    /** 星座名字号 14sp 横排，拉丁名与中文名两行（§五.5）。 */
    const val CONSTELLATION_NAME_FONT_SP: Float = 14f

    /** Sheet 标题字号 16sp（§五.5）。 */
    const val SHEET_TITLE_FONT_SP: Float = 16f

    /** Sheet 正文字号 14sp（§五.5）。 */
    const val SHEET_BODY_FONT_SP: Float = 14f

    /** Sheet 出处字号 14sp：用 `textTertiary` 弱化，不缩字号（§五.5）。 */
    const val SHEET_SOURCE_FONT_SP: Float = 14f

    // ─────────────────────────── §五.6/7 间距、圆角、Sheet ───────────────────────────

    /** 标签距星点 8dp，与 `BUBBLE_GAP_DP` 同值（§五.6）。 */
    const val LABEL_GAP_DP: Float = 8f

    /** 可点标签热区边长 ≥48dp（UX-03 下限；视觉 14sp 高，透明外扩）（§五.6）。 */
    const val HIT_ZONE_SIDE_DP: Float = 48f

    /** Sheet 圆角 20dp（§五.7）。 */
    const val SHEET_RADIUS_DP: Float = 20f

    /** 星名印框圆角 2dp：刻意硬边像印章，不套 16dp 卡片圆角（§五.7、§十五.1）。 */
    const val STAR_NAME_BOX_RADIUS_DP: Float = 2f

    /** Sheet 列表 spacedBy 8dp（§五.7）。 */
    const val LIST_SPACING_DP: Float = 8f

    /** Sheet 第一段 detent 30%（§五.7）。 */
    const val SHEET_DETENT_FRACTION_1: Float = 0.30f

    /** Sheet 第二段 detent 60%（§五.7）。 */
    const val SHEET_DETENT_FRACTION_2: Float = 0.60f

    /** Sheet 第三段 detent 90%（§五.7）。 */
    const val SHEET_DETENT_FRACTION_3: Float = 0.90f

    /** Sheet 关闭阈值 0.12（§五.7）。 */
    const val SHEET_CLOSE_THRESHOLD_RATIO: Float = 0.12f

    // ─────────────────────────── §六 叠加层派生比率（token 解析归接线单）───────────────────────────

    /** 夜空底 = `textPrimary` 压暗 40%（§六.4）：由函数算，禁写死；墨色偏暖，压暗仍暖墨。 */
    const val SKY_NIGHT_BASE_DARKEN_RATIO: Float = 0.40f

    /** 地平暖辉 = `brandCopper` alpha 0.10（§六.5）。 */
    const val SKY_HORIZON_GLOW_ALPHA_RATIO: Float = 0.10f

    /** 星官名标签底色 = `bgOverlay` alpha 0.45（§六.7）；字 `brandGold`、边 1px `brandCopper`。 */
    const val ASTERISM_LABEL_BG_ALPHA_RATIO: Float = 0.45f

    /** 禁用态 alpha 0.30（§六.10，`DISABLED_OPACITY`）。 */
    const val DISABLED_ALPHA_RATIO: Float = 0.30f

    // ─────────────────────────── 连续纯函数（§十.3、§十.9）───────────────────────────

    /**
     * 星等 → 星点直径 px（§五.1 锚点表，锚点间按 Pogson 线性插值）。
     * 纯函数、单调不增；非有限输入回落保底直径，不抛异常。
     */
    fun starDiameterPx(mag: Float): Float {
        if (!mag.isFinite()) return STAR_DIAMETER_FLOOR_PX
        if (mag <= MAG_ANCHOR_VERY_BRIGHT) return STAR_DIAMETER_VERY_BRIGHT_PX
        // 严格大于 6.5 才进金字塔层；6.5 锚点本身保留 1px（§五.1）。
        if (mag > MAG_ANCHOR_6_5) return STAR_DIAMETER_FLOOR_PX
        if (mag <= MAG_ANCHOR_0) {
            val t = (mag - MAG_ANCHOR_VERY_BRIGHT) / (MAG_ANCHOR_0 - MAG_ANCHOR_VERY_BRIGHT)
            return lerp(STAR_DIAMETER_VERY_BRIGHT_PX, STAR_DIAMETER_0_PX, t)
        }
        if (mag <= MAG_ANCHOR_2) {
            val t = (mag - MAG_ANCHOR_0) / (MAG_ANCHOR_2 - MAG_ANCHOR_0)
            return lerp(STAR_DIAMETER_0_PX, STAR_DIAMETER_2_PX, t)
        }
        if (mag <= MAG_ANCHOR_4_5) {
            val t = (mag - MAG_ANCHOR_2) / (MAG_ANCHOR_4_5 - MAG_ANCHOR_2)
            return lerp(STAR_DIAMETER_2_PX, STAR_DIAMETER_4_5_PX, t)
        }
        val t = (mag - MAG_ANCHOR_4_5) / (MAG_ANCHOR_6_5 - MAG_ANCHOR_4_5)
        return lerp(STAR_DIAMETER_4_5_PX, STAR_DIAMETER_6_5_PX, t)
    }

    /**
     * 地球视觉直径 px → `skyBlend`（0→1）（§十.3、§十.5）。
     * 纯函数、连续、反向完全对称：直径回到 400px 一侧 blend 归 0，缩到 40px 锚 blend 归 1；
     * 越界钳到 [0,1]，不存在开关式跳变。非有限输入回落 0。
     */
    fun skyBlendFromDiameter(px: Float): Float {
        if (!px.isFinite()) return 0f
        if (px >= EARTH_DIAMETER_PX_FULL) return 0f
        if (px <= SKY_ENTER_EARTH_DIAMETER_PX) return 1f
        return (EARTH_DIAMETER_PX_FULL - px) /
            (EARTH_DIAMETER_PX_FULL - SKY_ENTER_EARTH_DIAMETER_PX)
    }

    /**
     * `skyBlend` → 星等上限（§十.9）：blend 0 → 6.5 等实体层，blend 1 → 11.5 等深度上限。
     * 纯函数、在 blend∈[0,1] 上单调递增、无阈值跳变；非有限输入回落 6.5。
     */
    fun magLimitFromBlend(blend: Float): Float {
        val b = if (blend.isFinite()) blend.coerceIn(0f, 1f) else 0f
        return MAG_BRIGHT_ENTITY + (MAG_DEPTH_LIMIT - MAG_BRIGHT_ENTITY) * b
    }

    private fun lerp(a: Float, b: Float, tIn: Float): Float {
        val t = tIn.coerceIn(0f, 1f)
        return a + (b - a) * t
    }
}
