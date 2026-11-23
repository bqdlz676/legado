package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.VictoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战役 JSON 解析器兜底单测（纯 JVM）。
 * 覆盖：非法 / 空输入走兜底、缺字段默认值、未知动作跳过、合法演示字段对齐。
 */
class CampaignJsonParserTest {

    @Test
    fun `非法 JSON 不抛异常并标记降级`() {
        val def = CampaignJsonParser.parse("{{{broken")
        assertTrue(def.degraded)
        assertTrue(def.warnings.isNotEmpty())
    }

    @Test
    fun `空字符串走兜底中立定义`() {
        val def = CampaignJsonParser.parse(null)
        assertTrue(def.degraded)
        assertTrue(def.armies.isEmpty())
        assertEquals(CampaignJsonParser.DEFAULT_DURATION, def.duration)
    }

    @Test
    fun `空对象给保守默认值`() {
        val def = CampaignJsonParser.parse("{}")
        assertTrue(def.degraded)
        assertEquals(CampaignJsonParser.DEFAULT_DURATION, def.duration)
        assertEquals(VictoryType.ANNIHILATE, def.victory.type)
    }

    @Test
    fun `未知动作事件被跳过并记警告`() {
        val json = """
        {
          "id": "x",
          "events": [
            { "trigger": { "turn": 1 }, "action": "nuke", "target": "a", "reason": "未知动作" }
          ]
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue(def.events.isEmpty())
        assertTrue(def.warnings.any { it.contains("action 未知") })
    }

    @Test
    fun `演示 JSON 六军四事件且胜利条件对齐`() {
        val def = CampaignJsonParser.parse(DemoCampaigns.CHISHUI_JSON)
        assertEquals("chishui", def.id)
        assertEquals(6, def.armies.size)
        assertEquals(4, def.events.size)
        assertEquals(VictoryType.DEFEND, def.victory.type)
        assertEquals("red", def.victory.side)
        assertTrue(def.terrain.rivers.isNotEmpty())
    }

    @Test
    fun `F3 畸形河流点列被类型守卫吃掉，不抛异常并降级`() {
        val json = """
        {
          "id": "malformed-rivers",
          "terrain": {
            "rivers": [
              { "pts": "not-an-array" },
              { "pts": [ { "x": 0.1 } ] },
              { "pts": [ [0.1, 0.2], "bad-point" ] },
              { "pts": [ [0.1] ] },
              { "pts": [ [0.1, 0.2], [0.3, 0.4] ] }
            ]
          }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue("畸形点列不得抛穿解析层", def.terrain.rivers.size == 1)
        assertTrue(def.degraded)
        assertTrue(def.warnings.any { it.contains("rivers") })
    }

    @Test
    fun `军队缺字段回退保守默认`() {
        val json = """
        {
          "id": "x",
          "armies": [ { "id": "a1", "name": "孤军", "side": "a", "initialPos": [0.0, 0.0] } ]
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertEquals(1, def.armies.size)
        val army = def.armies.first()
        assertEquals(5.0, army.strength, 1e-6)
        assertTrue(army.supply in 0..CampaignJsonParser.SUPPLY_FULL)
    }

    @Test
    fun `B4 数组字段类型错位回落空列表并标记降级，不抛异常`() {
        // sides / diplomacy / armies / events 本应是数组；这里错置为对象 / 字符串，
        // 旧实现 getAsJsonArray 会抛 JsonSyntaxException 穿透到 UI；新口径记 warning 后按空列表兜底。
        val json = """
        {
          "id": "type-mismatch",
          "sides": { "id": "a" },
          "diplomacy": "not-an-array",
          "armies": { "id": "a1" },
          "events": 42,
          "terrain": { "ridges": "oops", "rivers": { "pts": [] } }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue("类型错位不得抛穿解析层", def.degraded)
        assertTrue(def.sides.isEmpty())
        assertTrue(def.diplomacy.isEmpty())
        assertTrue(def.armies.isEmpty())
        assertTrue(def.events.isEmpty())
        assertTrue(def.terrain.ridges.isEmpty())
        assertTrue(def.terrain.rivers.isEmpty())
        assertTrue(def.warnings.any { it.contains("sides") })
        assertTrue(def.warnings.any { it.contains("armies") })
    }

    @Test
    fun `B4 victory.sub 非数组时递归解析不穿透并降级`() {
        val json = """
        {
          "id": "sub-mismatch",
          "victory": { "type": "compound", "op": "AND", "sub": { "type": "defend" } }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue(def.degraded)
        assertTrue(def.victory.sub.isEmpty())
    }

    @Test
    fun `B4 heightMap 非字符串回落为 null 且不抛异常`() {
        val json = """
        {
          "id": "heightmap-type",
          "heightMap": { "asset": "china_height.png" },
          "armies": [ { "id": "a1", "side": "a", "initialPos": [0.0, 0.0] } ]
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue(def.degraded)
        assertEquals(null, def.heightMap)
    }
}
