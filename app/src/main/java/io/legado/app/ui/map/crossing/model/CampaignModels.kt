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
 * @param exposed 本回合是否暴露伏击意图（回合级姿态：每回合推进时复位，由本回合视线穿越 /
 *        接触交战 / 佯退诱出等事实重新置真，不跨回合粘性保留）；[highGround] 本回合是否据制高点
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
    /**
     * 本回合侧翼寻路是否被迫降级（B8R4）：严格避视野无路后走了加价绕行 / 直线折线兜底。
     * 判定单源在寻路层 `FlankRoute.degraded`，控制器只读不重算；每回合 planRoute 刷新，
     * 不沿用到下一回合的其它意图。
     */
    var routeDegraded: Boolean = false,
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

/**
 * 各兵种视野半径（格）：model 层单一来源（B8R1 收口）。
 *
 * 口径：骑兵 6 / 精锐 5 / 常规 4 / 民兵 3。情报侧 [Corps.visionRadiusCells] 赋值、
 * 情报模糊距离项封顶、侧翼寻路逐敌闸门全部引用本符号；engine 层（Pathfinder / CampaignController）
 * 不得再各建一份字面量表，避免「情报看见的半径」与「侧翼躲开的半径」改平衡时只改一处而漂移。
 *
 * 为什么放 model：半径是兵种固有口径属性，model 被 engine 两侧共同依赖，单向向下，
 * 不产生 engine 双向引用；枚举 [ArmyType] 自身保持纯枚举，表以扩展属性落在模型文件集中对账。
 */
val ArmyType.visionRadiusCells: Int
    get() = when (this) {
        ArmyType.CAVALRY -> 6
        ArmyType.ELITE -> 5
        ArmyType.REGULAR -> 4
        ArmyType.MILITIA -> 3
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
    /**
     * 规则意图；null = 未观测敌军（情报雾）：敌方决策意图属机密，数据层随位置一并掩码，
     * 不下发缺省意图冒充情报；壳层据此显示未探明态。已观测本方 / 盟友 / 敌军为真实意图。
     */
    val intent: Intent? = null,
    val lat: Double,
    val lng: Double,
    /** 行军目标点；未观测敌军随意图一并掩码为 null，不以假坐标冒充未探明方位。 */
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
    /**
     * 情报模糊半径（格）：未观测敌军上次已知位置周围的不确定范围；null = 引擎未下发
     * （友军 / 已观测 / 从未侦察）。壳层据此画盲区圈；缺省 null 保证旧构造点不破。
     */
    val reportRadiusCells: Int? = null,
    /** 盲区占位文案：从未侦察到的敌军方位提示；null = 非盲区。缺省 null 保证旧构造点不破。 */
    val blindZoneLabel: String? = null,
    /**
     * 本回合侧翼路线被迫降级（加价绕行 / 折线兜底，B8R4）；壳层在意图标签旁挂「降级·绕行」
     * 标识。未观测敌军随 [route] 一并模糊，不下发本标志。
     */
    val routeDegraded: Boolean = false,
)

/**
 * 单条事件日志条目（B8R6）。[attribution] 为相对玩家阵营的结构化归属，[text] 为可读文案；
 * 壳层渲染 / 节拍分类读 [text]，阵营分组 / 过滤读 [attribution]，二者不互相反推。
 */
data class EventEntry(
    val attribution: EventAttribution,
    val text: String,
)

/** 一回合的推演快照（回放 / 分支对比消费）。 */
data class TurnSnapshot(
    val turn: Int,
    val elapsedDays: Int,
    val status: BattleStatus,
    val winnerSide: String?,
    val corps: List<CorpsView>,
    val events: List<EventEntry>,
)

/** 分支存档：手动改意图后可存一条状态线，之后与历史线对比（§五 分支保存）。 */
data class BranchSnapshot(
    val turn: Int,
    val elapsedDays: Int,
    val corps: List<Corps>,
    val firedEventIds: Set<Int>,
    /** 据点连续占领计数：不回滚会让恢复后第一次"易主"判定沿用旧时间线（B7）。默认空表兼容旧调用方。 */
    val captureHold: Map<String, Int> = emptyMap(),
    /** 据点当前归属（据点标识 → 阵营 id）：B7 要求快照回滚全部可变量；恢复路径整表回写本表，
     *  缺字段旧档再由控制器按恢复后军位重建（见 CampaignController.restorePointHolders）。 */
    val pointHolders: Map<String, String> = emptyMap(),
    /** 事件日志全表（D-1）：恢复分支时先清空运行态日志再整表回写，避免被丢弃时间线的条目
     *  残留到恢复后的面板；默认空表兼容旧构造点（旧五参构造不传本字段可直接编译）。
     *  B8R6：条目为结构化 [EventEntry]，归属与文案并存；往返一致性按条目整体相等比对。 */
    val eventLog: List<EventEntry> = emptyList(),
)

