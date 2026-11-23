package io.legado.app.ui.autoTask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C2R6 · 自动任务列表行"最近运行时间"文案单测。
 *
 * 覆盖：有真实执行记录 → 走既有格式器；无记录（0 / 负数）→ "未运行"，
 * 不允许空白、破折号或 1970 时间戳。格式器以桩函数注入，保持纯 JVM 可测。
 */
class AutoTaskLastRunLabelTest {

    private var formatterCalled = false
    private val stubFormat: (Long) -> String = { ts ->
        formatterCalled = true
        "stub:$ts"
    }

    @Test
    fun `record present passes timestamp through existing formatter`() {
        val timestamp = 1_737_696_000_000L

        val label = formatLastRunLabel(timestamp, stubFormat)

        assertEquals("stub:$timestamp", label)
        assertTrue(formatterCalled)
    }

    @Test
    fun `never run with zero timestamp shows 未运行 and skips formatter`() {
        val label = formatLastRunLabel(0L, stubFormat)

        assertEquals(LAST_RUN_NEVER, label)
        assertFalse("无记录时不应调用时间格式器", formatterCalled)
    }

    @Test
    fun `negative timestamp is treated as never run`() {
        val label = formatLastRunLabel(-1L, stubFormat)

        assertEquals(LAST_RUN_NEVER, label)
        assertFalse("非法时间戳不应调用时间格式器", formatterCalled)
    }
}
