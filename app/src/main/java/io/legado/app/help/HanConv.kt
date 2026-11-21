package io.legado.app.help

import android.content.Context

/**
 * 繁简互转
 * ts.txt：繁体 → 简体（OpenCC TSCharacters）
 * st.txt：简体 → 繁体（OpenCC STCharacters，取官方第一候选，避免「苏→囌」这类错字）
 * 只做单字映射，够用于字头、词头、姓名；不做词组级异体校正。
 */
object HanConv {

    @Volatile
    private var t2s: Map<Char, Char> = emptyMap()

    @Volatile
    private var s2t: Map<Char, Char> = emptyMap()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            t2s = readMap(context, "ts.txt")
            s2t = readMap(context, "st.txt")
            loaded = true
        }
    }

    private fun readMap(context: Context, asset: String): Map<Char, Char> {
        val m = HashMap<Char, Char>(4600)
        runCatching {
            context.assets.open(asset).bufferedReader().use { r ->
                while (true) {
                    val ln = r.readLine() ?: break
                    if (ln.isEmpty() || ln.startsWith("#")) continue
                    val parts = ln.split('\t')
                    if (parts.size < 2 || parts[0].length != 1) continue
                    val other = parts[1].substringBefore(' ')
                    if (other.length == 1) m[parts[0][0]] = other[0]
                }
            }
        }
        return m
    }

    fun toSimp(text: String): String {
        if (t2s.isEmpty() || text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(t2s[c] ?: c)
        return sb.toString()
    }

    fun toTrad(text: String): String {
        if (s2t.isEmpty() || text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(s2t[c] ?: c)
        return sb.toString()
    }

    /** 是否含繁体字（转简后不同即认为有） */
    fun hasTrad(text: String): Boolean = toSimp(text) != text

    /** 气泡用：繁简不同才给「简」这一行，否则空串 */
    fun simpLine(text: String): String {
        val s = toSimp(text)
        return if (s != text && s.isNotEmpty()) "〔简〕$s" else ""
    }
}
