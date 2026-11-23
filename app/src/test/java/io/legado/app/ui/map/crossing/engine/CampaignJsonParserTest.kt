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
}
