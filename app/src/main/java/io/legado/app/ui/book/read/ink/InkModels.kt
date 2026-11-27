package io.legado.app.ui.book.read.ink

import androidx.compose.ui.geometry.Rect
import io.legado.app.ui.book.read.vertical.CharHit as VerticalCharHit

/**
 * 阅读页 Compose 壳与宿主之间传递的 UI 模型。
 *
 * 气泡内容 / 动作 / 候选统一复用模块⑥ `io.legado.app.help.bubble` 的
 * [io.legado.app.help.bubble.BubbleContent]，本目录不另建并行数据模型。
 * 逐字命中信息复用分页引擎 [VerticalCharHit]，壳侧只补气泡定位用的外接矩形。
 *
 * 不变式（归位口径，B4-05）：
 *  - 全部为不可变纯数据：无 Compose 可观察态（`MutableState` / `remember`）、无
 *    `@Composable` 回调、无 Activity / Context / View 持有，解析与编排逻辑不进模型层；
 *  - [Rect] 仅取 `androidx.compose.ui.geometry` 的几何值类型（left/top/right/bottom 四浮点），
 *    不依赖 Composition 运行时，普通 JVM 单测可构造；若后续要彻底去 Compose 几何类型，
 *    需连同 [CharHit.anchor] 的消费方（气泡定位 / 手势桥）一并搬迁，登记 D23 后续批次；
 *  - 模型字段只做透传与只读派生，不改写引擎命中结果。
 */

/** 命中字符的屏幕信息（相对阅读壳坐标，px）。宿主通过桥接接口从分页引擎取到后回填。 */
data class CharHit(
    /** 原字外接矩形（px），气泡箭头对准其水平中心 */
    val anchor: Rect,
    /** 分页引擎逐字命中结果（字符 / 流内偏移 / 段落原文 / 段内下标）。 */
    val engineHit: VerticalCharHit,
) {
    /** 命中的单字原文。 */
    val text: String get() = engineHit.char.toString()

    /** 命中字所在段落原文，喂给 BubbleResolver.resolve。 */
    val paragraphText: String get() = engineHit.paragraphText

    /** 命中字在段落内的下标，喂给 BubbleResolver.resolve。 */
    val pos: Int get() = engineHit.pos

    /** 章节字符流内偏移。 */
    val offset: Int get() = engineHit.offset
}

/** 气泡锚点：原字位置 + 所在句行边界，用于「不遮当前句」与上下翻转判定。 */
data class BubbleAnchor(
    val centerX: Float,
    val topY: Float,
    val bottomY: Float,
    /** 当前句行顶部 y（px），气泡翻到下方时不得越过此线 */
    val lineTopY: Float,
    /** 当前句行底部 y（px） */
    val lineBottomY: Float,
) {
    constructor(hit: CharHit) : this(
        centerX = (hit.anchor.left + hit.anchor.right) / 2f,
        topY = hit.anchor.top,
        bottomY = hit.anchor.bottom,
        lineTopY = hit.anchor.top,
        lineBottomY = hit.anchor.bottom,
    )
}

/** 手势分区（契约第五节，值不可变） */
enum class ReaderZone {
    /** 左 1/5 边缘 */
    LeftEdge,

    /** 中间 3/5 正文区 */
    Center,

    /** 右 1/5 边缘 */
    RightEdge,

    /** 底栏 60dp */
    BottomBar,
}

/** 翻页方向：左边缘 → 上一页，右边缘 → 下一页 */
enum class EdgeDirection { Prev, Next }

/**
 * 触摸事件派发模式。
 *
 * - [ComposeOverlay]：Compose 层 detectTapGestures 只吃 tap / longPress，
 *   滑动不消费，回传给底层 View 的 fling / 翻页动画。
 * - [ViewBridge]：降级 fallback。若真机上 View 收不到滑动（Compose 遮断），
 *   把中间区点击 / 长按检测移到引擎 View 的 OnClickListener / OnLongClickListener。
 */
enum class DispatchMode { ComposeOverlay, ViewBridge }

/** 气泡显示状态 */
sealed interface BubbleState {
    data object Hidden : BubbleState
    data class Visible(val anchor: BubbleAnchor, val hit: CharHit) : BubbleState
}
