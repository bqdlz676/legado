package io.legado.app.ui.components.contract

import io.legado.app.ui.components.ComponentPalette

/**
 * S-04 组件系统 · 可数值化参数单源（平台无关层，纯数值，不引用任何 Android / Compose 类型，
 * 普通 JVM 单测可直接断言）。
 *
 * 〔锚点原文〕
 *  - `docs/落文-规格切片/切片-04-组件系统.md:3`：Bubble「撕边纸片 + 金边 + 注入动画（spring）+ 义项切换点」
 *  - `docs/落文-规格切片/切片-04-组件系统.md:4`：BottomSheet「毛玻璃底 + 金杠拖拽指示器 + 三段高度（30%/60%/90%）」
 *  - `docs/落文-规格切片/切片-04-组件系统.md:6`：InkButton「touchstart → 元素短暂泛朱砂色…120ms 后回落…组件系统的基类」
 *  - `docs/落文-规格切片/切片-04-组件系统.md:9-10`：组件状态覆盖（默认/悬浮/点击/禁用/加载/空状态/离线）
 *  - `docs/落文-规格切片/切片-14-字体系统.md:7`：字体三档——标题思源宋体 SemiBold / 正文 Noto Serif SC / 注音 Noto Sans SC Light 10sp
 *  - `docs/落文-规格切片/切片-15-全局动效铁律.md:7`：全局动效时长表（120ms 点触 / 250ms 气泡 / 350ms stagger）
 *
 * 〔为什么集中一处〕
 * 圆角 / 描边 / 内边距 / 触控下限 / 字重 / 时长三档只在本对象登记；渲染层接线时只允许引用
 * 本表，不在画面里另写数值，避免渲染层、既有 token 副本、单测三处口径漂移。
 *
 * 〔颜色口径〕
 * 本对象不持有任何色值；需要颜色时由状态契约层回 [ComponentPalette] 取 12 色 token
 * （`docs/落文-模块契约索引.md:59` §1），派生色按 §1.3 集中声明，本文件不写裸 hex。
 *
 * 〔本单边界〕
 *  - 12 色 token / 缓动曲线 / spring 刚度阻尼 / Sheet 三段比例的真值源仍是 [ComponentPalette]
 *    （S-03 单源）；本对象只做组件档位的语义归并，规格缺档才新增并标注「规格新给」。
 *  - Sheet 金杠 / 遮罩暗化 / 三段回弹归 S-11 半屏 Sheet；书架下拉浮层尺寸归 S-13 搜索浮层；
 *    本对象不重复登记这两处参数。
 */
object ComponentSystemParams {

    // ─────────────────────────── 圆角三档（dp，按钮 > 卡片 > 浮层/气泡，单调递减） ───────────────────────────

    /** 按钮圆角 20dp：复用 [ComponentPalette.BUTTON_RADIUS_DP]；切片-25:51 空态暖金按钮「圆角 20」。 */
    const val RADIUS_BUTTON_DP: Int = ComponentPalette.BUTTON_RADIUS_DP

    /** 卡片圆角 16dp：复用 [ComponentPalette.CARD_RADIUS_DP]；切片-25:13 通用卡片 Token「圆角 16dp」。 */
    const val RADIUS_CARD_DP: Int = ComponentPalette.CARD_RADIUS_DP

    /**
     * 浮层（气泡）圆角 12dp：复用 [ComponentPalette.BUBBLE_RADIUS_DP]；切片-04:3 气泡容器。
     * 半屏 Sheet 顶部圆角归 S-11 口径（[ComponentPalette.SHEET_RADIUS_DP]），不在本档重复登记。
     */
    const val RADIUS_OVERLAY_DP: Int = ComponentPalette.BUBBLE_RADIUS_DP

    // ─────────────────────────── 描边宽度 ───────────────────────────

    /**
     * 描边宽度 1dp：规格原文写「1px」——锚点 S-04:17 气泡「金边（1px 金）」、切片-11:18
     * Sheet「金色边框 1px」、切片-03:129 输入框「云灰 1px 底线」。
     * 【规格新给】px→dp 换算口径：契约层统一按 1dp 登记，密度换算留渲染层；卡片基座默认无边框
     * （切片-25:14「边框 无。卡片靠阴影拉开层次，不靠边线」），仅原文指定金边 / 朱砂边的变体追加此宽度。
     */
    const val STROKE_WIDTH_DP: Int = 1

    // ─────────────────────────── 卡片内边距 ───────────────────────────

    /**
     * 卡片内容内边距 12dp。【规格新给】切片-25 未给卡片内边距数值，只给卡片间距 12dp
     * （切片-25:16）与满宽卡片左右各 12dp 边距（切片-25:30）；内边距对齐同一栅格档 12dp 兜底，
     * 避免卡内文字贴边。
     */
    const val CARD_PADDING_DP: Int = 12

    // ─────────────────────────── 最小触控点击区 ───────────────────────────

    /**
     * 按钮 / 可点行最小点击区 48dp：无障碍触控下限（Android 可点目标建议 ≥48dp）；
     * 切片-25:56 设置行「每行 48dp」同口径。视觉尺寸不足 48dp 的图标 / 文字按钮，
     * 命中区仍须外扩到本下限，不许按可视大小收窄。
     */
    const val MIN_TOUCH_TARGET_DP: Int = 48

    // ─────────────────────────── 字体字重三档（Int 字重数值，渲染层转 FontWeight） ───────────────────────────

    /**
     * 标题字重 600（SemiBold）：切片-14:7「标题 思源宋体 SemiBold」。
     * 【规格新给】规格只给字重名 SemiBold，600 为常规 SemiBold 数值映射；渲染层转 FontWeight.SemiBold。
     */
    const val WEIGHT_TITLE: Int = 600

    /**
     * 正文字重 400（Regular）：切片-14:7「正文（宋体）Noto Serif SC」，常规字重。
     * 【规格新给】规格未标字重名，按宋体正文常规 400 兜底。
     */
    const val WEIGHT_BODY: Int = 400

    /**
     * 注音字重 300（Light）：切片-14:7「注音小字 Noto Sans SC Light 10sp」。
     * 【规格新给】规格给字重名 Light，300 为 Light 数值映射。
     */
    const val WEIGHT_ANNOTATION: Int = 300

    // ─────────────────────────── 动效时长三档（ms，复用 ComponentPalette 既有档位，不新造） ───────────────────────────

    /** 快档 120ms：点触反馈 / 墨触回落；复用 [ComponentPalette.DURATION_MICRO]；切片-04:6「120ms 后回落」、切片-15:7「点触反馈 120ms」。 */
    const val DURATION_FAST_MS: Int = ComponentPalette.DURATION_MICRO

    /** 中档 250ms：卡片 / 气泡浮入；复用 [ComponentPalette.DURATION_CARD]；切片-15:7「气泡弹入 250ms」。 */
    const val DURATION_MID_MS: Int = ComponentPalette.DURATION_CARD

    /** 慢档 350ms：页面转场 / 卡片错峰浮入；复用 [ComponentPalette.DURATION_PAGE]；切片-15:7「卡片浮入 stagger 350ms each」。 */
    const val DURATION_SLOW_MS: Int = ComponentPalette.DURATION_PAGE
}
