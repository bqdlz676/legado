package io.legado.app.ui.welcome

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 落文 · 启动动画编排引擎（纯 JVM，无 Android / Compose 依赖，可直接跑单测与 bench）。
 *
 * 时间线严格按《落文 · 启动动画》：
 *  0–400    墨滴坠落（重力加速 easeInQuad，边缘撕裂的饱蘸一滴）
 *  400–700  炸裂 48 粒墨屑（圆粒 / 飞白丝 / 大块溅墨），飞散半径 ≤60dp
 *  700–1000 悬停呼吸；悬停尾部加速回聚，碰撞旋转融合，凝成竖排「落文」
 *  880–…    字面由水渍渗出 0.4s 凝成浓墨；笔顺先「落」草字头、后「文」末捺
 * 1000–1200 金粉爆 76 粒暖金，飘 0.5s 亮度衰减到 0
 * 1200–1600 水墨远山勾线淡入，山腰白雾缓移，最高不透明度 0.25
 * 1400–1800 暖金线顿挫扫过，字面发光 + 40dp 金辉，停在「文」末捺收成金点
 * 1800–2200 落款「读其文，知其人，通其时。」淡入 400ms
 * 2200–2800 全幅淡出 600ms；山先灭，「文」末捺最后消失
 *
 * 性能口径：
 *  - [update] 帧内零分配；随机数在 init 一次性算好，单测固定种子可复现；
 *  - 粒子总数 = 墨 [LuowenSplash.INK_COUNT] + 金 [LuowenSplash.GOLD_COUNT]，≤140；
 *  - 帧率掉线时优先降更新频率 / 复用数组，不靠砍粒子数量兜底。
 */
