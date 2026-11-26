package io.legado.app.ui.components.compat

import io.legado.app.ui.components.ComponentVisualState as LegacyVisualState
import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-04 状态收口 Compat 过渡身份扩展单测（纯 JVM，只用 org.junit + java.*，不引 Compose / Android 类型）。
 *
 * B1-件：旧七态枚举定义已删除，[LegacyVisualState] 为指向契约枚举的同名别名；`toVisualState()`
 * 现为身份函数，不再做档位搬运。对账口径：逐态断言入参出参同为契约档；EMPTY / OFFLINE 各自独立，
 * 不并入 DISABLED；EMPTY 不压淡且允许事件，OFFLINE 不压淡但禁止事件；DISABLED / LOADING 事件不放行；
 * 全量档位结果均登记在契约枚举，透明度落在 [0,1]。
 */
class ComponentStateCompatTest {

    @Test
    fun `可交互三态同名平移到契约态`() {
        assertEquals(ComponentVisualState.DEFAULT, LegacyVisualState.DEFAULT.toVisualState())
        assertEquals(ComponentVisualState.HOVERED, LegacyVisualState.HOVERED.toVisualState())
        assertEquals(ComponentVisualState.PRESSED, LegacyVisualState.PRESSED.toVisualState())
    }

    @Test
    fun `禁用与加载同名平移`() {
        assertEquals(ComponentVisualState.DISABLED, LegacyVisualState.DISABLED.toVisualState())
        assertEquals(ComponentVisualState.LOADING, LegacyVisualState.LOADING.toVisualState())
    }

    @Test
    fun `空数据态映射到独立空数据档位 不并入禁用`() {
        assertEquals(ComponentVisualState.EMPTY, LegacyVisualState.EMPTY.toVisualState())
    }

    @Test
    fun `离线态映射到独立离线档位 不并入禁用`() {
        assertEquals(ComponentVisualState.OFFLINE, LegacyVisualState.OFFLINE.toVisualState())
    }

    @Test
    fun `禁用态事件不放行`() {
        assertFalse(LegacyVisualState.DISABLED.toVisualState().eventsAllowed)
    }

    @Test
    fun `加载态事件不放行`() {
        assertFalse(LegacyVisualState.LOADING.toVisualState().eventsAllowed)
    }

    @Test
    fun `空数据不压淡且允许事件 离线不压淡但禁止事件`() {
        val empty = LegacyVisualState.EMPTY.toVisualState()
        val offline = LegacyVisualState.OFFLINE.toVisualState()

        assertEquals(1f, empty.contentAlpha, 1e-6f)
        assertTrue(empty.eventsAllowed)

        assertEquals(1f, offline.contentAlpha, 1e-6f)
        assertFalse(offline.eventsAllowed)
    }

    @Test
    fun `七态全量映射不抛异常且结果均为契约已登记态`() {
        val registered = ComponentVisualState.entries.toSet()
        LegacyVisualState.entries.forEach { legacy ->
            val mapped = legacy.toVisualState()
            assertTrue("映射结果未登记在契约状态枚举", mapped in registered)
            assertTrue("透明度越界", mapped.contentAlpha in 0f..1f)
        }
    }
}
