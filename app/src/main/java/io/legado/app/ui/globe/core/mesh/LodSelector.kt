package io.legado.app.ui.globe.core.mesh

import io.legado.app.ui.globe.core.GlobeConfig
import kotlin.math.max

/**
 * LOD 选择与切换 morph 进度（纯 JVM）。
 *
 * 距离按「相机到球心距离 / 球半径」归一化（切片 20）：
 *  - > 3R        → LOD0（128×128，2K）
 *  - 1R – 3R     → LOD1（256×256，4K）
 *  - 0.1R – 1R   → LOD2（512×512，8K）
 *
 * 切换在 GPU 上 morph，2–3 帧内完成（[GlobeConfig.LOD_MORPH_FRAMES]）；
 * 本选择器只产出目标层级与 morph 进度，真正的顶点过渡由渲染层执行。
 */
class LodSelector(initial: LodLevel = LodLevel.LOD0) {

    var current: LodLevel = initial
        private set

    var target: LodLevel = initial
        private set

    /** morph 剩余帧数；0 表示已稳定在 current。 */
    var framesLeft: Int = 0
        private set

    val isMorphing: Boolean get() = framesLeft > 0 || current != target

    /** morph 进度 0f → 1f（1 表示新层级已就位）。 */
    val morphProgress: Float
        get() {
            if (current == target) return 1f
            val total = GlobeConfig.LOD_MORPH_FRAMES
            val done = total - framesLeft
            return (done.toFloat() / total).coerceIn(0f, 1f)
        }

    /**
     * 根据相机距离选层级。
     * @param distance 相机到球心的世界距离
     * @param radius   球半径
     */
    fun select(distance: Double, radius: Double): LodLevel {
        val ratio = if (radius <= 0) 1.0 else distance / radius
        val next = when {
            ratio > GlobeConfig.LOD_FAR_RATIO -> LodLevel.LOD0
            ratio >= GlobeConfig.LOD_NEAR_RATIO -> LodLevel.LOD1
            else -> LodLevel.LOD2
        }
        if (next != target) {
            target = next
            framesLeft = GlobeConfig.LOD_MORPH_FRAMES
        }
        return next
    }

    /** 每帧推进 morph；返回当前应使用的层级（morph 期间仍画 current，进度给渲染层过渡）。 */
    fun update(): LodLevel {
        if (framesLeft > 0) {
            framesLeft = max(0, framesLeft - 1)
            if (framesLeft == 0) current = target
        } else if (current != target) {
            current = target
        }
        return current
    }

    /** 测试 / 兜底用：强制对齐到目标层级，清空 morph。 */
    fun snapTo(level: LodLevel) {
        current = level
        target = level
        framesLeft = 0
    }
}
