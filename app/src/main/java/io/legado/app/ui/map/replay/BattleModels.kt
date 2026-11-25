package io.legado.app.ui.map.replay

/**
 * 战役回放数据模型（纯 JVM，切片-07 军队棋子 / 箭头 / 麾下状态）。
 *
 * 本文件只放阵营、士气、意图、事件等枚举与数据结构，不含渲染；
 * 模型与 assets/map/campaigns 目录下各 .json 的字段口径对齐，批量解析由数据管道完成，
 * 本模块种子数据直接构造，不引 JSON 库。
 */

/** 阵营角色：己方（红军金朱）/ 敌方（蓝军灰青）/ 盟友（铜色）。 */
enum class SideRole {
    OWN,
    ENEMY,
    ALLY,
}

/** 麾下士气：高昂 / 平稳 / 动摇 / 溃散。 */
enum class Morale {
    HIGH,
    STEADY,
    WAVERING,
    ROUT;

    companion object {
        /** 数据资产字符串 → 士气；未知值兜底 STEADY，不崩。 */
        fun fromLabel(label: String?): Morale = when (label?.lowercase()) {
            "high" -> HIGH
            "wavering" -> WAVERING
            "rout", "routed", "surrender" -> ROUT
            else -> STEADY
        }
    }
}

/** 箭头意图：进军 / 急行军 / 包抄 / 撤退 / 设伏。 */
enum class Intent {
    ADVANCE,
    RAPID,
    FLANK,
    RETREAT,
    AMBUSH;

    companion object {
        /**
         * 数据资产字符串 → 意图；未知 / 缺失标签返回 null。
         * 派发层收到 null 必须记告警并保留棋子原意图，不许静默回落 [ADVANCE] 降级。
         */
        fun fromLabelOrNull(label: String?): Intent? = when (label?.trim()?.lowercase()) {
            "rapid", "forced" -> RAPID
            "flank", "outflank" -> FLANK
            "retreat", "fallback" -> RETREAT
            "ambush", "埋伏" -> AMBUSH
            else -> null
        }
    }
}

/**
 * 战役事件动作（回放可视化栈子集）。
 *
 * 回放栈由静态种子 / 后续数据管道驱动，不吃 campaign JSON。与 crossing 活栈 19 分支
 * 对照后的落地分类（返工验收 6/6/4）：
 *
 * - 已接线 9 种：既有 [MORALE_CHANGE] / [SUPPLY_CUT] / [SURRENDER]，加上本批补接的
 *   [SET_INTENT] / [SET_TARGET] / [STRENGTH_CHANGE] / [AMBUSH] / [FLANK] / [RETREAT]
 *   ——这六种所需 intent / targetLat / targetLng / strength 状态位在 [ArmyPiece] 上已存在，
 *   且已被 PieceVisual / BattleArrow 渲染层消费。
 * - 显式拒绝 4 种：[DEFEND_FALL] / [CAPTURE] / [ANNIHILATE] / [FEIGNED_RETREAT]，
 *   回放模型缺 alive / surrendered / exposed 位，派发层逐条告警写明缺哪个位，不静默。
 * - 部分缺失 6 种（ROUT / FIRE_ATTACK / SIEGE_ENGINE / BREAKTHROUGH / NAVAL_ROUT /
 *   ESCAPE）：兵力 / 士气位在，但缺损耗裁决、ROUT/BREAKOUT 意图位，暂落 Director 兜底
 *   告警分支；待双栈合并 / 数据管道落地时再裁。
 */
enum class EventAction {
    MORALE_CHANGE,
    SUPPLY_CUT,
    SURRENDER,
    SET_INTENT,
    SET_TARGET,
    STRENGTH_CHANGE,
    AMBUSH,
    FLANK,
    RETREAT,
    DEFEND_FALL,
    CAPTURE,
    ANNIHILATE,
    FEIGNED_RETREAT,
}

/** 战役一方。 */
data class BattleSide(
    val id: String,
    val name: String,
    val role: SideRole,
)

/**
 * 一支军队棋子。
 *
 * @param strength 兵力规模：棋子尺寸与之成正比，主力比偏师大 1.5 倍（见 PieceVisual）
 * @param targetLat / targetLng 行军目标；null 表示固守原地
 * @param supply 剩余补给等级 0..5（与 crossing 活栈 Corps.supply 口径对齐；5 = 满补给）。
 *        回放棋子原无补给字段，D2 修复补入：断粮事件据此清零补给，不再错降士气。
 *        消费点：PieceVisual.of 读 [isStarved]，断粮棋子压暗、顶光缩短（即时视觉后果）；
 *        活栈的断粮回合计数 / 疲劳撤退长尾链不在回放静态镜像范围内。
 */
