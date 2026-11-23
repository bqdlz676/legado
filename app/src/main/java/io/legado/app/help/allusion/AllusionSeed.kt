package io.legado.app.help.allusion

/**
 * 模块 D4 · 内置种子典故（少量、逐条有公版出处，先跑通；不灌无来路的海量词表）。
 *
 * 收录原则（规格 §升格段「宁可 5000 条有出处，不要 9 万条没来路」）：
 * - 词目 2~4 字，全部能在公版典籍或公版传世诗文中找到本事；
 * - 本事叙述与白话释义为本模块改写，已在 source 末尾以 [AllusionSource.LOCAL_GLOSS] 标明，
 *   不冒充古人原文；
 * - 四字成语若是后世对古籍本事的概括，detail 中如实说明，不在出处里伪托原文；
 * - 后续外部公版资产接入时，走 [AllusionDict] 的注册扩展点，不改动本种子表。
 */
internal object AllusionSeed {

    /** 典籍出处拼接：公版书目篇卷 + 版本口径 + 本地改写标注。 */
    private fun classic(bookChapter: String, authorNote: String): String =
        AllusionSource.join(
            AllusionSource.classic(bookChapter, authorNote),
            AllusionSource.LOCAL_GLOSS,
        )

    /** 诗文出处拼接。 */
    private fun poem(poet: String, title: String): String =
        AllusionSource.join(
            AllusionSource.poem(poet, title),
            AllusionSource.LOCAL_GLOSS,
        )

