package io.legado.app.ui.map.replay

/**
 * 落文 · 3D 战役回放规格常数收口（切片-07「3D 战役回放 · 完整视觉规格」）。
 *
 * 为什么单独收口：切片-07 对出生编排、呼吸周期、溃散时长、交战两阶段、光轨转速等
 * 给了逐段毫秒值，散落到动画 / 状态层必然对不上。本文件只放逐字对齐的数值，
 * 纯 Kotlin 无 Android 依赖，JVM 单测可直接引用。
 */
object ReplayConfig {

    // ─────────────────────────── 第一帧出生编排（切片-07 第一帧加载）───────────────────────────

    /** 0-400ms：黑底浮金色经纬网格，线从暗到亮。 */
    const val BIRTH_GRID_END_MS: Long = 400L

    /** 400-800ms：地形从网格下方缓缓隆起（平面→丘陵→山脊→河谷）。 */
    const val BIRTH_RISE_END_MS: Long = 800L

    /** 800-1000ms：贴地雾气沉入河谷，山顶露出。 */
    const val BIRTH_FOG_END_MS: Long = 1000L

    /** 1000-1200ms：棋子从天而降落位，落地震颤 + 金色涟漪。 */
    const val BIRTH_DROP_END_MS: Long = 1200L

    /** 1200ms：顶部标题淡入，底部面板从下方滑入；此后出生编排结束。 */
    const val BIRTH_READY_MS: Long = 1200L

    /** 棋子落地涟漪扩散半径：80dp。 */
    const val LANDING_RIPPLE_DP: Float = 80f

    // ─────────────────────────── 顶栏 / 底栏自动隐藏（切片-07）───────────────────────────

    /** 顶栏 3 秒不碰屏幕自动淡出，点画面再淡入。 */
    const val TOP_BAR_IDLE_MS: Long = 3000L

    /** 底栏 3 秒无操作收缩成一条 4dp 高金线；再点金线展开。 */
    const val BOTTOM_BAR_IDLE_MS: Long = 3000L

    /** 收起状态底栏高度：4dp 金线。 */
    const val COLLAPSED_BAR_DP: Float = 4f

    /** 战役名字号：16sp；年代字号：12sp。 */
    const val TITLE_SP: Int = 16
    const val ERA_SP: Int = 12

    // ─────────────────────────── 推演时钟（切片-07 底部控制栏 / 切片-06 推演播放）───────────────────────────

    /** 自动播放每回合时长：1.2s。 */
    const val TURN_DURATION_MS: Long = 1200L

    /** 信息卡自动消失：3 秒。 */
    const val INFO_CARD_TTL_MS: Long = 3000L

    // ─────────────────────────── 军队棋子（切片-07 军队棋子）───────────────────────────

    /** 底部发光圆环离地高度：3 单位。 */
    const val RING_LIFT_UNITS: Double = 3.0

    /** 顶部竖直细光线向上延伸：8 单位。 */
    const val TOP_LIGHT_UNITS: Double = 8.0

    /** 常态呼吸周期：1200ms。 */
    const val BREATH_MS: Long = 1200L

    /** 选中态呼吸加快：400ms 周期。 */
    const val SELECTED_BREATH_MS: Long = 400L

    /** 主力棋子相对偏师的尺寸倍率：1.5 倍。 */
    const val MAIN_FORCE_SCALE: Double = 1.5

    /** 动摇态微颤幅度：0.5°。 */
    const val WAVER_JITTER_DEG: Double = 0.5

    /** 溃散碎片数下限：6 片。 */
    const val ROUT_SHARDS_MIN: Int = 6

    /** 溃散碎片数上限：8 片。 */
    const val ROUT_SHARDS_MAX: Int = 8

    /** 溃散飘散消失时长：800ms。 */
    const val ROUT_DURATION_MS: Long = 800L

    /** 设伏态棋子虚化透明度：0.3。 */
    const val AMBUSH_OPACITY: Float = 0.3f

    // ─────────────────────────── 势力 / 战线多边形（契约 §1.5 E 势力色）───────────────────────────

    /** 己方势力填充不透明度：0.12（暖金半透明）。 */
    const val OWN_FILL_ALPHA: Float = 0.12f

    /** 敌方势力填充不透明度：0.10（灰青半透明）。 */
    const val ENEMY_FILL_ALPHA: Float = 0.10f

    /** 盟友势力填充不透明度：0.08（铜色半透明）。 */
    const val ALLY_FILL_ALPHA: Float = 0.08f

    // ─────────────────────────── 箭头兵锋（切片-07 箭头）───────────────────────────

    /** 箭身内部流动金色光粒子：2-3 粒。 */
    const val ARROW_PARTICLES_MIN: Int = 2
    const val ARROW_PARTICLES_MAX: Int = 3

    /** 两军相遇朱砂光环时长：300ms。 */
    const val ENCOUNTER_RING_MS: Long = 300L

    // ─────────────────────────── 交战场面（切片-07 交战场面）───────────────────────────

    /** 第一阶段 0-200ms：朱砂冲击环扩散到半径 120dp。 */
    const val CLASH_RING_MS: Long = 200L
    const val CLASH_RING_RADIUS_DP: Float = 120f

    /** 第二阶段 200-600ms：两棋子互冲两次，每次冲撞火花 3-4 粒。 */
    const val CLASH_CHARGE_END_MS: Long = 600L
    const val SPARKS_MIN: Int = 3
    const val SPARKS_MAX: Int = 4

    // ─────────────────────────── 播放光轨（切片-07 底部控制栏）───────────────────────────

    /** 自动播放时 ▶ 周围旋转虚线光轨弧度：240°（不满圈）。 */
    const val PLAY_ORBIT_ARC_DEG: Float = 240f

    /** 光轨旋转一圈时长：6s。 */
    const val PLAY_ORBIT_PERIOD_MS: Long = 6000L

    /** 进度条当前回合朱砂圆点浮在线上方：2dp。 */
    const val PROGRESS_DOT_LIFT_DP: Float = 2f
}
