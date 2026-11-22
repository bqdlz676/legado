package io.legado.app.help.chrono

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 纪年/推演内核 bench：单次换算与候选推演红线 ≤8ms（任务规格）。
 * 数字打到测试日志；阈值取宽松 8ms，真机上还会再压一轮。
 */
class ChronoBenchTest {

    @Before
    fun setUp() {
        ChronoPlaces.resetForTest()
        ContextAnchor.personProvider = null
        ContextAnchor.allusionProvider = null
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
    fun `bench chrono kernel under 8ms per op`() {
        val anchorAvg = bench("anchorFromText") {
            Chronology.anchorFromText("鲁隐公元年，郑庄公二十二年")
        }
        val candidatesAvg = bench("anchorCandidates") {
            Chronology.anchorCandidates("鲁隐公元年，郑庄公二十二年")
        }
        val reportAvg = bench("timelineReport") {
            Chronology.timelineReport(-722)
        }
        val ganzhiAvg = bench("yearGanzhi") {
            Chronology.yearGanzhi(-722)
        }
        val princeAvg = bench("princeYear") {
            Chronology.princeYear("郑庄公", 22)
        }
        val resolveAvg = bench("ContextAnchor.resolve") {
            ContextAnchor.resolve("晋", -700)
        }

        println("[BENCH] summary: anchor=$anchorAvg candidates=$candidatesAvg report=$reportAvg " +
            "ganzhi=$ganzhiAvg prince=$princeAvg resolve=$resolveAvg ms/op")

        // 红线：纪年换算 / 候选推演单次 ≤8ms
        assertTrue("anchorFromText 超 8ms: $anchorAvg", anchorAvg < 8.0)
        assertTrue("anchorCandidates 超 8ms: $candidatesAvg", candidatesAvg < 8.0)
        assertTrue("timelineReport 超 8ms: $reportAvg", reportAvg < 8.0)
        assertTrue("yearGanzhi 超 8ms: $ganzhiAvg", ganzhiAvg < 8.0)
        assertTrue("princeYear 超 8ms: $princeAvg", princeAvg < 8.0)
        assertTrue("resolve 超 8ms: $resolveAvg", resolveAvg < 8.0)
    }
}
