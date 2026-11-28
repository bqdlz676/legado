package io.legado.app.ui.map.globe.astro.data

import io.legado.app.ui.map.globe.astro.contract.SkyParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * blend → 星等上限曲线单验收（任务 D38 对齐项）。
 *
 * `magLimitFromBlend` 在 blend∈[0,1] 上必须是单条连续线性：blend 0 取实体层 6.5 等，
 * blend 1 取深度上限 11.5 等；等步长采样单调不减、相邻增量近似常数，不许出现档位跳变。
 * 纯 JVM，不碰渲染线程 / 资产。
 */
class SkyBlendCurveTest {

    // 端点：实体层 6.5 等 ↔ 深度上限 11.5 等；等步长单调不减、相邻增量近似常数（单曲线无档位跳变）。
    @Test
    fun `blend 从零到一 星等上限单调不减 端点实体与深度上限无跳变`() {
        val steps = 20
        val samples = (0..steps).map { it / steps.toFloat() }
        val limits = samples.map { SkyParams.magLimitFromBlend(it) }

        // 端点：实体层 ↔ 深度上限。
        assertEquals(SkyParams.MAG_BRIGHT_ENTITY, limits.first(), 1e-6f)
        assertEquals(SkyParams.MAG_DEPTH_LIMIT, limits.last(), 1e-6f)

        val expectedStep = (SkyParams.MAG_DEPTH_LIMIT - SkyParams.MAG_BRIGHT_ENTITY) / steps
        for (i in 1 until limits.size) {
            // 单调不减：相邻步不回退（浮点容差）。
            assertTrue("blend=${samples[i]} 星等上限不得回退", limits[i] >= limits[i - 1] - 1e-7f)
            // 等步长线性：相邻增量近似常数，说明是单曲线而非档位切换。
            val delta = limits[i] - limits[i - 1]
            assertEquals(expectedStep.toDouble(), delta.toDouble(), 1e-5)
        }
    }

    @Test
    fun `越界 blend 钳到端点 非有限输入回落实体层`() {
        // 改坏：若越界未钳制，blend>1 会推出 >11.5 的上限，下一行变红。
        assertEquals(SkyParams.MAG_DEPTH_LIMIT, SkyParams.magLimitFromBlend(2f), 1e-6f)
        assertEquals(SkyParams.MAG_BRIGHT_ENTITY, SkyParams.magLimitFromBlend(-1f), 1e-6f)
        assertEquals(SkyParams.MAG_BRIGHT_ENTITY, SkyParams.magLimitFromBlend(Float.NaN), 1e-6f)
    }
}
