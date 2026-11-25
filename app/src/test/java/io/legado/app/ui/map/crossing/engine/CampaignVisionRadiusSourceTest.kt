package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.visionRadiusCells
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B8R1：赋值路径单测——装配时 `Corps.visionRadiusCells` 必须取自 model 单一来源
 * [ArmyType.visionRadiusCells]，控制器侧不得另算一张表。
 *
 * 覆盖装配赋值路径（`CampaignController` 构造 Corps）；情报播种 / 模糊半径封顶 /
 * 侧翼逐敌闸门均读该字段或同一扩展属性，不再持有私有字面量表。
 */
class CampaignVisionRadiusSourceTest {

    @Test
    fun `装配后各军 visionRadiusCells 等于其兵种 model 半径`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        assertEquals(4, controller.corps.size)
        controller.corps.forEach { corps ->
            assertEquals(
                "军 ${corps.id}（${corps.type}）半径与 model 表不一致",
                corps.type.visionRadiusCells,
                corps.visionRadiusCells,
            )
        }
        // 逐兵种回查，四分支各落一条断言。
        assertEquals(6, controller.corps.first { it.type == ArmyType.CAVALRY }.visionRadiusCells)
        assertEquals(5, controller.corps.first { it.type == ArmyType.ELITE }.visionRadiusCells)
        assertEquals(4, controller.corps.first { it.type == ArmyType.REGULAR }.visionRadiusCells)
        assertEquals(3, controller.corps.first { it.type == ArmyType.MILITIA }.visionRadiusCells)
    }

    /** 平场栅格：全域同高程，通视恒真、无水面 / 关隘，供装配口径用例确定性布位。 */
    private fun flatPlainRaster(): HeightRaster =
        HeightRaster.create(
            CampaignController.GRID_N,
            CampaignController.GRID_N,
            IntArray(CampaignController.GRID_N * CampaignController.GRID_N) { 16 },
            degradedHint = false,
        )

    private companion object {
        /** 四兵种各一支，覆盖半径表全部分支；JSON 坐标仅作占位，测试体不覆写格位。 */
        const val RADIUS_JSON: String = """
{
  "id": "b8r1-radius-source",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 20,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "骑兵甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 5, "type": "cavalry", "morale": "steady", "intent": "hold" },
    { "id": "a2", "name": "精锐甲", "side": "a", "initialPos": [0.02, 0.02],
      "strength": 5, "type": "elite", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "常规乙", "side": "b", "initialPos": [0.03, 0.03],
      "strength": 5, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b2", "name": "民兵乙", "side": "b", "initialPos": [0.04, 0.04],
      "strength": 5, "type": "militia", "morale": "steady", "intent": "hold" }
  ]
}
"""
    }
}
