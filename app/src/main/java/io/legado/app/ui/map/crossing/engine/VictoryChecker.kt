package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.VictoryDef
import io.legado.app.ui.map.crossing.model.VictoryType

/**
 * 胜利条件判定（纯 JVM，§四：每回合都查，达到即结束，不是最后一回合才查）。
 *
 * 七种类型 + COMPOUND 的 AND/OR 组合；判定只读当前快照与据点占领计数，不改状态。
 * 占领回合计数由 [CampaignController] 维护，本层只消费。
 *
 * 纯函数 / 只读约定：本对象不修改传入的 [corps] 列表、captureHoldTurns / pointHolders 表
 * 与 alliedSideIds 集合（仅持引用读取），调用方可在多个条件 / 多回合间安全复用同一实例；
 * 任何占领计数与归属变更都由控制器在调用前后自行落盘。
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
     * @param pointHolders 据点当前占据方表（key = 据点标识，value = 占据方 sideId）。
     *        为什么需要：DEFEND 是「守点」而非「歼灭」，只看场上有生力量无法判断目标点是否已被
     *        突入，引擎层又不掌握据点归属推导，故必须由调用方每回合显式传入当前占据方。
     *        适用条件：仅 DEFEND 分支消费；其余胜利类型收到后忽略。表中缺该点视为「未被任何方
     *        持有」，不按「未知」挂起成既不胜也不负的僵局。
     * @param alliedSideIds 与胜利条件方结盟的 sideId 集合。
     *        为什么需要：盟友协防据点不算失守、盟友军队不计入 ROUT 敌方 blockers，友敌身份必须
     *        由外交关系推导后显式传入，判定层不内置阵营好恶。
     *        适用条件：DEFEND 判占据方、ROUT 筛敌军；其余类型忽略。为空集即视为本场无盟友，
     *        除胜利方同阵营外一律按非友方处理，谓词有空值守卫、不会崩溃。
     *        两个形参为必传项：生产调用方 [CampaignController] 与测试源集均须显式传入，
     *        不设默认值、不留旧五参重载兼容层。
     */
    fun check(
        def: VictoryDef,
        corps: List<Corps>,
        turn: Int,
        duration: Int,
        captureHoldTurns: Map<String, Int>,
        pointHolders: Map<String, String>,
        alliedSideIds: Set<String>,
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
                // 兜底：def.point 为 null、或 pointHolders 中查不到该 key → holder 为 null，
                // 视为「未被任何方持有」，不按未知挂起；只有存在非友方占据记录时才判失守。
                val holder = def.point?.let { pointHolders[pointKey(it)] }
                // 失守判定走 B3 收口的友方谓词：己方 / 盟友占据均不算失守，仅非友方占据判负；
                // 不在本分支另写 sideId 相异判敌的第二套口径。alliedSideIds 为空集即无盟友，
                // 谓词空值守卫保证此处不抛 NPE。
                val lost = holder != null && !isFriendlySide(def.side, holder, alliedSideIds)
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
                // 溃退口径（规格 §四 rout：敌方所有军队士气 broken）：
                // 敌军 = 非胜利方、非盟友、仍可作战（存活 / 未投降 / 兵力>0）的军队。
                // 投降、被歼者不计入溃散；敌军被清空本身不构成 ROUT 胜利，只能拖到回合耗尽判负。
                val enemies = corps.filter {
                    it.sideId != def.side && it.sideId !in alliedSideIds && it.combatReady()
                }
                val routed = enemies.isNotEmpty() && enemies.all { it.morale == Morale.BROKEN }
                when {
                    routed -> Outcome(BattleStatus.VICTORY, def.side, "敌方所有军队士气均已溃散")
                    turn > duration -> Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，敌方尚未全溃")
                    else -> Ongoing
                }
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

    /**
     * 阵营级友方判定（B3 收口；CampaignController 的私有 isFriendlySide 委托到本谓词）：
     * 同阵营或在 [allies] 盟友集合内即友；任一侧 sideId 为空一律非友。
     * 判定层与控制器共用本谓词，禁止在其它分支再按 sideId 相异自行判敌。
     */
    internal fun isFriendlySide(side: String?, other: String?, allies: Set<String>): Boolean =
        side != null && other != null && (side == other || other in allies)

    private val Ongoing = Outcome(BattleStatus.ONGOING, null, "推演进行中")

    /** 抵达判定半径（公里）：对应约一格多的容差。 */
    const val REACH_RADIUS_KM: Double = 3.0
}
