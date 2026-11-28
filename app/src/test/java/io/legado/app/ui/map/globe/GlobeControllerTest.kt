package io.legado.app.ui.map.globe

import io.legado.app.ui.map.globe.astro.contract.CameraYawPitch
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.SiderealTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * 地球仪门面反向分野聚焦与观测点 / 时刻注入纯 JVM 单测（D39 + 判据 2；§十一.2、§十四.2）。
 *
 * 覆盖：未入星空聚焦先进天外视角且目标 null 不改姿态、重复调用不抛、观测点写回帧快照、
 * 观测点越界钳制、固定观测时刻注入后 LST 按注入时刻推导、带目标姿态弹簧续接。
 * 纯 JVM：不引 Android / Compose；帧驱动走 [GlobeController.nextFrame] 既有入口。
 */
class GlobeControllerTest {

    private companion object {
        /** 验收手算时刻 B：2026-11-28 16:00:00 UTC。 */
        val FIXED_UTC_B: Long = Instant.parse("2026-11-28T16:00:00Z").toEpochMilli()

        /** 无注入时默认观测点经度（与 GlobeController 伴生长安示意一致）。 */
        const val DEFAULT_LON_DEG: Double = 108.94
    }

    @Test
    fun `聚焦州域未入星空先进天外视角 目标null不改姿态`() {
        val controller = GlobeController()
        controller.focusFieldRegion("雍州", null)

        assertNotNull("反向聚焦后星空帧必须建立", controller.scene.sky)
        assertEquals(SkyViewMode.SKY_HEAVEN, controller.scene.sky!!.mode)
        // 改坏推演：若 focusFieldRegion 误调 enterSky 地上观或写入默认姿态，
        // 上面 mode 断言 / 下面姿态断言依次变红；target=null 时 cameraYawPitch 保持 null。
        assertNull(controller.scene.sky!!.cameraYawPitch)

        // 重复点按同一州域：幂等不叠加第二路过场、不抛异常（施工图 2.3 边界 6）。
        controller.focusFieldRegion("雍州", null)
    }

    @Test
    fun `setObserver写回帧快照观测点 越界经纬度钳制`() {
        val controller = GlobeController()
        controller.setObserver(30.0, 120.0)
        val snapshot = controller.nextFrame(0.016, FIXED_UTC_B)

        assertNotNull(snapshot.sky?.observerLatLon)
        assertEquals(30.0, snapshot.sky!!.observerLatLon!!.latDeg, 1e-9)
        assertEquals(120.0, snapshot.sky!!.observerLatLon!!.lonDeg, 1e-9)

        // 改坏推演：若钳制缺失，999° 会原样写进快照，下面两条断言变红。
        controller.setObserver(999.0, 999.0)
        val clamped = controller.nextFrame(0.016, FIXED_UTC_B)
        assertEquals(90.0, clamped.sky!!.observerLatLon!!.latDeg, 1e-9)
        assertEquals(180.0, clamped.sky!!.observerLatLon!!.lonDeg, 1e-9)
    }

    @Test
    fun `setObservationTime注入固定时刻 恒星时按注入时刻推导`() {
        val controller = GlobeController()
        controller.setObservationTime(FIXED_UTC_B)
        // 帧墙钟传 0：LST 若跟墙钟会得到 0 时刻值；正确实现必须取注入时刻。
        val snapshot = controller.nextFrame(0.016, 0L)
        val expectedLst = SiderealTime.localSiderealTimeDeg(FIXED_UTC_B, DEFAULT_LON_DEG)
        // 改坏推演：若注入入口没接进 nextFrame（仍读 utcMillis），这里 LST 按 0 时刻算，
        // 与期望值偏差数百度，断言在下面 assertEquals 变红。
        assertEquals(expectedLst, snapshot.sky!!.siderealTimeDeg, 1e-9)

        // 传 null 恢复跟随帧驱动墙钟。
        controller.setObservationTime(null)
        val restored = controller.nextFrame(0.016, FIXED_UTC_B)
        assertEquals(
            SiderealTime.localSiderealTimeDeg(FIXED_UTC_B, DEFAULT_LON_DEG),
            restored.sky!!.siderealTimeDeg,
            1e-9,
        )
    }

    @Test
    fun `focusFieldRegion带目标姿态 弹簧续接写入相机姿态`() {
        val controller = GlobeController()
        controller.focusFieldRegion("雍州", CameraYawPitch(30.0, 10.0))
        val snapshot = controller.nextFrame(0.016, FIXED_UTC_B)
        // 改坏推演：若 aimSkyTo 没接 beginTransition，帧弹簧不推进，cameraYawPitch 仍为 null，
        // 下面 assertNotNull 变红。
        assertNotNull(snapshot.sky?.cameraYawPitch)
    }
}
