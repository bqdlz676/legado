package io.legado.app.help.bubble

import io.legado.app.help.allusion.AllusionDict
import io.legado.app.help.allusion.AllusionEntry
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.dict.ChineseDict

/**
 * 词语 Provider，承担优先级链上的两个槽位：
 * - [matchAllusion]：典故阶段（第 2 层），词表命中 2~4 字；已下划线的优先命中；
 * - [matchWord]：多字词阶段（第 5 层），zh_ci 词表 2~5 字，长词优先。
 *
 * 钩子对齐规格 §6：ChineseDict.wordAt + AllusionDict.find。
 * 典故出处与词库版本一律写进 [BubbleContent.source]，不标没来路的释义。
 * 本 Provider 只读纯内存索引，不持有 Context。
 */
object WordProvider : BubbleProvider {

    /** 典故词长上限。 */
    private const val ALLUSION_MAX_LEN = 4

    /** 多字词长上限。 */
    private const val WORD_MAX_LEN = 5

    override fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null

        // 2~4 字窗口长词优先；AllusionDict 内部整词精确匹配，单字一律不命中，
        // 从机制上保证普通单字（如「抽」）不会被拆成典故。
        for (span in BubbleSupport.windows(paragraph, pos, ALLUSION_MAX_LEN)) {
            if (span.length < 2) continue
            runCatching { AllusionDict.find(span.token) }
                .getOrNull()
                ?.let { return allusionContent(it) }
        }
        return null
    }

    override fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        if (!BubbleSupport.isHan(paragraph[pos])) return null

        // 最长词优先：wordAt 内部 5→2 字窗口扫描紧凑词表。
        runCatching { ChineseDict.wordAt(paragraph, pos) }
            .getOrNull()
            ?.let { return wordContent(it.word, it.meaning, it.source) }
        return null
    }

    /** 整词查询：先典故后普通词（非段落场景候选切换回退用）。 */
    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val clean = BubbleSupport.cleanToken(token)
        if (clean.isEmpty()) return null

        runCatching { AllusionDict.find(clean) }
            .getOrNull()
            ?.let { return allusionContent(it) }

        if (clean.length in 2..WORD_MAX_LEN && clean.all { BubbleSupport.isHan(it) }) {
            runCatching { ChineseDict.wordAt(clean, clean.length / 2) }
                .getOrNull()
                ?.let { return wordContent(it.word, it.meaning, it.source) }
        }
        return null
    }

    // ---------- 组装 ----------

    private fun allusionContent(e: AllusionEntry): BubbleContent {
        return BubbleContent(
            kind = BubbleKind.ALLUSION,
            token = e.word,
            phonetic = e.phonetic?.takeIf { it.isNotBlank() },
            headline = e.headline.ifBlank { e.word },
            body = e.body,
            detail = e.detail,
            actions = emptyList(),
            candidates = emptyList(),
            source = e.source,
        )
    }

    private fun wordContent(word: String, meaning: String, source: String): BubbleContent {
        return BubbleContent(
            kind = BubbleKind.WORD,
            token = word,
            phonetic = null,
            headline = word,
            body = meaning.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            detail = meaning,
            actions = emptyList(),
            candidates = emptyList(),
            source = source,
        )
    }
}
