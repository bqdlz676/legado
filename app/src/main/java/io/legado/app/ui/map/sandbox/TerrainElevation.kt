package io.legado.app.ui.map.sandbox

/**
 * 地形高度采样接口（纯 JVM）。
 *
 * 切片-06 §一：势力多边形「贴在地形表面」——按多边形顶点采样地形高度，生成贴合地表
 * 起伏的半透明色块；河流 / 箭头同理贴地走。引擎层（DEM 网格或噪声地形）实现本接口，
 * 沙盘模型只负责取高度，不关心高度从哪来。
 *
 * 兜底约定：实现方在无 DEM 数据时必须返回 0.0（平面地形），不允许返回 NaN 或抛异常，
 * 保证离线 / 空数据场景色块仍能平铺在基准面上。
 */
fun interface TerrainElevation {

    /** 返回 (lat, lng) 处地形高度（世界单位）；无数据时返回 0.0。 */
    fun heightAt(lat: Double, lng: Double): Double

    /** 平面兜底实现：所有位置高度为 0，供空数据 / 单测使用。 */
    object Flat : TerrainElevation {
        override fun heightAt(lat: Double, lng: Double): Double = 0.0
    }
}
