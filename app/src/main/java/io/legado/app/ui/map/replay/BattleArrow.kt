package io.legado.app.ui.map.replay

import io.legado.app.ui.map.sandbox.TerrainElevation
import kotlin.math.min

/**
 * 箭头兵锋样式（切片-07 箭头状态对应军队意图）。
 *
 * SOLID 进军实线金 / DASHED 急行军虚线（间距短、闪得快）/ ARC 包抄弧线（绕山曲线）/
 * SCATTER 撤退末端散开虚线 / AMBUSH_DOT 设伏（不画兵锋，棋子虚化 + 地面脉冲红点）。
 */
enum class ArrowStyle {
    SOLID,
    DASHED,
    ARC,
    SCATTER,
    AMBUSH_DOT,
}

/**
 * 一条行军箭头（纯 JVM）。
 *
 * 切片-07：箭身根部细锋部粗，内部 2-3 粒金色光粒子沿箭头方向流动；箭头贴地走，
 * 上坡抬头下坡低头。路径点为地形表面坐标，逐节点贴地高度见 [ground]，由注入高程采样。
 */
data class BattleArrow(
    val id: String,
    val armyId: String,
    val role: SideRole,
    val intent: Intent,
    val points: List<LatLng>,
    val flowParticles: Int,
    /** 箭身各节点贴地高度（世界单位）；与 [points] 一一对应，上坡抬头下坡低头按此取高。 */
    val ground: List<GroundPoint> = emptyList(),
) {

    val style: ArrowStyle get() = styleFor(intent)

    val isValid: Boolean get() = points.size >= 2

    /** 两军箭头是否在阈值距离内相遇（遭遇 / 交战判定输入）。 */
    fun meets(other: BattleArrow, thresholdKm: Double): Boolean {
        if (!isValid || !other.isValid) return false
        for (a in points) {
            for (b in other.points) {
                if (a.distanceKm(b) <= thresholdKm) return true
            }
        }
        return false
    }

    companion object {

        /** 意图 → 箭头样式映射（切片-07 箭头状态表）。 */
        fun styleFor(intent: Intent): ArrowStyle = when (intent) {
            Intent.ADVANCE -> ArrowStyle.SOLID
            Intent.RAPID -> ArrowStyle.DASHED
            Intent.FLANK -> ArrowStyle.ARC
            Intent.RETREAT -> ArrowStyle.SCATTER
            Intent.AMBUSH -> ArrowStyle.AMBUSH_DOT
        }

        /** 光粒子数 2-3 粒，按箭头 id 稳定散列。 */
        fun particleCount(arrowId: String): Int {
            val span = ReplayConfig.ARROW_PARTICLES_MAX - ReplayConfig.ARROW_PARTICLES_MIN
            return ReplayConfig.ARROW_PARTICLES_MIN +
                ((arrowId.hashCode() and 0x7FFFFFFF) % (span + 1))
        }

        /**
         * 由棋子与其目标点生成箭头；设伏棋子不生成兵锋。
         * @param elevation 注入地形采样器；默认 Flat 平面兜底，箭身节点高度 0.0
         */
        fun fromPiece(
            piece: ArmyPiece,
            role: SideRole,
            elevation: TerrainElevation = TerrainElevation.Flat,
        ): BattleArrow? {
            if (piece.intent == Intent.AMBUSH) return null
            if (!piece.hasTarget) return null
            val target = LatLng(piece.targetLat!!, piece.targetLng!!)
            val origin = LatLng(piece.lat, piece.lng)
            // 包抄弧线由路线途经点决定；模型层先给起终两点，绕山折点由路线数据补齐。
            val points = listOf(origin, target)
            val ground = points.map { ReplayGround.sample(elevation, it.lat, it.lng) }
            return BattleArrow(
                id = "${piece.id}-arrow",
                armyId = piece.id,
                role = role,
                intent = piece.intent,
                points = points,
                flowParticles = particleCount(piece.id),
                ground = ground,
            )
        }
    }
}
