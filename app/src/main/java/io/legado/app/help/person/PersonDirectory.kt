package io.legado.app.help.person

/**
 * 人物档案目录：别名归并索引 + 同名分组消歧 + 种子档案 + 外部（CBDB）注册扩展点。
 *
 * 职责边界（模块 D2）：
 * - 只做「称呼 → 档案」的归并与同名区分，不碰气泡 UI、不碰拼音、不碰关系抽取；
 * - 纯 Kotlin / 纯 JVM，不依赖 android.content.Context，宿主单测可直接跑；
 * - 数据可信 = 命：每条记录必须带 [PersonSource]，无出处记录在注册时即被拒绝，不进索引。
 *
 * 同名消歧规则：
 * 1. 主键 [PersonProfile.personId] 精确命中，直接返回（候选切换走这条）；
 * 2. 上下文年份命中生卒区间者优先；区间均无载时取年代中点最近者；
 * 3. 仍无法区分时，返回分组内 [PersonProfile.preferred] 项，其余同名者进 [candidatesOf] 供一键切换。
 */
internal object PersonDirectory {

    /** 内置种子档案：少量、有公版典籍出处；生卒无载者如实置 null，不补约数。 */
    private val seed: List<PersonProfile> = buildSeed()

    /** 外部档案（后续接入 Harvard CBDB 资产解析后注册到此）；种子优先，外部补缺。 */
    private val external: MutableList<PersonProfile> = mutableListOf()

    private var all: List<PersonProfile> = seed
    private var byId: Map<String, PersonProfile> = emptyMap()

    /** 归一化称呼 → personId 列表（一个称呼可能对应同名/撞称多人，故为列表）。 */
    private val byCallName: MutableMap<String, MutableList<String>> = mutableMapOf()

    init {
        rebuild()
    }

    // ---- 对外查询（companion facade 转发到这里）----

    fun profileOf(raw: String, personId: String? = null, year: Int? = null): PersonProfile? {
        personId?.let { byId[it]?.let { return it } }
        val key = normalize(raw)
        if (key.isEmpty()) return null
        val ids = byCallName[key] ?: return null
        val candidates = ids.mapNotNull { byId[it] }.distinct()
        return disambiguate(candidates, year)
    }

    fun canonical(raw: String): String = profileOf(raw)?.name ?: normalize(raw)

    fun lifeSpan(raw: String): Pair<Int?, Int?>? {
        val p = profileOf(raw) ?: return null
        return Pair(p.birthYear, p.deathYear)
    }

    fun candidatesOf(raw: String): List<PersonProfile> {
        val key = normalize(raw)
        if (key.isEmpty()) return emptyList()
        val ids = byCallName[key] ?: return emptyList()
        return ids.mapNotNull { byId[it] }.distinct()
    }

    /** 全部在册档案（测试 / 自检遍历出处用）。 */
    fun allRecords(): List<PersonProfile> = all

    /**
     * 注册外部档案（CBDB 解析层调用）。重复 [PersonProfile.personId] 以后注册者覆盖；
     * 无出处、本名空、称呼空的记录直接拒绝，抛 [IllegalArgumentException]，不进索引。
     */
    @Synchronized
    fun register(records: List<PersonProfile>) {
        records.forEach { requireValid(it) }
        external.addAll(records)
        rebuild()
    }

    // ---- 内部实现 ----

    @Synchronized
    private fun rebuild() {
        val records = seed + external
        records.forEach { requireValid(it) }
        all = records
        byId = records.associateBy { it.personId }
        byCallName.clear()
        for (r in records) {
            for (call in r.allCallNames) {
                val key = normalize(call)
                if (key.isEmpty()) continue
                byCallName.getOrPut(key) { mutableListOf() }.add(r.personId)
            }
        }
    }

    private fun requireValid(p: PersonProfile) {
        require(p.personId.isNotBlank()) { "personId 不能为空" }
        require(p.name.isNotBlank()) { "本名不能为空：${p.personId}" }
        require(p.source.label.isNotBlank()) { "出处不能为空：${p.name}" }
    }

