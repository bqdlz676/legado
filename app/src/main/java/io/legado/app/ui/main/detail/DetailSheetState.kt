package io.legado.app.ui.main.detail

import android.content.Context
import io.legado.app.help.ChineseDict
import io.legado.app.help.PlaceTimeDict
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.person.PersonProfile
import io.legado.app.help.bubble.PersonProfileWiring
import io.legado.app.help.person.graph.PersonGraphExtractor

/**
 * 落文 · 详情半屏纯状态模型（B1 重做）。
 *
 * 本文件**不引用任何 Compose / View 类型**：旧 [io.legado.app.ui.main.DetailSheet] 在
 * onViewCreated 里直接 new 平台文本控件编排界面；重写后数据采集与界面编排分离——
 * [DetailSheetStateBuilder] 只跑旧实现里的同一批门面查询，产出不可变快照，
 * Compose 侧 [DetailSheetContent] 只负责渲染。字段口径与旧实现逐条对齐，见
 * `docs/修复-DetailSheet贴皮.md` 字段覆盖表。
 *
 * 兜底口径沿用旧实现：无载字段如实标「无载 / 未收录」，不留空白态；数据查询全程
 * 沿用各门面自身的 runCatching 口径，本层不再二次吞异常。
 */

/** 分区形态：普通文本节；[TIMELINE] 节内嵌纪年时间轴（自绘 View 经 AndroidView 桥接）。 */
enum class DetailSectionKind { PLAIN, TIMELINE }

/**
 * 半屏内一个分区。
 *
 * @param title 分区标题；空串表示无标题行（如旧实现里先于「本段」出现的读音行）。
 * @param lines 文本行列表；[TIMELINE] 节为空，时间轴由渲染层按 [DetailSheetState.timelineYear] 挂载。
 */
data class DetailSection(
    val title: String,
    val lines: List<String>,
    val kind: DetailSectionKind = DetailSectionKind.PLAIN,
)

/** 全屏动作跳转目标；旧实现的两个出口 Activity 原样保留。 */
sealed class DetailTarget {
    /** 人物详情全屏页（旧 PersonDetailActivity 出口）。 */
    data class PersonDetail(val name: String) : DetailTarget()

    /** 古今地图全屏页（旧 PlaceMapActivity 出口）；[year] 为空表示只按地名打开。 */
    data class PlaceMap(val name: String, val year: Int?, val newTask: Boolean) : DetailTarget()
}

/** 半屏底部动作行：文案 + 跳转目标；null 表示该类型无全屏出口（如单字）。 */
data class DetailAction(val label: String, val target: DetailTarget)

/**
 * 详情半屏快照。
 *
 * @param type 旧四型：char / person / year / place，仅用于交接与日志，渲染不分支。
 * @param title 标题行（对齐旧 tv_title）。
 * @param subtitle 副题行（对齐旧 tv_sub：拼音部首 / 国别身份来源 / 纪年标题 / 地名首行）。
 * @param sections 分区列表，顺序与旧实现渲染顺序一致。
 * @param action 底部全屏动作；null 不渲染动作行。
 * @param timelineYear 纪年时间轴年份；非 year 型为 null。
 */
data class DetailSheetState(
    val type: String,
    val title: String,
    val subtitle: String,
    val sections: List<DetailSection>,
    val action: DetailAction?,
    val timelineYear: Int?,
)

/** 旧 DetailSheet 四型数据采集门面：输入 arguments 拆出的原始值，输出不可变快照。 */
object DetailSheetStateBuilder {

    /**
     * @param extraInt 整型参数：person=锚定年份（Int.MIN_VALUE 表示未带）；year=年份；place/char 不用。
     * @param detail 文本参数：person=正文片段 excerpt；place=地名沿革 detail；其余不用。
     * @param placeOthers place 型同名候选列表（旧实现以 \u0001 串存 arguments，拆包后传入）。
     */
    fun build(
        context: Context,
        type: String,
        name: String,
        extraInt: Int,
        detail: String,
        placeOthers: List<String>,
    ): DetailSheetState = when (type) {
        "person" -> buildPerson(context, name, extraInt, detail)
        "year" -> buildYear(context, extraInt)
        "place" -> buildPlace(context, name, detail, placeOthers)
        else -> buildChar(context, name)
    }

