package io.legado.app.ui.globe.core.replay.campaign

import io.legado.app.ui.globe.core.replay.route.GlobeRoute
import io.legado.app.ui.globe.core.replay.route.RouteWaypoint

/** 战役一方（阵营配色走 FactionPalette 同一张表）。 */
data class CampaignSide(
    val id: String,
    val name: String,
    val colorHex: String,
)

/** 时间轴阶段：turn 为该阶段起始回合，篆章节点落在底部金线时间轴上。 */
data class CampaignPhase(
    val turn: Int,
    val label: String,
    val note: String? = null,
)

/**
 * 一场国外战役（纯 JVM，切片 18）。
 *
 * 数据 schema 对齐 `campaign.json`：引擎算大圆，地球仪渲染弧线；
 * 沙盘与地球仪共用同一份数据，引擎不区分载体。
 *
 * @param rounds 总回合数（切片 18：汉尼拔 20 / 亚历山大 30 / 十字军 20 / 蒙古 30 / 维京 15 / 哥伦布 10）
 * @param dailyLi 日行里数 30 / 50 / 80
 */
data class Campaign(
    val id: String,
    val title: String,
    val era: String,
    val context: String,
    val rounds: Int,
    val sides: List<CampaignSide>,
    val waypoints: List<RouteWaypoint>,
    val phases: List<CampaignPhase>,
    val dailyLi: Int,
    val factionIds: List<String>,
) {

    /** 由途经点派生行军路线（主阵营取第一方配色）。 */
    fun route(): GlobeRoute {
        val faction = factionIds.firstOrNull() ?: sides.firstOrNull()?.id ?: "macedon"
        val color = sides.firstOrNull()?.colorHex ?: "#D4A54A"
        return GlobeRoute(
            id = "$id-route",
            factionId = faction,
            colorHex = color,
            waypoints = waypoints,
            dailyLi = dailyLi,
        )
    }

    /** 某回合所处阶段（turn ≥ phase.turn 的最后一个阶段）；无阶段返回 null。 */
    fun phaseAt(turn: Int): CampaignPhase? {
        if (phases.isEmpty()) return null
        var current: CampaignPhase? = null
        for (phase in phases) {
            if (turn >= phase.turn) current = phase else break
        }
        return current
    }

    /** 回合是否已到末尾。 */
    fun isComplete(turn: Double): Boolean = turn >= rounds
}
