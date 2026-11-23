package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.EventDef
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.TerrainFocus
import kotlin.math.hypot

/**
 * 条件触发器引擎（纯 JVM，§一「事件用条件触发器，不硬编码回合数」）。
 *
 * 为什么独立成层：战役事件全部声明在 JSON 的 `events[].trigger` 里，引擎只做
 * 「条件 AND 求值 + 一次性触发」，不写死任何一场仗的历史节点。换仗只换 JSON。
 *
 * 条件口径（全部 AND，null = 不约束）：
 * - `turn`：当前回合 ≥ 声明回合即满足（一次性事件；回合不会回退，晚一回合补触也不漏）。
 * - `army`：指定军队 id；null 表示事件不绑定具体军队（如全局天候 / 据点事件）。
 * - `strengthBelow`：目标军队兵力 < 阈值。
 * - `morale`：目标军队士气标签命中。
 * - `atPoint`：目标军队距声明点的网格距离 ≤ [AT_POINT_RADIUS_CELLS]。
 */
object TriggerEngine {

    /** 命中结果：事件下标（一次性去重）+ 事件 + 作用对象（全局事件为 null）。 */
    data class Match(val index: Int, val event: EventDef, val corps: Corps?)

    /**
     * 求值本回合所有未触发事件。
     *
     * @param fired 已触发事件下标集合（一次性语义：触发过的不再重复点燃）
     */
    fun evaluate(
        events: List<EventDef>,
        corps: List<Corps>,
        turn: Int,
        fired: Set<Int>,
        focus: TerrainFocus,
        gridN: Int,
    ): List<Match> {
        val out = ArrayList<Match>()
        events.forEachIndexed { index, event ->
            if (index in fired) return@forEachIndexed
            val target = event.target?.let { id -> corps.firstOrNull { it.id == id } }
            if (matches(event, target, corps, turn, focus, gridN)) {
                out += Match(index, event, target)
            }
        }
        return out
    }

    /** 单条事件条件求值；目标军队已解散 / 投降时，除 turn 类全局条件外不触发。 */
    fun matches(
        event: EventDef,
        target: Corps?,
        corps: List<Corps>,
        turn: Int,
        focus: TerrainFocus,
        gridN: Int,
    ): Boolean {
        val t = event.trigger
        if (t.turn != null && turn < t.turn) return false

        val boundToArmy = t.army != null || t.strengthBelow != null || t.morale != null || t.atPoint != null
        if (boundToArmy) {
            val armyId = t.army ?: event.target
            val subject = target ?: corps.firstOrNull { it.id == armyId } ?: return false
            if (!subject.combatReady() && t.strengthBelow == null) return false

            if (t.strengthBelow != null && subject.strength >= t.strengthBelow!!) return false
            if (t.morale != null && subject.morale != Morale.fromLabel(t.morale)) return false
            if (t.atPoint != null && !nearPoint(subject.latLng, t.atPoint, focus, gridN)) return false
        }
        return true
    }

    /** 经纬度点距换算成网格单元距离，落在焦点半径内做粗判。 */
    private fun nearPoint(a: Geo, b: Geo, focus: TerrainFocus, gridN: Int): Boolean {
        val dLat = Math.abs(a.lat - b.lat) * 111.32
        val dLng = Math.abs(a.lng - b.lng) * 111.32 * kotlin.math.cos(Math.toRadians(focus.center.lat))
        val distKm = hypot(dLat, dLng)
        val cellKm = focus.radiusKm * 2.0 / gridN
        return distKm <= cellKm * AT_POINT_RADIUS_CELLS
    }

    /** atPoint 命中半径（格）。 */
    private const val AT_POINT_RADIUS_CELLS: Double = 1.5
}
