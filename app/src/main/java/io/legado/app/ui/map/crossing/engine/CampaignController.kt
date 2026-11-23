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
        val allySides = alliedSidesWith(ownSideId)
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
            pointHolders = buildPointHolders(),
            alliedSideIds = alliedSidesWith(definition.victory.side),
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
        if (target == null) {
            // 边界：目标军 id 在棋盘上不存在；记日志不抛异常，事件仍由触发器登记为已点燃。
            log("事件「${event.reason}」：目标 ${event.target ?: "全局"} 缺失，动作 ${event.action} 未生效")
            return
        }
        if (!target.alive && event.action != EventAction.MORALE_CHANGE) {
            // 边界：目标已阵亡 / 出局，不再重复结算（含重复触发、同回合先溃后降的冲突序）；
            // MORALE_CHANGE 例外：保留溃兵归队复振路径。
            log("事件「${event.reason}」：${label} 已失去作战能力，动作 ${event.action} 未生效")
            return
        }
        when (event.action) {
            EventAction.MORALE_CHANGE -> {
                target.morale = Morale.fromLabel(event.value)
                log("事件「${event.reason}」：${label} 士气变为 ${target.morale}")
            }
            EventAction.SURRENDER -> {
                target.surrendered = true; target.disband()
                log("事件「${event.reason}」：${label} 投降")
            }
            EventAction.SUPPLY_CUT -> {
                target.supply = 0
                log("事件「${event.reason}」：${label} 补给断绝")
            }
            EventAction.SET_INTENT -> {
                target.intent = Intent.fromLabel(event.value)
                log("事件「${event.reason}」：${label} 意图调整为 ${target.intent}")
            }
            EventAction.SET_TARGET -> {
                target.target = event.targetPos
                log("事件「${event.reason}」：${label} 目标点更新")
            }
            EventAction.STRENGTH_CHANGE -> {
                val delta = event.value?.toDoubleOrNull() ?: 0.0
                target.strength = (target.strength + delta).coerceAtLeast(0.0)
                log("事件「${event.reason}」：${label} 兵力变动 $delta")
            }
            EventAction.ROUT -> {
                target.morale = Morale.BROKEN
                target.intent = Intent.ROUT
                log("事件「${event.reason}」：${label} 溃散")
            }

            // ── 事件型：火攻 / 攻城器械 / 中央突破 ────────────────────
            EventAction.FIRE_ATTACK -> {
                val frac = fracOf(event.value, LOSS_FIRE_ATTACK)
                val loss = target.strength * frac
                target.applyLoss(loss)
                target.morale = target.morale.downgrade()
                log("事件「${event.reason}」：${label} 遭火攻，折损 ${loss.round1()}，士气下降")
            }
            EventAction.SIEGE_ENGINE -> {
                val frac = fracOf(event.value, LOSS_SIEGE_ENGINE)
                val loss = target.strength * frac
                target.applyLoss(loss)
                target.morale = Morale.BROKEN
                log("事件「${event.reason}」：${label} 被攻城器械破城，士气崩溃")
            }
            EventAction.BREAKTHROUGH -> {
                val frac = fracOf(event.value, LOSS_BREAKTHROUGH)
                val loss = target.strength * frac
                target.applyLoss(loss)
                target.morale = target.morale.downgrade()
                log("事件「${event.reason}」：${label} 阵形被中央突破，士气下降")
            }
            EventAction.DEFEND_FALL -> {
                // 城破殉国：出局、兵力归零；非投降（surrendered 保持 false）。
                target.disband()
                target.morale = Morale.BROKEN
                log("事件「${event.reason}」：${label} 据点失守，守军殉国")
            }
            EventAction.CAPTURE -> {
                target.surrendered = true
                target.disband()
                log("事件「${event.reason}」：${label} 被俘")
            }
            EventAction.ANNIHILATE -> {
                target.disband()
                target.morale = Morale.BROKEN
                log("事件「${event.reason}」：${label} 全军覆没")
            }

            // ── 混合型：击溃 + 溃逃意图 ──────────────────────────────
            EventAction.NAVAL_ROUT -> {
                val frac = fracOf(event.value, LOSS_NAVAL_ROUT)
                target.applyLoss(target.strength * frac)
                target.morale = Morale.BROKEN
                target.intent = Intent.ROUT
                log("事件「${event.reason}」：${label} 舟师溃乱，脱离水阵")
            }

            // ── 意图型：改写意图，路径交由意图裁决器 / 寻路器 ──────────
            EventAction.AMBUSH -> {
                target.intent = Intent.AMBUSH
                log("事件「${event.reason}」：${label} 转入设伏")
            }
            EventAction.FLANK -> {
                target.intent = Intent.FLANK
                log("事件「${event.reason}」：${label} 转入侧翼包抄")
            }
            EventAction.RETREAT -> {
                target.intent = Intent.RETREAT
                log("事件「${event.reason}」：${label} 转入撤退")
            }
            EventAction.ESCAPE -> {
                // 突围脱走：对应规则意图 BREAKOUT（向最弱方向突出重围）。
                target.intent = Intent.BREAKOUT
                log("事件「${event.reason}」：${label} 转入突围")
            }
            EventAction.FEIGNED_RETREAT -> {
                // 佯退诱敌：目标被诱出既阵转入追击，阵形松动并暴露位置。
                target.intent = Intent.ADVANCE
                target.exposed = true
                log("事件「${event.reason}」：${label} 中计追击，阵形散开")
            }
        }
    }

    /** value 折损比例：空白 / 非法数字 / 越界回落到默认比例，不抛异常。 */
    private fun fracOf(raw: String?, default: Double): Double =
        raw?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: default

    // ── 情报 / 意图 / 移动 / 遭遇 ─────────────────────────────

    private fun updateIntelligence(corps: Corps) {
        val enemies = enemiesOf(corps)
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
        val allies = alliesOf(corps)
        val enemies = enemiesOf(corps)
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
            Intent.FLANK -> pathfinder.flankPath(corps.position, target, corps.type, enemiesOf(corps).map { it.position })
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
        val enemies = enemiesOf(corps).filter { octile(corps.position, it.position) <= ENCOUNTER_RANGE }
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
        val key = pointKey(point)
        val holder = corps.any { it.sideId == definition.victory.side && it.combatReady() && nearPoint(it.latLng, point) }
        captureHold[key] = if (holder) (captureHold[key] ?: 0) + 1 else 0
    }

    /**
     * 据点当前占据方表（key = 据点标识，value = 占据方 sideId）。
     * 口径：仅取顶层胜利条件的目标点；可战军队（存活 / 未投降 / 兵力 > 0）落在抵达半径内即视为占据；
     * 同点多支军队时己方优先，其次任一可战他方（敌 / 友交由判定层按 [alliedSidesWith] 区分）。
     * 目标点缺失或半径内无可战军队 → 空表，判定层按「据点未失守」处理。
     */
    private fun buildPointHolders(): Map<String, String> {
        val point = definition.victory.point ?: return emptyMap()
        val ready = corps.filter { it.combatReady() && nearPoint(it.latLng, point) }
        val holder = ready.firstOrNull { it.sideId == definition.victory.side } ?: ready.firstOrNull()
        return holder?.let { mapOf(pointKey(point) to it.sideId) } ?: emptyMap()
    }

    /** 与 [sideId] 结盟的阵营 id 集合（diplomacy ALLIED 对的另一方）；sideId 为 null 时返回空集。 */
    private fun alliedSidesWith(sideId: String?): Set<String> {
        val target = sideId ?: return emptySet()
        return definition.diplomacy
            .filter { it.relation == Relation.ALLIED && (it.a == target || it.b == target) }
            .map { if (it.a == target) it.b else it.a }
            .toSet()
    }

    private fun pointKey(point: Geo): String =
        "${"%.4f".format(point.lng)},${"%.4f".format(point.lat)}"

    fun snapshot(): TurnSnapshot {
        // 情报雾（F8）：只有"本方军 / 盟友"或"被任一可见本方军看到"的敌方军才算 observed；
        // 未观测的敌军不下发实时坐标（reportLat/Lng = null），避免玩家透视全图。
        // 友敌划分统一走 [isFriendlySide]（B3 收口）：盟友不进敌方侦察集，也不误标为未观测敌军。
        val playerSide = definition.victory.side
        val observers = corps.filter { it.sideId == playerSide && it.combatReady() }
        val observedIds = HashSet<String>()
        for (ob in observers) for (e in corps) {
            if (isFriendlySide(ob.sideId, e.sideId)) continue
            val d = octile(ob.position, e.position)
            if (d <= ob.visionRadiusCells && map.visible(ob.position, e.position)) observedIds += e.id
        }
        val views = corps.map { c ->
            val seen = isFriendlySide(playerSide, c.sideId) || c.id in observedIds
            c.toView(observed = seen, report = if (seen) c.latLng else null)
        }
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
        captureHold = captureHold.toMap(),
        pointHolders = buildPointHolders(),
    )

    /** 恢复分支：时间 / 兵力 / 已触发事件一并回滚；状态重置为进行中。 */
    fun restoreBranch(branch: BranchSnapshot) {
        corps.clear()
        corps += branch.corps.map { it.copy() }
        turn = branch.turn
        elapsedDays = branch.elapsedDays
        firedEvents.clear()
        firedEvents += branch.firedEventIds
        captureHold.clear()
        captureHold.putAll(branch.captureHold)
        restorePointHolders(branch.pointHolders)
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

    // 敌我判定必须叠加外交盟约：ALLIED 关系的另一阵营既不进敌方集合，也计入盟友集合。
    // 只按 sideId 相异判敌会让外交盟友在遭遇战中互耗（F2 回归根因）。
    // 收口约定：遭遇（[resolveEncounters]）、支援 / 溃逃意图裁决（[resolveIntent]）、
    // 侧翼路径（[planRoute]）、情报（[updateIntelligence]）的敌我划分一律走这两处；
    // 阵营级友敌划分走 [isFriendlySide]。禁止在其它分支再按 sideId 相异自行判敌（B3）。
    private fun enemiesOf(corps: Corps): List<Corps> =
        corps.filter {
            it.id != corps.id && it.sideId != corps.sideId &&
                it.sideId !in alliedSidesWith(corps.sideId) && it.combatReady()
        }

    private fun alliesOf(corps: Corps): List<Corps> =
        corps.filter {
            it.id != corps.id &&
                (it.sideId == corps.sideId || it.sideId in alliedSidesWith(corps.sideId)) &&
                it.combatReady()
        }

    /** 测试口径出口：同包单测直接断言敌 / 友集合划分；生产代码一律走私有 [enemiesOf] / [alliesOf]。 */
    internal fun filterEnemiesOf(corps: Corps): List<Corps> = enemiesOf(corps)

    /** 测试口径出口：同包单测直接断言敌 / 友集合划分；生产代码一律走私有 [enemiesOf] / [alliesOf]。 */
    internal fun filterAlliesOf(corps: Corps): List<Corps> = alliesOf(corps)

    /**
     * 阵营级友方判定（B3 收口）：同阵营或外交 ALLIED 盟约方；任一侧 sideId 为空时一律非友。
     * 情报快照的侦察跳过条件 / 可见性标记与 [alliesOf] 阵营口径同源，避免两套口径漂移。
     */
    private fun isFriendlySide(side: String?, otherSide: String?): Boolean =
        side != null && otherSide != null &&
            (side == otherSide || otherSide in alliedSidesWith(side))

    private fun dailyMaxCells(corps: Corps): Int {
        val kind = map.kindAt(corps.position)
        val speed = TerrainCost.marchSpeed(corps.type, corps.fatigue, definition.weather?.condition, kind)
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

    private fun Corps.toView(observed: Boolean = true, report: io.legado.app.ui.map.crossing.model.Geo? = null): CorpsView = CorpsView(
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
        observed = observed,
        reportLat = report?.lat,
        reportLng = report?.lng,
    )

    /** 恢复据点归属：快照里记录了"据点 id → 阵营"，恢复时重算对应军的占领计数并写回。 */
    private fun restorePointHolders(holders: Map<String, String>) {
        if (holders.isEmpty()) return
        captureHold.clear()
        for ((_, side) in holders) for (c in corps) if (c.sideId == side) captureHold[c.id] = 1
    }

    companion object {
        /** 推演网格边长（格 × 格）；高程图与投影共用同一口径。 */
        const val GRID_N: Int = 32

        /** 基础日行上限（格 / 回合），兵种速度系数在此上乘除。 */
        const val BASE_DAILY_CELLS: Int = 4

        /** 遭遇判定半径（格，八向相邻）。 */
        const val ENCOUNTER_RANGE: Int = 1

        /** 事件型动作默认折损比例（占当前兵力）；与 help/sim/EventTrigger 口径一致。 */
        const val LOSS_FIRE_ATTACK: Double = 0.35
        const val LOSS_SIEGE_ENGINE: Double = 0.55
        const val LOSS_BREAKTHROUGH: Double = 0.25
        const val LOSS_NAVAL_ROUT: Double = 0.20

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
