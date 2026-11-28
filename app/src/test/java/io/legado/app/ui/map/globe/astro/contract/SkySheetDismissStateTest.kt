package io.legado.app.ui.map.globe.astro.contract

import io.legado.app.ui.map.globe.GlobeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sheet 关闭快照 / 状态回滚纯 JVM 单测（判据 12；§七 跨态约束、§十四.12）。
 *
 * 口径（后令优先）：Sheet 关闭只清 selectedId，不动相机；本用例证明关闭前后相机
 * yaw / pitch / zoom（距离比）三元组逐位相等，且 Sheet 选中态回到初始（selectedId 清空，
 * 宿主挂载条件 `selectedId != null` 不成立 → 卡片不可见）。
 *
 * 口径冲突留痕：规格 §七 / §十四.12 原文称「选中 id 逐位相等」，实现口径为关闭即取消选中；
 * 两处原文与待裁结论见交付报告「口径冲突待裁定」节，本单不自作主张改口径。
 */
class SkySheetDismissStateTest {

    @Test
    fun `关闭Sheet只清selectedId 相机yawpitchzoom逐位不变`() {
        val controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_HEAVEN)
        controller.setSkyPose(25.0, -10.0)
        controller.selectStar(7L)

        val before = controller.scene.sky!!
        val beforePose = before.cameraYawPitch!!
        val beforeZoom = controller.scene.distanceRatio
        assertEquals(7L, before.selectedId)

        // 宿主 onDismiss 同款调用（GlobeScreenHost.kt:216）：仅回传 selectStar(null)，
        // 整条 dismiss 链无任何相机姿态 / 距离写回。
        controller.selectStar(null)

        val after = controller.scene.sky!!
        // 相机姿态逐位相等：yaw / pitch 双标量不变（四元数口径冲突见报告留痕）。
        assertEquals(beforePose.yawDeg, after.cameraYawPitch!!.yawDeg, 0.0)
        assertEquals(beforePose.pitchDeg, after.cameraYawPitch.pitchDeg, 0.0)
        // zoom（相机距离比）逐位相等：关闭链路不写回相机距离。
        assertEquals(beforeZoom, controller.scene.distanceRatio, 0.0f)
        // Sheet 状态回到初始：selectedId 清空；宿主仅在 selectedId != null 时挂载卡片。
        assertNull(after.selectedId)
    }

    @Test
    fun `关闭后选中态回落初始 与新鲜控制器空选中口径一致`() {
        val controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_HEAVEN)
        controller.setSkyPose(12.0, 5.0)
        controller.selectStar(3L)
        controller.selectStar(null)

        val fresh = GlobeController()
        // 新鲜控制器未入星空（sky=null）；本控制器关闭后 selectedId 同为 null 初始口径。
        assertNull(controller.scene.sky!!.selectedId)
        assertNull(fresh.scene.sky?.selectedId)
    }
}
