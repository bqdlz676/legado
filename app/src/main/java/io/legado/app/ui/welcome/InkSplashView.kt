package io.legado.app.ui.welcome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser
import io.legado.app.R
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * 落文 · 启动动画（2.8s 单 View 自绘，无图片依赖）
 *
 *   0-400    墨滴坠落（顶圆底尖，带撕裂丝）
 * 400-560    触底炸裂 48 粒：圆粒 20 / 飞白丝 16 / 大块溅墨 12，半径 ≤60dp
 * 560-660    悬停
 * 660-1000   加速回聚（粒子边收边转）
 * 1000-1400  篆书「落文」按笔顺生长（PathMeasure 逐段描出来）
 * 1400-1600  顿
 * 1600-1750  金粉爆 70 粒（飞到 2100 衰减到 0）
 * 1500-1900  水墨远山勾线淡入（不透明度 ≤0.25），山腰雾横向缓移
 * 1700-2100  暖金线顿挫扫过，停在「文」末捺端点，收成一粒呼吸的金点
 * 2100-2400  落款小字
 * 2400-2800  淡出：山先灭，字最后
 */
class InkSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density
    private fun sinF(v: Float): Float = sin(v.toDouble()).toFloat()
    private fun cosF(v: Float): Float = cos(v.toDouble()).toFloat()
    private fun dp(v: Float) = v * d

    private companion object {
        const val BG = 0xFF0A0C14.toInt()
        const val PAPER = 0xFFEDE4D3.toInt()
        const val GOLD = 0xFFD4A54A.toInt()
        const val GOLD_HI = 0xFFF3CD86.toInt()
        const val GREY = 0xFF9B9687.toInt()
        const val HILL = 0xFF8C8A80.toInt()
        const val DROP_END = 400f
        const val BURST_END = 560f
        const val HOLD_END = 660f
        const val GATHER_END = 1000f
        const val WRITE_END = 1400f
        const val PAUSE_END = 1600f
        const val HILL_IN = 1500f
        const val HILL_END = 1900f
        const val SPARK_IN = 1600f
        const val SPARK_END = 1750f
        const val SPARK_LIFE = 2100f
        const val GOLD_IN = 1700f
        const val GOLD_END = 2100f
        const val SLOGAN_IN = 2100f
        const val SLOGAN_END = 2400f
        const val FADE_IN = 2400f
        const val FADE_END = 2800f
    }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF161821.toInt()
    }
    private val inkSolid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1B1E28.toInt()
    }
    private val inkSheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2A2E3A.toInt()
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = PAPER
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = GOLD
    }
    private val goldLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.05f)
        color = GOLD
    }
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = HILL
    }
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GREY
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.06f
    }
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x0EFFFFFF
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rand = Random(20261121L)

    /** 炸裂粒子：kind 0 圆粒 / 1 飞白丝 / 2 大块溅墨 */
    private class Shard(
        val kind: Int,
        val ang: Float,
        val speed: Float,
        val size: Float,
        val len: Float,
        val spin: Float,
        val drift: Float
    )

    /** 金粉 */
    private class Spark(
        val ox: Float, val oy: Float,
        val vx: Float, val vy: Float,
        val size: Float, val life: Float, val bright: Float
    )

    private val shards = ArrayList<Shard>(48)
    private val sparks = ArrayList<Spark>(72)
    private val grain = FloatArray(900)

    /** 一字的笔画：解析后的 Path + 它的长度 */
    private class Stroke(val path: Path, val len: Float)

    private val luoStrokes = ArrayList<Stroke>(12)
    private val wenStrokes = ArrayList<Stroke>(4)
    private var glyphReady = false
    private var strokeWidth = dp(5f)

    private val glyphBounds = RectF()
    private val segPath = Path()
    private val measure = PathMeasure()
    private val matrix = Matrix()

    private var progressMs = 0f
    private var animator: ValueAnimator? = null
    private var w = 0f
    private var h = 0f
    private var cx = 0f
    private var cy = 0f

    init {
        var i = 0
        while (i < 20) {
            shards.add(
                Shard(0,
                    ang = rand.nextFloat() * PI2,
                    speed = dp(16f + rand.nextFloat() * 46f),
                    size = dp(1.4f + rand.nextFloat() * 2.8f),
                    len = 0f,
                    spin = 0f,
                    drift = rand.nextFloat() * 2f - 1f)
            )
            i++
        }
        i = 0
        while (i < 16) {
            shards.add(
                Shard(1,
                    ang = rand.nextFloat() * PI2,
                    speed = dp(22f + rand.nextFloat() * 44f),
                    size = dp(0.7f + rand.nextFloat() * 0.5f),
                    len = dp(7f + rand.nextFloat() * 13f),
                    spin = rand.nextFloat() * 2f - 1f,
                    drift = rand.nextFloat() * 2f - 1f)
            )
            i++
        }
        i = 0
        while (i < 12) {
            shards.add(
                Shard(2,
                    ang = rand.nextFloat() * PI2,
                    speed = dp(14f + rand.nextFloat() * 40f),
                    size = dp(2.6f + rand.nextFloat() * 3.4f),
                    len = 0f,
                    spin = rand.nextFloat() * 2f - 1f,
                    drift = rand.nextFloat() * 2f - 1f)
            )
            i++
        }
        var k = 0
        while (k < grain.size) {
            grain[k] = rand.nextFloat()
            grain[k + 1] = rand.nextFloat()
            k += 2
        }
    }

    fun start(durationMs: Long = 2800L) {
        animator?.cancel()
        progressMs = 0f
        prepareGlyph()
        animator = ValueAnimator.ofFloat(0f, durationMs.toFloat()).apply {
            this.duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                progressMs = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onSizeChanged(nw: Int, nh: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(nw, nh, oldw, oldh)
        w = nw.toFloat()
        h = nh.toFloat()
        cx = w / 2f
        cy = h * 0.46f
        glyphReady = false
    }

    // ---------------- 字形 ----------------

    /** 读取 raw 里的篆书笔画，按目标尺寸装配成可生长的 Stroke 列表 */
    private fun prepareGlyph() {
        val targetH = h * 0.132f
        val gap = h * 0.048f
        val blockH = targetH * 2f + gap
        val top = cy - blockH / 2f

        luoStrokes.clear()
        wenStrokes.clear()
        glyphBounds.setEmpty()

        loadGlyph(R.raw.ink_luo, top, targetH).let { luoStrokes.addAll(it) }
        loadGlyph(R.raw.ink_wen, top + targetH + gap, targetH).let { wenStrokes.addAll(it) }

        strokeWidth = targetH * 0.052f
        glyphPaint.strokeWidth = strokeWidth

        // 字块包围盒（用于金粉起点与金线端点）
        glyphBounds.set(cx - targetH * 0.62f, top, cx + targetH * 0.62f, top + blockH)
        glyphReady = true
    }

    private fun loadGlyph(rawId: Int, topY: Float, targetH: Float): List<Stroke> {
        val text = resources.openRawResource(rawId).bufferedReader().use { it.readText() }
        val raw = ArrayList<Path>(16)
        var b = RectF()
        var inited = false
        text.split('\n').forEach { line ->
            val s = line.trim()
            if (s.isEmpty()) return@forEach
            val p = runCatching { PathParser.createPathFromPathData(s) }.getOrNull() ?: return@forEach
            val r = RectF()
            p.computeBounds(r, true)
            if (!inited) {
                b = RectF(r); inited = true
            } else {
                b.union(r)
            }
            raw.add(p)
        }
        if (raw.isEmpty() || b.height() <= 0f) return emptyList()

        val scale = targetH / b.height()
        val tx = cx - (b.left + b.right) / 2f * scale
        val ty = topY - b.top * scale
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(tx, ty)

        val out = ArrayList<Stroke>(raw.size)
        raw.forEach { p ->
            p.transform(matrix)
            out.add(Stroke(p, if (p.length > 0f) p.length else 1f))
        }
        return out
    }

    private val Path.length: Float
        get() {
            measure.setPath(this, false)
            return measure.length
        }

    /** 按进度 t 把一组笔画描出来 */
    private fun drawGlyph(canvas: Canvas, strokes: List<Stroke>, t: Float, alpha: Int) {
        if (t <= 0f) return
        glyphPaint.alpha = alpha
        val total = strokes.size.toFloat()
        strokes.forEachIndexed { idx, st ->
            val local = ((t * total) - idx).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed
            measure.setPath(st.path, false)
            segPath.reset()
            measure.getSegment(0f, st.len * local, segPath, true)
            canvas.drawPath(segPath, glyphPaint)
        }
        glyphPaint.alpha = 255
    }

    // ---------------- 绘制 ----------------

    override fun onDraw(canvas: Canvas) {
        val p = progressMs
        canvas.drawColor(BG)

        // 深底极淡颗粒
        var i = 0
        while (i < grain.size) {
            canvas.drawCircle(grain[i] * w, grain[i + 1] * h, dp(0.55f), grainPaint)
            i += 2
        }
        if (!glyphReady && h > 0f) prepareGlyph()

        drawDrop(canvas, p)
        drawShards(canvas, p)
        drawGlyphs(canvas, p)
        drawHills(canvas, p)
        drawSparks(canvas, p)
        drawGoldLine(canvas, p)
        drawSlogan(canvas, p)
    }

    /** 墨滴：顶圆底尖，落得越快拉得越长，带一条细撕裂丝 */
    private fun drawDrop(canvas: Canvas, p: Float) {
        if (p >= DROP_END) return
        val t = (p / DROP_END).coerceIn(0f, 1f)
        val ease = t * t
        val startY = -dp(90f)
        val y = startY + (cy - startY) * ease
        val stretch = 0.55f + 0.45f * ease
        val r = dp(9f) * (0.55f + 0.45f * t)

        // 拖尾：越落越长，尾端透明
        val tail = dp(52f) * ease
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, y - tail, cx, y,
                0x00161821, 0xB3161821.toInt(), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(cx - dp(1.6f), y - tail, cx + dp(1.6f), y, trailPaint)

        val drop = Path()
        drop.moveTo(cx, y + r * 1.0f * stretch)                       // 底尖
        drop.cubicTo(cx + r * 0.72f, y + r * 0.25f, cx + r * 0.95f, y - r * 0.18f, cx + r * 0.52f, y - r * 0.78f)
        drop.cubicTo(cx + r * 0.22f, y - r * 1.02f, cx - r * 0.22f, y - r * 1.02f, cx - r * 0.52f, y - r * 0.78f)
        drop.cubicTo(cx - r * 0.95f, y - r * 0.18f, cx - r * 0.72f, y + r * 0.25f, cx, y + r * 1.0f * stretch)
        canvas.drawPath(drop, inkSolid)

        // 撕裂丝：底部两条极细墨线，随机摆
        val tear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99161821.toInt(); strokeWidth = dp(0.8f) }
        val sway = sinF(p * 0.05f)
        canvas.drawLine(cx - dp(3f), y + r * 0.55f, cx - dp(3f) + sway * dp(2f), y + r * 1.35f, tear)
        canvas.drawLine(cx + dp(3f), y + r * 0.6f, cx + dp(3f) - sway * dp(2f), y + r * 1.2f, tear)
    }

    /** 炸裂 → 悬停 → 回聚 */
    private fun drawShards(canvas: Canvas, p: Float) {
        if (p < BURST_END || p >= GATHER_END) return
        val outT = ((p - BURST_END) / (HOLD_END - BURST_END)).coerceIn(0f, 1f)  // 炸开
        val holdT = ((p - HOLD_END) / 60f).coerceIn(0f, 1f)                     // 悬停抖动
        val backT = ((p - HOLD_END - 60f) / (GATHER_END - HOLD_END - 60f)).coerceIn(0f, 1f)

        val outE = 1f - (1f - outT) * (1f - outT)
        val backE = backT * backT * backT   // 加速吸入
        val radius = (1f - backE)
        val jitterAmp = (1f + 0.8f * sinF(p * 0.24f)) * (if (holdT > 0f && backT <= 0f) 1.4f else 1f)

        shards.forEach { s ->
            val dist = s.speed * outE * radius
            val jx = sinF(p * 0.11f + s.ang) * dp(1.2f) * jitterAmp
            val jy = cosF(p * 0.09f + s.ang * 1.7f) * dp(1.2f) * jitterAmp
            val x = cx + cosF(s.ang) * dist + jx + s.drift * dp(3f) * backE
            val y = cy + sinF(s.ang) * dist * 0.86f + jy
            val fade = (1f - backE).coerceIn(0f, 1f)
            val alpha = (255 * fade * (0.55f + 0.45f * (1f - backT))).toInt().coerceIn(0, 255)
            if (alpha <= 4) return@forEach

            when (s.kind) {
                0 -> {
                    inkPaint(alpha)
                    canvas.drawCircle(x, y, s.size * (1f - 0.3f * backE), ink)
                }
                1 -> {
                    val rot = s.spin * backT * 220f
                    val rad = Math.toRadians((s.ang + rot).toDouble())
                    val dx = cos(rad).toFloat()
                    val dy = sin(rad).toFloat()
                    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF161821.toInt()
                        strokeWidth = s.size
                    }
                    linePaint.alpha = alpha
                    canvas.drawLine(
                        x, y,
                        x + dx * s.len * (1f - 0.6f * backE),
                        y + dy * s.len * (1f - 0.6f * backE),
                        linePaint
                    )
                }
                else -> {
                    inkPaint(alpha)
                    val blob = Path()
                    val rr = s.size * (1f - 0.35f * backE)
                    blob.moveTo(x - rr, y)
                    blob.quadTo(x - rr * 0.2f, y - rr * 0.9f, x + rr * 0.7f, y - rr * 0.5f)
                    blob.quadTo(x + rr * 1.05f, y + rr * 0.35f, x + rr * 0.15f, y + rr * 0.75f)
                    blob.quadTo(x - rr * 0.75f, y + rr * 0.8f, x - rr, y)
                    canvas.drawPath(blob, inkSolid)
                }
            }
        }
        ink.alpha = 255
    }

    private fun inkPaint(alpha: Int) {
        ink.alpha = alpha
    }

    /** 篆书生长 + 顿 + 淡出 */
    private fun drawGlyphs(canvas: Canvas, p: Float) {
        if (p < GATHER_END) return
        val writeT = ((p - GATHER_END) / (WRITE_END - GATHER_END)).coerceIn(0f, 1f)
        // 初段淡如水渍，后段凝成浓墨
        val inkUp = (70f + 185f * writeT * writeT).toInt()
        // 「落」占前 62%，「文」后 50% 起步
        val tLuo = (writeT / 0.62f).coerceIn(0f, 1f)
        val tWen = ((writeT - 0.42f) / 0.58f).coerceIn(0f, 1f)
        // 字最后走：2600-2800 收干净
        val fade = ((p - 2600f) / 200f).coerceIn(0f, 1f)
        val alpha = (inkUp * (1f - fade)).toInt().coerceIn(0, 255)
        if (alpha <= 3) return
        drawGlyph(canvas, luoStrokes, tLuo, alpha)
        drawGlyph(canvas, wenStrokes, tWen, alpha)
    }

    /** 远山：只勾线，最高 0.25 不透明度；山腰雾气横向缓移 */
    private fun drawHills(canvas: Canvas, p: Float) {
        if (p < HILL_IN) return
        val inT = ((p - HILL_IN) / (HILL_END - HILL_IN)).coerceIn(0f, 1f)
        val fadeT = ((p - 2400f) / 160f).coerceIn(0f, 1f)  // 山最先灭
        val a = (0.25f * inT * (1f - fadeT) * 255f).toInt().coerceIn(0, 64)
        if (a <= 2) return
        hillPaint.alpha = a

        val baseY = h * 0.735f
        for (layer in 0..1) {
            val path = Path()
            val y0 = baseY + layer * h * 0.024f
            val amp = h * (0.017f + layer * 0.009f)
            path.moveTo(-w * 0.04f, y0)
            var k = 0
            while (k <= 6) {
                val x1 = w * 0.05f + w * 0.9f * k / 6f
                val y1 = y0 - amp * (if (k % 2 == 0) 1f else 0.42f)
                val x2 = w * 0.05f + w * 0.9f * (k + 1) / 6f
                val y2 = y0 - amp * (if (k % 2 == 0) 0.3f else 1f)
                path.quadTo(x1, y1, x2, y2)
                k += 2
            }
            canvas.drawPath(path, hillPaint)
        }

        // 雾：横带渐变，中心缓慢左右飘
        val fogT = ((p - HILL_IN) / (HILL_END - HILL_IN)).coerceIn(0f, 1f)
        val drift = sinF(p * 0.0035f) * w * 0.18f
        fogPaint.shader = LinearGradient(
            cx + drift - w * 0.45f, baseY - h * 0.012f,
            cx + drift + w * 0.45f, baseY + h * 0.012f,
            intArrayOf(0x00EDE4D3, 0x1FEDE4D3, 0x00EDE4D3),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        fogPaint.alpha = (fogT * (1f - fadeT) * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(
            cx + drift - w * 0.45f, baseY - h * 0.03f,
            cx + drift + w * 0.45f, baseY + h * 0.03f,
            fogPaint
        )
        fogPaint.alpha = 255
    }

    /** 金粉：从字面飞出，向上飘，0.5s 衰减 */
    private fun drawSparks(canvas: Canvas, p: Float) {
        if (p < SPARK_IN || p >= SPARK_LIFE) return
        if (sparks.isEmpty()) buildSparks()
        val t0 = ((p - SPARK_IN) / (SPARK_END - SPARK_IN)).coerceIn(0f, 1f)
        val age = p - SPARK_IN
        val active = (t0 * sparks.size).toInt()

        for (i in 0 until active.coerceAtMost(sparks.size)) {
            val s = sparks[i]
            val lifeT = ((age / (SPARK_LIFE - SPARK_IN)) * (0.55f + 0.45f * s.life)).coerceIn(0f, 1f)
            val a = (255 * (1f - lifeT) * s.bright).toInt().coerceIn(0, 255)
            if (a <= 6) continue
            val x = s.ox + s.vx * lifeT * dp(120f)
            val y = s.oy + s.vy * lifeT * dp(120f) + lifeT * lifeT * dp(14f)
            goldPaint.color = if (s.bright > 0.75f) GOLD_HI else GOLD
            goldPaint.alpha = a
            canvas.drawCircle(x, y, s.size, goldPaint)
        }
        goldPaint.alpha = 255
    }

    private fun buildSparks() {
        sparks.clear()
        var i = 0
        while (i < 72) {
            val bx = glyphBounds.left + rand.nextFloat() * glyphBounds.width()
            val by = glyphBounds.top + rand.nextFloat() * glyphBounds.height()
            val ang = -PI_HALF + (rand.nextFloat() - 0.5f) * 2.4f
            val sp = 0.35f + rand.nextFloat() * 0.95f
            sparks.add(
                Spark(
                    ox = bx, oy = by,
                    vx = cosF(ang) * sp,
                    vy = sinF(ang) * sp,
                    size = dp(0.7f + rand.nextFloat() * 1.4f),
                    life = rand.nextFloat(),
                    bright = 0.5f + rand.nextFloat() * 0.5f
                )
            )
            i++
        }
    }

    /** 暖金线：顿挫走笔，停在「文」末捺端点，收成一粒呼吸金点 */
    private fun drawGoldLine(canvas: Canvas, p: Float) {
        if (p < GOLD_IN) return
        val t = ((p - GOLD_IN) / (GOLD_END - GOLD_IN)).coerceIn(0f, 1f)
        val startX = w * 0.06f
        val endX = cx + strokeWidth * 1.2f
        val gy = glyphBounds.bottom - strokeWidth * 0.7f

        // 顿挫：7 段，每段先快后慢，段间微停
        val segs = 7
        val segT = (t * segs).coerceIn(0f, segs.toFloat())
        val idx = segT.toInt().coerceAtMost(segs - 1)
        val local = (segT - idx).coerceIn(0f, 1f)
        val eased = (idx + (1f - (1f - local) * (1f - local))) / segs
        val gx = startX + (endX - startX) * eased.coerceIn(0f, 1f)

        // 已走过的一段留一条极淡的痕
        val trace = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26D4A54A }
        canvas.drawLine(startX, gy, gx, gy, trace)

        canvas.drawLine(startX, gy, gx, gy, goldLinePaint)

        // 笔头辉光
        glowPaint.shader = RadialGradient(
            gx, gy - dp(4f), dp(44f),
            intArrayOf(0x55D4A54A, 0x00D4A54A),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(gx, gy - dp(4f), dp(44f), glowPaint)

        if (t >= 1f) {
            val breath = 0.55f + 0.45f * sinF(p * 0.006f)
            goldPaint.alpha = (210 * breath).toInt().coerceIn(0, 255)
            canvas.drawCircle(endX, gy, dp(2.6f), goldPaint)
            glowPaint.shader = RadialGradient(
                endX, gy, dp(28f),
                intArrayOf((0x40 * breath).toInt().coerceIn(0, 255) shl 24 or 0xD4A54A, 0x00D4A54A),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(endX, gy, dp(28f), glowPaint)
            goldPaint.alpha = 255
        }

        // 淡出
        val fade = ((p - 2600f) / 200f).coerceIn(0f, 1f)
        if (fade > 0f) {
            goldLinePaint.alpha = (255 * (1f - fade)).toInt()
            canvas.drawLine(startX, gy, endX, gy, goldLinePaint)
            goldLinePaint.alpha = 255
        }
    }

    /** 落款小字 */
    private fun drawSlogan(canvas: Canvas, p: Float) {
        if (p < SLOGAN_IN) return
        val t = ((p - SLOGAN_IN) / (SLOGAN_END - SLOGAN_IN)).coerceIn(0f, 1f)
        val fade = ((p - 2460f) / 200f).coerceIn(0f, 1f)
        sloganPaint.alpha = (200 * t * (1f - fade)).toInt().coerceIn(0, 255)
        canvas.drawText("读其文，知其人，通其时。", cx, h * 0.80f, sloganPaint)
        sloganPaint.alpha = 255
    }
}

private const val PI2 = (Math.PI * 2.0).toFloat()
private const val PI_HALF = (Math.PI / 2.0).toFloat()
