package io.legado.app.ui.book.read.ink

/**
 * 阅读页手势分区判定（契约第五节硬规则）。
 *
 * 分区比例：左 1/5 边缘 ｜ 中间 3/5 正文 ｜ 右 1/5 边缘；底部 60dp 归底栏，
 * 底栏判定优先于左右边缘（手指落在底栏高度内时一律归底栏，由 Compose 全吃）。
 *
 * 不变式：
 *  - 全部以 px 坐标计算，密度换算在调用处完成，判定函数本身保持纯函数可测；
 *  - 同一组 (x, y, width, height, bottomBarPx) 反复调用返回同一分区，无内部状态；
 *  - 非法 / 退化输入（宽高 ≤0、坐标 NaN、底栏高度为负）回退安全分区 [ReaderZone.Center]，
 *    不抛异常、不返回 null。
 *
 * 方向口径：[edgeDirection] 给中性映射（左缘 Prev、右缘 Next）；R17 / P-14 要求
 * 手势随排版方向、与书类型无关，竖排 / 横排是否对调左右边缘由壳装配期按排版方向
 * 决定，本件不读取书类型、不绑定具体排版模式。
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
        if (x.isNaN() || y.isNaN()) return ReaderZone.Center
        val bar = bottomBarPx.coerceAtLeast(0f)
        if (y >= height - bar) return ReaderZone.BottomBar

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
