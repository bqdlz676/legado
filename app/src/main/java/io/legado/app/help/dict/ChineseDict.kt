package io.legado.app.help.dict

/**
 * 模块 D1 · 字典数据 Provider（纯 Kotlin 离线数据层，不依赖 Android Context）。
 *
 * 对齐规格切片-02 §6 Provider 接口清单（对外签名冻结）：
 * - [getChar] 字义 + 部首 + 笔画 + 义项（CharProvider）
 * - [getShuowen] 《说文解字》大徐本释文（CharProvider）
 * - [getUsage] 古籍用例（CharProvider）
 * - [wordAt] 2~5 字长词优先命中（WordProvider）
 *
 * 数据口径（§升格做：数据可信 = 命）：
 * - 每条条目都带出处；说文仅录大徐本公版可确考者，存疑不录；
 * - 用例全部出自公版先秦两汉典籍并标注篇名；
 * - 种子规模先跑通（数百条），不灌无来源的海量数据。
 *
 * 本类从头编写；help/bubble/ 旧 Provider 仅作参考，未复用其代码。
 */
object ChineseDict {

    // ------------------------------------------------------------------
    // 内部种子结构
    // ------------------------------------------------------------------

    private data class CharSeed(
        val entry: CharEntry,
        val shuowen: String?,
        val usage: List<String>,
    )

    private data class WordSeed(
        val meaning: String,
        val source: String,
    )

    /** 供单测 / bench 读取的规模计数。 */
    data class Counts(val charCount: Int, val wordCount: Int)

    @Volatile
    private var charIndex: Map<String, CharSeed>? = null

    @Volatile
    private var wordIndex: Map<String, WordSeed>? = null

    // ------------------------------------------------------------------
    // 对外冻结 API
    // ------------------------------------------------------------------

    /** 单字条目；字库未收返回 null，不猜义、不编说文。 */
    fun getChar(ch: Char): CharEntry? = ensureChar()[ch.toString()]

    /** 单字条目（String 入参；长度非 1 返回 null）。 */
    fun getChar(ch: String): CharEntry? =
        if (ch.length == 1) getChar(ch[0]) else null

    /** 《说文解字》大徐本释文；无载返回 null。 */
    fun getShuowen(ch: Char): String? = ensureChar()[ch.toString()]?.shuowen

    /** 《说文解字》大徐本释文（String 入参）。 */
    fun getShuowen(ch: String): String? =
        if (ch.length == 1) getShuowen(ch[0]) else null

    /** 古籍用例（《出处》例句）；无载返回空列表，永不返回 null。 */
    fun getUsage(ch: Char): List<String> =
        ensureChar()[ch.toString()]?.usage.orEmpty()

    /** 古籍用例（String 入参）。 */
    fun getUsage(ch: String): List<String> =
        if (ch.length == 1) getUsage(ch[0]) else emptyList()

    /**
     * 点击位置的多字词命中：2~5 字，长词优先；同长度取最靠左起点。
     * 窗口只在连续汉字串内滑动，遇标点 / 拉丁 / 数字截断；未命中返回 null。
     */
    fun wordAt(text: String, pos: Int): WordHit? {
        if (text.isEmpty() || pos !in text.indices) return null
        if (!isHan(text[pos])) return null

        val index = ensureWord()

        // 定位 pos 所在的连续汉字串。
        var from = pos
        var to = pos
        while (from > 0 && isHan(text[from - 1])) from--
        while (to < text.length - 1 && isHan(text[to + 1])) to++
        if (to - from + 1 < 2) return null

        val maxLen = minOf(MAX_WORD_LEN, to - from + 1)
        for (len in maxLen downTo MIN_WORD_LEN) {
            val startMin = maxOf(from, pos - len + 1)
            val startMax = minOf(pos, to - len + 1)
            if (startMax < startMin) continue
            for (start in startMin..startMax) {
                val candidate = text.substring(start, start + len)
                val seed = index[candidate] ?: continue
                return WordHit(
                    word = candidate,
                    start = start,
                    end = start + len,
                    meaning = seed.meaning,
                    source = seed.source,
                )
            }
        }
        return null
    }

    /** 预热全部种子表（App 启动协程调用；失败不阻启动）。 */
    fun preload() {
        ensureChar()
        ensureWord()
    }

    /** 种子规模计数（单测 / bench 用）。 */
    fun counts(): Counts = Counts(ensureChar().size, ensureWord().size)

    // ------------------------------------------------------------------
    // 懒加载（双重检查锁；纯内存解析，离线可用）
    // ------------------------------------------------------------------

    private const val MIN_WORD_LEN = 2
    private const val MAX_WORD_LEN = 5

    private fun ensureChar(): Map<String, CharSeed> =
        charIndex ?: synchronized(this) {
            charIndex ?: parseChars().also { charIndex = it }
        }

    private fun ensureWord(): Map<String, WordSeed> =
        wordIndex ?: synchronized(this) {
            wordIndex ?: parseWords().also { wordIndex = it }
        }

    private fun parseChars(): Map<String, CharSeed> {
        val out = HashMap<String, CharSeed>(256)
        DictSeedData.CHAR_TSV.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val p = line.split('\t')
                if (p.isEmpty() || p[0].length != 1) return@forEach
                val char = p[0]
                val pinyin = p.getOrElse(1) { "" }.ifBlank { null }
                val radical = p.getOrElse(2) { "" }.ifBlank { null }
                val strokes = p.getOrElse(3) { "" }.trim().toIntOrNull()
                val senses = splitList(p.getOrElse(4) { "" })
                val shuowen = p.getOrElse(5) { "" }.ifBlank { null }
                val usage = splitList(p.getOrElse(6) { "" })
                val tag = p.getOrElse(7) { "" }.trim()

                val source = DictSource.join(
                    if (shuowen != null) DictSource.SHUOWEN_DAXU else null,
                    if (tag == "kangxi") DictSource.KANGXI else null,
                    DictSource.LOCAL_GLOSS,
                )

                val entry = CharEntry(
                    char = char,
                    pinyin = pinyin,
                    radical = radical,
                    strokes = strokes,
                    senses = senses,
                    source = source,
                )
                out[char] = CharSeed(entry, shuowen, usage)
            }
        return out
    }

    private fun parseWords(): Map<String, WordSeed> {
        val out = HashMap<String, WordSeed>(256)
        DictSeedData.WORD_TSV.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val p = line.split('\t')
                if (p.size < 2) return@forEach
                val word = p[0].trim()
                val meaning = p[1].trim()
                val tag = p.getOrElse(2) { "" }.trim()
                if (word.length !in MIN_WORD_LEN..MAX_WORD_LEN) return@forEach
                if (word.any { !isHan(it) }) return@forEach
                if (meaning.isBlank()) return@forEach
                val source = if (tag == "classic") DictSource.CLASSIC_WORD else DictSource.COMMON_WORD
                out[word] = WordSeed(meaning, source)
            }
        return out
    }

    private fun splitList(raw: String): List<String> =
        raw.split('；').map { it.trim() }.filter { it.isNotEmpty() }

    private fun isHan(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF

    // ------------------------------------------------------------------
    // 测试专用钩子：清空内存索引，模拟冷启动首触路径。
    // 生产代码不调用；仅 bench 冷路径与单测重置使用。
    // ------------------------------------------------------------------
    internal fun resetForBench() {
        synchronized(this) {
            charIndex = null
            wordIndex = null
        }
    }
}
