package io.legado.app.ui.map.globe.scene

import io.legado.app.ui.map.globe.replay.marker.GlobePlace
import io.legado.app.ui.map.globe.replay.marker.PlaceTier

/**
 * 地名标签分级可见性（纯 JVM，切片 19 / 20）。
 *
 *  - Billboard Sprite 朝向相机、固定在球面位置；
 *  - 都城任何缩放级别可见；一般地名放大后才浮出；
 *  - 远距时标签淡出，避免堆在一起看不清。
 */
object LabelScheme {

    /** 一般地名浮出的距离比阈值（相机距离 / 半径）；大于该值淡出到 0。 */
    const val NORMAL_LABEL_NEAR_RATIO: Double = 2.0

    /** 单个地名在当前距离比下的可见透明度 0..1；都城恒为 1。 */
    fun fadeOf(place: GlobePlace, distanceRatio: Double): Float = when (place.tier) {
        PlaceTier.CAPITAL -> 1f
        PlaceTier.NORMAL -> {
            if (distanceRatio >= NORMAL_LABEL_NEAR_RATIO) 0f
            else ((NORMAL_LABEL_NEAR_RATIO - distanceRatio) / NORMAL_LABEL_NEAR_RATIO).toFloat()
                .coerceIn(0f, 1f)
        }
    }

    /** 批量计算可见清单（fade > 0 的地名）。 */
    fun visible(places: List<GlobePlace>, distanceRatio: Double): List<Pair<GlobePlace, Float>> =
        places.map { it to fadeOf(it, distanceRatio) }.filter { it.second > 0f }
}
