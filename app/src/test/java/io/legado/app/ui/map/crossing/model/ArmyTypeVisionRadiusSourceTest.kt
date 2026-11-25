package io.legado.app.ui.map.crossing.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B8R1：视野半径表 model 层单一来源单测。
 *
 * 断言四兵种半径 = 情报表口径（骑兵 6 / 精锐 5 / 常规 4 / 民兵 3），枚举穷尽映射；
 * engine 两侧（Pathfinder / CampaignController）只允许引用 [ArmyType.visionRadiusCells]，
 * 不得再各建一份字面量表，避免「情报看见的半径」与「侧翼躲开的半径」漂移。
 */
class ArmyTypeVisionRadiusSourceTest {

    @Test
    fun `四兵种视野半径对齐情报表口径 骑兵6 精锐5 常规4 民兵3`() {
        assertEquals(6, ArmyType.CAVALRY.visionRadiusCells)
        assertEquals(5, ArmyType.ELITE.visionRadiusCells)
        assertEquals(4, ArmyType.REGULAR.visionRadiusCells)
        assertEquals(3, ArmyType.MILITIA.visionRadiusCells)
    }

    @Test
    fun `枚举穷尽：每一支兵种都有确定正半径且取值与口径表一致`() {
        ArmyType.entries.forEach { type ->
            assertTrue("${type} 半径必须为正格数", type.visionRadiusCells > 0)
        }
        assertEquals(
            mapOf(
                ArmyType.CAVALRY to 6,
                ArmyType.ELITE to 5,
                ArmyType.REGULAR to 4,
                ArmyType.MILITIA to 3,
            ),
            ArmyType.entries.associateWith { it.visionRadiusCells },
        )
    }
}
