package io.legado.app.ui.map.globe.replay.marker

/**
 * 球面地名条目（纯 JVM）。
 *
 * 切片 19 / 20：Billboard Sprite 朝向相机、固定在球面位置；
 * 标签密度分级——都城任何缩放级别可见，一般地名放大后才浮出；远距淡出避免堆叠。
 */
data class GlobePlace(
    val name: String,
    val lat: Double,
    val lng: Double,
    val tier: PlaceTier,
    val region: String,
)
