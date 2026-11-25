package io.legado.app.ui.components.compat

import io.legado.app.ui.components.ComponentVisualState as LegacyVisualState
import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-04 六态接线 Compat 单向映射单测（纯 JVM，只用 org.junit + java.*，不引 Compose / Android 类型）。
 *
 * 对账口径：旧七态 → 新六态逐态断言；EMPTY / OFFLINE 归并 DISABLED 且事件不放行；
 * DISABLED / LOADING 事件不放行；七态全量映射不抛异常，结果均为契约已登记态，透明度落在 [0,1]。
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
    fun `空数据态映射到禁用态`() {
        assertEquals(ComponentVisualState.DISABLED, LegacyVisualState.EMPTY.toVisualState())
    }

    @Test
    fun `离线态映射到禁用态`() {
        assertEquals(ComponentVisualState.DISABLED, LegacyVisualState.OFFLINE.toVisualState())
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
    fun `空数据与离线映射后事件不放行`() {
        assertFalse(LegacyVisualState.EMPTY.toVisualState().eventsAllowed)
        assertFalse(LegacyVisualState.OFFLINE.toVisualState().eventsAllowed)
    }

    @Test
    fun `七态全量映射不抛异常且结果均为契约已登记态`() {
        val registered = ComponentVisualState.entries.toSet()
        LegacyVisualState.entries.forEach { legacy ->
            val mapped = legacy.toVisualState()
            assertTrue("映射结果未登记在契约六态", mapped in registered)
            assertTrue("透明度越界", mapped.contentAlpha in 0f..1f)
        }
    }
}
