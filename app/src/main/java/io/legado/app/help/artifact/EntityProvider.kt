package io.legado.app.help.artifact

import io.legado.app.help.bubble.contract.BubbleContent

/**
 * D16 通用名物查询接口（原文 09:15：`EntityProvider.query(name, type, anchor) -> BubbleContent`）。
 *
 * 为什么是唯一统一查询接口：09:30 拍板——名剑 / 名马 / 名寺 / 名楼 / 名琴 / 名鼎 /
 * 星宿 / 名酒 / 名锦等其余类别走通用 EntityProvider，不加新 Provider；加新类别只加数据表，
 * 不改 Provider/UI（索引:218）。
 *
 * 边界（09:31）：本接口不重构 D1/D2/D3；D1 字典、D2 人物、D3 地名走各自原 Provider 路径，
 * 不双轨。本层内部可复用 D1/D2/D3 接口，但代码结构不合并。
 *
 * 纯 JVM：不 import Android / Compose 类型，普通 JVM 单测可直接实例化断言。
 */
interface EntityProvider {

    /**
     * 按名称 + 类别查询名物，返回一张结构完整的气泡内容。
     *
     * @param name   原文命中的名物名称（裸串，未消歧）。
     * @param type   schema type 取值（见 [ArtifactSchema] 各表 types）；未知 type 走兜底，不抛异常。
     * @param anchor 消歧锚点（文本时代背景 / 地点上下文等）；类型由接线层约定，本层以 [Any] 透传，
     *               可为 null。地名消歧年代锚用「文本时代背景」，不复用人物生卒区间（09:74）。
     * @return 结构完整的 [BubbleContent]；空数据 / 断网缓存未命中 / 缺年份 / 同名消歧失败
     *         均由实现层回退，绝不返回 null、绝不抛异常。
     */
    fun query(name: String, type: String, anchor: Any?): BubbleContent
}
