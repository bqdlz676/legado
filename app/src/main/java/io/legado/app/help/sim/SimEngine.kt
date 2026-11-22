package io.legado.app.help.sim

import com.google.gson.Gson
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 落文 · 推演引擎（core:sim / §五，数据驱动，不内置任何战役）
 *
 * 一回合流水线：
 *   按速度降序 → 情报（视野内真位置 / 视野外最后已知 + 模糊）→ 意图（15+ 条规则）
 *   → A* 移动 → 遭遇（兰彻斯特）→ 士气 / 疲劳 / 补给 → 事件 → 胜利判定（7 种）。
 *
 * 换战役只换 campaign JSON + 高度图像素，引擎零改动。
 * 纯 Kotlin：无 Android 类型依赖，JVM 单测可直接 new SimEngine(...)。
 */

// ----------------------------- 数据契约（与 schema 对齐） -----------------------------

data class FocusSpec(val center: List<Double> = listOf(0.0, 0.0), val radius: Double = 50.0)

data class RiverSpec(
    val a: List<Double>? = null,
    val b: List<Double>? = null,
    val pts: List<List<Double>>? = null,
    val w: Double? = null,
    val depth: Double? = null,
)

data class RidgeSpec(
    val x: Double? = null,
    val y: Double? = null,
    val a: List<Double>? = null,
    val b: List<Double>? = null,
    val h: Double? = null,
    val w: Double? = null,
)

data class TerrainSpec(
    val seed: Long? = null,
    val source: String? = null,
    val ridges: List<RidgeSpec>? = null,
    val rivers: List<RiverSpec>? = null,
    val heightPixels: IntArray? = null,
    val minEl: Double? = null,
    val maxEl: Double? = null,
    val bbox: List<Double>? = null,
    val procedural: Boolean? = null,
)

data class SideSpec(val id: String, val name: String, val color: String? = null)

data class DiplomacySpec(val a: String, val b: String, val relation: String)

data class ArmySpec(
    val id: String,
    val name: String,
    val side: String,
    val initialPos: List<Double>,
    val strength: Int,
    val type: String,
    val fatigue: Int = 0,
    val morale: String = "steady",
    val intent: String = "hold",
    val target: List<Double>? = null,
    val supply: Double? = null,
)

data class TriggerSpec(
    val turn: Int? = null,
    val army: String? = null,
    val strengthBelow: Int? = null,
    val moraleIs: String? = null,
)

data class EventSpec(
    val trigger: TriggerSpec? = null,
    val action: String,
    val target: String? = null,
    val value: String? = null,
    val reason: String? = null,
    val targetPos: List<Double>? = null,
) {
    @Transient
    var done: Boolean = false
}

data class VictorySpec(
    val type: String,
    val target: String? = null,
    val side: String? = null,
    val point: List<Double>? = null,
    val clauses: List<VictorySpec>? = null,
)

data class CampaignSpec(
    val id: String,
    val title: String? = null,
    val era: String? = null,
    val context: String? = null,
    val terrainFocus: FocusSpec? = null,
    val heightMap: String? = null,
    val duration: Int = 30,
    val victory: VictorySpec? = null,
    val sides: List<SideSpec>? = null,
    val diplomacy: List<DiplomacySpec>? = null,
    val armies: List<ArmySpec>? = null,
    val events: List<EventSpec>? = null,
    val phases: List<Any>? = null,
    val terrain: TerrainSpec? = null,
)

// ----------------------------- 运行态 -----------------------------

/** 最后已知位置：fuzzy>0 表示视野外漂移后的模糊定位。 */
data class LastKnown(val r: Int, val c: Int, val fuzzy: Int)

class SimArmy(val spec: ArmySpec) {
    val id: String = spec.id
    val name: String = spec.name
    val side: String = spec.side
    val type: String = spec.type

    var strength: Int = spec.strength
    var morale: Morale = Morale.fromString(spec.morale)
    var fatigue: Int = spec.fatigue
    var supply: Double = spec.supply ?: 5.0
    var intent: String = spec.intent
    var target: List<Double>? = spec.target

    var r: Int = 0
    var c: Int = 0
    var pos: DoubleArray = spec.initialPos.toDoubleArray()
    var alive: Boolean = true
    var surrendered: Boolean = false

    var intentActive: String = spec.intent
    var reason: String = "初始意图"
    var path: List<Cell>? = null
    var targetCell: Cell? = null
    val lastSeen = mutableMapOf<String, LastKnown>()
    var hunt: String? = null
    var noSupplyTurn: Int = 0
}

