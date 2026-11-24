package io.legado.app.ui.splash.contract

/**
 * S-01 启动动画 · 金点序列纯契约（纯 JVM，不 import 任何 Android / Compose 类型，
 * 普通 JVM 单测可直接断言）。
 *
 * 〔编排口径〕
 * 三点依次亮灭：第 i 个点在第 `i · [SplashParams.DOT_STAGGER_MS]` 毫秒起亮，
 * 起亮瞬间最亮（alpha = 1.0），随后在 [SplashParams.DOT_PERIOD_MS] 周期内线性衰减到 0；
 * 末点衰减完毕即一圈结束，[SplashParams.SEQUENCE_LOOP_MS] 之后从头再来，天然循环。
 *
 * 〔边界兜底〕
 *  - index 不在 0..2（含负数、越界正数）→ 返回 0.0，不抛异常；
 *  - elapsedMs 为负 → 返回 0.0，不抛异常；
 *  - elapsedMs 大于整圈长度 → 对循环长度取模，相位不变（天然循环，不靠外部截断）；
 *  - 返回值恒在 0.0~1.0 闭区间内。
 *
 * 〔本单边界〕
 * 本函数只算单点透明度；点的尺寸 / 间距 / 颜色 / 字号 / 浓度 / 半径归 [SplashParams]；
 * Compose 帧时钟驱动与既有 `ui/welcome` 编排引擎不在本单范围。
 */
fun goldDotAlpha(index: Int, elapsedMs: Long): Float {
    // 越界下标：三点之外没有对应金点，静默回落 0，不抛异常（空数据 / 错帧兜底）。
    if (index !in 0 until SplashParams.DOT_COUNT) return 0f
    // 负时间：外部时钟未起或回退帧，静默回落 0，不抛异常。
    if (elapsedMs < 0L) return 0f

    val loop = SplashParams.SEQUENCE_LOOP_MS
    // 天然循环：超过整圈长度按相位取模，长时长与同一相位得到同一透明度。
    val t = elapsedMs % loop
    val start = index * SplashParams.DOT_STAGGER_MS.toLong()
    val local = t - start

    // 尚未轮到本点，或本点已在自己的周期内衰减到 0：当前帧不亮。
    if (local < 0L || local >= SplashParams.DOT_PERIOD_MS.toLong()) return 0f

    val progress = local.toFloat() / SplashParams.DOT_PERIOD_MS.toFloat()
    return (1f - progress).coerceIn(0f, 1f)
}
