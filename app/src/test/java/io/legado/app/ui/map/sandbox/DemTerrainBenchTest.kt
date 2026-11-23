package io.legado.app.ui.map.sandbox

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEM 采样 bench（缺陷 D-APK-1）：1024×1024 栅格采样 10 万次。
 *
 * 阈值依据：60fps 单帧预算 16.7ms；每帧贴地采样的势力顶点 / 河道样条 / 箭头落点
 * 合计不过数百点，单点预算宽松到 3µs 仍有百倍余量。3µs/op 是宽松上限，真机上线前
 * 再压一轮；这里只防插值算法退化到毫秒级。
 */
class DemTerrainBenchTest {

    private fun buildGrid(n: Int): DemTerrainElevation {
        val data = IntArray(n * n) { i -> i % 256 }
        return DemTerrainElevation.create(n, n, data, DemTerrainElevation.DEFAULT_BBOX)
    }

    @Test
    fun `bench dem sample 100k under 3us per op`() {
        val n = 1024
        val dem = buildGrid(n)
        val bbox = DemTerrainElevation.DEFAULT_BBOX

        // 预热：触发 JIT / 数组页加载。
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
        // 避免采样被优化掉。
        assertTrue("采样结果被优化: $sink", sink > 0.0)
        println("[BENCH] demSample loops=$loops total=${"%.2f".format(totalMs)}ms avg=${"%.4f".format(avgMs)}ms/op")
        assertTrue("DEM 采样超 3µs/op: $avgMs", avgMs < 0.003)
    }
}
