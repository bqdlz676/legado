package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.page.entities.TextPage

/**
 * 阅读壳 ↔ 宿主（ReadBookActivity / ViewModel / core 引擎）桥接协议。
 *
 * 壳只通过本接口触碰排版引擎与业务解析，不直接引用 ContentTextView / ReadView 内部；
 * 引擎 View 一行不动，命中检测、翻页、选词、气泡解析全部由宿主在实现侧接既有能力。
 *
 * 不变式（归位口径，B4-05）：
 *  - 本契约不携带 Context / Activity / View 引用；Context 注入只发生在实现侧构造槽位，
 *    接口方法签名不暴露 Android 框架类型，壳层无法经契约持有宿主 Activity；
 *  - 未 attach / 引擎未排版态下各方法给安全默认：[probeChar] 返回 null、[turnPage] /
 *    [onCenterTap] / [onCenterLongPress] 为空操作或关气泡、[handleBack] 逐层消化后落到退出分支；
 *  - 返回裁决只有 [handleBack] 一个出口，手势层 / 硬件键 / Activity 不许另写一套层级判定。
 *
 * 状态源（R5，B4-06）：章 / 页 / 进度 / 样式真值唯一在 `model/ReadBook`；本协议不持有第二份
 * 章文本 / 标题 / 进度镜像。进章 `openChapter`、翻页 `moveToNextPage/moveToPrevPage`、
 * 出页 `TextPage` 交 `setContent` 由控制器实现侧接 ReadBook；当前 TextPage 出页缝在控制器
 * （`InkReadController.currentTextPage`），供 B4-07 文字面 AndroidView 上屏取页。
 */
interface ReaderShellHost {

    /**
     * 在 (x, y)（壳坐标 px）做字符命中检测。
     * @return 命中字符信息；点在空白处 / 图片外 / 行距留白返回 null。
     * 铁律：点中字永远优先于翻页——边缘区只有在本方法返回 null 时才允许翻页。
     */
    fun probeChar(x: Float, y: Float): CharHit?

    /** 按方向翻一页（宿主走既有 PageDelegate 动画 / fling 通道）。 */
    fun turnPage(direction: EdgeDirection)

    /** 中间区单击：查字 / 出气泡。hit 为 null 时按空白单击处理（关闭气泡等）。 */
    fun onCenterTap(x: Float, y: Float, hit: CharHit?)

    /** 中间区长按：进入文字选择。hit 可能为 null（长按在留白处）。 */
    fun onCenterLongPress(x: Float, y: Float, hit: CharHit?)

    /**
     * 请求解析并展示气泡。宿主按 core 契约 resolve(paragraph, pos, anchor) 取
     * `io.legado.app.help.bubble.BubbleContent` 后回传给壳；低置信度 / 未收录走
     * Missing 兜底，不允许空气泡。
     */
    fun requestBubble(hit: CharHit)

    /** 立即关闭气泡（滚动 / 翻页 / 点空白触发，0ms 消失）。 */
    fun dismissBubble()

    /** 当前是否已有文字选中（选中态下壳不抢长按 / 点击）。 */
    fun isTextSelected(): Boolean

    /** 翻页动画是否进行中（进行中屏蔽新的气泡与边缘翻页，避免动画打架）。 */
    fun isPageAnimating(): Boolean

    /**
     * 返回统一出口：硬件返回键、系统手势返回、手势路由 back 全部只走本方法，
     * Activity / Compose 壳 / 手势层禁止另写一套层级判定（铁律63：单一决策源，防两套状态漂移）。
     *
     * 层级对齐旧壳 ReadBookActivity.onBackPressedDispatcher.addCallback：
     * 先消化页内覆盖层（气泡 / 朗读），无可消化时返回 [InkBackResult.ExitToShelf]
     * 由调用方走退出链路。
     */
    fun handleBack(): InkBackResult

    /**
     * 出页缝（B4-07 文字面上屏）：取 ReadBook 当前页 [TextPage] 交文字面
     * `ContentTextView.setContent` 上屏；章未就绪 / 页序号越界返回 null，壳侧走空态兜底，
     * 不出空白态。本方法只做回源读取，实现侧不另存第二份页文本（R5）。
     */
    fun currentTextPage(): TextPage?

    /**
     * 文字面就绪 / 空态归类（B4-07）：由 ReadBook 唯一状态源派生页序 / 页数口径，
     * 供 Compose 文字面判定就绪态或展示明确空态文案；不持有第二份章 / 页文本。
     */
    fun textPageState(): InkTextPageState
}

/**
 * 返回统一出口的裁决结果。
 *
 * 层级顺序（与旧壳 611–635 分支一一对应；旧壳有对应物的才移植）：
 *  1. [BubbleDismissed]  气泡开着 → 关气泡，页内消化；
 *  2. [ReadAloudPaused]  朗读播放中 → 暂停朗读，页内消化；
 *  3. [ReturnKeyLocked]  「禁用返回键」偏好开启 → 消费返回、不退出；
 *  4. [ExitToShelf]      无覆盖层 → 调用方 finish() 回书架。
 *
 * 旧壳的「全文搜索结果退出 / 搜索前进度恢复 / 自动翻页停止」在新壳无对应状态，不移植。
 */
enum class InkBackResult {
    /** 气泡已关闭；返回在阅读页内消化，不退出。 */
    BubbleDismissed,

    /** 朗读已暂停；返回在阅读页内消化，不退出（对齐旧壳 isPlay() → pause 分支）。 */
    ReadAloudPaused,

    /** 「禁用返回键」偏好生效；返回被消费、不退出（对齐旧壳 disableReturnKey 分支）。 */
    ReturnKeyLocked,

    /** 无任何页内覆盖层；调用方应执行 finish() 退出阅读回到书架。 */
    ExitToShelf,
}
