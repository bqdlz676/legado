package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 竖排字位缓存 bench：稀疏映射查表时延预算。
 *
 * 说明：沙箱内不执行，由云端 CI 跑 `testDebugUnitTest`；阈值写死，未跑过须在交付说明标注。
 */
class VerticalMeasureCacheBench {

    private companion object {
        const val ENTRY_COUNT = 5_000

        /** 单条字位查表预算：1μs，单位 ns。 */
        const val LOOKUP_BUDGET_NS = 1_000L
    }

    private fun cell(offset: Int) = LayoutCell(
        char = '汉',
        offset = offset,
        kind = CharKind.CJK,
        columnIndex = offset % 28,
        x = 100f,
        y = 36f + (offset % 28) * 64.8f,
        size = 36f,
        rotated = false,
    )

    @Test
    fun `rect lookup stays under 1us per entry`() {
        val cache = VerticalMeasureCache()
        repeat(ENTRY_COUNT) { cache.putRect(cell(it)) }
        assertTrue(cache.coversRects(ENTRY_COUNT))

        // 预热。
        repeat(3) { repeat(ENTRY_COUNT) { cache.rect(it) } }

        val passes = 5
        var totalNanos = 0L
        repeat(passes) {
            repeat(ENTRY_COUNT) {
                val t0 = System.nanoTime()
                cache.rect(it)
                totalNanos += System.nanoTime() - t0
            }
        }
        val avgNanos = totalNanos / (passes * ENTRY_COUNT)
        assertTrue("单条查表超 1μs：${avgNanos}ns", avgNanos <= LOOKUP_BUDGET_NS)
    }
}