data class ArmyPiece(
    val id: String,
    val name: String,
    val sideId: String,
    val lat: Double,
    val lng: Double,
    val strength: Int,
    val type: String,
    val morale: Morale,
    val intent: Intent,
    val targetLat: Double? = null,
    val targetLng: Double? = null,
    val supply: Int = SUPPLY_FULL,
) {

    val isRout: Boolean get() = morale == Morale.ROUT

    val hasTarget: Boolean get() = targetLat != null && targetLng != null

    /** 是否断粮（supply 0）：视觉层据此给疲弱态，口径对齐活栈 supply ≤ 0 的疲弱判定。 */
    val isStarved: Boolean get() = supply <= 0

    /** 复制并替换士气（事件触发用）。 */
    fun withMorale(next: Morale): ArmyPiece = copy(morale = next)

    /** 复制并替换补给等级；越界钳制在 0..[SUPPLY_FULL]，不允许负补给。 */
    fun withSupply(level: Int): ArmyPiece = copy(supply = level.coerceIn(0, SUPPLY_FULL))

    /** 复制并替换行军意图（SET_INTENT / AMBUSH / FLANK / RETREAT 事件用）。 */
    fun withIntent(next: Intent): ArmyPiece = copy(intent = next)

    /** 复制并替换目标落点（SET_TARGET 事件用）；箭头落点由 BattleArrow.fromPiece 消费。 */
    fun withTarget(lat: Double, lng: Double): ArmyPiece = copy(targetLat = lat, targetLng = lng)

    /** 复制并按增减量改兵力（STRENGTH_CHANGE 事件用）；兵力钳制 ≥0，不允许负兵力。 */
    fun withStrengthDelta(delta: Int): ArmyPiece =
        copy(strength = (strength + delta).coerceAtLeast(0))

    /** 复制并替换位置（回放推进 / 落点用）。 */
    fun withPosition(lat: Double, lng: Double): ArmyPiece = copy(lat = lat, lng = lng)

    companion object {
        /** 满补给等级 0..5，口径对齐 crossing 活栈 CampaignJsonParser.SUPPLY_FULL。 */
        const val SUPPLY_FULL: Int = 5
    }
}

/** 时间轴阶段：turn 为该阶段起始回合，铜点落在底部金线下方。 */
data class BattlePhase(
    val turn: Int,
    val label: String,
    val note: String? = null,
)

/**
 * 战役事件。
 *
 * @param turn 触发回合；null 表示按兵力条件触发（如 strengthBelow），由导演层判定
 * @param value 事件参数：士气变化后的新士气标签等；无参数为 null
 */
data class BattleEvent(
    val turn: Int?,
    val targetId: String,
    val action: EventAction,
    val value: String? = null,
    val reason: String = "",
)

/**
 * 一场战役（纯 JVM）。
 *
 * @param rounds 总回合数
 * @param dailyLi 日行里数（30 / 50 / 80 里），测距换算日程用
 */
data class Campaign(
    val id: String,
    val title: String,
    val era: String,
    val context: String,
    val rounds: Int,
    val sides: List<BattleSide>,
    val armies: List<ArmyPiece>,
    val phases: List<BattlePhase>,
    val events: List<BattleEvent>,
    val dailyLi: Int,
    /** 势力 / 战线多边形；资产暂无该字段时为空列表，渲染 pass 跳过（贴地采样见 [ReplayFrontSector]）。 */
    val sectors: List<ReplayFrontSector> = emptyList(),
) {

    fun sideOf(army: ArmyPiece): BattleSide? = sides.firstOrNull { it.id == army.sideId }

    fun sideById(sideId: String): BattleSide? = sides.firstOrNull { it.id == sideId }

    /** 某回合所处阶段（turn ≥ phase.turn 的最后一个）；无阶段返回 null。 */
    fun phaseAt(turn: Int): BattlePhase? {
        if (phases.isEmpty()) return null
        var current: BattlePhase? = null
        for (phase in phases) {
            if (turn >= phase.turn) current = phase else break
        }
        return current
    }

    fun isComplete(turn: Double): Boolean = turn >= rounds

    /** 按阵营取出棋子；role 为 null 时返回全部。 */
    fun armiesByRole(role: SideRole? = null): List<ArmyPiece> {
        if (role == null) return armies
        return armies.filter { sideOf(it)?.role == role }
    }
}
