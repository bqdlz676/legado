package io.legado.app.help.person

/**
 * 人名专用异读拼音层（规格切片-02 §6 人名注音；历史附件「人名中的多音字有很高概率读错」）。
 *
 * 字库给的是单字常用音，人名里会错读：郦食其（lì yì jī）、樊於期（fán wū jī）、
 * 嫪毐（lào ǎi）、冒顿（mò dú）、皋陶（gāo yáo）、傅说（fù yuè）。
 * 顺序：整词异读表 → 复姓 → 姓氏首字异读 → 人名中单字异读 → 宿主注入的字库读音兜底。
 *
 * 本表只收公版史传通行读音，不引 Wikipedia / CC-CEDICT 等受版权词典词条；
 * 兜底音节由调用方注入（D1 字库），本层不直接依赖字典资产。
 */
object PersonPinyin {

    /** 整词读音，优先级最高（姓名 / 史传专名固定读法）。 */
    private val WORD: Map<String, String> = mapOf(
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

    /** 姓氏 / 复姓固定异读，key 为整姓或复姓两字。 */
    private val SURNAME: Map<String, String> = mapOf(
        "祭" to "zhài", "繁" to "pó", "种" to "chóng", "能" to "nài",
        "员" to "yùn", "单" to "shàn", "解" to "xiè", "仇" to "qiú",
        "查" to "zhā", "覃" to "qín", "区" to "ōu", "华" to "huà",
        "任" to "rén", "隗" to "wěi", "郄" to "qiè", "郤" to "xì",
        "郦" to "lì", "万俟" to "mò qí", "尉迟" to "yù chí",
        "子车" to "zǐ jū", "女娲" to "nǚ wā", "皋陶" to "gāo yáo",
    )

    /** 单字在人名（非首字位置）中的异读。 */
    private val CHAR: Map<String, String> = mapOf(
        "食" to "yì", "其" to "jī", "於" to "wū", "期" to "jī",
        "嫪" to "lào", "毐" to "ǎi", "冒" to "mò", "顿" to "dú",
        "说" to "yuè", "陶" to "yáo", "郤" to "xì", "阏" to "yān",
        "龟" to "qiū", "兹" to "cí", "可" to "kè", "汗" to "hán",
        "翟" to "dí", "儋" to "dān", "贲" to "bēn", "舄" to "xì",
        "隗" to "wěi", "郦" to "lì", "郇" to "xún", "阚" to "kàn",
    )

    /** 整词异读命中（不含兜底音节）；查无返回 null。 */
    fun wordOverride(name: String): String? = WORD[name.trim()]

    /**
     * 人名注音。[syllable] 为字库单字读音兜底（入参为单字，返回带调拼音或 null）；
     * 兜底也查不到的字标「?」，不猜读音。
     */
    fun of(name: String, syllable: (String) -> String? = { null }): String {
        val clean = name.trim()
        if (clean.isEmpty()) return ""
        WORD[clean]?.let { return it }

        val sb = StringBuilder()
        val chars = clean.toCharArray()
        var i = 0
        while (i < chars.size) {
            // 复姓先吃两字：万俟、尉迟、子车
            val two = if (i + 1 < chars.size) "" + chars[i] + chars[i + 1] else null
            val pyTwo = two?.let { SURNAME[it] }
            if (pyTwo != null) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(pyTwo)
                i += 2
                continue
            }
            val c = chars[i].toString()
            val py = (if (i == 0) SURNAME[c] else CHAR[c]) ?: syllable(c)
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(py ?: "?")
            i++
        }
        return sb.toString()
    }
}
