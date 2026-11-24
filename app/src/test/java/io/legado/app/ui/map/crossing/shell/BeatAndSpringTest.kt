package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B14 节拍计算与过渡参数选择纯函数单测（纯 JVM，不依赖 Compose 运行时）。
 *
 * 覆盖：三类事件档位节拍（战斗/行军/脚本事件）+ 空事件静默档；
 * 长局批量节拍压缩（≥50 回合向地板收拢，深回合不跌破地板）；
 * 回合 0 不压缩；弹簧角色参数集中且与组件库口径一致。
 */
class BeatAndSpringTest {

    private val combatLine = listOf("遭遇战：红军 对 黔军（伏击=false，高地=false）")
    private val marchLine = listOf("红一团 转入侧翼包抄")
    private val eventLine = listOf("事件「四面楚歌」：楚军 士气变为 wavering")
    private val mixedLine = listOf("遭遇战：甲 对 乙", "乙 转入撤退")

    @Test
    fun `combat events resolve to combat beat`() {
        assertEquals(BeatKind.COMBAT, classifyBeat(combatLine))
        // 短局（turn < 50）取基础节拍，不压缩。
        assertEquals(620L, beatDelayMs(combatLine, turn = 7))
    }

    @Test
    fun `march events resolve to march beat`() {
        assertEquals(BeatKind.MARCH, classifyBeat(marchLine))
        assertEquals(460L, beatDelayMs(marchLine, turn = 3))
    }

    @Test
    fun `scripted events resolve to event beat`() {
        assertEquals(BeatKind.EVENT, classifyBeat(eventLine))
        assertEquals(540L, beatDelayMs(eventLine, turn = 5))
    }

    @Test
    fun `mixed events take highest priority combat beat`() {
        // 优先级：战斗 > 脚本事件 > 行军 > 静默；同回合并存时取战斗档。
        assertEquals(BeatKind.COMBAT, classifyBeat(mixedLine))
    }

    @Test
    fun `empty events resolve to quiet beat`() {
        assertEquals(BeatKind.QUIET, classifyBeat(emptyList()))
        assertEquals(320L, beatDelayMs(emptyList(), turn = 1))
    }

    @Test
    fun `turn zero never compresses beat`() {
        // 首回合（turn = 0）直接返回档位基础节拍，不进入长局压缩公式。
        assertEquals(620L, beatDelayMs(combatLine, turn = 0))
        assertEquals(320L, beatDelayMs(emptyList(), turn = 0))
    }

    @Test
    fun `long campaign batch beat compresses toward floor`() {
        // turn 60（超过 50 起点 10 步）：战斗档节拍低于基础值，但仍高于地板。
        val mid = beatDelayMs(combatLine, turn = 60)
        assertTrue("压缩后应低于基础 620ms，实际 $mid", mid < 620L)
        assertTrue("不应跌破地板 180ms，实际 $mid", mid >= BATCH_BEAT_FLOOR_MS)

        // 深回合（turn 100）：压缩持续向地板收拢，仍不破地板。
        val deep = beatDelayMs(combatLine, turn = 100)
        assertTrue("深回合应高于地板，实际 $deep", deep >= BATCH_BEAT_FLOOR_MS)
        assertTrue("深回合节拍应比中期更短，实际 $deep <= $mid", deep <= mid)
    }

    @Test
    fun `very long campaign clamps beat to floor`() {
        // 压缩系数在深回合会压到负值，必须钳到地板而不是产出负节拍。
        val floor = beatDelayMs(combatLine, turn = 400)
        assertEquals(BATCH_BEAT_FLOOR_MS, floor)
    }

    @Test
    fun `quiet beat also compresses in long campaign`() {
        val quietDeep = beatDelayMs(emptyList(), turn = 80)
        assertTrue("空事件长局节拍应高于地板，实际 $quietDeep", quietDeep >= BATCH_BEAT_FLOOR_MS)
        assertTrue("空事件长局节拍应低于静默基础值 320ms，实际 $quietDeep", quietDeep < 320L)
    }

    @Test
    fun `spring roles source palette constants and carry rationale`() {
        // 高亮/改写/面板三角色沿用 reveal 口径；回合淡入沿用气泡 overshoot 口径。
        assertEquals(ComponentPalette.REVEAL_SPRING_DAMPING, springFor(SpringRole.REVEAL_HIGHLIGHT).dampingRatio, 0f)
        assertEquals(ComponentPalette.REVEAL_SPRING_STIFFNESS, springFor(SpringRole.REVEAL_HIGHLIGHT).stiffness, 0f)
        assertEquals(ComponentPalette.REVEAL_SPRING_DAMPING, springFor(SpringRole.INTENT_OVERRIDE).dampingRatio, 0f)
        assertEquals(ComponentPalette.REVEAL_SPRING_STIFFNESS, springFor(SpringRole.PALETTE_CHIP).stiffness, 0f)
        assertEquals(ComponentPalette.BUBBLE_SPRING_DAMPING, springFor(SpringRole.TURN_CROSSFADE).dampingRatio, 0f)
        assertEquals(ComponentPalette.BUBBLE_SPRING_STIFFNESS, springFor(SpringRole.TURN_CROSSFADE).stiffness, 0f)
        SpringRole.entries.forEach { role ->
            assertFalse("每个弹簧角色必须写明阻尼/刚度选型理由", role.rationale.isBlank())
        }
    }
}
