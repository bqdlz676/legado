package io.legado.app.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型层 bench 断言：各阶段 p95 必须落在模型预算内（4ms），远低于单帧 16ms 红线。
 * 注意：本 bench 只覆盖纯模型 / 状态解析耗时；Compose 渲染帧耗时需真机或模拟器实测，
 * 未在本测试中验证。
 */
class ComponentBenchTest {

    @Test
    fun `模型层各阶段 p95 在 4ms 预算内`() {
        val report = ComponentBenchmark.run()
        println(report.summary())
        assertTrue(
            "存在超出预算的阶段：\n${report.summary()}",
            report.withinBudget,
        )
        report.phases.forEach {
            assertTrue(
                "${it.name} p95=${it.p95Ms}ms 超过单帧预算 ${ComponentPalette.FRAME_BUDGET_MS}ms",
                it.p95Ms <= ComponentPalette.FRAME_BUDGET_MS,
            )
        }
    }
}
