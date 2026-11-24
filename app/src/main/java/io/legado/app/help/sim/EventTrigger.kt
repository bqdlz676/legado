package io.legado.app.help.sim

import kotlin.math.roundToInt

/**
 * 落文 · 战役事件触发器（core:sim / §五）
 *
 * 事件完全由 campaign JSON 驱动：turn / 部队 + 兵力阈值 / 士气状态 为触发条件，
 * 动作覆盖士气变更、溃散、改令（含改目标点）、断粮、兵力增减。每条事件只触发一次。
 *
 * B15 动作分类（JSON `"action"` 位，两类不许混进同一个语义层）：
 * - **事件型**：直接改士气 / 兵力 / 补给 / 存活——
 *   moraleChange / surrender / supplyCut / strengthDelta / fireAttack / siegeEngine /
 *   breakthrough / defendFall / capture / annihilate。
 * - **意图型**：改写目标军意图字符串，由 IntentResolver 后续回合消费路径——
 *   ambush / flank / retreat / escape。
 * - **混合型**：既改意图又附带士气/兵力降级——rout / navalRout（击溃 + 溃逃意图），
 *   feignedRetreat（佯退意图 + 附带士气降级，见 [fire] 中 feignedRetreat 分支）。
 *   之所以单列混合型：新人按"意图型=纯改意图"读会漏掉士气副作用，分类表与实现必须一一对应。
 *
 * 边界口径：目标缺失 / 目标已阵亡 / value 非法数字 / 重复触发均不抛异常；
 * 异常边界逐条记入 [warnings]，未知 action 走 else 告警，不静默吞掉。
 *
 * B15 复审结论（对照 `落文-原文-06-四渡赤水引擎.md`）：13 种补登动作逐一有显式分支，
 * 每种都能读出可观察结果（兵力 / 士气 / 存活 / 投降 / 意图之一发生变化，且 FiredEvent 照常登记）。
 * 规格切片未把任何一种标为「暂不实现」，故无动作被降级为纯 no-op；
 * 仅有的两条显式不做副作用路径：① 全局事件无目标军；② 未知 action（else 告警登记）。
 */
data class FiredEvent(val action: String, val target: String?, val reason: String)

object EventTrigger {

    /**
     * 告警台账：未知动作、目标缺失、目标已阵亡等边界情况逐条记录。
     * 纯 JVM 模块不引 Android Log；每次 [fire] 清空，引擎可把台账并入回合 notes 上报壳层。
     */
    val warnings: MutableList<String> = mutableListOf()

    // 事件型动作的默认折损比例（占当前兵力）；value 为合法 0..1 数字时按 value 覆盖。
    // 四个值为**经验默认值，待用户校**（规格切片 06 未给数字口径）；调整时须与测试同批改，避免断言与实现错位。
    private const val LOSS_FIRE_ATTACK: Double = 0.35
    private const val LOSS_SIEGE_ENGINE: Double = 0.55
    private const val LOSS_BREAKTHROUGH: Double = 0.25
    private const val LOSS_NAVAL_ROUT: Double = 0.20

    fun fire(
        events: List<EventSpec>?,
        armies: List<SimArmy>,
        turn: Int,
    ): List<FiredEvent> {
        warnings.clear()
        if (events.isNullOrEmpty()) return emptyList()
        val fired = ArrayList<FiredEvent>()

        for (ev in events) {
            if (ev.done) continue
            val tr = ev.trigger ?: TriggerSpec()
            var ok = true

            if (tr.turn != null) ok = ok && turn >= tr.turn

            val triggerArmy = tr.army?.let { id -> armies.firstOrNull { it.id == id } }
            if (tr.army != null) {
                if (triggerArmy == null) ok = false
                if (tr.strengthBelow != null && triggerArmy != null) {
                    ok = ok && triggerArmy.strength < tr.strengthBelow
                }
                if (tr.moraleIs != null && triggerArmy != null) {
                    ok = ok && triggerArmy.morale.label == tr.moraleIs
                }
            }
            if (!ok) continue

            ev.done = true
            val target = ev.target?.let { id -> armies.firstOrNull { it.id == id } }

            when {
                ev.target != null && target == null ->
                    warnings += "事件「${ev.reason ?: ev.action}」目标军队 ${ev.target} 不存在，动作 ${ev.action} 未生效"
                // moraleChange 例外：保留「溃兵归队」复振路径，允许作用于已解散但仍有兵的军队。
                target != null && !target.alive && ev.action != "moraleChange" ->
                    warnings += "事件「${ev.reason ?: ev.action}」目标 ${target.name} 已阵亡 / 出局，动作 ${ev.action} 未生效"
                else -> execute(ev, target)
            }

            fired.add(FiredEvent(ev.action, ev.target, ev.reason ?: ""))
        }
        return fired
    }

