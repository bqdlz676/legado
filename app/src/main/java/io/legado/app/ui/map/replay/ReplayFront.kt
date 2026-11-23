package io.legado.app.ui.map.replay

import io.legado.app.ui.map.sandbox.TerrainElevation

/**
 * 回放势力 / 战线多边形（纯 JVM，切片-07 + 契约 §1.5 E 项势力色）。
 *
 * 切片-06 §一的贴地约定同样适用回放：多边形不是浮在空中的线框，每个顶点都要采样
 * 地形高度，渲染层按 [sampleSurface] 的地表点拼贴贴合起伏的半透明色块。
 *
 * 填充不透明度对齐 §1.5 E：己方暖金 0.12 / 敌方灰青 0.10 / 盟友铜 0.08；颜色仍走
 * [ReplayTokens] 阵营色 token，本类只持不透明度，不写色号。
 *
 * 边界兜底：顶点不足 3 个时 [sampleSurface] 返回空序列，对应渲染 pass 跳过，不崩、
 * 不空白态。战役资产暂无多边形字段时，[Campaign.sectors] 为空列表，同样走跳过分支。
 */
data class ReplayFrontSector(
    val id: String,
    val name: String,
    val role: SideRole,
    val pts: List<LatLng>,
) {

    /** 填充不透明度：己方 0.12 / 敌方 0.10 / 盟友 0.08（契约 §1.5 E）。 */
    fun fillAlpha(): Float = when (role) {
        SideRole.OWN -> ReplayConfig.OWN_FILL_ALPHA
        SideRole.ENEMY -> ReplayConfig.ENEMY_FILL_ALPHA
        SideRole.ALLY -> ReplayConfig.ALLY_FILL_ALPHA
    }

    /** 逐顶点贴地采样；顶点不足 3 个返回空序列，渲染 pass 跳过。 */
    fun sampleSurface(elevation: TerrainElevation): List<GroundPoint> =
        if (pts.size < 3) emptyList()
        else pts.map { ReplayGround.sample(elevation, it.lat, it.lng) }
}
