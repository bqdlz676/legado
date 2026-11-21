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
        val wlat: Double? = null,
        val wlng: Double? = null,
        /** 地名时空实体 id（place_entity），点面分离后只有 P 型画点 */
        val placeId: String? = null,
        val placeKind: Char? = null,
        val tier: Int? = null,
        /** 同名候选的简短标签，进「还有 N 个同名」 */
        val others: List<String> = emptyList(),
        /** 精确到一年就写这个，供人物卡片算年龄 */
        val anchorYear: Int? = null,
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
        var e = PlaceDict.matchAt(context, text, pos)
        if (e == null) {
            e = PlaceDict.matchEnglish(context, text, pos) ?: return null
        }
        // 时空本体优先：同名靠 ContextAnchor 的年份筛，点面分开
        PlaceTimeDict.ensureLoaded(context)
        val st = io.legado.app.help.chrono.ContextAnchor.get()
        val r = PlaceTimeDict.resolve(context, e.name, st.year)
        if (r.hit != null) {
            val hit = r.hit
            val others = r.others.take(6).map {
                "${it.name}（${it.era} ${it.spanLabel()}${if (!it.isPoint) "·辖区" else ""}）"
            }
            return Hit(
                name = hit.name,
                kind = Kind.PLACE,
                meta = buildString {
                    append(hit.era).append('·')
                    append(if (hit.isPoint) "城邑" else "辖区")
                    append('·').append(hit.tierLabel)
                    if (!r.anchored) append("·年份未锚定")
                },
                detail = buildString {
                    append(hit.spanLabel()).append('\u3000').append(hit.tierLabel).append('\n')
                    append(hit.note)
                    if (others.isNotEmpty()) {
                        append("\n另有同名 ").append(r.others.size).append(" 处，可切换")
                    }
                    if (hit.tier >= 3 && hit.isPoint) {
                        append("\n坐标由文献推定，只作定位参考")
                    }
                },
                pinyin = "",
                wlat = hit.lat,
                wlng = hit.lng,
                placeId = hit.id,
                placeKind = hit.kind,
                tier = hit.tier,
                others = others,
                anchorYear = st.year,
            )
        }
        // 未入时空本体，退回旧条目
        val foreign = e.en != null
        return Hit(
            name = e.name,
            kind = Kind.PLACE,
            meta = if (e.world) "世界地名" else "古地名",
            detail = buildString {
                if (e.note.isNotBlank()) append(e.note)
                if (e.modern.isNotBlank() && !e.note.contains(e.modern)) {
                    if (isNotEmpty()) append('\n')
                    append("今地：").append(e.modern)
                }
                if (foreign && e.en != null) {
                    if (isNotEmpty()) append('\n')
                    append("原名：").append(e.en)
                }
                if (isEmpty()) append(if (e.world) "世界地名" else "先秦地名")
            },
            pinyin = "",
            wlat = if (e.world) e.lat else null,
            wlng = if (e.world) e.lng else null,
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
