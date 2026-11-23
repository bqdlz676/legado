package io.legado.app.help.dict

/**
 * 出处标注常量（规格 §升格做：数据可信 = 命）。
 *
 * 版权口径：
 * - 《说文解字》取大徐本（南唐/宋·徐铉校定本）公版影印系统的字头与释文，
 *   不使用中华书局点校版等现代点校本的排印成果；
 * - 典籍例句一律取自公版先秦两汉典籍（《诗经》《左传》《论语》《孟子》《周易》等）；
 * - 白话训释为本模块据公版字书所作，单独标注，不冒充古人原文。
 */
internal object DictSource {

    /** 《说文解字》大徐本（公版）。 */
    const val SHUOWEN_DAXU =
        "《说文解字》大徐本（宋·徐铉校定，公版影印本；非现代点校本）"

    /** 《康熙字典》（清·张玉书等纂，公版）。 */
    const val KANGXI = "《康熙字典》（清·张玉书等纂，公版）"

    /** 白话训释：据公版字书义项改写，非古籍原文。 */
    const val LOCAL_GLOSS = "白话训释（本模块据公版字书义项改写）"

    /** 词目出自公版典籍，训释为本地种子。 */
    const val CLASSIC_WORD = "词目见公版先秦两汉典籍；训释为本模块种子"

    /** 常用文言词，未见单一典籍首见出处。 */
    const val COMMON_WORD = "文言常用词种子（公版典籍词目；训释为本模块）"

    /** 拼接多条出处，去空去重，分号分隔。 */
    fun join(vararg parts: String?): String =
        parts.filter { !it.isNullOrBlank() }.distinct().joinToString("；")
}
