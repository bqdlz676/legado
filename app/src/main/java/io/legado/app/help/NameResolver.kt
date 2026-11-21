package io.legado.app.help

import android.content.Context
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.relation.PersonPinyin
import io.legado.app.help.relation.PersonProfile

/**
 * 阅读页点击的「人名 / 地名」一级。
 * 人名优先，其次地名；都匹配不到返回 null，回落到单字。
 */
object NameResolver {

    data class Hit(
        val name: String,
        val kind: Kind,
        val meta: String,
        val detail: String,
        val pinyin: String,
    )

    enum class Kind { PERSON, PLACE }

    @Volatile
    private var idx: Map<Char, List<String>>? = null

    fun preload(context: Context) {
        runCatching {
            PersonProfile.ensureLoaded(context)
            index(context)
            loadLords(context)
        }
        PlaceDict.ensureLoaded(context)
    }

    fun matchAt(context: Context, text: String, pos: Int): Hit? {
        if (pos !in text.indices) return null
        personAt(context, text, pos)?.let { return it }
        return placeAt(context, text, pos)
    }

    private fun personAt(context: Context, text: String, pos: Int): Hit? {
        lordAt(context, text, pos)?.let { return it }
        val map = index(context)
        for (len in 5 downTo 2) {
            val s0 = maxOf(0, pos - len + 1)
            val s1 = minOf(pos, text.length - len)
            if (s1 < s0) continue
            for (s in s0..s1) {
                val cand = text.substring(s, s + len)
                val bucket = map[cand[0]] ?: continue
                if (!bucket.contains(cand)) continue
                val p = PersonProfile.profileOf(cand)
                return Hit(
                    name = p?.name ?: cand,
                    kind = Kind.PERSON,
                    meta = personMeta(p),
                    detail = personDetail(p, cand),
                    pinyin = PersonPinyin.of(context, cand)
                )
            }
        }
        return null
    }

    private data class Lord(
        val full: String,
        val state: String,
        val title: String,
        val start: Int,
        val span: Int,
    )

    @Volatile
    private var lordMap: Map<String, Lord>? = null

    @Volatile
    private var lordIdx: Map<Char, List<String>>? = null

    /** 君主表：鲁僖公 / 鲁僖 都能命中，正文里的简称也能落到人 */
    private fun lordAt(context: Context, text: String, pos: Int): Hit? {
        val map = loadLords(context)
        if (map.isEmpty()) return null
        val idx = lordIndex(context)
        for (len in 5 downTo 2) {
            val s0 = maxOf(0, pos - len + 1)
            val s1 = minOf(pos, text.length - len)
            if (s1 < s0) continue
            for (s in s0..s1) {
                val cand = text.substring(s, s + len)
                val bucket = idx[cand[0]] ?: continue
                if (!bucket.contains(cand)) continue
                val lord = map[cand] ?: continue
                return Hit(
                    name = lord.full,
                    kind = Kind.PERSON,
                    meta = lord.state + "国君 · " + lord.title,
                    detail = lordDetail(lord),
                    pinyin = PersonPinyin.of(context, lord.full)
                )
            }
        }
        return null
    }

    private fun lordDetail(l: Lord): String {
        val sb = StringBuilder()
        sb.append(l.state).append("国 ").append(l.title)
        if (l.start != 0) {
            sb.append('\n').append("即位 ").append(Chronology.yearLabel(l.start))
            if (l.span > 0) sb.append("，在位 ").append(l.span).append(" 年")
        }
        return sb.toString()
    }

    private fun loadLords(context: Context): Map<String, Lord> {
        lordMap?.let { return it }
        synchronized(this) {
            lordMap?.let { return it }
            val loaded = HashMap<String, Lord>(520)
            runCatching {
                context.applicationContext.assets.open("princes.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        val p = line.split('\t')
                        if (p.size < 3) return@forEach
                        val state = p[0].trim()
                        val title = p[1].trim()
                        if (state.isEmpty() || title.isEmpty()) return@forEach
                        val start = p.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                        val span = p.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
                        val full = state + title
                        val short = state + title.removeSuffix("公")
                        val lord = Lord(full, state, title, start, span)
                        loaded[full] = lord
                        if (short != full && short.length in 2..5) loaded[short] = lord
                    }
                }
            }
            lordMap = loaded
            return loaded
        }
    }

    private fun lordIndex(context: Context): Map<Char, List<String>> {
        lordIdx?.let { return it }
        synchronized(this) {
            lordIdx?.let { return it }
            val src = loadLords(context)
            val buckets = HashMap<Char, ArrayList<String>>(80)
            for (k in src.keys) {
                if (k.length < 2 || k.length > 5) continue
                buckets.getOrPut(k[0]) { ArrayList(12) }.add(k)
            }
            buckets.values.forEach { it.sortByDescending { w -> w.length } }
            val out = HashMap<Char, List<String>>(buckets.size)
            buckets.forEach { (k, v) -> out[k] = v }
            lordIdx = out
            return out
        }
    }

    private fun placeAt(context: Context, text: String, pos: Int): Hit? {
        val e = PlaceDict.matchAt(context, text, pos) ?: return null
        return Hit(
            name = e.name,
            kind = Kind.PLACE,
            meta = "古地名",
            detail = buildString {
                if (e.note.isNotBlank()) append(e.note)
                if (e.modern.isNotBlank() && !e.note.contains(e.modern)) {
                    if (isNotEmpty()) append('\n')
                    append("今地：").append(e.modern)
                }
                if (isEmpty()) append("先秦地名")
            },
            pinyin = ""
        )
    }

    private fun personMeta(p: PersonProfile.P?): String {
        if (p == null) return "古籍人名"
        val parts = ArrayList<String>(2)
        if (p.state.isNotBlank()) parts.add(p.state)
        if (p.identity.isNotBlank()) parts.add(p.identity)
        return if (parts.isEmpty()) "古籍人名" else parts.joinToString(" · ")
    }

    private fun personDetail(p: PersonProfile.P?, raw: String): String {
        if (p == null) return "档案未收，仅按古籍词表识别为人名。"
        val sb = StringBuilder()
        val span = lifeSpan(p.birth, p.death)
        if (span.isNotEmpty()) sb.append(span)
        if (p.alias.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("称谓　").append(p.alias.joinToString("、"))
        }
        if (sb.isEmpty()) sb.append("生平待考")
        return sb.toString()
    }

    private fun lifeSpan(b: Int?, d: Int?): String {
        if (b == null && d == null) return ""
        val sb = StringBuilder()
        if (b != null) sb.append("生 ").append(Chronology.yearLabel(b))
        if (d != null) {
            if (sb.isNotEmpty()) sb.append("　")
            sb.append("卒 ").append(Chronology.yearLabel(d))
        }
        return sb.toString()
    }

    /** 人名首字分桶，长词在前，3 万条只建一次 */
    private fun index(context: Context): Map<Char, List<String>> {
        idx?.let { return it }
        synchronized(this) {
            idx?.let { return it }
            PersonProfile.ensureLoaded(context)
            val buckets = HashMap<Char, ArrayList<String>>(6000)
            for (t in PersonProfile.allTerms) {
                if (t.length < 2 || t.length > 5) continue
                buckets.getOrPut(t[0]) { ArrayList(24) }.add(t)
            }
            buckets.values.forEach { it.sortByDescending { w -> w.length } }
            val out = HashMap<Char, List<String>>(buckets.size)
            buckets.forEach { (k, v) -> out[k] = v }
            idx = out
            return out
        }
    }
}
