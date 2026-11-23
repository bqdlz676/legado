package io.legado.app.help.person.graph

import android.content.Context
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.person.PersonDirectory
import io.legado.app.help.person.PersonProfile
import io.legado.app.utils.printOnDebug

/**
 * 文本 → 人物关系图（D2 支撑版）。
 *
 * 产线沿用旧抽取器：规则给候选 → 共现边（谁与谁、几次、哪章）+ 出场段落列表；
 * 共现以「段」为单位统计，不按句，避免一句话里被切碎。
 *
 * 与旧 `help.relation.RelationExtractor` 的区别：
 *   - 档案归属 / 身份 / 生卒 / 称谓全部走 D2 `PersonProfile`，不再读旧扁平表与 CBDB txt；
 *   - 抽取词表来自 D2 在册人物的全部称呼 + assets/person_terms.txt 保底词表；
 *   - 图谱模型在本包（[Graph] / [Node] / [Edge] / [Appearance]），不依赖旧 relation 包。
 *
 * 边界说明：旧栈里「桓公 / 楚子」这类短称谓按国别 + 在位君主消歧的能力，D2 档案未建模；
 * 新实现只在 D2 能归并到具体人物时才落桶，归并不出的短称谓宁可不动，避免张冠李戴。
 */
object PersonGraphExtractor {

    /** 春秋列国，用来判定阵营，也用来拼「齐桓公」这类人名 */
    private val STATES = listOf(
        "周", "齐", "晋", "楚", "秦", "宋", "鲁", "卫", "郑", "燕", "吴", "越",
        "陈", "蔡", "曹", "滕", "薛", "许", "邾", "莒", "虞", "虢", "梁", "徐",
        "巴", "蜀", "息", "随", "纪", "邢", "谭", "遂", "邹", "戴", "沈", "胡",
        "邓", "申", "唐", "黄", "江", "舒", "庸", "赖", "萧", "钟离"
    )

    /** 常见专名：诸子、名臣、将帅。规则抓不到的靠这张表兜底 */
    private val KNOWN = listOf(
        "孔子", "孟子", "庄子", "荀子", "老子", "墨子", "管子", "晏子", "列子", "韩非",
        "孙武", "孙膑", "吴起", "商鞅", "申不害", "慎到", "邹衍", "惠施", "公孙龙", "鬼谷子",
        "苏秦", "张仪", "范雎", "蔡泽", "吕不韦", "李斯", "尉缭", "王翦", "蒙恬", "白起",
        "管仲", "鲍叔牙", "隰朋", "晏婴", "子产", "子皮", "子罕", "子大叔", "叔向", "女叔齐",
        "赵盾", "赵武", "赵鞅", "赵无恤", "狐偃", "狐毛", "先轸", "先且居", "胥臣", "介子推",
        "荀林父", "荀偃", "士会", "士燮", "栾书", "栾盈", "郤缺", "郤克", "郤至", "韩厥",
        "魏绛", "魏舒", "智伯", "智瑶", "范宣子", "中行偃", "羊舌肸", "祁奚", "解狐",
        "伍举", "伍奢", "伍子胥", "申包胥", "伯嚭", "文种", "范蠡", "专诸", "要离",
        "阖闾", "夫差", "勾践", "西施", "郑旦",
        "屈原", "宋玉", "唐勒", "景差", "黄歇", "信陵君", "平原君", "孟尝君",
        "蔺相如", "廉颇", "赵括", "赵奢", "李牧", "荆轲", "樊於期", "高渐离", "田光",
        "刘邦", "项羽", "张良", "萧何", "韩信", "陈平", "曹参", "樊哙", "英布", "彭越",
        "霍去病", "卫青", "李广", "司马迁", "司马相如", "东方朔", "董仲舒", "贾谊", "晁错",
        "曹操", "刘备", "孙权", "诸葛亮", "周瑜", "司马懿", "关羽", "张飞", "赵云", "马超",
        "黄忠", "庞统", "法正", "姜维", "陆逊", "鲁肃", "吕蒙", "甘宁", "太史慈",
        "陶渊明", "谢灵运", "谢安", "王羲之", "顾恺之", "祖冲之", "郦道元",
        "李白", "杜甫", "白居易", "韩愈", "柳宗元", "刘禹锡", "元稹", "李贺", "杜牧", "李商隐",
        "苏轼", "苏辙", "苏洵", "王安石", "欧阳修", "曾巩", "司马光", "范仲淹", "辛弃疾", "李清照"
    )

    @Volatile
    private var extTermsCache: List<String>? = null

    @Volatile
    private var termBucket: Map<Char, List<String>>? = null

