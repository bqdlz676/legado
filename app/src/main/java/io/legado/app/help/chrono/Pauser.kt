package io.legado.app.help.chrono

/**
 * 断句层（TTS 停顿，契约 §四）。
 *
 * 优先级（高 → 低）：
 *   典故词前后 200ms > 人名后 200ms > 地名前后 150ms > 虚词前 100ms
 *   > 标点（句/问/叹 400ms，逗号及次级句读 200ms）
 *
 * 同一位置叠加多条规则时：停顿毫秒取最大值；毫秒相同按上述优先级定标签。
 * 典故 / 人名 / 地名的区间由上游识别结果（AllusionDict / NameResolver / PlaceTimeDict）
 * 以 [Mark] 传入；本层只做纯 Kotlin 计算，不持 Android 依赖，可直接单测。
 */
object Pauser {

    enum class MarkKind { ALLUSION, PERSON, PLACE }

    /** 一段被识别出的专名/典故区间，[start] 含、[end] 不含 */
    data class Mark(val start: Int, val end: Int, val kind: MarkKind)

    /** 一个停顿边界：[at] 为文本中停顿落点下标，[ms] 停顿时长，[reason] 标签 */
    data class Boundary(val at: Int, val ms: Int, val reason: String, val rank: Int)

    // —— 契约锁定的毫秒值，不可改 ——
    const val ALLUSION_MS = 200
    const val PERSON_MS = 200
    const val PLACE_MS = 150
    const val PARTICLE_MS = 100
    const val SENTENCE_MS = 400
    const val COMMA_MS = 200

    /** 优先级序号：越小越优先（同毫秒时定标签用） */
    private const val RANK_ALLUSION = 0
    private const val RANK_PERSON = 1
    private const val RANK_PLACE = 2
    private const val RANK_PARTICLE = 3
    private const val RANK_PUNCT = 4

    /** 虚词（契约 §四 列字） */
    val PARTICLES: Set<Char> = setOf('于', '之', '而', '以', '则', '者', '也', '乎', '哉', '焉', '矣')

    /** 句 / 问 / 叹 */
    private val SENTENCE_PUNCT: Set<Char> = setOf('。', '！', '？', '!', '?', '…')

    /** 逗号及次级句读 */
    private val COMMA_PUNCT: Set<Char> = setOf('，', ',', '；', ';', '、', '：', ':')

    /**
     * 计算全文停顿边界。
     * @param marks 上游识别出的典故 / 人名 / 地名区间
     * @return 按文本位置升序的边界列表
     */
    fun analyze(text: String, marks: List<Mark> = emptyList()): List<Boundary> {
        // at → 候选 (rank, ms, reason)
        val boundaries = HashMap<Int, MutableList<Triple<Int, Int, String>>>()

        fun add(at: Int, ms: Int, rank: Int, reason: String) {
            if (at < 0) return
            boundaries.getOrPut(at) { ArrayList(2) }.add(Triple(rank, ms, reason))
        }

        marks.forEach { m ->
            if (m.start < 0 || m.end > text.length || m.end <= m.start) return@forEach
            when (m.kind) {
                MarkKind.ALLUSION -> {
                    add(m.start, ALLUSION_MS, RANK_ALLUSION, "典故前")
                    add(m.end, ALLUSION_MS, RANK_ALLUSION, "典故后")
                }
                MarkKind.PERSON -> add(m.end, PERSON_MS, RANK_PERSON, "人名后")
                MarkKind.PLACE -> {
                    add(m.start, PLACE_MS, RANK_PLACE, "地名前")
                    add(m.end, PLACE_MS, RANK_PLACE, "地名后")
                }
            }
        }

        text.forEachIndexed { i, c ->
            when {
                c in PARTICLES -> add(i, PARTICLE_MS, RANK_PARTICLE, "虚词前")
                c in SENTENCE_PUNCT -> add(i + 1, SENTENCE_MS, RANK_PUNCT, "句末")
                c in COMMA_PUNCT -> add(i + 1, COMMA_MS, RANK_PUNCT, "句读")
            }
        }

        return boundaries.map { (at, opts) ->
            // 毫秒大者胜；同毫秒按优先级（rank 小者）胜
            val win = opts.sortedWith(
                compareByDescending<Triple<Int, Int, String>> { it.second }
                    .thenBy { it.first }
            ).first()
            Boundary(at, win.second, win.third, win.first)
        }.sortedBy { it.at }
    }

    /** 按停顿边界把正文切成段，每段带段尾停顿毫秒（供 TTS 逐段播） */
    fun segment(text: String, marks: List<Mark> = emptyList()): List<Pair<String, Int>> {
        val bounds = analyze(text, marks)
        if (bounds.isEmpty()) return listOf(text to 0)
        val out = ArrayList<Pair<String, Int>>(bounds.size + 1)
        var cursor = 0
        bounds.forEach { b ->
            val end = b.at.coerceIn(cursor, text.length)
            out.add(text.substring(cursor, end) to b.ms)
            cursor = end
        }
        if (cursor < text.length) out.add(text.substring(cursor) to 0)
        return out
    }
}
