package io.legado.app.ui.components

import kotlin.math.roundToInt

/**
 * S-11 半屏 Sheet · 容器参数单源（平台无关层，纯数值）。
 *
 * 〔为什么参数集中在一处〕
 * 拖拽横杠高、阅读页下沉量、遮罩暗化比、Sheet 背景、分隔线宽与色、横杠色，全部逐字
 * 来自 `docs/落文-规格切片/切片-11-半屏Sheet.md` 与 `docs/锚点/S-11-半屏Sheet.md`
 * 参数表。集中单点定义是为了：
 *  1. 将来接入 Sheet 渲染时，Composable / 绘制代码只允许引用本表，不得在页面里另写
 *     尺寸、暗化比或色值，避免渲染层、既有 token 副本、单测三处口径漂移；
 *  2. 单测可对常数直接断言（横杠 4dp、下沉 8dp、暗化 0.4 三项可 grep 回证）；
 *  3. 后续调参只改这一处，改动可被 grep 回证与单测同时兜住。
 *
 * 〔为什么用纯数值〕
 * 本对象只持有 Int / Float / Long 常数与 getter，不调用任何 Android API（dp/sp 换算、
 * Paint、Canvas、Resources），也不依赖 Compose 运行时或资源加载，普通 JVM 单测可直接
 * 运行断言；dp ↔ px 的单位换算留给将来渲染层接入时自行完成。
 *
 * 〔颜色口径〕
 * 全部颜色只引用既有单源 [ComponentPalette]：Sheet 背景取 BG_PAGE、横杠色取
 * BRAND_GOLD、分隔线色由 TEXT_TERTIARY 派生（RGB 位取自云灰 token，高位 alpha 按
 * [DIVIDER_ALPHA] 重算）。本文件不写任何十六进制色值（锚点卡判据 3）。
 *
 * 〔与状态快照的分工〕
 * 本对象只管 Sheet 容器外观参数；开合前后阅读页是否「一字不差」，由
 * [ReaderSnapshot] 与顶层纯函数 [isRestoredExactly] 收口，不在本对象内读写宿主状态。
 */
object HalfSheetParams {

    // ── 切片-11 原文参数（锚点卡参数表；单位不许自创） ──

    /** 拖拽指示器横杠高度 dp：4dp 金色横杠，不是圆角条（切片-11 横杠条 / 锚点卡:17） */
    const val GRABBER_BAR_HEIGHT_DP: Int = 4

    /** 弹起时阅读页整体下沉量 dp：8dp（切片-11「下沉 8dp」/ 锚点卡:18） */
    const val HOST_SINK_DP: Int = 8

    /** 遮罩暗化比：40%，即 0.4 茶色玻璃罩（切片-11「暗化 40%」/ 锚点卡:19） */
    const val DIM_ALPHA: Float = 0.4f

    /** 分隔线宽 dp：1dp 云灰线（切片-11 分区线 / 锚点卡:21） */
    const val DIVIDER_WIDTH_DP: Int = 1

    /**
     * 分隔线派生透明度：云灰 token 压到浅淡，避免分区线抢正文；
     * 派生只改高位 alpha，RGB 位仍取自 TEXT_TERTIARY。
     */
    const val DIVIDER_ALPHA: Float = 0.25f

    /** Sheet 背景：纸白，取 [ComponentPalette.BG_PAGE]，不写 hex */
    val sheetBackground: Long get() = ComponentPalette.BG_PAGE

    /** 横杠色：暖金，取 [ComponentPalette.BRAND_GOLD]，不写 hex */
    val grabberColor: Long get() = ComponentPalette.BRAND_GOLD

    /**
     * 分隔线色：RGB 位取自 [ComponentPalette.TEXT_TERTIARY]，高位 alpha 按
     * [DIVIDER_ALPHA] 重算；本 getter 不内联任何 hex 色值。
     */
    val dividerColor: Long
        get() {
            val rgb = ComponentPalette.TEXT_TERTIARY and 0xFFFFFFL
            val alpha = (DIVIDER_ALPHA * 255).roundToInt().toLong()
            return (alpha shl 24) or rgb
        }
}
