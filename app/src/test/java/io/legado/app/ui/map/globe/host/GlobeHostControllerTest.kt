package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.offline.TextureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地球仪宿主粘合层单测（纯 JVM，不引 Android / Compose）。
 *
 * 覆盖门面调用关键路径：离线兜底快照完整性、在线未缓存降级、缓存登记升档、
 * 战役切换 / 播放 / 重置 / 回合钳制、手势映射、派生文案与战役种子对齐切片 18。
 * 口径：本测试在宿主 JVM 跑，只验证纯逻辑路径；荣耀 WIN 真机渲染耗时需复测，
 * 未在本机 / 真机验证的部分不计入通过结论（编译通过 ≠ 功能可用）。
 */
class GlobeHostControllerTest {

    private fun newHost(online: Boolean = false): GlobeHostController {
        val host = GlobeHostController()
        host.setOnline(online)
        return host
    }

    @Test
    fun `离线首帧快照带齐路线光针标签且走程序化兜底`() {
        val host = newHost(online = false)
        val snapshot = host.frame(0.016, System.currentTimeMillis())

        assertNotNull(snapshot.route)
        assertTrue("离线兜底光针不能为空", snapshot.markers.isNotEmpty())
        assertTrue("离线兜底地名标签不能为空", snapshot.visibleLabels.isNotEmpty())
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, snapshot.degradation)
        assertEquals(
            TextureKind.entries.size,
            snapshot.fallbackResolutions.size,
        )
        assertTrue("势力图层必须带种子兜底", snapshot.powerLayer.powers.isNotEmpty())
    }

    @Test
    fun `在线但纹理未命中仍先画兜底不空白`() {
        val host = newHost(online = true)
        val snapshot = host.frame(0.016, System.currentTimeMillis())

        // 在线未缓存 = PENDING_DOWNLOAD，门面仍返回兜底快照，不等待下载。
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, snapshot.degradation)
        assertNotNull(snapshot.route)
        assertTrue(snapshot.markers.isNotEmpty())
    }

    @Test
    fun `登记核心素材缓存后档位升到混合`() {
        val host = newHost(online = true)
        host.markAssetCached(TextureKind.STAR_SKY, null)
        val snapshot = host.frame(0.016, System.currentTimeMillis())
        assertEquals(DegradationLevel.CACHED_MIX, snapshot.degradation)
    }

    @Test
    fun `切换战役路线跟随且回合归零`() {
        val host = newHost()
        host.selectCampaign("alexander_east")
        val snapshot = host.frame(0.016, System.currentTimeMillis())
        assertEquals("alexander_east-route", snapshot.route?.id)
        assertEquals(0.0, host.turn, 1e-9)
    }

    @Test
    fun `切换不存在的战役保持原样不崩溃`() {
        val host = newHost()
        val before = host.currentCampaign?.id
        host.selectCampaign("no_such_campaign")
        assertEquals(before, host.currentCampaign?.id)
    }

    @Test
    fun `播放按时间推进回合，重置归零`() {
        val host = newHost()
        val rounds = host.currentCampaign!!.rounds
        host.play()
        repeat(rounds + 2) { host.frame(dtSec = 1.0, utcMillis = System.currentTimeMillis()) }
        assertEquals(rounds.toDouble(), host.turn, 1e-9)
        assertTrue(!host.playing)

        host.resetReplay()
        assertEquals(0.0, host.turn, 1e-9)
    }

    @Test
    fun `seek 钳制在零到总回合之间`() {
        val host = newHost()
        val rounds = host.currentCampaign!!.rounds
        host.seekTurn(-5.0)
        assertEquals(0.0, host.turn, 1e-9)
        host.seekTurn(9999.0)
        assertEquals(rounds.toDouble(), host.turn, 1e-9)
    }

    @Test
    fun `拖动与缩放映射门面姿态，距离钳制在区间内`() {
        val host = newHost()
        host.dragStart()
        val beforeYaw = host.controller.scene.rotator.yawDeg
        host.drag(120.0, 0.0)
        assertTrue(host.controller.scene.rotator.yawDeg != beforeYaw)
        host.dragEnd()

        host.zoom(0.1f)
        assertTrue(host.distanceRatio >= 0.6f - 1e-6)
        host.zoom(100f)
        assertTrue(host.distanceRatio <= 6.0f + 1e-6)
    }

    @Test
    fun `双击飞行设定姿态，自动旋转开关可切换`() {
        val host = newHost()
        host.flyTo(39.9, 116.4)
        // flyTo 内部 setPose(-lng, lat)，偏航取负经度。
        assertEquals(-116.4, host.controller.scene.rotator.yawDeg, 1e-6)

        assertFalse(host.autoRotate)
        host.toggleAutoRotate()
        assertTrue(host.autoRotate)
        host.toggleAutoRotate()
        assertFalse(host.autoRotate)
    }

    @Test
    fun `派生文案非空且汇总数值合法`() {
        val host = newHost()
        val snapshot = host.frame(0.016, System.currentTimeMillis())

        assertTrue(host.degradationLabel(snapshot.degradation).isNotBlank())
        assertTrue(host.routeSummary(snapshot).contains("公里"))
        assertTrue(host.fallbackSummary(snapshot).contains("素材就位"))
        assertTrue(host.sidesLabel().isNotBlank())
        assertNotNull(host.phaseLabel())
    }

    @Test
    fun `六场种子战役回合数对齐切片 18 表`() {
        val host = newHost()
        assertEquals(6, host.campaigns.size)
        val rounds = host.campaigns.associate { it.id to it.rounds }
        assertEquals(20, rounds["hannibal_alps"])
        assertEquals(30, rounds["alexander_east"])
        assertEquals(20, rounds["crusade_jerusalem"])
        assertEquals(30, rounds["mongol_west"])
        assertEquals(15, rounds["viking_sea"])
        assertEquals(10, rounds["columbus_atlantic"])
    }
}
