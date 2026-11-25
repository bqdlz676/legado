package io.legado.app.help.artifact

import io.legado.app.help.bubble.contract.BubbleContent

/**
 * 名物库兜底结果（B25 收口，锚点卡 `docs/锚点/B25-超时态回读.md` §二-1 第二分支：
 * 返回结构显式带状态字段）。
 *
 * 为什么不走「气泡承载标记」：冻结契约 [BubbleContent] 九字段不可扩（选型裁决 §3.4 / §7，
 * 契约文件在 `help/bubble/contract`，不在本单允许改动域），状态标记不能塞进 body / detail /
 * source 等既有展示字段；故在 `help/artifact` 包内另立本结果壳，把兜底态 [state] 与结构完整的
 * 气泡壳 [content] 成对返回。这是唯一的状态载体，不在气泡壳上另设第二份标记。
 *
 * 五态回读同构（判据 3）：[FallbackState.NO_DATA] / [FallbackState.CACHE_MISS_OFFLINE] /
 * [FallbackState.YEAR_MISSING] / [FallbackState.AMBIGUOUS] / [FallbackState.TIMEOUT]
 * 一律经本结构的 [state] 回读，不为超时单开特例；新增枚举值时 [fallbackMessage] 的 when
 * 分支必须同步补齐，否则编译期穷尽检查会红。
 *
 * 纯 JVM：不 import Android / Compose 类型，普通 JVM 单测可直接构造断言。
 *
 * @param state   兜底边界态；由兜底构造器在落壳时一并钉死，调用方先读本字段再取气泡壳。
 * @param content 结构完整的气泡壳；绝不返回 null、绝不抛异常到 UI。
 */
data class ArtifactFallbackResult(
    val state: FallbackState,
    val content: BubbleContent,
) {

    /**
     * 兜底提示口径（B25 判据 2 的生产消费落点）：接线层 / UI 按本字段展示分流文案，
     * 超时与查无此项为两种不同口径；五态穷举匹配，各态只取本层既定提示，不猜数据、不造假条目。
     */
    val fallbackMessage: String
        get() = when (state) {
            FallbackState.NO_DATA -> "查无此项"
            FallbackState.CACHE_MISS_OFFLINE -> "当前离线，本地未收录"
            FallbackState.YEAR_MISSING -> "年代信息缺失，未作年代过滤"
            FallbackState.AMBIGUOUS -> "存在同名条目，请点选确认"
            FallbackState.TIMEOUT -> "加载超时，展示缓存内容"
        }
}
