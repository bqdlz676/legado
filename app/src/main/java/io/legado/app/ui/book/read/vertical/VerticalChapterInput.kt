package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 竖排引擎 —— Legado 章节结果映射的纯 JVM 模型。
 *
 * 为什么单独收口：[TextChapter] 是 App 重类（构造依赖 BookChapter / 协程 / 页面列表），
 * 无法在纯 JVM 单测内实例化；标题空白归一、正文透传这些与 Android 无关的规则抽到本文件，
 * 适配器扩展函数只做“取值进来 → 交给本层”的薄绑定，归一规则全部可在 JVM 单测里覆盖。
 */
data class VerticalChapterInput(
    /** 章节标题；去空白后为空则为 null，走引擎标题通道（最右列、字号更大）。 */
    val title: String?,
    /** 章节正文文本流，作为 [VerticalLayoutEngine.layout] 的排版输入。 */
    val bodyText: String,
)

/**
 * 章节适配纯逻辑：只做标题空白归一与正文透传，不触碰任何 Android 类。
 *
 * 规则口径（与原 Legado 扩展行为逐字对齐）：
 * - 标题为 null 或全空白 → null；非空白标题原样保留（不 trim，段内空白由分页器在发射字形时跳过）。
 * - 正文原样透传，空串/全空白也交给引擎兜底，不在此处截断。
 */
object VerticalChapterMapper {

    fun map(title: String?, bodyText: String): VerticalChapterInput =
        VerticalChapterInput(
            title = title?.takeIf { it.isNotBlank() },
            bodyText = bodyText,
        )
}