    @Volatile
    private var aliasBucket: Map<Char, List<String>>? = null

    /** assets/person_terms.txt：三万条保底人名词表，只作匹配字典，不携带任何档案字段。 */
    private fun extTerms(context: Context): List<String> {
        extTermsCache?.let { return it }
        synchronized(this) {
            extTermsCache?.let { return it }
            val list = ArrayList<String>(30000)
            runCatching {
                context.assets.open("person_terms.txt").bufferedReader().use { r ->
                    while (true) {
                        val ln = r.readLine() ?: break
                        val t = ln.trim()
                        if (t.length in 2..5) list.add(t)
                    }
                }
            }.onFailure { it.printOnDebug() }
            extTermsCache = list
            return list
        }
    }

    /** 抽取词表：D2 在册人物全部称呼 + 保底词表 + 内置常见专名。 */
    private fun terms(context: Context): List<String> {
        val d2Names = PersonDirectory.allRecords().flatMap { it.allCallNames }
        return (KNOWN + d2Names + extTerms(context))
            .filter { it.length >= 2 }
            .distinct()
    }

    /** 按首字分桶，长词在前；三万词线性 indexOf 会把整页扫描拖死 */
    private fun termsByFirst(context: Context): Map<Char, List<String>> {
        termBucket?.let { return it }
        val m = terms(context).groupBy { it[0] }
            .mapValues { e -> e.value.sortedByDescending { it.length } }
        termBucket = m
        return m
    }

    /** 短称谓桶：字 / 号 / 封号 / 别称等 2..4 字称呼，按首字立桶 */
    private fun aliasBuckets(context: Context): Map<Char, List<String>> {
        aliasBucket?.let { return it }
        val names = PersonDirectory.allRecords()
            .flatMap { it.allCallNames }
            .filter { it.length in 2..4 }
        val m = names.groupBy { it[0] }
            .mapValues { e -> e.value.sortedByDescending { it.length } }
        aliasBucket = m
        return m
    }

    private val SPEECH = listOf("曰", "谓", "对", "谏", "问", "答", "云")

    private val REL_PATTERNS = listOf(
        "杀" to "敌对", "弑" to "敌对", "诛" to "敌对", "戮" to "敌对", "斩" to "敌对",
        "伐" to "征伐", "攻" to "征伐", "侵" to "征伐", "袭" to "征伐", "围" to "征伐",
        "奔" to "出奔", "逃" to "出奔", "亡" to "出奔",
        "盟" to "会盟", "会" to "会盟", "遇" to "会盟", "朝" to "朝聘", "聘" to "朝聘",
        "立" to "拥立", "纳" to "拥立", "废" to "废立",
        "使" to "遣使", "遣" to "遣使",
        "事" to "事奉", "相" to "辅佐", "佐" to "辅佐", "傅" to "辅佐",
        "子" to "亲属", "弟" to "亲属", "兄" to "亲属", "女" to "亲属",
        "妻" to "亲属", "父" to "亲属", "母" to "亲属", "孙" to "亲属",
        "友" to "交游", "善" to "交游", "怨" to "交恶", "恶" to "交恶"
    )

