package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor

/**
 * 气泡 Provider 契约（规格切片-02 §6「Provider 接口清单」）。
 *
 * 各 Provider 只做「查字典 → 组装 [BubbleContent]」，不做优先级编排；
 * 命中与否、先后顺序由 [BubbleResolver] 统一决定。查不到返回 null，落到下一优先级。
 *
 * - [query]：按整词 / 整称谓查表，[token] 为已切出的候选串；[anchor] 为当前章节锚点
 *   （当前书 / 章 / 候选年代区间），供同名消歧与按年代选图层。
 * - [hitAt]：段落级位置命中（典故下划线、地名 / 多字词需要左右窗口才能切准）。
 *   默认回退到只取点击单字；不需要段落窗口的 Provider 可不覆写。
 * - [matchAllusion]：典故阶段与多字词阶段的独立入口，仅 [WordProvider] 覆写；
 *   Resolver 在优先级链的不同槽位分别调用。
 * - [matchWord]：多字词阶段入口，仅 [WordProvider] 覆写。
 *
 * 实现约束：
 *  - 任何一路异常都必须在 Provider 内部兜住（runCatching），不得把异常抛进 Resolver；
 *  - 查不到就返回 null，不凭字形猜身份、不编生卒 / 出处；
 *  - Context 一律从 [BubbleEnv] 取，不在接口参数里传递。
 */
interface BubbleProvider {

    /** 整词查询；未命中返回 null。 */
    fun query(token: String, anchor: ContextAnchor): BubbleContent? = null

    /** 段落位置命中；默认取点击处单字走 [query]。 */
    fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? =
        if (pos in paragraph.indices) query(paragraph[pos].toString(), anchor) else null

    /** 典故阶段（2~4 字，已下划线优先）；仅 WordProvider 覆写。 */
    fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = null

    /** 多字词阶段（2~5 字，长词优先）；仅 WordProvider 覆写。 */
    fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = null
}
