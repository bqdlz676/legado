package io.legado.app.ui.map.crossing.engine

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.ui.map.crossing.model.ArmyDef
import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.CampaignDefinition
import io.legado.app.ui.map.crossing.model.DiplomacyDef
import io.legado.app.ui.map.crossing.model.EventAction
import io.legado.app.ui.map.crossing.model.EventDef
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.RidgeDef
import io.legado.app.ui.map.crossing.model.Relation
import io.legado.app.ui.map.crossing.model.RiverDef
import io.legado.app.ui.map.crossing.model.SideDef
import io.legado.app.ui.map.crossing.model.TerrainDef
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.model.TriggerDef
import io.legado.app.ui.map.crossing.model.VictoryDef
import io.legado.app.ui.map.crossing.model.VictoryType
import io.legado.app.ui.map.crossing.model.WeatherDef

/**
 * 战役 JSON 解析器（纯 JVM，Gson；不引 Android 资源）。
 *
 * 铁律：**换仗只换 JSON，不换代码**。解析层对残缺 / 非法数据全部兜底——字段缺失给默认值，
 * 类型错位做数值强转，整条 army / event 无法解析时跳过并记 warning，绝不把异常抛到 UI。
 * 解析失败也返回一份最小中立定义（[CampaignDefinition.degraded] = true），壳层据此显示降级标识。
 */
object CampaignJsonParser {

    /** 解析入口：永不抛异常。 */
    fun parse(json: String?): CampaignDefinition {
        val warnings = mutableListOf<String>()
        if (json.isNullOrBlank()) {
            return fallback("战役 JSON 为空或断网未读到", warnings)
        }
        val root = try {
            JsonParser.parseString(json)
        } catch (t: Throwable) {
            return fallback("JSON 解析失败：${t.javaClass.simpleName}", warnings)
        }
        if (root == null || !root.isJsonObject) {
            return fallback("JSON 根节点不是对象", warnings)
        }
        val obj = root.asJsonObject

        val id = str(obj, "id", "unknown")
        val title = str(obj, "title", "未名之战")
        val era = str(obj, "era", "")
        val context = str(obj, "context", "")
        val heightMap = obj.get("heightMap")?.takeIf { it.isJsonPrimitive }?.asString

        val focus = parseFocus(obj, warnings)
        val duration = int(obj, "duration", DEFAULT_DURATION).coerceAtLeast(1)
        val weather = parseWeather(obj)
        val victory = parseVictory(obj, warnings)
        val sides = parseSides(obj)
        val diplomacy = parseDiplomacy(obj)
        val armies = parseArmies(obj, warnings)
        val events = parseEvents(obj, warnings)
        val terrain = parseTerrain(obj)

        if (armies.isEmpty()) warnings += "armies 为空：棋盘上没有可推演的军队"
        if (victory.type == VictoryType.ANNIHILATE && victory.target.isNullOrBlank()) {
            warnings += "victory.target 缺失：歼灭条件无法判定，按撑满回合判胜负"
        }

        return CampaignDefinition(
            id = id,
            title = title,
            era = era,
            context = context,
            focus = focus,
            duration = duration,
            weather = weather,
            victory = victory,
            sides = sides,
            diplomacy = diplomacy,
            armies = armies,
            events = events,
            terrain = terrain,
            heightMap = heightMap,
            degraded = warnings.isNotEmpty(),
            warnings = warnings,
        )
    }

    // ── 分块解析 ──────────────────────────────────────────────

    private fun parseFocus(obj: JsonObject, warnings: MutableList<String>): TerrainFocus {
        val focusEl = obj.get("terrainFocus")
        if (focusEl == null || !focusEl.isJsonObject) {
            warnings += "terrainFocus 缺失：投影按原点 50km 兜底"
            return TerrainFocus(Geo(0.0, 0.0), 50.0)
        }
        val focus = focusEl.asJsonObject
        val center = geo(focus, "center") ?: Geo(0.0, 0.0)
        val radius = dbl(focus, "radius", 50.0).coerceIn(5.0, 500.0)
        return TerrainFocus(center, radius)
    }

    private fun parseWeather(obj: JsonObject): WeatherDef? {
        val el = obj.get("weather") ?: return null
        if (!el.isJsonObject) return null
        val w = el.asJsonObject
        return WeatherDef(
            condition = str(w, "condition", "clear"),
            windMps = dbl(w, "wind", 0.0),
            temperatureC = dbl(w, "temperature", 15.0),
        )
    }