    private fun execute(ev: EventSpec, target: SimArmy?) {
        // 显式 no-op（非 else 兜底）：全局事件（target 为空）不作用于任何军队，
        // 不改兵力 / 士气 / 补给 / 意图；事件仍由 [fire] 登记进 FiredEvent，供回合 notes 与回放展示。
        if (target == null) return
        // 注册表口径：字符串字面量 when（未做枚举化）。适用条件——纯 JVM 单注册表、标签与
        // campaign JSON 原样 camelCase 对齐；若动作数继续膨胀可改 enum + handler map，
        // 但须同步 crossing 栈枚举表（他人域，本单不改）。保持行为等价：分支顺序不影响结果。
        when (ev.action) {
            // ── 既有 5 分支（口径不变） ──────────────────────────────
            "moraleChange" -> {
                target.morale = Morale.fromString(ev.value)
                // 士气复振 = 溃兵归队：还有人就不该留在「已解散」状态当幽灵。
                if (target.morale != Morale.BROKEN && target.strength > 0) {
                    target.alive = true
                    target.surrendered = false
                }
            }
            "surrender" -> {
                target.alive = false
                target.surrendered = true
            }
            "setIntent" -> {
                applyIntent(target, ev.value ?: "hold")
                if (ev.targetPos != null) target.target = ev.targetPos
            }
            "supplyCut" -> {
                target.supply = 0.0
            }
            "strengthDelta" -> {
                val delta = ev.value?.toDoubleOrNull() ?: 0.0
                target.strength = (target.strength + delta).coerceAtLeast(0.0).toInt()
                if (target.strength <= 0) target.alive = false
            }

            // ── 事件型：火攻 / 攻城器械 / 阵形突破 ────────────────────
            "fireAttack" -> {
                // 火攻：折损三成五上下，士气降一级；兵力打空即出局。
                applyLoss(target, fraction(ev.value, LOSS_FIRE_ATTACK))
                target.morale = MoraleSystem.down(target.morale)
            }
            "siegeEngine" -> {
                // 回回炮 / 攻城槌破城：重挫半数以上，士气直接击溃。
                applyLoss(target, fraction(ev.value, LOSS_SIEGE_ENGINE))
                target.morale = Morale.BROKEN
            }
            "breakthrough" -> {
                // 楔形阵中央突破：当面之军被凿穿一角，折损两成五，士气降一级。
                applyLoss(target, fraction(ev.value, LOSS_BREAKTHROUGH))
                target.morale = MoraleSystem.down(target.morale)
            }
            "defendFall" -> {
                // 据点失守、守军殉国：兵力归零、移出棋盘；不降 surrendered（战死非被俘）。
                target.strength = 0
                target.alive = false
                target.morale = Morale.BROKEN
            }
            "capture" -> {
                // 主帅被俘：出局且标记投降，不再参与后续接战。
                target.surrendered = true
                target.alive = false
                target.strength = 0
            }
            "annihilate" -> {
                // 全军覆没：兵力归零、出局、士气击溃；非投降。
                target.strength = 0
                target.alive = false
                target.morale = Morale.BROKEN
            }

            // ── 混合型：士气击溃 + 改溃逃意图 ─────────────────────────
            "navalRout" -> {
                // 舟师溃乱：部分舟船沉没，士气崩溃，意图转 rout 脱离水阵。
                applyLoss(target, fraction(ev.value, LOSS_NAVAL_ROUT))
                target.morale = Morale.BROKEN
                applyIntent(target, "rout")
            }
            "rout" -> {
                // 陆战溃散：士气崩溃，意图转 rout；损耗由后续遭遇 / 士气系统结算。
                target.morale = Morale.BROKEN
                applyIntent(target, "rout")
            }

            // ── 意图型：只改写意图，路径规划交由 IntentResolver ──────
            // 可观察结果 = intent / intentActive 改写 + 旧 path/hunt 清空；
            // 下回合 IntentResolver 规则裁决与 SimEngine.applyIntent 据此选路（flank 走 flankPath）。
            "ambush" -> applyIntent(target, "ambush")
            "flank" -> applyIntent(target, "flank")
            "retreat" -> applyIntent(target, "retreat")
            "escape" -> applyIntent(target, "escape")
            "feignedRetreat" -> {
                // 佯退诱敌：目标被诱出既阵转入追击（advance），阵形松动士气降一级。
                applyIntent(target, "advance")
                target.morale = MoraleSystem.down(target.morale)
            }

            // 未知动作：记录告警并照常登记 FiredEvent，不许静默 continue 吞掉。
            else -> warnings += "未知 action「${ev.action}」（目标 ${ev.target ?: "全局"}）：事件已记录，无生效分支"
        }
    }

    /** value 折损比例解析：空白 / 非法数字 / 越界一律回落到默认比例，不抛异常。 */
    private fun fraction(raw: String?, default: Double): Double =
        raw?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: default

    /** 按比例扣兵；兵力钳制 ≥0，打空即出局。 */
    private fun applyLoss(army: SimArmy, fraction: Double) {
        val loss = (army.strength * fraction).roundToInt()
        army.strength = (army.strength - loss).coerceAtLeast(0)
        if (army.strength <= 0) army.alive = false
    }

    /** 改写意图并清掉旧路径 / 追击绑定，逼意图裁决器下回合重新规划。 */
    private fun applyIntent(army: SimArmy, intent: String) {
        army.intent = intent
        army.intentActive = intent
        army.path = null
        army.hunt = null
    }
}
