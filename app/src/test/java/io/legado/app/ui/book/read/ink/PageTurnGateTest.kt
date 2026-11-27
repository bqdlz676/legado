package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 翻页门禁状态机单测（纯 JVM，离线可跑）。
 *
 * 裁决口径：掀角动画进行中屏蔽一切翻页；首页 / 末页边界越界拒绝；其余放行。
 * 与控制器 [InkReadController.dispatchCommand] 灌入的动画态 / 边界态一一对应。
 */
class PageTurnGateTest {

    @Test
    fun `空闲且前后均可翻 双向放行`() {
        val gate = PageTurnGate(animating = false, canPrev = true, canNext = true)
        assertTrue(gate.evaluate(EdgeDirection.Prev).allowed)
        assertTrue(gate.evaluate(EdgeDirection.Next).allowed)
    }

    @Test
    fun `翻页动画进行中 双向都屏蔽`() {
        val gate = PageTurnGate(animating = true, canPrev = true, canNext = true)
        val prev = gate.evaluate(EdgeDirection.Prev)
        val next = gate.evaluate(EdgeDirection.Next)
        assertFalse(prev.allowed)
        assertFalse(next.allowed)
        assertEquals(TurnDenied.Animating, prev.denied)
        assertEquals(TurnDenied.Animating, next.denied)
    }

    @Test
    fun `首页再上翻被边界拒绝 下翻仍放行`() {
        val gate = PageTurnGate(animating = false, canPrev = false, canNext = true)
        val prev = gate.evaluate(EdgeDirection.Prev)
        val next = gate.evaluate(EdgeDirection.Next)
        assertFalse(prev.allowed)
        assertEquals(TurnDenied.AtBoundary, prev.denied)
        assertTrue(next.allowed)
        assertNull(next.denied)
    }

    @Test
    fun `末页再下翻被边界拒绝 上翻仍放行`() {
        val gate = PageTurnGate(animating = false, canPrev = true, canNext = false)
        val prev = gate.evaluate(EdgeDirection.Prev)
        val next = gate.evaluate(EdgeDirection.Next)
        assertTrue(prev.allowed)
        assertFalse(next.allowed)
        assertEquals(TurnDenied.AtBoundary, next.denied)
    }

    @Test
    fun `单页章双向均为边界`() {
        val gate = PageTurnGate(animating = false, canPrev = false, canNext = false)
        assertFalse(gate.evaluate(EdgeDirection.Prev).allowed)
        assertFalse(gate.evaluate(EdgeDirection.Next).allowed)
    }

    @Test
    fun `动画态优先于边界裁决`() {
        // 动画中且处于首页：拒绝原因记动画屏蔽，而不是边界越界。
        val gate = PageTurnGate(animating = true, canPrev = false, canNext = false)
        assertEquals(TurnDenied.Animating, gate.evaluate(EdgeDirection.Prev).denied)
        assertEquals(TurnDenied.Animating, gate.evaluate(EdgeDirection.Next).denied)
    }

    @Test
    fun `裁决计数按放行 动画屏蔽 边界拒绝分别累加 清零后归零`() {
        val gate = PageTurnGate(animating = false, canPrev = false, canNext = true)
        gate.evaluate(EdgeDirection.Next)   // 放行
        gate.evaluate(EdgeDirection.Prev)   // 边界拒绝
        gate.animating = true
        gate.evaluate(EdgeDirection.Next)   // 动画屏蔽（优先于边界）
        assertEquals(3, gate.evaluatedCount)
        assertEquals(1, gate.allowedCount)
        assertEquals(1, gate.deniedBoundaryCount)
        assertEquals(1, gate.deniedAnimatingCount)

        gate.resetCounters()
        assertEquals(0, gate.evaluatedCount)
        assertEquals(0, gate.allowedCount)
        assertEquals(0, gate.deniedBoundaryCount)
        assertEquals(0, gate.deniedAnimatingCount)
    }
}
