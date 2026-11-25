package io.legado.app.help.artifact

import io.legado.app.help.artifact.policy.ArtifactLoadStrategy
import io.legado.app.help.artifact.policy.CacheStatus
import io.legado.app.help.artifact.store.LandscapeStore
import io.legado.app.help.artifact.store.PlaceStore
import io.legado.app.help.bubble.contract.MISSING_SOURCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D16 名物库契约层纯 JVM 验收（只用 org.junit + java.*，不依赖 Android / Compose / 资源）。
 *
 * 对照锚点卡 `docs/锚点/D16-名物库.md` §三判据 1-4 与 §四兜底边界四条：
 * 十七类计数与逐字、schema 表名与 type 合法性、空数据回退字段齐全、同名多候选、
 * 未知 type 兜底、landscape 不落 artifact、断网缓存未命中标记、缺年份不过滤、分域 Store 分立。
 */
class ArtifactContractTest {

    @Test
    fun `十七类枚举清点为十七项`() {
        assertEquals(17, ArtifactCategory.entries.size)
    }

    @Test
    fun `类别名与锚点卡逐字一致`() {
        val expected = listOf(
            "人物", "地名", "草木", "鸟兽", "药名", "名剑", "名马", "名寺(古刹)", "名道观",
            "名楼(台阁)", "名琴(乐器)", "名鼎(礼器)", "星宿(天文)", "名酒(名食)",
            "名锦(名瓷/服饰)", "典故", "山川风物",
        )
        assertEquals(expected, ArtifactCategory.WIRES)
    }

    @Test
    fun `五张新表名与type取值逐字一致`() {
        val byName = ArtifactSchema.NEW_TABLES.associateBy { it.name }
        assertEquals(5, byName.size)
        assertEquals(
            listOf("sword", "horse", "instrument", "vessel", "textile", "vehicle", "seal", "jade", "armor"),
            byName.getValue("artifact").types,
        )
        assertEquals(
            listOf("temple", "daoist", "tower", "palace", "pavilion", "bridge", "tomb", "academy"),
            byName.getValue("architecture").types,
        )
        assertEquals(
            listOf("star", "constellation", "planet", "asterism", "calendar_term"),
            byName.getValue("astronomy").types,
        )
        assertEquals(
            listOf("wine", "tea", "dish", "snack", "ingredient"),
            byName.getValue("food").types,
        )
        assertEquals(
            listOf("mountain", "river", "lake", "sea", "waterfall", "cave", "boundary"),
            byName.getValue("landscape").types,
        )
    }

    @Test
    fun `合法type通过校验未知type返回false`() {
        assertTrue(ArtifactSchema.isValidType("artifact", "sword"))
        assertTrue(ArtifactSchema.isValidType("landscape", "river"))
        assertFalse(ArtifactSchema.isValidType("artifact", "river"))
        assertFalse(ArtifactSchema.isValidType("no_such_table", "sword"))
        assertFalse(ArtifactSchema.isValidType("food", "no_such_type"))
    }

    @Test
    fun `空数据回退气泡非null且字段齐全`() {
        val result = ArtifactFallback.notFound("龙泉剑", ArtifactCategory.SWORD, FallbackState.NO_DATA)
        val content = result.content
        assertEquals(FallbackState.NO_DATA, result.state)
        assertNotNull(content)
        assertEquals("龙泉剑", content.token)
        assertEquals("龙泉剑", content.headline)
        assertNull(content.phonetic)
        assertNotNull(content.body)
        assertNotNull(content.detail)
        assertTrue(content.actions.isEmpty())
        assertTrue(content.candidates.isEmpty())
        assertEquals(MISSING_SOURCE, content.source)
    }

    @Test
    fun `空实现覆盖全部十七类均返回完整未命中气泡`() {
        val provider = EmptyEntityProvider()
        ArtifactCategory.entries.forEach { category ->
            val content = provider.queryByCategory("测试名", category, null)
            assertNotNull(content)
            assertEquals(MISSING_SOURCE, content.source)
            assertTrue(content.actions.isEmpty())
            assertTrue(content.candidates.isEmpty())
            assertNotNull(content.body)
            assertNotNull(content.detail)
        }
    }

    @Test
    fun `同名多候选候选顺序与入参一致空候选退化为空壳`() {
        // B22 收口：构造器不接收默认下标；候选只保顺序与裸串，默认项由接线层 Candidate.isDefault 展开。
        val candidates = listOf("庐山 · 江西", "庐山 · 安徽别称")
        val ambiguousResult = ArtifactFallback.ambiguous("庐山", ArtifactCategory.LANDSCAPE, candidates)
        val content = ambiguousResult.content
        assertEquals(FallbackState.AMBIGUOUS, ambiguousResult.state)
        assertEquals(candidates, content.candidates)
        assertEquals(2, content.candidates.size)
        assertTrue(content.actions.isEmpty())
        assertEquals(MISSING_SOURCE, content.source)

        val degraded = ArtifactFallback.ambiguous("无名", ArtifactCategory.LANDSCAPE, emptyList()).content
        assertTrue(degraded.candidates.isEmpty())
        assertEquals(MISSING_SOURCE, degraded.source)
    }

