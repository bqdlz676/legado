package io.legado.app.ui.map.globe.astro.data

/**
 * 金字塔层（L1 6.5–9.0 等 / L2 9.0–11.5 等）按需加载结果（D38 / 方案 §4.1）。
 *
 * 四态收敛：上层只按结果分流，不接收原始数组引用（维持 §十二.2 零分配口径）；
 * 除 [Loaded] 外其余三态都不改变当前已加载层，缺文件 / 损坏 / 低内存均不抛异常、不出空白。
 */
sealed interface PyramidResult {

    /** 成功合并：[level] 级新增 [added] 颗；与 L0 / 已加载层重复的 id 不计入 [added]。 */
    data class Loaded(val level: Int, val added: Int) : PyramidResult

    /** 缓存缺文件 / 旁证不存在 / 未下载 / level 越界：保留当前层，不弹错误态。 */
    object Missing : PyramidResult

    /** 校验和不符 / 零有效行 / 解析损坏：隔离坏片，回落内置亮星层。 */
    object Corrupt : PyramidResult

    /** 合并前内存评估不足：拒绝合并该层，保持当前已加载层，不抛 OOM、不留半合并态。 */
    object LowMemory : PyramidResult
}
