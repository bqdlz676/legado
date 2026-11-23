package io.legado.app.help.allusion

/**
 * 模块 D4 · 典故条目数据契约（冻结，下游 C2 气泡层按此对接）。
 *
 * 对齐规格切片-02 §3「各类型字段」典故行：
 * - phonetic 拼音；headline 典故词；body 一句话释义；detail 全文 + 出处；action 典故行无动作。
 *
 * 本层只产出纯数据：不持有 Android Context、不碰气泡 UI、不填 kind（kind 恒为「典故」，
 * 由下游 Provider 在组装 BubbleContent 时填）。
 *
 * 数据口径（§升格段「数据可信 = 命」）：每条必须带 [source] 出处与版本标注；
 * 典籍只取公版影印 / 公版校本，不采中华书局点校版等现代点校本的排印成果；
 * 白话释义与本事叙述单独标明，不冒充古人原文；Wikipedia / CC-CEDICT 等 copyleft 源
 * 只可交叉验证，不直接灌入本表。
 *
 * @property id        稳定主键；内置种子记录用 `seed:…` 前缀，后续外部资产注册另定前缀
 * @property word      典故词形，2~4 字；条目长度约束在注册 / 种子校验时强制，不放宽
 * @property phonetic  拼音（带声调）；无确考读音才允许为 null，种子条目必须给音
 * @property headline  一行摘要，按规格典故行即典故词本身
 * @property body      气泡内一句话释义
 * @property detail    进半屏 sheet 的完整内容：本事叙述 + 典籍原文引句 + 出处
 * @property source    出处标注（含书名篇卷与版本口径），绝不允许空出处
 */
data class AllusionEntry(
    val id: String,
    val word: String,
    val phonetic: String?,
    val headline: String,
    val body: String,
    val detail: String,
    val source: String,
)
