package io.legado.app.ui.book.read.ink

import android.view.View

/**
 * 降级 fallback 通道（契约手势铁律第 4 条）：
 *
 * 当真机上 Compose 遮罩层把滑动事件拦断、底层 View 收不到 fling 时，
 * 把中间区 / 边缘区的点击、长按检测从 Compose [detectTapGestures] 移到
 * 引擎 View 的 OnClickListener / OnLongClickListener；OnTouchListener 只记录
 * 最后触点坐标且**不消费**事件，滑动依旧回 View 的翻页动画。
 *
 * 分层不变式（定界 I-09：保留·壳纯逻辑 + 接线重写）：
 *  - 判定核心 [touchMove] / [tap] / [longPress] / [zoneOf] 全部吃标量参数，
 *    纯 JVM 可离线单测，不触 Android framework 类；
 *  - [attach] / [detach] 是唯一接线侧（internal），只把引擎 View 的监听接到判定核心；
 *    本类不持有 View 字段、不持有 Activity / Context / Binding / PopupWindow；
 *  - 命中检测仍走 [ReaderShellHost.probeChar]，由宿主接既有引擎能力；
 *  - 触摸监听返回 false 不消费事件，滑动回 View fling，本通道不抢翻页手势。
 *
 * 交接注记：旧 `ReadBookActivity.onTouch:1429` 旧壳触摸判定与 View 编排耦合在一起
 * （旧实现留待 B4-31 下线）；本件只保留降级通道的坐标快照与路由收口，View 挂摘
 * 在 B4-21 壳装配期按新宿主重接。
 */
class ViewGestureBridge(
    private val host: ReaderShellHost,
    /** 底栏高度 px（底栏归 Compose，不进 View 点击路由）。 */
    private val bottomBarPx: Float,
) {
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var viewWidth: Float = 1f
    private var viewHeight: Float = 1f

    /**
     * 触摸移动快照（纯逻辑入口）。
     *
     * 边界兜底：坐标 NaN 不覆盖上一次有效坐标；宽高退化（≤0）夹紧到 1，
     * 分区器再按安全尺寸回退 Center。返回值恒为 false：不消费事件。
     *
     * @return false = 事件不消费，引擎 onTouchEvent / fling 照常收到。
     */
    fun touchMove(x: Float, y: Float, width: Float, height: Float): Boolean {
        if (!x.isNaN() && !y.isNaN()) {
            lastX = x
            lastY = y
        }
        viewWidth = width.coerceAtLeast(1f)
        viewHeight = height.coerceAtLeast(1f)
        return false
    }

    /** 单击路由入口（纯逻辑）：按给定坐标分区后交 [GestureRouter.onTap]。 */
    fun tap(x: Float = lastX, y: Float = lastY, width: Float = viewWidth, height: Float = viewHeight) {
        GestureRouter.onTap(host, zoneOf(x, y, width, height), x, y)
    }

    /** 长按路由入口（纯逻辑）：交 [GestureRouter.onLongPress]；返回 true 表示长按已消费。 */
    fun longPress(x: Float = lastX, y: Float = lastY, width: Float = viewWidth, height: Float = viewHeight): Boolean {
        GestureRouter.onLongPress(host, zoneOf(x, y, width, height), x, y)
        return true
    }

    /** 分区纯函数入口；退化尺寸回退 [ReaderZone.Center]。 */
    fun zoneOf(x: Float, y: Float, width: Float, height: Float): ReaderZone =
        ZoneClassifier.classify(
            x = x,
            y = y,
            width = width,
            height = height,
            bottomBarPx = bottomBarPx,
        )

    // ── 接线侧（internal）：View 监听挂摘；判定逻辑全部委托上面的纯逻辑入口 ──

    /** 挂到引擎 View 上。宿主在 fallback 模式启用时调用；接线细节 B4-21 重接。 */
    internal fun attach(view: View) {
        view.setOnTouchListener { _, event ->
            touchMove(event.x, event.y, view.width.toFloat(), view.height.toFloat())
        }
        view.setOnClickListener { tap() }
        view.setOnLongClickListener { longPress() }
    }

    /** 摘除监听，恢复 ComposeOverlay 模式或释放时调用。 */
    internal fun detach(view: View) {
        view.setOnTouchListener(null)
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
    }
}
