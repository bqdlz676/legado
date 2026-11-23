package io.legado.app.ui.map.globe.replay

import io.legado.app.ui.map.globe.replay.campaign.SeedCampaigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战役回放状态机单测：选关、播放推进、回合钳制、路线 / 光针 / 阶段派生（切片 18）。
 */
class ReplayStateTest {

    @Test
    fun `默认选中第一场种子战役`() {
        val state = ReplayState()
        assertNotNull(state.current)
        assertEquals(SeedCampaigns.all.first().id, state.campaignId)
    }

    @Test
    fun `切换到存在的战役并归零回合`() {
        val state = ReplayState()
        val target = SeedCampaigns.all[1].id
        state.select(target)
        assertEquals(target, state.campaignId)
        assertEquals(0.0, state.turn, 1e-9)
        assertFalse(state.playing)
    }

    @Test
    fun `切换不存在的战役保持原样不崩溃`() {
        val state = ReplayState()
        val before = state.campaignId
        state.select("no_such_campaign")
        assertEquals(before, state.campaignId)
    }

    @Test
    fun `播放按时间推进回合并在末尾停住`() {
        val state = ReplayState()
        state.play()
        state.update(dt = 100.0, turnsPerSec = 1.0)
        val campaign = state.current!!
        assertEquals(campaign.rounds.toDouble(), state.turn, 1e-9)
        assertFalse(state.playing)
    }

    @Test
    fun `seek 钳制在 0 到总回合之间`() {
        val state = ReplayState()
        state.seek(-5.0)
        assertEquals(0.0, state.turn, 1e-9)
        state.seek(9999.0)
        assertEquals(state.current!!.rounds.toDouble(), state.turn, 1e-9)
    }

    @Test
    fun `路线进度等于回合除总回合`() {
        val state = ReplayState()
        state.seek(state.current!!.rounds / 2.0)
        assertEquals(0.5, state.routeProgress(), 1e-6)
    }

    @Test
    fun `途经点派生等量光针`() {
        val state = ReplayState()
        val markers = state.markers()
        assertEquals(state.current!!.waypoints.size, markers.size)
    }

    @Test
    fun `当前阶段随回合推进切换`() {
        val state = ReplayState()
        val firstPhase = state.current!!.phases.first()
        state.seek(firstPhase.turn.toDouble())
        assertEquals(firstPhase.label, state.currentPhase()?.label)
    }

    @Test
    fun `无战役时路线与光针为空不崩溃`() {
        val state = ReplayState(campaigns = emptyList())
        assertNull(state.route())
        assertTrue(state.markers().isEmpty())
        assertEquals(0.0, state.routeProgress(), 1e-9)
    }

    @Test
    fun `六场种子战役回合数对齐切片 18 表`() {
        val rounds = SeedCampaigns.all.associate { it.id to it.rounds }
        assertEquals(20, rounds["hannibal_alps"])
        assertEquals(30, rounds["alexander_east"])
        assertEquals(20, rounds["crusade_jerusalem"])
        assertEquals(30, rounds["mongol_west"])
        assertEquals(15, rounds["viking_sea"])
        assertEquals(10, rounds["columbus_atlantic"])
    }
}
