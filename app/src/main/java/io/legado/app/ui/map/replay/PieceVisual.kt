package io.legado.app.ui.map.replay

/**
 * 溃散光屑：一片碎光的水平 / 垂直偏移（世界单位），飘散轨迹由渲染层按时间推进。
 */
data class Shard(val index: Int, val offsetX: Double, val offsetY: Double)

/**
 * 棋子派生视觉状态（纯 JVM，切片-07 军队棋子 / 选中态 / 麾下状态可视化）。
 *
 * 模型只算「该长什么样」：尺寸倍率、透明度、颤幅、顶光长度、呼吸周期、溃散碎片；
 * 动画推进（呼吸明暗、碎片飘散）由渲染层按帧读取本状态执行。
 */
data class PieceVisual(
    val piece: ArmyPiece,
    val scale: Double,
    val opacity: Float,
    val jitterDeg: Double,
    val topLightUnits: Double,
    val breathMs: Long,
    val selected: Boolean,
    val routShards: List<Shard>?,
    val residualRing: Boolean,
    val color: Long,
) {

    val isRout: Boolean get() = routShards != null

    companion object {

        /** 兵力达到该阈值算主力，棋子尺寸 ×1.5（切片-07：主力比偏师大 1.5 倍）。 */
        const val MAIN_FORCE_STRENGTH: Int = 10

        /**
         * 由棋子派生视觉状态。
         *
         * @param role 阵营角色（由导演层按 Campaign 解析传入）；null 时兜底暖金
         * @param selected 是否选中（选中：呼吸加快到 400ms，光环两圈慢转）
         * @param routElapsedMs 溃散已持续毫秒；null 表示未溃散。超过 [ReplayConfig.ROUT_DURATION_MS]
         *        后棋子应不再绘制，只留暗红残影圈
         */
        fun of(
            piece: ArmyPiece,
            role: SideRole?,
            selected: Boolean,
            routElapsedMs: Long? = null,
        ): PieceVisual {
            val color = when (role) {
                SideRole.OWN -> ReplayTokens.ownColor()
                SideRole.ENEMY -> ReplayTokens.enemyColor()
                SideRole.ALLY -> ReplayTokens.allyColor()
                null -> ReplayTokens.brandGold
            }

            // 尺寸：基础倍率随兵力，主力再 ×1.5；偏师不放大。
            val baseScale = 0.8 + piece.strength / 12.0
            val mainForce = piece.strength >= MAIN_FORCE_STRENGTH
            val scale = if (mainForce) baseScale * ReplayConfig.MAIN_FORCE_SCALE else baseScale

            // 设伏：棋子虚化到 0.3 opacity，地面另画脉冲红点。
            val ambush = piece.intent == Intent.AMBUSH
            val opacity = if (ambush) ReplayConfig.AMBUSH_OPACITY else 1f

            // 士气三态：高昂稳定直射；动摇 0.5° 微颤、顶光缩短；溃散出碎片。
            val jitterDeg: Double
            val topLightUnits: Double
            when (piece.morale) {
                Morale.HIGH, Morale.STEADY -> {
                    jitterDeg = 0.0
                    topLightUnits = ReplayConfig.TOP_LIGHT_UNITS
                }
                Morale.WAVERING -> {
                    jitterDeg = ReplayConfig.WAVER_JITTER_DEG
                    topLightUnits = ReplayConfig.TOP_LIGHT_UNITS * 0.6
                }
                Morale.ROUT -> {
                    jitterDeg = 0.0
                    topLightUnits = 0.0
                }
            }

            val breathMs = if (selected) ReplayConfig.SELECTED_BREATH_MS else ReplayConfig.BREATH_MS

            val routShards: List<Shard>?
            val residualRing: Boolean
            if (piece.morale == Morale.ROUT) {
                routShards = buildShards(piece.id)
                // 溃散 800ms 内飘碎片；超时后碎片消失，原位留暗红残影圈。
                residualRing = routElapsedMs == null || routElapsedMs >= ReplayConfig.ROUT_DURATION_MS
            } else {
                routShards = null
                residualRing = false
            }

            return PieceVisual(
                piece = piece,
                scale = scale,
                opacity = opacity,
                jitterDeg = jitterDeg,
                topLightUnits = topLightUnits,
                breathMs = breathMs,
                selected = selected,
                routShards = routShards,
                residualRing = residualRing,
                color = color,
            )
        }

        /** 溃散碎片数 6-8 片，按棋子 id 稳定散列，偏移方向不每次跳动。 */
        private fun buildShards(armyId: String): List<Shard> {
            val span = ReplayConfig.ROUT_SHARDS_MAX - ReplayConfig.ROUT_SHARDS_MIN
            val count = ReplayConfig.ROUT_SHARDS_MIN +
                ((armyId.hashCode() and 0x7FFFFFFF) % (span + 1))
            return List(count) { i ->
                val angle = (Math.PI * 2 * i) / count
                val radius = 0.6 + 0.4 * ((i * 37) % 10) / 10.0
                Shard(i, Math.cos(angle) * radius, Math.sin(angle) * radius)
            }
        }
    }
}
