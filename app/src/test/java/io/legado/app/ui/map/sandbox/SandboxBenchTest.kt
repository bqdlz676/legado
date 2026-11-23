package io.legado.app.ui.map.sandbox

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 沙盘模型层 bench（切片-06 性能红线 + 契约 §1-5）。
 *
 * 预算口径沿用组件库 MODEL_BUDGET_MS：场景 / 配额核算单帧内留出绘制余量；
 * 判点与样条采样是每帧高频调用，单独压阈值。数字打到测试日志，阈值为宽松上限，
 * 真机上线前还会再压一轮。
 */
class SandboxBenchTest {

    private lateinit var scene: SandboxScene

    @Before
    fun setUp() {
        scene = SandboxScene(
            powers = PowerLayer(SeedSandbox.powers),
            cities = SeedSandbox.cities,
            rivers = SeedSandbox.rivers,
            sites = SeedSandbox.sites,
            chokes = SeedSandbox.chokes,
            trails = emptyList(),
        )
        scene.year = -700
    }

    private inline fun bench(
        name: String,
        warmup: Int = 200,
        loops: Int = 5_000,
        op: () -> Unit,
    ): Double {
        repeat(warmup) { op() }
        val start = System.nanoTime()
        repeat(loops) { op() }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val avgMs = totalMs / loops
        println("[BENCH] $name loops=$loops total=${"%.2f".format(totalMs)}ms avg=${"%.4f".format(avgMs)}ms/op")
        return avgMs
    }

    @Test
    fun `bench scene build under 4ms`() {
        val buildAvg = bench("sceneBuild") {
            SandboxScene(
                powers = PowerLayer(SeedSandbox.powers),
                cities = SeedSandbox.cities,
                rivers = SeedSandbox.rivers,
                sites = SeedSandbox.sites,
                chokes = SeedSandbox.chokes,
                trails = emptyList(),
            )
        }
        assertTrue("场景构建超 4ms: $buildAvg", buildAvg < 4.0)
    }

    @Test
    fun `bench power hit test under 0_5ms`() {
        val jin = SeedSandbox.powers.first { it.id == "jin" }
        val hitAvg = bench("powerContains") {
            jin.contains(35.60, 112.00)
        }
        assertTrue("势力判点超 0.5ms: $hitAvg", hitAvg < 0.5)
    }

    @Test
    fun `bench river sampling under 1ms`() {
        val river = SeedSandbox.rivers.first { it.id == "changjiang" }
        val sampleAvg = bench("riverSample") {
            river.sampledPath()
        }
        assertTrue("河道采样超 1ms: $sampleAvg", sampleAvg < 1.0)
    }

    @Test
    fun `bench city render and atlas under 1ms`() {
        val renderAvg = bench("cityRender") {
            scene.cityRender()
            scene.labelAtlas()
        }
        assertTrue("城池 / atlas 核算超 1ms: $renderAvg", renderAvg < 1.0)
    }

    @Test
    fun `bench quota accounting under 1ms`() {
        val quotaAvg = bench("quotaAccounting") {
            scene.visiblePowers()
            scene.visibleRivers()
            scene.estimateDrawCalls()
        }
        assertTrue("配额核算超 1ms: $quotaAvg", quotaAvg < 1.0)
    }
}
