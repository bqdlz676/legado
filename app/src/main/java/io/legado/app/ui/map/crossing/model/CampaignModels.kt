package io.legado.app.ui.map.crossing.model

/**
 * 推演引擎 · 战役定义与运行态模型（纯 JVM）。
 *
 * 分层约定：`*Def` 为 JSON 反序列化出来的静态战役定义（换仗只换这份数据）；
 * [Corps] 为回合推进中的运行态；[CorpsView] / [TurnSnapshot] 为喂给 Compose 壳的不可变快照。
 * 引擎不内置任何一场战役：本文件只描述「一场仗长什么样」，不写死任何历史事件。
 */

/** 经纬度（纬度在前，与工程既有 Geo 口径一致）。JSON 坐标为 [经度, 纬度]，由解析层转换。 */
data class Geo(val lat: Double, val lng: Double) {

    companion object {
        /** 从 JSON 数组 [lng, lat] 构造；长度不足 / 非数兜底原点 (0,0)，不抛。 */
        fun fromLngLat(arr: List<Double?>?): Geo {
            val lng = arr?.getOrNull(0) ?: 0.0
            val lat = arr?.getOrNull(1) ?: 0.0
            return Geo(lat = lat ?: 0.0, lng = lng ?: 0.0)
        }
    }
}

/** 网格单元（列 / 行），行列均落在 [0, gridN) 内；越界判定由 TerrainMap 负责。 */
data class Cell(val col: Int, val row: Int)

/** 地形聚焦范围：中心经纬度 + 半径（公里）。 */
data class TerrainFocus(
    val center: Geo,
    val radiusKm: Double,
)

/** 天候块（赤壁等水战火攻战役使用；缺省即晴朗，引擎不得因缺该块崩）。 */
data class WeatherDef(
    val condition: String,
    val windMps: Double,
    val temperatureC: Double,
)

/** 胜利条件定义；COMPOUND 时 [sub] 按 [op] 组合。 */
data class VictoryDef(
    val type: VictoryType,
    val target: String?,
    val side: String?,
    val point: Geo?,
    val holdTurns: Int,
    val op: String,
    val sub: List<VictoryDef>,
)

/** 阵营定义。color 仅作数据留存；壳层一律按角色取 12 色 token，不读 JSON 色值。 */
data class SideDef(
    val id: String,
    val name: String,
    val colorRaw: String?,
)

/** 外交关系定义。 */
data class DiplomacyDef(
    val a: String,
    val b: String,
    val relation: Relation,
)

/** 军队静态定义（JSON 一条 army）。 */
data class ArmyDef(
    val id: String,
    val name: String,
    val sideId: String,
    val pos: Geo,
    val strength: Double,
    val type: ArmyType,
    val fatigue: Double,
    val morale: Morale,
    val intent: Intent,
    val target: Geo?,
    val supply: Int,
)

/** 触发器条件（§一：条件 if-trigger，不是硬编码回合数）。全部条件 AND；null 表示不约束。 */
data class TriggerDef(
    val turn: Int?,
    val army: String?,
    val strengthBelow: Double?,
    val morale: String?,
    val atPoint: Geo?,
)

/** 脚本事件：条件满足后执行一次动作。 */
data class EventDef(
    val trigger: TriggerDef,
    val action: EventAction,
    val target: String?,
    val value: String?,
    val targetPos: Geo?,
    val reason: String,
)

/** 山脊（归一化 0..1 坐标，procedural 地形用）。 */
data class RidgeDef(val x: Double, val y: Double, val height: Double, val width: Double)

/** 河流折线（归一化 0..1 坐标点列）。 */
data class RiverDef(val pts: List<Geo>)

/** 地形附加定义：栅格高程图缺失时，用山脊 / 河流程序化兜底。 */
data class TerrainDef(
    val procedural: Boolean,
    val ridges: List<RidgeDef>,
    val rivers: List<RiverDef>,
)

/** 一场战役的完整静态定义。 */
data class CampaignDefinition(
    val id: String,
    val title: String,
    val era: String,
    val context: String,
    val focus: TerrainFocus,
    val duration: Int,
    val weather: WeatherDef?,
    val victory: VictoryDef,
    val sides: List<SideDef>,
    val diplomacy: List<DiplomacyDef>,
    val armies: List<ArmyDef>,
    val events: List<EventDef>,
    val terrain: TerrainDef,
    val heightMap: String?,
    /** 载入是否走了兜底（JSON 残缺 / 高程图缺失等）；壳层据此显示降级标识。 */
    val degraded: Boolean,
    val warnings: List<String>,
)

/**
 * 一支军队的运行态。
 *
 * @param supply 剩余补给等级 0..5；[supplyCutTurns] 记录断粮连续回合数（>3 疲劳加速）
 * @param exposed 是否已暴露伏击意图；[highGround] 本回合是否据制高点
 * @param route 本回合规划路径（单元序列，供壳层画行军线）
 * @param lastKnown 情报模糊：看不到的敌军用上次已知位置 + 模糊半径
 */
