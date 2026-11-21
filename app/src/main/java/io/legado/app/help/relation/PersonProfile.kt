package io.legado.app.help.relation

/**
 * 人物档案：本名 · 别名（字/号/封号/别称） · 生卒 · 身份
 *
 * 古书里同一个人会换着称呼出现：刘邦—沛公—汉王，屈原—屈平—三闾大夫。
 * 抽人时先按这张表把别名并到本名，一个人才是一个节点。
 * 生卒年为公元年，负数表示公元前，null 表示史料无载（不猜）。
 */
object PersonProfile {

    data class P(
        val name: String,
        val alias: List<String>,
        val birth: Int?,
        val death: Int?,
        val identity: String,
        val state: String,
    )

    private val table: List<P> = listOf(
        P("孔子", listOf("仲尼", "孔丘", "孔夫子", "尼父"), -551, -479, "思想家、教育家，儒家创始人", "鲁"),
        P("孟子", listOf("孟轲", "子舆", "亚圣"), -372, -289, "思想家，儒家代表人物", "邹"),
        P("荀子", listOf("荀况", "孙卿", "荀卿"), -313, -238, "思想家，儒家代表人物", "赵"),
        P("老子", listOf("李耳", "老聃", "伯阳"), -571, -471, "思想家，道家创始人，周守藏室之史", "周"),
        P("庄子", listOf("庄周", "南华真人"), -369, -286, "思想家，道家代表人物", "宋"),
        P("墨子", listOf("墨翟", "墨子"), -468, -376, "思想家，墨家创始人", "宋"),
        P("管子", listOf("管仲", "管夷吾", "仲父"), -723, -645, "齐国相，助桓公成霸", "齐"),
        P("晏子", listOf("晏婴", "晏平仲"), -578, -500, "齐国大夫，历事三君", "齐"),
        P("孙武", listOf("孙子", "孙武子", "长卿"), -545, -470, "军事家，《孙子兵法》作者", "吴"),
        P("孙膑", listOf("孙膑"), -382, -316, "军事家，齐国军师", "齐"),
        P("吴起", listOf("吴起"), -440, -381, "军事家、改革家，楚悼王令尹", "楚"),
        P("商鞅", listOf("卫鞅", "公孙鞅"), -395, -338, "秦国变法主持者，大良造", "秦"),
        P("韩非", listOf("韩非子"), -280, -233, "法家集大成者", "韩"),
        P("苏秦", listOf("苏子", "武安君"), null, -284, "纵横家，合纵六国", "周"),
        P("张仪", listOf("张子"), null, -309, "纵横家，连横事秦", "魏"),
        P("范雎", listOf("范睢", "张禄", "应侯"), null, -255, "秦国相，远交近攻", "魏"),
        P("吕不韦", listOf("文信侯", "吕相"), -292, -235, "秦国相，《吕氏春秋》主编", "卫"),
        P("李斯", listOf("李通古"), -284, -208, "秦朝丞相，法家", "楚"),
        P("白起", listOf("公孙起", "武安君"), null, -257, "秦国名将", "秦"),
        P("王翦", listOf("王翦"), null, null, "秦国名将，灭楚赵", "秦"),
        P("蒙恬", listOf("蒙恬"), null, -210, "秦朝名将，北筑长城", "秦"),
        P("赵盾", listOf("赵宣子", "宣孟"), null, -601, "晋国正卿，执政二十年", "晋"),
        P("赵武", listOf("赵文子", "赵孟"), null, -541, "晋国正卿，主弭兵之会", "晋"),
        P("赵鞅", listOf("赵简子", "志父"), null, -476, "晋国正卿，赵氏宗主", "晋"),
        P("狐偃", listOf("子犯", "舅犯", "狐子"), null, -622, "晋文公舅父、谋臣", "晋"),
        P("先轸", listOf("原轸"), null, -627, "晋国中军元帅，城濮之战", "晋"),
        P("介子推", listOf("介之推", "介推"), null, null, "晋国隐士，割股啖君", "晋"),
        P("荀林父", listOf("中行桓子", "荀伯"), null, -593, "晋国正卿，邲之战主帅", "晋"),
        P("士会", listOf("随武子", "范武子", "士季"), null, null, "晋国正卿", "晋"),
        P("栾书", listOf("栾武子", "栾伯"), null, null, "晋国正卿，鄢陵之战", "晋"),
        P("韩厥", listOf("韩献子"), null, null, "晋国正卿", "晋"),
        P("魏绛", listOf("魏庄子", "魏昭子"), null, null, "晋国大夫，主和戎", "晋"),
        P("智伯", listOf("智瑶", "荀瑶", "智襄子"), null, -453, "晋国正卿，三家灭其族", "晋"),
        P("祁奚", listOf("祁黄羊", "祁大夫"), null, null, "晋国大夫，外举不避仇", "晋"),
        P("子产", listOf("公孙侨", "子美", "国侨"), null, -522, "郑国执政，铸刑书", "郑"),
        P("子皮", listOf("罕虎", "子展"), null, null, "郑国执政，让政于子产", "郑"),
        P("叔向", listOf("羊舌肸", "叔肸"), null, null, "晋国大夫，博识善谏", "晋"),
        P("伍子胥", listOf("伍员", "申胥", "伍胥"), -559, -484, "吴国大夫，助阖闾破楚", "吴"),
        P("申包胥", listOf("王孙包胥", "申包胥"), null, null, "楚国大夫，哭秦庭求救", "楚"),
        P("伯嚭", listOf("伯否", "太宰嚭"), null, -473, "吴国太宰，谗害子胥", "吴"),
        P("文种", listOf("文少禽", "大夫种"), null, -472, "越国大夫，助勾践灭吴", "越"),
        P("范蠡", listOf("少伯", "陶朱公", "鸱夷子皮"), -536, -448, "越国大夫，灭吴后泛舟", "越"),
        P("专诸", listOf("鱄设诸"), null, -515, "刺客，刺吴王僚", "吴"),
        P("要离", listOf("要离"), null, null, "刺客，刺庆忌", "吴"),
        P("西施", listOf("施夷光", "西子"), null, null, "越国美人，献于吴", "越"),
        P("屈原", listOf("屈平", "正则", "灵均", "三闾大夫"), -340, -278, "楚国左徒，《离骚》作者", "楚"),
        P("宋玉", listOf("宋子"), -298, null, "楚国辞赋家", "楚"),
        P("黄歇", listOf("春申君"), null, -238, "楚国令尹，战国四公子", "楚"),
        P("蔺相如", listOf("蔺相如"), null, null, "赵国上卿，完璧归赵", "赵"),
        P("廉颇", listOf("廉将军"), -327, -243, "赵国名将", "赵"),
        P("赵奢", listOf("马服君"), null, null, "赵国名将，阏与之战", "赵"),
        P("李牧", listOf("李牧"), null, -229, "赵国名将，北破匈奴", "赵"),
        P("信陵君", listOf("魏无忌", "公子无忌"), null, -243, "魏国公子，窃符救赵", "魏"),
        P("平原君", listOf("赵胜", "公子胜"), null, -251, "赵国公子，毛遂自荐", "赵"),
        P("孟尝君", listOf("田文", "薛公"), null, -279, "齐国公子，食客三千", "齐"),
        P("荆轲", listOf("荆卿", "庆卿"), null, -227, "刺客，刺秦王", "卫"),
        P("樊於期", listOf("樊於期"), null, -227, "秦国叛将，献首助荆轲", "秦"),
        P("高渐离", listOf("高渐离"), null, null, "燕国乐师，击筑刺秦", "燕"),
        P("项羽", listOf("项籍", "子羽", "西楚霸王"), -232, -202, "西楚霸王", "楚"),
        P("刘邦", listOf("刘季", "沛公", "汉王", "汉高祖"), -256, -195, "汉朝开国皇帝", "沛"),
        P("张良", listOf("子房", "留侯"), null, -186, "汉初谋臣", "韩"),
        P("萧何", listOf("萧相国", "酂侯"), null, -193, "汉初丞相", "沛"),
        P("韩信", listOf("韩王孙", "淮阴侯"), null, -196, "汉初名将，破赵下齐", "楚"),
        P("陈平", listOf("陈丞相", "曲逆侯"), null, -178, "汉初丞相", "魏"),
        P("曹参", listOf("曹相国", "平阳侯"), null, -190, "汉初丞相，萧规曹随", "沛"),
        P("樊哙", listOf("樊将军", "舞阳侯"), null, -189, "汉初猛将，鸿门护主", "沛"),
        P("英布", listOf("黥布", "淮南王"), null, -195, "汉初诸侯王，九江王", "六"),
        P("彭越", listOf("彭仲", "梁王"), null, -196, "汉初诸侯王", "昌邑"),
        P("陈胜", listOf("陈涉", "胜"), null, -208, "大泽乡起义领袖，张楚王", "阳城"),
        P("吴广", listOf("吴叔"), null, -208, "大泽乡起义领袖，假王", "阳夏"),
        P("范增", listOf("亚父"), -278, -204, "项羽谋士", "居巢"),
        P("司马迁", listOf("子长", "太史公"), -145, -86, "西汉史学家，《史记》作者", "夏阳"),
        P("司马相如", listOf("长卿"), -179, -117, "西汉辞赋家", "蜀"),
        P("董仲舒", listOf("董子"), -179, -104, "西汉思想家，天人三策", "广川"),
        P("贾谊", listOf("贾生", "贾长沙"), -200, -168, "西汉政论家，《过秦论》", "洛阳"),
        P("晁错", listOf("晁错"), -200, -154, "西汉政论家，削藩", "颍川"),
        P("东方朔", listOf("曼倩"), -154, -93, "西汉辞赋家，滑稽之雄", "厌次"),
        P("霍去病", listOf("霍嫖姚", "冠军侯"), -140, -117, "西汉名将，封狼居胥", "河东"),
        P("卫青", listOf("卫仲卿", "长平侯"), null, -106, "西汉名将，七击匈奴", "河东"),
        P("李广", listOf("李将军", "飞将军"), null, -119, "西汉名将", "陇西"),
        P("苏武", listOf("苏子卿"), -140, -60, "西汉使臣，牧羊十九年", "杜陵"),
        P("曹操", listOf("孟德", "魏武帝", "曹公", "阿瞒"), 155, 220, "魏王，汉丞相", "沛国"),
        P("刘备", listOf("玄德", "汉昭烈帝", "刘皇叔"), 161, 223, "蜀汉开国皇帝", "涿郡"),
        P("孙权", listOf("仲谋", "吴大帝"), 182, 252, "吴国开国皇帝", "吴郡"),
        P("诸葛亮", listOf("孔明", "卧龙", "武乡侯"), 181, 234, "蜀汉丞相", "琅琊"),
        P("关羽", listOf("云长", "关公", "汉寿亭侯"), null, 220, "蜀汉名将", "河东"),
        P("张飞", listOf("翼德", "益德"), null, 221, "蜀汉名将", "涿郡"),
        P("赵云", listOf("子龙"), null, 229, "蜀汉名将", "常山"),
        P("马超", listOf("孟起"), 176, 222, "蜀汉名将", "扶风"),
        P("黄忠", listOf("汉升"), null, 220, "蜀汉名将", "南阳"),
        P("庞统", listOf("士元", "凤雏"), 179, 214, "刘备谋士", "襄阳"),
        P("法正", listOf("孝直"), 176, 220, "刘备谋士", "扶风"),
        P("姜维", listOf("伯约"), 202, 264, "蜀汉大将军", "天水"),
        P("周瑜", listOf("公瑾", "周郎"), 175, 210, "东吴都督，赤壁破曹", "庐江"),
        P("鲁肃", listOf("子敬"), 172, 217, "东吴都督", "临淮"),
        P("吕蒙", listOf("子明", "吴下阿蒙"), 178, 220, "东吴都督，白衣渡江", "汝南"),
        P("陆逊", listOf("伯言"), 183, 245, "东吴丞相，夷陵破蜀", "吴郡"),
        P("甘宁", listOf("兴霸"), null, null, "东吴名将", "巴郡"),
        P("司马懿", listOf("仲达", "晋宣帝"), 179, 251, "魏国太尉，晋室奠基", "河内"),
        P("陶渊明", listOf("陶潜", "元亮", "五柳先生"), 365, 427, "东晋诗人，田园诗宗", "浔阳"),
        P("谢安", listOf("安石", "东山再起"), 320, 385, "东晋宰相，淝水之战", "陈郡"),
        P("王羲之", listOf("逸少", "右军", "书圣"), 303, 361, "东晋书法家", "琅琊"),
        P("顾恺之", listOf("长康", "虎头"), 348, 409, "东晋画家", "晋陵"),
        P("祖冲之", listOf("文远"), 429, 500, "南朝数学家，算圆周率", "范阳"),
        P("郦道元", listOf("善长"), 472, 527, "北魏地理学家，《水经注》", "范阳"),
        P("李白", listOf("太白", "青莲居士", "谪仙人"), 701, 762, "唐代诗人，诗仙", "陇西"),
        P("杜甫", listOf("子美", "少陵野老", "杜工部"), 712, 770, "唐代诗人，诗圣", "襄阳"),
        P("白居易", listOf("乐天", "香山居士", "醉吟先生"), 772, 846, "唐代诗人", "太原"),
        P("韩愈", listOf("退之", "韩昌黎", "昌黎先生"), 768, 824, "唐代文学家，古文运动", "河阳"),
        P("柳宗元", listOf("子厚", "柳河东", "柳州"), 773, 819, "唐代文学家", "河东"),
        P("刘禹锡", listOf("梦得", "刘宾客"), 772, 842, "唐代诗人", "洛阳"),
        P("元稹", listOf("微之"), 779, 831, "唐代诗人", "河南"),
        P("李贺", listOf("长吉", "诗鬼"), 790, 816, "唐代诗人", "福昌"),
        P("杜牧", listOf("牧之", "樊川居士"), 803, 852, "唐代诗人", "京兆"),
        P("李商隐", listOf("义山", "玉溪生"), 813, 858, "唐代诗人", "怀州"),
        P("苏轼", listOf("子瞻", "东坡居士", "苏东坡"), 1037, 1101, "北宋文学家", "眉山"),
        P("苏辙", listOf("子由", "颍滨遗老"), 1039, 1112, "北宋文学家", "眉山"),
        P("苏洵", listOf("明允", "老泉"), 1009, 1066, "北宋文学家", "眉山"),
        P("王安石", listOf("介甫", "半山", "王荆公"), 1021, 1086, "北宋政治家，变法", "临川"),
        P("欧阳修", listOf("永叔", "醉翁", "六一居士"), 1007, 1072, "北宋文学家", "吉安"),
        P("曾巩", listOf("子固", "南丰先生"), 1019, 1083, "北宋文学家", "南丰"),
        P("司马光", listOf("君实", "涑水先生"), 1019, 1086, "北宋史学家，《资治通鉴》", "陕州"),
        P("范仲淹", listOf("希文", "文正"), 989, 1052, "北宋政治家", "苏州"),
        P("辛弃疾", listOf("幼安", "稼轩"), 1140, 1207, "南宋词人", "济南"),
        P("李清照", listOf("易安", "漱玉词人"), 1084, 1155, "南宋词人", "济南"),
        P("陆游", listOf("务观", "放翁"), 1125, 1210, "南宋诗人", "山阴"),
    )


