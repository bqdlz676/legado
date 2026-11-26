package io.legado.app.help.artifact.provider

import io.legado.app.help.artifact.ArtifactCategory
import io.legado.app.help.artifact.entry.ArtifactEntry
import io.legado.app.help.artifact.policy.ArtifactLoadStrategy
import io.legado.app.help.artifact.policy.CacheStatus
import io.legado.app.help.artifact.registry.ArtifactRegistry
import io.legado.app.help.bubble.contract.Action
import io.legado.app.help.bubble.contract.MISSING_SOURCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RegistryEntityProvider 纯 JVM 验收（只用 org.junit + kotlin stdlib，不依赖 Android / Compose / 资源）。
 *
 * 改坏会红的核心断言：
 *  - 注册表缺类 / 资产损坏 / 查不到 → 未收录气泡，不抛异常、不返回 null；
 *  - 路由 type 错 → 不串到别的类；
 *  - 既有槽类别不进通用实体链（防双轨）；
 *  - 同名多条取首条并标疑似；动作挂点 OpenArtifact / OpenMap 就位但不接 UI。
 */
class RegistryEntityProviderTest {

    /** 测试用种子源：按登记 id 注入指定条目。 */
    private class FakeSource(
        private val map: Map<String, List<ArtifactEntry>> = emptyMap(),
    ) : ArtifactSeedSource {
        override fun entriesFor(registrationId: String): List<ArtifactEntry> =
            map[registrationId].orEmpty()
    }

    /** 抛异常种子源：模拟表缺失 / 资产损坏 / 解析崩。 */
    private class CorruptSource : ArtifactSeedSource {
        override fun entriesFor(registrationId: String): List<ArtifactEntry> =
            throw IllegalStateException("asset corrupt: $registrationId")
    }

    /** 记录加载调用的懒加载策略。 */
    private class RecordingStrategy : ArtifactLoadStrategy {
        val loaded = mutableSetOf<ArtifactCategory>()
        override fun isLoaded(category: ArtifactCategory): Boolean = category in loaded
        override fun loadIfNeeded(category: ArtifactCategory) { loaded += category }
        override fun cacheStatus(category: ArtifactCategory): CacheStatus = CacheStatus.MISS
    }

    private fun seedEntry(id: String, name: String, type: String, relatedPlaceId: String? = null) =
        ArtifactEntry(
            id = id, name = name, type = type,
            headline = "$name · 测试条", body = "$name 测试一句话。", detail = "$name 测试详文。",
            aliases = emptyList(), source = "测试来源",
            relatedPlaceId = relatedPlaceId, relatedArtifactIds = emptyList(),
            longitude = null, latitude = null,
        )

    @Test
    fun `注册表缺类查询返回未收录气泡不抛异常`() {
        val provider = RegistryEntityProvider()
        val content = provider.query("测试名", "no_such_type", null)
        assertNotNull(content)
        assertEquals(MISSING_SOURCE, content.source)
        assertTrue(content.candidates.isEmpty())
        assertTrue(content.actions.isEmpty())
        assertTrue(content.body.isEmpty())
    }

    @Test
    fun `路由type错不串到别的类`() {
        val provider = RegistryEntityProvider()
        // 赤兔是名马种子；用名剑 type 查必须未命中，不得串到 horse 类。
        val cross = provider.query("赤兔", "sword", null)
        assertEquals(MISSING_SOURCE, cross.source)
        val hit = provider.query("赤兔", "horse", null)
        assertTrue(hit.source != MISSING_SOURCE)
        assertEquals("赤兔", hit.token)
        // 反向：龙泉是名剑种子；用名马 type 查同样未命中。
        val reverse = provider.query("龙泉", "horse", null)
        assertEquals(MISSING_SOURCE, reverse.source)
    }

    @Test
    fun `既有槽人物地名走通用提供者返回兜底壳防双轨`() {
        val provider = RegistryEntityProvider()
        val person = provider.query("曹操", "person", null)
        val place = provider.query("洛阳", "place", null)
        assertEquals(MISSING_SOURCE, person.source)
        assertEquals(MISSING_SOURCE, place.source)
        assertTrue(person.actions.isEmpty())
        assertTrue(place.actions.isEmpty())
    }