    @Test
    fun `加载超时走兜底气泡非null且标记超时态`() {
        // B23 收口：纯 JVM 直接构造超时态，断言走兜底而非 null / 异常。
        assertEquals(CacheStatus.TIMEOUT, CacheStatus.valueOf("TIMEOUT"))
        assertEquals(FallbackState.TIMEOUT, FallbackState.valueOf("TIMEOUT"))

        val timeoutResult = ArtifactFallback.timeout("龙泉剑", ArtifactCategory.SWORD)
        val content = timeoutResult.content
        assertEquals(FallbackState.TIMEOUT, timeoutResult.state)
        assertNotNull(content)
        assertEquals("龙泉剑", content.token)
        assertEquals(MISSING_SOURCE, content.source)
        assertTrue(content.actions.isEmpty())
        assertTrue(content.candidates.isEmpty())
        assertNotNull(content.body)
        assertNotNull(content.detail)

        // 阈值单点：全包唯一超时阈值常量，值可回读，实现层不得另立第二份。
        assertEquals(5_000L, ArtifactLoadStrategy.LOAD_TIMEOUT_MS)
    }

    @Test
    fun `超时兜底与查无兜底状态及提示口径可区分五态同构回读`() {
        // B25 判据 4：纯 JVM 可区分「超时兜底」与「查无兜底」两条返回；判据 3：五态回读同构。
        val timeoutResult = ArtifactFallback.timeout("龙泉剑", ArtifactCategory.SWORD)
        val missingResult = ArtifactFallback.notFound("龙泉剑", ArtifactCategory.SWORD, FallbackState.NO_DATA)

        assertEquals(FallbackState.TIMEOUT, timeoutResult.state)
        assertEquals(FallbackState.NO_DATA, missingResult.state)
        assertTrue(timeoutResult.state != missingResult.state)
        assertEquals("加载超时，展示缓存内容", timeoutResult.fallbackMessage)
        assertEquals("查无此项", missingResult.fallbackMessage)
        assertTrue(timeoutResult.fallbackMessage != missingResult.fallbackMessage)

        // 五态同构：每一条兜底返回都经同一结果壳回读 state 与提示口径，不为超时单开特例。
        FallbackState.entries.forEach { state ->
            val result = ArtifactFallback.notFound("测试名", null, state)
            assertEquals(state, result.state)
            assertNotNull(result.content)
            assertTrue(result.fallbackMessage.isNotBlank())
        }
    }

    @Test
    fun `未知type查询不抛异常且走未命中兜底`() {
        val provider = EmptyEntityProvider()
        val content = provider.query("未知物", "no_such_type", null)
        assertNotNull(content)
        assertEquals(MISSING_SOURCE, content.source)
        assertTrue(content.candidates.isEmpty())
        assertTrue(content.actions.isEmpty())
    }

    @Test
    fun `landscape类型不落artifact表`() {
        ArtifactSchema.LANDSCAPE.types.forEach { landscapeType ->
            assertFalse(ArtifactSchema.ARTIFACT.isValidType(landscapeType))
        }
        assertEquals("landscape", ArtifactCategory.LANDSCAPE.schemaTable)
        assertFalse(ArtifactSchema.isValidType("artifact", "mountain"))
    }

    @Test
    fun `断网本地缓存未命中状态可标记`() {
        assertEquals(CacheStatus.OFFLINE_CACHE_MISS, CacheStatus.valueOf("OFFLINE_CACHE_MISS"))
        val offlineResult = ArtifactFallback.notFound("木莲", ArtifactCategory.PLANT, FallbackState.CACHE_MISS_OFFLINE)
        val content = offlineResult.content
        assertEquals(FallbackState.CACHE_MISS_OFFLINE, offlineResult.state)
        assertNotNull(content)
        assertEquals(MISSING_SOURCE, content.source)
    }

    @Test
    fun `缺年份锚点不过滤不抛异常`() {
        val provider = EmptyEntityProvider()
        val content = provider.query("白马寺", "temple", mapOf("year" to null))
        assertNotNull(content)
        assertEquals(MISSING_SOURCE, content.source)
        val yearMissing = ArtifactFallback.notFound("白马寺", ArtifactCategory.TEMPLE, FallbackState.YEAR_MISSING)
        assertEquals(FallbackState.YEAR_MISSING, yearMissing.state)
        assertNotNull(yearMissing.content)
    }

    @Test
    fun `山川与城邑分域接口分立`() {
        assertTrue(LandscapeStore::class.java.isInterface)
        assertTrue(PlaceStore::class.java.isInterface)
        assertFalse(LandscapeStore::class.java == PlaceStore::class.java)
        assertNotNull(LandscapeStore::class.java.methods.firstOrNull { it.name == "queryByName" })
        assertNotNull(PlaceStore::class.java.methods.firstOrNull { it.name == "queryByName" })
    }
}