class SplashChoreography(
    private val seed: Long = DEFAULT_SEED,
) {

    /** 墨屑形态：0 圆粒 / 1 飞白细丝 / 2 大块溅墨 */
    enum class InkKind { ROUND, FILAMENT, SPLAT }

    /** 编排主导相位（测试与降级分支判定用；相位允许重叠，取当前主导段） */
    enum class Phase { DROP, BURST, HOLD, GATHER, GOLD_BURST, HILL, SWEEP, SLOGAN, FADE, DONE }

    class InkParticle {
        var x = 0f
        var y = 0f
        var size = 0f
        var rot = 0f
        var spin = 0f
        var alpha = 0f
        var kind: InkKind = InkKind.ROUND
        /** 回聚目标锚点（归一化 0..1，相对屏宽 / 屏高） */
        var targetX = 0.5f
        var targetY = 0.5f
        var angle = 0f
        var burstRadius = 0f
        var seedPhase = 0f
    }

    class GoldParticle {
        var x = 0f
        var y = 0f
        var size = 0f
        var alpha = 0f
        var drift = 0f
        var seedPhase = 0f
    }

    val ink: List<InkParticle>
    val gold: List<GoldParticle>

    /** 山脊折线（归一化坐标：x 0..1，y 0 顶 1 底），雾带横向偏移在此之上叠加 */
    val ridge: List<Pair<Float, Float>>

    // —— 场景导出值（Compose 绘制层只读）——
    var dropX = 0f
        private set
    var dropY = -1f
        private set
    var dropSize = 0f
        private set
    var dropAlpha = 0f
        private set
    var glyphLuoAlpha = 0f
        private set
    var glyphWenAlpha = 0f
        private set
    /** 笔顺生长 0..1：「落」先出 */
    var luoStroke = 0f
        private set
    /** 笔顺生长 0..1：「文」末捺最后收 */
    var wenStroke = 0f
        private set
    var hillAlpha = 0f
        private set
    var mistOffset = 0f
        private set
    var sweepProgress = 0f
        private set
    var sweepGlow = 0f
        private set
    var goldDotAlpha = 0f
        private set
    var sloganAlpha = 0f
        private set
    var fadeAlpha = 1f
        private set
    var elapsedMs = 0L
        private set

    init {
        val rnd = Random(seed)
        ink = buildList(LuowenSplash.INK_COUNT) {
            repeat(LuowenSplash.INK_ROUND) { add(newInk(InkKind.ROUND, rnd)) }
            repeat(LuowenSplash.INK_FILAMENT) { add(newInk(InkKind.FILAMENT, rnd)) }
            repeat(LuowenSplash.INK_SPLAT) { add(newInk(InkKind.SPLAT, rnd)) }
        }
        gold = buildList(LuowenSplash.GOLD_COUNT) {
            val p = GoldParticle()
            p.size = 1.4f + rnd.nextFloat() * 2.2f
            p.drift = 6f + rnd.nextFloat() * 14f
            p.seedPhase = rnd.nextFloat() * 6.2831f
            add(p)
        }
        // 远山：三段起伏的山脊折线，固定伪随机种子，单测可复现
        ridge = buildList(15) { i ->
            val x = i / 14f
            val peak = when (i) {
                3 -> 0.10f; 6 -> 0.22f; 9 -> 0.13f; 12 -> 0.20f
                else -> 0.16f + rnd.nextFloat() * 0.05f
            }
            x to (0.78f - peak)
        }
    }

    private fun newInk(kind: InkKind, rnd: Random): InkParticle {
        val p = InkParticle()
        p.kind = kind
        p.angle = rnd.nextFloat() * 6.2831f
        p.burstRadius = 0.55f + rnd.nextFloat() * 0.45f
        p.size = when (kind) {
            InkKind.ROUND -> 2.5f + rnd.nextFloat() * 3.5f
            InkKind.FILAMENT -> 0.8f + rnd.nextFloat() * 1.2f
            InkKind.SPLAT -> 5f + rnd.nextFloat() * 7f
        }
        p.spin = (rnd.nextFloat() - 0.5f) * 6f
        p.seedPhase = rnd.nextFloat() * 6.2831f
        // 回聚锚点：落（上）/ 文（下）两簇，竖排；字间距 = 字高 0.3 倍
        val upper = rnd.nextBoolean()
        p.targetX = 0.5f + (rnd.nextFloat() - 0.5f) * 0.16f
        p.targetY = if (upper) 0.42f else 0.58f
        return p
    }

    /**
     * 推进一帧。
     * @param tMs 自启动起的毫秒数（外部时钟驱动，引擎不自持时间）
     * @param widthPx / heightPx 画布尺寸（px）
     * @param density 屏幕密度（dp → px）
     */
    fun update(tMs: Long, widthPx: Float, heightPx: Float, density: Float) {
        elapsedMs = tMs.coerceIn(0L, LuowenSplash.TOTAL_MS)
        val w = widthPx.coerceAtLeast(1f)
        val h = heightPx.coerceAtLeast(1f)
        val cx = w * 0.5f
        val cy = h * 0.48f
        val radiusPx = LuowenSplash.BURST_RADIUS_DP * density

        // —— 墨滴坠落 0–400ms，重力加速 easeInQuad ——
        if (elapsedMs <= LuowenSplash.DROP_END_MS) {
            val p = elapsedMs.toFloat() / LuowenSplash.DROP_END_MS
            val ease = p * p
            dropX = cx
            dropSize = 10f * density * (0.8f + 0.4f * p)
            dropY = -dropSize + (cy + dropSize) * ease
            dropAlpha = 1f
        } else {
            dropAlpha = 0f
            dropY = cy
        }

        // —— 墨屑：炸裂 / 悬停 / 回聚 ——
        val burstP = progress(elapsedMs, LuowenSplash.DROP_END_MS, LuowenSplash.BURST_END_MS)
        val gatherP = progress(elapsedMs, LuowenSplash.SUCK_START_MS, LuowenSplash.GATHER_END_MS)

        for (p in ink) {
            when {
                elapsedMs < LuowenSplash.DROP_END_MS -> p.alpha = 0f
                elapsedMs < LuowenSplash.BURST_END_MS -> {
                    // 炸裂：径向飞出，半径随 easeOutCubic 到上限
                    val r = radiusPx * p.burstRadius * easeOutCubic(burstP)
                    p.x = cx + cos(p.angle) * r
                    p.y = cy + sin(p.angle) * r * 0.82f
                    p.rot += p.spin * 0.05f
                    p.alpha = 1f
                }
                elapsedMs < LuowenSplash.SUCK_START_MS -> {
                    // 悬停：极小幅呼吸漂移，不落不飞
                    val drift = sin(elapsedMs / 90f + p.seedPhase) * 1.5f * density
                    p.x += cos(p.angle) * drift * 0.02f
                    p.y += sin(p.angle) * drift * 0.02f
                    p.alpha = 0.85f
                }
                else -> {
                    // 加速回聚：easeInQuad 吸入，途中旋转融合
                    val g = easeInQuad(gatherP)
                    val tx = p.targetX * w
                    val ty = p.targetY * h
                    p.x = lerp(p.x, tx, g)
                    p.y = lerp(p.y, ty, g)
                    p.rot += p.spin * (0.05f + g * 0.12f)
                    p.alpha = 1f
                    if (gatherP >= 1f) p.alpha = 0f
                }
            }
        }

        // —— 字面渗出：落先、文后；0.4s 由水渍凝成浓墨 ——
        val seepStart = LuowenSplash.GATHER_END_MS - 120f
        val seepP = progress(elapsedMs, seepStart.toLong(), seepStart.toLong() + 400L)
        glyphLuoAlpha = easeOutCubic(seepP)
        val wenSeepStart = seepStart + 120f
        val wenP = progress(elapsedMs, wenSeepStart.toLong(), wenSeepStart.toLong() + 400L)
        glyphWenAlpha = easeOutCubic(wenP)

        // 笔顺生长：落的草字头先出，文的末捺最后收
        luoStroke = progress(elapsedMs, seepStart.toLong(), (seepStart + 240f).toLong())
        wenStroke = progress(elapsedMs, wenSeepStart.toLong(), (wenSeepStart + 240f).toLong())

        // —— 金粉爆 1000–1200，飘至 1700 衰减到 0 ——
        val goldIn = progress(elapsedMs, LuowenSplash.GOLD_BURST_END_MS - 200L, LuowenSplash.GOLD_BURST_END_MS)
        val goldLife = progress(elapsedMs, LuowenSplash.GOLD_BURST_END_MS, LuowenSplash.GOLD_LIFE_MS)
        for (g in gold) {
            if (elapsedMs < LuowenSplash.GOLD_BURST_END_MS - 200L) {
                g.alpha = 0f
                continue
            }
            val a = g.seedPhase
            val speed = (0.35f + 0.65f * sin(a * 3.1f)) * radiusPx * 1.15f
            val out = easeOutCubic(goldIn)
            g.x = cx + cos(a) * speed * out
            g.y = cy + sin(a) * speed * out * 0.7f
            // 上飘 + 横向漂移，亮度随生命衰减
            g.y -= g.drift * density * goldLife
            g.x += sin(g.seedPhase + goldLife * 4f) * 8f * density * goldLife
            g.alpha = (1f - goldLife) * 0.95f
        }

        // —— 远山 1200–1600 淡入，雾横向缓移 ——
        hillAlpha = LuowenSplash.HILL_MAX_ALPHA * progress(elapsedMs, 1200L, LuowenSplash.HILL_END_MS)
        mistOffset = ((elapsedMs % 2600L) / 2600f) * w * 0.12f

        // —— 暖金线 1400–1800 顿挫扫过 ——
        val sweepRaw = progress(elapsedMs, LuowenSplash.SWEEP_START_MS, LuowenSplash.SWEEP_END_MS)
        sweepProgress = stutter(sweepRaw)
        sweepGlow = if (sweepRaw in 0f..1f) 0.6f + 0.4f * sin(sweepRaw * 6.2831f) else 0f
        goldDotAlpha = progress(elapsedMs, LuowenSplash.SWEEP_END_MS, LuowenSplash.SWEEP_END_MS + 160L)

        // —— 落款 1800 起淡入 400ms ——
        sloganAlpha = progress(elapsedMs, LuowenSplash.SLOGAN_IN_MS, LuowenSplash.SLOGAN_END_MS)

        // —— 淡出 2200–2800：山先灭，字最后 ——
        fadeAlpha = 1f - progress(elapsedMs, LuowenSplash.FADE_IN_MS, LuowenSplash.FADE_END_MS)
        // 「落」先收，「文」末捺延后淡出，最后消失
        glyphLuoAlpha *= luoFadeHold()
        glyphWenAlpha *= wenFadeHold()
    }

    /** 远山在淡出段优先消失（2200–2350），供绘制层叠加裁剪 */
    fun hillFadeAlpha(): Float =
        hillAlpha * (1f - progress(elapsedMs, LuowenSplash.FADE_IN_MS, LuowenSplash.HILL_FADE_END_MS))

    /** 「落」字淡出：2200 先收，2600 灭 */
    private fun luoFadeHold(): Float =
        1f - progress(elapsedMs, LuowenSplash.FADE_IN_MS, 2600L)

    /** 「文」字在淡出段保留更久：2400 后才开始收，末捺最后灭 */
    private fun wenFadeHold(): Float =
        1f - progress(elapsedMs, 2400L, LuowenSplash.FADE_END_MS)

    /** 当前主导相位（测试用） */
    fun phaseAt(tMs: Long): Phase {
        val t = tMs.coerceIn(0L, LuowenSplash.TOTAL_MS)
        return when {
            t >= LuowenSplash.TOTAL_MS -> Phase.DONE
            t >= LuowenSplash.FADE_IN_MS -> Phase.FADE
            t >= LuowenSplash.SLOGAN_IN_MS -> Phase.SLOGAN
            t >= LuowenSplash.SWEEP_START_MS -> Phase.SWEEP
            t >= 1200L -> Phase.HILL
            t >= LuowenSplash.GOLD_BURST_END_MS - 200L -> Phase.GOLD_BURST
            t >= LuowenSplash.SUCK_START_MS -> Phase.GATHER
            t >= LuowenSplash.BURST_END_MS -> Phase.HOLD
            t >= LuowenSplash.DROP_END_MS -> Phase.BURST
            else -> Phase.DROP
        }
    }

    private fun progress(t: Long, from: Long, to: Long): Float {
        if (t <= from) return 0f
        if (t >= to) return 1f
        return (t - from).toFloat() / (to - from)
    }

    private fun easeInQuad(p: Float): Float = p * p
    private fun easeOutCubic(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * 毛笔走纸般的顿挫：整体前进，途中插入两处短促停顿，不是匀速直线。
     */
    private fun stutter(p: Float): Float {
        if (p <= 0f) return 0f
        if (p >= 1f) return 1f
        val segments = floatArrayOf(0f, 0.34f, 0.38f, 0.66f, 0.70f, 1f)
        val out = floatArrayOf(0f, 0.30f, 0.30f, 0.62f, 0.62f, 1f)
        for (i in 0 until segments.size - 1) {
            if (p <= segments[i + 1]) {
                val local = (p - segments[i]) / (segments[i + 1] - segments[i])
                return lerp(out[i], out[i + 1], local)
            }
        }
        return 1f
    }

    companion object {
        const val DEFAULT_SEED: Long = 20261122L
    }
}
