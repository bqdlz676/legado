package io.legado.app.ui.book.read.vertical

import io.legado.app.ui.book.read.page.entities.TextChapter

/**
 * Legado 分章结果 → 竖排引擎输入的适配器（只读映射，不修改横排引擎任何类）。
 *
 * 复用边界：只取 Legado 的文本解析/分章结果（标题 + 正文文本流），
 * 横排的分页、列模型、绘制、翻页一律不复用。
 */

/** 章节标题（去空白后可空），走竖排引擎标题通道：最右列、字号更大。 */
fun TextChapter.verticalTitle(): String? = title.takeIf { it.isNotBlank() }

/** 章节正文文本流，作为 [VerticalLayoutEngine.layout] 的排版输入。 */
fun TextChapter.verticalBodyText(): String = getContent()
