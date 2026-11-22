package io.legado.app.ui.book.read.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.compose.ui.graphics.toArgb
import io.legado.app.ui.book.read.vertical.CharCell
import io.legado.app.ui.book.read.vertical.VerticalPage

/**
 * 竖排正文自绘 View（模块⑦渲染层）。
 *
 * 只吃竖排引擎产出的 [VerticalPage]：逐字按 [CharCell] 的 x / y / 宽高绘制，
 * 列从右到左、列内字从上到下；拉丁字母 / 数字 / 年份 90° 侧排。
 * 本 View 不做测量与分页——几何全部来自引擎，渲染层只负责把字画到字位上。
 *
 * 兜底：空页 / 空文本只画绢帛底，不崩、不空白态报错。
 */
class VerticalPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** 当前要画的页；null 表示尚未排版（只画底色）。 */
    var page: VerticalPage? = null
        private set

    /** 尺寸变化回调：宿主据此重排竖排引擎（宽高 px）。 */
    var onSizeChanged: ((widthPx: Int, heightPx: Int) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
        color = InkTokens.textPrimary.toArgb()
    }

    /** 引擎翻页 / 重排后回填当前页并触发重绘。 */
    fun render(page: VerticalPage?) {
        this.page = page
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) onSizeChanged?.invoke(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绢帛宣纸底，兜住空页。
        canvas.drawColor(InkTokens.bgPage.toArgb())

        val current = page ?: return
        for (column in current.columns) {
            for (cell in column.cells) {
                drawCell(canvas, cell)
            }
        }
    }

    private fun drawCell(canvas: Canvas, cell: CharCell) {
        val text = cell.char.toString()
        if (text.isEmpty()) return
        // 字身方框边长即字号（标题字更大，引擎已在 cell 尺寸里区分）。
        textPaint.textSize = cell.height.coerceAtLeast(1f)

        val cx = cell.x + cell.width / 2f
        val cy = cell.y + cell.height / 2f

        if (isInlineScript(cell.char)) {
            // 拉丁 / 数字 / 年份：绕字心 90° 侧排。
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(90f)
            drawCentered(canvas, text, 0f, 0f)
            canvas.restore()
        } else {
            drawCentered(canvas, text, cx, cy)
        }
    }

    private fun drawCentered(canvas: Canvas, text: String, cx: Float, cy: Float) {
        val fm = textPaint.fontMetrics
        val advance = textPaint.measureText(text)
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx - advance / 2f, baseline, textPaint)
    }

    /** 与引擎分类口径一致：拉丁字母 / 数字走侧排；CJK、标点、空白保持竖排正字。 */
    private fun isInlineScript(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'
}
