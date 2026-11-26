package io.legado.app.help.artifact.registry

import io.legado.app.help.artifact.ArtifactRoute
import io.legado.app.help.artifact.seed.ArtifactSeedTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D16 十七类注册表纯 JVM 验收（只用 org.junit + kotlin stdlib，不依赖 Android / Compose / 资源）。
 *
 * 对照 F-07 定稿名单（`docs/口径冻结-2026-11-24-15点后.md:20`）与锚点卡 §三判据：
 * 十七类清点逐字、登记字段齐全、实体链 / 既有槽分流、种子每类 3~5 条、type 归属不串类。
 */
class ArtifactRegistryTest {

    @Test
    fun `十七类登记清点为十七项且显示名逐字一致`() {
        val expected = listOf(
            "人物", "地名", "草木", "鸟兽", "药名", "名剑", "名马", "名寺(古刹)", "名道观",
            "名楼(台阁)", "名琴(乐器)", "名鼎(礼器)", "星宿(天文)", "名酒", "名食",
            "名锦名瓷服饰", "山川风物",
        )
        assertEquals(17, ArtifactRegistry.REGISTRATIONS.size)
        assertEquals(expected, ArtifactRegistry.DISPLAY_NAMES)
    }

    @Test
    fun `每条登记表名资产路径来源许可非空且实体链带代表type`() {
        ArtifactRegistry.REGISTRATIONS.forEach { registration ->
            assertTrue(registration.id.isNotBlank())
            assertTrue(registration.displayName.isNotBlank())
            assertTrue(registration.schemaTable.isNotBlank())
            assertTrue(registration.assetPath.isNotBlank())
            assertTrue(registration.source.isNotBlank())
            assertTrue(registration.license.isNotBlank())
            assertTrue(registration.basis.isNotBlank())
            if (registration.route == ArtifactRoute.ENTITY_PROVIDER) {
                assertNotNull(registration.schemaType)
                assertTrue(registration.schemaType!!.isNotBlank())
            }
        }
    }

    @Test
    fun `既有槽两类实体链十五类分流防双轨`() {
        val existing = ArtifactRegistry.existingRegistrations.map { it.id }.toSet()
        val entity = ArtifactRegistry.entityRegistrations.map { it.id }.toSet()
        assertEquals(setOf("person", "place"), existing)
        assertEquals(15, entity.size)
        assertTrue(existing.intersect(entity).isEmpty())
    }

    @Test
    fun `实体链每类种子三至五条既有槽不重复种子`() {
        ArtifactRegistry.entityRegistrations.forEach { registration ->
            val seeds = ArtifactSeedTable.forRegistration(registration.id)
            assertTrue(
                "${registration.id} 种子数应在 3~5 条之间，实际 ${seeds.size}",
                seeds.size in 3..5,
            )
            seeds.forEach { entry ->
                assertTrue(entry.id.isNotBlank())
                assertTrue(entry.name.isNotBlank())
                assertTrue(entry.type.isNotBlank())
                assertTrue(entry.headline.isNotBlank())
                assertTrue(entry.body.isNotBlank())
                assertTrue(entry.detail.isNotBlank())
                assertTrue(entry.source.isNotBlank())
            }
        }
        assertEquals(0, ArtifactSeedTable.forRegistration("person").size)
        assertEquals(0, ArtifactSeedTable.forRegistration("place").size)
    }

    @Test
    fun `type反查登记唯一且不串到别的类`() {
        assertEquals("horse", ArtifactRegistry.byType("horse")?.id)
        assertEquals("sword", ArtifactRegistry.byType("sword")?.id)
        assertEquals("temple", ArtifactRegistry.byType("temple")?.id)
        assertEquals("landscape", ArtifactRegistry.byType("mountain")?.id)
        // 子类型归入所属类别：星宿子类、山川子类、台阁子类均不串到别的类。
        assertEquals("astronomy", ArtifactRegistry.byType("constellation")?.id)
        assertEquals("astronomy", ArtifactRegistry.byType("calendar_term")?.id)
        assertEquals("landscape", ArtifactRegistry.byType("river")?.id)
        assertEquals("landscape", ArtifactRegistry.byType("lake")?.id)
        assertEquals("tower", ArtifactRegistry.byType("pavilion")?.id)
        // 同表多类：food 下名酒 / 名食各自独立登记，type 不互串。
        assertEquals("wine", ArtifactRegistry.byType("wine")?.id)
        assertEquals("dish", ArtifactRegistry.byType("dish")?.id)
        assertTrue(ArtifactRegistry.byType("no_such_type") == null)
    }

    @Test
    fun `山川独立landscape表不混入artifact表`() {
        val landscape = ArtifactRegistry.byId("landscape")
        assertNotNull(landscape)
        assertEquals("landscape", landscape!!.schemaTable)
        ArtifactRegistry.byTable("artifact").forEach {
            assertTrue(it.id != "landscape")
        }
    }

    @Test
    fun `注册表机器id唯一无重复键`() {
        val ids = ArtifactRegistry.REGISTRATIONS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