    private fun parseVictory(obj: JsonObject, warnings: MutableList<String>): VictoryDef {
        val el = obj.get("victory")
        if (el == null || !el.isJsonObject) {
            warnings += "victory 缺失：默认 ANNIHILATE 无目标"
            return VictoryDef(VictoryType.ANNIHILATE, null, null, null, 3, "AND", emptyList())
        }
        val v = el.asJsonObject
        val type = VictoryType.fromLabel(str(v, "type", "annihilate"))
        val target = strOrNull(v, "target")
        val side = strOrNull(v, "side")
        val point = geo(v, "point")
        val holdTurns = int(v, "holdTurns", 3).coerceAtLeast(1)
        val op = str(v, "op", "AND").uppercase()
        val sub = mutableListOf<VictoryDef>()
        v.getAsJsonArray("sub")?.forEach { item ->
            if (item.isJsonObject) {
                sub += VictoryDef(
                    type = VictoryType.fromLabel(str(item.asJsonObject, "type", "annihilate")),
                    target = strOrNull(item.asJsonObject, "target"),
                    side = strOrNull(item.asJsonObject, "side"),
                    point = geo(item.asJsonObject, "point"),
                    holdTurns = int(item.asJsonObject, "holdTurns", 3),
                    op = "AND",
                    sub = emptyList(),
                )
            }
        }
        return VictoryDef(type, target, side, point, holdTurns, op, sub)
    }

