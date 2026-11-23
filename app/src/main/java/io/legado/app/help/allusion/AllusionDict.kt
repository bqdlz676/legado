package io.legado.app.help.allusion

/**
 * 模块 D4 · 典故词表（纯 Kotlin / 纯 JVM 离线数据层，不依赖 Android Context、不碰 UI）。
 *
 * 对齐规格切片-02 §6 Provider 接口清单：WordProvider → ChineseDict.wordAt + AllusionDict.find；
 * §3 典故行：phonetic 拼音 / headline 典故词 / body 一句话释义 / detail 全文 + 出处。
 *
 * 命中口径（验收标准）：
 * - 只按整词精确命中，不做单字拆分：普通单字（如「抽」）一律返回 null，不误弹典故；
 * - 词长约束 2~4 字，超出区间的入参直接不进索引查找；
 * - 入参两侧的引号、括号、空白先剥除，兼容气泡层带标点传词的情况。
 *
 * 数据可信（§升格段）：种子表每条在装载时强制校验出处与字段完整性；
 * 无来路、字段残缺、词长越界的条目在装载 / 注册阶段即被拒绝，不进索引。
 *
 * 本类从头编写；help/bubble/ 旧 Provider 与 help 根目录旧 AllusionDict 仅作参考，未复用。
 */
object AllusionDict {

    /** 种子条目 + 外部注册条目的合并视图。 */
    private var records: List<AllusionEntry>

    /** 词形 → 条目 精确索引；整词匹配，不做子串扫描。 */
    private var byWord: Map<String, AllusionEntry>

    init {
        val seed = AllusionSeed.entries.map { validate(it, fromSeed = true) }
        checkDuplicates(seed, existing = emptyList())
        records = seed
        byWord = seed.associateBy { it.word }
    }

    // ------------------------------------------------------------------
    // 冻结对外 API（下游 C2 气泡层按此对接，签名不得变更）
    // ------------------------------------------------------------------

    /**
     * 按词命中典故；词表未收返回 null，不凭字面猜典故。
     * 单字、空串、超过四字的入参一律返回 null。
     */
    fun find(word: String): AllusionEntry? {
        val key = normalize(word)
        if (key.length !in 2..4) return null
        return byWord[key]
    }

    // ------------------------------------------------------------------
    // 内部注册扩展点（后续接入外部公版典故资产时使用）
    // ------------------------------------------------------------------

    /**
     * 注册外部条目；逐条走与种子相同的校验，无出处 / 字段残缺 / 词长越界 / 撞词
     * 均抛 [IllegalArgumentException]，不静默丢弃也不塞假数据。
     */
    @Synchronized
    fun register(entries: List<AllusionEntry>) {
        val validated = entries.map { validate(it, fromSeed = false) }
        checkDuplicates(validated, existing = records)
        records = records + validated
        byWord = records.associateBy { it.word }
    }

    /** 供单测 / bench 读取的全部条目（含种子与外部注册）。 */
    internal fun allRecords(): List<AllusionEntry> = records

    /** 条目总数，供 bench 与规模断言使用。 */
    internal fun count(): Int = records.size

    // ------------------------------------------------------------------
    // 校验与归一化
    // ------------------------------------------------------------------

    private fun validate(entry: AllusionEntry, fromSeed: Boolean): AllusionEntry {
        require(entry.id.isNotBlank()) { "典故条目 id 不能为空" }
        require(entry.word.length in 2..4) {
            "典故词「${entry.word}」长度须为 2~4 字，实际 ${entry.word.length} 字"
        }
        require(entry.headline.isNotBlank()) { "典故「${entry.word}」headline 为空" }
        require(entry.headline == entry.word) {
            "典故「${entry.word}」headline 须等于典故词本身"
        }
        require(entry.body.isNotBlank()) { "典故「${entry.word}」一句话释义为空" }
        require(entry.detail.isNotBlank()) { "典故「${entry.word}」全文与出处为空" }
        require(entry.source.isNotBlank()) { "典故「${entry.word}」无出处标注" }
        require(entry.source.contains("《")) {
            "典故「${entry.word}」出处须含书名号《》，实际：${entry.source}"
        }
        if (fromSeed) {
            // 种子条目是本模块逐条核校的，读音必须给全；外部资产允许读音暂缺。
            require(!entry.phonetic.isNullOrBlank()) {
                "种子典故「${entry.word}」拼音缺失"
            }
        }
        return entry
    }

    private fun checkDuplicates(
        incoming: List<AllusionEntry>,
        existing: List<AllusionEntry>,
    ) {
        val seen = existing.map { it.word }.toMutableSet()
        incoming.forEach { entry ->
            require(seen.add(entry.word)) { "典故词「${entry.word}」重复注册" }
        }
    }

    /** 剥除两侧空白与引号、括号、句读；内部不做繁简转换，词形以典籍通行字为准。 */
    private fun normalize(raw: String): String {
        var s = raw.trim()
        val wrapChars = charArrayOf('「', '」', '『', '』', '（', '）', '(', ')', '"', '“', '”', '\'', '‘', '’', '，', '。', '、', '；', '：', ' ')
        var changed = true
        while (changed && s.isNotEmpty()) {
            val first = s.first()
            val last = s.last()
            if (first in wrapChars && last in wrapChars && s.length > 1) {
                s = s.substring(1, s.length - 1)
            } else if (first in wrapChars) {
                s = s.substring(1)
            } else if (last in wrapChars) {
                s = s.substring(0, s.length - 1)
            } else {
                changed = false
            }
        }
        return s
    }
}
