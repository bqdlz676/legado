package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.mesh.LodLevel
import io.legado.app.ui.globe.core.scene.GlobeScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 场景聚合状态：相机钳制、LOD 联动、太阳方向、地名可见性（纯 JVM）。
 */
class GlobeSceneTest {

    @Test
    fun `初始相机距离在合法区间内`() {
        val scene = GlobeScene()
        val ratio = scene.distanceRatio
        assertTrue(ratio >= GlobeConfig.CAMERA_MIN_DISTANCE_RATIO)
        assertTrue(ratio <= GlobeConfig.CAMERA_MAX_DISTANCE_RATIO)
    }

    @Test
    fun `缩放钳制在最近与最远之间`() {
        val scene = GlobeScene()
        scene.zoomBy(10f) // 拉近
        assertTrue(scene.distanceRatio <= GlobeConfig.CAMERA_MIN_DISTANCE_RATIO + 1e-3f)
        scene.zoomBy(0.1f) // 拉远
        assertTrue(scene.distanceRatio >= GlobeConfig.CAMERA_MAX_DISTANCE_RATIO - 1e-3f)
    }

    @Test
    fun `远距更新后选 LOD0`() {
        val scene = GlobeScene()
        scene.setCameraDistance(GlobeConfig.RADIUS * 5f)
        scene.update(0.016, System.currentTimeMillis())
        assertEquals(LodLevel.LOD0, scene.currentLod)
    }

    @Test
    fun `近距更新后选 LOD2（morph 3 帧归位）`() {
        val scene = GlobeScene()
        scene.setCameraDistance(GlobeConfig.RADIUS * 0.7f)
        repeat(4) { scene.update(0.016, System.currentTimeMillis()) }
        assertEquals(LodLevel.LOD2, scene.currentLod)
    }

    @Test
    fun `太阳方向为单位向量且地名列表非空`() {
        val scene = GlobeScene()
        scene.update(0.016, System.currentTimeMillis())
        val len = sqrt(scene.sunDir.x * scene.sunDir.x +
            scene.sunDir.y * scene.sunDir.y + scene.sunDir.z * scene.sunDir.z)
        assertEquals(1.0, len, 1e-6)
        assertTrue(scene.visiblePlaces().isNotEmpty())
    }
}
