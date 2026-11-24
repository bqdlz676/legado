package io.legado.app.ui.book.read.vertical

import io.legado.app.ui.book.read.page.entities.TextChapter

/**
 * Legado 分章结果 → 分页引擎（竖排模式）输入的适配器（只读映射，不修改 legado 分页引擎任何类）。
 *
 * 复用边界：只取 Legado 的文本解析/分章结果（标题 + 正文文本流），
 * 横排的分页、列模型、绘制、翻页一律不复用。
 *
 * 测试边界（交接清单 #4）：本文件两个扩展函数直接引用 [TextChapter]（App 重类，
 * 构造依赖 BookChapter / 协程 / 页面列表），**无法在纯 JVM 单测内实例化，绑定路径【未验证】**；
 * 标题/正文归一的纯逻辑已抽到 [VerticalChapterMapper]，由 JVM 单测覆盖；
 * 本扩展与 [TextChapter] 字段的咬合待云端 CI / 真机接入时复验。
 */

/** 章节标题（去空白后可空），走分页引擎（竖排模式）标题通道：最右列、字号更大。 */
fun TextChapter.verticalTitle(): String? = VerticalChapterMapper.map(title, "").title

/** 章节正文文本流，作为 [VerticalLayoutEngine.layout] 的排版输入。 */
fun TextChapter.verticalBodyText(): String =
    VerticalChapterMapper.map(null, getContent()).bodyText