data class MoveRecord(
    val armyId: String,
    val name: String,
    val side: String,
    val intent: String,
    val from: DoubleArray,
    val to: DoubleArray,
    val reason: String,
    val animation: String,
)

data class Highlight(val type: String, val pos: DoubleArray, val label: String)

data class VictoryResult(val winner: String, val type: String, val description: String)

data class TurnResult(
    val turn: Int,
    val moves: List<MoveRecord>,
    val encounters: List<CombatResolver.CombatResult>,
    val highlights: List<Highlight>,
    val events: List<FiredEvent>,
    val notes: List<String>,
    val victory: VictoryResult?,
)

// ----------------------------- 引擎 -----------------------------

class SimEngine(
    val campaign: CampaignSpec,
    val gridN: Int = 96,
    seed: Long = 42,
    heightPixels: IntArray? = null,
) : IntentContext {

    override val terrain: TerrainData = TerrainAnalyzer.analyze(campaign, gridN, heightPixels)

    private val rand: () -> Double = TerrainAnalyzer.mulberry32(seed)

    private val scratch = Pathfinder.Scratch(gridN)

    override val armies: List<SimArmy> = campaign.armies.orEmpty().map { spec ->
        SimArmy(spec).apply {
            val cell = terrain.toCell(spec.initialPos[0], spec.initialPos[1])
            r = cell.r; c = cell.c
        }
    }

    private val sides: Map<String, SideSpec> =
        campaign.sides.orEmpty().associateBy { it.id }

    override val victorySide: String? get() = campaign.victory?.side

    private var turn: Int = 0
    private val log = ArrayList<TurnResult>()

    init {
        // 追击绑定：advance / rush 的军队默认追击最近敌对军（情报驱动，不是死坐标）。
        for (a in armies) {
            if (a.intent != "advance" && a.intent != "rush") continue
            val foes = armies.filter { it.alive && it.id != a.id && relation(a.side, it.side) == "hostile" }
            val best = foes.minByOrNull { hypot((a.r - it.r).toDouble(), (a.c - it.c).toDouble()) }
            if (best != null) a.hunt = best.id
        }
    }

    // ------------------------- 上下文实现 -------------------------

    override fun relation(sideA: String, sideB: String): String {
        if (sideA == sideB) return "allied"
        val row = campaign.diplomacy.orEmpty().firstOrNull {
            (it.a == sideA && it.b == sideB) || (it.a == sideB && it.b == sideA)
        }
        return row?.relation ?: "hostile"
    }

    override fun distKm(a: SimArmy, b: SimArmy): Double =
        hypot((a.r - b.r).toDouble(), (a.c - b.c).toDouble()) * terrain.cellKm

    override fun rand(): Double = rand.invoke()

    override fun toCell(lng: Double, lat: Double): Cell = terrain.toCell(lng, lat)

    override fun planPath(from: Cell, to: Cell, armyType: String): List<Cell>? =
        Pathfinder.aStar(terrain, from, to, armyType, scratch = scratch)?.path

    override fun flankPath(from: Cell, to: Cell, armyType: String): List<Cell>? =
        Pathfinder.flankPath(terrain, from, to, armyType, scratch = scratch)?.path

    /** A* 不可达时的兜底：朝目标方向走一格可通行邻接格，避免部队原地僵死。 */
    private fun greedyStep(army: SimArmy, target: Cell): List<Cell>? {
        var best: Cell? = null
        var bestD = Double.POSITIVE_INFINITY
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = army.r + dr
            val cc = army.c + dc
            if (!terrain.passable(rr, cc, army.type)) continue
            val d = hypot((rr - target.r).toDouble(), (cc - target.c).toDouble())
            if (d < bestD) { bestD = d; best = Cell(rr, cc) }
        }
        return if (best != null) listOf(Cell(army.r, army.c), best) else null
    }

    // ------------------------- 速度 / 情报 -------------------------

    private fun typeSpeed(type: String): Double = when (type) {
        "cavalry" -> 1.5
        "elite" -> 1.2
        "regular" -> 1.0
        "militia" -> 0.8
        else -> 1.0
    }

    private fun dayKmOf(a: SimArmy): Double {
        val base = when (a.intentActive) {
            "rush" -> 30.0
            "rout" -> 24.0
            else -> 15.0
        }
        val fatigueFactor = when {
            a.fatigue >= 4 -> 0.6
            a.fatigue >= 3 -> 0.8
            else -> 1.0
        }
        return base * typeSpeed(a.type) * fatigueFactor
    }

    private fun updateIntelligence(army: SimArmy) {
        val foes = armies.filter {
            it.alive && it.id != army.id && relation(army.side, it.side) == "hostile"
        }
        for (foe in foes) {
            val visible = terrain.visible(Cell(army.r, army.c), Cell(foe.r, foe.c)) && distKm(army, foe) <= 30
            if (visible) {
                army.lastSeen[foe.id] = LastKnown(foe.r, foe.c, 0)
            } else {
                val known = army.lastSeen[foe.id] ?: continue
                val fz = (rand() * 3).toInt()
                val nr = (known.r + ((rand() * 5).toInt() - 2)).coerceIn(0, gridN - 1)
                val nc = (known.c + ((rand() * 5).toInt() - 2)).coerceIn(0, gridN - 1)
                army.lastSeen[foe.id] = LastKnown(nr, nc, fz)
            }
        }
    }

    // ------------------------- 意图落地 + 路径 -------------------------

    private fun applyIntent(army: SimArmy, resolved: ResolvedIntent) {
        army.intentActive = resolved.intent
        army.reason = resolved.reason
        army.targetCell = resolved.target
        val target = resolved.target
        if (target == null) {
            army.path = null
            return
        }
        if (max(abs(army.r - target.r), abs(army.c - target.c)) <= 0) {
            army.path = null
            return
        }
        // 还有路径余量就沿路继续，不重复寻路（CONTINUE）。
        val existing = army.path
        if (existing != null && existing.size > 1) return

        val planned = if (army.intent == "flank") {
            flankPath(Cell(army.r, army.c), target, army.type)
        } else {
            planPath(Cell(army.r, army.c), target, army.type)
        }
        army.path = planned ?: greedyStep(army, target)
    }

    // ------------------------- 主回合 -------------------------

    fun tick(): TurnResult {
        turn++
        val moves = ArrayList<MoveRecord>()
        val encounters = ArrayList<CombatResolver.CombatResult>()
        val highlights = ArrayList<Highlight>()
        val notes = ArrayList<String>()
        val paired = HashSet<String>()

        val sorted = armies.filter { it.alive }
            .sortedByDescending { typeSpeed(it.type) }

        for (army in sorted) {
            if (!army.alive) continue

            updateIntelligence(army)
            val resolved = IntentResolver.resolve(army, this)
            applyIntent(army, resolved)

            val from = Cell(army.r, army.c)
            val path = army.path
            if (path != null && path.size > 1) {
                val stepKm = dayKmOf(army)
                val stepCells = max(1, (stepKm / terrain.cellKm).roundToInt())
                var walked = 0
                val mutablePath = path.toMutableList()
                while (walked < stepCells && mutablePath.size > 1) {
                    val nxt = mutablePath[1]
                    if (!terrain.passable(nxt.r, nxt.c, army.type)) {
                        mutablePath.clear()
                        break
                    }
                    army.r = nxt.r
                    army.c = nxt.c
                    mutablePath.removeAt(1)
                    walked++
                }
                army.path = if (mutablePath.size > 1) mutablePath else null
                army.fatigue = min(5, army.fatigue + (stepKm / 45).roundToInt())
                army.pos = terrain.toLngLat(Cell(army.r, army.c))
            }

            if (from.r != army.r || from.c != army.c) {
                val animation = when (army.intentActive) {
                    "rush" -> "dashed"
                    "flank" -> "curved"
                    "rout" -> "scatter"
                    else -> "solid"
                }
                moves.add(
                    MoveRecord(
                        army.id, army.name, army.side, army.intentActive,
                        terrain.toLngLat(from), army.pos.copyOf(), army.reason, animation,
                    ),
                )
            }

            // 遭遇：邻接敌对军，每对一回合只结算一次。
            for (foe in armies) {
                if (!foe.alive || foe.id == army.id) continue
                if (relation(army.side, foe.side) != "hostile") continue
                if (!CombatResolver.adjacent(army, foe)) continue
                val key = listOf(army.id, foe.id).sorted().joinToString("|")
                if (!paired.add(key)) continue
                val result = CombatResolver.resolve(army, foe, terrain)
                encounters.add(result)
                highlights.add(Highlight("clash", result.pos, "遭遇"))
                notes.add("${army.name} 与 ${foe.name} 交战，${result.winnerId} 胜")
            }

            // 渡口 / 关隘高亮。
            if (terrain.fords.any { abs(it.r - army.r) <= 2 && abs(it.c - army.c) <= 2 }) {
                highlights.add(Highlight("ford", army.pos.copyOf(), "渡口"))
            }
            if (terrain.chokePoints.any { it.r == army.r && it.c == army.c }) {
                highlights.add(Highlight("choke", army.pos.copyOf(), "关隘"))
            }

            MoraleSystem.tick(army, rand)
        }

        val fired = EventTrigger.fire(campaign.events, armies, turn)
        for (f in fired) notes.add(f.reason.ifEmpty { f.action })

        val victory = evaluateVictory(campaign.victory)
        val result = TurnResult(turn, moves, encounters, highlights, fired, notes, victory)
        log.add(result)
        return result
    }

    fun runAll(): List<TurnResult> {
        val results = ArrayList<TurnResult>()
        val duration = campaign.duration.coerceAtLeast(1)
        for (i in 0 until duration) {
            val r = tick()
            results.add(r)
            if (r.victory != null) break
        }
        return results
    }

    fun currentTurn(): Int = turn
    fun history(): List<TurnResult> = log

    // ------------------------- 胜利判定（7 种 + 兜底） -------------------------

    private fun findArmy(idOrName: String?): SimArmy? {
        if (idOrName == null) return null
        return armies.firstOrNull { it.id == idOrName || it.name == idOrName }
    }

    private fun evaluateVictory(v: VictorySpec?): VictoryResult? {
        if (v == null) return fallback()
        val target = findArmy(v.target)

        when (v.type) {
            "annihilate" -> {
                if (target != null && !target.alive) {
                    return VictoryResult(v.side ?: target.side, "annihilate", "${target.name} 被消灭")
                }
            }
            "capture" -> {
                if (target != null && target.alive) {
                    val hostiles = armies.count {
                        it.alive && it.side != target.side &&
                            relation(target.side, it.side) == "hostile" &&
                            max(abs(it.r - target.r), abs(it.c - target.c)) <= 1
                    }
                    if (target.morale == Morale.BROKEN || hostiles >= 2) {
                        return VictoryResult(v.side ?: target.side, "capture", "${target.name} 被合围俘获")
                    }
                }
            }
            "defend" -> {
                if (turn >= campaign.duration) {
                    if (target != null && target.alive && target.strength > 0) {
                        return VictoryResult(v.side ?: target.side, "defend", "${target.name} 撑到最后，未破围")
                    }
                }
            }
            "escape" -> {
                if (target != null && target.alive && target.target != null) {
                    val tc = terrain.toCell(target.target!![0], target.target!![1])
                    if (max(abs(tc.r - target.r), abs(tc.c - target.c)) <= 1) {
                        return VictoryResult(target.side, "escape", "${target.name} 突围成功")
                    }
                }
            }
            "reachPoint" -> {
                val point = v.point
                if (target != null && target.alive && point != null) {
                    val tc = terrain.toCell(point[0], point[1])
                    if (max(abs(tc.r - target.r), abs(tc.c - target.c)) <= 1) {
                        return VictoryResult(target.side, "reachPoint", "到达目标点")
                    }
                }
            }
            "rout" -> {
                val side = v.side
                val foes = armies.filter { it.alive && it.side != side }
                if (foes.isNotEmpty() && foes.all { it.morale == Morale.BROKEN }) {
                    return VictoryResult(side ?: "", "rout", "敌军全线崩溃")
                }
            }
            "compound" -> {
                val clauses = v.clauses.orEmpty()
                if (clauses.isNotEmpty() && clauses.all { evaluateVictory(it) != null }) {
                    return VictoryResult(v.side ?: "", "compound", "复合条件达成")
                }
            }
        }
        return fallback()
    }

    /** 通用兜底：只剩一方有活人有兵，对方全军覆没。 */
    private fun fallback(): VictoryResult? {
        val allSides = armies.map { it.side }.distinct()
        val live = allSides.filter { sd -> armies.any { it.side == sd && it.alive && it.strength > 0 } }
        if (allSides.size > 1 && live.size == 1) {
            return VictoryResult(live[0], "annihilate", "对方全军覆没")
        }
        return null
    }

    companion object {
        private val gson = Gson()

        /** 从 campaign JSON 字符串构造引擎；heightPixels 为 PNG 解码后的灰度像素（可选）。 */
        fun fromJson(
            json: String,
            heightPixels: IntArray? = null,
            gridN: Int = 96,
            seed: Long = 42,
        ): SimEngine {
            val spec = gson.fromJson(json, CampaignSpec::class.java)
                ?: throw IllegalArgumentException("campaign JSON 解析为空")
            return SimEngine(spec, gridN, seed, heightPixels)
        }
    }
}