    /**
     * @param chapters 章节标题 to 正文
     */
    fun extract(context: Context, chapters: List<Pair<String, String>>, bookName: String): Graph {
        // 先用章标题猜一次年代，全章沿用；猜不出就留空，下游不猜
        ContextAnchor.setChapter(bookName, chapters.firstOrNull()?.first)
        chapters.firstOrNull()?.first?.let { ContextAnchor.guessFromText(it) }
        val candidates = HashMap<String, Int>(1024)
        val appearanceMap = HashMap<String, MutableList<Appearance>>(1024)
        val pairWeight = HashMap<String, Int>(8192)
        val pairLabel = HashMap<String, String>(2048)
        val pairChapters = HashMap<String, MutableSet<String>>(2048)

        for ((title, body) in chapters) {
            for (para in splitParagraphs(body)) {
                val hits = findPersonHits(context, para, ContextAnchor.get().year)
                if (hits.isEmpty()) continue
                val names = hits.map { it.first }.distinct()
                for (n in names) {
                    candidates[n] = (candidates[n] ?: 0) + 1
                    val list = appearanceMap.getOrPut(n) { ArrayList(12) }
                    if (list.size < 12) {
                        list.add(Appearance(title, para.trim().take(120)))
                    }
                }
                if (names.size < 2) continue
                // 段内两两计数（档案 615：同段人物两两计数）
                for (i in names.indices) {
                    for (j in i + 1 until names.size) {
                        val key = keyOf(names[i], names[j])
                        pairWeight[key] = (pairWeight[key] ?: 0) + 1
                        pairChapters.getOrPut(key) { LinkedHashSet(4) }.add(title)
                    }
                }
                // 关系标签：同一段里按「A…动词…B」判
                for (i in hits.indices) {
                    for (j in i + 1 until hits.size) {
                        val a = hits[i]
                        val b = hits[j]
                        if (a.first == b.first || b.second.first < a.second.first) continue
                        val between = para.substring(
                            (a.second.first + a.first.length).coerceAtMost(para.length),
                            b.second.first.coerceAtLeast(0)
                        )
                        labelOf(between)?.let {
                            pairLabel.putIfAbsent(keyOf(a.first, b.first), it)
                        }
                    }
                }
            }
        }

        val persons = candidates.entries
            .filter { it.value >= 2 && it.key.length in 2..5 }
            .sortedByDescending { it.value }
            .take(60)

        val nodes = persons.map { (name, cnt) ->
            val d2 = PersonProfile.profileOf(name)
            val span = PersonProfile.lifeSpan(name)
            Node(
                id = name,
                name = name,
                faction = d2?.nationality?.takeIf { it.isNotBlank() && it != "?" } ?: factionOf(name),
                count = cnt,
                appearances = appearanceMap[name] ?: emptyList(),
                identity = d2?.identity.orEmpty(),
                lifeSpan = span?.let { formatSpan(it.first, it.second) }.orEmpty(),
                aliases = d2?.allCallNames?.filter { it != name }.orEmpty(),
            )
        }
        val edges = pairWeight.entries
            .filter { a -> a.value >= 1 && persons.any { it.key == a.key.split('\u0001')[0] } }
            .sortedByDescending { it.value }
            .take(220)
            .map { (k, w) ->
                val parts = k.split('\u0001')
                Edge(
                    source = parts[0],
                    target = parts[1],
                    weight = w,
                    label = pairLabel[k] ?: "同现",
                    chapters = (pairChapters[k] ?: emptySet()).take(6)
                )
            }

        return Graph(nodes, edges, bookName)
    }

    private fun keyOf(a: String, b: String) = if (a < b) "$a\u0001$b" else "$b\u0001$a"

    /** 按空行、单换行切段，段过长再截 */
    private fun splitParagraphs(text: String): List<String> {
        val list = ArrayList<String>(text.length / 80 + 8)
        val clean = text.replace('\r', '\n').replace("　", " ")
        for (raw in clean.split('\n')) {
            val t = raw.trim()
            if (t.length < 6) continue
            if (t.length <= 400) {
                list.add(t)
            } else {
                var i = 0
                while (i < t.length) {
                    val end = (i + 300).coerceAtMost(t.length)
                    list.add(t.substring(i, end))
                    i = end
                }
            }
        }
        return list
    }

    private fun findPersonHits(
        context: Context,
        sentence: String,
        year: Int?,
    ): List<Pair<String, IntRange>> {
        val hits = ArrayList<Pair<String, IntRange>>(8)
        val aliasB = aliasBuckets(context)
        val anchorState = ContextAnchor.get().state

        // 国号与人名共走一遍字符位，别开两趟
        for (i in sentence.indices) {
            val sts = STATES_BY_FIRST[sentence[i]]
            if (sts != null) {
                for (st in sts) {
                    if (!sentence.startsWith(st, i)) continue
                    val rest = sentence.substring(i)
                    RULER.matchAt(rest, 0)?.let { m ->
                        val name = m.value
                        if (name.length in 3..5) {
                            hits.add(PersonProfile.canonical(name) to IntRange(i, i + name.length - 1))
                        }
                    }
                }
            }
            val cands = termsByFirst(context)[sentence[i]] ?: continue
            for (w in cands) {
                if (sentence.startsWith(w, i)) {
                    hits.add(PersonProfile.canonical(w) to IntRange(i, i + w.length - 1))
                }
            }
            // 短称谓：D2 归并不到具体人物时宁可不动，避免张冠李戴；
            // 旧栈按国别 + 在位君主消歧的规则在此不适用，锚点仅用于保留调用语义。
            if (year == null && anchorState == null) continue
            val als = aliasB[sentence[i]] ?: continue
            for (a in als) {
                if (!sentence.startsWith(a, i)) continue
                val owner = PersonProfile.profileOf(a)?.name ?: continue
                hits.add(owner to IntRange(i, i + a.length - 1))
            }
        }

        for (v in SPEECH) {
            var from = 0
            while (true) {
                val i = sentence.indexOf(v, from)
                if (i < 0) break
                if (i >= 2) {
                    val start = maxOf(0, i - 3)
                    val cand = sentence.substring(start, i)
                    pickName(context, cand, year)?.let { (n, off) ->
                        hits.add(
                            PersonProfile.canonical(n) to
                                IntRange(start + off, start + off + n.length - 1)
                        )
                    }
                }
                from = i + 1
            }
        }

        return hits.sortedBy { it.second.first }
    }

