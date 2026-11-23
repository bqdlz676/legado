package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.dict.ChineseDict

/**
 * 单字 Provider（优先级链第 6 层，最后一个字典命中层）。
 *
 * 钩子对齐规格 §6：ChineseDict.getChar / getShuowen / getUsage。
 * - 命中后填 kind=字，phonetic=拼音，headline=字本身，
 *   body=部首+笔画+义项 1，detail=全义项 + 说文 + 古籍用例；
 * - 生僻字字库无载时返回 null，交 Missing 兜底，不猜义、不编说文；
 * - 本 Provider 只读 D1 纯内存索引，不持有 Context，宿主 JVM 可直接单测。
 */
object CharProvider : BubbleProvider {

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        val ch = paragraph[pos]
        if (!BubbleSupport.isHan(ch)) return null
        return query(ch.toString(), anchor)
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val ch = token.firstOrNull() ?: return null
        if (!BubbleSupport.isHan(ch)) return null

        val entry = runCatching { ChineseDict.getChar(ch.toString()) }.getOrNull()
        val shuowen = runCatching { ChineseDict.getShuowen(ch.toString()) }.getOrNull()
        val usage = runCatching { ChineseDict.getUsage(ch.toString()) }.getOrNull().orEmpty()

        if (entry == null && shuowen == null && usage.isEmpty()) return null

        val senses = entry?.senses.orEmpty()
        val firstSense = senses.firstOrNull().orEmpty()

        val bodyParts = ArrayList<String>(4)
        entry?.radical?.takeIf { it.isNotBlank() }?.let { bodyParts += "部首 $it" }
        entry?.strokes?.let { bodyParts += "${it}画" }
        firstSense.takeIf { it.isNotBlank() }?.let { bodyParts += it }

        val detailParts = ArrayList<String>(6)
        if (senses.isNotEmpty()) detailParts += "【义项】\n" + senses.joinToString("\n")
        if (shuowen != null) detailParts += "【说文】$shuowen"
        if (usage.isNotEmpty()) detailParts += "【用例】\n" + usage.joinToString("\n")
        if (detailParts.isEmpty()) detailParts += "字库未收释义，仅存字头。"

        val sources = ArrayList<String>(2)
        entry?.source?.takeIf { it.isNotBlank() }?.let { sources += it }
        if (shuowen != null) sources += "《说文解字》大徐本（宋·徐铉校定，公版影印本；非现代点校本）"
        if (sources.isEmpty()) sources += "本地字库"

        return BubbleContent(
            kind = BubbleKind.CHAR,
            token = ch.toString(),
            phonetic = entry?.pinyin?.takeIf { it.isNotBlank() },
            headline = ch.toString(),
            body = bodyParts.joinToString("　"),
            detail = detailParts.joinToString("\n\n"),
            actions = emptyList(),
            candidates = emptyList(),
            source = sources.joinToString("；"),
        )
    }
}
