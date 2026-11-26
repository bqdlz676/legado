package io.legado.app.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import io.legado.app.SlowBenchTest
import org.junit.experimental.categories.Category

/**
 * U6 Sheet 模型层 bench 断言：各阶段 p95 必须落在模型预算内（4ms），远低于单帧 16ms 红线。
 * 渲染层动画帧耗时需在真机 / 模拟器上另测，本 bench 不冒充渲染结果。
 */
@Category(SlowBenchTest::class)
class HalfSheetBenchTest {

    @Test
    fun `Sheet 模型层各阶段 p95 在 4ms 预算内`() {
        val report = HalfSheetBenchmark.run()
        println(report.summary())
        assertTrue("存在超出预算的阶段：\n${report.summary()}", report.withinBudget)
        report.phases.forEach {
            assertTrue(
                "${it.name} p95=${it.p95Ms}ms 超过单帧预算 ${ComponentPalette.FRAME_BUDGET_MS}ms",
                it.p95Ms <= ComponentPalette.FRAME_BUDGET_MS,
            )
        }
    }
}
