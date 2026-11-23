package io.legado.app.ui.main.search

/**
 * 落文 · 搜索高亮切分（纯 JVM，可单测）
 *
 * 切片-13：匹配文字片段用朱砂高亮。本件只把原文切成「命中 / 未命中」片段序列，
 * 着色由 Compose 条目按 [MatchSegment.highlighted] 取 [SearchTokens.TEXT_ACCENT] 完成；
 * 不依赖 Android 字符串工具，CJK 按子串直接匹配，大小写不敏感。
 */
object SearchHighlight {

    /**
     * 把 [text] 按 [query] 切分成高亮片段。
     *
     * 兜底规则（不允许抛异常 / 留空）：
     * - [text] 为空 → 返回空列表；
     * - [query] 归一化后为空 → 整段普通墨色；
     * - 查询词长于原文 / 无命中 → 整段普通墨色；
     * - 多处命中依次切分，相邻同色片段合并，避免碎段。
     */
    fun split(text: String, query: String): List<MatchSegment> {
        if (text.isEmpty()) return emptyList()
        val needle = query.trim()
        if (needle.isEmpty()) return listOf(MatchSegment(text, false))

        val lowerHay = text.lowercase()
        val lowerNeedle = needle.lowercase()
        if (lowerNeedle.length > lowerHay.length) {
            return listOf(MatchSegment(text, false))
        }

        val raw = mutableListOf<MatchSegment>()
        var cursor = 0
        while (cursor < text.length) {
            val hit = lowerHay.indexOf(lowerNeedle, cursor)
            if (hit < 0) {
                raw += MatchSegment(text.substring(cursor), false)
                break
            }
            if (hit > cursor) {
                raw += MatchSegment(text.substring(cursor, hit), false)
            }
            raw += MatchSegment(
                text.substring(hit, hit + needle.length),
                true,
            )
            cursor = hit + needle.length
        }
        return mergeAdjacent(raw)
    }

    /** 相邻同色片段合并，减少 Compose 逐条拼装的碎片。 */
    private fun mergeAdjacent(segments: List<MatchSegment>): List<MatchSegment> {
        if (segments.size <= 1) return segments
        val out = mutableListOf<MatchSegment>()
        for (seg in segments) {
            val last = out.lastOrNull()
            if (last != null && last.highlighted == seg.highlighted) {
                out[out.lastIndex] = last.copy(text = last.text + seg.text)
            } else {
                out += seg
            }
        }
        return out
    }
}
