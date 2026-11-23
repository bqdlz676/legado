package io.legado.app.help.person

/**
 * 人物档案（规格切片-02 §3 人名行 + §升格段「点进去看到本名 / 字 / 号 / 封号 / 生卒 / 一件最出名的事」）。
 *
 * 古书里同一个人换着称呼出现：刘邦—沛公—汉王，屈原—屈平—三闾大夫。
 * 抽取时先按别名索引把称呼并到本名，一个人才是一个档案节点。
 *
 * 生卒年为公元年，负数表示公元前；[birthYear] / [deathYear] 为 null 表示史料无载——
 * 不猜、不补约数。同名不同人各持独立 [personId]，靠 [PersonDirectory] 的同名分组区分。
 *
 * 字段冻结：对外气泡层只读本类字段，不新增「凭姓名猜身份」的派生字段。
 *
 * @param personId      稳定主键；CBDB 记录透传 CBDB Person ID，内置种子记录用 `seed:…` 前缀
 * @param name          本名
 * @param courtesyName  字，无载为 null
 * @param styleName     号，无载为 null
 * @param titles        封号 / 爵号 / 谥号（如 淮阴侯、武安君、文信侯）
 * @param aliases       其余别称：邦称、排行、官称、别称等；一人十几种称呼全部在此归并
 * @param birthYear     生年（公元年，负为公元前），无载为 null
 * @param deathYear     卒年（公元年，负为公元前），无载为 null
 * @param nationality   国籍 / 国别（如 魏、秦、楚）
 * @param identity      身份（如 秦国相、汉初名将）
 * @param biography     一句话小传
 * @param lineage       谱系：父 / 子 / 师承 / 同宗等关系，无考为空列表，不编
 * @param notableEvent  一件最出名的事
 * @param dynasty       朝代 / 时期，同名消歧展示用，可空
 * @param source        出处标注（CBDB 收录号或公版典籍篇卷），绝不允许空出处
 * @param preferred     同名分组内的默认命中项（无上下文锚点时先出这一位）
 */
data class PersonProfile(
    val personId: String,
    val name: String,
    val courtesyName: String? = null,
    val styleName: String? = null,
    val titles: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val birthYear: Int? = null,
    val deathYear: Int? = null,
    val nationality: String,
    val identity: String,
    val biography: String,
    val lineage: List<String> = emptyList(),
    val notableEvent: String,
    val dynasty: String? = null,
    val source: PersonSource,
    val preferred: Boolean = false,
) {

    /** 该人物的全部称呼（本名 + 字 + 号 + 封号 + 别称），供别名归并索引与候选展示。 */
    val allCallNames: List<String>
        get() = buildList {
            add(name)
            courtesyName?.let { add(it) }
            styleName?.let { add(it) }
            addAll(titles)
            addAll(aliases)
        }.filter { it.isNotBlank() }.distinct()

    companion object {

        // ---- 冻结对外 API（规格切片-02 §6 PersonProvider 行）----

        /**
         * 按称呼（本名 / 字 / 号 / 封号 / 别称）查人物档案；别名归并到本名后返回。
         * 同名多人时返回分组内默认命中项；查无此人返回 null，不凭姓名猜身份。
         */
        fun profileOf(name: String): PersonProfile? = PersonDirectory.profileOf(name)

        /** 别名 / 字 / 号 / 封号归并到本名；查无此人时原样返回归一化后的输入串。 */
        fun canonical(name: String): String = PersonDirectory.canonical(name)

        /** 取生卒年对（生年，卒年）；任一年份无载对应位置为 null；查无此人为 null。 */
        fun lifeSpan(name: String): Pair<Int?, Int?>? = PersonDirectory.lifeSpan(name)

        // ---- 以下为非冻结扩展，供消歧 / 候选切换使用，气泡层按需调用 ----

        /** 按称呼 + 主键精确定位同名者；[personId] 命中时忽略同名默认规则。 */
        fun profileOf(name: String, personId: String?): PersonProfile? =
            PersonDirectory.profileOf(name, personId = personId)

        /**
         * 按称呼查同名全部候选（含默认命中项），供气泡「还有 N 种解释」一键切换。
         * 无候选返回空列表，绝不塞假数据。
         */
        fun candidatesOf(name: String): List<PersonProfile> = PersonDirectory.candidatesOf(name)
    }
}
