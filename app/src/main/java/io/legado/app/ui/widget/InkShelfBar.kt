package io.legado.app.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayout

/**
 * 落文 · 书架顶栏
 *
 * 不用 Toolbar / TabLayout 的默认外观，整条横栏自己画：
 * 左端一枚朱砂小印「落」，中间「全部 / 本地」两段题签（选中朱砂 + 短横线），
 * 右端搜索与「地图」入口。底色随日夜切换：绢纸 / 暖墨。
 */
class InkShelfBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private var tabLayout: TabLayout? = null
    var onSearch: (() -> Unit)? = null
    var onMap: (() -> Unit)? = null

    private var selected = 0

    private val night: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val barHeight = dp(56f)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val cInk get() = if (night) 0xFFE8DFCB.toInt() else 0xFF1A1410.toInt()
    private val cDim get() = if (night) 0xFF8B8272.toInt() else 0xFF8C8272.toInt()
    private val cVermilion get() = 0xFFC23B2E.toInt()
    private val cGold get() = 0xFFD4A54A.toInt()

    private val tabStartX get() = dp(84f)
    private val tabWidth get() = dp(52f)

    init {
        setWillNotDraw(false)
        isClickable = true
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(0, top, 0, 0)
            v.requestLayout()
            insets
        }
    }

    fun attach(tab: TabLayout) {
        tabLayout = tab
        selected = tab.selectedTabPosition.coerceAtLeast(0)
        tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                selected = t.position
                invalidate()
            }

            override fun onTabUnselected(t: TabLayout.Tab) = Unit
            override fun onTabReselected(t: TabLayout.Tab) = Unit
        })
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, paddingTop + barHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val cy = paddingTop + barHeight / 2f

        // 底色：绢纸 / 暖墨
        bgPaint.color = if (night) 0xFF16130E.toInt() else 0xFFF7F1E6.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), h, bgPaint)

        // 底部 1px 古铜金线
        linePaint.color = if (night) 0x59D4A54A else 0x33D4A54A
        linePaint.strokeWidth = d
        canvas.drawLine(0f, h - d / 2f, width.toFloat(), h - d / 2f, linePaint)

        // 左端朱砂小印「落」
        val sealSize = dp(19f)
        val sealLeft = dp(18f)
        val sealTop = cy - sealSize / 2f
        linePaint.color = cVermilion
        linePaint.strokeWidth = dp(1.4f)
        canvas.drawRect(sealLeft, sealTop, sealLeft + sealSize, sealTop + sealSize, linePaint)
        textPaint.color = cVermilion
        textPaint.textSize = dp(11f)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("落", sealLeft + sealSize / 2f, sealTop + sealSize / 2f + dp(4f), textPaint)

        // 印旁「文」
        textPaint.color = cInk
        textPaint.textSize = dp(14f)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("文", sealLeft + sealSize + dp(5f), cy + dp(5f), textPaint)

        // 题签：全部 / 本地
        textPaint.textSize = dp(15f)
        textPaint.textAlign = Paint.Align.CENTER
        val labels = arrayOf("全部", "本地")
        labels.forEachIndexed { i, label ->
            val cx = tabStartX + tabWidth * i + tabWidth / 2f
            textPaint.color = if (i == selected) cInk else cDim
            canvas.drawText(label, cx, cy + dp(5f), textPaint)
            if (i == selected) {
                linePaint.color = cVermilion
                linePaint.strokeWidth = dp(1.8f)
                val rw = dp(22f)
                canvas.drawLine(
                    cx - rw / 2f, h - dp(7f),
                    cx + rw / 2f, h - dp(7f), linePaint
                )
            }
        }

        // 右端：搜索（放大镜）
        val searchCx = width - dp(96f)
        iconPaint.color = cDim
        iconPaint.strokeWidth = dp(1.4f)
        canvas.drawCircle(searchCx, cy, dp(6f), iconPaint)
        canvas.drawLine(
            searchCx + dp(4.2f), cy + dp(4.2f),
            searchCx + dp(8.2f), cy + dp(8.2f), iconPaint
        )

        // 右端：「地图」入口（朱砂描边小签）
        val mapRect = RectF(
            width - dp(66f), cy - dp(11f),
            width - dp(16f), cy + dp(11f)
        )
        iconPaint.color = cVermilion
        iconPaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(mapRect, dp(11f), dp(11f), iconPaint)
        textPaint.color = cVermilion
        textPaint.textSize = dp(10.5f)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("地图", mapRect.centerX(), cy + dp(3.6f), textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            if (y > paddingTop + barHeight) {
                performClick()
                return true
            }
            when {
                x >= tabStartX && x < tabStartX + tabWidth -> select(0)
                x >= tabStartX + tabWidth && x < tabStartX + tabWidth * 2 -> select(1)
                x in (width - dp(108f))..(width - dp(84f)) -> onSearch?.invoke()
                x >= width - dp(72f) && x <= width - dp(10f) -> onMap?.invoke()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun select(index: Int) {
        if (selected == index) return
        selected = index
        tabLayout?.getTabAt(index)?.select()
        invalidate()
    }
}
