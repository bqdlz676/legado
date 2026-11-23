package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyDef
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.BranchSnapshot
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.CorpsView
import io.legado.app.ui.map.crossing.model.EventAction
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.Relation
import io.legado.app.ui.map.crossing.model.TurnSnapshot
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.TerrainCost
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import kotlin.math.roundToInt

/**
 * 推演引擎对外门面（纯 JVM，不引 Android / Compose 类型）。
 *
 * 职责收口：载入 JSON + 高程图 + 规则 → 初始化棋盘 → 回合主循环（情报 → 意图 → 移动 →
 * 遭遇 → 状态 → 胜利判定）→ 产出不可变快照供壳层消费。换仗只换 [definition] 对应的数据，
 * 本类不写死任何历史事件。
 *
 * 不变量（单测逐项把守）：
 * - 时间单调：[turn] / [elapsedDays] 只在 [step] 内 +1，分支回档属显式恢复，不算回退。
 * - 兵力守恒：兵力只经战斗损耗 / 事件变更变动，钳制 ≥0；累计损耗记入 [Corps.losses]。
 * - 河道不可越：移动落点与退却落点都必须 [TerrainMap.passable]，非涉渡水面不允许站上。
 */
class CampaignController private constructor(
    val definition: io.legado.app.ui.map.crossing.model.CampaignDefinition,
    val raster: HeightRaster,
    val projection: GeoProjection,
    val map: TerrainMap,
    private val pathfinder: Pathfinder,
    val ownSideId: String,
) {

    val corps: MutableList<Corps> = mutableListOf()

    var turn: Int = 0
        private set
    var elapsedDays: Int = 0
        private set
    var status: BattleStatus = BattleStatus.ONGOING
        private set
    var winnerSide: String? = null
        private set
    var finishReason: String = ""
        private set

    private val firedEvents: MutableSet<Int> = mutableSetOf()
    private val captureHold: MutableMap<String, Int> = mutableMapOf()
    private val _eventLog: MutableList<String> = mutableListOf()

    /** 本回合每支军队的行军格数（士气推进消费）。 */
    private val marchCells: MutableMap<String, Int> = mutableMapOf()

    /** 降级标志：JSON 残缺 / 高程图走合成兜底 / 军队为空，任一命中即明示壳层。 */
    val degraded: Boolean get() = definition.degraded || raster.degraded || corps.isEmpty()

    val warnings: List<String> get() = definition.warnings

    init {
        instantiateCorps()
    }

    // ── 载入 ──────────────────────────────────────────────────

    private fun instantiateCorps() {
        val allySides = definition.diplomacy
            .filter { it.relation == Relation.ALLIED && (it.a == ownSideId || it.b == ownSideId) }
            .map { if (it.a == ownSideId) it.b else it.a }
            .toSet()
        definition.armies.forEachIndexed { index, def ->
            val role = FactionRole.fromSideIds(def.sideId, ownSideId, allySides)
            val rawCell = projection.toCell(def.pos)
            // 防御兜底：初始坐标压到非渡口水面 / 超坡度格时，钳制到最近可通行邻格，避免开局即堵死。
            val cell = if (map.passable(rawCell, def.type)) rawCell else nearestPassable(rawCell, def.type)
            corps += Corps(
                id = def.id,
                name = def.name,
                sideId = def.sideId,
                role = role,
                type = def.type,
                strength = def.strength,
                fatigue = def.fatigue,
                morale = def.morale,
                intent = def.intent,
                position = cell,
                latLng = def.pos,
                target = def.target,
                supply = def.supply,
                supplyCutTurns = 0,
                alive = true,
                surrendered = false,
                exposed = false,
                highGround = false,
                route = emptyList(),
                lastKnown = emptyMap(),
                visionRadiusCells = visionRadius(def),
            )
            marchCells[def.id] = 0
        }
    }

    private fun visionRadius(def: ArmyDef): Int = when (def.type) {
        io.legado.app.ui.map.crossing.model.ArmyType.CAVALRY -> 6
        io.legado.app.ui.map.crossing.model.ArmyType.ELITE -> 5
        io.legado.app.ui.map.crossing.model.ArmyType.REGULAR -> 4
        io.legado.app.ui.map.crossing.model.ArmyType.MILITIA -> 3
    }

    // ── 回合主循环 ────────────────────────────────────────────

    /**
     * 推进一步。[overrides] 为手动模式意图覆盖（军队 id → 意图），缺省走规则裁决。
     * 返回本回合不可变快照；终局后再调用只重复返回终局快照。
     */
    fun step(overrides: Map<String, Intent>? = null): TurnSnapshot {
        if (status != BattleStatus.ONGOING) return snapshot()

        turn += 1
        elapsedDays += 1

        fireTriggeredEvents()

        // 回合内行动顺序：骑兵先动、步兵后动（按基础速度降序）。
        val ordered = corps.filter { it.combatReady() }
            .sortedByDescending { it.type.speedBase }

        for (corps in ordered) {
            updateIntelligence(corps)
            val decision = resolveIntent(corps, overrides)
            corps.intent = decision.intent
            val targetCell = decision.target ?: corps.target?.let { projection.toCell(it) }
            corps.route = planRoute(corps, decision.intent, targetCell)
            marchAlong(corps, decision.intent)
            resolveEncounters(corps)
        }

        for (corps in corps) {
            val effect = MoraleSystem.advance(corps, marchCells[corps.id] ?: 0, dailyMaxCells(corps))
            if (effect.disbanded) log("${corps.name} 士气溃散，解散退出战场")
            else if (effect.moraleDropped) log("${corps.name} 疲劳过重，士气下降一级")
        }

        updateCaptureTracker()

        val outcome = VictoryChecker.check(
            def = definition.victory,
            corps = corps,
            turn = turn,
            duration = definition.duration,
            captureHoldTurns = captureHold,
        )
        if (outcome.finished) {
            status = outcome.status
            winnerSide = outcome.winnerSide
            finishReason = outcome.reason
            log(outcome.reason)
        }

        return snapshot()
    }

    /** 自动推演到终局或回合上限，返回逐回合快照序列。 */
    fun runToEnd(maxTurns: Int = definition.duration): List<TurnSnapshot> {
        val snapshots = ArrayList<TurnSnapshot>()
        var guard = 0
        while (status == BattleStatus.ONGOING && guard < maxTurns) {
            snapshots += step()
            guard++
        }
        return snapshots
    }

    // ── 触发器 ────────────────────────────────────────────────

    private fun fireTriggeredEvents() {
        val matches = TriggerEngine.evaluate(
            events = definition.events,
            corps = corps,
            turn = turn,
            fired = firedEvents,
            focus = definition.focus,
            gridN = GRID_N,
        )
        matches.forEach { match ->
            firedEvents += match.index
            applyEvent(match.event, match.corps)
        }
    }

    private fun applyEvent(event: io.legado.app.ui.map.crossing.model.EventDef, target: Corps?) {
        val label = target?.name ?: event.target ?: "全局"
        when (event.action) {
            EventAction.MORALE_CHANGE -> {
                target?.let { it.morale = Morale.fromLabel(event.value) }
                log("事件「${event.reason}」：${label} 士气变为 ${target?.morale}")
            }
            EventAction.SURRENDER -> {
                target?.let { it.surrendered = true; it.disband() }
                log("事件「${event.reason}」：${label} 投降")
            }
            EventAction.SUPPLY_CUT -> {
                target?.supply = 0
                log("事件「${event.reason}」：${label} 补给断绝")
            }
            EventAction.SET_INTENT -> {
                target?.intent = Intent.fromLabel(event.value)
                log("事件「${event.reason}」：${label} 意图调整为 ${target?.intent}")
            }
            EventAction.SET_TARGET -> {
                target?.target = event.targetPos
                log("事件「${event.reason}」：${label} 目标点更新")
            }
            EventAction.STRENGTH_CHANGE -> {
                val delta = event.value?.toDoubleOrNull() ?: 0.0
                target?.let { it.strength = (it.strength + delta).coerceAtLeast(0.0) }
                log("事件「${event.reason}」：${label} 兵力变动 $delta")
            }
            EventAction.ROUT -> {
                target?.morale = Morale.BROKEN
                log("事件「${event.reason}」：${label} 溃散")
            }
        }
    }

    // ── 情报 / 意图 / 移动 / 遭遇 ─────────────────────────────

    private fun updateIntelligence(corps: Corps) {
        val enemies = corps.filterEnemies()
        val known = HashMap<String, Geo>()
        for (enemy in enemies) {
            val distCells = octile(corps.position, enemy.position)
            val visible = distCells <= corps.visionRadiusCells && map.visible(corps.position, enemy.position)
            if (visible) known[enemy.id] = enemy.latLng
            else known[enemy.id] = corps.lastKnown[enemy.id] ?: enemy.latLng
        }
        corps.lastKnown = known
    }

    private fun resolveIntent(corps: Corps, overrides: Map<String, Intent>?): IntentResolver.Decision {
        val override = overrides?.get(corps.id)
        if (override != null) {
            return IntentResolver.Decision(override, corps.target?.let { projection.toCell(it) }, "手动覆盖意图")
        }
        val allies = corps.filterAllies()
        val enemies = corps.filterEnemies()
        return IntentResolver.resolve(
            corps = corps,
            allies = allies,
            enemies = enemies,
            map = map,
            chokeCells = map.chokeCells,
            supplyCells = allies.map { it.position },
        )
    }

    private fun planRoute(corps: Corps, intent: Intent, target: io.legado.app.ui.map.crossing.model.Cell?): List<io.legado.app.ui.map.crossing.model.Cell> {
        if (target == null) return emptyList()
        return when (intent) {
            Intent.FLANK -> pathfinder.flankPath(corps.position, target, corps.type, corps.filterEnemies().map { it.position })
            Intent.AMBUSH, Intent.HOLD -> emptyList()
            else -> pathfinder.find(corps.position, target, corps.type)
        }
    }

    private fun marchAlong(corps: Corps, intent: Intent) {
        marchCells[corps.id] = 0
        if (corps.route.size <= 1) return
        val budget = dailyMaxCells(corps)
        val steps = minOf(budget, corps.route.size - 1)
        var moved = 0
        var current = corps.position
        for (i in 1..steps) {
            val next = corps.route.getOrNull(i) ?: break
            if (!map.passable(next, corps.type)) break
            current = next
            moved++
        }
        corps.position = current
        corps.latLng = projection.toGeo(current)
        marchCells[corps.id] = moved
        corps.highGround = map.highGround(current)
        if (moved == 0 && intent == Intent.CONTINUE) corps.intent = Intent.HOLD
    }

    private fun resolveEncounters(corps: Corps) {
        val enemies = corps.filterEnemies().filter { octile(corps.position, it.position) <= ENCOUNTER_RANGE }
        val target = enemies.minByOrNull { octile(corps.position, it.position) } ?: return

        val ambushed = corps.intent == Intent.AMBUSH && !corps.exposed
        val highGroundDef = target.highGround
        val outcome = CombatResolver.resolve(corps, target, ambushed, highGroundDef)

        corps.applyLoss(outcome.attackerLoss)
        target.applyLoss(outcome.defenderLoss)
        corps.exposed = true

        if (outcome.attackerLoss > outcome.defenderLoss) {
            corps.morale = corps.morale.downgrade()
        } else {
            target.morale = target.morale.downgrade()
        }
        if (outcome.defenderRetreated && target.combatReady()) retreatFrom(target, corps.position)

        log("遭遇战：${corps.name} 对 ${target.name}（伏击=${ambushed}，高地=${highGroundDef}）" +
            "，损 ${outcome.attackerLoss.round1()} / ${outcome.defenderLoss.round1()}")

        if (!target.alive) log("${target.name} 兵败解散")
        if (!corps.alive) log("${corps.name} 兵败解散")
    }

    private fun retreatFrom(corps: Corps, threat: io.legado.app.ui.map.crossing.model.Cell) {
        val escape = map.neighbors8(corps.position)
            .filter { map.passable(it, corps.type) }
            .maxByOrNull { octile(it, threat) }
        if (escape != null) {
            corps.position = escape
            corps.latLng = projection.toGeo(escape)
        }
    }

    // ── 据点 / 快照 / 分支 ────────────────────────────────────

    private fun updateCaptureTracker() {
        val point = definition.victory.point ?: return
        val key = "${"%.4f".format(point.lng)},${"%.4f".format(point.lat)}"
        val holder = corps.any { it.sideId == definition.victory.side && it.combatReady() && nearPoint(it.latLng, point) }
        captureHold[key] = if (holder) (captureHold[key] ?: 0) + 1 else 0
    }

    fun snapshot(): TurnSnapshot {
        val views = corps.map { it.toView() }
        return TurnSnapshot(
            turn = turn,
            elapsedDays = elapsedDays,
            status = status,
            winnerSide = winnerSide,
            corps = views,
            events = _eventLog.takeLast(LOG_TAIL),
        )
    }

    /** 保存分支存档（深拷贝运行态，避免后续回合改动污染存档）。 */
    fun saveBranch(): BranchSnapshot = BranchSnapshot(
        turn = turn,
        elapsedDays = elapsedDays,
        corps = corps.map { it.copy() },
        firedEventIds = firedEvents.toSet(),
    )

    /** 恢复分支：时间 / 兵力 / 已触发事件一并回滚；状态重置为进行中。 */
    fun restoreBranch(branch: BranchSnapshot) {
        corps.clear()
        corps += branch.corps.map { it.copy() }
        turn = branch.turn
        elapsedDays = branch.elapsedDays
        firedEvents.clear()
        firedEvents += branch.firedEventIds
        status = BattleStatus.ONGOING
        winnerSide = null
        finishReason = ""
        log("已恢复分支存档（第 $turn 回合）")
    }

    // ── 内部工具 ──────────────────────────────────────────────

    private fun nearestPassable(start: io.legado.app.ui.map.crossing.model.Cell, type: io.legado.app.ui.map.crossing.model.ArmyType): io.legado.app.ui.map.crossing.model.Cell {
        if (map.passable(start, type)) return start
        for (radius in 1..6) {
            var best: io.legado.app.ui.map.crossing.model.Cell? = null
            var bestDist = Int.MAX_VALUE
            for (dc in -radius..radius) for (dr in -radius..radius) {
                val c = io.legado.app.ui.map.crossing.model.Cell(start.col + dc, start.row + dr)
                if (!map.inBounds(c) || !map.passable(c, type)) continue
                val d = dc * dc + dr * dr
                if (d < bestDist) {
                    bestDist = d
                    best = c
                }
            }
            if (best != null) return best
        }
        return start
    }

    private fun Corps.filterEnemies(): List<Corps> =
        corps.filter { it.id != this.id && it.sideId != this.sideId && it.combatReady() }

    private fun Corps.filterAllies(): List<Corps> =
        corps.filter { it.id != this.id && it.sideId == this.sideId && it.combatReady() }

    private fun dailyMaxCells(corps: Corps): Int {
        val speed = TerrainCost.marchSpeed(corps.type, corps.fatigue, definition.weather?.condition)
        return (BASE_DAILY_CELLS * speed).roundToInt().coerceAtLeast(1)
    }

    private fun nearPoint(a: Geo, b: Geo): Boolean {
        val dLat = Math.abs(a.lat - b.lat) * 111.32
        val dLng = Math.abs(a.lng - b.lng) * 111.32
        return Math.hypot(dLat, dLng) <= VictoryChecker.REACH_RADIUS_KM
    }

    private fun octile(a: io.legado.app.ui.map.crossing.model.Cell, b: io.legado.app.ui.map.crossing.model.Cell): Int {
        val dc = kotlin.math.abs(a.col - b.col)
        val dr = kotlin.math.abs(a.row - b.row)
        return maxOf(dc, dr) + minOf(dc, dr)
    }

    private fun Double.round1(): Double = (this * 10).roundToInt() / 10.0

    private fun log(message: String) {
        _eventLog += message
        if (_eventLog.size > LOG_CAPACITY) _eventLog.removeAt(0)
    }

    private fun Corps.toView(): CorpsView = CorpsView(
        id = id,
        name = name,
        sideId = sideId,
        role = role,
        type = type,
        strength = strength,
        fatigue = fatigue,
        morale = morale,
        intent = intent,
        lat = latLng.lat,
        lng = latLng.lng,
        targetLat = target?.lat,
        targetLng = target?.lng,
        alive = alive,
        surrendered = surrendered,
        route = route.map { projection.toGeo(it) },
    )

    companion object {
        /** 推演网格边长（格 × 格）；高程图与投影共用同一口径。 */
        const val GRID_N: Int = 32

        /** 基础日行上限（格 / 回合），兵种速度系数在此上乘除。 */
        const val BASE_DAILY_CELLS: Int = 4

        /** 遭遇判定半径（格，八向相邻）。 */
        const val ENCOUNTER_RANGE: Int = 1

        /** 快照事件条保留条数。 */
        const val LOG_TAIL: Int = 8

        /** 事件日志容量上限（超出滚出最早一条）。 */
        const val LOG_CAPACITY: Int = 64

        /**
         * 载入入口：JSON 残缺 / 高程图缺失全部兜底，永不抛异常。
         *
         * @param raster 壳层解码 PNG 得到的高程栅格；null 时用内置合成默认场并置降级标志
         * @param ownSideId 己方阵营 id；缺省取第一支军队所在阵营
         */
        fun load(json: String?, raster: HeightRaster? = null, ownSideId: String? = null): CampaignController {
            val definition = CampaignJsonParser.parse(json)
            val effectiveRaster = raster ?: HeightRaster.synthetic(seed = SEED_FALLBACK, gridN = GRID_N, degraded = true)
            val projection = GeoProjection(definition.focus, GRID_N)
            val map = TerrainMap(
                raster = effectiveRaster,
                projection = projection,
                rivers = definition.terrain.rivers,
                ridges = definition.terrain.ridges,
            )
            val pathfinder = Pathfinder(map, definition.weather?.condition)
            val fallbackSide = definition.armies.firstOrNull()?.sideId ?: definition.sides.firstOrNull()?.id ?: "own"
            val side = ownSideId ?: fallbackSide
            return CampaignController(definition, effectiveRaster, projection, map, pathfinder, side)
        }

        /** 内置演示：四渡赤水。 */
        fun demo(): CampaignController = load(DemoCampaigns.CHISHUI_JSON)

        private const val SEED_FALLBACK: Long = 42L
    }
}
