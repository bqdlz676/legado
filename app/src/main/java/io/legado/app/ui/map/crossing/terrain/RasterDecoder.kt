package io.legado.app.ui.map.crossing.terrain

/**
 * 高程 PNG → 栅格解码器接口（纯 JVM 定义，Android 侧薄适配注入）。
 *
 * 为什么是接口而不是引擎直接读资产：引擎层（model / engine / terrain）必须保持零
 * `android` import，可在普通 JVM 单测中断言；PNG 解码属于平台能力，由壳层实现本接口
 * 后注入 [io.legado.app.ui.map.crossing.engine.CampaignController.load]。
 *
 * 契约（实现方必须遵守）：
 * - 解码失败 / 资产缺失 / 栅格退化时返回 null，由引擎回落合成高程场并置降级标志；
 *   实现方不得把异常抛回引擎。
 * - 灰度口径与 sandbox `DemTerrainLoader` 同源：8-bit 灰度 0..255 ↔ 0..7000m，
 *   行优先、行 0 = 北缘；同一资产在一次载入内只解码一次，结果可缓存复用。
 */
fun interface RasterDecoder {

    /**
     * 按战役 JSON 声明的高程图名解码；返回 0..255 灰度栅格；不可用时返回 null。
     * @param assetName JSON `heightMap` 字段值（如 `china_height.png`），缺省由调用侧给默认资产名
     */
    fun decode(assetName: String): HeightRaster?
}
