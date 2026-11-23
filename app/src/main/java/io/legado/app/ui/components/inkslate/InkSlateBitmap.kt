package io.legado.app.ui.components.inkslate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.legado.app.ui.components.ComponentPalette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * U7 画板 · PNG 原生位图渲染（切片-12 §8.1 PNG/SVG 导出）。
 *
 * 与 [InkSlateCanvas] 消费同一份不可变 [InkSlateState]，几何口径逐点对齐：
 * 画笔折线压感变宽、直线 / 箭头 / 矩形 / 圆描边、文字落点渲染；橡皮不产生几何。
 * 本类只持有 android.graphics 类型，不引 Compose；位图编码由宿主 Activity 落盘 / 分享。
 *
 * 坐标与线宽沿用画布 px 口径（与 DrawScope 直传 strokeWidth 一致）；
 * [density] 仅用于文字 sp → px 换算，不缩放笔迹线宽。
 */
object InkSlateBitmap {

    /**
     * 渲染整幅画板到位图：纸白底 → 批注底图 → 底图层笔迹 → 墨迹层笔迹 → 底部附注。
     *
     * @param background 批注模式带入的正文页截图（可空：自由画板无底图）
     * @param density 屏幕密度（仅文字 sp → px 使用）
     */
    fun render(state: InkSlateState, background: Bitmap?, density: Float): Bitmap {
        val w = state.canvasWidth.toInt().coerceAtLeast(1)
        val h = state.canvasHeight.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ComponentPalette.BG_PAGE.argbInt())

        background?.let { bg ->
            val dst = android.graphics.Rect(0, 0, w, h)
            canvas.drawBitmap(bg, null, dst, null)
        }

        // 图层顺序与 Compose 画布一致：底图层先绘，墨迹层后绘
        InkLayer.entries.forEach { layer ->
            state.strokes.filter { it.layer == layer }.forEach { drawStroke(canvas, it, density) }
            state.currentStroke?.takeIf { it.layer == layer }?.let { drawStroke(canvas, it, density) }
        }

        state.annotation?.footnote()?.let { footnote ->
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ComponentPalette.TEXT_SECONDARY.argbInt()
                textSize = 12f * density
                typeface = Typeface.SERIF
            }
            canvas.drawText(footnote, 8f, h - 8f, notePaint)
        }
        return bitmap
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke, density: Float) {
        if (stroke.points.isEmpty()) return
        val color = stroke.color.argbInt()
        when (stroke.tool) {
            InkTool.LINE, InkTool.ARROW -> {
                val a = stroke.points.first()
                val b = stroke.points.last()
                val line = paint(color, stroke.width, Paint.Cap.ROUND)
                canvas.drawLine(a.x, a.y, b.x, b.y, line)
                if (stroke.tool == InkTool.ARROW) drawArrowHead(canvas, a, b, color, stroke.width)
            }

            InkTool.RECT -> {
                val a = stroke.points.first()
                val b = stroke.points.last()
                val rect = RectF(
                    minOf(a.x, b.x),
                    minOf(a.y, b.y),
                    maxOf(a.x, b.x),
                    maxOf(a.y, b.y),
                )
                val p = paint(color, stroke.width, Paint.Cap.ROUND)
                p.style = Paint.Style.STROKE
                canvas.drawRect(rect, p)
            }

            InkTool.CIRCLE -> {
                val a = stroke.points.first()
                val b = stroke.points.last()
                val cx = (a.x + b.x) / 2f
                val cy = (a.y + b.y) / 2f
                val r = hypot(b.x - a.x, b.y - a.y) / 2f
                val p = paint(color, stroke.width, Paint.Cap.ROUND)
                p.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, r, p)
            }

            InkTool.BRUSH -> drawBrush(canvas, stroke)

            InkTool.TEXT -> {
                val a = stroke.points.first()
                val text = stroke.text ?: return
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    textSize = stroke.width * 3f * density
                    typeface = Typeface.SERIF
                }
                canvas.drawText(text, a.x, a.y, p)
            }

            InkTool.ERASER -> Unit
        }
    }

    private fun drawBrush(canvas: Canvas, stroke: Stroke) {
        val points = stroke.points
        if (points.size == 1) {
            val p = points.first()
            val dotWidth = stroke.width * p.pressure
            val dot = paint(stroke.color.argbInt(), dotWidth, Paint.Cap.ROUND)
            canvas.drawCircle(p.x, p.y, dotWidth / 2f, dot)
            return
        }
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val width = stroke.width * ((a.pressure + b.pressure) / 2f)
            val p = paint(stroke.color.argbInt(), width, Paint.Cap.ROUND)
            canvas.drawLine(a.x, a.y, b.x, b.y, p)
        }
    }

    private fun drawArrowHead(
        canvas: Canvas,
        a: StrokePoint,
        b: StrokePoint,
        color: Int,
        width: Float,
    ) {
        val angle = atan2(b.y - a.y, b.x - a.x)
        val head = 10f + width * 2f
        val left = angle + Math.PI * 5 / 6
        val right = angle - Math.PI * 5 / 6
        val lx = b.x + head * cos(left).toFloat()
        val ly = b.y + head * sin(left).toFloat()
        val rx = b.x + head * cos(right).toFloat()
        val ry = b.y + head * sin(right).toFloat()
        val p = paint(color, width, Paint.Cap.ROUND)
        canvas.drawLine(lx, ly, b.x, b.y, p)
        canvas.drawLine(rx, ry, b.x, b.y, p)
    }

    private fun paint(color: Int, widthPx: Float, cap: Paint.Cap): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = widthPx.coerceAtLeast(1f)
        strokeCap = cap
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    /** ARGB Long → 平台 Int 色值（掩码去符号位，避免 Compose Color 依赖）。 */
    private fun Long.argbInt(): Int = (this and 0xFFFFFFFFL).toInt()
}
