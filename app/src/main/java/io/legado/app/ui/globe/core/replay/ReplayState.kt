package io.legado.app.ui.globe.core.replay

import io.legado.app.ui.globe.core.replay.campaign.Campaign
import io.legado.app.ui.globe.core.replay.campaign.SeedCampaigns
import io.legado.app.ui.globe.core.replay.marker.GlobeMarker
import io.legado.app.ui.globe.core.replay.power.PowerLayer
import io.legado.app.ui.globe.core.replay.power.SeedPowers
import io.legado.app.ui.globe.core.replay.route.GlobeRoute
import kotlin.math.min

/**
 * 战役回放状态（纯 JVM，切片 18）。
 *
 * 持有当前战役、回合进度、势力图层；渲染层每帧读它取路线采样、箭头位置、
 * 粒子偏移、光针与势力色块。引擎算大圆，地球仪只负责渲染弧线。
 *
 * 边界兜底：无匹配战役时 current 为 null，渲染层跳过回放 pass，不崩溃、不空白态。
 */
class ReplayState(
    val campaigns: List<Campaign> = SeedCampaigns.all,
    val powers: PowerLayer = PowerLayer(SeedPowers.all),
) {

    /** 当前战役 id；null 表示不回放任何战役（仅显示势力图层）。 */
    var campaignId: String? = campaigns.firstOrNull()?.id
        private set

    /** 是否正在播放时间轴。 */
    var playing: Boolean = false
        private set

    /** 当前回合（可小数，供弧线进度 / 粒子连续流动）。 */
    var turn: Double = 0.0
        private set

    val current: Campaign?
        get() = campaigns.firstOrNull { it.id == campaignId }

    /** 切换战役并归零回合。 */
    fun select(id: String) {
        if (campaigns.none { it.id == id }) return
        campaignId = id
        turn = 0.0
        playing = false
    }

    fun play() { playing = true }

    fun pause() { playing = false }

    fun reset() { turn = 0.0; playing = false }

    /** 直接定位到指定回合（测试 / 时间轴拖拽用），钳制在 [0, rounds]。 */
    fun seek(turnValue: Double) {
        val c = current ?: return
        turn = turnValue.coerceIn(0.0, c.rounds.toDouble())
    }

    /**
     * 推进一帧。
     * @param dt 帧间隔秒
     * @param turnsPerSec 每秒推进回合数
     */
    fun update(dt: Double, turnsPerSec: Double = 1.0) {
        val campaign = current ?: return
        if (!playing) return
        turn = min(turn + dt * turnsPerSec, campaign.rounds.toDouble())
        if (campaign.isComplete(turn)) playing = false
    }

    /** 路线进度 0..1（回合 / 总回合）。 */
    fun routeProgress(): Double {
        val c = current ?: return 0.0
        if (c.rounds <= 0) return 0.0
        return (turn / c.rounds).coerceIn(0.0, 1.0)
    }

    /** 当前战役行军路线；无战役返回 null。 */
    fun route(): GlobeRoute? = current?.route()

    /** 当前战役途经点光针。 */
    fun markers(): List<GlobeMarker> {
        val c = current ?: return emptyList()
        val faction = c.factionIds.firstOrNull()
        return c.waypoints.map { GlobeMarker.fromWaypoint(it, faction) }
    }

    /** 当前回合所处阶段。 */
    fun currentPhase() = current?.phaseAt(turn.toInt())
}
