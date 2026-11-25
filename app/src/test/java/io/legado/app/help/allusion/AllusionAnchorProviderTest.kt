package io.legado.app.help.allusion

import io.legado.app.help.chrono.ContextAnchor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * D4 典故 → 共享消歧总线接线验收（P04 / DEF-10）：
 * - 注册后典故词查询能产出 [ContextAnchor.Type.ALLUSION] 候选；
 * - 未注册时总线典故路为空，[ContextAnchor.resolve] 返回结果不含典故候选且不抛异常；
 * - 查无此典 / 单字 / 空串返回空列表，不塞假数据。
 *
 * 本类为宿主 JVM 纯逻辑测试，不依赖 android.Context。
 */
class AllusionAnchorProviderTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
        ContextAnchor.personProvider = null
        ContextAnchor.allusionProvider = null
    }

    @After
    fun tearDown() {
        // 总线为 object 单例：用例结束解绑，避免注册状态泄漏到其它测试类。
        ContextAnchor.allusionProvider = null
    }

    @Test
    fun `bind registers allusion candidate onto shared anchor bus`() {
        AllusionAnchorProvider.bindAnchorProvider()
        assertNotNull(ContextAnchor.allusionProvider)

        val candidates = ContextAnchor.allusionProvider!!.invoke("破釜沉舟")
        assertEquals(1, candidates.size)
        val c = candidates.single()
        assertEquals(ContextAnchor.Type.ALLUSION, c.type)
        assertEquals("破釜沉舟", c.name)
        assertEquals("seed:pofuchenzhou", c.id)
        // 条目无年代区间字段：start / end 如实留 null，不编造年份。
        assertNull(c.start)
        assertNull(c.end)
        assertEquals(emptyList<String>(), c.aliases)
        assertEquals(ContextAnchor.Confidence.MEDIUM, c.confidence)
        assertTrue(c.source.contains("《"))
        assertTrue(c.note.isNotBlank())
    }

    @Test
    fun `registered allusion candidate flows into resolve chain`() {
        AllusionAnchorProvider.bindAnchorProvider()
        val resolved = ContextAnchor.resolve("破釜沉舟", null)
        val allusion = resolved.firstOrNull { it.type == ContextAnchor.Type.ALLUSION }
        assertNotNull(allusion)
        assertEquals("破釜沉舟", allusion!!.name)
    }

    @Test
    fun `unregistered bus returns no allusion candidates and does not throw`() {
        // 不显式注册：allusionProvider 保持 null。
        assertNull(ContextAnchor.allusionProvider)

        // 未注册路径：resolve 照常跑完，不含典故候选，不抛异常。
        val resolved = ContextAnchor.resolve("破釜沉舟", null)
        assertTrue(resolved.none { it.type == ContextAnchor.Type.ALLUSION })
    }

    @Test
    fun `unknown word single char and blank query return empty without throwing`() {
        AllusionAnchorProvider.bindAnchorProvider()
        val provider = ContextAnchor.allusionProvider!!

        assertTrue(provider.invoke("桌子").isEmpty())
        assertTrue(provider.invoke("抽").isEmpty())
        assertTrue(provider.invoke("").isEmpty())
        assertTrue(provider.invoke("   ").isEmpty())
        // 超四字串不进典故索引。
        assertTrue(provider.invoke("破釜沉舟之计").isEmpty())
    }
}
