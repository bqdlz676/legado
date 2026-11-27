package io.legado.app.ui.book.read.ink

/**
 * 手势路由：Compose 遮罩层与 ViewBridge 降级通道共用同一套判定结果，
 * 保证两种派发模式下边缘 / 中间区行为一致（契约第五节）。
 *
 * 不变式：
 *  - 点中字永远优先于翻页：边缘区先 [ReaderShellHost.probeChar]，命中出气泡，
 *    点空白才翻页；任何分支不得跳过探测直接翻页；
 *  - 翻页动画进行中（[ReaderShellHost.isPageAnimating]）屏蔽一切点击 / 长按；
 *  - 已有文字选中时（[ReaderShellHost.isTextSelected]）长按不抢手势；
 *  - 底栏点击不探测、不路由，由 Compose 按钮自行消费；
 *  - 方向只取 [ZoneClassifier.edgeDirection] 的结论，路由内部不自判左右方向；
 *  - 本对象不持有 View / Binding / PopupWindow，不读书类型，不持有 Context。
 *
 * 滑动不经过本路由，必须回 View 的 fling（定界 I-09：触摸监听不消费事件）。
 */
internal object GestureRouter {

    /** 单击路由。坐标为壳坐标 px，宽高由调用侧按壳尺寸传入分类器。 */
    fun onTap(host: ReaderShellHost, zone: ReaderZone, x: Float, y: Float) {
        if (host.isPageAnimating()) return
        when (zone) {
            ReaderZone.BottomBar -> Unit // 底栏由 Compose 按钮自行消费，不进路由

            ReaderZone.LeftEdge, ReaderZone.RightEdge -> {
                val hit = host.probeChar(x, y)
                if (hit != null) {
                    // 点中字永远优先于翻页
                    host.requestBubble(hit)
                } else {
                    ZoneClassifier.edgeDirection(zone)?.let { host.turnPage(it) }
                }
            }

            ReaderZone.Center -> {
                val hit = host.probeChar(x, y)
                host.onCenterTap(x, y, hit)
            }
        }
    }

    /** 长按路由：进入文字选择；已有选区或翻页动画中不抢手势。 */
    fun onLongPress(host: ReaderShellHost, zone: ReaderZone, x: Float, y: Float) {
        if (host.isTextSelected() || host.isPageAnimating()) return
        val hit = host.probeChar(x, y)
        host.onCenterLongPress(x, y, hit)
    }

    /**
     * 返回手势出口：与硬件返回键 / Activity 分发器共用宿主 [ReaderShellHost.handleBack]
     * 同一裁决函数，手势层不另写层级判定（铁律63：单一决策源）。
     *
     * 当前系统手势导航返回经 OnBackPressedDispatcher 进入 Activity 回调；
     * 后续若加入屏内右滑返回手势，一律从本方法进同一出口，禁止在路由内自判气泡 / 朗读。
     */
    fun onBack(host: ReaderShellHost): InkBackResult = host.handleBack()
}
