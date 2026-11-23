package io.legado.app.help.allusion

/**
 * 典故出处标注与版本口径（规格切片-02 §升格段「每个字词 / 人物 / 事件标来源和版本」）。
 *
 * 版权口径（为什么这样标）：
 * - 典籍一律标公版书名 + 篇卷：先秦两汉诸子、《史记》《汉书》《后汉书》《三国志》
 * 《晋书》《宋书》《世说新语》《吕氏春秋》《列子》《山海经》《淮南子》《列女传》
 * 及唐宋诗文，作者均故世逾千年，文本本身在公版；
 * - 明确不采中华书局点校版等现代点校本的断句、校勘记与排印成果，标注里写清
 * 「公版影印本；非现代点校本」，避免把今人整理版权带进离线包；
 * - 四字成语若是后世对古籍本事的概括（如「纸上谈兵」），detail 中如实说明概括关系，
 * 不在 source 里伪托原文出处；
 * - 白话释义、本事叙述为本模块据公版典籍改写，单独标注，不冒充古籍原文。
 */
internal object AllusionSource {

    /** 公版典籍出处（书 + 篇卷）。[authorNote] 如「西汉·司马迁撰」。 */
    fun classic(bookChapter: String, authorNote: String): String =
        "$bookChapter（$authorNote，公版影印本；非现代点校本）"

    /** 公版诗文名句出处。 */
    fun poem(poet: String, title: String): String =
        "$poet《$title》（公版传世诗文集）"

    /** 古籍注疏出处（如三国志裴注）。 */
    fun annotatedClassic(bookChapter: String, note: String): String =
        "$bookChapter $note（公版古籍注疏；非现代点校本）"

    /** 白话释义与本事叙述的本地改写标注，拼在典籍出处之后。 */
    const val LOCAL_GLOSS = "释义与本事为本模块据公版典籍改写，非古籍原文"

    /** 拼接多条出处，去空去重，分号分隔。 */
    fun join(vararg parts: String?): String =
        parts.filter { !it.isNullOrBlank() }.distinct().joinToString("；")
}
