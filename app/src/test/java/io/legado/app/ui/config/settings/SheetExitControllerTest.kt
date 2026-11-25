package io.legado.app.ui.config.settings

import androidx.compose.runtime.mutableStateOf
import io.legado.app.ui.book.read.NoteDialogExitGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B29 设置域弹层两段式离场控制器单测（纯 JVM，不依赖 Compose / Android 运行时）。
 *
 * 阶段机复用 B26 [NoteDialogExitGate]（其复用 B21 `ReaderSheetExitPhase`）；本控制器只是
 * Compose 层接线薄封装，迁移口径与 B21 / B26 一致：OPEN → EXITING（子树保留、visible=false）
 * → REMOVED（子树摘除）。动画延时留在 Compose 层 [SheetExitHost]，不进入本断言。
 *
 * 断言与锚点卡判据一一对应：
 *  - 判据 1：关闭先进离场段播动画，收束（finishExit）后才摘除子树；
 *  - 判据 3：离场段 [SheetExitController.canOpen] 拒收重复打开；
 *  - 判据 5：关闭瞬间子树仍在（[SheetExitController.isComposed] 为 true）、可见性已 false。
 */
class SheetExitControllerTest {

    private fun newController() = SheetExitController(NoteDialogExitGate(), mutableStateOf(false))

    @Test
    fun `初始为摘除态 不可见 子树不在 可接受新打开`() {
        val controller = newController()

        assertFalse(controller.visible)
        assertFalse(controller.isComposed)
        assertTrue(controller.canOpen)
        assertFalse(controller.isExiting)
    }

    @Test
    fun `open 后在场态 可见 子树保留 可开`() {
        val controller = newController()

        controller.open()

        assertTrue(controller.visible)
        assertTrue(controller.isComposed)
        assertTrue(controller.canOpen)
        assertFalse(controller.isExiting)
    }

    @Test
    fun `关闭瞬间子树仍在且可见性已翻 false`() {
        val controller = newController()
        controller.open()

        controller.beginExit()

        // 子树保留：离场段仍按原内容组合；可见性已翻 false，播组件内离场动画。
        assertTrue(controller.isComposed)
        assertFalse(controller.visible)
        assertTrue(controller.isExiting)
    }

    @Test
    fun `离场段拒收重复打开`() {
        val controller = newController()
        controller.open()
        controller.beginExit()

        assertFalse(controller.canOpen)
        // 离场段重复 beginExit 不改阶段，子树仍在。
        controller.beginExit()
        assertTrue(controller.isComposed)
        assertFalse(controller.visible)
    }

    @Test
    fun `动画收束后摘除子树`() {
        val controller = newController()
        controller.open()
        controller.beginExit()

        controller.finishExit()

        assertFalse(controller.isComposed)
        assertFalse(controller.visible)
        assertFalse(controller.isExiting)
        assertTrue(controller.canOpen)
    }

    @Test
    fun `空操作兜底 非在场开始离场不动状态 非离场收尾不迁移`() {
        val controller = newController()

        controller.beginExit()
        assertFalse(controller.isComposed)
        assertFalse(controller.visible)

        controller.open()
        controller.finishExit() // 在场段直接收尾：非离场段，空操作
        assertTrue(controller.isComposed)
        assertTrue(controller.visible)
    }

    @Test
    fun `收束后重开回到在场态并可见`() {
        val controller = newController()
        controller.open()
        controller.beginExit()
        controller.finishExit()

        controller.open()

        assertTrue(controller.visible)
        assertTrue(controller.canOpen)
        assertTrue(controller.isComposed)
    }
}
