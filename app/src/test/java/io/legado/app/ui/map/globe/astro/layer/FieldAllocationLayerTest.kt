package io.legado.app.ui.map.globe.astro.layer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 分野联动层纯 JVM 单测（D39 反向链路；§十一.2、§十一.5）。
 *
 * 覆盖：州域→codes 主表反查（异说剔除）、codes→州域、focusRegion 空 codes 不写选中态、
 * 多宿成员并集、focusRegion 命中起脉冲。纯 JVM：只用 org.junit，不碰 Android / Canvas。
 */
class FieldAllocationLayerTest {

    private companion object {
        const val NOW_MS: Long = 1_000L
    }

    private fun fixtureLayer(): FieldAllocationLayer = FieldAllocationLayer().apply {
        loadEntries(
            listOf(
                FieldEntry("jing", "雍州", "秦", "《史记·天官书》", false),
                FieldEntry("gui", "雍州", "秦", "《史记·天官书》", false),
                FieldEntry("yi", "雍州", null, "一作异说", true),
            ),
        )
    }

    @Test
    fun `州域反查codes 异说条目不进主表`() {
        val layer = fixtureLayer()
        // 改坏推演：若过滤漏了 !it.isVariant，返回列表会混入异说 code "yi"，
        // 断言在下面这条 assertEquals 列表相等行变红。
        assertEquals(listOf("jing", "gui"), layer.asterismCodesForRegion("雍州"))
    }

    @Test
    fun `codes正向反查州域 空列表未知code异说均回落null`() {
        val layer = fixtureLayer()
        assertEquals("雍州", layer.regionForCodes(listOf("jing", "gui")))
        // 改坏推演：若 firstNotNullOfOrNull 漏判异说，异说 code "yi" 用例在此行变红。
        assertNull(layer.regionForCodes(emptyList()))
        assertNull(layer.regionForCodes(listOf("no_such_code")))
        assertNull(layer.regionForCodes(listOf("yi")))
    }

    @Test
    fun `focusRegion空codes 不写选中态不起脉冲`() {
        val layer = FieldAllocationLayer()
        // 改坏推演：若空分支仍调 selectRegion，selectedRegion 非 null，
        // 下面 selectedRegion 断言行与 pulseStartMs 断言行依次变红。
        val codes = layer.focusRegion("雍州", NOW_MS)
        assertEquals(emptyList<String>(), codes)
        assertNull(layer.selectedRegion)
        assertEquals(0L, layer.pulseStartMs)
    }

    @Test
    fun `focusRegion未知州域 保持原选中态不清空`() {
        val layer = fixtureLayer()
        layer.selectRegion("豫州", 500L)
        val codes = layer.focusRegion("益州", NOW_MS)
        assertEquals(emptyList<String>(), codes)
        // 改坏推演：若空分支清空选中态，selectedRegion 断言在此行变红。
        assertEquals("豫州", layer.selectedRegion)
        assertEquals(500L, layer.pulseStartMs)
    }

    @Test
    fun `多宿成员星按并集归集 去重保留全部成员`() {
        val layer = fixtureLayer()
        layer.loadMembers(mapOf("jing" to listOf(1L, 2L), "gui" to listOf(2L, 3L)))
        // 改坏推演：若 flatMap 漏并第二个 code，成员集合缺 3L，下面集合相等行变红。
        assertEquals(setOf(1L, 2L, 3L), layer.memberIdsForRegion("雍州").toSet())
    }

    @Test
    fun `focusRegion命中主表 记选中州域并起算脉冲`() {
        val layer = fixtureLayer()
        val codes = layer.focusRegion("雍州", NOW_MS)
        assertEquals(listOf("jing", "gui"), codes)
        assertEquals("雍州", layer.selectedRegion)
        assertEquals(NOW_MS, layer.pulseStartMs)
    }
}
