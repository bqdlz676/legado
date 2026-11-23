package io.legado.app.ui.map.replay

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 战役回放模型层 bench（切片-07 性能红线 + 契约 §1-5）。
 *
 * 导演主循环每帧都要跑事件派发 / 交战检测 / 视觉派生，单帧预算压在 2ms 内留出
 * 绘制余量；战役构建与箭头采样按模型层 4ms / 1ms 口径。数字打到测试日志，真机再压。
 */
class ReplayBenchTest {

    private lateinit var director: ReplayDirector
    private var now: Long = 0L

    @Before
    fun setUp() {
        now = 0L
        director = ReplayDirector(SeedReplay.changping, clock = { now })
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
    fun `bench director build under 4ms`() {
        val buildAvg = bench("directorBuild") {
            ReplayDirector(SeedReplay.changping, clock = { now })
        }
        assertTrue("导演构建超 4ms: $buildAvg", buildAvg < 4.0)
    }

    @Test
    fun `bench director update under 2ms per frame`() {
        val updateAvg = bench("directorUpdate") {
            director.update(16)
        }
        assertTrue("导演单帧 update 超 2ms: $updateAvg", updateAvg < 2.0)
    }

    @Test
    fun `bench visuals derive under 2ms`() {
        val visualAvg = bench("visuals") {
            director.visuals()
        }
        assertTrue("棋子视觉派生超 2ms: $visualAvg", visualAvg < 2.0)
    }

    @Test
    fun `bench arrows under 1ms`() {
        val arrowAvg = bench("arrows") {
            director.arrows()
        }
        assertTrue("箭头派生超 1ms: $arrowAvg", arrowAvg < 1.0)
    }

    @Test
    fun `bench clash sparks under 0_5ms`() {
        val clash = ClashEffect("bench-clash", LatLng(35.0, 112.0), 0)
        val sparkAvg = bench("clashSparks") {
            clash.sparks()
        }
        assertTrue("火花生成超 0.5ms: $sparkAvg", sparkAvg < 0.5)
    }
}
