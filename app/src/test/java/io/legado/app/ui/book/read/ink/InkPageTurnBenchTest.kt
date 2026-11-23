package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 翻页通道 bench（纯 JVM，离线可跑；沙箱内不执行，由云端 CI 跑 testDebugUnitTest 取真实耗时）。
 *
 * 红线（切片-08 / 契约 §1.5）：翻页 ≤16ms/帧。本 bench 度量「键码 → 命令解析 → 门禁裁决」
 * 这段纯决策路径的耗时；掀角 spring 积分 / 粒子明灭的帧预算另见 [InkPerfBenchmarkTest]。
 * 断言写死指标，不在源码里放宽阈值；CI 未跑过时交付说明标注「bench 未验证」。
 */
class InkPageTurnBenchTest {

    private companion object {
        /** 单帧翻页决策预算：16ms（[InkTokens.FRAME_BUDGET_MS]），单位 ns。 */
        val FRAME_BUDGET_NS = (InkTokens.FRAME_BUDGET_MS * 1_000_000).toLong()
    }

    private val hardwareKeys = listOf(
        InkPageKeys.KEYCODE_VOLUME_UP,
        InkPageKeys.KEYCODE_VOLUME_DOWN,
        InkPageKeys.KEYCODE_DPAD_UP,
        InkPageKeys.KEYCODE_DPAD_LEFT,
        InkPageKeys.KEYCODE_DPAD_DOWN,
        InkPageKeys.KEYCODE_DPAD_RIGHT,
        InkPageKeys.KEYCODE_PAGE_UP,
        InkPageKeys.KEYCODE_PAGE_DOWN,
        InkPageKeys.KEYCODE_SPACE,
    )

    private val mediaKeys = listOf(
        InkPageKeys.KEYCODE_MEDIA_PREVIOUS,
        InkPageKeys.KEYCODE_MEDIA_NEXT,
        InkPageKeys.KEYCODE_MEDIA_PLAY_PAUSE,
    )

    @Test
    fun `单次翻页决策（解析加门禁）在 16ms 帧预算内`() {
        val config = PageKeyConfig.DEFAULT
        val gate = PageTurnGate(animating = false, canPrev = true, canNext = true)

        // 预热：JIT 与类装载稳定，避免首轮冷启动污染极值。
        repeat(200) {
            hardwareKeys.forEach { InkPageKeys.resolveHardwareKey(it, 0, config) }
            mediaKeys.forEach { InkPageKeys.resolveMediaKey(it) }
        }

        var maxNanos = 0L
        repeat(10) {
            hardwareKeys.forEach { key ->
                val t0 = System.nanoTime()
                val command = InkPageKeys.resolveHardwareKey(key, 0, config)
                (command as? PageCommand.Turn)?.let { gate.evaluate(it.direction) }
                maxNanos = maxOf(maxNanos, System.nanoTime() - t0)
            }
            mediaKeys.forEach { key ->
                val t0 = System.nanoTime()
                val command = InkPageKeys.resolveMediaKey(key)
                (command as? PageCommand.Turn)?.let { gate.evaluate(it.direction) }
                maxNanos = maxOf(maxNanos, System.nanoTime() - t0)
            }
        }

        assertTrue(
            "单次翻页决策超 16ms 预算：${maxNanos}ns",
            maxNanos <= FRAME_BUDGET_NS,
        )
    }

    @Test
    fun `批量翻页决策平均耗时不超单帧预算`() {
        val config = PageKeyConfig.DEFAULT
        val gate = PageTurnGate(animating = false, canPrev = true, canNext = true)

        // 预热。
        repeat(100) {
            hardwareKeys.forEach { InkPageKeys.resolveHardwareKey(it, 0, config) }
        }

        val iterations = 1000
        val start = System.nanoTime()
        repeat(iterations) { i ->
            val key = hardwareKeys[i % hardwareKeys.size]
            val command = InkPageKeys.resolveHardwareKey(key, 0, config)
            (command as? PageCommand.Turn)?.let { gate.evaluate(it.direction) }
        }
        val avgNanos = (System.nanoTime() - start) / iterations

        assertTrue(
            "翻页决策平均 ${avgNanos}ns 超出 16ms 帧预算",
            avgNanos <= FRAME_BUDGET_NS,
        )
    }
}
