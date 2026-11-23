package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.VictoryDef
import io.legado.app.ui.map.crossing.model.VictoryType

/**
 * 胜利条件判定（纯 JVM，§四：每回合都查，达到即结束，不是最后一回合才查）。
 *
 * 七种类型 + COMPOUND 的 AND/OR 组合；判定只读当前快照与据点占领计数，不改状态。
 * 占领回合计数由 [CampaignController] 维护，本层只消费。
 */
object VictoryChecker {

    data class Outcome(
        val status: BattleStatus,
        val winnerSide: String?,
        val reason: String,
    ) {
        val finished: Boolean get() = status != BattleStatus.ONGOING
    }

    /**
     * @param captureHoldTurns 据点被目标 side 连续占领的回合数（key = 据点标识）
     * @param pointHolders 据点当前占据方表（key = 据点标识，value = 占据方 sideId）；
     *        仅 DEFEND 分支消费，用于判定目标点是否被敌方占据。
     * @param alliedSideIds 与胜利条件方结盟的 sideId 集合；盟友占据据点不算失守。
     *        两个默认空值仅为兼容测试源集中沿用五参旧形参表的既有用例；生产调用方
     *        （CampaignController）必须显式传入实参，不得依赖默认值。
     */
    fun check(
        def: VictoryDef,
        corps: List<Corps>,
        turn: Int,
        duration: Int,
        captureHoldTurns: Map<String, Int>,
        pointHolders: Map<String, String> = emptyMap(),
        alliedSideIds: Set<String> = emptySet(),
    ): Outcome {
        val outcome = evaluate(def, corps, turn, duration, captureHoldTurns, pointHolders, alliedSideIds)
        // 防御类：撑满回合且目标方仍在、据点未失守 → 胜；目标方提前被歼或据点失守 → 负。
        return outcome
    }

    private fun evaluate(
        def: VictoryDef,
        corps: List<Corps>,
        turn: Int,
        duration: Int,
        captureHoldTurns: Map<String, Int>,
        pointHolders: Map<String, String>,
        alliedSideIds: Set<String>,
    ): Outcome {
        return when (def.type) {
            VictoryType.ANNIHILATE -> {
                val target = corps.firstOrNull { it.id == def.target }
                if (target == null || !target.alive || target.surrendered) {
                    Outcome(BattleStatus.VICTORY, def.side, "目标「${def.target}」已被歼灭或投降")
                } else if (turn > duration) {
                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，目标未被歼灭")
                } else Ongoing
            }

            VictoryType.CAPTURE -> {
                val held = def.point?.let { captureHoldTurns[pointKey(it)] } ?: 0
                if (held >= def.holdTurns) {
                    Outcome(BattleStatus.VICTORY, def.side, "据点已占领 $held 回合")
                } else if (turn > duration) {
                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，据点未守住")
                } else Ongoing
            }

            VictoryType.DEFEND -> {
                val defenders = corps.filter { it.sideId == def.side && it.combatReady() }
                // 据点占据方：据点 key 与 captureHold / 测试用例同一口径（lng,lat 各四位小数）。
                val holder = def.point?.let { pointHolders[pointKey(it)] }
                // 被非盟友的敌方占据即失守；防御方己方或盟友占据均不算失守。
                val lost = holder != null && holder != def.side && holder !in alliedSideIds
                when {
                    lost -> Outcome(BattleStatus.DEFEAT, def.side, "据点已被敌方占据，防御失败")
                    defenders.isEmpty() -> Outcome(BattleStatus.DEFEAT, def.side, "防御方已被击溃")
                    turn >= duration -> Outcome(BattleStatus.VICTORY, def.side, "撑过全部 $duration 回合，防御成功")
                    else -> Ongoing
                }
            }

            VictoryType.ESCAPE -> {
                val target = corps.firstOrNull { it.id == def.target }
                val point = def.point
                if (target != null && target.combatReady() && point != null &&
                    reachedPoint(target, point)
                ) {
                    Outcome(BattleStatus.VICTORY, def.side, "目标已抵达逃脱点")
                } else if (turn > duration) {
                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，目标未能脱身")
                } else Ongoing
            }

            VictoryType.REACH_POINT -> {
                val point = def.point
                val reached = corps.any { it.sideId == def.side && it.combatReady() && reachedPoint(it, point) }
                if (reached) Outcome(BattleStatus.VICTORY, def.side, "我方军队抵达目标点")
                else if (turn > duration) Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，未能抵达目标点")
                else Ongoing
            }

            VictoryType.ROUT -> {
                val enemies = corps.filter { it.sideId != def.side && it.combatReady() }
                if (enemies.isEmpty()) {
                    Outcome(BattleStatus.VICTORY, def.side, "敌方所有军队均已溃散")
                } else if (turn > duration) {
                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，敌方尚未全溃")
                } else Ongoing
            }

            VictoryType.COMPOUND -> {
                val results = def.sub.map { evaluate(it, corps, turn, duration, captureHoldTurns, pointHolders, alliedSideIds) }
                val hit = results.map { it.finished && it.status == BattleStatus.VICTORY }
                // all() 无无参重载，必须显式给 predicate；{ it } 即“每项均为 true”，与 AND 语义一致。
                val passed = if (def.op.equals("OR", ignoreCase = true)) hit.any() else hit.all { it }
                if (passed) Outcome(BattleStatus.VICTORY, def.side, "组合条件达成（${def.op}）")
                else if (turn > duration) Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，组合条件未达成")
                else Ongoing
            }
        }
    }

    private fun reachedPoint(corps: Corps, point: io.legado.app.ui.map.crossing.model.Geo?): Boolean {
        if (point == null) return false
        val dLat = Math.abs(corps.latLng.lat - point.lat) * 111.32
        val dLng = Math.abs(corps.latLng.lng - point.lng) * 111.32
        return Math.hypot(dLat, dLng) <= REACH_RADIUS_KM
    }

    private fun pointKey(point: io.legado.app.ui.map.crossing.model.Geo): String =
        "${"%.4f".format(point.lng)},${"%.4f".format(point.lat)}"

    private val Ongoing = Outcome(BattleStatus.ONGOING, null, "推演进行中")

    /** 抵达判定半径（公里）：对应约一格多的容差。 */
    const val REACH_RADIUS_KM: Double = 3.0
}
