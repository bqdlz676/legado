package io.legado.app.help.artifact.seed

import io.legado.app.help.artifact.entry.ArtifactEntry

/**
 * D16 种子样本表（本片只落表结构 + 每类 3~5 条真实条目；全量数据表归后续数据片）。
 *
 * 为什么种子内置内存表而不读资产文件：本片纯 JVM 可单测，资产解析 / 按需下载归数据片；
 * [io.legado.app.help.artifact.registry.ArtifactRegistration.assetPath] 已预留落盘路径。
 *
 * 来源口径：条目均出自公版古籍 / 出土器名，释义为合成摘要；第三方建筑条目按 CC BY-SA。
 * 每条 [ArtifactEntry.source] 标注篇目；无出处的条目不许进表（09:75）。
 *
 * 键为 [io.legado.app.help.artifact.registry.ArtifactRegistration.id]；既有槽类别
 * （person / place）本表不重复种子，数据归 D2 / D3 原库（09:31 不双轨）。
 */
object ArtifactSeedTable {

    val entries: Map<String, List<ArtifactEntry>> = mapOf(

        // ---------- 草木（plant，《毛诗草木鸟兽虫鱼疏》/《尔雅》） ----------
        "plant" to listOf(
            ArtifactEntry(
                id = "plant-tao", name = "桃", type = "plant",
                headline = "桃 · 花木之属",
                body = "《诗经·周南·桃夭》：「桃之夭夭，灼灼其华。」",
                detail = "桃，蔷薇科李属落叶小乔木，春日花先叶开放；古诗文中常以桃花喻盛年、新嫁。《毛诗草木鸟兽虫鱼疏》《尔雅》均有著录。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("桃花"), source = "《诗经·周南·桃夭》；《毛诗草木鸟兽虫鱼疏》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "plant-liu", name = "柳", type = "plant",
                headline = "柳 · 杨柳之属",
                body = "《诗经·小雅·采薇》：「昔我往矣，杨柳依依。」",
                detail = "柳，杨柳科落叶乔木，枝条柔垂；古诗折柳赠别，「杨柳」在《诗经》中已为征夫怀乡之象。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("杨柳"), source = "《诗经·小雅·采薇》；《尔雅》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "plant-xuancao", name = "萱草", type = "plant",
                headline = "萱草 · 忘忧之草",
                body = "《诗经·卫风·伯兮》：「焉得谖草，言树之背。」谖草即萱草。",
                detail = "萱草，百合科多年生草本，古人以为食之令人忘忧，故又名忘忧草；「谖」即忘。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("谖草", "忘忧草"), source = "《诗经·卫风·伯兮》；《毛诗草木鸟兽虫鱼疏》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "plant-jianjia", name = "蒹葭", type = "plant",
                headline = "蒹葭 · 芦苇之属",
                body = "《诗经·秦风·蒹葭》：「蒹葭苍苍，白露为霜。」",
                detail = "蒹葭，芦苇一类水生草本；蒹为未秀之芦，葭为初生之芦。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("芦苇"), source = "《诗经·秦风·蒹葭》；《尔雅》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 鸟兽（animal，《毛诗草木鸟兽虫鱼疏》/《山海经》） ----------
        "animal" to listOf(
            ArtifactEntry(
                id = "animal-peng", name = "鹏", type = "animal",
                headline = "鹏 · 大鸟之名",
                body = "《庄子·逍遥游》：「北冥有鱼，其名为鲲……化而为鸟，其名为鹏。」",
                detail = "鹏为庄子寓言中的巨鸟，抟扶摇而上九万里；后以鹏程喻远大前程。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("大鹏"), source = "《庄子·逍遥游》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "animal-honghu", name = "鸿鹄", type = "animal",
                headline = "鸿鹄 · 天鹅之属",
                body = "《史记·陈涉世家》：「燕雀安知鸿鹄之志哉。」",
                detail = "鸿鹄即天鹅，善高飞；陈涉以鸿鹄自比，喻有远大志向者。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("天鹅"), source = "《史记·陈涉世家》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "animal-qilin", name = "麒麟", type = "animal",
                headline = "麒麟 · 仁兽之名",
                body = "《诗经·周南·麟之趾》：「麟之趾，振振公子。」",
                detail = "麒麟为古传说仁兽，雄曰麒、雌曰麟；《诗经》以麟趾喻公子仁厚。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("麟"), source = "《诗经·周南·麟之趾》；《毛诗草木鸟兽虫鱼疏》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "animal-fenghuang", name = "凤凰", type = "animal",
                headline = "凤凰 · 瑞鸟之名",
                body = "《诗经·大雅·卷阿》：「凤凰鸣矣，于彼高冈。」",
                detail = "凤凰为古传说瑞鸟，雄曰凤、雌曰凰；传为百鸟之王，世治则现。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("凤皇", "凤"), source = "《诗经·大雅·卷阿》；《山海经》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 药名（medicine，《神农本草经》辑佚 /《本草纲目》） ----------
        "medicine" to listOf(
            ArtifactEntry(
                id = "med-renshen", name = "人参", type = "medicine",
                headline = "人参 · 药名之属",
                body = "《神农本草经》上品：「人参，味甘微寒，主补五脏。」",
                detail = "人参，五加科多年生草本，根入药；《神农本草经》列上品，后世本草沿袭。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("人衔", "鬼盖"), source = "《神农本草经》(辑佚)；《本草纲目》(1596)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "med-gancao", name = "甘草", type = "medicine",
                headline = "甘草 · 药名之属",
                body = "《神农本草经》上品：「甘草，味甘平，主五脏六腑寒热邪气。」",
                detail = "甘草，豆科多年生草本，根状茎入药，方中多用为调和诸药。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("国老"), source = "《神农本草经》(辑佚)；《本草纲目》(1596)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "med-danggui", name = "当归", type = "medicine",
                headline = "当归 · 药名之属",
                body = "《神农本草经》中品：「当归，味甘温，主咳逆上气。」",
                detail = "当归，伞形科多年生草本，根入药，古方妇科常用。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("干归"), source = "《神农本草经》(辑佚)；《本草纲目》(1596)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "med-fuling", name = "茯苓", type = "medicine",
                headline = "茯苓 · 药名之属",
                body = "《神农本草经》上品：「茯苓，味甘平，主胸胁逆气。」",
                detail = "茯苓为多孔菌科真菌寄生于松根的菌核，入药。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("茯菟"), source = "《神农本草经》(辑佚)；《本草纲目》(1596)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名剑（artifact/sword，《古今刀剑录》/《越绝书》） ----------
        "sword" to listOf(
            ArtifactEntry(
                id = "sword-longquan", name = "龙泉", type = "sword",
                headline = "龙泉 · 名剑",
                body = "相传欧冶子、干将铸剑于龙泉溪，剑因溪得名；见《越绝书》《古今刀剑录》。",
                detail = "龙泉剑，古代名剑，后世以龙泉为宝剑泛称；今浙江龙泉沿其名铸剑。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("龙泉剑"), source = "《越绝书》；《古今刀剑录》(陶弘景)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "sword-ganjiang", name = "干将", type = "sword",
                headline = "干将 · 名剑",
                body = "《吴越春秋》载干将、莫邪为吴王铸剑，干将为雄剑。",
                detail = "干将与莫邪为成对名剑，雄曰干将、雌曰莫邪；事见《吴越春秋·阖闾内传》。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("干将剑"), source = "《吴越春秋》；《古今刀剑录》(陶弘景)",
                relatedPlaceId = null, relatedArtifactIds = listOf("sword-moye"), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "sword-moye", name = "莫邪", type = "sword",
                headline = "莫邪 · 名剑",
                body = "干将之雌剑，与干将并称干将莫邪。",
                detail = "莫邪为雌剑名，与雄剑干将成对；典故见《吴越春秋》。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("莫邪剑"), source = "《吴越春秋》；《古今刀剑录》(陶弘景)",
                relatedPlaceId = null, relatedArtifactIds = listOf("sword-ganjiang"), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "sword-yuchang", name = "鱼肠", type = "sword",
                headline = "鱼肠 · 名剑",
                body = "《史记·刺客列传》专诸置匕首于鱼腹中刺吴王僚，即鱼肠剑。",
                detail = "鱼肠为古代短剑 / 匕首名，专诸刺王僚所用；事见《史记·刺客列传·专诸》。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("鱼肠剑"), source = "《史记·刺客列传》；《越绝书》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名马（artifact/horse，《相马经》/《史记》/《三国志》） ----------
        "horse" to listOf(
            ArtifactEntry(
                id = "horse-chitu", name = "赤兔", type = "horse",
                headline = "赤兔 · 名马",
                body = "《三国志》注引《曹瞒传》：「人中有吕布，马中有赤兔。」后归关羽。",
                detail = "赤兔为三国名马，本属吕布，布败后归关羽；「兔」言其驰疾。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("赤兔马"), source = "《三国志·魏书·吕布传》注引《曹瞒传》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "horse-dilu", name = "的卢", type = "horse",
                headline = "的卢 · 名马",
                body = "《三国志·先主传》的卢马，檀溪跃渡救刘备。",
                detail = "的卢为刘备所乘马，额有白斑；襄阳檀溪之役跃水而过，甩掉追兵。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("的卢马"), source = "《三国志·蜀书·先主传》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "horse-jueying", name = "绝影", type = "horse",
                headline = "绝影 · 名马",
                body = "《三国志·魏书》注引《魏书》，曹操乘马绝影，张绣之乱中被流矢所中。",
                detail = "绝影为曹操所乘名马，跑速绝于影子；宛城张绣之乱中中箭，曹操赖其子曹昂让马得脱。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("绝影马"), source = "《三国志·魏书·武帝纪》注引《魏书》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "horse-wuzhui", name = "乌骓", type = "horse",
                headline = "乌骓 · 名马",
                body = "《史记·项羽本纪》项羽骑乌骓马，垓下困围赠亭长。",
                detail = "乌骓为项羽所乘黑马；垓下之围，项羽骏马赠乌江亭长，不忍杀之。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("乌骓马"), source = "《史记·项羽本纪》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名寺(古刹)（architecture/temple，《洛阳伽蓝记》/《高僧传》） ----------
        "temple" to listOf(
            ArtifactEntry(
                id = "temple-baimasi", name = "白马寺", type = "temple",
                headline = "白马寺 · 古刹",
                body = "东汉永平年间建，中国最早官办佛寺之一，位于洛阳。",
                detail = "白马寺在洛阳城东，传为东汉明帝迎西域高僧、以白马驮经而建；中国佛教祖庭之一。relatedPlaceId 为两级跳转挂点，真实 place 主键归 D3 地名库回填。",
                aliases = listOf("白马古寺"), source = "《洛阳伽蓝记》；《高僧传》；Wikipedia 公开历史建筑条目(CC BY-SA)",
                relatedPlaceId = "place-baimasi", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "temple-shaolin", name = "少林寺", type = "temple",
                headline = "少林寺 · 古刹",
                body = "北魏太和年间建，嵩山少室山北麓。",
                detail = "少林寺在河南登封嵩山少室山，北魏太和十九年（495）孝文帝为跋陀所建；后为禅宗祖庭、少林武术发源地。relatedPlaceId 为跳转挂点，主键归 D3 回填。",
                aliases = listOf("少林"), source = "《洛阳伽蓝记》；Wikipedia 公开历史建筑条目(CC BY-SA)",
                relatedPlaceId = "place-shaolin", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "temple-lingyin", name = "灵隐寺", type = "temple",
                headline = "灵隐寺 · 古刹",
                body = "东晋咸和年间慧理禅师建，杭州西湖飞来峰下。",
                detail = "灵隐寺在杭州西湖西北飞来峰旁，东晋咸和元年（326）慧理禅师开山；江南著名古刹。relatedPlaceId 为跳转挂点，主键归 D3 回填。",
                aliases = listOf("云林寺"), source = "《高僧传》；Wikipedia 公开历史建筑条目(CC BY-SA)",
                relatedPlaceId = "place-lingyin", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "temple-famen", name = "法门寺", type = "temple",
                headline = "法门寺 · 古刹",
                body = "东汉初年建，扶风，藏佛指舍利。",
                detail = "法门寺在陕西扶风，传东汉明帝时建；塔下地宫出土佛指舍利与唐代皇室供奉器物。relatedPlaceId 为跳转挂点，主键归 D3 回填。",
                aliases = listOf("法门寺塔"), source = "《洛阳伽蓝记》；Wikipedia 公开历史建筑条目(CC BY-SA)",
                relatedPlaceId = "place-famen", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名道观（architecture/daoist，《云笈七签》） ----------
        "daoist" to listOf(
            ArtifactEntry(
                id = "daoist-baiyun", name = "白云观", type = "daoist",
                headline = "白云观 · 道观",
                body = "唐代天长观沿革，全真道龙门派祖庭，在北京。",
                detail = "白云观在北京西城，前身为唐天长观，金元间改观名；全真道龙门派祖庭。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("白云观道观"), source = "《云笈七签》；地方志乘",
                relatedPlaceId = "place-baiyunguan", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "daoist-shangqing", name = "上清宫", type = "daoist",
                headline = "上清宫 · 道观",
                body = "龙虎山，东汉张道陵修道之所，历代天师道场。",
                detail = "龙虎山在上清镇，传东汉张道陵炼丹修道于此；历代张天师府与上清宫为正一道祖庭。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("龙虎山上清"), source = "《云笈七签》；《汉天师世家》",
                relatedPlaceId = "place-longhushan", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "daoist-louguan", name = "楼观台", type = "daoist",
                headline = "楼观台 · 道观",
                body = "周至，传说老子在此著《道德经》、说经台。",
                detail = "楼观台在陕西周至终南山，传为老子说经处，有说经台；道教最早的宫观之一。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("说经台"), source = "《云笈七签》；《史记·老子韩非列传》",
                relatedPlaceId = "place-louguantai", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "daoist-qingyang", name = "青羊宫", type = "daoist",
                headline = "青羊宫 · 道观",
                body = "成都，唐代重建，道教著名宫观。",
                detail = "青羊宫在成都西南，传为老子西行与尹喜相约处；唐代重建，现存铜羊二尊。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("青羊观"), source = "《云笈七签》；地方志乘",
                relatedPlaceId = "place-qingyanggong", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名楼(台阁)（architecture/tower） ----------
        "tower" to listOf(
            ArtifactEntry(
                id = "tower-huanghe", name = "黄鹤楼", type = "tower",
                headline = "黄鹤楼 · 名楼",
                body = "三国吴黄武年间始建于江夏黄鹄山，历代题咏最著。",
                detail = "黄鹤楼在武汉武昌蛇山（黄鹄山），三国吴黄武二年始建；崔颢、李白题诗最著，历代屡毁屡建。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("黄鹤山楼"), source = "《元和郡县图志》；历代诗文",
                relatedPlaceId = "place-wuhan", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "tower-yueyang", name = "岳阳楼", type = "tower",
                headline = "岳阳楼 · 名楼",
                body = "唐开元年间建，洞庭湖畔，范仲淹《岳阳楼记》显名。",
                detail = "岳阳楼在湖南岳阳洞庭湖畔，唐开元年间张说筑楼；北宋范仲淹作《岳阳楼记》，「先天下之忧而忧」传诵。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("岳阳城楼"), source = "《岳阳楼记》(范仲淹)；地方志乘",
                relatedPlaceId = "place-yueyang", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "tower-tengwang", name = "滕王阁", type = "tower",
                headline = "滕王阁 · 名楼",
                body = "唐永徽年间李元婴建，南昌赣江东岸，王勃《滕王阁序》。",
                detail = "滕王阁在南昌赣江东岸，唐永徽四年滕王李元婴都督洪州时建；王勃作《滕王阁序》，「落霞与孤鹜齐飞」传诵。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("南昌滕王阁"), source = "《滕王阁序》(王勃)；地方志乘",
                relatedPlaceId = "place-nanchang", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "tower-guanque", name = "鹳雀楼", type = "tower",
                headline = "鹳雀楼 · 名楼",
                body = "北周始建，蒲州黄河岸，王之涣「欲穷千里目，更上一层楼」。",
                detail = "鹳雀楼在山西永济蒲州黄河岸，北周时建；因常有鹳雀栖其上得名，王之涣《登鹳雀楼》显名。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("鹳鹊楼"), source = "《登鹳雀楼》(王之涣)；地方志乘",
                relatedPlaceId = "place-yongji", relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名琴(乐器)（artifact/instrument，《琴史》朱长文） ----------
        "instrument" to listOf(
            ArtifactEntry(
                id = "instrument-jiaowei", name = "焦尾", type = "instrument",
                headline = "焦尾 · 名琴",
                body = "《后汉书·蔡邕传》吴人烧桐炊饭，蔡邕闻火声取为琴，尾焦，号焦尾琴。",
                detail = "焦尾为蔡邕所制名琴，以烧剩桐木为材，琴尾尚焦；后世以焦尾泛指良琴。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("焦尾琴"), source = "《后汉书·蔡邕传》；《琴史》(朱长文)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "instrument-lvqi", name = "绿绮", type = "instrument",
                headline = "绿绮 · 名琴",
                body = "司马相如所弹琴名，见《琴史》及传世题咏。",
                detail = "绿绮为汉代名琴，传司马相如以此琴弹《凤求凰》；后世诗文中以绿绮为琴的美称。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("绿绮琴"), source = "《琴史》(朱长文)；《西京杂记》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "instrument-raoliang", name = "绕梁", type = "instrument",
                headline = "绕梁 · 名琴",
                body = "《列子》所载楚琴，韩娥歌余音绕梁，典故一脉。",
                detail = "绕梁为古代名琴名，与「余音绕梁，三日不绝」典故相系；传为楚庄王时琴。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("绕梁琴"), source = "《列子·汤问》；《琴史》(朱长文)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "instrument-haozhong", name = "号钟", type = "instrument",
                headline = "号钟 · 名琴",
                body = "古代名琴，传为伯牙所弹，见《琴史》辑录。",
                detail = "号钟为古代名琴，琴音洪亮如钟；传伯牙弹奏之琴，与绕梁、绿绮、焦尾并称古四大名琴。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("号钟琴"), source = "《琴史》(朱长文)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名鼎(礼器)（artifact/vessel，《金石录》/《史记》） ----------
        "vessel" to listOf(
            ArtifactEntry(
                id = "vessel-houmuwu", name = "后母戊鼎", type = "vessel",
                headline = "后母戊鼎 · 礼器",
                body = "商代晚期青铜方鼎，1939 年安阳殷墟出土，现存国家博物馆。",
                detail = "后母戊鼎（旧称司母戊鼎），商晚期王室青铜祭器，重达八百余公斤，为现存最大商代青铜器。种子释义为公版出土器名辑录合成口径。",
                aliases = listOf("司母戊鼎"), source = "殷墟出土器；《金石录》体例著录",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "vessel-dayu", name = "大盂鼎", type = "vessel",
                headline = "大盂鼎 · 礼器",
                body = "西周早期青铜鼎，康王时器，铭文记册命盂。",
                detail = "大盂鼎为西周康王时青铜鼎，器内壁铭文十九行，记康王册命贵族盂之事；清代出土于郿县。种子释义为公版出土器名辑录合成口径。",
                aliases = listOf("盂鼎"), source = "出土器铭文；《金石录》(赵明诚)体例著录",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "vessel-maogong", name = "毛公鼎", type = "vessel",
                headline = "毛公鼎 · 礼器",
                body = "西周晚期青铜鼎，宣王时器，铭文近五百字，现存最长金文之一。",
                detail = "毛公鼎为西周宣王时青铜鼎，铭文三十二行近五百字，为现存铭文最长的青铜器之一；清代出土于岐山。种子释义为公版出土器名辑录合成口径。",
                aliases = listOf("毛公鼎彝"), source = "出土器铭文；《金石录》(赵明诚)体例著录",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "vessel-siyang", name = "四羊方尊", type = "vessel",
                headline = "四羊方尊 · 礼器",
                body = "商代晚期青铜尊，宁乡出土，四角饰卷角羊首。",
                detail = "四羊方尊为商晚期青铜礼器，1938 年湖南宁乡出土，尊肩四角各铸一卷角羊首，为商代青铜尊精品。种子释义为公版出土器名辑录合成口径。",
                aliases = listOf("四羊尊"), source = "宁乡出土器；《金石录》体例著录",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 星宿(天文)（astronomy，《史记·天官书》/《步天歌》） ----------
        "astronomy" to listOf(
            ArtifactEntry(
                id = "ast-beidou", name = "北斗", type = "star",
                headline = "北斗 · 星宿",
                body = "《史记·天官书》：「北斗七星，所谓璇玑玉衡以齐七政。」",
                detail = "北斗七星属大熊座，古人以斗柄指向定季节、辨方向；天官书列为天象枢机。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("北斗七星"), source = "《史记·天官书》；《步天歌》(丹元子)",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "ast-shen", name = "参宿", type = "constellation",
                headline = "参宿 · 星宿",
                body = "二十八宿之一，参宿七星，《诗经》屡见参星。",
                detail = "参宿为西方白虎七宿之末，七星横列如衡石；与商宿（心宿）此出彼没，故有参商之叹。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("参星"), source = "《诗经》；《史记·天官书》；《步天歌》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "ast-xin", name = "心宿", type = "constellation",
                headline = "心宿 · 星宿",
                body = "东宫苍龙之心，大火星；《诗经·豳风·七月》「七月流火」即大火西流。",
                detail = "心宿为东宫苍龙七宿之一，主星即大火（心宿二，天蝎座 α）；「七月流火」谓大火星西降、暑气渐退。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("大火", "商星"), source = "《诗经·豳风·七月》；《史记·天官书》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "ast-zhinv", name = "织女", type = "star",
                headline = "织女 · 星宿",
                body = "天琴座 α 星，《诗经·小雅·大东》：「跂彼织女，终日七襄。」",
                detail = "织女为天琴座最亮星，与牵牛（牛郎星）隔银河相对；《诗经·大东》已咏及，后世衍为七夕传说。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("织女星"), source = "《诗经·小雅·大东》；《史记·天官书》；《步天歌》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名酒（food/wine，《北山酒经》/《齐民要术》） ----------
        "wine" to listOf(
            ArtifactEntry(
                id = "wine-dukang", name = "杜康", type = "wine",
                headline = "杜康 · 名酒",
                body = "曹操《短歌行》：「何以解忧，唯有杜康。」后世以杜康为酒代称。",
                detail = "杜康传为上古造酒者，后世以杜康名酒、亦为酒之代称；河南汝阳、伊川一带沿其名酿酒。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("杜康酒"), source = "曹操《短歌行》；《北山酒经》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "wine-xinfeng", name = "新丰酒", type = "wine",
                headline = "新丰 · 名酒",
                body = "王维《少年行》：「新丰美酒斗十千，咸阳游侠多少年。」",
                detail = "新丰在今陕西临潼东北，汉唐时所出名酒；唐诗中屡见新丰美酒之咏。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("新丰美酒"), source = "王维《少年行》；《齐民要术》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "wine-tusu", name = "屠苏酒", type = "wine",
                headline = "屠苏 · 名酒",
                body = "《荆楚岁时记》元日饮屠苏，自少及长次第而饮。",
                detail = "屠苏酒为正月元日所饮药酒，古法以大黄、白术、桂枝等浸酿；饮次序自幼及长，贺年少者先长。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("屠苏"), source = "《荆楚岁时记》；《北山酒经》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "wine-juhua", name = "菊花酒", type = "wine",
                headline = "菊花 · 名酒",
                body = "《西京杂记》汉高祖时宫中九月九日饮菊花酒，祈长寿。",
                detail = "菊花酒以菊花浸米酿而成，重阳时节饮之，古俗以求长寿；汉宫廷已有其制。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("菊酒"), source = "《西京杂记》；《齐民要术》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名食（food/dish，《随园食单》/《齐民要术》/《礼记》） ----------
        "dish" to listOf(
            ArtifactEntry(
                id = "dish-bazhen", name = "八珍", type = "dish",
                headline = "八珍 · 名食",
                body = "《周礼·天官》《礼记·内则》所列周代八种珍肴，为古代美食总称。",
                detail = "八珍为周代八种名贵肴馔（炮豚、炮牂、渍、熬等），后用以泛指精美食品。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("周八珍"), source = "《周礼·天官》；《礼记·内则》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "dish-chunghuailu", name = "莼羹鲈脍", type = "dish",
                headline = "莼羹鲈脍 · 名食",
                body = "《世说新语·识鉴》张翰见秋风起，思吴中菰菜、莼羹、鲈鱼脍。",
                detail = "莼羹以莼菜作羹，鲈脍为鲈鱼切片；张翰因思吴中家乡风味弃官归里，后世以莼鲈之思喻乡愁。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("莼鲈之思", "鲈鱼脍"), source = "《世说新语·识鉴》；《齐民要术》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "dish-paotun", name = "炮豚", type = "dish",
                headline = "炮豚 · 名食",
                body = "《礼记·内则》「八珍」之一，煨烤乳猪之法。",
                detail = "炮豚为周代八珍之一，以乳猪裹草涂泥煨烤，剖割后复煎诸油；记于《礼记·内则》。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("烤乳猪古制"), source = "《礼记·内则》；《齐民要术》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "dish-yukuai", name = "鱼脍", type = "dish",
                headline = "鱼脍 · 名食",
                body = "古时生食鱼片，《诗经·小雅·六月》「炰鳖鲜鱼」，后世称脍。",
                detail = "鱼脍为鲜鱼切片生食，周秦两汉常见；《诗经》已有鲜鱼之咏，后世称生鱼片为脍。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("脍", "生鱼片"), source = "《诗经·小雅·六月》；《齐民要术》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 名锦名瓷服饰（artifact/textile，《蜀锦谱》/《陶说》/《天工开物》） ----------
        "textile" to listOf(
            ArtifactEntry(
                id = "textile-shujin", name = "蜀锦", type = "textile",
                headline = "蜀锦 · 名锦",
                body = "四川所织锦，《蜀锦谱》专记；汉唐为西南名产。",
                detail = "蜀锦为蜀地（今成都）所织提花锦，汉唐时与绮縠并重，经丝绸之路西传；《蜀锦谱》记其名色名目。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("蜀锦缎"), source = "《蜀锦谱》；《天工开物》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "textile-yueqingci", name = "越窑青瓷", type = "textile",
                headline = "越窑青瓷 · 名瓷",
                body = "越州窑系青瓷，唐诗「九秋风露越窑开，夺得千峰翠色来」。",
                detail = "越窑在今浙江上虞、余姚一带，唐五代青瓷代表；釉色青翠，陆龟蒙诗咏其夺得千峰翠色。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("越瓷", "秘色瓷"), source = "《陶说》；陆龟蒙诗；《天工开物》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "textile-xingbaici", name = "邢窑白瓷", type = "textile",
                headline = "邢窑白瓷 · 名瓷",
                body = "邢州窑系白瓷，唐代与越窑青瓷并称南青北白。",
                detail = "邢窑在今河北内丘、临城一带，唐代白瓷代表；与越窑青瓷南北并称，天下无贵贱通用之。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("邢瓷"), source = "《陶说》；《天工开物》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "textile-kesi", name = "缂丝", type = "textile",
                headline = "缂丝 · 名织",
                body = "通经断纬丝织品，《天工开物》记其织造之法。",
                detail = "缂丝以通经断纬之法织成，图案正反如一，唐宋以来为高档丝织工艺；《天工开物·乃服》记其制。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("刻丝"), source = "《天工开物·乃服》；《蜀锦谱》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),

        // ---------- 山川风物（landscape，独立表，八源公版） ----------
        "landscape" to listOf(
            ArtifactEntry(
                id = "landscape-taishan", name = "泰山", type = "mountain",
                headline = "泰山 · 五岳东岳",
                body = "《史记·封禅书》载七十二君封禅于泰山。",
                detail = "泰山在山东泰安，五岳东岳，古称岱宗；古帝王封禅大典多在此举行。山川坐标归 D16 landscape 域，城邑坐标归 place 域（09:37）。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("岱宗", "岱山"), source = "《史记·封禅书》；《尔雅·释山》；《禹贡》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "landscape-huanghe", name = "黄河", type = "river",
                headline = "黄河 · 四渎",
                body = "《尔雅·释水》「江淮河济为四渎」；《禹贡》导河积石。",
                detail = "黄河为北方大河，源出积石，东流入海；《禹贡》导河、《尔雅》列为四渎之一。山川坐标归 landscape 域。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("河水", "大河"), source = "《尔雅·释水》；《禹贡》；《水经注》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "landscape-changjiang", name = "长江", type = "river",
                headline = "长江 · 四渎",
                body = "四渎之一，古称江 / 大江，《禹贡》「岷山导江」。",
                detail = "长江为南方大河，古称江、大江；《禹贡》记岷山导江，后世河道沿革见《水经注》。山川坐标归 landscape 域。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("江", "大江"), source = "《禹贡》；《尔雅·释水》；《水经注》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
            ArtifactEntry(
                id = "landscape-dongting", name = "洞庭湖", type = "lake",
                headline = "洞庭湖 · 名湖",
                body = "古九泽之一，《尔雅·释地》「楚有云梦」；洞庭为今名湖。",
                detail = "洞庭湖在湖南北部，古云梦泽遗存水域；《尔雅·释地》列九泽，《水经注》记其湖水脉络。山川坐标归 landscape 域。种子释义为公版古籍辑录合成口径。",
                aliases = listOf("洞庭"), source = "《尔雅·释地》；《水经注》；《方舆纪要》",
                relatedPlaceId = null, relatedArtifactIds = emptyList(), longitude = null, latitude = null,
            ),
        ),
    )

    /** 按登记 id 取种子；无登记 / 表缺失返回空列表，不抛异常。 */
    fun forRegistration(registrationId: String): List<ArtifactEntry> =
        entries[registrationId].orEmpty()
}
