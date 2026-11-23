package io.legado.app.ui.book.read.ink

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.Interpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.toArgb
import io.legado.app.ui.book.read.vertical.CharCell
import io.legado.app.ui.book.read.vertical.VerticalPage
import io.legado.app.utils.dpToPx
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 竖排正文自绘 View（模块⑦渲染层，规格切片-09 §3.1~3.3）。
 *
 * 只吃竖排引擎产出的 [VerticalPage]：逐字按 [CharCell] 的 x / y / 宽高绘制，
 * 列从右到左、列内字从上到下；拉丁字母 / 数字 / 年份 90° 侧排。
 * 本 View 不做测量与分页——几何全部来自引擎，渲染层只负责把字画到字位上。
 *
 * 纸面质感（§3.2）：宣纸底 #F7F1E6 + 2px 级噪点颗粒；左上 2800K 烛光暖光渐变，
 * 右下略暗；章节氛围粒子以极淡水墨散点勾勒，不是贴图。
 * 翻页（§3.3）：右下角掀起纸角 400ms，背面墨色透印 alpha 15%，翻角带物理阴影。
 *
 * 兜底：空页 / 空文本只画宣纸底与光影，不崩、不空白态报错。
 */
class VerticalPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** 当前要画的页；null 表示尚未排版（只画纸面）。 */
    var page: VerticalPage? = null
        private set

    /** 尺寸变化回调：宿主据此重排竖排引擎（宽高 px）。 */
    var onSizeChanged: ((widthPx: Int, heightPx: Int) -> Unit)? = null

    /** 翻页动画结束回调：宿主据此复位动画中屏蔽位。 */
    var onTurnAnimEnd: (() -> Unit)? = null

    /** 正文宋体；null 时取 [InkFonts] 注入值，再兜底系统 serif。U9 字体系统收口注入。 */
    var typeface: Typeface? = null
        set(value) {
            field = value
            textPaint.typeface = effectiveTypeface
            invalidate()
        }

    private val effectiveTypeface: Typeface
        get() = typeface ?: InkFonts.viewTypeface

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = effectiveTypeface
        color = InkTokens.textPrimary.toArgb()
    }

    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 噪点瓦片（48dp 见方，跨屏平铺），尺寸 / 密度变化时重建。 */
    private var noiseTile: Bitmap? = null
    private var noiseTileSizePx: Int = 0

    /** 氛围粒子预生成：归一化坐标 + 半径比，种子固定避免每帧抖动。 */
    private var particles: List<Particle> = emptyList()

    // ── 掀角翻页动画状态 ──────────────────────────────────────────────

    private var turnAnimator: ValueAnimator? = null
    private var peelProgress = 0f
    private var turnAnimating = false

    private data class Particle(
        val xRatio: Float,
        val yRatio: Float,
        val radiusRatio: Float,
        val alpha: Float,
    )

    /** 引擎翻页 / 重排后回填当前页并触发重绘。 */
    fun render(page: VerticalPage?) {
        this.page = page
        invalidate()
    }

    /** 触发右下角掀角翻页动画；[direction] 预留（Prev / Next 均自右下掀角，规格只定义一种掀法）。 */
    fun startPageTurn(direction: EdgeDirection) {
        turnAnimator?.cancel()
        turnAnimating = true
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = InkTokens.DURATION_PAGE.toLong()
            interpolator = ComposeEasingInterpolator(InkTokens.pageTurnEasing)
            addUpdateListener {
                peelProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    turnAnimating = false
                    peelProgress = 0f
                    onTurnAnimEnd?.invoke()
                    invalidate()
                }
            })
        }
        turnAnimator = animator
        animator.start()
    }

    /** 翻页动画是否进行中（进行中屏蔽新气泡与边缘翻页，避免动画打架）。 */
    fun isTurnAnimating(): Boolean = turnAnimating

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            rebuildNoiseTile(w, h)
            rebuildParticles()
            onSizeChanged?.invoke(w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        // §3.2 宣纸底
        canvas.drawColor(InkTokens.bgPage.toArgb())

        drawCandlelight(canvas, w, h)
        drawNoise(canvas, w, h)
        drawParticles(canvas, w, h)

        val current = page
        if (current != null) {
            for (column in current.columns) {
                for (cell in column.cells) {
                    drawCell(canvas, cell)
                }
            }
            drawProgressLine(canvas, w, h, current.progress)
        }

        drawPeel(canvas, w, h)
    }

    // ── §3.2 光影 / 颗粒 / 粒子 ──────────────────────────────────────

    private fun drawCandlelight(canvas: Canvas, w: Int, h: Int) {
        // 左上 2800K 烛光：金辉自左上角径向淡出；右下叠一层墨色薄纱压暗。
        val radius = hypot(w.toFloat(), h.toFloat()) * InkTokens.CANDLELIGHT_RADIUS_RATIO
        val glow = RadialGradient(
            0f, 0f, radius,
            intArrayOf(
                InkTokens.brandGold.copy(alpha = InkTokens.CANDLELIGHT_ALPHA).toArgb(),
                InkTokens.brandGold.copy(alpha = 0f).toArgb(),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        paperPaint.shader = glow
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paperPaint)
        paperPaint.shader = null

        val shade = LinearGradient(
            w.toFloat(), h.toFloat(), w.toFloat() * 0.55f, h.toFloat() * 0.55f,
            InkTokens.textPrimary.copy(alpha = InkTokens.CORNER_SHADE_ALPHA).toArgb(),
            InkTokens.textPrimary.copy(alpha = 0f).toArgb(),
            Shader.TileMode.CLAMP,
        )
        paperPaint.shader = shade
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paperPaint)
        paperPaint.shader = null
    }

    private fun rebuildNoiseTile(w: Int, h: Int) {
        val tilePx = 48f.dpToPx().toInt().coerceAtMost(min(w, h) / 2)
        if (tilePx <= 0) return
        if (noiseTile != null && noiseTileSizePx == tilePx) return
        noiseTile?.recycle()
        val tile = Bitmap.createBitmap(tilePx, tilePx, Bitmap.Config.ALPHA_8)
        val rand = Random(NOISE_SEED)
        val grid = InkTokens.NOISE_GRID_DP.dpToPx().toInt().coerceAtLeast(1)
        for (y in 0 until tilePx step grid) {
            for (x in 0 until tilePx step grid) {
                // 每格约三成概率落一颗 2px 级颗粒，alpha 按噪点阈值抖动。
                if (rand.nextFloat() < 0.3f) {
                    val v = (rand.nextFloat() * 255 * InkTokens.NOISE_ALPHA / NOISE_ALPHA_MAX).roundToInt()
                    tile.setPixel(x, y, v)
                }
            }
        }
        noiseTile = tile
        noiseTileSizePx = tilePx
    }

    private fun drawNoise(canvas: Canvas, w: Int, h: Int) {
        val tile = noiseTile ?: return
        val size = noiseTileSizePx
        noisePaint.color = InkTokens.textPrimary.toArgb()
        noisePaint.colorFilter = null
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                canvas.drawBitmap(tile, x.toFloat(), y.toFloat(), noisePaint)
                x += size
            }
            y += size
        }
    }

    private fun rebuildParticles() {
        val rand = Random(PARTICLE_SEED)
        particles = List(InkTokens.PARTICLE_COUNT) {
            Particle(
                xRatio = rand.nextFloat(),
                yRatio = rand.nextFloat(),
                radiusRatio = rand.nextFloat(),
                alpha = InkTokens.PARTICLE_ALPHA * (0.5f + rand.nextFloat() * 0.5f),
            )
        }
    }

    private fun drawParticles(canvas: Canvas, w: Int, h: Int) {
        val minRadius = InkTokens.PARTICLE_RADIUS_MIN_DP.value.dpToPx()
        val maxRadius = InkTokens.PARTICLE_RADIUS_MAX_DP.value.dpToPx()
        particlePaint.color = InkTokens.textPrimary.toArgb()
        for (p in particles) {
            val radius = minRadius + (maxRadius - minRadius) * p.radiusRatio
            particlePaint.alpha = (p.alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(p.xRatio * w, p.yRatio * h, radius, particlePaint)
        }
        particlePaint.alpha = 255
    }

    // ── §3.1 右缘进度线 ──────────────────────────────────────────────

    private fun drawProgressLine(canvas: Canvas, w: Int, h: Int, progress: Float) {
        val lineWidth = InkTokens.PROGRESS_LINE_WIDTH.value.dpToPx()
        val x = w - lineWidth / 2f
        val clamped = progress.coerceIn(0f, 1f)
        // 未读：云灰（textTertiary）通底
        linePaint.color = InkTokens.textTertiary.toArgb()
        canvas.drawLine(x, 0f, x, h.toFloat(), linePaint)
        // 已读：朱砂，自顶向下覆盖
        if (clamped > 0f) {
            linePaint.color = InkTokens.textAccent.toArgb()
            canvas.drawLine(x, 0f, x, h * clamped, linePaint)
        }
    }

    // ── §3.3 掀角翻页 ────────────────────────────────────────────────

    private fun drawPeel(canvas: Canvas, w: Int, h: Int) {
        if (peelProgress <= 0f) return
        val corner = w * InkTokens.PEEL_CORNER_FRACTION
        // 折起量：角点沿对角线缩回；背面透印 15% 墨色。
        val peel = corner * peelProgress
        val backPath = Path().apply {
            moveTo(w.toFloat(), h.toFloat())
            lineTo(w - peel, h.toFloat())
            lineTo(w.toFloat(), h - peel)
            close()
        }
        peelPaint.color = InkTokens.textPrimary
            .copy(alpha = InkTokens.BACK_TRANSMISSION_ALPHA)
            .toArgb()
        canvas.drawPath(backPath, peelPaint)

        // 翻角物理阴影：折痕线另一侧的柔影，随掀角幅度增强。
        shadowPaint.color = InkTokens.textPrimary
            .copy(alpha = InkTokens.PEEL_SHADOW_ALPHA * peelProgress)
            .toArgb()
        shadowPaint.strokeWidth = 2f.dpToPx()
        canvas.drawLine(
            w - peel, h.toFloat(),
            w.toFloat(), h - peel,
            shadowPaint,
        )
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

    private companion object {
        const val NOISE_SEED = 20261123L
        const val PARTICLE_SEED = 77440101L
        const val NOISE_ALPHA_MAX = 255f
    }
}

/** Compose [CubicBezierEasing] → Android [Interpolator] 适配，翻页掀角沿用同一根曲线。 */
private class ComposeEasingInterpolator(
    private val easing: CubicBezierEasing,
) : Interpolator {
    override fun getInterpolation(input: Float): Float = easing.transform(input)
}
