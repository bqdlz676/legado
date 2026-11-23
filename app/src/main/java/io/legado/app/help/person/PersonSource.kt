package io.legado.app.help.person

/**
 * 人物档案出处标注（规格切片-02 §升格段「数据可信 = 命」）。
 *
 * 每条人物记录必须带来源与版本：
 * - [Cbdb]：Harvard CBDB（中国历代人物传记资料库，Harvard University）收录记录，[recordId] 透传 CBDB Person ID；
 * - [Classic]：公版典籍出处，标注到篇/卷，如《史记·卷九十二·淮阴侯列传第三十二》；
 *   避开中华书局点校版等现代点校本的版权表述，只写公版书名与篇卷。
 *
 * 本类型为冻结契约的一部分：新增来源种类前先回规格核对，不随意扩展。
 */
sealed class PersonSource {

    /** Harvard CBDB 收录记录。[recordId] 为 CBDB Person ID，不可为空串。 */
    data class Cbdb(val recordId: String) : PersonSource()

    /** 公版典籍出处。[citation] 形如「《史记·淮阴侯列传》」，不可为空串。 */
    data class Classic(val citation: String) : PersonSource()

    /** 气泡 / 人物卡上直接展示的出处字符串。 */
    val label: String
        get() = when (this) {
            is Cbdb -> "Harvard CBDB #$recordId"
            is Classic -> citation
        }
}
