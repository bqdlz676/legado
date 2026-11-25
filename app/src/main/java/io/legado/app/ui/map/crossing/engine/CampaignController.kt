package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.BranchSnapshot
import io.legado.app.ui.map.crossing.model.CampaignDefinition
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.CorpsView
import io.legado.app.ui.map.crossing.model.EventAction
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.Relation
import io.legado.app.ui.map.crossing.model.TerrainDef
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.model.TurnSnapshot
import io.legado.app.ui.map.crossing.model.visionRadiusCells
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightMapRaster
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.TerrainCost
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import kotlin.math.roundToInt

/**
 * 火攻推演三态（B12；规格 §六 赤壁战役：风向 / 火攻 / 气温是关键变量）。
 *
 * 口径限制：`WeatherDef` 目前只有标量风速，**没有风向方位角字段**；顺 / 逆 / 侧风在本层
 * 以风速区间近似——中等风 + 干热 ≈ 顺风借势蔓延，极强风 ≈ 逆风失控反噬，小风 ≈ 侧风受控。
 * 风向方位字段补齐前，规则层不得臆造攻方与风向的相对方位（EventDef 亦无攻方字段）。
 *
 * 三态必须在消费侧 [when] 中逐一枚举，禁止 `else` 兜底吞掉未知态。
 */
enum class FireAttackRule(val label: String) {
    /** 火势蔓延：风助火威叠加干热天气，折损放大，目标士气降一级。 */
    SPREAD("火势蔓延"),

    /** 火势受控：风弱或气温偏低，按基础折损结算，目标士气降一级。 */
    CONTAINED("火势受控"),

    /** 强风反噬：大风乱卷致火势失控，目标仅轻微波及且阵脚不乱；攻方反噬损失待攻方字段建模。 */
    BACKFIRE("强风反噬");
}

/**
 * 单次火攻结算结论（B12，纯数据，供事件分支与单测逐例核对）。
 *
 * @param rule 三态裁决结果
 * @param damageFrac 最终折损比例（0..1，占目标当前兵力）
 * @param moraleDrops 目标是否降一级士气；BACKFIRE 为 false
 * @param note 可读说明（风 / 温取值与裁决理由，进事件日志）
 */
