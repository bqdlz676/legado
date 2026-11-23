package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 落文 · 候选循环下标边界单测：环形回绕、单候选钉住、空候选兜底、越界下标先收敛。
 */
class CandidateCycleTest {

    @Test
    fun `三个候选依次循环并在末尾回绕到首个`() {
        assertEquals(1, nextCandidateIndex(3, 0))
        assertEquals(2, nextCandidateIndex(3, 1))
        assertEquals(0, nextCandidateIndex(3, 2))
    }

    @Test
    fun `单候选始终停在 0`() {
        assertEquals(0, nextCandidateIndex(1, 0))
    }

    @Test
    fun `空候选兜底回 0，不除零`() {
        assertEquals(0, nextCandidateIndex(0, 0))
        assertEquals(0, nextCandidateIndex(-1, 2))
    }

    @Test
    fun `负下标先收敛到合法区间再循环`() {
        assertEquals(0, nextCandidateIndex(3, -1))
        assertEquals(2, nextCandidateIndex(3, -2))
    }
}
