package io.legado.app.ui.book.read.ink

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
import android.view.Choreographer
import android.view.View
import androidx.compose.ui.graphics.toArgb
import io.legado.app.ui.book.read.vertical.CharCell
import io.legado.app.ui.book.read.vertical.VerticalPage
import io.legado.app.ui.book.read.vertical.VerticalTokens
import io.legado.app.utils.dpToPx
import io.legado.app.utils.spToPx
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 竖排正文自绘 View（模块⑦渲染层，规格切片-09 §3.1~3.3）。
 *
 * 只吃竖排引擎产出的 [VerticalPage]：逐字按 [CharCell] 的 x / y / 宽高绘制，
 * 列从右到左、列内字从上到下；拉丁字母 / 数字 / 年份 90° 侧排。
 * 本 View 不做测量与分页——几何全部来自引擎，渲染层只负责把字画到字位上。
 *
 * 纸面质感（§3.2）：宣纸底 #F7F1E6 + 2px 级噪点颗粒；左上 2800K 烛光暖光渐变，
 * 右下略暗；章节氛围粒子极缓漂移 + 明灭（固定种子可复现，低端机减半并静止）。
 * 点字墨爆（§4 交互铁律）：独立 100ms 字面冲击环 + 飞散墨点，不挂气泡入场动画。
 * 翻页（§3.3）：右下角掀起纸角，真 spring 物理（刚度 / 阻尼比见 [InkTokens]），
 * 整定 400ms 量级；背面墨色透印 alpha 15%，翻角带物理阴影。
 *
 * 帧预算守卫：单帧绘制超 [InkTokens.FRAME_BUDGET_MS] 连续若干帧即进入降级档
 * （粒子减半、噪点叠层跳过）；兜底：空页 / 空文本只画宣纸底与光影，不崩、不空白态报错。
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

    /** 正文字号 px；标题字号 px；标题判定由 [InkSpec.isTitleCellSize] 按二者中点阈值完成。 */
    private val bodySizePx: Float by lazy { VerticalTokens.BODY_SP.spToPx() }
    private val titleSizePx: Float by lazy {
        (VerticalTokens.BODY_SP + VerticalTokens.TITLE_SIZE_OFFSET_SP).spToPx()
    }

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
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 噪点瓦片（48dp 见方，跨屏平铺），尺寸 / 密度变化时重建。 */
    private var noiseTile: Bitmap? = null
    private var noiseTileSizePx: Int = 0

    /** 氛围粒子（归一化坐标 + 漂移速度比 + 明灭相位），种子固定。 */
    private var particles: List<Particle> = emptyList()

    // ── 掀角 spring 翻页状态 ──────────────────────────────────────────

    private val peelSpring = PeelSpring(
        stiffness = InkTokens.PEEL_SPRING_STIFFNESS,
        dampingRatio = InkTokens.PEEL_SPRING_DAMPING_RATIO,
    )
    private var peelProgress = 0f
    private var turnAnimating = false

    // ── 点字墨爆状态（独立 100ms，不挂气泡入场） ──────────────────────

    private var burstActive = false
    private var burstStartMs = 0L
    private var burstCenterX = 0f
    private var burstCenterY = 0f
    private var burstFlecks: List<Fleck> = emptyList()

    // ── 帧循环 / 帧预算守卫 ──────────────────────────────────────────

    private val choreographer = Choreographer.getInstance()
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameMs = 0L
    private var overBudgetStreak = 0
    private var degraded = false

    private data class Particle(
        val xRatio: Float,
        val yRatio: Float,
        val radiusRatio: Float,
        val baseAlpha: Float,
        val vxRatioPerSec: Float,
        val vyRatioPerSec: Float,
        val phase: Float,
        val twinkleSpeed: Float,
    )

    private data class Fleck(
        val angle: Float,
        val distanceRatio: Float,
        val radiusRatio: Float,
    )

    /** 引擎翻页 / 重排后回填当前页并触发重绘。 */
    fun render(page: VerticalPage?) {
        this.page = page
        invalidate()
    }

    /**
     * 触发右下角掀角翻页；弹簧积分在帧回调里推进，整定后复位屏蔽位。
     * [direction] 预留（Prev / Next 均自右下掀角，规格只定义一种掀法）。
     */
    fun startPageTurn(direction: EdgeDirection) {
        peelSpring.reset()
        peelProgress = 0f
        turnAnimating = true
        ensureFrameLoop()
    }

    /** 点字墨爆：在字心 (cx, cy) 起独立 100ms 冲击，与气泡 250ms 入场互不挂接。 */
    fun burstAt(cx: Float, cy: Float) {
        burstCenterX = cx
        burstCenterY = cy
        burstStartMs = nowMillis()
        burstActive = true
        val rand = Random(BURST_SEED xor (cx.roundToInt() * 31L) xor (cy.roundToInt() * 17L))
        burstFlecks = List(InkTokens.BURST_FLECK_COUNT) {
            Fleck(
                angle = rand.nextFloat() * (2f * Math.PI.toFloat()),
                distanceRatio = 0.03f + rand.nextFloat() * 0.05f,
                radiusRatio = rand.nextFloat(),
            )
        }
        ensureFrameLoop()
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
        val drawStart = System.nanoTime()
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        // §3.2 宣纸底
        canvas.drawColor(InkTokens.bgPage.toArgb())

        drawCandlelight(canvas, w, h)
        if (!degraded) drawNoise(canvas, w, h)
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

        drawBurst(canvas)
        drawPeel(canvas, w, h)

        noteFrameBudget(drawStart)
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
        val lowSpec = InkSpec.isLowSpec() || degraded
        val count = InkSpec.particleCount(
            full = InkTokens.PARTICLE_COUNT,
            low = InkTokens.PARTICLE_COUNT_LOW_SPEC,
            lowSpec = lowSpec,
        )
        val rand = Random(PARTICLE_SEED)
        particles = List(count) {
            val drift = if (lowSpec) 0f else InkTokens.PARTICLE_DRIFT_RATIO_PER_SEC
            Particle(
                xRatio = rand.nextFloat(),
                yRatio = rand.nextFloat(),
                radiusRatio = rand.nextFloat(),
                baseAlpha = InkTokens.PARTICLE_ALPHA * (0.5f + rand.nextFloat() * 0.5f),
                vxRatioPerSec = (rand.nextFloat() - 0.5f) * drift,
                vyRatioPerSec = (rand.nextFloat() - 0.5f) * drift,
                phase = rand.nextFloat() * (2f * Math.PI.toFloat()),
                twinkleSpeed = 0.6f + rand.nextFloat() * 0.8f,
            )
        }
    }

    private fun drawParticles(canvas: Canvas, w: Int, h: Int) {
        if (particles.isEmpty()) return
        val minRadius = InkTokens.PARTICLE_RADIUS_MIN_DP.value.dpToPx()
        val maxRadius = InkTokens.PARTICLE_RADIUS_MAX_DP.value.dpToPx()
        particlePaint.color = InkTokens.textPrimary.toArgb()
        val now = nowMillis()
        val motionActive = !InkSpec.isLowSpec() && !degraded
        for (p in particles) {
            val xRatio = if (motionActive) wrap01(p.xRatio + p.vxRatioPerSec * elapsedSec(now)) else p.xRatio
            val yRatio = if (motionActive) wrap01(p.yRatio + p.vyRatioPerSec * elapsedSec(now)) else p.yRatio
            val twinkle = if (motionActive) {
                val cycle = (now % InkTokens.PARTICLE_TWINKLE_PERIOD_MS) /
                    InkTokens.PARTICLE_TWINKLE_PERIOD_MS.toFloat()
                0.55f + 0.45f * sin(2f * Math.PI.toFloat() * cycle * p.twinkleSpeed + p.phase)
            } else 1f
            val radius = minRadius + (maxRadius - minRadius) * p.radiusRatio
            particlePaint.alpha = (p.baseAlpha * twinkle * 255).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(xRatio * w, yRatio * h, radius, particlePaint)
        }
        particlePaint.alpha = 255
    }

    // ── 点字墨爆（独立 100ms） ────────────────────────────────────────

    private fun drawBurst(canvas: Canvas) {
        if (!burstActive) return
        val t = ((nowMillis() - burstStartMs).toFloat() / InkTokens.BURST_DURATION_MS)
            .coerceIn(0f, 1f)
        if (t >= 1f) {
            burstActive = false
            return
        }
        val ease = 1f - (1f - t) * (1f - t)
        val ringRadius = InkTokens.BURST_RING_RADIUS_DP.value.dpToPx() * ease
        burstPaint.color = InkTokens.textPrimary.toArgb()
        burstPaint.alpha = ((1f - t) * 0.18f * 255).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(burstCenterX, burstCenterY, ringRadius, burstPaint)

        val minSide = min(width, height).toFloat()
        val minFleck = InkTokens.BURST_FLECK_MIN_DP.value.dpToPx()
        val maxFleck = InkTokens.BURST_FLECK_MAX_DP.value.dpToPx()
        for (fleck in burstFlecks) {
            val dist = fleck.distanceRatio * minSide * ease
            val fx = burstCenterX + cos(fleck.angle) * dist
            val fy = burstCenterY + sin(fleck.angle) * dist
            val radius = minFleck + (maxFleck - minFleck) * fleck.radiusRatio
            burstPaint.alpha = ((1f - t) * 0.22f * 255).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(fx, fy, radius, burstPaint)
        }
        burstPaint.alpha = 255
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

    // ── §3.3 掀角 spring 翻页 ────────────────────────────────────────

    private fun drawPeel(canvas: Canvas, w: Int, h: Int) {
        val progress = peelProgress.coerceIn(0f, 1f)
        if (progress <= 0f) return
        val corner = w * InkTokens.PEEL_CORNER_FRACTION
        // 折起量：角点沿对角线缩回；背面透印 15% 墨色。
        val peel = corner * progress
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
            .copy(alpha = InkTokens.PEEL_SHADOW_ALPHA * progress)
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
        // 字身方框边长即字号；标题列字号更大（正文 18sp / 标题 20sp），按尺寸阈值识别后上加粗宋体。
        val isTitle = InkSpec.isTitleCellSize(cell.height, bodySizePx, titleSizePx)
        textPaint.typeface = if (isTitle) InkFonts.viewTypefaceBold else InkFonts.viewTypeface
        textPaint.isFakeBoldText = isTitle
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

    // ── 帧循环 / 预算守卫内部 ─────────────────────────────────────────

    private fun ensureFrameLoop() {
        if (frameCallback != null) return
        lastFrameMs = nowMillis()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val now = nowMillis()
                val dtSec = ((now - lastFrameMs).coerceAtLeast(1L)) / 1000f
                lastFrameMs = now

                var keepAlive = false

                // 掀角弹簧推进
                if (turnAnimating) {
                    peelProgress = peelSpring.update(dtSec)
                    if (peelSpring.settled) {
                        turnAnimating = false
                        peelProgress = 0f
                        onTurnAnimEnd?.invoke()
                    } else {
                        keepAlive = true
                    }
                }

                // 墨爆到期由 drawBurst 自行收口；未到期保持帧循环
                if (burstActive) keepAlive = true

                // 氛围粒子运动档：持续帧循环；静止档不挂循环
                if (ambientMotionActive()) keepAlive = true

                invalidate()
                if (keepAlive) {
                    frameCallback = this
                    choreographer.postFrameCallback(this)
                } else {
                    frameCallback = null
                }
            }
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun ambientMotionActive(): Boolean =
        !InkSpec.isLowSpec() && !degraded && particles.any { it.vxRatioPerSec != 0f || it.vyRatioPerSec != 0f }

    private fun noteFrameBudget(drawStartNanos: Long) {
        val elapsedMs = (System.nanoTime() - drawStartNanos) / 1_000_000.0
        overBudgetStreak = InkSpec.recordFrame(
            streak = overBudgetStreak,
            elapsedMs = elapsedMs,
            budgetMs = InkTokens.FRAME_BUDGET_MS,
        )
        val shouldDegrade = InkSpec.shouldDegrade(overBudgetStreak, InkTokens.OVER_BUDGET_STREAK)
        if (shouldDegrade && !degraded) {
            degraded = true
            rebuildParticles()
            invalidate()
        }
    }

    private fun elapsedSec(nowMs: Long): Float = (nowMs - PARTICLE_EPOCH) / 1000f

    private fun nowMillis(): Long = System.currentTimeMillis()

    private fun wrap01(v: Float): Float = ((v % 1f) + 1f) % 1f

    private companion object {
        const val NOISE_SEED = 20261123L
        const val PARTICLE_SEED = 77440101L
        const val BURST_SEED = 51502027L
        const val NOISE_ALPHA_MAX = 255f
        const val PARTICLE_EPOCH = 0L
    }
}
