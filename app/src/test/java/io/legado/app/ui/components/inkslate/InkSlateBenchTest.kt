package io.legado.app.ui.components.inkslate

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import io.legado.app.SlowBenchTest
import org.junit.experimental.categories.Category

/**
 * U7 画板模型层 bench 断言：各阶段 p95 必须落在模型预算内（4ms），远低于单帧 16ms 红线。
 * 画布渲染帧、压感笔触平滑度需真机 / 模拟器实测，本 bench 不冒充渲染结果。
 */
@Category(SlowBenchTest::class)
class InkSlateBenchTest {

    @Test
    fun `画板模型层各阶段 p95 在 4ms 预算内`() {
        val report = InkSlateBenchmark.run()
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
