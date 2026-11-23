package io.legado.app.ui.map.crossing.model

/**
 * 推演引擎 · 枚举口径（纯 JVM，不引 Android / Compose 类型，可在普通 JVM 单测中断言）。
 *
 * 规格来源：`docs/落文-原文/落文-原文-06-四渡赤水引擎.md` §一 JSON schema、§三 意图优先级、
 * §四 胜利条件；字段字符串与 `assets/map/campaigns/*.json` 既有数据资产口径对齐。
 * 所有 fromLabel 兜底到保守默认值：未知 / 缺失字符串不得抛异常，由调用侧记 warning。
 */

/** 士气四级：高昂 > 平稳 > 动摇 > 溃散。 */
enum class Morale {
    HIGH,
    STEADY,
    WAVERING,
    BROKEN;

    /** 降一级；BROKEN 不再降。 */
    fun downgrade(): Morale = when (this) {
        HIGH -> STEADY
        STEADY -> WAVERING
        WAVERING -> BROKEN
        BROKEN -> BROKEN
    }

    /** 升一级；HIGH 不再升。 */
    fun upgrade(): Morale = when (this) {
        HIGH -> HIGH
        STEADY -> HIGH
        WAVERING -> STEADY
        BROKEN -> WAVERING
    }

    companion object {
        /** 数据资产字符串 → 士气；未知值兜底 STEADY，不崩。 */
        fun fromLabel(label: String?): Morale = when (label?.trim()?.lowercase()) {
            "high" -> HIGH
            "steady", "stable" -> STEADY
            "wavering", "shaken" -> WAVERING
            "broken", "rout", "routed", "surrender" -> BROKEN
            else -> STEADY
        }
    }
}

/** 兵种：精锐 / 常规 / 民兵（均按步兵口径）/ 骑兵（机动与坡度通行更宽）。 */
enum class ArmyType {
    ELITE,
    REGULAR,
    MILITIA,
    CAVALRY;

    /** 基础日行系数：骑兵先动、民兵最慢（§三 按速度排序，骑兵先动）。 */
    val speedBase: Double
        get() = when (this) {
            CAVALRY -> 1.40
            ELITE -> 1.10
            REGULAR -> 1.00
            MILITIA -> 0.80
        }

    /** 排序键：回合内行动顺序，骑兵在前。 */
    val orderKey: Double get() = -speedBase

    companion object {
        fun fromLabel(label: String?): ArmyType = when (label?.trim()?.lowercase()) {
            "elite" -> ELITE
            "regular", "line" -> REGULAR
            "militia", "levy" -> MILITIA
            "cavalry", "horse" -> CAVALRY
            else -> REGULAR
        }
    }
}

/**
 * 规则意图（§三 优先级表 + §五 手动干预可拖拽切换）。
 * CONTINUE = 沿原意图继续；HOLD = 原地据守。
 */
enum class Intent {
    FIGHT,
    ROUT,
    BREAKOUT,
    AMBUSH,
    RETREAT,
    SUPPORT,
    ADVANCE,
    FLANK,
    SEIZE,
    RAPID,
    CONTINUE,
    HOLD;

    companion object {
        /** 数据资产 / 手动拖拽字符串 → 意图；未知值兜底 HOLD（据守，最保守动作）。 */
        fun fromLabel(label: String?): Intent = when (label?.trim()?.lowercase()) {
            "fight", "engage", "battle" -> FIGHT
            "rout", "flee", "escape" -> ROUT
            "breakout", "burst" -> BREAKOUT
            "ambush", "埋伏" -> AMBUSH
            "retreat", "fallback", "withdraw" -> RETREAT
            "support", "reinforce" -> SUPPORT
            "advance", "march" -> ADVANCE
            "flank", "outflank" -> FLANK
            "seize", "occupy" -> SEIZE
            "rapid", "rush", "forced", "急行军" -> RAPID
            "continue" -> CONTINUE
            "hold", "guard", "defend" -> HOLD
            else -> HOLD
        }
    }
}

/** 脚本事件动作（条件触发器声明，不硬编码回合数）。 */
enum class EventAction {
    MORALE_CHANGE,
    SURRENDER,
    SUPPLY_CUT,
    SET_INTENT,
    SET_TARGET,
    STRENGTH_CHANGE,
    ROUT;

    companion object {
        fun fromLabel(label: String?): EventAction? = when (label?.trim()?.lowercase()) {
            "moralechange", "morale_change" -> MORALE_CHANGE
            "surrender" -> SURRENDER
            "supplycut", "supply_cut" -> SUPPLY_CUT
            "setintent", "set_intent" -> SET_INTENT
            "settarget", "set_target" -> SET_TARGET
            "strengthchange", "strength_change" -> STRENGTH_CHANGE
            "rout", "break" -> ROUT
            else -> null
        }
    }
}

/** 胜利条件类型（§四，每回合都查，达到即结束；不是最后一回合才查）。 */
enum class VictoryType {
    ANNIHILATE,
    CAPTURE,
    DEFEND,
    ESCAPE,
    REACH_POINT,
    ROUT,
    COMPOUND;

    companion object {
        fun fromLabel(label: String?): VictoryType = when (label?.trim()?.lowercase()) {
            "annihilate" -> ANNIHILATE
            "capture" -> CAPTURE
            "defend" -> DEFEND
            "escape" -> ESCAPE
            "reachpoint", "reach_point" -> REACH_POINT
            "rout" -> ROUT
            "compound" -> COMPOUND
            else -> ANNIHILATE
        }
    }
}

/** 地形类别（相对地形，不依赖具体坐标）。 */
enum class TerrainKind {
    WATER,
    PLAIN,
    HILL,
    MOUNTAIN,
    SNOW;

    companion object {
        /** 按米制高程归类；阈值对齐 globe/terrain 既有 DEM 口径（雪线 3500m、海面 0m）。 */
        fun fromMeters(meters: Double): TerrainKind = when {
            meters <= 0.5 -> WATER
            meters > 3500.0 -> SNOW
            meters > 1500.0 -> MOUNTAIN
            meters > 300.0 -> HILL
            else -> PLAIN
        }
    }
}

/** 势力角色：己方 / 敌方 / 盟友 / 中立；壳层按此取 token 与填充透明度。 */
enum class FactionRole {
    OWN,
    ENEMY,
    ALLY,
    NEUTRAL;

    companion object {
        fun fromSideIds(sideId: String?, ownSideId: String?, allies: Set<String>): FactionRole = when {
            sideId == null -> NEUTRAL
            sideId == ownSideId -> OWN
            sideId in allies -> ALLY
            else -> ENEMY
        }
    }
}

/** 外交关系。 */
enum class Relation {
    HOSTILE,
    ALLIED,
    NEUTRAL;

    companion object {
        fun fromLabel(label: String?): Relation = when (label?.trim()?.lowercase()) {
            "hostile", "war" -> HOSTILE
            "allied", "ally", "friend" -> ALLIED
            else -> NEUTRAL
        }
    }
}

/** 推演终局状态。 */
enum class BattleStatus {
    ONGOING,
    VICTORY,
    DEFEAT,
    DRAW;
}
