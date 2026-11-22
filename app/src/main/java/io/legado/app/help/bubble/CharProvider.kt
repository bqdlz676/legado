package io.legado.app.help.bubble

import android.content.Context
import io.legado.app.help.ChineseDict
import io.legado.app.help.HanConv
import io.legado.app.help.chrono.ContextAnchor

/**
 * 单字 Provider（优先级链第 6 层，最后一个字典命中层）。
 *
 * 钩子对齐规格 §6：ChineseDict.getChar / getShuowen / getUsage。
 * - 命中后填 kind=字，phonetic=拼音，headline=字本身，
 *   body=部首+笔画+义项 1，detail=全义项 + 说文 + 古籍用例；
 * - 异体 / 繁体字：气泡内补今体字（〔简〕行）；一字多音时列出通假 / 异读读音；
 * - 通假字取通假义项，不取本字义项（读音层先于字库首音）；
 * - 生僻字字库无载时返回 null，交 Missing 兜底，不猜义、不编说文。
 */
object CharProvider : BubbleProvider {

    /** 预热字库 / 说文 / 用例三表（App 启动协程调用，失败不阻启动）。 */
    fun warm(context: Context) {
        runCatching { ChineseDict.preload(context) }
    }

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        val ch = paragraph[pos]
        if (!BubbleSupport.isHan(ch)) return null
        return query(ch.toString(), anchor)
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val ctx = BubbleEnv.ctx() ?: return null
        val ch = token.firstOrNull() ?: return null
        if (!BubbleSupport.isHan(ch)) return null

        val info = runCatching { ChineseDict.getChar(ctx, ch.toString()) }.getOrNull()
        val shuowen = runCatching { ChineseDict.getShuowen(ctx, ch.toString()) }.getOrNull()
        val usage = runCatching { ChineseDict.getUsage(ctx, ch.toString()) }.getOrNull()
        val readings = runCatching { ChineseDict.getPinyin(ctx, ch.toString()) }.getOrNull().orEmpty()

        if (info == null && shuowen == null && usage == null) return null

        HanConv.ensureLoaded(ctx)
        val simpLine = HanConv.simpLine(ch.toString())

        val pinyin = info?.pinyin?.takeIf { it.isNotBlank() }
            ?: readings.firstOrNull()?.takeIf { it.isNotBlank() }

        val meaning = info?.meaning.orEmpty()
        val firstSense = meaning.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

        val bodyParts = ArrayList<String>(4)
        val meta = listOf(
            info?.radical?.takeIf { it.isNotBlank() }?.let { "部首 $it" },
            info?.strokes?.takeIf { it.isNotBlank() }?.let { "${it}画" },
            firstSense.takeIf { it.isNotBlank() },
            simpLine.takeIf { it.isNotBlank() },
        ).filterNotNull()
        bodyParts += meta

        val detailParts = ArrayList<String>(6)
        if (meaning.isNotBlank()) detailParts += "【义项】\n$meaning"
        if (shuowen != null) {
            val sw = buildString {
                append("【说文】").append(shuowen.text)
                if (shuowen.fanqie.isNotBlank()) append("　反切 ").append(shuowen.fanqie)
            }
            detailParts += sw
        }
        if (readings.size > 1) {
            detailParts += "【异读】" + readings.joinToString("、")
        }
        if (usage != null) {
            detailParts += "【用例】${usage.source}：${usage.sentence}"
        }
        if (detailParts.isEmpty()) detailParts += "字库未收释义，仅存字头。"

        val sources = ArrayList<String>(2)
        if (info != null) sources += "汉字字典（公版校本）"
        if (shuowen != null) sources += "《说文解字》"
        if (usage != null) sources += usage.source
        if (sources.isEmpty()) sources += "本地字库"

        return BubbleContent(
            kind = BubbleKind.CHAR,
            token = ch.toString(),
            phonetic = pinyin,
            headline = ch.toString(),
            body = bodyParts.joinToString("　"),
            detail = detailParts.joinToString("\n\n"),
            actions = emptyList(),
            candidates = emptyList(),
            source = sources.joinToString("；"),
        )
    }
}
