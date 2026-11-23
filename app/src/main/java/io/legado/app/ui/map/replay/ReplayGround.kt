package io.legado.app.ui.map.replay

import io.legado.app.ui.map.sandbox.DemTerrainElevation
import io.legado.app.ui.map.sandbox.TerrainElevation

/**
 * 回放地表采样点：经纬度 + 地形高度（世界单位）。
 *
 * 与沙盘 [io.legado.app.ui.map.sandbox.SurfacePoint] 同口径：高度由注入的同一
 * [TerrainElevation] 实例采样得到，棋子基座 / 箭身节点 / 势力多边形顶点 / 冲突特效
 * 落点都挂在这个高度上，保证与地形面咬合，不悬空、不穿地。
 */
data class GroundPoint(val lat: Double, val lng: Double, val height: Double)

/**
 * 回放贴地高程收口（纯 JVM，U11）。
 *
 * 高程来源：壳层把沙盘同一个 [TerrainElevation] 实例注入导演层；回放域内**不第二次
 * 解码 DEM 资产**，只调用接口 [TerrainElevation.heightAt]。采样口径与沙盘完全一致：
 * 0–255 灰度 ↔ 0–7000m，米制 × 0.009 得世界单位（7000m ↔ 63 单位），换算在 DEM
 * 核心内完成，回放不再乘第二套夸张系数。
 *
 * 兜底铁律：注入实例缺失 / 空数据 / 坐标 NaN / 结果非有限值，一律回落 0.0（平面），
 * 不抛异常、不产出 NaN、不留空白态；降级只影响可见标志，不阻断推演推进。
 */
object ReplayGround {

    /**
     * 取某经纬度的地形高度（世界单位）；对采样结果再做一道有限值 / 负值兜底。
     * 沙盘 DEM 核心自身已有兜底，这里是回放侧第二道防线，防止未来换实现时漏网。
     */
    fun heightAt(elevation: TerrainElevation, lat: Double, lng: Double): Double {
        val h = elevation.heightAt(lat, lng)
        return when {
            !h.isFinite() -> 0.0
            h < 0.0 -> 0.0
            else -> h
        }
    }

    /** 采样为地表点；坐标非法时高度兜底 0.0。 */
    fun sample(elevation: TerrainElevation, lat: Double, lng: Double): GroundPoint =
        GroundPoint(lat, lng, heightAt(elevation, lat, lng))

    /**
     * 降级判定：平面兜底实例，或 DEM 栅格退化 → true。
     *
     * 降级标志仅供壳层做可见提示（一行小字），不影响时钟推进 / 事件派发 / 交战检测。
     */
    fun isDegraded(elevation: TerrainElevation): Boolean = when (elevation) {
        TerrainElevation.Flat -> true
        is DemTerrainElevation -> elevation.degraded
        else -> false
    }
}
