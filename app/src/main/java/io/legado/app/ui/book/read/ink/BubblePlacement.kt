package io.legado.app.ui.book.read.ink

import kotlin.math.max
import kotlin.math.min

/**
 * 气泡定位结果（壳坐标 px）。
 * @param above true=气泡在原字上方（尖角朝下）；false=翻到原字下方（尖角朝上）
 * @param x 气泡左上 x
 * @param y 气泡左上 y
 * @param arrowX 尖角水平中心 x（对准原字中心，越界时夹在屏内）
 */
data class BubblePlacement(
    val above: Boolean,
    val x: Float,
    val y: Float,
    val arrowX: Float,
) {
    var bubbleW: Float = 0f
    var bubbleH: Float = 0f
}

/**
 * 气泡定位纯函数（契约第四节 Bubble：原文上方 8dp、箭头对字心、上方越界翻下方、
 * 不遮当前句）。测量出气泡宽高后调用；所有边界兜底夹取，不允许越界崩溃。
 */
object BubblePlacer {

    /**
     * @param anchor 原字锚点
     * @param bubbleW 气泡宽 px
     * @param bubbleH 气泡高 px
     * @param viewW 壳宽 px
     * @param viewH 壳高 px
     * @param gapPx 气泡与原字间距 px（契约 8dp）
     * @param topSafePx 顶部安全区 px（状态栏）
     * @param bottomReservedPx 底部保留 px（底栏高度）
     * @param marginPx 气泡左右离屏边最小间距 px
     */
    fun place(
        anchor: BubbleAnchor,
        bubbleW: Float,
        bubbleH: Float,
        viewW: Float,
        viewH: Float,
        gapPx: Float,
        topSafePx: Float,
        bottomReservedPx: Float,
        marginPx: Float,
    ): BubblePlacement {
        val arrowHalf = bubbleW / 2f
        val arrowX = clamp(anchor.centerX, marginPx + arrowHalf, viewW - marginPx - arrowHalf)

        val belowMaxY = viewH - bottomReservedPx - bubbleH
        // 以当前句行边界为基准：气泡贴行外 8dp，不遮当前句
        val aboveY = anchor.lineTopY - gapPx - bubbleH
        val belowY = anchor.lineBottomY + gapPx

        val aboveFits = aboveY >= topSafePx
        val belowFits = belowY <= belowMaxY

        val above = when {
            aboveFits -> true
            belowFits -> false
            // 上下都放不下（气泡过高 / 行距过窄）：优先下方，再把整体夹回安全区，
            // 宁可轻微压行也不允许气泡飞出屏幕或消失。
            else -> false
        }

        val y = when {
            above -> max(topSafePx, aboveY)
            else -> min(max(belowY, topSafePx), belowMaxY)
        }

        val x = clamp(
            arrowX - bubbleW / 2f,
            marginPx,
            max(marginPx, viewW - marginPx - bubbleW),
        )

        return BubblePlacement(above = above, x = x, y = y, arrowX = arrowX).apply {
            this.bubbleW = bubbleW
            this.bubbleH = bubbleH
        }
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = min(max(v, lo), hi)
}
