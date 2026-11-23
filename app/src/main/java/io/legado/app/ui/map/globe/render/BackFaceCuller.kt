package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.math.Vec3

/**
 * 背面剔除判定原因（纯 JVM）。
 */
enum class CullReason {
    /** 正面，保留。 */
    NONE,

    /** 三顶点都在背面半球（相机在 +Z，z 均 ≤ -eps）。 */
    BACK_HEMISPHERE,

    /** 三角形面法线背向相机（面序朝外但转到了背面）。 */
    BACK_FACE,
}

/** 单片剔除结论。 */
data class CullDecision(val culled: Boolean, val reason: CullReason) {
    companion object {
        val KEEP = CullDecision(false, CullReason.NONE)
        fun cull(reason: CullReason) = CullDecision(true, reason)
    }
}

/**
 * 背面半球 / 背面剔除器（纯 JVM，渲染核心）。
 *
 * 两道剔除由粗到细：
 *  1. 半球剔除：三角形三个顶点都在相机背面半球（z ≤ -eps），整片不进投影；
 *  2. 面剔除：面法线与视线方向点积 < 0，说明片序背向相机，丢弃。
 *
 * 为什么两道都保留：半球剔除先挡掉整个背半球的片，面剔除再兜住跨轮廓边缘、
 * 法线恰好背向相机的片；相机坐标系下视线方向恒为 +Z（相机在 +Z 看向原点），
 * 调用方传入世界空间已变换的顶点即可。
 */
object BackFaceCuller {

    /** 深度容差，与 [GlobeCamera.frontEpsilon] 同口径。 */
    const val DEPTH_EPS: Double = 1e-3

    /** 顶点是否在正面半球（z > -eps）。 */
    fun hemisphereVisible(worldPoint: Vec3): Boolean = worldPoint.z > -DEPTH_EPS

    /** 面法线是否朝向相机（视线方向默认 +Z）。 */
    fun faceFacing(normal: Vec3, viewDir: Vec3 = Vec3.UNIT_Z): Boolean =
        normal.normalize().dot(viewDir.normalize()) > 0.0

    /**
     * 判定一片三角形是否剔除。
     * @param a,b,c 世界空间顶点（已施加倾斜 / 偏航 / 俯仰）
     * @param viewDir 视线方向（表面指向相机），默认 +Z
     */
    fun shouldCull(a: Vec3, b: Vec3, c: Vec3, viewDir: Vec3 = Vec3.UNIT_Z): CullDecision {
        val frontCount = countFront(a, b, c)
        if (frontCount == 0) return CullDecision.cull(CullReason.BACK_HEMISPHERE)

        val ab = b - a
        val ac = c - a
        val normal = ab.cross(ac)
        if (normal.lengthSquared() > 1e-18 && !faceFacing(normal, viewDir)) {
            return CullDecision.cull(CullReason.BACK_FACE)
        }
        return CullDecision.KEEP
    }

    private fun countFront(a: Vec3, b: Vec3, c: Vec3): Int {
        var n = 0
        if (hemisphereVisible(a)) n++
        if (hemisphereVisible(b)) n++
        if (hemisphereVisible(c)) n++
        return n
    }
}