// ── B10 分支并列对比：逐回合差异（纯 JVM，不依赖 Compose） ─────────────────────

/**
 * 两线同一回合内，单支军队被标出的差异维度。
 *
 * 标签只做语义化展示；壳层用暖金描边 + 文字标签呈现，不映射红 / 绿纯色块（口径冻结 F 系列配色约束）。
 */
enum class DiffField(val label: String) {
    /** 兵力差值超出容差。 */
    STRENGTH("兵力"),

    /** 士气档位发生变化。 */
    MORALE("士气"),

    /** 坐标位移超出投影舍入容差。 */
    POSITION("位置"),

    /** 存亡 / 投降状态变化（含一侧溃散、另一侧仍在阵）。 */
    STATUS("存亡"),
    // 据点归属差异预留：据点持有者随 B7 `pointHolders` 快照下发；B7 合并进 main 后在此补 HOLDING 分支。
}

/** 单支军队在两线同一回合的差异结论；[fields] 为空即本回合两线一致。 */
data class CorpsDiff(
    val corpsId: String,
    val name: String,
    val fields: Set<DiffField>,
) {
    val different: Boolean get() = fields.isNotEmpty()
}

/**
 * 同一回合两条回放线的对比结论。
 * [left] / [right] 任一为 null 表示该分支未推进到本回合，壳层据此出占位卡，不留白屏。
 */
data class TurnDiff(
    val turn: Int,
    val left: TurnSnapshot?,
    val right: TurnSnapshot?,
    val corps: List<CorpsDiff>,
) {
    /** 任一侧缺快照：整回合不逐行高亮，只出「未推进」占位。 */
    val missingSide: Boolean get() = left == null || right == null

    /** 两侧都有快照且至少一支军队存在差异。 */
    val hasAnyDiff: Boolean get() = !missingSide && corps.any { it.different }
}

/**
 * 分支对比 diff 纯函数（B10）。
 *
 * 为什么纯函数化：diff 不持有 Compose 状态、不读控制器内部，入参两条不可变快照、出参不可变结论，
 * 便于表驱动单测逐例核对；壳层只消费 [TurnDiff] 做描边与标签渲染。
 */
object SnapshotDiff {

    /** 兵力容差：结算舍入 / 浮点抖动在 0.05 以内视为同值，避免尾数差误标「兵力」。 */
    const val STRENGTH_EPSILON: Double = 0.05

    /** 位置容差（度）：网格投影再回算的舍入位移在此阈值内不算「位置」差异。 */
    const val POSITION_EPSILON_DEG: Double = 0.0005

    /**
     * 对比两条回放线在同一回合的快照。
     * [left] / [right] 任一为 null：直接返回缺侧结论，不做逐军比对。
     */
    fun diff(left: TurnSnapshot?, right: TurnSnapshot?): TurnDiff {
        val turn = left?.turn ?: right?.turn ?: -1
        if (left == null || right == null) {
            return TurnDiff(turn = turn, left = left, right = right, corps = emptyList())
        }
        val byIdLeft = left.corps.associateBy { it.id }
        val byIdRight = right.corps.associateBy { it.id }
        val ids = (byIdLeft.keys + byIdRight.keys).sorted()
        val corps = ids.map { id -> diffCorps(id, byIdLeft[id], byIdRight[id]) }
        return TurnDiff(turn = turn, left = left, right = right, corps = corps)
    }

    private fun diffCorps(id: String, a: CorpsView?, b: CorpsView?): CorpsDiff {
        val name = a?.name ?: b?.name ?: id
        if (a == null || b == null) {
            // 一侧缺编：只按存亡差异标注；兵力 / 位置不逐项比，避免把「溃散出局」误读成位移或减员。
            return CorpsDiff(id, name, setOf(DiffField.STATUS))
        }
        val fields = mutableSetOf<DiffField>()
        if (kotlin.math.abs(a.strength - b.strength) > STRENGTH_EPSILON) {
            fields += DiffField.STRENGTH
        }
        if (a.morale != b.morale) fields += DiffField.MORALE
        if (kotlin.math.abs(a.lat - b.lat) > POSITION_EPSILON_DEG ||
            kotlin.math.abs(a.lng - b.lng) > POSITION_EPSILON_DEG
        ) {
            fields += DiffField.POSITION
        }
        if (a.alive != b.alive || a.surrendered != b.surrendered) fields += DiffField.STATUS
        return CorpsDiff(id, name, fields)
    }
}
