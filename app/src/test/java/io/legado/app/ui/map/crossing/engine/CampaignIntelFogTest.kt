package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Corps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B9 情报模糊半径单测（纯 JVM）。
 *
 * 把守四条口径：
 * 1. 从未目睹的敌军：视图层不下发坐标（report null），模糊半径出口为 null；决策层只持开局部署报告。
 * 2. 目睹的敌军：快照刷新为实时真值，模糊半径清零。
 * 3. 模糊半径策略：刚目睹为 0，随未观测龄期单调放大并封顶（放大而非恒定，理由见交付文档）。
 * 4. 决策层感知拷贝存在且位置取自情报表，不直接引用敌军实时 [Corps.position] / [Corps.latLng]。
 */
class CampaignIntelFogTest {

    @Test
    fun `B9 未观测敌军：视图不下发坐标与半径，决策层仅持开局报告`() {
        val controller = CampaignController.load(FOG_JSON, null)
        controller.step()

        val enemyView = controller.snapshot().corps.first { it.id == "b1" }
        assertFalse("视野外敌军不应被标记观测", enemyView.observed)
        assertNull("从未目睹的敌军不下发上报纬度", enemyView.reportLat)
        assertNull("从未目睹的敌军不下发上报经度", enemyView.reportLng)
        assertNull("从未目睹的敌军模糊半径应为 null", controller.reportRadiusCells("b1"))

        // 决策层：敌军仍在感知集（存在性已知，来自开局部署情报），但位置是开局报告而非实时透视。
        val perceived: List<Corps> = controller.perceivedEnemiesOf("a1")
        val reported = perceived.firstOrNull { it.id == "b1" }
        assertNotNull("开局播种后决策层应掌握敌军存在性", reported)
        assertEquals("感知位置应取开局部署报告经度", 0.40, reported!!.latLng.lng, 1e-9)
        assertEquals("感知位置应取开局部署报告纬度", 0.40, reported.latLng.lat, 1e-9)

        val ownView = controller.snapshot().corps.first { it.id == "a1" }
        assertTrue("本方军队应标记观测", ownView.observed)
        assertNotNull("本方军队下发实时坐标", ownView.reportLat)
    }

    @Test
    fun `B9 目睹敌军：快照刷新为实时真值，模糊半径清零`() {
        val controller = CampaignController.load(NEAR_JSON, null)
        controller.step()

        val real = controller.corps.first { it.id == "b1" }
        val enemyView = controller.snapshot().corps.first { it.id == "b1" }

        assertTrue("同格近距离敌军应被标记观测", enemyView.observed)
        assertEquals("上报纬度应刷新为实时真值", real.latLng.lat, enemyView.reportLat!!, 1e-9)
        assertEquals("上报经度应刷新为实时真值", real.latLng.lng, enemyView.reportLng!!, 1e-9)
        assertEquals("刚目睹的敌军模糊半径为 0", 0, controller.reportRadiusCells("b1"))

        val perceived = controller.perceivedEnemiesOf("a1").firstOrNull { it.id == "b1" }
        assertNotNull("目睹后决策层感知位置应与真值一致", perceived)
        assertEquals("感知格应与实时格一致", real.position, perceived!!.position)
    }

    @Test
    fun `B9 模糊半径：刚目睹为 0，随龄期单调放大并封顶`() {
        // 常规军视野半径 4：龄期 1 → 4 + 1 = 5；龄期 2 → 6；龄期 4 → 8 触顶；龄期再长保持 8。
        assertEquals(0, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 0))
        assertEquals("负龄期视同刚目睹", 0, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, -2))
        assertEquals(5, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 1))
        assertEquals(6, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 2))
        assertEquals(8, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 4))
        assertEquals("超出封顶后保持上限，不再外扩", 8, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 10))

        // 民兵视野 3：龄期 1 → 4；放大速率与兵种基准解耦。
        assertEquals(4, CampaignController.fuzzyRadiusCells(ArmyType.MILITIA, 1))

        // 单调性：龄期越长半径不得缩小。
        val ages = listOf(0, 1, 2, 3, 4, 5, 10, 20)
        val radii = ages.map { CampaignController.fuzzyRadiusCells(ArmyType.CAVALRY, it) }
        assertTrue("模糊半径随龄期单调不减", radii.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `B9 分支回滚：情报表随存档重置，恢复后决策层按恢复位置重新播种`() {
        val controller = CampaignController.load(FOG_JSON, null)
        controller.step()
        val branch = controller.saveBranch()
        repeat(3) { controller.step() }

        controller.restoreBranch(branch)

        // 恢复后感知集仍含敌军，位置取恢复存档中的军位（回滚后的报告），不沿用回滚前的实时机动。
        val perceived = controller.perceivedEnemiesOf("a1")
        val reported = perceived.firstOrNull { it.id == "b1" }
        assertNotNull("回滚后决策层情报应重新播种", reported)
        val restoredEnemy = controller.corps.first { it.id == "b1" }
        assertEquals("感知位置应与回滚后存档位置一致", restoredEnemy.position, reported!!.position)
        assertNull("视图目睹记录随回滚清空，需重新侦察", controller.reportRadiusCells("b1"))
    }

    private companion object {

        /** 视野外用例：a1 居中，b1 远在视野半径之外，全程不被目睹。 */
        const val FOG_JSON: String = """
{
  "id": "b9-fog",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.0, 0.0],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.40, 0.40],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        /** 近距离用例：双方近乎同格，首回合即在视野内且通视；等力交战首回合不致溃散。 */
        const val NEAR_JSON: String = """
{
  "id": "b9-near",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.000, 0.000],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.001, 0.001],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""
    }
}
