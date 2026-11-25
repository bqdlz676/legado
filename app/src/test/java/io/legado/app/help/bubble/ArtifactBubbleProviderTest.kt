package io.legado.app.help.bubble

import io.legado.app.help.artifact.ArtifactCategory
import io.legado.app.help.artifact.ArtifactFallback
import io.legado.app.help.artifact.ArtifactSlots
import io.legado.app.help.artifact.EmptyEntityProvider
import io.legado.app.help.artifact.EntityProvider
import io.legado.app.help.artifact.FallbackState
import io.legado.app.help.bubble.contract.BubbleContent as ContractContent
import io.legado.app.help.bubble.contract.Action as ContractAction
import io.legado.app.help.bubble.contract.Kind
import io.legado.app.help.bubble.contract.MISSING_SOURCE
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P06 名物适配层纯 JVM 验收（不依赖 Android / Compose / 资源）。
 *
 * 覆盖：装配处实例化、五态兜底回读、空数据下钻 null、契约到既有气泡映射、异常兜底、
 * 既有槽三类不重复查询（去重防双轨）、解析链槽位顺序、先命中优先不重复出气泡。
 */
class ArtifactBubbleProviderTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
    }

    // ---------- 桩件 ----------

    private class StubEntity(
        private val hit: ContractContent? = null,
        private val recorded: MutableList<String>? = null,
        private val throwOnCall: Boolean = false,
    ) : EntityProvider {
        override fun query(name: String, type: String, anchor: Any?): ContractContent {
            recorded?.add(type)
            if (throwOnCall) throw IllegalStateException("boom")
            return hit ?: ArtifactFallback.notFound(name, null, FallbackState.NO_DATA).content
        }
    }

    private class Stub(
        private val tag: String,
        private val record: MutableList<String>? = null,
        private val result: BubbleContent? = null,
    ) : BubbleProvider {
        override fun query(token: String, anchor: ContextAnchor): BubbleContent? = strike()
        override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike()
        override fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike()
        override fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike()

        private fun strike(): BubbleContent? {
            record?.add(tag)
            return result
        }
    }

    private fun contractContent(
        kind: Kind,
        token: String,
        candidates: List<String> = emptyList(),
        actions: List<ContractAction> = emptyList(),
        source: String = "seed:test",
    ) = ContractContent(
        kind = kind,
        token = token,
        phonetic = null,
        headline = token,
        body = "body",
        detail = "detail",
        actions = actions,
        candidates = candidates,
        source = source,
    )

    private fun legacyContent(kind: String, token: String) = BubbleContent(
        kind = kind,
        token = token,
        phonetic = null,
        headline = token,
        body = "body",
        detail = "detail",
        actions = emptyList(),
        candidates = emptyList(),
        source = "test",
    )

    // ---------- 装配 ----------

    @Test
    fun `默认装配实例化名物适配提供者并注入空数据实现`() {
        val artifactField = BubbleResolverImpl::class.java.getDeclaredField("artifact")
        artifactField.isAccessible = true
        val artifact = artifactField.get(DefaultBubbleResolver)
        assertTrue(artifact is ArtifactBubbleProvider)

        val entityField = ArtifactBubbleProvider::class.java.getDeclaredField("entity")
        entityField.isAccessible = true
        assertTrue(entityField.get(artifact) is EmptyEntityProvider)
    }

    @Test
    fun `解析链地名之后多字词之前查询名物槽`() {
        val record = mutableListOf<String>()
        val resolver = BubbleResolverImpl(
            date = Stub("date", record),
            allusion = Stub("allusion", record),
            person = Stub("person", record),
            place = Stub("place", record),
            word = Stub("word", record),
            char = Stub("char", record),
            missing = MissingProvider,
            artifact = Stub("artifact", record),
        )
        resolver.resolve("鲁隐公元年，晋公子重耳出奔。", 1)
        assertEquals(
            listOf("date", "allusion", "person", "place", "artifact", "word", "char"),
            record,
        )
    }

    // ---------- 兜底五态 ----------

    @Test
    fun `五态兜底结果壳状态文案与气泡字段齐全`() {
        val adapter = ArtifactBubbleProvider(EmptyEntityProvider())
        FallbackState.entries.forEach { state ->
            val result = adapter.fallbackResult("龙泉剑", ArtifactCategory.SWORD, state)
            assertEquals(state, result.state)
            assertNotNull(result.content)
            assertEquals(MISSING_SOURCE, result.content.source)
            assertTrue(result.fallbackMessage.isNotBlank())
            assertTrue(result.content.actions.isEmpty())
            assertTrue(result.content.candidates.isEmpty())
        }
        assertEquals(
            "查无此项",
            adapter.fallbackResult("龙泉剑", ArtifactCategory.SWORD, FallbackState.NO_DATA).fallbackMessage,
        )
        assertEquals(
            "加载超时，展示缓存内容",
            adapter.fallbackResult("龙泉剑", ArtifactCategory.SWORD, FallbackState.TIMEOUT).fallbackMessage,
        )
    }

    @Test
    fun `空数据兜底壳不作为命中返回null下钻`() {
        val adapter = ArtifactBubbleProvider(EmptyEntityProvider())
        assertNull(adapter.query("龙泉剑", ContextAnchor))
        assertNull(adapter.hitAt("胯下赤兔马日行千里", 2, ContextAnchor))
    }

    @Test
    fun `实体提供者抛异常被兜住返回null不冒泡`() {
        val adapter = ArtifactBubbleProvider(StubEntity(throwOnCall = true))
        assertNull(adapter.query("赤兔马", ContextAnchor))
        assertNull(adapter.hitAt("胯下赤兔马", 2, ContextAnchor))
    }

    // ---------- 映射与去重 ----------

    @Test
    fun `真实命中映射回既有气泡契约动作候选收敛`() {
        val hit = contractContent(
            Kind.WORD,
            "龙泉剑",
            candidates = listOf("龙泉", "龙渊"),
            actions = listOf(ContractAction.OpenMap("P-LONGQUAN")),
        )
        val adapter = ArtifactBubbleProvider(StubEntity(hit = hit))
        val bubble = adapter.query("龙泉剑", ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.WORD, bubble!!.kind)
        assertEquals("龙泉剑", bubble.token)
        assertEquals(2, bubble.candidates.size)
        assertEquals("龙泉", bubble.candidates.first().query)
        val mapAction = bubble.actions.filterIsInstance<Action.OpenMap>().singleOrNull()
        assertNotNull(mapAction)
        assertEquals("P-LONGQUAN", mapAction!!.placeId)
    }

    @Test
    fun `契约名物动作在既有链无路由时丢弃不抛异常`() {
        val hit = contractContent(
            Kind.WORD,
            "龙泉剑",
            actions = listOf(ContractAction.OpenArtifact("art-1", "sword")),
        )
        val adapter = ArtifactBubbleProvider(StubEntity(hit = hit))
        val bubble = adapter.query("龙泉剑", ContextAnchor)
        assertNotNull(bubble)
        assertTrue(bubble!!.actions.isEmpty())
    }

    @Test
    fun `人物地名典故类别不在实体查询类型清单`() {
        val recorded = mutableListOf<String>()
        val adapter = ArtifactBubbleProvider(StubEntity(recorded = recorded))
        adapter.query("孔子", ContextAnchor)
        adapter.query("绛", ContextAnchor)
        adapter.query("退避三舍", ContextAnchor)
        val allowed = ArtifactSlots.entitySlots.mapNotNull { it.schemaType }.toSet()
        recorded.forEach { assertTrue(allowed.contains(it)) }
        assertTrue(!recorded.contains("person"))
        assertTrue(!recorded.contains("place"))
        assertTrue(!recorded.contains("allusion"))
        assertEquals(ArtifactSlots.entitySlots.size * 3, recorded.size)
    }

    @Test
    fun `既有槽先命中名物槽不重复出气泡`() {
        val personHit = legacyContent(BubbleKind.PERSON, "重耳")
        val artifactHit = legacyContent(BubbleKind.WORD, "赤兔")
        val resolver = BubbleResolverImpl(
            date = Stub("date"),
            allusion = Stub("allusion"),
            person = Stub("person", result = personHit),
            place = Stub("place"),
            word = Stub("word"),
            char = Stub("char"),
            missing = MissingProvider,
            artifact = Stub("artifact", result = artifactHit),
        )
        val bubble = resolver.resolve("晋公子重耳之亡", 4)
        assertSame(personHit, bubble)
    }

    @Test
    fun `整词查询链同样经过名物槽且兜底下钻`() {
        val resolver = BubbleResolverImpl(
            date = Stub("date"),
            allusion = Stub("allusion"),
            person = Stub("person"),
            place = Stub("place"),
            word = Stub("word"),
            char = Stub("char"),
            missing = MissingProvider,
            artifact = ArtifactBubbleProvider(EmptyEntityProvider()),
        )
        assertNull(resolver.queryToken("龙泉剑", ContextAnchor))
    }
}
