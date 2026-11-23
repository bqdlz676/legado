package io.legado.app.ui.map.crossing.terrain

/**
 * 高程图 → 灰度栅格解码器（纯 JVM 契约；Android 侧薄适配实现后注入壳层桥接）。
 *
 * 为什么是接口而不是引擎直接读资产：引擎层（model / engine / terrain）必须保持零
 * `android` import，可在普通 JVM 单测中断言；PNG 解码属于平台能力，由壳层实现本接口
 * 后经 [io.legado.app.ui.map.crossing.shell.CrossingRasterBridge] 装配。
 *
 * 契约（实现方必须遵守）：
 * - 解码失败 / 资产缺失 / 栅格退化 / 实现内部抛任何异常 → 返回 null，由桥接层回落合成
 *   高程场并置降级标志；实现方不得把异常抛回引擎。
 * - 灰度口径与 sandbox `DemTerrainElevation` / [HeightRaster] 同源：8-bit 灰度 0..255，
 *   行优先、行 0 = 北缘（纬度最高）、列 0 = 西缘（经度最小）；0 ↔ 0m，255 ↔ 7000m。
 * - 同一资产在一次载入内只解码一次；实例缓存归属 D13（DEM 实例缓存），本接口不负责缓存。
 */
fun interface RasterDecoder {

    /**
     * 按战役 JSON `heightMap` 字段声明的图名解码；返回 0..255 灰度栅格；不可用时返回 null。
     *
     * @param assetName JSON `heightMap` 值（如 `gaixia.png`）；空白由调用侧先拦截，
     *        实现方仍须对非法 / 找不到的名字返回 null，不得抛异常
     */
    fun decode(assetName: String): HeightRaster?
}