    private fun pickName(context: Context, raw: String, year: Int?): Pair<String, Int>? {
        val t = raw.trim()
        if (t.length < 2) return null
        for (st in STATES) {
            val idx = t.indexOf(st)
            if (idx < 0) continue
            RULER.matchAt(t.substring(idx), 0)?.let { m ->
                if (m.value.length >= 3) return m.value to idx
            }
        }
        for (i in t.indices) {
            val cands = termsByFirst(context)[t[i]] ?: continue
            for (w in cands) {
                if (t.startsWith(w, i)) return w to i
            }
            aliasBuckets(context)[t[i]]?.firstOrNull { a ->
                a.length >= 2 && t.startsWith(a, i) &&
                    PersonProfile.profileOf(a) != null
            }?.let { return it to i }
        }
        if (t.length >= 2 && t[0] in "子叔伯季孟仲" && t.all { it in '\u4e00'..'\u9fff' }) {
            return t to 0
        }
        return null
    }

    private val RULER = Regex("([\\u4e00-\\u9fff]{0,2})(公|侯|伯|子|男|王|君)")

    private fun factionOf(name: String): String {
        for (st in STATES) {
            if (name.startsWith(st)) return st
        }
        return "其他"
    }

    private fun labelOf(between: String): String? {
        val s = between.take(5)
        for ((kw, label) in REL_PATTERNS) {
            if (s.contains(kw)) return label
        }
        return null
    }

    /** 本段里与他有关的句子（按句切），最多 3 条 */
    fun sentencesAbout(
        context: Context,
        text: String,
        name: String,
        year: Int?,
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<String>(4)
        for (sen in splitSentences(text)) {
            if (sen.length < 4) continue
            val hits = findPersonHits(context, sen, year)
            if (hits.any { it.first == name }) {
                out.add(sen.trim())
                if (out.size >= 3) break
            }
        }
        return out
    }

    /** 本段同场人物：和他在同一句里出现的人，按次数排 */
    fun coPresent(
        context: Context,
        text: String,
        name: String,
        year: Int?,
    ): List<CoPresent> {
        if (text.isBlank()) return emptyList()
        val counts = HashMap<String, Int>(8)
        val samples = HashMap<String, String>(8)
        val labels = HashMap<String, String>(8)
        for (sen in splitSentences(text)) {
            if (sen.length < 4) continue
            val hits = findPersonHits(context, sen, year)
            if (hits.none { it.first == name }) continue
            for ((n, r) in hits) {
                if (n == name || n.length < 2) continue
                counts[n] = (counts[n] ?: 0) + 1
                if (!samples.containsKey(n)) samples[n] = sen.trim()
                if (!labels.containsKey(n)) {
                    val nr = hits.firstOrNull { it.first == name }?.second
                    if (nr != null) {
                        val between = when {
                            nr.last < r.first -> sen.substring(nr.last + 1, r.first)
                            r.last < nr.first -> sen.substring(r.last + 1, nr.first)
                            else -> ""
                        }
                        labelOf(between)?.let { labels[n] = it }
                    }
                }
            }
        }
        return counts.entries.sortedByDescending { it.value }.take(8)
            .map { CoPresent(it.key, it.value, samples[it.key].orEmpty(), labels[it.key].orEmpty()) }
    }

    private fun splitSentences(text: String): List<String> {
        val out = ArrayList<String>(12)
        val sb = StringBuilder()
        for (c in text) {
            sb.append(c)
            if (c == '。' || c == '！' || c == '？' ||
                c == '；' || c == '\n'
            ) {
                val v = sb.toString()
                if (v.isNotBlank()) out.add(v)
                sb.setLength(0)
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    }

    /** 生卒年格式化：公元前用「前 N」，生卒双全都算岁数；无载标「?」。 */
    private fun formatSpan(birth: Int?, death: Int?): String {
        if (birth == null && death == null) return ""
        val b = fmtYear(birth)
        val d = fmtYear(death)
        return if (birth != null && death != null) "$b — $d（${death - birth}岁）" else "$b — $d"
    }

    private fun fmtYear(y: Int?): String =
        y?.let { if (it <= 0) "前${1 - it}" else "$it" } ?: "?"

    private val STATES_BY_FIRST: Map<Char, List<String>> by lazy {
        STATES.groupBy { it[0] }.mapValues { e -> e.value.sortedByDescending { it.length } }
    }
}
