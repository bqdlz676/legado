package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 硬件键 / 媒体键 → 翻页命令解析单测（纯 JVM，离线可跑）。
 *
 * 覆盖：
 *  - 方向键 / 翻页键 / 空格 / 音量键的上下翻映射；
 *  - 音量键总开关、朗读播放中让位、长按连发规则；
 *  - 用户自定义键码；媒体上一曲 / 下一曲 / 播放暂停 / 线控键；
 *  - 未识别键返回 null，交系统默认处理。
 */
class InkPageCommandTest {

    private val defaultConfig = PageKeyConfig.DEFAULT

    @Test
    fun `方向上 左 翻页上 映射上翻`() {
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_UP, 0, defaultConfig),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_LEFT, 0, defaultConfig),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_PAGE_UP, 0, defaultConfig),
        )
    }

    @Test
    fun `方向下 右 翻页下 空格 映射下翻`() {
        assertEquals(
            PageCommand.Turn(EdgeDirection.Next),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_DOWN, 0, defaultConfig),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Next),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_RIGHT, 0, defaultConfig),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Next),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_PAGE_DOWN, 0, defaultConfig),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Next),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_SPACE, 0, defaultConfig),
        )
    }

    @Test
    fun `音量上翻上一页 音量下翻下一页`() {
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_VOLUME_UP, 0, defaultConfig),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Next),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_VOLUME_DOWN, 0, defaultConfig),
        )
    }

    @Test
    fun `音量键总开关关闭时音量键不翻页 方向键仍可用`() {
        val config = defaultConfig.copy(volumeKeyEnabled = false)
        assertNull(InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_VOLUME_UP, 0, config))
        assertNull(InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_VOLUME_DOWN, 0, config))
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_UP, 0, config),
        )
    }

    @Test
    fun `朗读播放中未开播放时翻页 音量键让位朗读`() {
        val playingConfig = defaultConfig.copy(
            readAloudPlaying = true,
            volumeKeyPageOnPlay = false,
        )
        assertNull(InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_VOLUME_UP, 0, playingConfig))
        assertNull(InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_VOLUME_DOWN, 0, playingConfig))

        val allowOnPlay = playingConfig.copy(volumeKeyPageOnPlay = true)
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_VOLUME_UP, 0, allowOnPlay),
        )
    }

    @Test
    fun `未开长按翻页时连发重复键被忽略 首次按下仍生效`() {
        val noLongPress = defaultConfig.copy(keyPageOnLongPress = false)
        assertNull(InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_UP, 1, noLongPress))
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_UP, 0, noLongPress),
        )

        val longPressAllowed = defaultConfig.copy(keyPageOnLongPress = true)
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_UP, 2, longPressAllowed),
        )
    }

    @Test
    fun `用户自定义上下翻键码生效`() {
        val config = defaultConfig.copy(
            prevCustomKeys = setOf(50),
            nextCustomKeys = setOf(51),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(50, 0, config),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Next),
            InkPageKeys.resolveHardwareKey(51, 0, config),
        )
    }

    @Test
    fun `未识别硬件键返回 null`() {
        assertNull(InkPageKeys.resolveHardwareKey(KeyEventUnknown, 0, defaultConfig))
    }

    @Test
    fun `媒体上一曲上翻 下一曲下翻`() {
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveMediaKey(InkPageKeys.KEYCODE_MEDIA_PREVIOUS),
        )
        assertEquals(
            PageCommand.Turn(EdgeDirection.Next),
            InkPageKeys.resolveMediaKey(InkPageKeys.KEYCODE_MEDIA_NEXT),
        )
    }

    @Test
    fun `媒体播放暂停与线控键为朗读切换`() {
        assertEquals(
            PageCommand.ToggleReadAloud,
            InkPageKeys.resolveMediaKey(InkPageKeys.KEYCODE_MEDIA_PLAY_PAUSE),
        )
        assertEquals(
            PageCommand.ToggleReadAloud,
            InkPageKeys.resolveMediaKey(InkPageKeys.KEYCODE_HEADSETHOOK),
        )
    }

    @Test
    fun `其余媒体键不消费`() {
        assertNull(InkPageKeys.resolveMediaKey(90)) // MEDIA_FAST_FORWARD
        assertNull(InkPageKeys.resolveMediaKey(0))  // KEYCODE_UNKNOWN
    }

    @Test
    fun `翻页命令方向与手势分区 EdgeDirection 同口径`() {
        val turn = InkPageKeys.resolveMediaKey(InkPageKeys.KEYCODE_MEDIA_PREVIOUS) as PageCommand.Turn
        assertTrue(turn.direction == EdgeDirection.Prev)
    }

    @Test
    fun `负连发计数按首次按下处理 不进连发屏蔽`() {
        val noLongPress = defaultConfig.copy(keyPageOnLongPress = false)
        assertEquals(
            PageCommand.Turn(EdgeDirection.Prev),
            InkPageKeys.resolveHardwareKey(InkPageKeys.KEYCODE_DPAD_UP, -3, noLongPress),
        )
    }

    private companion object {
        const val KeyEventUnknown = 0
    }
}
