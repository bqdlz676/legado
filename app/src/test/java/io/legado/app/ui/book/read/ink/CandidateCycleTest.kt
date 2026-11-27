package io.legado.app.ui.book.read.ink

import io.legado.app.help.bubble.BubbleCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 落文 · 候选循环单测（切片-02 §4 候选切换）。
 *
 * 覆盖：环形回绕、单候选钉住、空候选兜底、负 / 越界下标收敛、
 * query（重查裸词，不带括号）与 display（展示名，带括号装饰）双字段不串。
 */
class CandidateCycleTest {

    private fun cand(query: String, display: String) = BubbleCandidate(query, display)

    private lateinit var candidates: List<BubbleCandidate>

    @Before
    fun setUp() {
        candidates = listOf(
            cand("晋", "晋（诸侯国）"),
            cand("晋州", "晋（今山西晋城）"),
            cand("晋", "晋（唐以后地名）"),
        )
    }

    @Test
    fun `三个候选依次循环并在末尾回绕到首个`() {
        val cycle = CandidateCycle(candidates)
        assertEquals(0, cycle.index)
        assertEquals(candidates[1], cycle.next())
        assertEquals(1, cycle.index)
        assertEquals(candidates[2], cycle.next())
        assertEquals(2, cycle.index)
        assertEquals(candidates[0], cycle.next())
        assertEquals(0, cycle.index)
    }

    @Test
    fun `query 为不带括号的重查裸词，display 保留括号装饰`() {
        val cycle = CandidateCycle(candidates)
        val picked = cycle.next()!!
        assertEquals("晋州", picked.query)
        assertEquals("晋（今山西晋城）", picked.display)
        // 回传给解析链的只能是 query：括号装饰不得进重查词。
        assertTrue(cycle.query!!.none { it == '（' || it == '）' })
    }

    @Test
    fun `单候选始终停在 0`() {
        val only = cand("抽", "抽")
        val cycle = CandidateCycle(listOf(only))
        assertEquals(0, cycle.index)
        assertEquals(only, cycle.next())
        assertEquals(0, cycle.index)
    }

    @Test
    fun `空候选兜底返回 null，不除零`() {
        val cycle = CandidateCycle(emptyList())
        assertEquals(0, cycle.index)
        assertNull(cycle.current)
        assertNull(cycle.next())
        assertNull(cycle.query)
        assertNull(cycle.display)
        assertEquals("", candidateLabel(cycle))
    }

    @Test
    fun `负下标与越界下标先收敛到合法区间`() {
        assertEquals(0, CandidateCycle.nextIndex(3, -1))
        assertEquals(2, CandidateCycle.nextIndex(3, -2))
        assertEquals(0, CandidateCycle.nextIndex(3, 5))
        assertEquals(0, CandidateCycle.nextIndex(0, 2))
        assertEquals(0, CandidateCycle.nextIndex(-1, 2))
    }

    @Test
    fun `候选行文案：首项提示总数，后续标序号与展示名`() {
        val cycle = CandidateCycle(candidates)
        assertEquals("还有 3 种解释 ›", candidateLabel(cycle))
        cycle.next()
        assertEquals("候选 2/3：晋（今山西晋城） ›", candidateLabel(cycle))
    }

    @Test
    fun `构造期复制候选列表 后续改动原列表不影响循环`() {
        val mutable = candidates.toMutableList()
        val cycle = CandidateCycle(mutable)
        mutable.clear()
        assertEquals(3, cycle.size)
        assertEquals(candidates[0], cycle.current)
    }

    @Test
    fun `selectAt 越界收敛并定位候选`() {
        val cycle = CandidateCycle(candidates)
        assertEquals(candidates[2], cycle.selectAt(7))
        assertEquals(2, cycle.index)
        assertEquals(candidates[0], cycle.selectAt(-3))
        assertEquals(0, cycle.index)
    }
}
