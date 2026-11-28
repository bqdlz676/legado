package io.legado.app.ui.map.globe.astro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 星点 ↔ 分区 code 双向反查索引纯 JVM 单测（判据 3 · N1；§十一 双向闭环）。
 *
 * 覆盖：code → 成员集合非空、成员逐颗回查同一 code（双向一致）、未知 code / 未知星回落、
 * 成员去重、空映射。纯 JVM：只用 org.junit，不碰 Android / Canvas。
 */
class SkyNameIndexTest {

    private fun fixtureIndex(): SkyNameIndex = SkyNameIndex.build(
        mapOf(
            "jing" to listOf(30270L, 30271L, 30270L),
            "gui" to listOf(30280L, 30281L),
        ),
    )

    @Test
    fun `code反查成员集合非空 成员逐颗回查同一code`() {
        val index = fixtureIndex()
        // 改坏推演：若反向映射漏建，codeOf 返回 null，下面两条 assertEquals 变红。
        assertEquals(listOf(30270L, 30271L), index.membersOf("jing"))
        assertEquals("jing", index.codeOf(30270L))
        assertEquals("jing", index.codeOf(30271L))
        assertEquals("gui", index.codeOf(30280L))
        assertEquals("gui", index.codeOf(30281L))
    }

    @Test
    fun `双向一致 集合内每颗星反查归属code不串宿`() {
        val index = fixtureIndex()
        index.codes().forEach { code ->
            index.membersOf(code).forEach { starId ->
                // 改坏推演：若同一星被归到两个 code 或回查错位，这里断言变红。
                assertEquals(code, index.codeOf(starId))
            }
        }
    }

    @Test
    fun `未知code返回空列表 未知星返回null`() {
        val index = fixtureIndex()
        assertTrue(index.membersOf("no_such_code").isEmpty())
        assertNull(index.codeOf(999999L))
    }

    @Test
    fun `重复成员去重 不产生重复主键`() {
        val index = fixtureIndex()
        assertEquals(listOf(30270L, 30271L), index.membersOf("jing"))
        assertEquals(4, index.size())
    }

    @Test
    fun `空映射构建空索引 查询恒空不崩`() {
        val index = SkyNameIndex.build(emptyMap())
        assertTrue(index.codes().isEmpty())
        assertTrue(index.membersOf("jing").isEmpty())
        assertNull(index.codeOf(1L))
        assertEquals(0, index.size())
    }

    @Test
    fun `empty工厂与空构建等价`() {
        assertEquals(SkyNameIndex.empty().codes(), SkyNameIndex.build(emptyMap()).codes())
    }
}
