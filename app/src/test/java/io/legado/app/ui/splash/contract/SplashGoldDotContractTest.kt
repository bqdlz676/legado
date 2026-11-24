package io.legado.app.ui.splash.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-01 启动动画 · 金点序列纯函数验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖本单断言：
 *  ① 第 0 点起始最亮；② 第 1 点在一个 step 后最亮；③ 第 2 点在两个 step 后最亮；
 *  ④ 越界 index 返回 0；⑤ 负 elapsed 返回 0；
 *  ⑥ 循环性：elapsed = 整圈 + 同一相位得到相同 alpha；
 *  ⑦ 返回值恒在 0.0~1.0；⑧ 单点周期结束后衰减到 0。
 */
class SplashGoldDotContractTest {

    private val step = SplashParams.DOT_STAGGER_MS.toLong()
    private val period = SplashParams.DOT_PERIOD_MS.toLong()
    private val loop = SplashParams.SEQUENCE_LOOP_MS

    @Test
    fun `first dot is brightest at sequence start`() {
        assertEquals(1f, goldDotAlpha(0, 0L), 1e-4f)
    }

    @Test
    fun `second dot brightens after stagger step`() {
        assertEquals(1f, goldDotAlpha(1, step), 1e-4f)
    }

    @Test
    fun `third dot brightens after two stagger steps`() {
        assertEquals(1f, goldDotAlpha(2, 2 * step), 1e-4f)
    }

    @Test
    fun `out of range index returns zero`() {
        assertEquals(0f, goldDotAlpha(-1, 0L), 1e-4f)
        assertEquals(0f, goldDotAlpha(3, 0L), 1e-4f)
        assertEquals(0f, goldDotAlpha(100, step), 1e-4f)
    }

    @Test
    fun `negative elapsed returns zero`() {
        assertEquals(0f, goldDotAlpha(0, -1L), 1e-4f)
        assertEquals(0f, goldDotAlpha(1, -1000L), 1e-4f)
        assertEquals(0f, goldDotAlpha(2, -period), 1e-4f)
    }

    @Test
    fun `sequence wraps with same alpha after full loop`() {
        val phaseFirst = step / 2
        val expectedFirst = goldDotAlpha(0, phaseFirst)
        val actualFirst = goldDotAlpha(0, loop + phaseFirst)
        assertEquals(expectedFirst, actualFirst, 1e-4f)

        val phaseThird = 2 * step + period / 2
        val expectedThird = goldDotAlpha(2, phaseThird)
        val actualThird = goldDotAlpha(2, 2 * loop + phaseThird)
        assertEquals(expectedThird, actualThird, 1e-4f)
    }

    @Test
    fun `alpha stays within zero to one range`() {
        for (index in 0 until SplashParams.DOT_COUNT) {
            for (elapsed in 0L..loop + period) {
                val alpha = goldDotAlpha(index, elapsed)
                assertTrue("alpha 不得小于 0", alpha >= 0f)
                assertTrue("alpha 不得大于 1", alpha <= 1f)
            }
        }
    }

    @Test
    fun `dot extinguishes after its own period`() {
        assertEquals(0f, goldDotAlpha(0, period), 1e-4f)
        assertEquals(0f, goldDotAlpha(1, step + period), 1e-4f)
        assertEquals(0f, goldDotAlpha(2, 2 * step + period), 1e-4f)

        val mid = goldDotAlpha(0, period / 2)
        assertTrue("周期中段应处于亮灭之间", mid > 0f && mid < 1f)
    }
}
