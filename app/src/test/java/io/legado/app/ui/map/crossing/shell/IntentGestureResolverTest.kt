package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.map.crossing.model.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B11 意图手势识别纯函数单测（纯 JVM，不依赖 Compose）。
 *
 * 覆盖：12 意图逐条命中（8 方向拖拽罗盘 + 3 面板命中区 + 1 长按通道面板兜底）；
 * 边界 4 条：超短拖误触 / 超长拖归位 / 斜向象限 / 双指忽略；
 * 另补：长按静置展开面板、未知区码忽略。
 */
class IntentGestureResolverTest {

    private data class Case(
        val name: String,
        val input: GestureInput,
        val expected: GestureResult,
    )

    private fun dial(dx: Float, dy: Float, pointerCount: Int = 1) =
        GestureInput(dx = dx, dy = dy, elapsedMs = 0L, pointerCount = pointerCount)

    private fun zone(zone: String) =
        GestureInput(dx = 0f, dy = 0f, elapsedMs = 0L, pointerCount = 1, paletteZone = zone)

    @Test
    fun `12 intents all reachable through gesture or zone`() {
        val cases = listOf(
            // ── 八向罗盘：方向拖拽 ──────────────────────────────
            Case("进军·正北", dial(0f, -60f), GestureResult.Pick(Intent.ADVANCE)),
            Case("急行军·东北", dial(42f, -42f), GestureResult.Pick(Intent.RAPID)),
            Case("支援·正东", dial(60f, 0f), GestureResult.Pick(Intent.SUPPORT)),
            Case("溃逃·东南", dial(42f, 42f), GestureResult.Pick(Intent.ROUT)),
            Case("撤退·正南", dial(0f, 60f), GestureResult.Pick(Intent.RETREAT)),
            Case("突围·西南", dial(-42f, 42f), GestureResult.Pick(Intent.BREAKOUT)),
            Case("包抄·正西", dial(-60f, 0f), GestureResult.Pick(Intent.FLANK)),
            Case("夺隘·西北", dial(-42f, -42f), GestureResult.Pick(Intent.SEIZE)),
            // ── 面板命中区：直选 ────────────────────────────────
            Case("据守·面板", zone("据守"), GestureResult.Pick(Intent.HOLD)),
            Case("设伏·面板", zone("设伏"), GestureResult.Pick(Intent.AMBUSH)),
            Case("应战·面板", zone("应战"), GestureResult.Pick(Intent.FIGHT)),
            Case("续进·面板", zone("续进"), GestureResult.Pick(Intent.CONTINUE)),
        )

        cases.forEach { c ->
            val actual = resolveGesture(c.input)
            assertEquals("${c.name} 手势识别不符", c.expected, actual)
        }
    }

    @Test
    fun `boundary gestures do not misfire intent`() {
        val cases = listOf(
            // 超短拖：位移低于 minDragPx(24px) → 误触忽略，不改意图。
            Case("超短拖·忽略", dial(10f, 0f), GestureResult.Ignored),
            // 超长拖：位移超出 maxDragPx(160px) → 不归零，仍按最近八向归位正东。
            Case("超长拖·归位正东", dial(300f, 0f), GestureResult.Pick(Intent.SUPPORT)),
            // 斜向 45°：dx/dy 等长 → 东南象限溃逃。
            Case("斜向·东南", dial(30f, 30f), GestureResult.Pick(Intent.ROUT)),
            // 双指：预留给地图缩放/平移，面板不抢手势 → 忽略。
            Case("双指·忽略", dial(0f, -60f, pointerCount = 2), GestureResult.Ignored),
        )

        cases.forEach { c ->
            val actual = resolveGesture(c.input)
            assertEquals("${c.name} 边界处理不符", c.expected, actual)
        }
    }

    @Test
    fun `long press still opens palette without changing intent`() {
        val heldStill = GestureInput(dx = 0f, dy = 0f, elapsedMs = 400L, pointerCount = 1)
        assertEquals(GestureResult.OpenPalette, resolveGesture(heldStill))

        // 长按后位移超出取消半径(18px) → 长按作废，转入拖拽通道。
        val movedAfterHold = GestureInput(dx = 30f, dy = 0f, elapsedMs = 400L, pointerCount = 1)
        assertEquals(GestureResult.Pick(Intent.SUPPORT), resolveGesture(movedAfterHold))
    }

    @Test
    fun `unknown zone is ignored and mapping table covers all twelve`() {
        assertEquals(GestureResult.Ignored, resolveGesture(zone("不存在的意图")))
        // 映射表必须恰好覆盖枚举全部 12 项，新增枚举而漏登记会在此暴露。
        assertEquals(12, IntentGestureMap.entries.size)
        assertTrue(IntentGestureMap.entries.keys.containsAll(Intent.entries.toList()))
    }
}
