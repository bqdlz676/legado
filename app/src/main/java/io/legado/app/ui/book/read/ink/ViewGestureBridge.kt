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
 * 本类只持有通用 [View] 引用，不 import 任何排版引擎类；命中检测仍走
 * [ReaderShellHost.probeChar]，由宿主接既有引擎能力。
 */
class ViewGestureBridge(
    private val host: ReaderShellHost,
    /** 底栏高度 px（底栏归 Compose，不进 View 点击路由）。 */
    private val bottomBarPx: Float,
) {
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    private val touchListener = View.OnTouchListener { _, event ->
        lastX = event.x
        lastY = event.y
        false // 不消费：引擎 onTouchEvent / fling 照常收到
    }

    private val clickListener = View.OnClickListener { view ->
        routeTap(view)
    }

    private val longClickListener = View.OnLongClickListener { view ->
        routeLongPress(view)
        true
    }

    /** 挂到引擎 View 上。宿主在 fallback 模式启用时调用。 */
    fun attach(view: View) {
        view.setOnTouchListener(touchListener)
        view.setOnClickListener(clickListener)
        view.setOnLongClickListener(longClickListener)
    }

    /** 摘除监听，恢复 ComposeOverlay 模式或释放时调用。 */
    fun detach(view: View) {
        view.setOnTouchListener(null)
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
    }

    private fun routeTap(view: View) {
        val zone = zoneOf(view)
        GestureRouter.onTap(host, zone, lastX, lastY)
    }

    private fun routeLongPress(view: View) {
        val zone = zoneOf(view)
        GestureRouter.onLongPress(host, zone, lastX, lastY)
    }

    private fun zoneOf(view: View): ReaderZone = ZoneClassifier.classify(
        x = lastX,
        y = lastY,
        width = view.width.toFloat().coerceAtLeast(1f),
        height = view.height.toFloat().coerceAtLeast(1f),
        bottomBarPx = bottomBarPx,
    )
}
