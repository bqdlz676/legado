package io.legado.app.ui.map.globe.replay.campaign

/**
 * 时间轴阶段：turn 为该阶段起始回合，篆章节点落在底部金线时间轴上（切片 18 / 20）。
 */
data class CampaignPhase(
    val turn: Int,
    val label: String,
    val note: String? = null,
)