    @Test
    fun `资产损坏返回未收录气泡不抛异常`() {
        val provider = RegistryEntityProvider(seedSource = CorruptSource())
        val content = provider.query("白马寺", "temple", null)
        assertNotNull(content)
        assertEquals(MISSING_SOURCE, content.source)
        assertTrue(content.actions.isEmpty())
    }

    @Test
    fun `空名称查询返回兜底气泡不抛异常`() {
        val provider = RegistryEntityProvider()
        val blank = provider.query("   ", "temple", null)
        assertNotNull(blank)
        assertEquals(MISSING_SOURCE, blank.source)
    }

    @Test
    fun `命中种子返回最小气泡字段齐全且挂详情动作`() {
        val provider = RegistryEntityProvider()
        val content = provider.query("白马寺", "temple", null)
        assertNotNull(content)
        assertTrue(content.source != MISSING_SOURCE)
        assertTrue(content.headline.isNotBlank())
        assertTrue(content.body.isNotBlank())
        assertTrue(content.detail.isNotBlank())
        val openArtifact = content.actions.filterIsInstance<Action.OpenArtifact>()
        assertEquals(1, openArtifact.size)
        assertEquals("temple-baimasi", openArtifact.first().artifactId)
        assertEquals("temple", openArtifact.first().type)
    }

    @Test
    fun `名寺条目挂地名地图动作二级跳转挂点`() {
        val provider = RegistryEntityProvider()
        val temple = provider.query("白马寺", "temple", null)
        val openMap = temple.actions.filterIsInstance<Action.OpenMap>()
        assertEquals(1, openMap.size)
        assertEquals("place-baimasi", openMap.first().placeId)

        // 无关联地名的条目不挂地图按钮，不造占位地名。
        val sword = provider.query("龙泉", "sword", null)
        assertTrue(sword.actions.filterIsInstance<Action.OpenMap>().isEmpty())
    }

    @Test
    fun `同名多条取首条并标疑似候选回读`() {
        val duplicates = listOf(
            seedEntry("landscape-lushan-a", "庐山", "mountain", relatedPlaceId = "place-lushan-a"),
            seedEntry("landscape-lushan-b", "庐山", "mountain", relatedPlaceId = "place-lushan-b"),
        )
        val provider = RegistryEntityProvider(seedSource = FakeSource(mapOf("landscape" to duplicates)))
        val content = provider.query("庐山", "mountain", null)
        assertTrue(content.source != MISSING_SOURCE)
        assertEquals("landscape-lushan-a", content.actions.filterIsInstance<Action.OpenArtifact>().first().artifactId)
        assertEquals(2, content.candidates.size)
        assertTrue(content.body.contains("疑似"))
    }

    @Test
    fun `实体链每类种子均可被本类type命中至少一条`() {
        val provider = RegistryEntityProvider()
        ArtifactRegistry.entityRegistrations.forEach { registration ->
            val seeds = io.legado.app.help.artifact.seed.ArtifactSeedTable.forRegistration(registration.id)
            assertTrue("${registration.id} 种子不应为空", seeds.isNotEmpty())
            val first = seeds.first()
            val content = provider.query(first.name, registration.schemaType ?: registration.id, null)
            assertTrue(
                "${registration.id} 首条「${first.name}」应被本类 type 命中",
                content.source != MISSING_SOURCE,
            )
            assertEquals(first.name, content.token)
        }
    }

    @Test
    fun `懒加载策略对未驻留类别触发按需加载`() {
        val strategy = RecordingStrategy()
        val provider = RegistryEntityProvider(loadStrategy = strategy)
        assertTrue(strategy.loaded.isEmpty())
        provider.query("桃", "plant", null)
        assertTrue(strategy.loaded.contains(ArtifactCategory.PLANT))
    }

    @Test
    fun `未登记id直查返回未收录兜底不抛异常`() {
        val provider = RegistryEntityProvider()
        val content = provider.queryByRegistration("测试名", "no_such_registration", null)
        assertNotNull(content)
        assertEquals(MISSING_SOURCE, content.source)
    }
}