    // ---- 外部档案（CBDB 8.8 万条，繁体名，唐以后为主）----

    @Volatile
    private var cbdb: Map<String, P> = emptyMap()

    /** 繁 → 简，只用来做查询归一键 */
    private var tsMap: Map<Char, Char> = emptyMap()

    @Volatile
    private var extLoaded = false

    fun ensureLoaded(context: android.content.Context) {
        if (extLoaded) return
        synchronized(this) {
            if (extLoaded) return
            runCatching {
                val lines = context.assets.open("ts.txt").bufferedReader().use { it.readLines() }
                val m = HashMap<Char, Char>(5200)
                for (ln in lines) {
                    if (ln.isEmpty() || ln.startsWith("#")) continue
                    val parts = ln.split('\t')
                    if (parts.size >= 2 && parts[0].length == 1) {
                        val simp = parts[1].substringBefore(' ')
                        if (simp.length == 1) m[parts[0][0]] = simp[0]
                    }
                }
                tsMap = m
            }
            runCatching {
                val text = context.assets.open("person.txt").bufferedReader().use { it.readText() }
                val m = HashMap<String, P>(90000)
                for (ln in text.lineSequence()) {
                    if (ln.isBlank()) continue
                    val f = ln.split('\t')
                    if (f.size < 6) continue
                    val name = f[0]
                    if (name.length < 2) continue
                    val aliases = f[1].split('、').filter { it.length in 2..6 }
                    val b = f[2].toIntOrNull()
                    val d = f[3].toIntOrNull()
                    val prof = P(name, aliases, b, d, f[5], f[4])
                    val key = toSimp(name)
                    if (key !in m) m[key] = prof
                }
                cbdb = m
            }
            extLoaded = true
        }
    }

