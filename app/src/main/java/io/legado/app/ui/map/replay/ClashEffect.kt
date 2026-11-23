package io.legado.app.ui.map.replay

/**
 * 交战动画阶段（切片-07 交战场面）。
 *
 * NONE 无交战 → RING 第一阶段 0-200ms 朱砂冲击环扩散 →
 * CHARGE 第二阶段 200-600ms 两棋子互冲两次 + 火花 → DONE 动画结束待清理。
 */
enum class ClashStage {
    NONE,
    RING,
    CHARGE,
    DONE,
}

/** 冲撞火花：一粒金色火花的偏移方向（世界单位）。 */
data class Spark(val index: Int, val offsetX: Double, val offsetY: Double)

/**
 * 单场交战效果（纯 JVM，时间由注入时钟打点，单测可直接推进毫秒断言）。
 *
 * @param startedAtMs 效果启动时刻（导演层时钟毫秒）
 */
class ClashEffect(
    val id: String,
    val point: LatLng,
    val startedAtMs: Long,
) {

    fun stage(nowMs: Long): ClashStage {
        val elapsed = nowMs - startedAtMs
        return when {
            elapsed < 0 -> ClashStage.NONE
            elapsed < ReplayConfig.CLASH_RING_MS -> ClashStage.RING
            elapsed < ReplayConfig.CLASH_CHARGE_END_MS -> ClashStage.CHARGE
            else -> ClashStage.DONE
        }
    }

    /**
     * 冲击环当前半径（dp）：RING 阶段从 0 扩到 120dp；CHARGE 阶段随消散回缩；
     * DONE 后归零。暗红色半透明由渲染层按 token 叠色。
     */
    fun ringRadiusDp(nowMs: Long): Float {
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0)
        return when (stage(nowMs)) {
            ClashStage.NONE -> 0f
            ClashStage.RING -> {
                val t = elapsed.toFloat() / ReplayConfig.CLASH_RING_MS
                ReplayConfig.CLASH_RING_RADIUS_DP * t.coerceIn(0f, 1f)
            }
            ClashStage.CHARGE -> {
                val t = (elapsed - ReplayConfig.CLASH_RING_MS).toFloat() /
                    (ReplayConfig.CLASH_CHARGE_END_MS - ReplayConfig.CLASH_RING_MS)
                ReplayConfig.CLASH_RING_RADIUS_DP * (1f - t.coerceIn(0f, 1f))
            }
            ClashStage.DONE -> 0f
        }
    }

    /** 互冲次数：第二阶段内两个棋子各冲两次（弹回再冲），由渲染层按相位摆位。 */
    fun chargeCycles(nowMs: Long): Int = if (stage(nowMs) == ClashStage.CHARGE) 2 else 0

    /** 火花 3-4 粒，按交战 id 稳定散列；每次冲撞爆出一批。 */
    fun sparks(): List<Spark> {
        val span = ReplayConfig.SPARKS_MAX - ReplayConfig.SPARKS_MIN
        val count = ReplayConfig.SPARKS_MIN +
            ((id.hashCode() and 0x7FFFFFFF) % (span + 1))
        return List(count) { i ->
            val angle = (Math.PI * 2 * i) / count
            val radius = 0.5 + 0.3 * ((i * 29) % 10) / 10.0
            Spark(i, Math.cos(angle) * radius, Math.sin(angle) * radius)
        }
    }

    fun isFinished(nowMs: Long): Boolean = stage(nowMs) == ClashStage.DONE
}

/**
 * 遭遇光环：两军箭头相遇点炸一圈朱砂红光环，300ms 消散（切片-07 多军同时动）。
 */
class EncounterRing(
    val id: String,
    val point: LatLng,
    val startedAtMs: Long,
) {

    fun active(nowMs: Long): Boolean =
        (nowMs - startedAtMs) in 0 until ReplayConfig.ENCOUNTER_RING_MS

    fun progress(nowMs: Long): Float {
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0)
        return (elapsed.toFloat() / ReplayConfig.ENCOUNTER_RING_MS).coerceIn(0f, 1f)
    }

    fun isFinished(nowMs: Long): Boolean = !active(nowMs)
}
