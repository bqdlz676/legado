package io.legado.app.ui.map.globe.scene

import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.mesh.LodLevel
import io.legado.app.ui.map.globe.offline.DegradationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场景装配与帧快照冒烟单测：无缓存离线状态下也能产出完整快照，不空白、不崩溃。
 */
class SceneSnapshotTest {

    @Test
    fun `首帧快照带齐路线光针标签与兜底档位`() {
        val controller = GlobeController()
        controller.setOnline(false)
        val snapshot = controller.nextFrame(dt = 0.016, utcMillis = System.currentTimeMillis())

        assertNotNull(snapshot.route)
        assertTrue(snapshot.markers.isNotEmpty())
        assertTrue(snapshot.visibleLabels.isNotEmpty())
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, snapshot.degradation)
        assertEquals(12, snapshot.powerLayer.powers.size)
        assertTrue(snapshot.fallbackResolutions.size == io.legado.app.ui.map.globe.offline.TextureKind.entries.size)
    }

    @Test
    fun `推进多帧后 LOD 与相机距离保持合法区间`() {
        val controller = GlobeController()
        repeat(30) {
            val snapshot = controller.nextFrame(dt = 0.016, utcMillis = System.currentTimeMillis())
            assertTrue(snapshot.distanceRatio in 0.6f..6.0f)
            assertTrue(snapshot.lod in LodLevel.entries)
        }
    }

    @Test
    fun `捏合缩放钳制在相机距离区间内`() {
        val controller = GlobeController()
        controller.onZoom(0.1f) // 推到最近
        assertTrue(controller.scene.distanceRatio >= 0.6f - 1e-6)
        controller.onZoom(100f) // 拉到最远
        assertTrue(controller.scene.distanceRatio <= 6.0f + 1e-6)
    }

    @Test
    fun `切换战役后快照路线跟随`() {
        val controller = GlobeController()
        controller.selectCampaign("alexander_east")
        val snapshot = controller.nextFrame(0.016, System.currentTimeMillis())
        assertEquals("alexander_east-route", snapshot.route?.id)
    }

    @Test
    fun `缓存核心纹理后档位升到混合`() {
        val controller = GlobeController()
        controller.markAssetCached(io.legado.app.ui.map.globe.offline.TextureKind.STAR_SKY, null)
        val snapshot = controller.nextFrame(0.016, System.currentTimeMillis())
        assertEquals(DegradationLevel.CACHED_MIX, snapshot.degradation)
    }
}
