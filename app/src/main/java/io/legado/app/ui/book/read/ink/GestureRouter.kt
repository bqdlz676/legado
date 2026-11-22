package io.legado.app.ui.book.read.ink

/**
 * 手势路由：Compose 遮罩层与 ViewBridge 降级通道共用同一套判定结果，
 * 保证两种派发模式下边缘 / 中间区行为一致（契约第五节）。
 *
 * 铁律在此落实：边缘区先 [ReaderShellHost.probeChar]，点中字 → 出气泡；
 * 点空白 → 才翻页。滑动不经过本路由，必须回 View 的 fling。
 */
internal object GestureRouter {

    /** 单击路由。 */
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
}
