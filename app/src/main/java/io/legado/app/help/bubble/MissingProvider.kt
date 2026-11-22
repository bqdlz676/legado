package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor

/**
 * Missing 兜底 Provider（优先级链最后一层）。
 *
 * 规格 §2：全空走 Missing 兜底，允许「查网络 / 加入生字本」。
 * 任何位置 / 任何字符都必须兜出一张气泡：不崩溃、不留空白态、不强行释义、不编来源。
 */
object MissingProvider : BubbleProvider {

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        val token = paragraph.getOrNull(pos)?.toString().orEmpty()
        return query(token, anchor)
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent {
        val t = token.ifBlank { "此处" }.trim()
        return BubbleContent(
            kind = BubbleKind.MISSING,
            token = t,
            phonetic = null,
            headline = t,
            body = "本地字词典未收录「$t」，可联网检索，或先收入生字本待日后复查。",
            detail = "未命中任何字典 / 人物 / 地名 / 纪年条目；此处不强行释义，避免误注。",
            actions = listOf(Action.SearchWeb(t), Action.AddToNotebook(t)),
            candidates = emptyList(),
            source = "本地词库未命中",
        )
    }
}