    private fun parseSides(obj: JsonObject): List<SideDef> {
        val arr = obj.getAsJsonArray("sides") ?: return emptyList()
        val out = mutableListOf<SideDef>()
        arr.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val s = el.asJsonObject
            out += SideDef(
                id = str(s, "id", ""),
                name = str(s, "name", ""),
                colorRaw = strOrNull(s, "color"),
            )
        }
        return out
    }

    private fun parseDiplomacy(obj: JsonObject): List<DiplomacyDef> {
        val arr = obj.getAsJsonArray("diplomacy") ?: return emptyList()
        val out = mutableListOf<DiplomacyDef>()
        arr.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val d = el.asJsonObject
            out += DiplomacyDef(
                a = str(d, "a", ""),
                b = str(d, "b", ""),
                relation = Relation.fromLabel(str(d, "relation", "neutral")),
            )
        }
        return out
    }

    private fun parseArmies(obj: JsonObject, warnings: MutableList<String>): List<ArmyDef> {
        val arr = obj.getAsJsonArray("armies") ?: return emptyList()
        val out = mutableListOf<ArmyDef>()
        arr.forEachIndexed { index, el ->
            if (!el.isJsonObject) {
                warnings += "armies[$index] 不是对象，已跳过"
                return@forEachIndexed
            }
            val a = el.asJsonObject
            val id = str(a, "id", "army_$index")
            val pos = geo(a, "initialPos") ?: Geo(0.0, 0.0)
            out += ArmyDef(
                id = id,
                name = str(a, "name", id),
                sideId = str(a, "side", ""),
                pos = pos,
                strength = dbl(a, "strength", 5.0).coerceIn(0.0, 100.0),
                type = ArmyType.fromLabel(strOrNull(a, "type")),
                fatigue = dbl(a, "fatigue", 0.0).coerceIn(0.0, 5.0),
                morale = Morale.fromLabel(strOrNull(a, "morale")),
                intent = Intent.fromLabel(strOrNull(a, "intent")),
                target = geo(a, "target"),
                supply = int(a, "supply", SUPPLY_FULL).coerceIn(0, SUPPLY_FULL),
            )
        }
        return out
    }

    private fun parseEvents(obj: JsonObject, warnings: MutableList<String>): List<EventDef> {
        val arr = obj.getAsJsonArray("events") ?: return emptyList()
        val out = mutableListOf<EventDef>()
        arr.forEachIndexed { index, el ->
            if (!el.isJsonObject) {
                warnings += "events[$index] 不是对象，已跳过"
                return@forEachIndexed
            }
            val e = el.asJsonObject
            val action = EventAction.fromLabel(strOrNull(e, "action"))
            if (action == null) {
                warnings += "events[$index] action 未知：${strOrNull(e, "action")}，已跳过"
                return@forEachIndexed
            }
            val triggerEl = e.get("trigger")
            val trigger = if (triggerEl != null && triggerEl.isJsonObject) {
                val t = triggerEl.asJsonObject
                TriggerDef(
                    turn = if (t.has("turn")) int(t, "turn", -1).takeIf { it >= 0 } else null,
                    army = strOrNull(t, "army"),
                    strengthBelow = if (t.has("strengthBelow")) dbl(t, "strengthBelow", 0.0) else null,
                    morale = strOrNull(t, "morale"),
                    atPoint = geo(t, "atPoint"),
                )
            } else TriggerDef(null, null, null, null, null)
            out += EventDef(
                trigger = trigger,
                action = action,
                target = strOrNull(e, "target"),
                value = strOrNull(e, "value"),
                targetPos = geo(e, "targetPos"),
                reason = str(e, "reason", ""),
            )
        }
        return out
    }

    private fun parseTerrain(obj: JsonObject): TerrainDef {
        val el = obj.get("terrain") ?: return TerrainDef(procedural = true, emptyList(), emptyList())
        if (!el.isJsonObject) return TerrainDef(procedural = true, emptyList(), emptyList())
        val t = el.asJsonObject
        val ridges = mutableListOf<RidgeDef>()
        t.getAsJsonArray("ridges")?.forEach { r ->
            if (r.isJsonObject) {
                val ro = r.asJsonObject
                ridges += RidgeDef(
                    x = dbl(ro, "x", 0.5), y = dbl(ro, "y", 0.5),
                    height = dbl(ro, "h", 0.5), width = dbl(ro, "w", 0.2),
                )
            }
        }
        val rivers = mutableListOf<RiverDef>()
        t.getAsJsonArray("rivers")?.forEach { rv ->
            if (!rv.isJsonObject) return@forEach
            val pts = mutableListOf<Geo>()
            rv.asJsonObject.getAsJsonArray("pts")?.forEach { p ->
                val arr = p.asJsonArray
                val x = arr.getOrNull(0)?.asDouble ?: 0.0
                val y = arr.getOrNull(1)?.asDouble ?: 0.0
                pts += Geo(lat = y, lng = x)
            }
            if (pts.size >= 2) rivers += RiverDef(pts)
        }
        return TerrainDef(
            procedural = t.get("procedural")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
            ridges = ridges,
            rivers = rivers,
        )
    }

    // ── 兜底与原始取值工具 ─────────────────────────────────────

    private fun fallback(reason: String, warnings: MutableList<String>): CampaignDefinition {
        warnings += reason
        return CampaignDefinition(
            id = "fallback",
            title = "推演兜底战场",
            era = "",
            context = "",
            focus = TerrainFocus(Geo(0.0, 0.0), 50.0),
            duration = DEFAULT_DURATION,
            weather = null,
            victory = VictoryDef(VictoryType.DEFEND, null, null, null, 3, "AND", emptyList()),
            sides = emptyList(),
            diplomacy = emptyList(),
            armies = emptyList(),
            events = emptyList(),
            terrain = TerrainDef(procedural = true, emptyList(), emptyList()),
            heightMap = null,
            degraded = true,
            warnings = warnings,
        )
    }

    private fun str(obj: JsonObject, key: String, default: String): String =
        strOrNull(obj, key) ?: default

    private fun strOrNull(obj: JsonObject, key: String): String? {
        val el = obj.get(key) ?: return null
        return if (el.isJsonPrimitive) el.asString.trim().ifBlank { null } else null
    }

    private fun int(obj: JsonObject, key: String, default: Int): Int =
        dbl(obj, key, default.toDouble()).toInt()

    private fun dbl(obj: JsonObject, key: String, default: Double): Double {
        val el: JsonElement = obj.get(key) ?: return default
        return try {
            if (el.isJsonPrimitive) el.asDouble else default
        } catch (t: Throwable) {
            default
        }
    }

    /** 从 [lng, lat] 数组取经纬度；数组长度不足或非数返回 null。 */
    private fun geo(obj: JsonObject, key: String): Geo? {
        val el = obj.get(key) ?: return null
        if (!el.isJsonArray) return null
        val arr = el.asJsonArray
        if (arr.size() < 2) return null
        return try {
            Geo(lat = arr[1].asDouble, lng = arr[0].asDouble)
        } catch (t: Throwable) {
            null
        }
    }

    const val DEFAULT_DURATION: Int = 30
    const val SUPPLY_FULL: Int = 5
}