    private fun disambiguate(candidates: List<PersonProfile>, year: Int?): PersonProfile? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        if (year != null) {
            // 1. 生卒区间直接覆盖该年
            candidates.firstOrNull {
                it.birthYear != null && it.deathYear != null && year in it.birthYear!!..it.deathYear!!
            }?.let { return it }
            // 2. 年代中点最近者
            val withMid = candidates.mapNotNull { p ->
                val b = p.birthYear ?: return@mapNotNull null
                val d = p.deathYear ?: return@mapNotNull null
                p to (b + d) / 2
            }
            if (withMid.isNotEmpty()) {
                return withMid.minByOrNull { kotlin.math.abs(it.second - year) }!!.first
            }
        }
        // 3. 默认命中项；否则首位（候选清单由 candidatesOf 给出，不丢其余同名者）
        return candidates.firstOrNull { it.preferred } ?: candidates.first()
    }

    /** 称呼归一化：去首尾空白与包裹引号/括号，折叠内部空白；不做繁简转换（归一键表层另管）。 */
    fun normalize(raw: String): String {
        var s = raw.trim()
        val wrap = charArrayOf('「', '」', '『', '』', '“', '”', '‘', '’', '"', '\'', '（', '）', '(', ')', '【', '】', '[', ']', '《', '》')
        var changed = true
        while (changed && s.isNotEmpty()) {
            changed = false
            if (s.first() in wrap) { s = s.substring(1); changed = true }
            if (s.isNotEmpty() && s.last() in wrap) { s = s.substring(0, s.length - 1); changed = true }
        }
        return s.replace(Regex("\\s+"), "")
    }

    // ---- 种子档案（公版史料出处；生卒无载置 null）----

    private fun buildSeed(): List<PersonProfile> {
        val rows = listOf(
            seed(
                id = "seed:kongzi", name = "孔子", nationality = "鲁",
                identity = "思想家、教育家，儒家学派创始人",
                biography = "删订六经，聚徒讲学，弟子三千",
                notableEvent = "创立儒家学派，开私人讲学之风",
                source = PersonSource.Classic("《史记·卷四十七·孔子世家第十七》"),
                courtesy = "仲尼", aliases = listOf("孔丘", "孔夫子", "尼父"),
                birth = -551, death = -479, lineage = listOf("父叔梁纥"), dynasty = "春秋",
            ),
            seed(
                id = "seed:mengzi", name = "孟子", nationality = "邹",
                identity = "思想家，儒家代表人物，被尊亚圣",
                biography = "游说齐梁诸国，倡仁政与民贵君轻",
                notableEvent = "述《孟子》七篇，阐发性善与仁政",
                source = PersonSource.Classic("《史记·卷七十四·孟子荀卿列传第十四》"),
                courtesy = "子舆", aliases = listOf("孟轲", "亚圣"),
                birth = -372, death = -289, dynasty = "战国",
            ),
            seed(
                id = "seed:laozi", name = "老子", nationality = "周",
                identity = "思想家，道家学派创始人",
                biography = "周守藏室之史，见周衰而去",
                notableEvent = "著《道德经》五千言",
                source = PersonSource.Classic("《史记·卷六十三·老子韩非列传第三》"),
                aliases = listOf("李耳", "老聃", "伯阳"), dynasty = "春秋",
            ),
            seed(
                id = "seed:zhuangzi", name = "庄子", nationality = "宋",
                identity = "思想家，道家代表人物",
                biography = "曾为漆园吏，博学善辩",
                notableEvent = "著《庄子》，以寓言阐发逍遥齐物之旨",
                source = PersonSource.Classic("《史记·卷六十三·老子韩非列传第三》"),
                style = "南华真人", aliases = listOf("庄周"), dynasty = "战国",
            ),
            seed(
                id = "seed:quyuan", name = "屈原", nationality = "楚",
                identity = "楚国左徒，辞赋家",
                biography = "主张联齐抗秦，遭谗见疏",
                notableEvent = "郢都沦陷后怀石自沉汨罗江，作《离骚》",
                source = PersonSource.Classic("《史记·卷八十四·屈原贾生列传第二十四》"),
                courtesy = "原", aliases = listOf("屈平", "正则", "灵均", "三闾大夫"),
                birth = -340, death = -278, dynasty = "战国",
            ),
            seed(
                id = "seed:shangyang", name = "商鞅", nationality = "卫",
                identity = "秦国变法主持者，大良造",
                biography = "自魏入秦，两度推行变法",
                notableEvent = "废井田、奖军功，为秦国富强奠基",
                source = PersonSource.Classic("《史记·卷六十八·商君列传第八》"),
                aliases = listOf("卫鞅", "公孙鞅", "商君"),
                birth = -395, death = -338, dynasty = "战国",
            ),
            seed(
                id = "seed:suqin", name = "苏秦", nationality = "周",
                identity = "纵横家，合纵派代表",
                biography = "出游数年大困，发愤研读阴符",
                notableEvent = "佩六国相约从离衡，合纵抗秦",
                source = PersonSource.Classic("《史记·卷六十九·苏秦列传第九》"),
                courtesy = "季子", aliases = listOf("苏子"),
                death = -284, dynasty = "战国",
            ),
            seed(
                id = "seed:zhangyi", name = "张仪", nationality = "魏",
                identity = "纵横家，连横派代表，秦国相",
                biography = "与苏秦俱事鬼谷先生，后入秦主连横",
                notableEvent = "以连横游说六国事秦，拆散合纵",
                source = PersonSource.Classic("《史记·卷七十·张仪列传第十》"),
                aliases = listOf("张子"),
                death = -309, dynasty = "战国",
            ),
            seed(
                id = "seed:fanju", name = "范雎", nationality = "魏",
                identity = "秦国相，应侯",
                biography = "遭魏笞辱，化名张禄入秦",
                notableEvent = "献远交近攻之策，强秦而弱韩魏",
                source = PersonSource.Classic("《史记·卷七十九·范雎蔡泽列传第十九》"),
                courtesy = "叔", aliases = listOf("范睢", "张禄", "应侯"),
                death = -255, dynasty = "战国",
            ),
            seed(
                id = "seed:lvbuwei", name = "吕不韦", nationality = "卫",
                identity = "秦国相，文信侯",
                biography = "阳翟大贾，奇货可居，辅子楚继位",
                notableEvent = "组织门客纂《吕氏春秋》，悬金咸阳门求增损一字",
                source = PersonSource.Classic("《史记·卷八十五·吕不韦列传第二十五》"),
                titles = listOf("文信侯"), aliases = listOf("吕相"),
                birth = -292, death = -235, dynasty = "战国",
            ),
            seed(
                id = "seed:lisi", name = "李斯", nationality = "楚",
                identity = "秦朝丞相，法家",
                biography = "荀卿弟子，入秦为廷尉",
                notableEvent = "主张郡县、书同文，辅秦统一天下",
                source = PersonSource.Classic("《史记·卷八十七·李斯列传第二十七》"),
                courtesy = "通古",
                birth = -284, death = -208, dynasty = "秦",
            ),
            seed(
                id = "seed:baiqi", name = "白起", nationality = "秦",
                identity = "秦国名将，武安君",
                biography = "善用兵，南征北战七十余战未尝败",
                notableEvent = "长平之战坑赵卒四十万",
                source = PersonSource.Classic("《史记·卷七十三·白起王翦列传第十三》"),
                titles = listOf("武安君"), aliases = listOf("公孙起"),
                death = -257, dynasty = "战国",
            ),
            seed(
                id = "seed:wangjian", name = "王翦", nationality = "秦",
                identity = "秦国名将",
                biography = "频阳人，率秦军平定赵燕楚",
                notableEvent = "率六十万大军灭楚",
                source = PersonSource.Classic("《史记·卷七十三·白起王翦列传第十三》"),
                dynasty = "战国",
            ),
            seed(
                id = "seed:xiangyu", name = "项羽", nationality = "楚",
                identity = "西楚霸王",
                biography = "名籍字羽，楚将项燕之孙",
                notableEvent = "巨鹿之战破釜沉舟，垓下兵败自刎乌江",
                source = PersonSource.Classic("《史记·卷七·项羽本纪第七》"),
                courtesy = "羽", aliases = listOf("项籍", "西楚霸王"),
                birth = -232, death = -202, lineage = listOf("祖父项燕，楚将"), dynasty = "秦末汉初",
            ),
            seed(
                id = "seed:liubang", name = "刘邦", nationality = "沛",
                identity = "汉朝开国皇帝",
                biography = "泗水亭长起兵，先入关中约法三章",
                notableEvent = "垓下灭项羽，建立汉朝",
                source = PersonSource.Classic("《史记·卷八·高祖本纪第八》"),
                courtesy = "季", aliases = listOf("沛公", "汉王", "汉高祖"),
                birth = -256, death = -195, dynasty = "西汉",
            ),
            seed(
                id = "seed:zhangliang", name = "张良", nationality = "韩",
                identity = "汉初谋臣，留侯",
                biography = "五世相韩，博浪沙刺秦未遂",
                notableEvent = "鸿门宴助刘邦脱身，功成之后自请封留",
                source = PersonSource.Classic("《史记·卷五十五·留侯世家第二十五》"),
                courtesy = "子房", titles = listOf("留侯"),
                death = -186, lineage = listOf("五世相韩，父平为韩相"), dynasty = "西汉",
            ),
            seed(
                id = "seed:xiaohhe", name = "萧何", nationality = "沛",
                identity = "汉初丞相，酂侯",
                biography = "沛主吏掾，刘邦入关独收秦律令图书",
                notableEvent = "镇抚关中保障粮饷，荐韩信为大将，作《九章律》",
                source = PersonSource.Classic("《史记·卷五十三·萧相国世家第二十三》"),
                titles = listOf("酂侯"), aliases = listOf("萧相国"),
                death = -193, dynasty = "西汉",
            ),
            seed(
                id = "seed:hanxin-huaiyin", name = "韩信", nationality = "淮阴",
                identity = "汉初名将，汉初三杰之一",
                biography = "登坛拜将，助刘邦平定天下",
                notableEvent = "井陉背水一战破赵，垓下合围灭项羽",
                source = PersonSource.Classic("《史记·卷九十二·淮阴侯列传第三十二》"),
                titles = listOf("淮阴侯"), aliases = listOf("韩王孙"),
                death = -196, dynasty = "西汉", preferred = true,
            ),
            seed(
                id = "seed:hanxin-hanwang", name = "韩信", nationality = "韩",
                identity = "汉初诸侯王，故韩襄王孽孙",
                biography = "楚汉间受封韩王，后徙封马邑",
                notableEvent = "被围马邑后叛汉降匈奴，终为柴武所斩",
                source = PersonSource.Classic("《史记·卷九十三·韩信卢绾列传第三十三》"),
                aliases = listOf("韩王信", "韩王"),
                death = -196, lineage = listOf("故韩襄王孽孙"), dynasty = "西汉",
            ),
            seed(
                id = "seed:chenping", name = "陈平", nationality = "魏",
                identity = "汉初丞相，曲逆侯",
                biography = "少时家贫，好读书，先事项羽后归刘邦",
                notableEvent = "六出奇计，辅汉定天下",
                source = PersonSource.Classic("《史记·卷五十六·陈丞相世家第二十六》"),
                titles = listOf("曲逆侯"), aliases = listOf("陈丞相"),
                death = -178, dynasty = "西汉",
            ),
            seed(
                id = "seed:simaqian", name = "司马迁", nationality = "夏阳",
                identity = "西汉史学家、文学家",
                biography = "继任太史令，遍览皇家藏书",
                notableEvent = "受宫刑而著成《史记》一百三十篇",
                source = PersonSource.Classic("《史记·卷一百三十·太史公自序第七十》"),
                courtesy = "子长", aliases = listOf("太史公"), dynasty = "西汉",
            ),
            seed(
                id = "seed:caocao", name = "曹操", nationality = "沛国",
                identity = "东汉丞相，魏王",
                biography = "挟天子以令诸侯，扫平北方群雄",
                notableEvent = "官渡之战以少胜多破袁绍，统一北方",
                source = PersonSource.Classic("《三国志·卷一·魏书·武帝纪第一》"),
                courtesy = "孟德", aliases = listOf("阿瞒", "曹公", "魏武帝"),
                birth = 155, death = 220, lineage = listOf("父曹嵩，宦官曹腾养子"), dynasty = "东汉末",
            ),
            seed(
                id = "seed:zhugeliang", name = "诸葛亮", nationality = "琅琊",
                identity = "蜀汉丞相，武乡侯",
                biography = "躬耕南阳，刘备三顾茅庐而出",
                notableEvent = "隆中对定三分天下，北伐曹魏病逝五丈原",
                source = PersonSource.Classic("《三国志·卷三十五·蜀书·诸葛亮传第五》"),
                courtesy = "孔明", titles = listOf("武乡侯"), aliases = listOf("卧龙"),
                birth = 181, death = 234, dynasty = "三国·蜀",
            ),
            seed(
                id = "seed:zhouyu", name = "周瑜", nationality = "庐江",
                identity = "东吴大都督",
                biography = "与孙策总角之好，辅孙权据江东",
                notableEvent = "赤壁之战火烧曹兵，奠定三分格局",
                source = PersonSource.Classic("《三国志·卷五十四·吴书·周瑜鲁肃吕蒙传第九》"),
                courtesy = "公瑾", aliases = listOf("周郎"),
                birth = 175, death = 210, dynasty = "三国·吴",
            ),
            seed(
                id = "seed:libai", name = "李白", nationality = "陇西",
                identity = "唐代诗人，诗仙",
                biography = "仗剑远游，诗名动京师",
                notableEvent = "以乐府与歌行咏山河赠别，自成一家",
                source = PersonSource.Classic("《新唐书·卷二百二·文艺传中》"),
                courtesy = "太白", style = "青莲居士", aliases = listOf("谪仙人"),
                birth = 701, death = 762, dynasty = "唐",
            ),
            seed(
                id = "seed:dufu", name = "杜甫", nationality = "襄阳",
                identity = "唐代诗人，诗圣",
                biography = "献赋求仕，辗转陇蜀，晚岁漂泊湘江",
                notableEvent = "安史之乱中作「三吏三别」，纪一代兴衰",
                source = PersonSource.Classic("《新唐书·卷二百一·文艺传上》"),
                courtesy = "子美", style = "少陵野老", aliases = listOf("杜工部"),
                birth = 712, death = 770, dynasty = "唐",
            ),
            seed(
                id = "seed:sushi", name = "苏轼", nationality = "眉山",
                identity = "北宋文学家、书画家",
                biography = "嘉祐进士，历官杭、密、徐、湖，屡遭贬谪",
                notableEvent = "乌台诗案贬黄州，作前后《赤壁赋》",
                source = PersonSource.Classic("《宋史·卷三百三十八·列传第九十七》"),
                courtesy = "子瞻", style = "东坡居士", aliases = listOf("苏东坡"),
                birth = 1037, death = 1101, dynasty = "北宋",
            ),
        )
        return rows
    }

    private fun seed(
        id: String,
        name: String,
        nationality: String,
        identity: String,
        biography: String,
        notableEvent: String,
        source: PersonSource,
        courtesy: String? = null,
        style: String? = null,
        titles: List<String> = emptyList(),
        aliases: List<String> = emptyList(),
        birth: Int? = null,
        death: Int? = null,
        lineage: List<String> = emptyList(),
        dynasty: String? = null,
        preferred: Boolean = false,
    ) = PersonProfile(
        personId = id,
        name = name,
        courtesyName = courtesy,
        styleName = style,
        titles = titles,
        aliases = aliases,
        birthYear = birth,
        deathYear = death,
        nationality = nationality,
        identity = identity,
        biography = biography,
        lineage = lineage,
        notableEvent = notableEvent,
        dynasty = dynasty,
        source = source,
        preferred = preferred,
    )
}
