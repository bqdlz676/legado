package io.legado.app.ui.components.inkslate.contract

import kotlin.math.roundToInt

/** 宽高整数对（纯数值，平台无关；位图尺寸兜底用，不持 Android Bitmap）。 */
data class IntPair(val width: Int, val height: Int)

/**
 * S-12 导出结构（切片-12 §8.4）：Annotation 模式传给阅读层 / 分享链路的不只是位图截图，
 * 还必须带上章节名 + 正文纯文本；导出图片底部自动附注「《左传·隐公元年》第 3 页批注」。
 *
 * 本结构是纯 JVM 契约：位图只持占位引用，不持 Android Bitmap，便于后续 Y-03 云同步
 * 按全量 JSON 序列化；三件缺一即视为导出不完整（[hasAllThreeParts]）。
 *
 * @param bitmapRef 位图占位字段：JVM 契约层只持引用 / 标识；真机位图由宿主 Android 层落 Bitmap，
 *                  null 或空白表示尚未生成位图
 * @param chapterTitle 章节名（§8.4 章节名）
 * @param plainText 正文纯文本（§8.4 正文纯文本，非富文本 / HTML）
 */
data class InkExportPayload(
    val bitmapRef: String?,
    val chapterTitle: String?,
    val plainText: String?,
) {

    companion object {

        /** 大图单边像素上限（锚点卡判据 4：≥4096px 必须降采样兜底，不崩溃） */
        const val MAX_SIDE: Int = 4096

        /**
         * 导出三件是否齐全：位图占位 + 章节名 + 正文纯文本，缺任一件返回 false。
         * null 与 trim 后空白串都视为缺失。
         */
        fun hasAllThreeParts(payload: InkExportPayload): Boolean =
            !payload.bitmapRef.isNullOrBlank() &&
                !payload.chapterTitle.isNullOrBlank() &&
                !payload.plainText.isNullOrBlank()

        /**
         * 大图兜底纯函数：超限按比例降到 [maxSide]，而不是崩溃。
         *
         * - srcW / srcH 任一为 0 或负数：返回安全默认（[maxSide] 见方，不越上限）；
         * - [maxSide] 非正数时回落 [MAX_SIDE]；
         * - 输入合法且未超限：原样返回整数宽高；
         * - 超限：按长边等比缩放，短边同比例，结果单边不超过 [maxSide]，最小为 1。
         */
        fun targetBitmapSize(srcW: Int, srcH: Int, maxSide: Int = MAX_SIDE): IntPair {
            val safeMax = if (maxSide > 0) maxSide else MAX_SIDE
            if (srcW <= 0 || srcH <= 0) return IntPair(safeMax, safeMax)

            val longSide = maxOf(srcW, srcH)
            if (longSide <= safeMax) return IntPair(srcW, srcH)

            val ratio = safeMax.toDouble() / longSide
            val w = (srcW * ratio).roundToInt().coerceIn(1, safeMax)
            val h = (srcH * ratio).roundToInt().coerceIn(1, safeMax)
            return IntPair(w, h)
        }
    }
}