    private fun toSimp(s: String): String {
        if (tsMap.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(tsMap[c] ?: c)
        return sb.toString()
    }

    /** 抽取词表补先秦段：CBDB 只有漢前与秦漢这几百条 */
    private val extTerms: List<String> by lazy {
        cbdb.values
            .asSequence()
            .filter { it.state == "漢前" || it.state == "秦漢" }
            .map { it.name }
            .filter { it.length in 2..5 }
            .toList()
    }

    private val byName: Map<String, P> by lazy {
        val m = HashMap<String, P>(table.size * 3)
        for (p in table) {
            m[p.name] = p
            for (a in p.alias) m.putIfAbsent(a, p)
        }
        m
    }

    /** 全部称呼，长词在前，供抽取时优先匹配 */
    val allTerms: List<String> by lazy {
        ((table.flatMap { listOf(it.name) + it.alias }) + extTerms)
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { it.length }
    }

    fun find(raw: String): P? = byName[raw]

    fun canonical(raw: String): String = profileOf(raw)?.name ?: raw

    fun profileOf(name: String): P? = byName[name] ?: cbdb[toSimp(name)]

    fun lifeSpan(p: P): String {
        if (p.birth == null && p.death == null) return "生卒无载"
        val b = p.birth?.let { fmt(it) } ?: "?"
        val d = p.death?.let { fmt(it) } ?: "?"
        if (p.birth != null && p.death != null) {
            val age = p.death - p.birth
            return "$b — $d（${age}岁）"
        }
        return "$b — $d"
    }

    private fun fmt(y: Int): String = if (y <= 0) "前${1 - y}" else "$y"
}