data class Corps(
    val id: String,
    val name: String,
    val sideId: String,
    val role: FactionRole,
    val type: ArmyType,
    var strength: Double,
    var fatigue: Double,
    var morale: Morale,
    var intent: Intent,
    var position: Cell,
    var latLng: Geo,
    var target: Geo?,
    var supply: Int,
    var supplyCutTurns: Int,
    var alive: Boolean,
    var surrendered: Boolean,
    var exposed: Boolean,
    var highGround: Boolean,
    var route: List<Cell>,
    var lastKnown: Map<String, Geo>,
    var visionRadiusCells: Int,
    /** 累计兵力损失（守恒审计用）：只增不减，事件补强另计。 */
    var losses: Double = 0.0,
) {
    /** 溃散 / 解散：兵力归零、移出棋盘。 */
    fun disband() {
        alive = false
        strength = 0.0
    }

    /** 兵力是否还能作战。 */
    fun combatReady(): Boolean = alive && !surrendered && strength > 0.0

    /** 扣除伤亡；兵力钳制在 0 以上，损失计入 [losses]，不允许出现负兵力。 */
    fun applyLoss(amount: Double) {
        if (amount <= 0.0) return
        val cut = amount.coerceAtMost(strength)
        strength -= cut
        losses += cut
        if (strength <= 0.0) {
            strength = 0.0
            alive = false
        }
    }
}

/** 喂给壳层的不可变棋子快照。 */
data class CorpsView(
    val id: String,
    val name: String,
    val sideId: String,
    val role: FactionRole,
    val type: ArmyType,
    val strength: Double,
    val fatigue: Double,
    val morale: Morale,
    val intent: Intent,
    val lat: Double,
    val lng: Double,
    val targetLat: Double?,
    val targetLng: Double?,
    val alive: Boolean,
    val surrendered: Boolean,
    val route: List<Geo>,
    /** 情报雾（F8）：本方军、或被任一可见本方军看到的敌军 = true。 */
    val observed: Boolean = false,
    /** 观测到的实时坐标；未观测时为 null（不下发旧坐标，避免透视）。 */
    val reportLat: Double? = null,
    val reportLng: Double? = null,
)

/** 一回合的推演快照（回放 / 分支对比消费）。 */
data class TurnSnapshot(
    val turn: Int,
    val elapsedDays: Int,
    val status: BattleStatus,
    val winnerSide: String?,
    val corps: List<CorpsView>,
    val events: List<String>,
)

/**
 * 情报表条目快照（B7）：与控制器私有 `IntelRecord` 同构的不可变拷贝，供分支存档深拷贝往返。
 * 决策层 [BranchSnapshot.intel] 与视图层 [BranchSnapshot.reports] 共用本结构。
 */
data class IntelRecordSnapshot(
    val geo: Geo?,
    val cell: Cell?,
    val seenTurn: Int,
    val radiusCells: Int,
)

/**
 * 分支存档：手动改意图后可存一条状态线，之后与历史线对比（§五 分支保存）。
 *
 * B7 口径：快照必须覆盖运行态全部可变量，恢复后与存档时刻逐字段一致——除军队本体外，
 * 终局状态 / 胜负方 / 终局原因 / 事件日志 / 行军累计 / 双情报表均纳入；缺省空值仅用于
 * 空存档构造兜底（本存档只在内存中流转，不落盘，不存在旧格式反序列化兼容问题）。
 */
data class BranchSnapshot(
    val turn: Int,
    val elapsedDays: Int,
    val corps: List<Corps>,
    val firedEventIds: Set<Int>,
    /** 据点连续占领计数：不回滚会让恢复后第一次"易主"判定沿用旧时间线（B7）。默认空表兼容旧调用方。 */
    val captureHold: Map<String, Int> = emptyMap(),
    /** 据点当前归属（据点 id → 阵营 id）：B7 要求快照回滚全部可变量，否则恢复后胜负面板沿用旧占领方。 */
    val pointHolders: Map<String, String> = emptyMap(),
    /** 终局状态：存档时刻若已分胜负，恢复须逐字还原，不得强制重置为进行中（B7）。 */
    val status: BattleStatus = BattleStatus.ONGOING,
    /** 终局胜负方 sideId；未分胜负为 null。 */
    val winnerSide: String? = null,
    /** 终局原因文案；进行中为空串。 */
    val finishReason: String = "",
    /** 事件日志快照：分支回滚后日志不沿用旧时间线（B7）。 */
    val eventLog: List<String> = emptyList(),
    /** 本回合每支军队行军格数累计：士气推进消费，随存档回滚（B7）。 */
    val marchCells: Map<String, Int> = emptyMap(),
    /** 决策层情报表真快照：观察者军 id → 目标军 id → 情报条目；非"清空重播种"（B7/B9）。 */
    val intel: Map<String, Map<String, IntelRecordSnapshot>> = emptyMap(),
    /** 视图层目睹情报表真快照：口径同 [intel]；未目睹目标不留条目（B7/B9）。 */
    val reports: Map<String, Map<String, IntelRecordSnapshot>> = emptyMap(),
)
