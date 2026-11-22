package io.legado.app.ui.book.read.ink

/**
 * 阅读页手势分区判定（契约第五节硬规则）。
 *
 * 分区比例：左 1/5 边缘 ｜ 中间 3/5 正文 ｜ 右 1/5 边缘；底部 60dp 归底栏，
 * 底栏判定优先于左右边缘（手指落在底栏高度内时一律归底栏，由 Compose 全吃）。
 *
 * 全部以 px 坐标计算，密度换算在调用处完成，判定函数本身保持纯函数可测。
 */
object ZoneClassifier {

    /**
     * @param x 触点 x（相对阅读壳，px）
     * @param y 触点 y（相对阅读壳，px）
     * @param width 壳宽 px
     * @param height 壳高 px
     * @param bottomBarPx 底栏高度 px
     */
    fun classify(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        bottomBarPx: Float,
    ): ReaderZone {
        if (width <= 0f || height <= 0f) return ReaderZone.Center
        if (y >= height - bottomBarPx) return ReaderZone.BottomBar

        val edgeWidth = width * InkTokens.EDGE_FRACTION
        return when {
            x < edgeWidth -> ReaderZone.LeftEdge
            x > width - edgeWidth -> ReaderZone.RightEdge
            else -> ReaderZone.Center
        }
    }

    /** 边缘区对应的翻页方向；中间区 / 底栏返回 null。 */
    fun edgeDirection(zone: ReaderZone): EdgeDirection? = when (zone) {
        ReaderZone.LeftEdge -> EdgeDirection.Prev
        ReaderZone.RightEdge -> EdgeDirection.Next
        ReaderZone.Center, ReaderZone.BottomBar -> null
    }
}