    // ---------- 字 ----------

    private fun buildChar(ctx: Context, ch: String): DetailSheetState {
        val info = ChineseDict.getChar(ctx, ch)
        val sw = ChineseDict.getShuowen(ctx, ch)
        val usage = ChineseDict.getUsage(ctx, ch)
        val reads = ChineseDict.getPinyin(ctx, ch)

        val sub = buildString {
            if (reads.isNotEmpty()) append(reads.joinToString("　"))
            else info?.pinyin?.let { append(it) }
            info?.radical?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("　·　")
                append("部首 ").append(it)
            }
            info?.strokes?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("　·　")
                append("笔画 ").append(it)
            }
        }

        val sections = mutableListOf<DetailSection>()
        if (sw?.text?.isNotBlank() == true) {
            sections += DetailSection("说文", listOf("《说文解字》：${sw.text}"))
        }
        if (info?.meaning?.isNotBlank() == true) {
            sections += DetailSection("义项", listOf(info.meaning.replace('\u0001', '\n')))
        }
        if (usage != null && usage.sentence.isNotBlank()) {
            sections += DetailSection("书证", listOf("${usage.source}　${usage.sentence}"))
        }
        val words = ChineseDict.wordsStartingWith(ctx, ch.firstOrNull() ?: ' ')
        if (words.isNotEmpty()) {
            sections += DetailSection("词组", listOf(words.joinToString("　")))
        }
        if (info == null) {
            sections += DetailSection("提示", listOf("此字未收，只按字形显示。"))
        }
        return DetailSheetState("char", ch, sub, sections, null, null)
    }

    // ---------- 人 ----------

    private fun buildPerson(ctx: Context, name: String, anchorYear: Int, excerpt: String): DetailSheetState {
        // 单一总线：只读 ContextAnchor，不再各传一份年份（口径同旧实现）。
        val year: Int? = if (anchorYear == Int.MIN_VALUE) ContextAnchor.getYear() else anchorYear
        val prof = PersonProfile.profileOf(name)
        val allCandidates = PersonProfile.candidatesOf(name)
        val others = allCandidates.filter { it.personId != prof?.personId }

        val sub = buildString {
            if (prof != null) {
                if (prof.nationality.isNotBlank()) append(prof.nationality)
                if (prof.identity.isNotBlank()) {
                    if (isNotEmpty()) append("　·　")
                    append(prof.identity)
                }
            }
            if (isNotEmpty()) append("　·　")
            // 出处标注：D2 冻结字段只带 PersonSource；旧 L1/L2/L3 置信分层未建模，待 D2 补建。
            append(prof?.source?.label?.takeIf { it.isNotBlank() } ?: "未收录")
        }

        // 注音：人名专用异读门面优先（郦食其 / 樊於期等固定读法），未命中回落通用字库逐字拼。
        val py = PersonProfileWiring.pinyin(name) {
            ChineseDict.pinyinOfWord(ctx, name)
        }.orEmpty()

        // ① 本段即时信息：这段里他干了什么、和谁同场、当年几岁 —— 阅读优先。
        val mainLines = mutableListOf<String>()
        if (excerpt.isNotBlank()) {
            val evs = PersonGraphExtractor.sentencesAbout(ctx, excerpt, name, year)
            if (evs.isEmpty()) {
                mainLines += "这一段正文里没有他再次出现的句子。"
            } else {
                evs.take(3).forEach { mainLines += "· $it" }
            }
            val co = PersonGraphExtractor.coPresent(ctx, excerpt, name, year)
            if (co.isNotEmpty()) {
                mainLines += "同场：" + co.take(6).joinToString("、") {
                    it.name + (if (it.label.isNotBlank()) "〔${it.label}〕" else "")
                }
            }
        } else {
            mainLines += "未带入正文片段；从正文里点人名即可补上这一段的事迹与同场人物。"
        }
        if (year != null) {
            mainLines += "${Chronology.yearTitle(year)} · ${ageLabelOf(prof, year)}"
        } else {
            mainLines += "尚未锚定到具体年份，年龄不予推算。读到「隐公元年」这类纪年即可锚定。"
        }

        // ② 本书迷你时间轴（首次 → 关键事迹 → 末次）。
        val bookLines = buildList {
            add("当前章　${ContextAnchor.title.ifBlank { "未定位" }}")
            prof?.notableEvent?.takeIf { it.isNotBlank() }?.let {
                add("关键事迹　$it")
            }
            // 整本出场持久化表（旧 PersonChapterEvents：首次/末次出场段落）D2 暂无等价能力，待接；
            // 出场段落现由图谱 Node.appearances / 详情页 extras 通道提供，此处不挂旧持久化表。
            add("出场索引　尚未整本扫描；扫一遍即可给首次/末次出场。")
        }

        // ③ 基础档案（查阅读到这里才开始）。
        val archiveLines = mutableListOf<String>()
        archiveLines += buildString {
            if (prof != null) {
                prof.courtesyName?.takeIf { it.isNotBlank() }?.let { append("字 ").append(it).append("　") }
                prof.styleName?.takeIf { it.isNotBlank() }?.let { append("号 ").append(it).append("　") }
                prof.titles.forEach { append("封 ").append(it).append("　") }
            }
            if (isBlank()) {
                prof?.aliases?.takeIf { it.isNotEmpty() }?.let { append("又称 ").append(it.take(4).joinToString("、")) }
            }
            if (isBlank()) append("字号无载")
        }
        archiveLines += buildString {
            val span = PersonProfile.lifeSpan(name)
            val b = span?.first ?: prof?.birthYear
            val d = span?.second ?: prof?.deathYear
            if (b != null || d != null) {
                append(if (b != null) fmt(b) else "?").append(" — ")
                append(if (d != null) fmt(d) else "?")
            }
            if (isBlank()) append("生卒无载")
        }
        // 在位起讫：走 D2 reignOf 门面；非君主 / 史料无载如实标「在位无载」，不编约数、不留空白。
        val reign = PersonProfile.reignOf(name) ?: Pair(prof?.reignStart, prof?.reignEnd)
        archiveLines += PersonProfileWiring.reignLabel(reign?.first, reign?.second) { fmt(it) }
        archiveLines += buildString {
            append(if (prof?.nationality?.isNotBlank() == true) prof!!.nationality else "国别无载")
            append("　·　").append(if (prof?.identity?.isNotBlank() == true) prof!!.identity else "身份无载")
            val calls = prof?.aliases.orEmpty()
            if (calls.isNotEmpty()) {
                append("\n其他称呼　").append(calls.take(10).joinToString("、"))
            }
        }

        val sections = mutableListOf<DetailSection>()
        // 旧实现：读音行先于「本段」标签，无标题。
        if (py.isNotBlank()) {
            sections += DetailSection("", listOf("读音　$py"))
        }
        sections += DetailSection("本段", mainLines)
        sections += DetailSection("本书", bookLines)
        sections += DetailSection("档案", archiveLines)

        // ④ 同名候选：D2 同名分组；不是同一个人，别混成一条。
        if (others.size >= 2) {
            sections += DetailSection(
                "还有 ${others.size} 个同名",
                others.take(8).map { dupTag(it) },
            )
        }

        // 来源可追溯：D2 每条档案必带 PersonSource（CBDB 收录号 / 公版典籍篇卷）。
        sections += DetailSection(
            "来源",
            listOf(
                if (prof != null) "来源　${prof.source.label}"
                else "来源　未收录：仅按正文出现统计，不作生平结论",
            ),
        )

        val action = DetailAction(
            "全屏查看 →",
            DetailTarget.PersonDetail(name),
        )
        return DetailSheetState("person", name, sub, sections, action, null)
    }

    // ---------- 年 ----------

    private fun buildYear(ctx: Context, year: Int): DetailSheetState {
        Chronology.ensureLoaded(ctx)
        val title = if (year <= 0) "前${1 - year}年" else "${year}年"
        val sub = Chronology.yearTitle(year)

        val rows = Chronology.timelineFor(year)
        val compareSection = if (rows.isEmpty()) {
            DetailSection(
                "同年对照",
                listOf("这一年没有收录到在位君主（先秦年表只到战国）。"),
            )
        } else {
            DetailSection(
                "同年各国（在位第几年）",
                rows.map { (state, nth) ->
                    "$state · " + Chronology.chineseNumberText(nth) + "年"
                },
            )
        }

        val sections = listOf(
            compareSection,
            DetailSection("纪年", emptyList(), DetailSectionKind.TIMELINE),
            DetailSection(
                "说明",
                listOf("点「全屏」可在古今地图里按这一年切换朝代图层；读《左传》锚在春秋，读《史记》锚到西汉。"),
            ),
        )
        val action = DetailAction(
            "全屏查看 →",
            DetailTarget.PlaceMap(name = "", year = year, newTask = false),
        )
        return DetailSheetState("year", title, sub, sections, action, year)
    }

    // ---------- 地 ----------

    private fun buildPlace(ctx: Context, name: String, detail: String, others: List<String>): DetailSheetState {
        val lines = detail.split('\n')
        val sub = lines.firstOrNull().orEmpty()

        val sections = mutableListOf<DetailSection>()
        sections += DetailSection(
            "沿革",
            listOf(lines.drop(1).joinToString("\n").ifBlank { "暂无可考" }),
        )

        if (others.isNotEmpty()) {
            sections += DetailSection("还有 ${others.size} 个同名", others)
        }

        // 时空本体：点画点，面画辖区；锚点为空不猜。
        PlaceTimeDict.ensureLoaded(ctx)
        val yearP = ContextAnchor.get().year
        val st = PlaceTimeDict.resolve(ctx, name, yearP)
        val ent = st.hit
        when {
            ent != null && ent.isPoint -> {
                sections += DetailSection(
                    "位置（${ent.era.takeIf { e -> e.isNotBlank() } ?: "年代未锚定"}·${ent.tierLabel}）",
                    listOf(
                        "${ent.name} · ${ent.note.ifBlank { "坐标已收录" }}。\n" +
                            "地图不在这张纸片里挤着——点下面按钮，另开一个窗口。",
                    ),
                )
            }
            ent != null -> {
                sections += DetailSection(
                    "位置（${ent.era}·辖区）",
                    listOf("这是面（辖区），不是一座城。它的治所落在 ${ent.name}，全屏图上看范围。"),
                )
            }
            else -> {
                sections += DetailSection(
                    "位置",
                    listOf("此地在正文里出现，但坐标未收录到时空本体，按三级推定也不给点。"),
                )
            }
        }

        val anchor = ContextAnchor.get()
        if (anchor.year != null) {
            sections += DetailSection("当前锚点", listOf(Chronology.yearTitle(anchor.year)))
        }

        val action = DetailAction(
            "在地图窗口中打开 →",
            DetailTarget.PlaceMap(name = name, year = null, newTask = true),
        )
        return DetailSheetState("place", name, sub, sections, action, null)
    }

    // ---------- 展示辅助（口径同旧实现） ----------

    /** 同名候选行标签：朝代 · 身份 · 生卒，口径对齐 PersonProvider 候选展示。 */
    private fun dupTag(p: PersonProfile): String {
        val decor = listOfNotNull(p.dynasty, p.identity).filter { it.isNotBlank() }.joinToString("·")
        val head = if (decor.isBlank()) p.name else "${p.name}（$decor）"
        val b = p.birthYear
        val d = p.deathYear
        return if (b != null || d != null) {
            "$head　${b?.let { fmt(it) } ?: "?"}–${d?.let { fmt(it) } ?: "?"}"
        } else head
    }

    /** 按 D2 生卒字段推算当年状态；无生年不给岁数，不猜。 */
    private fun ageLabelOf(p: PersonProfile?, year: Int): String {
        val b = p?.birthYear
        val d = p?.deathYear
        if (b != null && year < b) return "尚未出生（生于${fmt(b)}）"
        if (d != null && year > d) return "已卒（卒于${fmt(d)}）"
        if (b != null) return "约 ${year - b} 岁" + if (d == null) "（卒年无载）" else ""
        return "生卒无载"
    }

    private fun fmt(y: Int) = if (y <= 0) "前${1 - y}" else "$y"
}
