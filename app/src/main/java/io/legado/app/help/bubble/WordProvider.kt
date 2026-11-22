package io.legado.app.help.bubble

import android.content.Context
import io.legado.app.help.AllusionDict
import io.legado.app.help.ChineseDict
import io.legado.app.help.EnDict
import io.legado.app.help.chrono.ContextAnchor

/**
 * 词语 Provider，承担优先级链上的两个槽位：
 * - [matchAllusion]：典故阶段（第 2 层），词表命中 2~4 字；已下划线的优先命中；
 * - [matchWord]：多字词阶段（第 5 层），zh_ci 词表 2~5 字，长词优先；英语单词走 EnDict
 *   词形还原后同样出气泡（规格：英语词也出气泡）。
 *
 * 钩子对齐规格 §6：ChineseDict.wordAt + AllusionDict.find；英文走 EnDict。
 * 典故出处与词库版本一律写进 [BubbleContent.source]，不标没来路的释义。
 */
object WordProvider : BubbleProvider {

    /** 典故词长上限。 */
    private const val ALLUSION_MAX_LEN = 4

    /** 多字词长上限。 */
    private const val WORD_MAX_LEN = 5

    /** 预热词表 / 典故表 / 英汉词典（App 启动协程调用，失败不阻启动）。 */
    fun warm(context: Context) {
        runCatching { ChineseDict.preload(context) }
        runCatching { AllusionDict.preload(context) }
        runCatching { EnDict.preload(context) }
    }

    override fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        val ctx = BubbleEnv.ctx() ?: return null

        // 已下划线 / 整行扫描命中的典故优先：AllusionDict 行缓存 O(1) 取命中段。
        runCatching { AllusionDict.hitAt(ctx, paragraph, pos) }
            .getOrNull()
            ?.let { return allusionContent(it.entry) }

        // 退化：点击处 2~4 字窗口长词优先整词查表。
        for (span in BubbleSupport.windows(paragraph, pos, ALLUSION_MAX_LEN)) {
            if (span.length < 2) continue
            runCatching { AllusionDict.find(ctx, span.token) }
                .getOrNull()
                ?.let { return allusionContent(it) }
        }
        return null
    }

    override fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        val ctx = BubbleEnv.ctx() ?: return null
        val c = paragraph[pos]

        if (BubbleSupport.isLatin(c)) {
            val span = BubbleSupport.extractToken(paragraph, pos)
            if (span.token.isNotEmpty()) {
                runCatching { EnDict.lookup(ctx, span.token) }
                    .getOrNull()
                    ?.let { return englishContent(it) }
            }
            return null
        }

        // 最长词优先：wordAt 内部 5→2 字窗口扫描紧凑词表，O(log n) 二分。
        runCatching { ChineseDict.wordAt(ctx, paragraph, pos) }
            .getOrNull()
            ?.let { return chineseWordContent(ctx, it.word, it.meaning) }
        return null
    }

    /** 整词查询：先典故后普通词 / 英语词（非段落场景回退用）。 */
    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val ctx = BubbleEnv.ctx() ?: return null
        val clean = BubbleSupport.cleanToken(token)
        if (clean.isEmpty()) return null

        if (clean.any { BubbleSupport.isLatin(it) }) {
            return runCatching { EnDict.lookup(ctx, clean) }
                .getOrNull()
                ?.let { englishContent(it) }
        }

        runCatching { AllusionDict.find(ctx, clean) }
            .getOrNull()
            ?.let { return allusionContent(it) }

        if (clean.length in 2..WORD_MAX_LEN) {
            runCatching { ChineseDict.wordAt(ctx, clean, clean.length / 2) }
                .getOrNull()
                ?.let { return chineseWordContent(ctx, it.word, it.meaning) }
        }
        return null
    }

    // ---------- 组装 ----------

    private fun allusionContent(e: AllusionDict.Entry): BubbleContent {
        val meaning = e.meaning.trim()
        return BubbleContent(
            kind = BubbleKind.ALLUSION,
            token = e.word,
            phonetic = e.pinyin.trim().ifBlank { null },
            headline = e.word,
            body = meaning.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            detail = buildString {
                if (meaning.isNotBlank()) append(meaning)
                if (e.source.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("出处：").append(e.source)
                }
            },
            actions = emptyList(),
            candidates = emptyList(),
            source = e.source.ifBlank { "典故词表" },
        )
    }

    private fun chineseWordContent(ctx: Context, word: String, meaning: String): BubbleContent {
        val pinyin = runCatching { ChineseDict.pinyinOfWord(ctx, word) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return BubbleContent(
            kind = BubbleKind.WORD,
            token = word,
            phonetic = pinyin,
            headline = word,
            body = meaning.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            detail = meaning,
            actions = emptyList(),
            candidates = emptyList(),
            source = "zh_ci 词表（词级注音 CC-CEDICT）",
        )
    }

    private fun englishContent(e: EnDict.Entry): BubbleContent {
        return BubbleContent(
            kind = BubbleKind.WORD,
            token = e.word,
            phonetic = e.phonetic.trim().ifBlank { null },
            headline = e.word,
            body = e.translation.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            detail = e.translation,
            actions = emptyList(),
            candidates = emptyList(),
            source = "离线英汉词典",
        )
    }
}
