package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONArray
import org.json.JSONObject

/**
 * 书页手写批注层。
 * 透明覆盖在书页之上，只在 inputEnabled 时接收触摸；笔迹存 JSON。
 */
class InkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Stroke(
        val color: Int,
        val width: Float,
        val points: MutableList<Float> = mutableListOf()
    )

    private val strokes = mutableListOf<Stroke>()
    private var current: Stroke? = null
    private var lastX = 0f
    private var lastY = 0f

    var inputEnabled: Boolean = false
        set(value) {
            field = value
            current = null
            if (!value) {
                saveListener?.invoke(this)
            }
        }

    var brushColor: Int = Color.parseColor("#E8453C")
    var brushWidth: Float = 3.2f
    var eraser: Boolean = false

    /** 退出书写或翻页前回调，用于落盘 */
    var saveListener: ((InkView) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(0.6f, 6f)
            invalidate()
            return true
        }
    })

    var zoom: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return false
        scaleDetector.onTouchEvent(event)

        val x = event.x / zoom
        val y = event.y / zoom
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = Stroke(if (eraser) Color.TRANSPARENT else brushColor, brushWidth)
                s.points.add(x)
                s.points.add(y)
                current = s
                lastX = x
                lastY = y
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val s = current ?: return true
                s.points.add(x)
                s.points.add(y)
                lastX = x
                lastY = y
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current?.let { s ->
                    if (eraser) eraseAlong(s) else strokes.add(s)
                }
                current = null
                invalidate()
            }
        }
        return true
    }

    /** 橡皮：删除擦到路径上的笔画 */
    private fun eraseAlong(eraserStroke: Stroke) {
        val r = brushWidth * 4f + 8f
        val it = strokes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            var i = 0
            while (i + 1 < s.points.size) {
                val px = s.points[i]
                val py = s.points[i + 1]
                if (near(px, py, eraserStroke.points, r)) {
                    it.remove()
                    break
                }
                i += 2
            }
        }
    }

    private fun near(x: Float, y: Float, pts: MutableList<Float>, r: Float): Boolean {
        var i = 0
        while (i + 1 < pts.size) {
            val dx = pts[i] - x
            val dy = pts[i + 1] - y
            if (dx * dx + dy * dy <= r * r) return true
            i += 2
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (strokes.isEmpty() && current == null) return
        canvas.save()
        canvas.scale(zoom, zoom)
        strokes.forEach { drawStroke(canvas, it) }
        current?.let { drawStroke(canvas, it) }
        canvas.restore()
    }

    private fun drawStroke(canvas: Canvas, s: Stroke) {
        if (s.points.size < 2) return
        paint.color = s.color
        paint.strokeWidth = s.width
        val path = Path()
        path.moveTo(s.points[0], s.points[1])
        var i = 2
        while (i + 1 < s.points.size) {
            path.lineTo(s.points[i], s.points[i + 1])
            i += 2
        }
        canvas.drawPath(path, paint)
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clearAll() {
        strokes.clear()
        invalidate()
    }

    fun hasInk(): Boolean = strokes.isNotEmpty()

    fun toJson(): String {
        val arr = JSONArray()
        strokes.forEach { s ->
            val o = JSONObject()
            o.put("c", s.color)
            o.put("w", s.width)
            val pts = JSONArray()
            s.points.forEach { pts.put(it.toDouble()) }
            o.put("p", pts)
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(json: String?) {
        strokes.clear()
        if (json.isNullOrBlank()) {
            invalidate()
            return
        }
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val s = Stroke(o.getInt("c"), o.getDouble("w").toFloat())
                val pts = o.getJSONArray("p")
                for (j in 0 until pts.length()) s.points.add(pts.getDouble(j).toFloat())
                strokes.add(s)
            }
        }
        invalidate()
    }
}
