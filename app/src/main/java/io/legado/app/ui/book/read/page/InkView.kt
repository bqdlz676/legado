package io.legado.app.ui.book.read.page

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * 书页手写批注层。
 *
 * 性能三条铁律，照方案落实：
 *  1 落笔即硬化成 Path 缓存，onDraw 只做遍历，不 new 任何东西（current 用复用 Path）；
 *  2 橡皮走 32×32 空间网格，只对命中格里的笔画精算，且擦到是「截断成两笔」不是整笔删；
 *  3 点用 FloatArray 存，每点 3 个 float（x, y, 该点宽），不做装箱。
 *
 * 毛笔感：有笔走 getPressure()，手指走速度反推（快扫则细、缓行则粗），
 * 每 6 点合一段，段内同宽，兼顾手感与绘制次数。
 */
class InkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        /** 朱砂 / 花青 / 藤黄 / 墨灰 */
        val INK_RED = Color.parseColor("#C23B2E")
        val INK_BLUE = Color.parseColor("#2F5D73")
        val INK_YELLOW = Color.parseColor("#D99B3C")
        val INK_GRAY = Color.parseColor("#4A4A46")

        private const val SEG = 6
        private const val GRID = 32
    }

    class Stroke(val color: Int, var baseWidth: Float) {
        var a = FloatArray(48)
        var n = 0

        fun add(x: Float, y: Float, w: Float) {
            if (n + 3 > a.size) a = a.copyOf(a.size * 2)
            a[n++] = x
            a[n++] = y
            a[n++] = w
        }

        fun count() = n / 3
        fun x(i: Int) = a[i * 3]
        fun y(i: Int) = a[i * 3 + 1]
        fun w(i: Int) = a[i * 3 + 2]

        fun copyRange(from: Int, to: Int): Stroke {
            val s = Stroke(color, baseWidth)
            for (i in from until to) s.add(x(i), y(i), w(i))
            return s
        }
    }

    private class Baked(val color: Int, val segs: List<Pair<Path, Float>>)

    private val strokes = ArrayList<Stroke>()
    private var current: Stroke? = null
    private var removing: Stroke? = null
    private var removeAnim: ValueAnimator? = null
    private val baked = ArrayList<Baked>()

    private val index = arrayOfNulls<MutableList<Int>>(GRID * GRID)

    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L

    var inputEnabled: Boolean = false
        set(value) {
            field = value
            current = null
            if (!value) saveNow()
        }

    var brushColor: Int = INK_RED
    var brushWidth: Float = 3.2f
    var eraser: Boolean = false

    /** 2s 防抖后才真正落盘，避免快速涂改时 IO 暴击 */
    var saveListener: ((InkView) -> Unit)? = null
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { saveListener?.invoke(this) }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val livePath = Path()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(0.6f, 6f)
                return true
            }
        }
    )

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
                val w = if (eraser) brushWidth * 4f + 8f else brushWidth
                val s = Stroke(if (eraser) Color.TRANSPARENT else brushColor, w)
                s.add(x, y, w)
                current = s
                lastX = x
                lastY = y
                lastTime = event.eventTime
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val s = current ?: return true
                // 压感优先，没笔就用速度反推：位移大则细（飞白），位移小则粗（顿笔）
                val dt = (event.eventTime - lastTime).coerceAtLeast(1L)
                val dist = sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY))
                val speed = dist / dt
                val pressure = event.pressure
                val hasPen = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                val dyn = if (hasPen) {
                    brushWidth * (0.3f + pressure * 0.7f)
                } else {
                    brushWidth * (0.5f + 0.5f / (1f + speed * 0.3f))
                }
                s.add(x, y, dyn.coerceIn(brushWidth * 0.35f, brushWidth * 1.4f))
                lastX = x
                lastY = y
                lastTime = event.eventTime
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val s = current
                current = null
                if (s != null) {
                    if (eraser) eraseAlong(s) else strokes.add(s)
                    rebuild()
                    scheduleSave()
                }
                invalidate()
            }
        }
        return true
    }

    // ---------- 橡皮：空间网格 + 截断 ----------

    private fun eraseAlong(eraserStroke: Stroke) {
        val reach = eraserStroke.baseWidth / 2f
        val cellW = (width / zoom).coerceAtLeast(1f) / GRID
        val cellH = (height / zoom).coerceAtLeast(1f) / GRID

        val hit = HashSet<Int>(16)
        val n = eraserStroke.count()
        for (i in 0 until n) {
            val cx = (eraserStroke.x(i) / cellW).toInt()
            val cy = (eraserStroke.y(i) / cellH).toInt()
            for (gy in (cy - 1)..(cy + 1)) {
                if (gy !in 0 until GRID) continue
                for (gx in (cx - 1)..(cx + 1)) {
                    if (gx !in 0 until GRID) continue
                    index[gy * GRID + gx]?.forEach { hit.add(it) }
                }
            }
        }

        val replaced = HashMap<Int, List<Stroke>>()
        for (idx in hit) {
            val s = strokes.getOrNull(idx) ?: continue
            val keep = cutOut(s, eraserStroke, reach)
            if (keep.size != 1 || keep[0] !== s) replaced[idx] = keep
        }
        if (replaced.isEmpty()) return

        val next = ArrayList<Stroke>(strokes.size + replaced.size)
        for (i in strokes.indices) {
            val r = replaced[i]
            if (r == null) next.add(strokes[i]) else next.addAll(r)
        }
        strokes.clear()
        strokes.addAll(next)
        rebuild()
        scheduleSave()
    }

    /** 把一笔里被橡皮扫到的段切掉，剩下的两端各自成笔 */
    private fun cutOut(s: Stroke, eraser: Stroke, reach: Float): List<Stroke> {
        val n = s.count()
        val keep = ArrayList<Stroke>(3)
        var from = -1
        for (i in 0 until n) {
            val d = distTo(eraser, s.x(i), s.y(i))
            val inked = d <= reach
            if (!inked) {
                if (from < 0) from = i
            } else {
                if (from >= 0 && i - from >= 2) keep.add(s.copyRange(from, i))
                from = -1
            }
        }
        if (from >= 0 && n - from >= 2) keep.add(s.copyRange(from, n))
        return keep
    }

    private fun distTo(eraser: Stroke, x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        val n = eraser.count()
        for (i in 0 until n) {
            val dx = eraser.x(i) - x
            val dy = eraser.y(i) - y
            val d = dx * dx + dy * dy
            if (d < best) best = d
        }
        return sqrt(best)
    }

    // ---------- 硬化缓存 ----------

    private fun rebuild() {
        baked.clear()
        for (s in strokes) baked.add(bake(s))
        rebuildIndex()
    }

    private fun bake(s: Stroke): Baked {
        val segs = ArrayList<Pair<Path, Float>>(s.count() / SEG + 2)
        val n = s.count()
        var i = 0
        while (i < n - 1) {
            val to = minOf(n, i + SEG)
            val path = Path()
            path.moveTo(s.x(i), s.y(i))
            var w = 0f
            for (k in i until to) {
                path.lineTo(s.x(k), s.y(k))
                w += s.w(k)
            }
            segs.add(path to (w / (to - i).coerceAtLeast(1)))
            i = to - 1
            if (to >= n) break
        }
        return Baked(s.color, segs)
    }

    private fun rebuildIndex() {
        for (i in index.indices) index[i] = null
        val cw = (width / zoom).coerceAtLeast(1f) / GRID
        val ch = (height / zoom).coerceAtLeast(1f) / GRID
        for (si in strokes.indices) {
            val s = strokes[si]
            val n = s.count()
            for (i in 0 until n) {
                val gx = (s.x(i) / cw).toInt().coerceIn(0, GRID - 1)
                val gy = (s.y(i) / ch).toInt().coerceIn(0, GRID - 1)
                val k = gy * GRID + gx
                val list = index[k] ?: ArrayList<Int>(4).also { index[k] = it }
                if (list.isEmpty() || list.last() != si) list.add(si)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildIndex()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(zoom, zoom)

        for (b in baked) {
            paint.color = b.color
            for ((path, w) in b.segs) {
                paint.strokeWidth = w
                canvas.drawPath(path, paint)
            }
        }

        // 撤销：从末端向起点吸回，180ms，不是直接消失
        removing?.let { s ->
            val p = (removeAnim?.animatedValue as? Float) ?: 0f
            if (s.count() >= 1 && p > 0f) {
                canvas.save()
                canvas.scale(p, p, s.x(0), s.y(0))
                paint.color = s.color
                paint.strokeWidth = s.w(0)
                livePath.reset()
                livePath.moveTo(s.x(0), s.y(0))
                for (i in 1 until s.count()) livePath.lineTo(s.x(i), s.y(i))
                canvas.drawPath(livePath, paint)
                canvas.restore()
            }
        }

        current?.let { s ->
            val n = s.count()
            if (n >= 2) {
                paint.color = s.color
                livePath.reset()
                livePath.moveTo(s.x(0), s.y(0))
                for (i in 1 until n) livePath.lineTo(s.x(i), s.y(i))
                paint.strokeWidth = s.w(0)
                canvas.drawPath(livePath, paint)
            }
        }

        canvas.restore()
    }

    // ---------- 对外 ----------

    fun undo() {
        if (removing != null || strokes.isEmpty()) return
        val s = strokes.removeAt(strokes.size - 1)
        removing = s
        removeAnim?.cancel()
        removeAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 180
            addUpdateListener { invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removing = null
                    rebuild()
                    scheduleSave()
                    invalidate()
                }
            })
            start()
        }
        invalidate()
    }

    fun clearAll() {
        strokes.clear()
        removing = null
        rebuild()
        saveNow()
        invalidate()
    }

    fun hasInk(): Boolean = strokes.isNotEmpty()

    fun widthOf(i: Int): Float = strokes.getOrNull(i)?.baseWidth ?: brushWidth

    private fun scheduleSave() {
        saveHandler.removeCallbacks(saveTask)
        saveHandler.postDelayed(saveTask, 2000)
    }

    fun saveNow() {
        saveHandler.removeCallbacks(saveTask)
        saveListener?.invoke(this)
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (s in strokes) {
            val o = JSONObject()
            o.put("c", s.color)
            o.put("w", s.baseWidth)
            val pts = JSONArray()
            for (i in 0 until s.count()) {
                pts.put(s.x(i).toDouble())
                pts.put(s.y(i).toDouble())
                pts.put(s.w(i).toDouble())
            }
            o.put("p", pts)
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(json: String?) {
        strokes.clear()
        removing = null
        if (json.isNullOrBlank()) {
            rebuild()
            invalidate()
            return
        }
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val s = Stroke(o.getInt("c"), o.getDouble("w").toFloat())
                val pts = o.getJSONArray("p")
                // 新格式每点 3 个数；老格式只有 x,y，宽度用笔的 baseWidth
                val step = if (pts.length() % 3 == 0 && pts.length() > 0) 3 else 2
                var j = 0
                while (j + step <= pts.length()) {
                    val x = pts.getDouble(j).toFloat()
                    val y = pts.getDouble(j + 1).toFloat()
                    val w = if (step == 3) pts.getDouble(j + 2).toFloat() else s.baseWidth
                    s.add(x, y, w)
                    j += step
                }
                strokes.add(s)
            }
        }
        rebuild()
        invalidate()
    }
}
