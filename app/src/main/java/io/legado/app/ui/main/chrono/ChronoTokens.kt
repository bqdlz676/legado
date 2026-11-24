package io.legado.app.ui.main.chrono

import androidx.compose.ui.graphics.Color

/**
 * 落文 · 纪年页视觉 token（ui/main/chrono 独占）。
 *
 * 颜色取 12 色语义契约在暗底下的派生值；纪年页材质为「gradient mesh 暗底暖光渐变」，
 * 不用绢帛纸白铺满。底栏几何 / 模糊参数取微交互契约「纪年页毛玻璃底栏」原文数值。
 *
 * TODO(D23 收口)：12 色基色真值源为 io.legado.app.ui.components.ComponentPalette；本文件暗底派生值
 * 后续改为「单源基色 + 暗底派生系数」表达，本轮只留值不改引用。
 */
object ChronoTokens {

    // —— 暗底暖光配色（语义对齐 12 色契约，暗底派生）——
    /** 暗底：深墨暖褐 */
    val bgDeep: Color = Color(0xFF12100D)
    /** 暖光 mesh：左上烛光暖褐 */
    val bgWarm: Color = Color(0xFF2A2118)
    /** 卡片 / 行底：暖绢 8% 透明 */
    val bgSurface: Color = Color(0x14F2EADB)
    /** 茶玻遮罩 */
    val bgOverlay: Color = Color(0x731A1410)
    val textPrimary: Color = Color(0xFFF2EADB)
    val textSecondary: Color = Color(0xFFB9AD94)
    val textTertiary: Color = Color(0xFF9B9687)
    val textAccent: Color = Color(0xFFC23B2E)
    val brandGold: Color = Color(0xFFD4A54A)
    val brandCopper: Color = Color(0xFF8B6914)
    val colorLink: Color = Color(0xFFC23B2E)
    val colorDivider: Color = Color(0x14F2EADB)
    val colorGlow: Color = Color(0x4DD4A54A)

    /** 朱砂按压泛色（InkButton 基类：0 → 8%，120ms 回落） */
    const val PRESS_ALPHA: Float = 0.08f
    const val MICRO_MS: Long = 120L

    // —— 毛玻璃底栏几何（微交互契约第 3 项，值不可变）——
    /** 底栏宽 338dp */
    const val BAR_WIDTH_DP: Float = 338f
    /** 底栏高 64dp */
    const val BAR_HEIGHT_DP: Float = 64f
    /** 胶囊圆角 32dp */
    const val BAR_RADIUS_DP: Float = 32f
    /** 背面地图实时模糊半径 18px */
    const val BAR_BLUR_PX: Float = 18f
    /** 背面亮度压到 0.62 */
    const val BAR_BRIGHTNESS: Float = 0.62f
    /** 升起面板高度 400dp */
    const val PANEL_HEIGHT_DP: Float = 400f
    /** 收起落回时的过冲回弹 10px */
    const val PANEL_OVERSHOOT_PX: Float = 10f
    /** 面板拖拽超过 1/3 高度或快速下甩即关闭 */
    const val CLOSE_DRAG_FRACTION: Float = 1f / 3f

    // —— 曲线 / 时长 ——
    /**
     * 面板升降弹簧：契约气泡 spring cubic-bezier(.34,1.56,.64,1)（overshoot 回弹）。
     * Compose Spring 口径按回弹感映射：低阻尼出 overshoot，质量取 0.64；刚度取常用中值，
     * 真机手感待荣耀 WIN 验证后微调。
     */
    const val SPRING_DAMPING: Float = 0.34f
    const val SPRING_STIFFNESS: Float = 380f
    const val SPRING_MASS: Float = 0.64f
    /** 微交互 120ms */
    const val BAR_PRESS_MS: Long = 120L

    // —— 粒子 / 帧率预算（纪年金环：环形粒子 + 流动数字粒子，上限 80，55fps）——
    const val RING_COUNT: Int = 48
    const val FLOW_COUNT: Int = 32
    const val PARTICLE_CAP: Int = 80
    const val FPS_FLOOR: Int = 55
    const val FRAME_BUDGET_MS: Float = 16f
}
