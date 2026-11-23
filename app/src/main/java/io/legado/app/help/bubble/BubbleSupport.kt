package io.legado.app.help.bubble

/**
 * 气泡层共用的文本小工具：字符判定、词窗切分、纪年 / 干支 / 朔望形状识别。
 *
 * 这里只做「形状识别与切词」，不做任何字典换算；换算结果由各 Provider 接数据表后填入。
 * 所有函数纯输入输出，可在宿主 JVM 单测中直接验收。
 */
internal object BubbleSupport {

    // ---------- 字符判定 ----------

    /** 汉字（含扩展 A 区常用范围）。 */
    fun isHan(c: Char): Boolean = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF

    /** 拉丁字母（英语词气泡用，规格：英语词也出气泡）。 */
    fun isLatin(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    /** 数字 / 阿拉伯数字纪年片段。 */
    fun isDigit(c: Char): Boolean = c in '0'..'9'

    /** 去掉常见标点 / 空白后的可查片段，保留汉字、拉丁字母、数字、撇号、连字符。 */
    fun cleanToken(raw: String): String =
        raw.trim().filter { isHan(it) || isLatin(it) || isDigit(it) || it == '\'' || it == '-' }

    // ---------- 词窗切分 ----------

    /** 点击处所在的连续片段：拉丁词整段取走；汉字取连续汉字串；其余退回单字。 */
    fun extractToken(paragraph: String, pos: Int): TokenSpan {
        if (paragraph.isEmpty() || pos !in paragraph.indices) return TokenSpan("", -1, -1)
        val c = paragraph[pos]
        when {
            isLatin(c) -> {
                var from = pos
                var to = pos
                while (from > 0 && (isLatin(paragraph[from - 1]) || paragraph[from - 1] == '\'')) from--
                while (to < paragraph.length - 1 && (isLatin(paragraph[to + 1]) || paragraph[to + 1] == '\'')) to++
                return TokenSpan(paragraph.substring(from, to + 1), from, to)
            }
            isHan(c) -> {
                var from = pos
                var to = pos
                while (from > 0 && isHan(paragraph[from - 1])) from--
                while (to < paragraph.length - 1 && isHan(paragraph[to + 1])) to++
                return TokenSpan(paragraph.substring(from, to + 1), from, to)
            }
            else -> return TokenSpan(c.toString(), pos, pos)
        }
    }

    /**
     * 以 [pos] 为中心、长度从 [maxLen] 降到 1 的候选词窗，长词优先（规格 §2：同优先级取更长词）。
     * 每个窗口都必须覆盖 [pos]；越界自动收窄。返回按长度降序、起点靠左稳定排序。
     */
    fun windows(paragraph: String, pos: Int, maxLen: Int): List<TokenSpan> {
        if (paragraph.isEmpty() || pos !in paragraph.indices) return emptyList()
        val result = LinkedHashSet<TokenSpan>()
        for (len in maxLen.coerceAtMost(paragraph.length) downTo 1) {
            val startMax = minOf(pos, paragraph.length - len)
            val startMin = maxOf(0, pos - len + 1)
            for (start in startMin..startMax) {
                val end = start + len - 1
                if (pos in start..end) {
                    result += TokenSpan(paragraph.substring(start, end + 1), start, end)
                }
            }
        }
        return result.toList()
    }

    // ---------- 纪年 / 干支 / 朔望形状识别（只判形状，不换算） ----------

    private val GANZHI = Regex("[甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥]")

    private val PHASE_CHARS = setOf('朔', '望', '晦')

    /** 年号 / 王公纪年：2~5 个汉字前缀 +「元 / 一~十 / 数字」年，如「鲁隐公元年」「贞观三年」。 */
    private val ERA_MENTION = Regex("[一-龥]{2,5}?(元|[一二三四五六七八九十]{1,3}|[0-9]{1,3})年")

    /** 点击处是否落在干支串上；返回命中的干支片段，未命中返回 null。 */
    fun ganzhiAt(paragraph: String, pos: Int): String? {
        GANZHI.findAll(paragraph).forEach { m ->
            if (pos in m.range) return m.value
        }
        return null
    }

    /** 点击处是否为朔 / 望 / 晦纪日字。 */
    fun phaseAt(paragraph: String, pos: Int): String? {
        if (pos in paragraph.indices && paragraph[pos] in PHASE_CHARS) return paragraph[pos].toString()
        return null
    }

    /** 点击处是否落在年号 / 王公纪年片段上；返回命中片段，未命中返回 null。 */
    fun eraMentionAt(paragraph: String, pos: Int): String? {
        ERA_MENTION.findAll(paragraph).forEach { m ->
            if (pos in m.range) return m.value
        }
        return null
    }

    /** 公元年 → 展示用年标：负数为公元前（内部 -722 即前 722），非负为公元年。 */
    fun yearText(y: Int): String = if (y <= 0) "前${-y}" else "公元$y"

    /** 生卒年对 → 展示串；任一年无载如实标注，不补约数。 */
    fun lifeSpanText(birth: Int?, death: Int?): String {
        if (birth == null && death == null) return "生卒无载"
        val b = birth?.let { yearText(it) } ?: "生年无载"
        val d = death?.let { yearText(it) } ?: "卒年无载"
        return "$b — $d"
    }

    /** 一个可查片段（token 与其在段落中的起止下标，闭区间）。 */
    data class TokenSpan(val token: String, val start: Int, val end: Int) {
        val length: Int get() = token.length
    }
}
