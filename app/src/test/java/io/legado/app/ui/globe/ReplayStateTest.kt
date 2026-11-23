package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.replay.ReplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切片 18：回放状态——战役选择、回合推进、进度钳制、光针派生、阶段跟随。
 */
class ReplayStateTest {

    @Test
    fun `默认选中首场战役`() {
        val state = ReplayState()
        assertNotNull(state.current)
        assertEquals(SeedCampaigns_firstId(), state.current?.id)
    }

    @Test
    fun `切换战役归零回合且未知 id 不生效`() {
        val state = ReplayState()
        state.play()
        state.update(2.0)
        state.select("columbus_atlantic")
        assertEquals("columbus_atlantic", state.current?.id)
        assertEquals(0.0, state.turn, 1e-9)
        assertFalse(state.playing)

        state.select("not-exist")
        assertEquals("columbus_atlantic", state.current?.id)
    }

    @Test
    fun `推进回合并在总回合处钳制，播完自动暂停`() {
        val state = ReplayState()
        state.select("columbus_atlantic") // 10 回合
        state.play()
        state.update(100.0, turnsPerSec = 1.0)
        assertEquals(10.0, state.turn, 1e-9)
        assertFalse("播完应自动暂停", state.playing)
        assertEquals(1.0, state.routeProgress(), 1e-9)
    }

    @Test
    fun `未播放时回合不推进`() {
        val state = ReplayState()
        state.select("viking_sea")
        state.update(5.0)
        assertEquals(0.0, state.turn, 1e-9)
        assertEquals(0.0, state.routeProgress(), 1e-9)
    }

    @Test
    fun `光针数量等于途经点数`() {
        val state = ReplayState()
        state.select("alexander_east")
        val markers = state.markers()
        val route = state.route()
        assertNotNull(route)
        assertEquals(route!!.waypoints.size, markers.size)
        markers.forEach { m -> assertNotNull(m.name) }
    }

    @Test
    fun `阶段随回合切换`() {
        val state = ReplayState()
        state.select("hannibal_alps")
        state.seek(12.0)
        val phase = state.currentPhase()
        assertNotNull(phase)
        assertEquals("翻越阿尔卑斯", phase!!.label)
    }

    private fun SeedCampaigns_firstId(): String =
        io.legado.app.ui.globe.core.replay.campaign.SeedCampaigns.all.first().id
}
