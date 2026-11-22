package io.legado.app.help.sim

/**
 * 落文 · 战役事件触发器（core:sim / §五）
 *
 * 事件完全由 campaign JSON 驱动：turn / 部队 + 兵力阈值 / 士气状态 为触发条件，
 * 动作覆盖士气变更、溃散、改令（含改目标点）、断粮、兵力增减。每条事件只触发一次。
 */
data class FiredEvent(val action: String, val target: String?, val reason: String)

object EventTrigger {

    fun fire(
        events: List<EventSpec>?,
        armies: List<SimArmy>,
        turn: Int,
    ): List<FiredEvent> {
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

            when (ev.action) {
                "moraleChange" -> {
                    if (target != null) {
                        target.morale = Morale.fromString(ev.value)
                        // 士气复振 = 溃兵归队：还有人就不该留在「已解散」状态当幽灵。
                        if (target.morale != Morale.BROKEN && target.strength > 0) {
                            target.alive = true
                            target.surrendered = false
                        }
                    }
                }
                "surrender" -> {
                    if (target != null) {
                        target.alive = false
                        target.surrendered = true
                    }
                }
                "setIntent" -> {
                    if (target != null) {
                        target.intent = ev.value ?: "hold"
                        target.intentActive = target.intent
                        if (ev.targetPos != null) target.target = ev.targetPos
                        target.path = null
                        target.hunt = null
                    }
                }
                "supplyCut" -> {
                    target?.supply = 0.0
                }
                "strengthDelta" -> {
                    if (target != null) {
                        val delta = ev.value?.toDoubleOrNull() ?: 0.0
                        target.strength = (target.strength + delta).coerceAtLeast(0.0).toInt()
                        if (target.strength <= 0) target.alive = false
                    }
                }
            }

            fired.add(FiredEvent(ev.action, ev.target, ev.reason ?: ""))
        }
        return fired
    }
}
