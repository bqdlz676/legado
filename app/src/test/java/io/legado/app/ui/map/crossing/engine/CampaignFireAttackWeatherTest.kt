package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Morale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B12 crossing 引擎火攻三态 + 风温入推演单测（纯 JVM）。
 *
 * 把守口径：
 * - 三态裁决：SPREAD / CONTAINED / BACKFIRE 各至少一例；消费侧 `when` 逐态枚举，无 else 兜底。
 * - 风：标量风速影响火势状态、折损倍率与行军折减；无风向方位字段（model 未提供），不臆造顺逆方位。
 * - 温：气温影响火攻干热加成 / 寒冷压制与行军折减；NaN / 无穷 / 越界夹紧并归一，不崩。
 * - 系数全部来自 [CampaignController] companion 常量（经验默认值，待用户校）。
 */
class CampaignFireAttackWeatherTest {

    // ── 三态裁决 ────────────────────────────────────────────────

    @Test
    fun `SPREAD 中等风加干热 火势蔓延 折损放大且士气下降`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 6.0, temperatureC = 32.0, eventFrac = null)
        assertEquals(FireAttackRule.SPREAD, outcome.rule)
        assertTrue(outcome.moraleDrops)
        // 0.35 × 1.35（蔓延） × 1.15（干热） = 0.543375
        assertEquals(0.543375, outcome.damageFrac, 1e-6)
    }

    @Test
    fun `CONTAINED 静风常温 火势受控 基础折损士气下降`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 0.0, temperatureC = 15.0, eventFrac = null)
        assertEquals(FireAttackRule.CONTAINED, outcome.rule)
        assertTrue(outcome.moraleDrops)
        assertEquals(0.35, outcome.damageFrac, 1e-6)
    }

    @Test
    fun `BACKFIRE 极强风 火势反噬 折损收窄且士气不降`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 14.0, temperatureC = 30.0, eventFrac = null)
        assertEquals(FireAttackRule.BACKFIRE, outcome.rule)
        assertTrue(!outcome.moraleDrops)
        // 0.35 × 0.40（反噬） × 1.15（干热） = 0.161
        assertEquals(0.161, outcome.damageFrac, 1e-6)
    }

    // ── 风速区间近似顺 / 逆 / 侧（无方位角字段，标量强度口径） ──────

    @Test
    fun `顺风近似 中等风伴干热 判蔓延`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 4.0, temperatureC = 29.0, eventFrac = null)
        assertEquals(FireAttackRule.SPREAD, outcome.rule)
    }

    @Test
    fun `逆风近似 极强风 判反噬`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 12.0, temperatureC = 35.0, eventFrac = null)
        assertEquals(FireAttackRule.BACKFIRE, outcome.rule)
    }

    @Test
    fun `侧风近似 小风 判受控`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 1.0, temperatureC = 15.0, eventFrac = null)
        assertEquals(FireAttackRule.CONTAINED, outcome.rule)
    }

    // ── 气温边界 ────────────────────────────────────────────────

    @Test
    fun `极冷天气 火势受湿冷压制 折损低于基础值`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 0.0, temperatureC = -50.0, eventFrac = null)
        assertEquals(FireAttackRule.CONTAINED, outcome.rule)
        // 0.35 × 0.65（寒冷压制） = 0.2275
        assertEquals(0.2275, outcome.damageFrac, 1e-6)
    }

    @Test
    fun `极热天气 静风不蔓延但有干热加成 折损高于基础值`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 0.0, temperatureC = 45.0, eventFrac = null)
        assertEquals(FireAttackRule.CONTAINED, outcome.rule)
        // 0.35 × 1.15（干热） = 0.4025
        assertEquals(0.4025, outcome.damageFrac, 1e-6)
    }

    // ── 缺字段 / NaN / 越界归一 ─────────────────────────────────

    @Test
    fun `缺天气块 控制器按静风常温装载 不崩且无归一告警`() {
        val controller = CampaignController.load(WEATHER_NULL_JSON, null)
        assertTrue(controller.weatherDiagnostics().isEmpty())
        controller.step()
        assertTrue(controller.corps.isNotEmpty())
    }

    @Test
    fun `NaN 与无穷 风速气温各自回落到静风常温`() {
        assertEquals(0.0, CampaignController.normalizeWindMps(Double.NaN), 1e-9)
        assertEquals(0.0, CampaignController.normalizeWindMps(Double.POSITIVE_INFINITY), 1e-9)
        assertEquals(15.0, CampaignController.normalizeTemperatureC(Double.NaN), 1e-9)
        assertEquals(15.0, CampaignController.normalizeTemperatureC(Double.NEGATIVE_INFINITY), 1e-9)
        assertEquals(0.0, CampaignController.normalizeWindMps(null), 1e-9)
        assertEquals(15.0, CampaignController.normalizeTemperatureC(null), 1e-9)
    }

    @Test
    fun `超出物理区间 风速气温夹紧到上下界`() {
        assertEquals(40.0, CampaignController.normalizeWindMps(50.0), 1e-9)
        assertEquals(0.0, CampaignController.normalizeWindMps(-7.0), 1e-9)
        assertEquals(60.0, CampaignController.normalizeTemperatureC(80.0), 1e-9)
        assertEquals(-60.0, CampaignController.normalizeTemperatureC(-80.0), 1e-9)
    }

    // ── 风温共同作用边界 ────────────────────────────────────────

    @Test
    fun `大风叠加严寒 风优先判反噬 不被寒冷改写`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 11.0, temperatureC = -10.0, eventFrac = null)
        assertEquals(FireAttackRule.BACKFIRE, outcome.rule)
        assertTrue(!outcome.moraleDrops)
    }

    @Test
    fun `静风叠加严寒 判受控并吃寒冷压制`() {
        val outcome = CampaignController.evaluateFireAttack(windMps = 0.0, temperatureC = -10.0, eventFrac = null)
        assertEquals(FireAttackRule.CONTAINED, outcome.rule)
        assertEquals(0.2275, outcome.damageFrac, 1e-6)
    }

    // ── 行军折减 ────────────────────────────────────────────────

    @Test
    fun `静风行军不折减 大风行军折减且恒不低于下限`() {
        assertEquals(1.0, CampaignController.windMarchFactor(0.0), 1e-9)
        val gale = CampaignController.windMarchFactor(10.0)
        assertTrue(gale < 1.0)
        assertTrue(gale >= 0.85 - 1e-9)
    }

    @Test
    fun `气温行军折减 酷热严寒各取一档 常温不折减`() {
        assertEquals(1.0, CampaignController.tempMarchFactor(15.0), 1e-9)
        assertEquals(0.90, CampaignController.tempMarchFactor(40.0), 1e-9)
        assertEquals(0.85, CampaignController.tempMarchFactor(-20.0), 1e-9)
    }

    // ── 控制器集成：火攻事件真实消费风温 ──────────────────────────

    @Test
    fun `干热多风战场 火攻折损大于静风常温同兵力对照`() {
        val windy = CampaignController.load(weatherJson(wind = 8.0, temp = 35.0), null)
        val calm = CampaignController.load(weatherJson(wind = 0.0, temp = 15.0), null)
        windy.step()
        calm.step()
        val hot = windy.corps.first { it.id == "c_fire" }
        val cool = calm.corps.first { it.id == "c_fire" }
        assertTrue("干热多风折损应更大：${hot.strength} vs ${cool.strength}", hot.strength < cool.strength)
    }

    @Test
    fun `强风反噬战场 目标士气不被火攻降级`() {
        val controller = CampaignController.load(weatherJson(wind = 14.0, temp = 30.0), null)
        controller.step()
        val target = controller.corps.first { it.id == "c_fire" }
        assertEquals(Morale.STEADY, target.morale)
    }

    private companion object {

        fun weatherJson(wind: Double, temp: Double): String = """
        {
          "id": "b12-fire",
          "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
          "duration": 10,
          "weather": { "condition": "clear", "wind": $wind, "temperature": $temp },
          "victory": { "type": "defend", "side": "a" },
          "armies": [
            { "id": "c_fire", "name": "火攻", "side": "a", "initialPos": [0.02, 0.02], "strength": 10, "type": "regular", "morale": "steady" }
          ],
          "events": [
            { "trigger": { "turn": 1 }, "action": "fireAttack", "target": "c_fire", "reason": "测试火攻" }
          ]
        }
        """

        const val WEATHER_NULL_JSON: String = """
        {
          "id": "b12-null-weather",
          "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
          "duration": 10,
          "victory": { "type": "defend", "side": "a" },
          "armies": [
            { "id": "c_one", "name": "孤军", "side": "a", "initialPos": [0.02, 0.02], "strength": 10, "type": "regular", "morale": "steady" }
          ],
          "events": []
        }
        """
    }
}
