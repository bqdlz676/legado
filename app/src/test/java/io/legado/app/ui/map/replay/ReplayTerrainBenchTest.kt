package io.legado.app.ui.map.replay

import io.legado.app.ui.map.sandbox.DemTerrainElevation
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U11 回放贴地采样 bench（纯 JVM）。
 *
 * 预算依据：60fps 单帧总预算 16.7ms；回放导演模型层单帧 update 预算 2ms（见
 * [ReplayBenchTest]），贴地采样在该模型预算内再切 **0.5ms/帧** 上限，留给绘制 / 动画
 * 余量。单点采样阈值 3µs/op，与沙盘 [DemTerrainBenchTest] 同口径，只防插值退化到
 * 毫秒级；真机上线前再压一轮。
 *
 * 单帧采样规模：种子 8 枚棋子基座 + 6 条箭头 ×2 节点 + 交战 / 遭遇落点，合计约 20
 * 次 heightAt；超编兵力时按模型层棋子 / 箭头配额等比例放大，0.5ms 仍有百倍余量。
 */
class ReplayTerrainBenchTest {

    private fun buildGrid(n: Int): DemTerrainElevation {
        val data = IntArray(n * n) { i -> i % 256 }
        return DemTerrainElevation.create(n, n, data, DemTerrainElevation.DEFAULT_BBOX)
    }

    /** 跑一帧的全部贴地采样：棋子基座 + 箭身节点 + 交战 / 遭遇落点；返回高度和防优化掉。 */
    private fun sampleFrame(director: ReplayDirector): Double {
        var sink = 0.0
        director.visuals().forEach { sink += it.groundHeight }
        director.arrows().forEach { arrow -> arrow.ground.forEach { sink += it.height } }
        director.clashes.forEach { sink += it.groundHeight }
        director.encounters.forEach { sink += it.groundHeight }
        return sink
    }

    @Test
    fun `bench terrain single sample under 3us`() {
        val dem = buildGrid(1024)
        val bbox = DemTerrainElevation.DEFAULT_BBOX

        repeat(200) { dem.heightAt(33.0, 113.0) }

        val loops = 100_000
        val start = System.nanoTime()
        var sink = 0.0
        repeat(loops) { i ->
            val lat = bbox.south + (i % 1000) / 1000.0 * (bbox.north - bbox.south)
            val lng = bbox.west + ((i * 7) % 1000) / 1000.0 * (bbox.east - bbox.west)
            sink += dem.heightAt(lat, lng)
        }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val avgMs = totalMs / loops
        assertTrue("采样结果被优化: $sink", sink > 0.0)
        println("[BENCH] replayTerrainSample loops=$loops total=${"%.2f".format(totalMs)}ms avg=${"%.4f".format(avgMs)}ms/op")
        assertTrue("单样本采样超 3µs/op: $avgMs", avgMs < 0.003)
    }

    @Test
    fun `bench per-frame terrain pass under 0_5ms`() {
        val dem = buildGrid(1024)
        val director = ReplayDirector(SeedReplay.changping, elevation = dem)

        repeat(200) { sampleFrame(director) }

        val loops = 5_000
        val start = System.nanoTime()
        var sink = 0.0
        repeat(loops) { sink += sampleFrame(director) }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val avgMs = totalMs / loops
        assertTrue("帧采样结果被优化: $sink", sink != 0.0)
        println("[BENCH] replayTerrainFrame loops=$loops total=${"%.2f".format(totalMs)}ms avg=${"%.4f".format(avgMs)}ms/frame")
        assertTrue("单帧贴地采样超 0.5ms: $avgMs", avgMs < 0.5)
    }
}