internal data class FireAttackOutcome(
    val rule: FireAttackRule,
    val damageFrac: Double,
    val moraleDrops: Boolean,
    val note: String,
)

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

    /**
     * B13/M5：解码出的 0..1 归一化栅格原始产物。model 接纳栅格字段前（挂账 D24），
     * 暂挂 controller internal 属性供桥接层 / 同包单测读取；不改 model 包内模型定义。
     * null = 本次装配未走 heightMap 解码通路（壳层直接传入了 [raster]，或字段空白）。
     */
    internal var heightMapRaster: HeightMapRaster? = null
        private set

    /**
     * 据点当前归属表（据点标识 → 阵营 id；B7/M3）。
     * 每回合由 [updateCaptureTracker] 按活军位置刷新；分支存档恢复时优先整表回写快照记录，
     * 旧档缺字段再按恢复后的军位重建。判定层（DEFEND 失守）每回合读本成员，不再临时现算，
     * 使回滚后的归属与存档时刻一致，不被旧时间线的活军位置污染。
     */
    private val pointHolders: MutableMap<String, String> = mutableMapOf()
    private val _eventLog: MutableList<String> = mutableListOf()

    /** 本回合每支军队的行军格数（士气推进消费）。 */
    private val marchCells: MutableMap<String, Int> = mutableMapOf()

    /**
     * 本回合已因交战被降过一级士气的军队 id。
     * 一回合内同一支军队可能被多支友军接连接战；士气降级每回合至多结算一次，避免两场
     * 遭遇叠加把 STEADY 连降两级到 BROKEN 当场解散（B9-7 同格 2v1 误溃散根因）。
     * 事件直接置 BROKEN（MORALE_CHANGE）不经此表，解散语义不变。
     */
    // F-73 待规格化：士气降级每回合至多降一级的节流口径为自创规则，规格原文无回合节流条款（审计 表三#5，待重做后回填规格出处）
    private val moraleDowngradedThisRound: MutableSet<String> = mutableSetOf()

    /**
     * 决策层情报表（B9）：观察军 id → 目标军 id → 已知情报。
     * 开局按部署情报播种各方初始位置，此后每回合情报更新覆盖；意图裁决 / 侧翼寻路
     * 只准读这张表的报告位置，不得回读敌军实时 [Corps.position] / [Corps.latLng]。
     */
    private val intel: MutableMap<String, MutableMap<String, IntelRecord>> = mutableMapOf()

    /**
     * 视图层情报表（B9）：仅记录本方 / 盟友观察者真正目睹过的敌军。快照里未观测敌军
     * 据此下发上次已知位置 + 模糊半径；从未目睹则 report 留空（F8 口径不变）。
     * 与 [intel] 分开：部署情报只供决策层推演，不向玩家透视开局部署。
     */
    private val reports: MutableMap<String, MutableMap<String, IntelRecord>> = mutableMapOf()

    /**
     * 单条情报记录（B9）。
     *
     * @param geo 上次已知经纬度；决策层播种后非空；视图层从未目睹为 null
     * @param cell 上次已知格（距离 / 寻路口径）；geo 非空时必有
     * @param seenTurn 最近一次目睹 / 接触交战回合号；0 表示开局部署情报播种
     * @param radiusCells 模糊半径（格）：刚目睹为 0；陈旧情报按龄期 + 目击距离放大（见 [recordRadius]）
     * @param seenDistCells 最近一次目睹时两军距离（八向格）：距离越远方位判读越粗，
     *        作为半径的距离项基数；开局播种取目标兵种视野半径，接触交战为 0（贴脸）
     */
    private data class IntelRecord(
        val geo: Geo?,
        val cell: io.legado.app.ui.map.crossing.model.Cell?,
        val seenTurn: Int,
        val radiusCells: Int,
        val seenDistCells: Int,
    )

    /** 降级标志：JSON 残缺 / 高程图走合成兜底 / 军队为空，任一命中即明示壳层。 */
    val degraded: Boolean get() = definition.degraded || raster.degraded || corps.isEmpty()

    val warnings: List<String> get() = definition.warnings

    /**
     * 归一后的风速（m/s）与气温（℃），推演层只消费这两份归一值，不直接读原始 JSON 数值。
     * 缺天气块 → 静风 + 常温；NaN / 无穷 / 越界一律夹紧并记 [weatherNotes]，极端值不崩。
     */
    private val windMps: Double
    private val temperatureC: Double

    /** 风温归一告警：载入时一次性记录，供单测与壳层排查异常天气数据。 */
    private val weatherNotes: MutableList<String> = mutableListOf()

    init {
        val rawWind = definition.weather?.windMps
        val rawTemp = definition.weather?.temperatureC
        windMps = normalizeWindMps(rawWind)
        temperatureC = normalizeTemperatureC(rawTemp)
        if (rawWind != null && (rawWind.isNaN() || rawWind.isInfinite() ||
                rawWind < 0.0 || rawWind > WIND_MAX_MPS)
        ) {
            weatherNotes += "风速异常（$rawWind）：已归一到 $windMps m/s"
        }
        if (rawTemp != null && (rawTemp.isNaN() || rawTemp.isInfinite() ||
                rawTemp < TEMP_MIN_C || rawTemp > TEMP_MAX_C)
        ) {
            weatherNotes += "气温异常（$rawTemp℃）：已归一到 $temperatureC℃"
        }
        instantiateCorps()
    }

    /** 测试 / 壳层口径出口：风温归一告警列表（只读）。 */
    internal fun weatherDiagnostics(): List<String> = weatherNotes.toList()

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
                visionRadiusCells = def.type.visionRadiusCells,
            )
            marchCells[def.id] = 0
        }
        seedIntel()
        // 开局即有军队驻点时，归属表先按初始军位播种；首回合 tracker 再统一刷新。
        pointHolders.putAll(buildPointHolders())
    }

    /**
     * 开局部署情报播种（B9）：决策层开局即掌握各方初始部署，位置是"开局报告"而非实时透视；
     * 此后敌军机动只有被目睹才会刷新进情报表。视图情报表 [reports] 不播种，玩家未目睹不下发。
     */
    private fun seedIntel() {
        for (observer in corps) {
            for (enemy in enemiesOf(observer)) {
                intel.getOrPut(observer.id) { mutableMapOf() }[enemy.id] = IntelRecord(
                    geo = enemy.latLng,
                    cell = enemy.position,
                    seenTurn = 0,
                    radiusCells = enemy.type.visionRadiusCells,
                    seenDistCells = enemy.type.visionRadiusCells,
                )
            }
        }
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
        // 回合级临时状态清零：交战士气降级标记随新回合重置。
        moraleDowngradedThisRound.clear()

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
            pointHolders = pointHolders,
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
                // B12：火攻不再是固定三成五；按归一后的风速 / 气温裁决三态（蔓延 / 受控 / 反噬），
                // 折损倍率与士气是否降级都由 [FireAttackOutcome] 收口。
                val outcome = evaluateFireAttack(windMps, temperatureC, event.value)
                val loss = target.strength * outcome.damageFrac
                target.applyLoss(loss)
                if (outcome.moraleDrops) target.morale = target.morale.downgrade()
                val moralePart = if (outcome.moraleDrops) "士气下降" else "阵脚未乱"
                log("事件「${event.reason}」：${label} 遭火攻（${outcome.rule.label}），" +
                    "折损 ${loss.round1()}，$moralePart；${outcome.note}")
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
        val known = HashMap<String, Geo>()
        val table = intel.getOrPut(corps.id) { mutableMapOf() }
        val reportTable = reports.getOrPut(corps.id) { mutableMapOf() }
        for (enemy in enemiesOf(corps)) {
            val distCells = octile(corps.position, enemy.position)
            val visible = distCells <= corps.visionRadiusCells && map.visible(corps.position, enemy.position)
            val record = when {
                // 目睹：情报刷新为真值，时间戳记本回合，目击距离留档，半径清零。
                visible -> IntelRecord(
                    geo = enemy.latLng,
                    cell = enemy.position,
                    seenTurn = turn,
                    radiusCells = 0,
                    seenDistCells = octileCells(corps.position, enemy.position),
                )
                else -> {
                    // 未目睹：已知位置不随时间推进自动刷新为真值；保留上次已知位置 / 目睹时刻 / 目击距离，
                    // 半径按龄期 + 距离现算（[recordRadius]）。缺历史条目的兜底为「位置未知」空记录，
                    // 不回退敌军实时坐标——播种后正常不会命中，此处是防御性兜底。
                    val prev = table[enemy.id]
                    prev?.copy(radiusCells = recordRadius(prev, enemy.type))
                        ?: IntelRecord(geo = null, cell = null, seenTurn = turn, radiusCells = MAX_STALE_RADIUS_CELLS, seenDistCells = 0)
                }
            }
            table[enemy.id] = record
            // 视图情报表只在真正目睹时落条目；陈旧条目保留旧位置，龄期半径在快照聚合时现算。
            if (visible) reportTable[enemy.id] = record
            record.geo?.let { known[enemy.id] = it }
        }
        corps.lastKnown = known
    }

    /**
     * 决策层敌情视图（B9）：意图裁决 / 侧翼寻路只消费本方法产出的拷贝。
     * 位置一律替换为情报表中的上次已知位置；战力等字段仍为真值。情报表开局已播种，
     * 故不存在"查无情报"的敌军；从未目睹的敌军也只有开局部署报告，不含其实时机动。
     * 情报表缺条 / 位置为空的敌军不进感知集：宁可让决策层当作"未知"，也不回读实时坐标。
     */
    private fun perceivedEnemies(observer: Corps): List<Corps> =
        enemiesOf(observer).mapNotNull { enemy ->
            val rec = intel[observer.id]?.get(enemy.id) ?: return@mapNotNull null
            val recCell = rec.cell ?: return@mapNotNull null
            val recGeo = rec.geo ?: return@mapNotNull null
            enemy.copy(position = recCell, latLng = recGeo)
            // 按军 id 去重：同一敌军在感知集只落一条（B9-7 同回合多观察者 / 重复情报写入
            // 不得产生重复条目）；去重保留首条记录，不靠截断列表条数糊弄唯一性断言。
        // F-73 待规格化：感知集按军 id 去重的唯一性口径为自创规则，规格原文无 distinct 条款（审计 表三#5，待重做后回填规格出处）
        }.distinctBy { it.id }

    private fun resolveIntent(corps: Corps, overrides: Map<String, Intent>?): IntentResolver.Decision {
        val override = overrides?.get(corps.id)
        if (override != null) {
            return IntentResolver.Decision(override, corps.target?.let { projection.toCell(it) }, "手动覆盖意图")
        }
        val allies = alliesOf(corps)
        // B9：意图裁决读敌情只走感知位置（上次已知），不回读敌军实时坐标。
        val enemies = perceivedEnemies(corps)
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
            // B9：侧翼避敌视野只用感知位置；真实敌军位置不进寻路层。
            // B8R2：逐敌传「感知位置 + 敌军自身 visionRadiusCells」，不再只取 position 把半径丢掉；
            // 混编兵种时高视野 / 低视野敌军各按自身半径被闸门。
            Intent.FLANK -> pathfinder.flankPath(
                corps.position,
                target,
                corps.type,
                perceivedEnemies(corps).map { EnemySight(it.position, it.visionRadiusCells) },
            )
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
            // 同回合已降过级的军队不再叠加降级（见 [moraleDowngradedThisRound] 注释）。
            if (moraleDowngradedThisRound.add(corps.id)) corps.morale = corps.morale.downgrade()
        } else {
            if (moraleDowngradedThisRound.add(target.id)) target.morale = target.morale.downgrade()
        }
        if (outcome.defenderRetreated && target.combatReady()) retreatFrom(target, corps.position)

        // 接触交战即情报刷新（B9 DoD-2）：贴脸是物理事实，视同最近距离侦察，双方互写真值 + 时间戳。
        refreshContactIntel(corps, target)

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

    /**
     * 接触交战情报刷新（B9 DoD-2）：两军贴脸接触是物理事实，不依赖视野通视；接战后双方
     * 决策层情报表互写当前真值，时间戳记本回合，目击距离 0（贴脸），半径清零。
     * 视图层 [reports] 只在观察者属本方 / 盟友阵营时落条目，敌方互侦不向玩家透传。
     * 适用条件：仅 [resolveEncounters] 确认物理相邻接战后调用；行军路过、视野瞥见走侦察口径。
     */
    private fun refreshContactIntel(a: Corps, b: Corps) {
        fun write(observer: Corps, enemy: Corps) {
            val record = IntelRecord(
                geo = enemy.latLng,
                cell = enemy.position,
                seenTurn = turn,
                radiusCells = 0,
                seenDistCells = 0,
            )
            intel.getOrPut(observer.id) { mutableMapOf() }[enemy.id] = record
            if (isFriendlySide(definition.victory.side, observer.sideId)) {
                reports.getOrPut(observer.id) { mutableMapOf() }[enemy.id] = record
            }
        }
        write(a, b)
        write(b, a)
    }

    // ── 据点 / 快照 / 分支 ────────────────────────────────────

    private fun updateCaptureTracker() {
        // 归属表先清空再按本回合活军位置重算：据点易主后旧持有者不得在表中残留为死条目。
        pointHolders.clear()
        val point = definition.victory.point ?: return
        val key = pointKey(point)
        val holder = corps.any { it.sideId == definition.victory.side && it.combatReady() && nearPoint(it.latLng, point) }
        captureHold[key] = if (holder) (captureHold[key] ?: 0) + 1 else 0
        pointHolders.putAll(buildPointHolders())
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
            // B9 DoD-1/3：已观测下发实时坐标；未观测有旧报告 → 主坐标用上次已知位置（模糊）；
            // 从未侦察 → 主坐标写 NaN 占位（情报空白区，见 [BLIND_ZONE_LABEL]），禁止回填真实坐标。
            // 未观测敌军的规划路线一并清空：行军计划属敌方决策信息，随位置一并模糊化。
            val report = if (seen) c.latLng else playerSide?.let { latestReport(it, c.id) }
            val emitGeo: Geo? = when {
                seen -> c.latLng
                report != null -> report
                else -> null
            }
            c.toView(observed = seen, report = report, emitGeo = emitGeo, blurRoute = !seen)
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

    /**
     * 汇总本方 / 盟友观察者对目标军的最新目睹 / 接触报告（B9）；从未目睹返回 null。
     * 取各观察者记录中 seenTurn 最大的一条：多支部队侦察时以最新情报为准；
     * 同回合多观察者重复侦察不会重复落条，later 同回合记录覆盖先写条目，时间戳仍是本回合。
     */
    private fun latestReportRecord(playerSide: String, targetId: String): IntelRecord? =
        reports.asSequence()
            .filter { (observerId, _) ->
                val ob = corps.firstOrNull { it.id == observerId }
                ob != null && isFriendlySide(playerSide, ob.sideId)
            }
            .mapNotNull { it.value[targetId] }
            .maxByOrNull { it.seenTurn }

    private fun latestReport(playerSide: String, targetId: String): Geo? =
        latestReportRecord(playerSide, targetId)?.geo

    /** 保存分支存档（深拷贝运行态，避免后续回合改动污染存档）。 */
    fun saveBranch(): BranchSnapshot = BranchSnapshot(
        turn = turn,
        elapsedDays = elapsedDays,
        corps = corps.map { it.copy() },
        firedEventIds = firedEvents.toSet(),
        captureHold = captureHold.toMap(),
        pointHolders = pointHolders.toMap(),
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
        restorePointHolders(branch.pointHolders, branch.captureHold)
        // B9：情报随存档回滚——按恢复后的军位重新播种决策层情报；视图目睹记录清空，需重新侦察。
        // 存档兼容（DoD-4）：旧版 BranchSnapshot 不含任何情报表字段（情报是可由军位重建的派生状态），
        // 此处一律重建而非读取存档；空军队 / 缺字段旧档循环自然空转，降级为"位置未知"，不抛异常。
        intel.clear()
        reports.clear()
        moraleDowngradedThisRound.clear()
        for (observer in corps) for (enemy in enemiesOf(observer)) {
            intel.getOrPut(observer.id) { mutableMapOf() }[enemy.id] = IntelRecord(
                geo = enemy.latLng,
                cell = enemy.position,
                seenTurn = branch.turn,
                radiusCells = 0,
                seenDistCells = 0,
            )
        }
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
    // 为什么改：形参原名 corps 与成员 corps 同名，实体过滤被写成对单个 Corps 调 .filter（编译红）。
    // 适用条件：入参是被审视的那支部队自身，全集一律取成员 corps。
    private fun enemiesOf(self: Corps): List<Corps> =
        corps.filter {
            it.id != self.id && it.sideId != self.sideId &&
                it.sideId !in alliedSidesWith(self.sideId) && it.combatReady()
        }

    private fun alliesOf(self: Corps): List<Corps> =
        corps.filter {
            it.id != self.id &&
                (it.sideId == self.sideId || it.sideId in alliedSidesWith(self.sideId)) &&
                it.combatReady()
        }

    /** 测试口径出口：同包单测直接断言敌 / 友集合划分；生产代码一律走私有 [enemiesOf] / [alliesOf]。 */
    internal fun filterEnemiesOf(corps: Corps): List<Corps> = enemiesOf(corps)

    /** 测试口径出口：同包单测直接断言敌 / 友集合划分；生产代码一律走私有 [enemiesOf] / [alliesOf]。 */
    internal fun filterAlliesOf(corps: Corps): List<Corps> = alliesOf(corps)

    /** 测试口径出口：决策层感知到的敌军拷贝（位置为上次已知位置，非实时坐标；B9）。 */
    internal fun perceivedEnemiesOf(corpsId: String): List<Corps> =
        corps.firstOrNull { it.id == corpsId }?.let { perceivedEnemies(it) } ?: emptyList()

    /**
     * 单条记录当前模糊半径（格）＝ 距离项 + 时间项，封顶 [MAX_STALE_RADIUS_CELLS]。
     * 三因子口径（B9 DoD-1）：
     * - 侦察：是否目睹 / 接触决定基准精度，刚目睹或贴脸接触 age=0 → 半径 0；
     * - 距离：目击距离越远方位判读越粗，距离项取半距并封顶到兵种视野半径；
     * - 时间：旧报告随行军逐日贬值，每回合外扩 [STALE_DRIFT_CELLS_PER_TURN] 格。
     * 为什么放大而非恒定：恒定半径意味着十回合前的旧报告与刚目睹同样精确，违背情报贬值常识；
     * 为什么封顶：半径涨到全图后"方位不明"即可，继续外扩无决策价值。
     * 适用条件：决策层感知拷贝与视图层半径出口共用本口径；龄期为负（存档回滚边界）视同刚目睹。
     */
    private fun recordRadius(record: IntelRecord, type: ArmyType): Int {
        val age = turn - record.seenTurn
        if (age <= 0) return 0
        val distanceTerm = (record.seenDistCells / 2).coerceIn(0, type.visionRadiusCells)
        val ageTerm = age * STALE_DRIFT_CELLS_PER_TURN
        return (distanceTerm + ageTerm).coerceAtMost(MAX_STALE_RADIUS_CELLS)
    }

    /**
     * 测试 / 壳层口径出口（B9）：目标军对本方当前的模糊半径（格）。
     * 已目睹且本回合在视野内为 0；陈旧情报按龄期 + 目击距离放大；从未目睹返回 null。
     * 注：[CorpsView] 暂无半径字段，正式下发壳层需 model 扩字段（见交付文档"需总控批准"项）。
     */
    internal fun reportRadiusCells(targetId: String): Int? {
        val record = latestReportRecord(definition.victory.side ?: return null, targetId) ?: return null
        val target = corps.firstOrNull { it.id == targetId } ?: return record.radiusCells
        return recordRadius(record, target.type)
    }

    /**
     * 情报空白区占位文案出口（B9 DoD-3）：目标为敌方、且本方 / 盟友从未侦察或接触过时返回
     * [BLIND_ZONE_LABEL]；友方单位、已有报告的敌军返回 null。为什么给占位而不是整行隐藏：
     * 玩家仍需知道"此方位有未知编制"这一存在性线索，只是不泄露坐标 / 路线；整行空白会让
     * 壳层误以为敌军已被歼灭（F8 观察口径与存亡状态是两个维度）。
     */
    internal fun blindZoneLabel(targetId: String): String? {
        val target = corps.firstOrNull { it.id == targetId } ?: return null
        val playerSide = definition.victory.side ?: return null
        if (isFriendlySide(playerSide, target.sideId)) return null
        if (latestReportRecord(playerSide, targetId) != null) return null
        return BLIND_ZONE_LABEL
    }

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
        // B12：标量风速与极端气温再叠一层行军折减（恒 ≤1，坏天气只拖慢不加速）；
        // B2 的 condition 定性折减仍在 [TerrainCost.marchSpeed] 内，两层口径见复审报告叠加说明。
        val adjusted = speed * windMarchFactor(windMps) * tempMarchFactor(temperatureC)
        return (BASE_DAILY_CELLS * adjusted).roundToInt().coerceAtLeast(1)
    }

    private fun nearPoint(a: Geo, b: Geo): Boolean {
        val dLat = Math.abs(a.lat - b.lat) * 111.32
        val dLng = Math.abs(a.lng - b.lng) * 111.32
        return Math.hypot(dLat, dLng) <= VictoryChecker.REACH_RADIUS_KM
    }

    // 八向格距统一走顶层单源 [octile]（B8R3）；本类不再私建距离公式。
    // Int 存档字段用 [octileCells] 取整，半径闸门 / 最近目标比较直接用双精度 [octile]。

    private fun Double.round1(): Double = (this * 10).roundToInt() / 10.0

    private fun log(message: String) {
        _eventLog += message
        if (_eventLog.size > LOG_CAPACITY) _eventLog.removeAt(0)
    }

    /**
     * 快照视图组装（B9）。
     * @param emitGeo 下发到主坐标 [CorpsView.lat]/[CorpsView.lng] 的位置；null 表示情报空白区，
     *        写 [Double.NaN] 占位——NaN 经投影 toInt 钳到边缘格不会崩，壳层须按 [observed]=false
     *        且 report 为空跳过绘子（壳层门控属交接项）；禁止此处回填 [latLng] 真实坐标。
     * @param blurRoute 为 true 时清空规划路线：未观测敌军的行军计划不随位置一起下发。
     */
    private fun Corps.toView(
        observed: Boolean = true,
        report: io.legado.app.ui.map.crossing.model.Geo? = null,
        emitGeo: io.legado.app.ui.map.crossing.model.Geo? = null,
        blurRoute: Boolean = false,
    ): CorpsView = CorpsView(
        id = id,
        name = name,
        sideId = sideId,
        role = role,
        type = type,
        strength = strength,
        fatigue = fatigue,
        morale = morale,
        intent = intent,
        lat = emitGeo?.lat ?: Double.NaN,
        lng = emitGeo?.lng ?: Double.NaN,
        targetLat = target?.lat,
        targetLng = target?.lng,
        alive = alive,
        surrendered = surrendered,
        route = if (blurRoute) emptyList() else route.map { projection.toGeo(it) },
        observed = observed,
        reportLat = report?.lat,
        reportLng = report?.lng,
    )

    /**
     * 恢复据点归属表（B7/M3）：快照记录「据点标识 → 阵营」，恢复时整表回写，不沿用旧时间线
     * 的活军推导。据点 key 口径与 [captureHold] / [VictoryChecker] 一致（lng,lat 四位小数）。
     *
     * 旧档兼容（DoD-4）：旧版存档缺 [pointHolders]（甚至 [captureHold]）字段——这两类都是可由
     * 军位重建的派生状态，故按恢复后的军位重新推导归属；若派生到当前占据方，占领计数从 1 起算
     * （旧档无从得知此前已占几回合，不臆造历史计数）。适用条件：存档仅内存流转、不落盘；
     * 军队列表为空或据点半径内无可战军队 → 两表自然空转，不抛异常。
     *
     * 为什么不再按军 id 回写 [captureHold]：计数表 key 是据点标识，按军 id 写键会形成命名空间
     * 错配的死条目，且会清掉刚从快照回写的连续回合数（翻倍 / 串线根因）。
     */
    private fun restorePointHolders(holders: Map<String, String>, holdCounts: Map<String, Int>) {
        pointHolders.clear()
        if (holders.isNotEmpty()) {
            pointHolders.putAll(holders)
            return
        }
        val rebuilt = buildPointHolders()
        pointHolders.putAll(rebuilt)
        // 旧档连计数表也缺：仅对当前派生占据方补 1 回合基线，历史占回合数不臆造。
        if (holdCounts.isEmpty()) {
            rebuilt.forEach { (key, _) -> captureHold[key] = 1 }
        }
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

        // ── B12 火攻 / 风温系数 ──────────────────────────────────────
        // 以下全部为**经验默认值，待用户校**：按定性战场经验标定，非实测气象 / 弹道数据；
        // 重标定时实现与本单测试须同批修改，避免断言与实现错位。

        /** 风速归一上界（m/s）：超出按大风封顶，不做线性外推。 */
        const val WIND_MAX_MPS: Double = 40.0

        /** 气温归一区间（℃）：低于 / 高于此物理区间夹紧；NaN / 无穷 / 缺省回落到常温。 */
        const val TEMP_MIN_C: Double = -60.0
        const val TEMP_MAX_C: Double = 60.0
        const val TEMP_DEFAULT_C: Double = 15.0

        /** 静风上限（m/s）：低于此值风不助燃，也不折减行军。 */
        const val WIND_CALM_MPS: Double = 2.0

        /** 大风阈值（m/s）：达到即判火势失控反噬（[FireAttackRule.BACKFIRE]）。 */
        const val WIND_GALE_MPS: Double = 10.0

        /** 干热助燃阈值（℃）：达到即给干燥加成。 */
        const val TEMP_HOT_DRY_C: Double = 28.0

        /** 寒冷难燃阈值（℃）：低于此值火势被湿冷压制。 */
        const val TEMP_COLD_C: Double = 0.0

        /** SPREAD 折损放大倍率（风助火势）。 */
        const val FIRE_SPREAD_MULT: Double = 1.35

        /** BACKFIRE 折损收窄倍率（反噬中目标仅轻微波及）。 */
        const val FIRE_BACKFIRE_MULT: Double = 0.40

        /** 干热天气折损加成倍率。 */
        const val TEMP_DRY_BONUS_MULT: Double = 1.15

        /** 寒冷天气折损压制倍率。 */
        const val TEMP_COLD_PENALTY_MULT: Double = 0.65

        /** 强风行军折减下限：风速自静风到大线性折减，不再往下压。 */
        const val WIND_MARCH_FLOOR: Double = 0.85

        /** 酷热阈值（℃）：中暑行军折减。 */
        const val TEMP_HEAT_C: Double = 35.0

        /** 严寒阈值（℃）：冻阻行军折减。 */
        const val TEMP_FREEZING_C: Double = -10.0

        /** 酷热行军系数（恒 ≤1）。 */
        const val TEMP_HEAT_MARCH_FACTOR: Double = 0.90

        /** 严寒行军系数（恒 ≤1）。 */
        const val TEMP_FREEZING_MARCH_FACTOR: Double = 0.85

        /** 风速归一：null / NaN / 无穷 → 静风；负值按 0；超过上界封顶。 */
        internal fun normalizeWindMps(raw: Double?): Double {
            if (raw == null || raw.isNaN() || raw.isInfinite()) return 0.0
            return raw.coerceIn(0.0, WIND_MAX_MPS)
        }

        /** 气温归一：null / NaN / 无穷 → 常温；超出物理区间夹紧。 */
        internal fun normalizeTemperatureC(raw: Double?): Double {
            if (raw == null || raw.isNaN() || raw.isInfinite()) return TEMP_DEFAULT_C
            return raw.coerceIn(TEMP_MIN_C, TEMP_MAX_C)
        }

        /** 事件 value 折损比例：空白 / 非法数字 / 越界回落到默认比例，不抛异常。 */
        private fun fireFrac(raw: String?, default: Double): Double =
            raw?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: default

        /**
         * 火攻三态裁决（纯函数，供事件分支与表驱动单测复用）。
         *
         * 裁决顺序（先风后温，强风优先）：
         * 1. 风速 ≥ [WIND_GALE_MPS] → [FireAttackRule.BACKFIRE]：大风乱卷失控，火势反噬；
         * 2. 气温 < [TEMP_COLD_C] → [FireAttackRule.CONTAINED]：湿冷难燃；
         * 3. 风速 > [WIND_CALM_MPS] 且气温 ≥ [TEMP_HOT_DRY_C] → [FireAttackRule.SPREAD]；
         * 4. 其余 → [FireAttackRule.CONTAINED]。
         *
         * 折损倍率 = 基础比例 × 状态倍率 × 气温倍率，最终钳到 0..1；BACKFIRE 不降级士气。
         */
        internal fun evaluateFireAttack(
            windMps: Double,
            temperatureC: Double,
            eventFrac: String?,
            baseFrac: Double = LOSS_FIRE_ATTACK,
        ): FireAttackOutcome {
            val rule = when {
                windMps >= WIND_GALE_MPS -> FireAttackRule.BACKFIRE
                temperatureC < TEMP_COLD_C -> FireAttackRule.CONTAINED
                windMps > WIND_CALM_MPS && temperatureC >= TEMP_HOT_DRY_C -> FireAttackRule.SPREAD
                else -> FireAttackRule.CONTAINED
            }
            val tempMult = when {
                temperatureC >= TEMP_HOT_DRY_C -> TEMP_DRY_BONUS_MULT
                temperatureC < TEMP_COLD_C -> TEMP_COLD_PENALTY_MULT
                else -> 1.0
            }
            val ruleMult = when (rule) {
                FireAttackRule.SPREAD -> FIRE_SPREAD_MULT
                FireAttackRule.CONTAINED -> 1.0
                FireAttackRule.BACKFIRE -> FIRE_BACKFIRE_MULT
            }
            val frac = fireFrac(eventFrac, baseFrac)
            val damageFrac = (frac * ruleMult * tempMult).coerceIn(0.0, 1.0)
            val moraleDrops = rule != FireAttackRule.BACKFIRE
            val note = "风速 ${windMps} m/s，气温 ${temperatureC}℃"
            return FireAttackOutcome(rule, damageFrac, moraleDrops, note)
        }

        /** 风速行军折减（恒 ≤1）：静风不折减；大风自 [WIND_CALM_MPS] 到 [WIND_GALE_MPS] 线性压到 [WIND_MARCH_FLOOR]。 */
        internal fun windMarchFactor(windMps: Double): Double {
            if (windMps <= WIND_CALM_MPS) return 1.0
            val t = ((windMps - WIND_CALM_MPS) / (WIND_GALE_MPS - WIND_CALM_MPS))
                .coerceIn(0.0, 1.0)
            return 1.0 - t * (1.0 - WIND_MARCH_FLOOR)
        }

        /** 气温行军折减（恒 ≤1）：酷热 / 严寒各取一档系数，常温不折减。 */
        internal fun tempMarchFactor(temperatureC: Double): Double = when {
            temperatureC >= TEMP_HEAT_C -> TEMP_HEAT_MARCH_FACTOR
            temperatureC <= TEMP_FREEZING_C -> TEMP_FREEZING_MARCH_FACTOR
            else -> 1.0
        }

        /** 快照事件条保留条数。 */
        const val LOG_TAIL: Int = 8

        /** 事件日志容量上限（超出滚出最早一条）。 */
        const val LOG_CAPACITY: Int = 64

        /** 久未观测模糊半径每回合外扩格数：旧报告随行军逐日贬值，龄期越长方位越不确。 */
        const val STALE_DRIFT_CELLS_PER_TURN: Int = 1

        /** 模糊半径封顶（格）：超出封顶按"方位不明"处理，避免半径涨到全图。 */
        const val MAX_STALE_RADIUS_CELLS: Int = 8

        /** 情报空白区占位文案（B9 DoD-3）：从未侦察 / 接触的敌军不下发坐标，壳层按此标盲区。 */
        const val BLIND_ZONE_LABEL: String = "情报空白区：未侦察到该部，方位不明"

        /**
         * 模糊半径（格）策略：**随未观测龄期单调放大，封顶 [MAX_STALE_RADIUS_CELLS]**。
         * 为什么放大而不是恒定：恒定半径意味着十回合前的旧报告与刚目睹同样精确，违背情报贬值常识；
         * 放大速率取每回合 1 格（约一日行军造成的位置不确定性），基准取目标兵种视野半径，封顶防失控。
         * 龄期 0（刚目睹）半径为 0：目睹即精确到格。
         * 注：本函数是纯时间衰减锚点（供单测 / 其它层复用）；情报表记录的三因子半径
         * （侦察 + 距离 + 时间）走实例方法 `recordRadius`，两处口径不得混用。
         */
        internal fun fuzzyRadiusCells(type: ArmyType, ageTurns: Int): Int {
            if (ageTurns <= 0) return 0
            return (type.visionRadiusCells + ageTurns * STALE_DRIFT_CELLS_PER_TURN)
                .coerceAtMost(MAX_STALE_RADIUS_CELLS)
        }

        /**
         * 测试专用装配故障桩（internal，同包单测可见；生产路径恒为 null，不进公开 API）。
         * 非空时 [assemble] 在实例化前原样抛出该异常，使顶层 catch → [assembleDegraded]
         * 降级分支可被单测触发；用例结束必须复位为 null，避免跨用例污染。
         */
        @JvmField
        internal var assemblyFault: Throwable? = null

        /**
         * 载入入口：JSON 残缺 / 高程图缺失全部兜底，永不抛异常。
         *
         * @param raster 壳层解码 PNG 得到的高程栅格；null 时用内置合成默认场并置降级标志
         * @param ownSideId 己方阵营 id；缺省取第一支军队所在阵营
         */
        fun load(json: String?, raster: HeightRaster? = null, ownSideId: String? = null): CampaignController {
            // 顶层兜底覆盖解析 + 装配全链：parser 漏网异常与投影 / 栅格 / 地形 / 寻路 / 实例化
            // 任一环节抛出，都走降级空战场，不穿透到 UI（B4 验收修 1：parse 原在 try 外，现一并纳入）。
            var definition: CampaignDefinition? = null
            return try {
                val parsed = CampaignJsonParser.parse(json)
                definition = parsed
                assemble(parsed, raster, ownSideId)
            } catch (t: Throwable) {
                // 降级空战役：错误文案进 warnings 供壳层读取；不崩、不留空白战场。
                // assembleDegraded 输入全部为常量空表，不依赖原始残缺 definition 再次装配。
                val reason = "战役载入失败：${t.javaClass.simpleName}${t.message?.let { "（$it）" } ?: ""}，已进入空战场降级态"
                val baseDefinition = definition ?: CampaignJsonParser.degradedFallback()
                val degradedDef = baseDefinition.copy(
                    degraded = true,
                    warnings = baseDefinition.warnings + reason,
                )
                assembleDegraded(degradedDef, ownSideId)
            }
        }

        /** 正常装配链：栅格兜底 → 投影 → 地形 → 寻路 → 控制器实例化。 */
        private fun assemble(
            definition: CampaignDefinition,
            raster: HeightRaster?,
            ownSideId: String?,
        ): CampaignController {
            assemblyFault?.let { throw it }
            // 壳层显式传入栅格时优先采用（既有覆盖通路不变）；否则按 definition.heightMap 解码装配。
            val (resolvedDef, effectiveRaster, decoded) = if (raster != null) {
                Triple(definition, raster, null)
            } else {
                val resolved = resolveHeightMap(definition)
                Triple(resolved.first, resolved.second, resolved.third)
            }
            val projection = GeoProjection(resolvedDef.focus, GRID_N)
            val map = TerrainMap(
                raster = effectiveRaster,
                projection = projection,
                rivers = resolvedDef.terrain.rivers,
                ridges = resolvedDef.terrain.ridges,
            )
            val pathfinder = Pathfinder(map, resolvedDef.weather?.condition)
            val fallbackSide = resolvedDef.armies.firstOrNull()?.sideId ?: resolvedDef.sides.firstOrNull()?.id ?: "own"
            val side = ownSideId ?: fallbackSide
            val controller = CampaignController(resolvedDef, effectiveRaster, projection, map, pathfinder, side)
            controller.heightMapRaster = decoded
            return controller
        }

        /**
         * B13/M5：按 [definition] 的 `heightMap` 字符串解码高程栅格。
         *
         * - 引擎层不引 Android 资源 API，平台 assets / 文件系统 reader 由壳层桥接注入；
         *   本通路 reader 传 null——内联 base64 纯 JVM 直解，资产 / 路径类在 reader 注入前
         *   由解码器回落确定性合成场（与既有降级口径一致）。
         * - 解码失败（回落合成场）时把 warning 并入 definition 既有 warnings 表，不新增第二条
         *   警告通道；`degraded` 随之置真。字段空白时保持旧合成兜底口径，不重复记 warning。
         *
         * @return 解析后的 definition（可能追加 warning）、可消费的 [HeightRaster]、0..1 原始栅格（可空）。
         */
        private fun resolveHeightMap(
            definition: CampaignDefinition,
        ): Triple<CampaignDefinition, HeightRaster, HeightMapRaster?> {
            val raw = definition.heightMap
            if (raw.isNullOrBlank()) {
                return Triple(
                    definition,
                    HeightRaster.synthetic(seed = SEED_FALLBACK, gridN = GRID_N, degraded = true),
                    null,
                )
            }
            val decoded = CampaignJsonParser.decodeHeightMap(raw, reader = null)
            val heightRaster = toHeightRaster(decoded)
            val resolvedDef = if (decoded.degraded) {
                val note = "heightMap 未解码为真实高程图：回落确定性合成高程场（来源 ${decoded.source}）"
                definition.copy(degraded = true, warnings = definition.warnings + note)
            } else {
                definition
            }
            return Triple(resolvedDef, heightRaster, decoded)
        }

        /**
         * 0..1 归一化栅格 → 既有 [HeightRaster]（0..255 `IntArray`）。
         *
         * 为什么 0..255：[HeightRaster] 是地形消费侧既有口径，米制换算
         * `metersFor = value / 255 * DEM_MAX_METERS`，TerrainMap / 通视 / 坡度全部按 0..255
         * 取像素；解码器内部归一化到 0..1 仅为隔离 PNG 亮度值域。换算集中在装配点这一处，
         * 避免消费侧混用两套值域；四舍五入后夹紧 0..255，越界值不穿透到地形层。
         */
        private fun toHeightRaster(raster: HeightMapRaster): HeightRaster {
            val data = IntArray(raster.size) { i ->
                (raster.data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
            return HeightRaster.create(raster.width, raster.height, data, degradedHint = raster.degraded)
        }

        /** 顶层兜底装配：原点聚焦 + 合成高程场 + 空地形空军队，保证降级态可建、可出快照。 */
        private fun assembleDegraded(definition: CampaignDefinition, ownSideId: String?): CampaignController {
            val emptyDef = definition.copy(
                focus = TerrainFocus(Geo(0.0, 0.0), 50.0),
                sides = emptyList(),
                diplomacy = emptyList(),
                armies = emptyList(),
                events = emptyList(),
                terrain = TerrainDef(procedural = true, ridges = emptyList(), rivers = emptyList()),
                weather = null,
            )
            val raster = HeightRaster.synthetic(seed = SEED_FALLBACK, gridN = GRID_N, degraded = true)
            val projection = GeoProjection(emptyDef.focus, GRID_N)
            val map = TerrainMap(
                raster = raster,
                projection = projection,
                rivers = emptyList(),
                ridges = emptyList(),
            )
            val pathfinder = Pathfinder(map, null)
            return CampaignController(emptyDef, raster, projection, map, pathfinder, ownSideId ?: "own")
        }

        /** 内置演示：四渡赤水。 */
        fun demo(): CampaignController = load(DemoCampaigns.CHISHUI_JSON)

        private const val SEED_FALLBACK: Long = 42L
    }
}
