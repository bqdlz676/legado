package io.legado.app.help.dict

/**
 * 模块 D1 · 字典数据 Provider 对外数据契约（冻结）。
 *
 * 对齐规格切片-02 §6 Provider 接口清单：
 * - CharProvider → ChineseDict.getChar / getShuowen / getUsage
 * - WordProvider → ChineseDict.wordAt
 *
 * 本包为纯数据层：不持有 Android Context、不依赖 UI；气泡组装由 help/bubble 层完成。
 * 所有条目必须带 [source] 出处标注（规格 §升格做：数据可信 = 命）。
 */

/**
 * 单字条目。
 *
 * @property char 字头（简体/通行字形，单字）
 * @property pinyin 首读音（带声调）；一字多音的其余读音不在此条展开
 * @property radical 部首；字书未确考时为 null，不猜
 * @property strokes 笔画数；未确考时为 null，不猜
 * @property senses 义项，按常用/古义顺序排列；白话训释
 * @property source 出处标注（字书版本 + 训释来源），不得为空
 */
data class CharEntry(
    val char: String,
    val pinyin: String?,
    val radical: String?,
    val strokes: Int?,
    val senses: List<String>,
    val source: String,
)

/**
 * 多字词命中（2~5 字，长词优先）。
 *
 * @property word 命中词形
 * @property start 命中起点（含，原文下标）
 * @property end 命中终点（不含，原文下标）
 * @property meaning 词义训释
 * @property source 出处标注（词目所见典籍 / 词表版本），不得为空
 */
data class WordHit(
    val word: String,
    val start: Int,
    val end: Int,
    val meaning: String,
    val source: String,
)
