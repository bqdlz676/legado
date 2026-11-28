package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeController
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 悬停 120ms 回弹单验收（D 件 · §七.2）。
 *
 * 纯 JVM：注入可控时间源，不碰 Compose / Android。覆盖两条边界——120ms 内无操作保持悬停
 * 不回落；超过 120ms 无新操作，悬停态回落（hoverId 归 -1），无残留。
 *
 * 每条用例注释写明「改坏会红在哪一行」。
 */
class SkyInputHandlerHoverTest {

    private lateinit var handler: SkyInputHandler
    private var now = 0L

    @Before
    fun setUp() {
        now = 1_000L
        val controller = GlobeController()
        handler = SkyInputHandler(controller, SkyCanvasRenderer, clock = { now })
        SkyCanvasRenderer.hoverId = -1L
        SkyCanvasRenderer.pressId = -1L
    }

    @Test
    fun `一百二十毫秒内无操作悬停保持不回落`() {
        handler.markHover(1001L)
        // 推进 100ms，仍在 120ms 阈值内：悬停态不得被节拍清掉。
        now += 100L
        handler.tickHover()
        // 改坏：若节拍把未超时悬停也回落，下一行 hoverId 断言变红。
        assertEquals(1001L, SkyCanvasRenderer.hoverId)
    }

    @Test
    fun `超过一百二十毫秒无操作悬停回落无残留`() {
        handler.markHover(1002L)
        // 推进 121ms，越过 120ms 阈值：悬停态必须回落。
        now += 121L
        handler.tickHover()
        // 改坏：若超时后仍保留悬停星，下一行 -1L 断言变红。
        assertEquals(-1L, SkyCanvasRenderer.hoverId)
    }
}
