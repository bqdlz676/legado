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

    // ── B4 畸形数据兜底：河流点列 / 顶层数组字段类型错位，一律不抛、置降级标志 ──

    @Test
    fun `B4 河流点列奇数坐标（单点数组）被跳过并降级`() {
        // 第一点 [0.1] 长度 1 不构成 [lng, lat]，守卫回落 null；第二点有效但不足 2 个，整条河流跳过。
        val json = """
        {
          "id": "odd-pts",
          "terrainFocus": { "center": [105.8, 28.3], "radius": 50 },
          "armies": [ { "id": "a1", "side": "red" } ],
          "victory": { "type": "defend", "side": "red" },
          "terrain": { "rivers": [ { "pts": [[0.1], [0.2, 0.3]] } ] }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue("畸形点列不得抛穿解析层", def.terrain.rivers.isEmpty())
        assertTrue(def.degraded)
        assertTrue(def.warnings.any { it.contains("有效点列不足") })
    }

    @Test
    fun `B4 河流坐标非数、对象、NaN、Infinity 被吃掉不抛`() {
        // "abc" 数字解析抛、{"x":1} 非数组、"NaN"/"Infinity" 经 isFinite 过滤；四类畸形点全部回落 null。
        val json = """
        {
          "id": "bad-coords",
          "terrainFocus": { "center": [105.8, 28.3], "radius": 50 },
          "armies": [ { "id": "a1", "side": "red" } ],
          "victory": { "type": "defend", "side": "red" },
          "terrain": { "rivers": [
            { "pts": [["abc", 0.2], {"x": 1}, ["NaN", 0.4], ["Infinity", 0.3]] }
          ] }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue("非数 / 对象 / 无穷坐标不得抛穿解析层", def.terrain.rivers.isEmpty())
        assertTrue(def.degraded)
        assertTrue(def.warnings.any { it.contains("有效点列不足") })
    }

    @Test
    fun `B4 河流点列空数组被跳过并降级`() {
        val json = """
        {
          "id": "empty-pts",
          "terrainFocus": { "center": [105.8, 28.3], "radius": 50 },
          "armies": [ { "id": "a1", "side": "red" } ],
          "victory": { "type": "defend", "side": "red" },
          "terrain": { "rivers": [ { "pts": [] } ] }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue("空点列不得抛穿解析层", def.terrain.rivers.isEmpty())
        assertTrue(def.degraded)
        assertTrue(def.warnings.any { it.contains("有效点列不足") })
    }

    @Test
    fun `B4 顶层数组字段类型错位（对象、字符串、数字、布尔）回落空表不抛`() {
        // sides/diplomacy/armies/events/rivers/ridges 全部写成非数组；getAsJsonArray 旧路径会抛
        // IllegalStateException，现走 jsonArray 守卫回落空表，degraded 置真。
        val json = """
        {
          "id": "misplaced",
          "sides": {},
          "diplomacy": "x",
          "armies": 5,
          "events": true,
          "terrain": { "rivers": "not-an-array", "ridges": 7 }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue("类型错位字段不得抛穿解析层", def.sides.isEmpty())
        assertTrue(def.diplomacy.isEmpty())
        assertTrue(def.armies.isEmpty())
        assertTrue(def.events.isEmpty())
        assertTrue(def.terrain.rivers.isEmpty())
        assertTrue(def.terrain.ridges.isEmpty())
        assertTrue(def.degraded)
    }

    @Test
    fun `B4-R 河流 pts 六类畸形（对象/字符串/数字/布尔/JsonNull/空数组）逐条跳过且各记一条 warning`() {
        // 六类点列形态各占一条河流；每条跳过都必须产生一条含 rivers 的可读 warning，不静默失败。
        val json = """
        {
          "id": "six-malformed-pts",
          "terrain": { "rivers": [
            { "pts": { "x": 0.1 } },
            { "pts": "not-an-array" },
            { "pts": 123 },
            { "pts": true },
            { "pts": null },
            { "pts": [] }
          ] }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue("六类畸形点列不得抛穿解析层", def.terrain.rivers.isEmpty())
        assertEquals("每条畸形河流各记一条 warning", 6, def.warnings.count { it.contains("rivers") })
        assertTrue(def.degraded)
    }

    @Test
    fun `B4-R 部分合法混合：两条合法河流保留，四条畸形河流各记一条 warning`() {
        val json = """
        {
          "id": "mixed-rivers",
          "terrainFocus": { "center": [105.8, 28.3], "radius": 50 },
          "armies": [ { "id": "a1", "side": "red" } ],
          "victory": { "type": "defend", "side": "red" },
          "terrain": { "rivers": [
            { "pts": [[0.1, 0.2], [0.3, 0.4]] },
            { "pts": [[0.5, 0.6], [0.7, 0.8]] },
            { "pts": { "x": 0.1 } },
            { "pts": "not-an-array" },
            { "pts": 5 },
            { "pts": null }
          ] }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertEquals("两条合法河流必须保留", 2, def.terrain.rivers.size)
        assertEquals(2, def.terrain.rivers.first().pts.size)
        assertEquals("四条畸形河流各记一条 warning", 4, def.warnings.count { it.contains("rivers") })
        assertTrue(def.degraded)
    }

    @Test
    fun `B4-R rivers 字段整体类型错位时记字段级 warning 且回落空表`() {
        val json = """
        {
          "id": "rivers-misplaced",
          "terrain": { "rivers": "not-an-array" }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertTrue(def.terrain.rivers.isEmpty())
        assertTrue(def.warnings.any { it.contains("rivers 类型错位") })
    }

    @Test
    fun `B4 合法最小战役：两点河流保留且不降级`() {
        // 对照基线：字段齐全、河流两点合法，不得记 warning、degraded 为假。
        val json = """
        {
          "id": "min-ok",
          "terrainFocus": { "center": [105.8, 28.3], "radius": 50 },
          "armies": [ { "id": "a1", "side": "red" } ],
          "victory": { "type": "defend", "side": "red" },
          "terrain": { "rivers": [ { "pts": [[0.1, 0.2], [0.5, 0.6]] } ] }
        }
        """
        val def = CampaignJsonParser.parse(json)
        assertEquals(1, def.armies.size)
        assertEquals(1, def.terrain.rivers.size)
        assertEquals(2, def.terrain.rivers.first().pts.size)
        assertTrue("合法战役不应置降级标志", !def.degraded)
        assertTrue(def.warnings.isEmpty())
    }
}
