package io.legado.app.help.artifact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P06 名物槽装配登记表纯 JVM 验收（只用 org.junit + kotlin stdlib，不依赖 Android / Compose / 资源）。
 *
 * 覆盖：十七类装配完整性（每类要么既有槽承接、要么显式登记 EntityProvider）、既有槽三类不进实体链、
 * 其余十四类走通用入口、典故不挂名物表、映射单源无重复键。
 */
class ArtifactSlotsTest {

    @Test
    fun `十七类每类都有唯一装配登记`() {
        assertEquals(17, ArtifactCategory.entries.size)
        assertEquals(17, ArtifactSlots.byCategory.size)
        assertEquals(ArtifactCategory.entries.size, ArtifactSlots.byCategory.size)
        ArtifactCategory.entries.forEach { category ->
            val slot = ArtifactSlots.slotOf(category)
            assertEquals(category, slot.category)
            assertTrue(slot.basis.isNotBlank())
        }
    }

    @Test
    fun `人物地名典故由既有槽承接不进实体链`() {
        val existing = ArtifactSlots.existingSlots.map { it.category }.toSet()
        assertEquals(
            setOf(ArtifactCategory.PERSON, ArtifactCategory.PLACE, ArtifactCategory.ALLUSION),
            existing,
        )
        ArtifactSlots.existingSlots.forEach { slot ->
            assertEquals(ArtifactRoute.EXISTING_SLOT, slot.route)
            assertTrue(ArtifactSlots.EXISTING_SLOT_NAMES.contains(slot.existingSlot))
            assertNull(slot.schemaType)
        }
    }

    @Test
    fun `其余十四类走通用实体提供者且类型非空`() {
        assertEquals(14, ArtifactSlots.entitySlots.size)
        ArtifactSlots.entitySlots.forEach { slot ->
            assertEquals(ArtifactRoute.ENTITY_PROVIDER, slot.route)
            assertTrue(!slot.schemaType.isNullOrBlank())
            assertNull(slot.existingSlot)
            assertTrue(!slot.schemaTable.isNullOrBlank())
        }
    }

    @Test
    fun `典故登记为典故槽承接且不挂名物表`() {
        val slot = ArtifactSlots.slotOf(ArtifactCategory.ALLUSION)
        assertEquals(ArtifactRoute.EXISTING_SLOT, slot.route)
        assertEquals(ArtifactSlots.SLOT_ALLUSION, slot.existingSlot)
        assertNull(ArtifactCategory.ALLUSION.schemaTable)
    }

    @Test
    fun `单源映射无重复键每类只登记一次`() {
        val wires = ArtifactSlots.byCategory.values.map { it.category.wire }
        assertEquals(wires.size, wires.distinct().size)
        val routes = ArtifactSlots.byCategory.values.map { it.category }
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun `实体链类别不含人物地名典故防双轨`() {
        val entityWires = ArtifactSlots.entitySlots.map { it.category.wire }.toSet()
        assertTrue(!entityWires.contains("人物"))
        assertTrue(!entityWires.contains("地名"))
        assertTrue(!entityWires.contains("典故"))
    }

    @Test
    fun `山川风物独立登记走实体链不并入地名槽`() {
        val landscape = ArtifactSlots.slotOf(ArtifactCategory.LANDSCAPE)
        assertEquals(ArtifactRoute.ENTITY_PROVIDER, landscape.route)
        assertEquals("landscape", landscape.schemaTable)
        assertEquals("mountain", landscape.schemaType)
        assertEquals(ArtifactSlots.SLOT_PLACE, ArtifactSlots.slotOf(ArtifactCategory.PLACE).existingSlot)
    }
}