    val entries: List<AllusionEntry> = buildList {
        // ---- 《史记》 ----
        add(
            AllusionEntry(
                id = "seed:pofuchenzhou",
                word = "破釜沉舟",
                phonetic = "pò fǔ chén zhōu",
                headline = "破釜沉舟",
                body = "项羽渡河救赵，砸破饭锅、凿沉船只，示士卒必死不退。",
                detail = "项羽引兵渡漳水救赵，沉船破釜、焚烧庐舍，只携三日粮，" +
                    "以示全军有进无退。遂大破秦军于巨鹿。\n" +
                    "《史记·项羽本纪》：「项羽乃悉引兵渡河，皆沉船，破釜甑，烧庐舍，" +
                    "持三日粮，以示士卒必死，无一还心。」",
                source = classic("《史记·项羽本纪》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:simianchuge",
                word = "四面楚歌",
                phonetic = "sì miàn chǔ gē",
                headline = "四面楚歌",
                body = "垓下被围，夜闻汉军四面皆唱楚歌，项王疑楚地尽失。",
                detail = "项王军壁垓下，兵少食尽，夜闻汉营四面楚歌，大惊以为汉已尽得楚地，" +
                    "虞姬和歌，项王突围至乌江自刎。\n" +
                    "《史记·项羽本纪》：「项王军壁垓下，兵少食尽，汉军及诸侯兵围之数重。" +
                    "夜闻汉军四面皆楚歌，项王乃大惊曰：『汉皆已得楚乎？是何楚人之多也！』」",
                source = classic("《史记·项羽本纪》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:hongmenyan",
                word = "鸿门宴",
                phonetic = "hóng mén yàn",
                headline = "鸿门宴",
                body = "项羽于鸿门设宴，范增席间欲除刘邦，刘邦借故脱身。",
                detail = "刘邦先入关中，项羽驻军鸿门。范增谋于宴上刺刘邦，项庄舞剑助兴，" +
                    "项伯拔剑相护；樊哙闯帐，刘邦借如厕脱身归营。\n" +
                    "《史记·项羽本纪》：「沛公旦日从百余骑来见项王……项王即日因留沛公与饮。」",
                source = classic("《史记·项羽本纪》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:xiangzhuangwujiang",
                word = "项庄舞剑",
                phonetic = "xiàng zhuāng wǔ jiàn",
                headline = "项庄舞剑",
                body = "鸿门宴上项庄拔剑起舞，用意常在刺杀沛公。",
                detail = "范增席间召项庄以舞剑为名刺刘邦，项伯亦起舞，以身翼蔽沛公，" +
                    "项庄不得下手。后世喻言行别有所图。\n" +
                    "《史记·项羽本纪》：「项庄拔剑起舞，项伯亦拔剑起舞，常以身翼蔽沛公，" +
                    "庄不得击。」",
                source = classic("《史记·项羽本纪》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:woxinchangdan",
                word = "卧薪尝胆",
                phonetic = "wò xīn cháng dǎn",
                headline = "卧薪尝胆",
                body = "越王勾践归国后苦身焦思，置胆于坐，饮食卧起皆尝胆，志在复仇。",
                detail = "勾践自吴返越，苦身焦思，悬胆于座，坐卧仰胆、饮食尝胆，" +
                    "终灭吴雪耻。按：《史记·越王勾践世家》仅载「尝胆」，" +
                    "「卧薪」之说为后世概括，此处如实标明。\n" +
                    "《史记·越王勾践世家》：「越王勾践反国，乃苦身焦思，置胆于坐，" +
                    "坐卧即仰胆，饮食亦尝胆也。」",
                source = classic("《史记·越王勾践世家》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:wanbiguizhao",
                word = "完璧归赵",
                phonetic = "wán bì guī zhào",
                headline = "完璧归赵",
                body = "蔺相如奉和氏璧入秦，见秦王无意偿城，终持璧全归赵国。",
                detail = "秦昭王愿以十五城易赵和氏璧，蔺相如奉璧入秦，察秦王无偿城之意，" +
                    "持璧倚柱怒斥，后遣从者怀璧间道归赵，自己留秦应对。\n" +
                    "《史记·廉颇蔺相如列传》：「城入赵而璧留秦；城不入，臣请完璧归赵。」",
                source = classic("《史记·廉颇蔺相如列传》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:fujingqingzui",
                word = "负荆请罪",
                phonetic = "fù jīng qǐng zuì",
                headline = "负荆请罪",
                body = "廉颇自知失礼，肉袒背负荆条，登门向蔺相如谢罪。",
                detail = "廉颇不服蔺相如位在己上，蔺以国事为先避让，廉颇闻之感悟，" +
                    "肉袒负荆至门谢罪，二人结为刎颈之交。\n" +
                    "《史记·廉颇蔺相如列传》：「廉颇闻之，肉袒负荆，因宾客至蔺相如门谢罪。」",
                source = classic("《史记·廉颇蔺相如列传》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:zhishangtanbing",
                word = "纸上谈兵",
                phonetic = "zhǐ shàng tán bīng",
                headline = "纸上谈兵",
                body = "赵括空谈兵法不知应变，长平一战大败于秦。",
                detail = "赵括少时学兵法，言兵事天下莫能当，然无实战经验。长平代廉颇为将，" +
                    "被秦将白起击破，赵卒四十万坑死。按：四字为后世对赵括事的概括，" +
                    "《史记》原文未用「纸上」一词。\n" +
                    "《史记·廉颇蔺相如列传》：「赵括自少时学兵法，言兵事，以天下莫能当。」",
                source = classic("《史记·廉颇蔺相如列传》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:maosuizijian",
                word = "毛遂自荐",
                phonetic = "máo suì zì jiàn",
                headline = "毛遂自荐",
                body = "平原君选食客赴楚求救，毛遂主动自荐随行，按剑逼楚定纵。",
                detail = "秦围邯郸，平原君欲选二十人赴楚，得十九人，毛遂自荐补缺。" +
                    "至楚，平原君与楚言纵日中不决，毛遂按剑上阶，陈说利害，" +
                    "楚王遂定合纵。\n" +
                    "《史记·平原君虞卿列传》：「门下有毛遂者，前，自赞于平原君曰：" +
                    "『遂闻君将合从于楚……』」",
                source = classic("《史记·平原君虞卿列传》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:tuoyingerchu",
                word = "脱颖而出",
                phonetic = "tuō yǐng ér chū",
                headline = "脱颖而出",
                body = "毛遂自比处囊之锥，锥尖终将透囊而出。",
                detail = "平原君疑毛遂无能，毛遂对以锥处囊中，其颖立见；若早得处囊，" +
                    "整个锥子都会脱出，不止露尖。后喻人才显露头角。\n" +
                    "《史记·平原君虞卿列传》：「使遂蚤得处囊中，乃颖脱而出，" +
                    "非特其末见而已。」",
                source = classic("《史记·平原君虞卿列传》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:weibiansanjue",
                word = "韦编三绝",
                phonetic = "wéi biān sān jué",
                headline = "韦编三绝",
                body = "孔子晚年读《易》，编联竹简的皮绳多次磨断。",
                detail = "孔子晚而喜《易》，反复研读，编竹简的韦绳断过三次，" +
                    "故曰韦编三绝。后喻读书勤奋。\n" +
                    "《史记·孔子世家》：「读《易》，韦编三绝。」",
                source = classic("《史记·孔子世家》", "西汉·司马迁撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:tuqiongbixian",
                word = "图穷匕见",
                phonetic = "tú qióng bǐ xiàn",
                headline = "图穷匕见",
                body = "荆轲献督亢地图，图卷展到尽头，藏在图中的匕首现出。",
                detail = "荆轲携秦叛将樊於期首级与督亢地图入秦，图中卷藏徐夫人匕首。" +
                    "秦王发图，图尽而匕首见，荆轲逐秦王于殿上。事亦见《战国策·燕策三》。\n" +
                    "《史记·刺客列传》：「轲既取图奉之，发图，图穷而匕首见。」",
                source = classic("《史记·刺客列传》", "西汉·司马迁撰"),
            ),
        )

        // ---- 《左传》 ----
        add(
            AllusionEntry(
                id = "seed:tuibisanshe",
                word = "退避三舍",
                phonetic = "tuì bì sān shè",
                headline = "退避三舍",
                body = "晋文公许诺晋楚若战于中原，先退九十里以报楚成王礼遇。",
                detail = "晋公子重耳流亡过楚，楚成王以礼相待，问何以报。对曰晋楚治兵" +
                    "遇于中原，当辟君三舍（三十里为一舍，三舍九十里）。后城濮之战，" +
                    "晋军果退三舍。\n" +
                    "《左传·僖公二十三年》：「子女玉帛则君有之……若以君之灵，得反晋国，" +
                    "晋楚治兵，遇于中原，其辟君三舍。」",
                source = classic("《左传·僖公二十三年》", "春秋·左丘明传"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:chunwangchihan",
                word = "唇亡齿寒",
                phonetic = "chún wáng chǐ hán",
                headline = "唇亡齿寒",
                body = "宫之奇谏虞公：虢虞相依，如唇与齿，唇没了齿便受寒。",
                detail = "晋欲假道于虞伐虢，宫之奇谏曰虢为虞之外表，虢亡虞必从之，" +
                    "以辅车相依、唇亡齿寒为喻。虞公不听，终为晋所灭。\n" +
                    "《左传·僖公五年》：「谚所谓『辅车相依，唇亡齿寒』者，其虞虢之谓也。」",
                source = classic("《左传·僖公五年》", "春秋·左丘明传"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:jiadaofaguo",
                word = "假道伐虢",
                phonetic = "jiǎ dào fá guó",
                headline = "假道伐虢",
                body = "晋国借道虞国攻打虢国，虢灭之后回师顺手灭虞。",
                detail = "晋荀息请以屈产之乘、垂棘之璧假道于虞伐虢。虢亡，晋师还驻于虞，" +
                    "遂袭灭虞，虞公与宫之奇皆被虏。\n" +
                    "《左传·僖公二年》：「晋荀息请以屈产之乘与垂棘之璧，假道于虞以伐虢。」",
                source = classic("《左传·僖公二年》《僖公五年》", "春秋·左丘明传"),
            ),
        )

        // ---- 《战国策》 ----
        add(
            AllusionEntry(
                id = "seed:hujiahuwei",
                word = "狐假虎威",
                phonetic = "hú jiǎ hǔ wēi",
                headline = "狐假虎威",
                body = "虎得狐，狐托天帝之命长百兽，虎随狐行，百兽皆走，虎以为畏狐。",
                detail = "虎求百兽食之，得狐。狐曰天帝使我长百兽，子随我后观百兽之见我" +
                    "敢不走乎。虎以为然，与之行；兽皆走，虎不知兽畏己，以为畏狐。\n" +
                    "《战国策·楚策一》：「虎以为然，故遂与之行。兽见之皆走。" +
                    "虎不知兽畏己而走也，以为畏狐也。」",
                source = classic("《战国策·楚策一》", "西汉·刘向辑"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:huashetianzu",
                word = "画蛇添足",
                phonetic = "huà shé tiān zú",
                headline = "画蛇添足",
                body = "先画成蛇的人偏要为蛇添脚，脚未成而酒被他人夺去。",
                detail = "楚祠者赐舍人卮酒，众人画地为蛇，先成者饮。一人蛇先成，" +
                    "左手持卮右手画蛇曰吾能为之足；足未成，一人蛇成，夺其卮。\n" +
                    "《战国策·齐策二》：「蛇固无足，子安能为之足？」",
                source = classic("《战国策·齐策二》", "西汉·刘向辑"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:yangyangbulao",
                word = "亡羊补牢",
                phonetic = "wáng yáng bǔ láo",
                headline = "亡羊补牢",
                body = "丢了羊再修补羊圈，为时未晚。",
                detail = "庄辛谏楚襄王，曰见兔顾犬未为晚，亡羊补牢未为迟；" +
                    "喻出了及时补救尚不为晚。\n" +
                    "《战国策·楚策四》：「见兔而顾犬，未为晚也；亡羊而补牢，未为迟也。」",
                source = classic("《战国策·楚策四》", "西汉·刘向辑"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:jiaotusanku",
                word = "狡兔三窟",
                phonetic = "jiǎo tù sān kū",
                headline = "狡兔三窟",
                body = "冯谖谓孟尝君：狡兔有三窟仅得免死，今君只有一窟，未可高枕。",
                detail = "冯谖为孟尝君焚券市义，又经营梁王聘问，使薛地立庙，" +
                    "为孟尝君凿就三窟，孟尝君赖以安枕。\n" +
                    "《战国策·齐策四》：「狡兔有三窟，仅得免其死耳；今君有一窟，" +
                    "未得高枕而卧也。」",
                source = classic("《战国策·齐策四》", "西汉·刘向辑"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:yubangxiangzheng",
                word = "鹬蚌相争",
                phonetic = "yù bàng xiāng zhēng",
                headline = "鹬蚌相争",
                body = "鹬啄蚌肉，蚌合箝鹬喙，互不相让，渔翁一并擒获。",
                detail = "苏代说赵惠王勿伐燕，喻蚌方出曝，鹬啄其肉，蚌合而箝其喙，" +
                    "两者不肯相舍，渔者得而并禽之。\n" +
                    "《战国策·燕策二》：「两者不肯相舍，渔者得而并禽之。」",
                source = classic("《战国策·燕策二》", "西汉·刘向辑"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:xuanliangcigu",
                word = "悬梁刺股",
                phonetic = "xuán liáng cì gǔ",
                headline = "悬梁刺股",
                body = "孙敬悬发于梁防瞌睡，苏秦引锥刺股提神，皆苦读自励。",
                detail = "两事合成一联苦读典故：刺股出《战国策·秦策一》，苏秦读书欲睡，" +
                    "引锥自刺其股；悬梁出《太平御览》引《汉书》，孙敬好学，晨夕不休，" +
                    "瞌睡时以绳系头悬屋梁。\n" +
                    "《战国策·秦策一》：「读书欲睡，引锥自刺其股，血流至足。」",
                source = AllusionSource.join(
                    AllusionSource.classic("《战国策·秦策一》", "西汉·刘向辑"),
                    AllusionSource.classic("《太平御览》引《汉书》孙敬事", "宋·李昉等辑，公版类书"),
                    AllusionSource.LOCAL_GLOSS,
                ),
            ),
        )

        // ---- 《韩非子》 ----
        add(
            AllusionEntry(
                id = "seed:laomashitu",
                word = "老马识途",
                phonetic = "lǎo mǎ shí tú",
                headline = "老马识途",
                body = "管仲伐孤竹迷道，放老马随行，循其归路得脱。",
                detail = "管仲、隰朋从桓公伐孤竹，春往冬返，迷惑失道。管仲曰老马之智可用，" +
                    "乃放老马而随之，遂得道。\n" +
                    "《韩非子·说林上》：「老马之智可用也。」",
                source = classic("《韩非子·说林上》", "战国·韩非撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:lanyuchongshu",
                word = "滥竽充数",
                phonetic = "làn yú chōng shù",
                headline = "滥竽充数",
                body = "南郭处士混在三百人吹竽队里充数，湣王好独奏，只得逃走。",
                detail = "齐宣王使人吹竽必三百人，南郭处士请为王吹竽，廪食数百人。" +
                    "宣王死，湣王立，好一一听之，处士逃。\n" +
                    "《韩非子·内储说上》：「南郭处士请为王吹竽，宣王说之，廪食以数百人。" +
                    "宣王死，湣王立，好一一听之，处士逃。」",
                source = classic("《韩非子·内储说上》", "战国·韩非撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:shouzhudaitu",
                word = "守株待兔",
                phonetic = "shǒu zhū dài tù",
                headline = "守株待兔",
                body = "耕者见兔触树桩而死，放下农具守着树桩，冀望再得兔。",
                detail = "宋人有耕者，田中有株，兔走触株折颈而死，因释其耒而守株，" +
                    "冀复得兔，身为宋国笑。韩非以此讽守旧不知变通。\n" +
                    "《韩非子·五蠹》：「兔走触株，折颈而死。因释其耒而守株，冀复得兔。」",
                source = classic("《韩非子·五蠹》", "战国·韩非撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:zixiangmaodun",
                word = "自相矛盾",
                phonetic = "zì xiāng máo dùn",
                headline = "自相矛盾",
                body = "楚人夸盾坚不可陷，又夸矛利无不陷，被人问以矛陷盾而不能应。",
                detail = "楚人鬻盾与矛，誉盾曰物莫能陷，誉矛曰于物无不陷。或曰以子之矛" +
                    "陷子之盾何如，其人弗能应。\n" +
                    "《韩非子·难一》：「吾盾之坚，物莫能陷也……吾矛之利，于物无不陷也。」",
                source = classic("《韩非子·难一》", "战国·韩非撰"),
            ),
        )

        // ---- 《孟子》 ----
        add(
            AllusionEntry(
                id = "seed:yamiaozhuzhang",
                word = "揠苗助长",
                phonetic = "yà miáo zhù zhǎng",
                headline = "揠苗助长",
                body = "宋人嫌苗长得慢，把苗一棵棵拔高，苗随即枯萎。",
                detail = "宋人闵其苗之不长而揠之，芒芒然归，谓其子今日病矣，予助苗长矣；" +
                    "其子趋往视之，苗则槁矣。孟子以此喻养气助长之害。\n" +
                    "《孟子·公孙丑上》：「宋人有闵其苗之不长而揠之者……苗则槁矣。」",
                source = classic("《孟子·公孙丑上》", "战国·孟轲撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:yuanmuqiuyu",
                word = "缘木求鱼",
                phonetic = "yuán mù qiú yú",
                headline = "缘木求鱼",
                body = "孟子讥齐宣王以武力求霸，犹爬上树去找鱼。",
                detail = "齐宣王欲辟土地朝秦楚，孟子对以若所为求若所欲，犹缘木而求鱼也，" +
                    "且缘木求鱼虽不得鱼无后灾，以武力求霸则后灾必至。\n" +
                    "《孟子·梁惠王上》：「以若所为，求若所欲，犹缘木而求鱼也。」",
                source = classic("《孟子·梁惠王上》", "战国·孟轲撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:beishuichexin",
                word = "杯水车薪",
                phonetic = "bēi shuǐ chē xīn",
                headline = "杯水车薪",
                body = "一杯水救不起一车柴草的火，喻力量太小无济于事。",
                detail = "孟子论仁，谓今之为仁者，犹以一杯水救一车薪之火，火不灭，" +
                    "则谓之水不胜火，此又与于不仁之甚者也。\n" +
                    "《孟子·告子上》：「今之为仁者，犹以一杯水救一车薪之火也。」",
                source = classic("《孟子·告子上》", "战国·孟轲撰"),
            ),
        )

        // ---- 《庄子》 ----
        add(
            AllusionEntry(
                id = "seed:handanxuebu",
                word = "邯郸学步",
                phonetic = "hán dān xué bù",
                headline = "邯郸学步",
                body = "寿陵少年去邯郸学走路，没学成步法，反倒忘了自己原来怎么走。",
                detail = "庄子以寿陵余子学行于邯郸为喻：未得赵国步态之能，又失其故行，" +
                    "直匍匐而归。喻模仿不成反失本有。\n" +
                    "《庄子·秋水》：「未得国能，又失其故行矣，直匍匐而归耳。」",
                source = classic("《庄子·秋水》", "战国·庄周撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:jingdizhiwa",
                word = "井底之蛙",
                phonetic = "jǐng dǐ zhī wā",
                headline = "井底之蛙",
                body = "井底之蛙被居所拘限，无法同它谈论大海。",
                detail = "井蛙拘于虚（所居之井），夏虫笃于时，曲士束于教；" +
                    "庄子以此喻见识受环境局限。\n" +
                    "《庄子·秋水》：「井蛙不可以语于海者，拘于虚也。」",
                source = classic("《庄子·秋水》", "战国·庄周撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:paodingjieniu",
                word = "庖丁解牛",
                phonetic = "páo dīng jiě niú",
                headline = "庖丁解牛",
                body = "庖丁宰牛凭筋骨肌理下刀，游刃有余，技艺进于道。",
                detail = "庖丁为文惠君解牛，手之所触肩之所倚，砉然向然；其刀十九年解数千牛，" +
                    "若新发于硎。所好者道，进乎技矣。文惠君由此得养生之理。\n" +
                    "《庄子·养生主》：「臣之所好者道也，进乎技矣。」",
                source = classic("《庄子·养生主》", "战国·庄周撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:tangbidangche",
                word = "螳臂当车",
                phonetic = "táng bì dāng chē",
                headline = "螳臂当车",
                body = "螳螂举起臂膀去挡车轮，不知自己力不能胜。",
                detail = "齐庄公出猎，一螳螂举足将搏其轮，问此何虫，御曰螳蜋；" +
                    "其为虫知进而不知却，不量力而轻敌。庄子亦用以喻不胜任。\n" +
                    "《庄子·人间世》：「汝不知夫螳螂乎，怒其臂以当车辙，" +
                    "不知其不胜任也。」",
                source = classic("《庄子·人间世》", "战国·庄周撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:dongshipinpin",
                word = "东施效颦",
                phonetic = "dōng shī xiào pín",
                headline = "东施效颦",
                body = "丑人模仿西施捧心皱眉，乡里见了纷纷避走。",
                detail = "西施病心而颦其里，里之丑人见而美之，归亦捧心而颦其里；" +
                    "富人见之坚闭门，贫人见之挈妻子而去。庄子以此喻不明所以而盲从模仿。\n" +
                    "《庄子·天运》：「其里之丑人见而美之，归亦捧心而颦其里。」",
                source = classic("《庄子·天运》", "战国·庄周撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:wangyangxingsan",
                word = "望洋兴叹",
                phonetic = "wàng yáng xīng tàn",
                headline = "望洋兴叹",
                body = "河伯见北海之广阔，才旋其面目，望洋向海神而叹。",
                detail = "秋水时至，百川灌河，河伯欣然自喜；顺流东行，见北海不见水端，" +
                    "始旋其面目，望洋向若而叹，自笑于大方之家。\n" +
                    "《庄子·秋水》：「于是焉河伯始旋其面目，望洋向若而叹。」",
                source = classic("《庄子·秋水》", "战国·庄周撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:pengchengwanli",
                word = "鹏程万里",
                phonetic = "péng chéng wàn lǐ",
                headline = "鹏程万里",
                body = "大鹏徙往南冥，击水三千里，乘风直上九万里。",
                detail = "北冥有鱼化为鹏，其翼若垂天之云；鹏之徙于南冥，水击三千里，" +
                    "抟扶摇而上者九万里。后喻前程远大。\n" +
                    "《庄子·逍遥游》：「鹏之徙于南冥也，水击三千里，抟扶摇而上者九万里。」",
                source = classic("《庄子·逍遥游》", "战国·庄周撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:zhaosanmusi",
                word = "朝三暮四",
                phonetic = "zhāo sān mù sì",
                headline = "朝三暮四",
                body = "狙公喂橡子，朝三暮四众狙皆怒，朝四暮三众狙皆悦。",
                detail = "宋有狙公赋芧，曰朝三而暮四，众狙皆怒；曰然则朝四而暮三，" +
                    "众狙皆悦。名实未亏而喜怒为用，庄子以此喻机巧与人心。\n" +
                    "《庄子·齐物论》：「朝三而暮四……然则朝四而暮三。」",
                source = classic("《庄子·齐物论》", "战国·庄周撰"),
            ),
        )

        // ---- 《吕氏春秋》 ----
        add(
            AllusionEntry(
                id = "seed:kezhouqiujian",
                word = "刻舟求剑",
                phonetic = "kè zhōu qiú jiàn",
                headline = "刻舟求剑",
                body = "楚人剑坠江中，在船舷刻下记号，等船停了再从记号处下水找剑。",
                detail = "楚人涉江，剑自舟中坠水，遽契其舟，曰是吾剑之所从坠。舟止，" +
                    "从其所契者入水求之。舟已行而剑不行，求剑若此不亦惑乎。\n" +
                    "《吕氏春秋·察今》：「遽契其舟，曰：『是吾剑之所从坠。』」",
                source = classic("《吕氏春秋·察今》", "战国·吕不韦门客撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:yanerdaoling",
                word = "掩耳盗铃",
                phonetic = "yǎn ěr dào líng",
                headline = "掩耳盗铃",
                body = "盗钟之人怕人听见，捂住自己耳朵，以为别人也听不到。",
                detail = "《吕氏春秋·自知》原文为盗钟：范氏之亡，百姓有得钟者，欲负而走，" +
                    "钟大不可负，以椎毁之，钟况然有音；恐人闻之夺己，遽掩其耳。" +
                    "后世演为「掩耳盗铃」。\n" +
                    "《吕氏春秋·自知》：「恐人闻之而夺己也，遽掩其耳。」",
                source = classic("《吕氏春秋·自知》", "战国·吕不韦门客撰"),
            ),
        )

        // ---- 《列子》 ----
        add(
            AllusionEntry(
                id = "seed:qirenyoutian",
                word = "杞人忧天",
                phonetic = "qǐ rén yōu tiān",
                headline = "杞人忧天",
                body = "杞国有个人担忧天会塌下来，废寝忘食。",
                detail = "杞国有人忧天地崩坠，身亡所寄，废寝食者；又有忧彼之所忧者，" +
                    "往晓之。列子以此喻不必要的忧虑。\n" +
                    "《列子·天瑞》：「杞国有人忧天地崩坠，身亡所寄，废寝食者。」",
                source = classic("《列子·天瑞》", "旧题战国·列御寇撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:yugongyishan",
                word = "愚公移山",
                phonetic = "yú gōng yí shān",
                headline = "愚公移山",
                body = "北山愚公率子孙凿移太行、王屋二山，至诚感动天帝，命神移山。",
                detail = "北山愚公年且九十，惩山北之塞、出入之迂，率子孙荷担叩石垦壤，" +
                    "寒暑易节始一反焉。河曲智叟笑其不智，愚公长息以子子孙孙无穷匮" +
                    "对之。天帝感其诚，命夸蛾氏二子负二山。\n" +
                    "《列子·汤问》：「北山愚公者，年且九十……惩山北之塞，出入之迂也。」",
                source = classic("《列子·汤问》", "旧题战国·列御寇撰"),
            ),
        )

        // ---- 《山海经》 ----
        add(
            AllusionEntry(
                id = "seed:jingweitianhai",
                word = "精卫填海",
                phonetic = "jīng wèi tián hǎi",
                headline = "精卫填海",
                body = "炎帝少女女娃溺于东海，化为精卫，衔西山木石以堙东海。",
                detail = "发鸠之山有鸟焉，状如乌，文首白喙赤足，名曰精卫，" +
                    "其鸣自詨。是炎帝之少女名曰女娃，女娃游于东海溺而不返，" +
                    "故为精卫，常衔西山之木石以堙于东海。\n" +
                    "《山海经·北山经》：「常衔西山之木石，以堙于东海。」",
                source = classic("《山海经·北山经》", "先秦传世古籍，公版"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:kuafuzhuri",
                word = "夸父逐日",
                phonetic = "kuā fù zhú rì",
                headline = "夸父逐日",
                body = "夸父与太阳赛跑，渴死于道，所弃杖化为邓林。",
                detail = "夸父与日逐走，入日；渴欲得饮，饮于河渭，河渭不足，北饮大泽，" +
                    "未至，道渴而死。弃其杖，化为邓林。\n" +
                    "《山海经·海外北经》：「夸父与日逐走，入日；渴，欲得饮。」",
                source = classic("《山海经·海外北经》", "先秦传世古籍，公版"),
            ),
        )

        // ---- 《淮南子》 ----
        add(
            AllusionEntry(
                id = "seed:nvwabutian",
                word = "女娲补天",
                phonetic = "nǚ wā bǔ tiān",
                headline = "女娲补天",
                body = "天倾地裂之后，女娲炼五色石补苍天，断鳌足以立四极。",
                detail = "往古之时，四极废，九州裂，火炎而不灭，水浩洋而不息；" +
                    "于是女娲炼五色石以补苍天，断鳌足以立四极，杀黑龙以济冀州，" +
                    "积芦灰以止淫水。\n" +
                    "《淮南子·览冥训》：「于是女娲炼五色石以补苍天。」",
                source = classic("《淮南子·览冥训》", "西汉·刘安门客撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:houyisheri",
                word = "后羿射日",
                phonetic = "hòu yì shè rì",
                headline = "后羿射日",
                body = "尧时十日并出，羿仰射十日，中其九日，留其一日。",
                detail = "《淮南子·本经训》载尧之时十日并出，焦禾稼杀草木，民无所食；" +
                    "尧乃使羿上射十日，中其九日，日中九乌皆死，堕其羽翼。" +
                    "「后羿」为后世对羿的通称，本传原文作「羿」。\n" +
                    "《淮南子·本经训》：「羿仰射十日，中其九日。」",
                source = classic("《淮南子·本经训》", "西汉·刘安门客撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:saiwengshima",
                word = "塞翁失马",
                phonetic = "sài wēng shī mǎ",
                headline = "塞翁失马",
                body = "塞上老翁马亡入胡，后引胡骏马归；祸福相依，转相伏倚。",
                detail = "近塞上之人有善术者，马无故亡而入胡，人皆吊之；其父曰此何遽" +
                    "不为福乎。居数月，其马将胡骏马而归。后其子堕马折髀，" +
                    "又转为福。喻祸福相倚。\n" +
                    "《淮南子·人间训》：「近塞上之人，有善术者，马无故亡而入胡。」",
                source = classic("《淮南子·人间训》", "西汉·刘安门客撰"),
            ),
        )

        // ---- 《论语》 ----
        add(
            AllusionEntry(
                id = "seed:juyifansan",
                word = "举一反三",
                phonetic = "jǔ yī fǎn sān",
                headline = "举一反三",
                body = "教人举出一角，不能推知其余三角，孔子便不再重复教他。",
                detail = "孔子教学之法：不愤不启，不悱不发；举一隅不以三隅反，则不复也。" +
                    "后喻触类旁通。\n" +
                    "《论语·述而》：「举一隅不以三隅反，则不复也。」",
                source = classic("《论语·述而》", "春秋·孔子弟子辑录"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:wenguzhixin",
                word = "温故知新",
                phonetic = "wēn gù zhī xīn",
                headline = "温故知新",
                body = "温习旧学而能悟出新理，就可以为人师了。",
                detail = "孔子论为学：温故而知新，可以为师矣。旧籍重读中自有新得，" +
                    "是为师的条件之一。\n" +
                    "《论语·为政》：「温故而知新，可以为师矣。」",
                source = classic("《论语·为政》", "春秋·孔子弟子辑录"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:jianxiansiqi",
                word = "见贤思齐",
                phonetic = "jiàn xián sī qí",
                headline = "见贤思齐",
                body = "见到贤人想着向他看齐，见到不贤的人便反省自身有无同类毛病。",
                detail = "孔子曰：见贤思齐焉，见不贤而内自省也。\n" +
                    "《论语·里仁》：「见贤思齐焉，见不贤而内自省也。」",
                source = classic("《论语·里仁》", "春秋·孔子弟子辑录"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:renzhongdaoyuan",
                word = "任重道远",
                phonetic = "rèn zhòng dào yuǎn",
                headline = "任重道远",
                body = "士人担当沉重、路途遥远，非弘毅不能胜任。",
                detail = "曾子曰：士不可以不弘毅，任重而道远。仁以为己任，不亦重乎；" +
                    "死而后已，不亦远乎。\n" +
                    "《论语·泰伯》：「士不可以不弘毅，任重而道远。」",
                source = classic("《论语·泰伯》", "春秋·孔子弟子辑录"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:shizherusi",
                word = "逝者如斯",
                phonetic = "shì zhě rú sī",
                headline = "逝者如斯",
                body = "孔子在川上感叹：时光就像这流水，日夜不停。",
                detail = "子在川上曰：逝者如斯夫，不舍昼夜。以川流喻光阴与世事一去不返。\n" +
                    "《论语·子罕》：「子在川上曰：『逝者如斯夫，不舍昼夜。』」",
                source = classic("《论语·子罕》", "春秋·孔子弟子辑录"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:huoqixiaoqiang",
                word = "祸起萧墙",
                phonetic = "huò qǐ xiāo qiáng",
                headline = "祸起萧墙",
                body = "季孙的忧患不在颛臾，而在宫室大门之内。",
                detail = "季氏将伐颛臾，孔子责冉有、子路：吾恐季孙之忧，不在颛臾，" +
                    "而在萧墙之内也。萧墙为国君宫门内照壁，代指内部。\n" +
                    "《论语·季氏》：「吾恐季孙之忧，不在颛臾，而在萧墙之内也。」",
                source = classic("《论语·季氏》", "春秋·孔子弟子辑录"),
            ),
        )

        // ---- 《诗经》 ----
        add(
            AllusionEntry(
                id = "seed:tianzuozhihe",
                word = "天作之合",
                phonetic = "tiān zuò zhī hé",
                headline = "天作之合",
                body = "文王与太姒的婚配，像是上天撮合的姻缘。",
                detail = "《大雅·大明》咏文王嫁娶：文王初载，天作之合；在洽之阳，在渭之涘。" +
                    "后多称婚姻美满为天作之合。\n" +
                    "《诗经·大雅·大明》：「文王初载，天作之合。」",
                source = classic("《诗经·大雅·大明》", "西周至春秋诗歌"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:xiaoxinyiyi",
                word = "小心翼翼",
                phonetic = "xiǎo xīn yì yì",
                headline = "小心翼翼",
                body = "恭敬谨慎，不敢有一点疏忽。",
                detail = "《大雅·烝民》颂仲山甫之德：令仪令色，小心翼翼，昭事上帝。\n" +
                    "《诗经·大雅·烝民》：「小心翼翼，昭事上帝。」",
                source = classic("《诗经·大雅·烝民》", "西周至春秋诗歌"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:tashanzhishi",
                word = "他山之石",
                phonetic = "tā shān zhī shí",
                headline = "他山之石",
                body = "别的山上的石头，可以用来琢磨本山的玉；喻外力可助己改进。",
                detail = "《小雅·鹤鸣》：鹤鸣于九皋，声闻于天；他山之石，可以攻玉。\n" +
                    "《诗经·小雅·鹤鸣》：「他山之石，可以攻玉。」",
                source = classic("《诗经·小雅·鹤鸣》", "西周至春秋诗歌"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:gaoshanyangzhi",
                word = "高山仰止",
                phonetic = "gāo shān yǎng zhǐ",
                headline = "高山仰止",
                body = "德行高远如高山，令人仰望；如大道，可循行。",
                detail = "《小雅·车舝》：高山仰止，景行行止。后用以称颂人品德崇高，" +
                    "如司马迁《史记·孔子世家》引以赞孔子。\n" +
                    "《诗经·小雅·车舝》：「高山仰止，景行行止。」",
                source = classic("《诗经·小雅·车舝》", "西周至春秋诗歌"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:rulvobing",
                word = "如履薄冰",
                phonetic = "rú lǚ bó bīng",
                headline = "如履薄冰",
                body = "戒慎恐惧，像走在薄冰之上。",
                detail = "《小雅·小旻》：战战兢兢，如临深渊，如履薄冰。\n" +
                    "《诗经·小雅·小旻》：「战战兢兢，如临深渊，如履薄冰。」",
                source = classic("《诗经·小雅·小旻》", "西周至春秋诗歌"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:xinshidandan",
                word = "信誓旦旦",
                phonetic = "xìn shì dàn dàn",
                headline = "信誓旦旦",
                body = "誓言说得诚恳明白，转眼却违背。",
                detail = "《卫风·氓》写弃妇追忆：总角之宴，言笑晏晏，信誓旦旦，不思其反。\n" +
                    "《诗经·卫风·氓》：「信誓旦旦，不思其反。」",
                source = classic("《诗经·卫风·氓》", "西周至春秋诗歌"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:suxingyemei",
                word = "夙兴夜寐",
                phonetic = "sù xīng yè mèi",
                headline = "夙兴夜寐",
                body = "早起晚睡，形容勤劳不懈。",
                detail = "《卫风·氓》：三岁为妇，靡室劳矣；夙兴夜寐，靡有朝矣。\n" +
                    "《诗经·卫风·氓》：「夙兴夜寐，靡有朝矣。」",
                source = classic("《诗经·卫风·氓》", "西周至春秋诗歌"),
            ),
        )

        // ---- 《后汉书》 ----
        add(
            AllusionEntry(
                id = "seed:toubicongrong",
                word = "投笔从戎",
                phonetic = "tóu bǐ cóng róng",
                headline = "投笔从戎",
                body = "班超家贫佣书供养，叹大丈夫当立功异域，弃笔从军。",
                detail = "班超为人有大志，常为官佣书以供养。久劳苦，尝辍业投笔叹曰：" +
                    "大丈夫无他志略，犹当效傅介子、张骞立功异域，以取封侯，" +
                    "安能久事笔砚间乎。后出使西域三十一年。\n" +
                    "《后汉书·班超传》：「大丈夫无他志略，犹当效傅介子、张骞立功异域……" +
                    "安能久事笔砚间乎？」",
                source = classic("《后汉书·班超传》", "南朝宋·范晔撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:mageguoshi",
                word = "马革裹尸",
                phonetic = "mǎ gé guǒ shī",
                headline = "马革裹尸",
                body = "马援谓男儿当战死边疆，以马皮裹尸还葬。",
                detail = "马援征交趾还，谓宾客：男儿要当死于边野，以马革裹尸还葬耳，" +
                    "何能卧床上在儿女手中邪。后年逾六旬仍请征匈奴、乌桓。\n" +
                    "《后汉书·马援传》：「男儿要当死于边野，以马革裹尸还葬耳。」",
                source = classic("《后汉书·马援传》", "南朝宋·范晔撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:laodangyizhuang",
                word = "老当益壮",
                phonetic = "lǎo dāng yì zhuàng",
                headline = "老当益壮",
                body = "处境越穷越要坚定，年纪越老越要壮心不衰。",
                detail = "马援尝谓：丈夫为志，穷当益坚，老当益壮。\n" +
                    "《后汉书·马援传》：「丈夫为志，穷当益坚，老当益壮。」",
                source = classic("《后汉书·马援传》", "南朝宋·范晔撰"),
            ),
        )

        // ---- 《三国志》 ----
        add(
            AllusionEntry(
                id = "seed:sangumaolu",
                word = "三顾茅庐",
                phonetic = "sān gù máo lú",
                headline = "三顾茅庐",
                body = "刘备三次亲赴草庐拜访诸葛亮，礼聘其出山。",
                detail = "诸葛亮《出师表》自述：先帝不以臣卑鄙，猥自枉屈，三顾臣于草庐之中，" +
                    "咨臣以当世之事。事见《三国志·蜀书·诸葛亮传》。\n" +
                    "《三国志·蜀书·诸葛亮传》：「先帝不以臣卑鄙，猥自枉屈，" +
                    "三顾臣于草庐之中。」",
                source = classic("《三国志·蜀书·诸葛亮传》", "西晋·陈寿撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:lebushishu",
                word = "乐不思蜀",
                phonetic = "lè bù sī shǔ",
                headline = "乐不思蜀",
                body = "刘禅在司马昭座中观蜀技，曰此间乐，不思蜀。",
                detail = "蜀汉亡，后主刘禅举家迁洛阳。司马文王与禅宴，为之作故蜀技，" +
                    "旁人皆为之感怆，禅喜笑自若，曰此间乐，不思蜀。事见裴松之注" +
                    "引《汉晋春秋》。\n" +
                    "《三国志·蜀书·后主传》裴松之注引《汉晋春秋》：「此间乐，不思蜀。」",
                source = AllusionSource.annotatedClassic(
                    "《三国志·蜀书·后主传》",
                    "裴松之注引《汉晋春秋》",
                ),
            ),
        )

        // ---- 《晋书》 ----
        add(
            AllusionEntry(
                id = "seed:wenjiqiwu",
                word = "闻鸡起舞",
                phonetic = "wén jī qǐ wǔ",
                headline = "闻鸡起舞",
                body = "祖逖中夜闻荒鸡鸣，蹴醒刘琨，起身舞剑练功。",
                detail = "祖逖与刘琨俱为司州主簿，情好绸缪，共被同寝。中夜闻荒鸡鸣，" +
                    "蹴琨觉曰此非恶声也，因起舞。后祖逖统兵北伐，收复黄河以南。\n" +
                    "《晋书·祖逖传》：「中夜闻荒鸡鸣，蹴琨觉曰：『此非恶声也。』因起舞。」",
                source = classic("《晋书·祖逖传》", "唐·房玄龄等撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:dongshanzaiqi",
                word = "东山再起",
                phonetic = "dōng shān zài qǐ",
                headline = "东山再起",
                body = "谢安隐居东山多年，晚年复出就任，官至太傅，挽东晋局势。",
                detail = "谢安少有重名，累违朝旨，高卧东山，年四十余始出仕；" +
                    "苻坚南侵，安任征讨大都督，指挥淝水之战获胜。后喻隐退后再度出任。\n" +
                    "《晋书·谢安传》载安隐居东山，晚乃受桓温司马，累迁太保。",
                source = classic("《晋书·谢安传》", "唐·房玄龄等撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:luoyangzhigui",
                word = "洛阳纸贵",
                phonetic = "luò yáng zhǐ guì",
                headline = "洛阳纸贵",
                body = "左思《三都赋》写成，豪贵竞相传抄，洛阳纸价上涨。",
                detail = "左思作《三都赋》，构思十年，赋成，皇甫谧为之作序，张载、刘逵为注，" +
                    "于是豪贵之家竞相传写，洛阳为之纸贵。\n" +
                    "《晋书·左思传》：「于是豪贵之家竞相传写，洛阳为之纸贵。」",
                source = classic("《晋书·左思传》", "唐·房玄龄等撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:nangyingyingxue",
                word = "囊萤映雪",
                phonetic = "náng yíng yìng xuě",
                headline = "囊萤映雪",
                body = "车胤囊盛萤火照书，孙康借雪光读书，家贫而苦读不辍。",
                detail = "两事合成一联：囊萤出《晋书·车胤传》，胤家贫不常得油，" +
                    "夏月则练囊盛数十萤火以照书；映雪出《艺文类聚》引《孙氏世录》，" +
                    "孙康家贫，常映雪读书。\n" +
                    "《晋书·车胤传》：「夏月则练囊盛数十萤火以照书，以夜继日焉。」",
                source = AllusionSource.join(
                    AllusionSource.classic("《晋书·车胤传》", "唐·房玄龄等撰"),
                    AllusionSource.classic("《艺文类聚》引《孙氏世录》孙康事", "唐·欧阳询等辑，公版类书"),
                    AllusionSource.LOCAL_GLOSS,
                ),
            ),
        )

        // ---- 《世说新语》 ----
        add(
            AllusionEntry(
                id = "seed:wangmeizhike",
                word = "望梅止渴",
                phonetic = "wàng méi zhǐ kě",
                headline = "望梅止渴",
                body = "曹操行军缺水，诈称前有梅林，士卒想到酸梅，口中生津解渴。",
                detail = "魏武行役失汲道，军皆渴，乃令曰前有大梅林，饶子，甘酸可以解渴。" +
                    "士卒闻之，口皆出水，乘此得及前源。\n" +
                    "《世说新语·假谲》：「前有大梅林，饶子，甘酸可以解渴。」",
                source = classic("《世说新语·假谲》", "南朝宋·刘义庆撰"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:qibuchenshi",
                word = "七步成诗",
                phonetic = "qī bù chéng shī",
                headline = "七步成诗",
                body = "曹丕限曹植七步内作诗，不成行大法，植应声成诗。",
                detail = "文帝尝令东阿王七步中作诗，不成者行大法；应声便为诗，" +
                    "即「煮豆持作羹，漉豉以为汁」一篇，帝深有惭色。\n" +
                    "《世说新语·文学》：「文帝尝令东阿王七步中作诗，不成者行大法。" +
                    "应声便为诗。」",
                source = classic("《世说新语·文学》", "南朝宋·刘义庆撰"),
            ),
        )

        // ---- 公版笔记 / 书画著作 ----
        add(
            AllusionEntry(
                id = "seed:zaobitouguang",
                word = "凿壁偷光",
                phonetic = "záo bì tōu guāng",
                headline = "凿壁偷光",
                body = "匡衡家贫无烛，凿穿墙壁借邻舍灯火映书而读。",
                detail = "匡衡勤学而无烛，邻舍有烛而不逮，衡乃穿壁引其光，" +
                    "以书映光而读之。后以喻家贫苦读。\n" +
                    "《西京杂记》卷二：「衡乃穿壁引其光，以书映光而读之。」",
                source = classic("《西京杂记》卷二", "旧题汉·刘歆撰，公版"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:rumusanfen",
                word = "入木三分",
                phonetic = "rù mù sān fēn",
                headline = "入木三分",
                body = "王羲之书祝版，工人削版，笔迹渗入木板三分深。",
                detail = "唐张怀瓘《书断》载：晋帝时祭北郊，更祝版，工人削之，" +
                    "笔入木三分。后喻书法笔力遒劲，亦喻议论深刻。\n" +
                    "唐·张怀瓘《书断》：「工人削之，笔入木三分。」",
                source = classic("唐·张怀瓘《书断》", "唐·张怀瓘撰，公版书画著作"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:hualongdianjing",
                word = "画龙点睛",
                phonetic = "huà lóng diǎn jīng",
                headline = "画龙点睛",
                body = "张僧繇画龙不点睛，点之则雷电破壁，一龙乘云上天。",
                detail = "张僧繇于金陵安乐寺画四龙于壁，不点睛，每曰点之即飞去。" +
                    "人以为诞，固请点之；须臾雷电破壁，一龙乘云上天，" +
                    "不点睛者皆在。\n" +
                    "唐·张彦远《历代名画记》：「须臾雷电破壁，一龙乘云上天。」",
                source = classic("唐·张彦远《历代名画记》", "唐·张彦远撰，公版书画著作"),
            ),
        )

        // ---- 公版诗文名句 ----
        add(
            AllusionEntry(
                id = "seed:qingmeizhuma",
                word = "青梅竹马",
                phonetic = "qīng méi zhú mǎ",
                headline = "青梅竹马",
                body = "李白写小儿女幼时亲昵：郎骑竹马来，绕床弄青梅。",
                detail = "《长干行》追忆少女与邻儿幼小时情状：郎骑竹马来，绕床弄青梅；" +
                    "同居长干里，两小无嫌猜。\n" +
                    "唐·李白《长干行》：「郎骑竹马来，绕床弄青梅。」",
                source = poem("唐·李白", "长干行"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:cuncaochunhui",
                word = "寸草春晖",
                phonetic = "cùn cǎo chūn huī",
                headline = "寸草春晖",
                body = "子女如寸草，母爱如春日光辉，寸草之心难报三春之晖。",
                detail = "孟郊《游子吟》写母子：慈母手中线，游子身上衣；" +
                    "谁言寸草心，报得三春晖。\n" +
                    "唐·孟郊《游子吟》：「谁言寸草心，报得三春晖。」",
                source = poem("唐·孟郊", "游子吟"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:chunfengdeyi",
                word = "春风得意",
                phonetic = "chūn fēng dé yì",
                headline = "春风得意",
                body = "孟郊登科后快意驰骋：春风得意马蹄疾，一日看尽长安花。",
                detail = "孟郊年逾五十始登进士第，作《登科后》：昔日龌龊不足夸，" +
                    "今朝放荡思无涯；春风得意马蹄疾，一日看尽长安花。\n" +
                    "唐·孟郊《登科后》：「春风得意马蹄疾，一日看尽长安花。」",
                source = poem("唐·孟郊", "登科后"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:liuanhuaming",
                word = "柳暗花明",
                phonetic = "liǔ àn huā míng",
                headline = "柳暗花明",
                body = "山重水复疑无路之际，忽逢柳绿花明又一村。",
                detail = "陆游《游山西村》：莫笑农家腊酒浑，丰年留客足鸡豚；" +
                    "山重水复疑无路，柳暗花明又一村。\n" +
                    "宋·陆游《游山西村》：「山重水复疑无路，柳暗花明又一村。」",
                source = poem("宋·陆游", "游山西村"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:shuiluoshichu",
                word = "水落石出",
                phonetic = "shuǐ luò shí chū",
                headline = "水落石出",
                body = "冬夜江潮退落，江中礁石显露；后喻事情真相终于大白。",
                detail = "苏轼《后赤壁赋》写冬夜江景：江流有声，断岸千尺；" +
                    "山高月小，水落石出。\n" +
                    "宋·苏轼《后赤壁赋》：「山高月小，水落石出。」",
                source = poem("宋·苏轼", "后赤壁赋"),
            ),
        )
        add(
            AllusionEntry(
                id = "seed:chengfengpolang",
                word = "乘风破浪",
                phonetic = "chéng fēng pò làng",
                headline = "乘风破浪",
                body = "宗悫少时言志：愿乘长风破万里浪。",
                detail = "宗悫年少时，叔父宗炳问其志，悫曰愿乘长风破万里浪。" +
                    "后屡立战功，封洮阳侯。\n" +
                    "《宋书·宗悫传》：「愿乘长风破万里浪。」",
                source = classic("《宋书·宗悫传》", "南朝梁·沈约撰"),
            ),
        )

        // ---- 《列女传》 ----
        add(
            AllusionEntry(
                id = "seed:mengmusanqian",
                word = "孟母三迁",
                phonetic = "mèng mǔ sān qiān",
                headline = "孟母三迁",
                body = "孟母为教子，居所近墓、近市皆不适，三徙终定居学宫之旁。",
                detail = "孟子幼时，舍近墓，嬉戏为墓间之事；孟母迁于市旁，" +
                    "孟子又嬉为贾人炫卖之事；再迁学宫之旁，孟子乃嬉为设俎豆、" +
                    "揖让进退之事，孟母遂定居焉。\n" +
                    "《列女传·母仪传·邹孟轲母》：「遂迁于学宫之旁。」",
                source = classic("《列女传·母仪传·邹孟轲母》", "西汉·刘向撰"),
            ),
        )
    }
}
