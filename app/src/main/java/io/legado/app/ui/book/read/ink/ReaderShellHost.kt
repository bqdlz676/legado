package io.legado.app.ui.book.read.ink

/**
 * 阅读壳 ↔ 宿主（ReadBookActivity / ViewModel / core 引擎）桥接协议。
 *
 * 壳只通过本接口触碰排版引擎与业务解析，不直接引用 ContentTextView / ReadView 内部；
 * 引擎 View 一行不动，命中检测、翻页、选词、气泡解析全部由宿主在实现侧接既有能力。
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
}
