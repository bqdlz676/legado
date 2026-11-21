package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.widget.FrameLayout
import android.widget.TextView
import io.legado.app.R

/**
 * 落文 · 阅读页顶栏
 *
 * 不是 Material AppBar，也不带 Toolbar 菜单。只是一条贴纸面的横栏：
 * 左侧章节名、右侧「书源」小字，底边一条 1px 古铜细线，左端一枚朱砂小印。
 */
class InkReadBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = context.resources.displayMetrics.density
    private val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        color = 0x2E8B6914
    }
    private val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC23B2E.toInt()
    }

    init {
        setWillNotDraw(false)
        clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.updatePadding(top = top)
            insets
        }
    }

    /** 兼容旧 TitleBar 的调用签名：整条横栏即为可点区域 */
    val toolbar: View
        get() = this

    /** 兼容旧 TitleBar 的调用签名：书名，留作后续副标题使用 */
    var title: CharSequence? = null

    /** 兼容旧 TitleBar 的调用签名，改为给章节名着色 */
    fun setTextColor(color: Int) {
        findViewById<TextView>(R.id.tv_chapter_name)?.setTextColor(color)
        findViewById<TextView>(R.id.tv_chapter_url)?.setTextColor(color)
    }

    /** 兼容旧 TitleBar 的调用签名（旧代码给 Toolbar 图标上色），此处无图标，忽略 */
    fun setColorFilter(color: Int) = Unit

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bottom = height.toFloat()
        val y = bottom - hairPaint.strokeWidth / 2f
        canvas.drawLine(0f, y, width.toFloat(), y, hairPaint)
        canvas.drawRect(0f, y - 5f * density, 13f * density, y, sealPaint)
    }
}
