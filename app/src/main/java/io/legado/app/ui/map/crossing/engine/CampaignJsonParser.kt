package io.legado.app.ui.map.crossing.engine

import com.google.gson.JsonArray
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
        // B13：heightMap 统一走 strOrNull 守卫——非 primitive（对象 / 数组 / JsonNull）回落 null，
        // 空白串裁掉后回落 null；与 B4 其余字段同一兜底口径，不把类型错位抛到壳层。
        val heightMap = strOrNull(obj, "heightMap")

        val focus = parseFocus(obj, warnings)
        val duration = int(obj, "duration", DEFAULT_DURATION).coerceAtLeast(1)
        val weather = parseWeather(obj)
        val victory = parseVictory(obj, warnings)
        val sides = parseSides(obj)
        val diplomacy = parseDiplomacy(obj)
        val armies = parseArmies(obj, warnings)
        val events = parseEvents(obj, warnings)
        val terrain = parseTerrain(obj, warnings)

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
        return parseVictoryDef(el.asJsonObject, warnings)
    }

    /** 递归解析 COMPOUND 子条件（规格 L109 AND/OR 组合；嵌套层级不限）。 */
    private fun parseVictoryDef(v: JsonObject, warnings: MutableList<String>): VictoryDef {
        val type = VictoryType.fromLabel(str(v, "type", "annihilate"))
        val target = strOrNull(v, "target")
        val side = strOrNull(v, "side")
        val point = geo(v, "point")
        val holdTurns = int(v, "holdTurns", 3).coerceAtLeast(1)
        val op = str(v, "op", "AND").uppercase()
        val sub = mutableListOf<VictoryDef>()
        // sub 被素材写成对象 / 字符串 / 数字时，getAsJsonArray 会抛 IllegalStateException；
        // 走 jsonArray 守卫回落空表，胜利条件按主条件判定，不穿透到壳层。
        jsonArray(v, "sub")?.forEach { item ->
            if (item.isJsonObject) {
                sub += parseVictoryDef(item.asJsonObject, warnings)
            } else {
                warnings += "victory.sub 存在非对象子条件，已跳过"
            }
        }
        return VictoryDef(type, target, side, point, holdTurns, op, sub)
    }

    private fun parseSides(obj: JsonObject): List<SideDef> {
        // sides 类型错位（对象 / 字符串 / 数字 / JsonNull）回落空阵营表，不抛；守卫见 jsonArray。
        val arr = jsonArray(obj, "sides") ?: return emptyList()
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
        // diplomacy 类型错位回落空盟约表；守卫见 jsonArray，不抛穿透。
        val arr = jsonArray(obj, "diplomacy") ?: return emptyList()
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
        // armies 类型错位回落空军队表（壳层 degraded 标志随之置真）；守卫见 jsonArray。
        val arr = jsonArray(obj, "armies") ?: return emptyList()
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
        // events 类型错位回落空事件表；守卫见 jsonArray，不抛穿透。
        val arr = jsonArray(obj, "events") ?: return emptyList()
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

    private fun parseTerrain(obj: JsonObject, warnings: MutableList<String>): TerrainDef {
        // 兜底铁律：地形块任何畸形（对象错位 / 点列非数组 / 元素非数 / 长度异常）都不得抛穿到 UI，
        // 残缺部分按空列表回落并记 warning。
        return try {
            val el = obj.get("terrain") ?: return TerrainDef(procedural = true, emptyList(), emptyList())
            if (!el.isJsonObject) return TerrainDef(procedural = true, emptyList(), emptyList())
            val t = el.asJsonObject
            val ridges = mutableListOf<RidgeDef>()
            // ridges 类型错位回落空山脊表；守卫见 jsonArray，避免一条畸形山脊拖垮整座地形。
            jsonArray(t, "ridges")?.forEach { r ->
                if (r.isJsonObject) {
                    val ro = r.asJsonObject
                    ridges += RidgeDef(
                        x = dbl(ro, "x", 0.5), y = dbl(ro, "y", 0.5),
                        height = dbl(ro, "h", 0.5), width = dbl(ro, "w", 0.2),
                    )
                }
            }
            val rivers = mutableListOf<RiverDef>()
            // rivers 类型错位（对象 / 字符串 / 数字 / JsonNull）回落空河流表；守卫见 jsonArray。
            jsonArray(t, "rivers")?.forEachIndexed { index, rv ->
                if (!rv.isJsonObject) {
                    warnings += "terrain.rivers[$index] 不是对象，已跳过"
                    return@forEachIndexed
                }
                val ptsEl = rv.asJsonObject.get("pts")
                if (ptsEl == null || !ptsEl.isJsonArray) {
                    warnings += "terrain.rivers[$index].pts 不是数组，已跳过"
                    return@forEachIndexed
                }
                val pts = mutableListOf<Geo>()
                ptsEl.asJsonArray.forEach { p ->
                    val geo = guardedPoint(p)
                    if (geo != null) pts += geo
                }
                if (pts.size >= 2) rivers += RiverDef(pts)
                else warnings += "terrain.rivers[$index] 有效点列不足 2 个，已跳过"
            }
            TerrainDef(
                // procedural 被素材写成字符串 / 数字时 asBoolean 会抛；走 boolPrimitive 守卫回落 true，
                // 不把一条开关错位放大成整座地形回退（守卫见 boolPrimitive）。
                procedural = boolPrimitive(t, "procedural", true),
                ridges = ridges,
                rivers = rivers,
            )
        } catch (t: Throwable) {
            warnings += "terrain 块解析异常已兜底：${t.javaClass.simpleName}"
            TerrainDef(procedural = true, emptyList(), emptyList())
        }
    }

    /**
     * 单个河流折线点类型守卫，任何畸形形态都返回 null，不抛异常：
     * - 元素为 null / JsonNull / 对象 / 字符串 / 数字 / 布尔（非数组）→ null；
     * - 点数组长度 <2（奇数坐标，如 [0.1]）→ null；长度 >2 时只取前 [lng, lat] 两位；
     * - 坐标为字符串 "abc"（getAsNumber 抛）/ 对象（getAsNumber 抛）→ catch 回落 null；
     * - 坐标解析出 NaN / Infinity → isFinite 过滤回落 null。
     * 适用条件：河流折线归一化 0..1 坐标；本守卫不做范围钳制，越界由地形层消费。
     */
    private fun guardedPoint(el: com.google.gson.JsonElement?): Geo? {
        if (el == null || el.isJsonNull || !el.isJsonArray) return null
        val arr = el.asJsonArray
        if (arr.size() < 2) return null
        return try {
            // 数字取值走 JsonElement.getAsNumber()（Gson 已声明），Number.toDouble() 转双精度；
            // 非数 / 非法值抛异常后由外层 try/catch 兜底返回 null，畸形 JSON 不抛到 UI。
            val x = arr[0].getAsNumber().toDouble()
            val y = arr[1].getAsNumber().toDouble()
            if (!x.isFinite() || !y.isFinite()) null else Geo(lat = y, lng = x)
        } catch (t: Throwable) {
            null
        }
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
            if (el.isJsonPrimitive) el.getAsNumber().toDouble() else default
        } catch (t: Throwable) {
            default
        }
    }

    /**
     * 数组取值守卫：字段缺失 / JsonNull / 非数组（对象、字符串、数字、布尔）一律返回 null，不抛。
     * 适用条件：Gson `JsonObject.getAsJsonArray(name)` 在字段存在但类型错位时直接抛
     * `IllegalStateException`，会穿透到 UI 层；本守卫把类型错位统一降级为空表，由调用方记 warning。
     */
    private fun jsonArray(obj: JsonObject, key: String): JsonArray? {
        val el = obj.get(key) ?: return null
        if (el.isJsonNull || !el.isJsonArray) return null
        return el.asJsonArray
    }

    /**
     * 布尔取值守卫：字段缺失 / 非布尔 primitive / 解析异常回落 [default]，不抛。
     * 适用条件：`asBoolean` 对非布尔字符串 / 数字会抛；procedural 等开关被素材写错类型时按默认值走，
     * 避免一条开关错位把整块地形拖进外层兜底分支。
     */
    private fun boolPrimitive(obj: JsonObject, key: String, default: Boolean): Boolean {
        val el = obj.get(key) ?: return default
        if (!el.isJsonPrimitive) return default
        return try {
            el.asBoolean
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
            Geo(lat = arr[1].getAsNumber().toDouble(), lng = arr[0].getAsNumber().toDouble())
        } catch (t: Throwable) {
            null
        }
    }

    const val DEFAULT_DURATION: Int = 30
    const val SUPPLY_FULL: Int = 5
}
