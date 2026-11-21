package io.legado.app.help.relation

import android.content.Context
import io.legado.app.help.ChineseDict

/**
 * 人名注音
 *
 * 字库给的是单字常用音，人名里会错读：郦食其（lì yì jī）、樊於期（fán wū jī）、
 * 嫪毐（lào ǎi）、冒顿（mò dú）、皋陶（gāo yáo）、傅说（fù yuè）。
 * 这里先查人名专用读音，再逐字回落到字库。
 */
object PersonPinyin {

    /** 姓氏与人名里的固定异读，key 是整个姓或整字 */
    private val SURNAME = mapOf(
        "祭" to "zhài", "繁" to "pó", "种" to "chóng", "能" to "nài",
        "员" to "yùn", "单" to "shàn", "解" to "xiè", "仇" to "qiú",
        "查" to "zhā", "覃" to "qín", "区" to "ōu", "华" to "huà",
        "任" to "rén", "隗" to "wěi", "郄" to "qiè", "郤" to "xì",
        "郦" to "lì", "万俟" to "mò qí", "尉迟" to "yù chí",
        "子车" to "zǐ jū", "女娲" to "nǚ wā", "皋陶" to "gāo yáo",
    )

    /** 整词读音，优先级最高 */
    private val WORD = mapOf(
        "郦食其" to "lì yì jī",
        "樊於期" to "fán wū jī",
        "嫪毐" to "lào ǎi",
        "冒顿" to "mò dú",
        "傅说" to "fù yuè",
        "皋陶" to "gāo yáo",
        "郤克" to "xì kè",
        "隗嚣" to "wěi áo",
        "阏氏" to "yān zhī",
        "龟兹" to "qiū cí",
        "可汗" to "kè hán",
        "阏与" to "yān yǔ",
        "郇瑕" to "xún xiá",
        "阚泽" to "kàn zé",
        "郏敖" to "jiá áo",
        "邾娄" to "zhū lóu",
        "郦商" to "lì shāng",
        "郤缺" to "xì quē",
        "栾盈" to "luán yíng",
        "蘧伯玉" to "qú bó yù",
        "咎犯" to "jiù fàn",
        "南宫适" to "nán gōng kuò",
        "阏伯" to "è bó",
        "实沈" to "shí chén",
    )

    /** 单字在人名中的异读 */
    private val CHAR = mapOf(
        "食" to "yì", "其" to "jī", "於" to "wū", "期" to "jī",
        "嫪" to "lào", "毐" to "ǎi", "冒" to "mò", "顿" to "dú",
        "说" to "yuè", "陶" to "yáo", "郤" to "xì", "阏" to "yān",
        "龟" to "qiū", "兹" to "cí", "可" to "kè", "汗" to "hán",
        "翟" to "dí", "儋" to "dān", "贲" to "bēn", "舄" to "xì",
        "隗" to "wěi", "郦" to "lì", "郇" to "xún", "阚" to "kàn",
    )

    /** 整词优先，其次逐字，最后落字库 */
    fun of(context: Context, name: String): String {
        if (name.isBlank()) return ""
        WORD[HanToSimp(name)]?.let { return it }
        WORD[name]?.let { return it }

        val sb = StringBuilder()
        val chars = name.toCharArray()
        var i = 0
        while (i < chars.size) {
            // 复姓先吃两字：万俟、尉迟、子车
            if (i + 1 < chars.size) {
                val two = "" + chars[i] + chars[i + 1]
                val py2 = SURNAME[two]
                if (py2 != null) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(py2)
                    i += 2
                    continue
                }
            }
            val c = chars[i].toString()
            val py = (if (i == 0) SURNAME[c] else CHAR[c]) ?: syllable(context, c)
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(py ?: "?")
            i++
        }
        return sb.toString()
    }

    private fun syllable(context: Context, ch: String): String? {
        val info = ChineseDict.getChar(context, ch) ?: return null
        return info.pinyin.split(' ').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 繁体字也走一遍简体键，CBDB 名字是繁体 */
    private fun HanToSimp(s: String): String = io.legado.app.help.HanConv.toSimp(s)
}
