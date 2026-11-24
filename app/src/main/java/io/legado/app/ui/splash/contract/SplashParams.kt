package io.legado.app.ui.splash.contract

import io.legado.app.ui.components.ComponentPalette

/**
 * S-01 启动动画 · 参数单源（平台无关层，纯数值，不引用任何 Android / Compose 类型，
 * 普通 JVM 单测可直接断言）。
 *
 * 〔锚点原文 · 逐字可回源〕
 *  - `docs/落文-规格切片/切片-01-启动动画.md:89`：「总时长：固定 2.8s，不做任何加载等待——
 *    2.8s 结束必须进书架，哪怕后台还在初始化」。
 *  - `docs/落文-规格切片/切片-01-启动动画.md:61`：标语「12sp，云灰色（#9B9687），不加粗
 *    不加斜」。
 *  - `docs/锚点/S-01-启动动画.md` 第二节参数表：总时长 2.8s 固定；远山 1200–1600ms；
 *    整体淡出 600ms；粒子预算墨 50 + 金 80（T3）；辉光半径 / 远山浓度 / 标语色值原文未给。
 *  - `docs/立项-冲突裁决.md` 第一节 A1：方向已拍板，数值未给——辉光半径、远山浓度、标语
 *    字号色值原文未给，不代填；实现时给默认取值 + 一行理由，进 P1 观感复核由用户目测微调。
 *
 * 〔为什么参数集中在一处〕
 * 金点尺寸 / 间距、三点错峰周期、整体时长、标题 / 标语字号、远山浓度、辉光半径只在本表
 * 定义；将来启动页渲染接线时只允许引用本表，不在画面里另写尺寸、字号、浓度、半径或色值，
 * 避免渲染层、既有 token 副本、单测三处口径漂移。
 *
 * 〔颜色口径〕
 * 全部颜色只引用既有单源 [ComponentPalette]：绢帛底 BG_PAGE、暖金 BRAND_GOLD、
 * 墨色 TEXT_PRIMARY、茶褐 TEXT_SECONDARY；本文件不写任何十六进制色值（锚点卡判据 4）。
 *
 * 〔本单边界〕
 * 本对象只管外观与时序参数；三点依次亮灭的透明度序列由 [goldDotAlpha] 纯函数收口；
 * Compose 动画接线、既有 `ui/welcome` 启动页与粒子编排不在本单改动范围。
 */
object SplashParams {

    // ── 金点序列（三点依次亮灭；规格切片未给三点序列，本单按默认编排收口） ──

    /** 金点数量：固定三点。 */
    const val DOT_COUNT: Int = 3

    /** 金点直径 dp：默认 6dp（规格未给三点尺寸；对齐既有启动页收束金点半径量级）。 */
    const val DOT_SIZE_DP: Int = 6

    /** 相邻金点间距 dp：默认 12dp（规格未给间距；三点水平排布的留白基准）。 */
    const val DOT_GAP_DP: Int = 12

    /**
     * 单点亮灭一圈周期 ms：复用全局微交互档位 [ComponentPalette.DURATION_CARD]（250ms）。
     * 一个点在自己的周期内从最亮线性衰减到 0，不另造新的时长档位（S-03：时长只取五档）。
     */
    val DOT_PERIOD_MS: Int = ComponentPalette.DURATION_CARD

    /**
     * 相邻两点错峰 step ms：第 i 个点在第 i · [DOT_STAGGER_MS] 毫秒起亮；
     * 复用全局微交互档位 [ComponentPalette.DURATION_MICRO]（120ms）。
     */
    val DOT_STAGGER_MS: Int = ComponentPalette.DURATION_MICRO

    /**
     * 三点序列一整圈的循环长度 ms = 单点周期 + 末点相对首点的错峰间隔。
     * 末点在 `2 · step` 起亮，恰好在本周期末尾衰减到 0，下一圈从首点重新起亮，天然闭环。
     */
    val SEQUENCE_LOOP_MS: Long =
        DOT_PERIOD_MS.toLong() + (DOT_COUNT - 1) * DOT_STAGGER_MS.toLong()

    // ── 整体编排时长 ──

    /** 整体动画总时长 ms：固定 2.8s，转发全局启动档 [ComponentPalette.DURATION_BOOT]（2800）。 */
    val TOTAL_MS: Int = ComponentPalette.DURATION_BOOT

    // ── 字号 ──

    /**
     * 标题字号 sp：默认 52sp。
     * 规格切片未给「落文」标题字号（切片-01:37 只写从墨迹渗出、0.4s 凝成浓墨）；
     * 此处对齐既有启动页画布字面 52 的口径取默认值，接线单接入时按观感复核微调。
     */
    const val TITLE_FONT_SIZE_SP: Float = 52f

    /** 标语字号 sp：12sp（切片-01:61「12sp，云灰色，不加粗不加斜」）。 */
    const val SLOGAN_FONT_SIZE_SP: Float = 12f

    // ── 浓度 / 辉光（A1：原文未给数值，默认取值 + 理由，待观感复核） ──

    /**
     * 远山浓度（山脊 / 雾带最高不透明度 0.0~1.0）。
     * // [待拍板] A1 裁决：远山 opacity 原文未给数值；本单按默认 0.35 实现，
     * 理由：远山「存在感很低、不抢字的注意力」，0.35 为低饱和淡墨档；P1 观感复核由用户目测微调。
     */
    const val HILL_MAX_ALPHA: Float = 0.35f

    /**
     * 扫光背后金色辉光半径 dp。
     * // [待拍板] A1 裁决：辉光半径原文未给数值；本单按默认 24dp 实现，
     * 理由：暖金辉光只托字面轮廓、不铺满屏幕，24dp 为局部光晕档；P1 观感复核由用户目测微调。
     */
    const val GLOW_RADIUS_DP: Int = 24

    // ── 颜色（只转发 ComponentPalette，不写 hex） ──

    /** 绢帛底，取 [ComponentPalette.BG_PAGE]。 */
    val bgPage: Long get() = ComponentPalette.BG_PAGE

    /** 暖金（金点 / 金粉 / 辉光），取 [ComponentPalette.BRAND_GOLD]。 */
    val brandGold: Long get() = ComponentPalette.BRAND_GOLD

    /** 墨色（标题字面），取 [ComponentPalette.TEXT_PRIMARY]。 */
    val textPrimary: Long get() = ComponentPalette.TEXT_PRIMARY

    /** 茶褐（标语 / 远山轮廓），取 [ComponentPalette.TEXT_SECONDARY]。 */
    val textSecondary: Long get() = ComponentPalette.TEXT_SECONDARY
}
