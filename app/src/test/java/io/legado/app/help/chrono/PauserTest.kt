package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 契约 §四 断句层验收（毫秒值锁定）：
 *  典故前后 200 > 人名后 200 > 地名前后 150 > 虚词前 100 > 句末 400 / 逗号 200。
 */
class PauserTest {

    private fun msAt(bounds: List<Pauser.Boundary>, at: Int): Int? =
        bounds.firstOrNull { it.at == at }?.ms

    @Test
    fun `sentence end punctuation pauses 400ms`() {
        val bounds = Pauser.analyze("子曰。")
        assertEquals(Pauser.SENTENCE_MS, msAt(bounds, 3))
    }

    @Test
    fun `comma pauses 200ms`() {
        val bounds = Pauser.analyze("段，克之")
        assertEquals(Pauser.COMMA_MS, msAt(bounds, 2))
    }

    @Test
    fun `particle gets 100ms before it`() {
        val bounds = Pauser.analyze("立于郊")
        // 「于」在下标 1，虚词前停顿落在下标 1
        assertEquals(Pauser.PARTICLE_MS, msAt(bounds, 1))
    }

    @Test
    fun `allusion gets 200ms before and after`() {
        val marks = listOf(Pauser.Mark(0, 2, Pauser.MarkKind.ALLUSION))
        val bounds = Pauser.analyze("退避三舍，及楚", marks)
        assertEquals(Pauser.ALLUSION_MS, msAt(bounds, 0))
        assertEquals(Pauser.ALLUSION_MS, msAt(bounds, 2))
    }

    @Test
    fun `person gets 200ms after`() {
        val marks = listOf(Pauser.Mark(0, 2, Pauser.MarkKind.PERSON))
        val bounds = Pauser.analyze("管仲相齐", marks)
        assertEquals(Pauser.PERSON_MS, msAt(bounds, 2))
        assertTrue(bounds.none { it.at == 0 && it.ms == Pauser.PERSON_MS })
    }

    @Test
    fun `place gets 150ms before and after`() {
        val marks = listOf(Pauser.Mark(2, 4, Pauser.MarkKind.PLACE))
        val bounds = Pauser.analyze("战于新郑", marks)
        assertEquals(Pauser.PLACE_MS, msAt(bounds, 2))
        assertEquals(Pauser.PLACE_MS, msAt(bounds, 4))
    }

    @Test
    fun `same boundary tie keeps higher priority label`() {
        // 人名「克段」0..2，逗号在下标 2：同为 200ms，标签取人名后
        val marks = listOf(Pauser.Mark(0, 2, Pauser.MarkKind.PERSON))
        val bounds = Pauser.analyze("克段，", marks)
        val b = bounds.first { it.at == 2 }
        assertEquals(Pauser.PERSON_MS, b.ms)
        assertEquals("人名后", b.reason)
    }

    @Test
    fun `segment splits by boundaries with trailing pause`() {
        val segs = Pauser.segment("甲，乙。")
        assertEquals(2, segs.size)
        assertEquals("甲，", segs[0].first)
        assertEquals(Pauser.COMMA_MS, segs[0].second)
        assertEquals("乙。", segs[1].first)
        assertEquals(Pauser.SENTENCE_MS, segs[1].second)
    }
}
