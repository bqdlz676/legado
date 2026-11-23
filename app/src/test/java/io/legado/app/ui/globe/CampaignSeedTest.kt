package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.replay.campaign.SeedCampaigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切片 18：6 场国外战役回合数、时间轴阶段、派生路线与阵营配色。
 */
class CampaignSeedTest {

    @Test
    fun `种子战役共 6 场且回合数对齐规格表`() {
        val campaigns = SeedCampaigns.all
        assertEquals(6, campaigns.size)

        val byId = campaigns.associateBy { it.id }
        assertEquals(20, byId["hannibal_alps"]!!.rounds)
        assertEquals(30, byId["alexander_east"]!!.rounds)
        assertEquals(20, byId["crusade_jerusalem"]!!.rounds)
        assertEquals(30, byId["mongol_west"]!!.rounds)
        assertEquals(15, byId["viking_sea"]!!.rounds)
        assertEquals(10, byId["columbus_atlantic"]!!.rounds)
    }

    @Test
    fun `每场战役至少两方阵营且途经点可成线`() {
        SeedCampaigns.all.forEach { c ->
            assertTrue("${c.title} 至少一方", c.sides.isNotEmpty())
            assertTrue("${c.title} 至少两个途经点", c.waypoints.size >= 2)
            val route = c.route()
            assertTrue("${c.title} 弧长大于 0", route.totalKm() > 0.0)
            assertTrue("${c.title} 日程大于 0", route.marchDays() > 0)
        }
    }

    @Test
    fun `阶段回合落在 0 到总回合内且按序排列`() {
        SeedCampaigns.all.forEach { c ->
            assertTrue("${c.title} 至少一个阶段", c.phases.isNotEmpty())
            var prev = -1
            c.phases.forEach { phase ->
                assertTrue("阶段回合非负", phase.turn >= 0)
                assertTrue("阶段回合不超总回合", phase.turn <= c.rounds)
                assertTrue("阶段按回合递增", phase.turn > prev)
                prev = phase.turn
            }
        }
    }

    @Test
    fun `阶段定位：起始回合取首阶段，末尾取末阶段`() {
        val alex = SeedCampaigns.all.first { it.id == "alexander_east" }
        assertEquals(alex.phases.first(), alex.phaseAt(0))
        assertEquals(alex.phases.last(), alex.phaseAt(alex.rounds))
        assertNotNull(alex.phaseAt(15))
    }

    @Test
    fun `亚历山大阵营配色暖金对波斯暗铜`() {
        val alex = SeedCampaigns.all.first { it.id == "alexander_east" }
        val bySide = alex.sides.associateBy { it.id }
        assertEquals("#D4A54A", bySide["macedon"]!!.colorHex)
        assertEquals("#8B6914", bySide["persia"]!!.colorHex)
    }

    @Test
    fun `蒙古阵营为朱砂红`() {
        val mongol = SeedCampaigns.all.first { it.id == "mongol_west" }
        val bySide = mongol.sides.associateBy { it.id }
        assertEquals("#C23B2E", bySide["mongol"]!!.colorHex)
    }

    @Test
    fun `战役路线使用阵营色且凸起固定高度`() {
        val columbus = SeedCampaigns.all.first { it.id == "columbus_atlantic" }
        val route = columbus.route()
        assertEquals("#8A5C4C", route.colorHex)
        assertEquals(1.5, route.lift, 1e-9)
    }
}
