package io.legado.app.ui.map.globe.replay.campaign

/**
 * 战役一方。颜色不在此硬编码，由阵营 id 走 FactionPalette 同一张 token 表解析，
 * 保证沙盘与地球仪配色一致（切片 18：跟沙盘配色一张表）。
 */
data class CampaignSide(
    val id: String,
    val name: String,
)
