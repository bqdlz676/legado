package io.legado.app.help.bubble

/**
 * 落文 · 气泡协议数据模型（规格切片-02 §1「统一内容协议」，字段名与顺序不可变）。
 *
 * 本目录只负责「点中一处文本 → 产出一张气泡的内容与动作」，不触碰 Compose / 自绘渲染层；
 * 渲染由阅读页壳按设计系统契约自绘。所有词典都实现 [BubbleProvider]，各自产出同一种
 * [BubbleContent]；气泡 UI 只认这一种结构，新增类型（职官 / 避讳 / 异体字）只需加 Provider，
 * UI 零改动。
 *
 * 数据可信 = 命：每条字词 / 人物 / 事件必须标来源与版本（CBDB 标 Harvard CBDB 与收录号；
 * 典籍用公版影印 / 公版校本并写明）；宁可少量有出处，不要大量没来路。
 */
object BubbleKind {
    /** 单字：字义 + 说文 + 古籍用例 */
    const val CHAR = "字"

    /** 多字词：词表命中的 2~5 字 */
    const val WORD = "词"

    /** 典故：词表命中的 2~4 字，已下划线的优先 */
    const val ALLUSION = "典故"

    /** 人名：本名 + 身份 + 生卒 + 别名 + 谱系 */
    const val PERSON = "人名"

    /** 地名：古名今址 + 沿革，按锚点年代选层 */
    const val PLACE = "地名"

    /** 年号 / 王公纪年：公元年 + 干支 + 同期对照 */
    const val ERA = "年号"

    /** 干支纪年 / 纪日 */
    const val GANZHI = "干支"

    /** 朔 / 望 / 晦 纪日 */
    const val PHASE = "朔望"

    /** 全空兜底：本地未收录，允许联网检索 / 加入生字本 */
    const val MISSING = "未识"
}

/**
 * 气泡可执行动作。冻结契约只允许四类：OpenMap / OpenPerson / OpenTimeline / OpenRelation，
 * 不许增删；UI 层 `when(action)` 按这四类穷尽分支。
 *
 * 「联网检索 / 加入生字本」不是导航动作，属于 Missing 兜底入口，改由
 * [BubbleContent.allowWebSearch] / [BubbleContent.allowNotebook] 两个标志位告知 UI 出按钮，
 * 不进本 sealed 层级。
 */
sealed class Action {

    /** 打开古今地图。[placeId] 对齐时空本体 place 主键；[placeName] 供无主键时按名检索。 */
    data class OpenMap(
        val placeId: String,
        val placeName: String? = null,
    ) : Action()

    /** 打开人物档案页。[personId] 对齐 CBDB / 人物库主键，可空（仅按名检索时）。 */
    data class OpenPerson(
        val name: String,
        val personId: String? = null,
    ) : Action()

    /** 打开纪年时间轴（同年各政权横向对照 + 干支 + 距今）。[year] 为公元年，负数为公元前；[label] 供轴标题。 */
    data class OpenTimeline(
        val year: Int,
        val label: String? = null,
    ) : Action()

    /** 打开人物关系图。 */
    data class OpenRelation(
        val name: String,
    ) : Action()
}

/**
 * 一张气泡的完整内容（字段顺序对齐冻结协议，不可调序）。
 *
 * @param kind       [BubbleKind] 之一
 * @param token      原文片段（字 / 词 / 称谓 / 纪年串），如「鲁隐公元年」
 * @param phonetic   注音 / 拼音，可空；无字库读音时为 null，不猜
 * @param headline   一行摘要，如「郑庄公 · 春秋初霸」
 * @param body       气泡内 2~3 行解释
 * @param detail     进半屏 sheet 的完整内容（全义项 / 生卒 / 今地 / 出处 / 对照年等，可多行）
 * @param actions    可执行动作（冻结四类），顺序即按钮顺序
 * @param candidates 同名 / 多义候选；[BubbleCandidate.query] 为可查裸 token/id（重查用），
 *                   [BubbleCandidate.display] 为带朝代 / 辖区等括号装饰的展示名；无候选为空列表，绝不塞假数据
 * @param source     出处标注，如「Harvard CBDB #xxx」「《说文解字》」；每条数据必须带来源与版本
 * @param allowWebSearch 兜底标志：本地未收录时是否允许「联网检索」入口；正常命中为 false
 * @param allowNotebook  兜底标志：是否允许「加入生字本」入口；正常命中为 false
 */
data class BubbleContent(
    val kind: String,
    val token: String,
    val phonetic: String?,
    val headline: String,
    val body: String,
    val detail: String,
    val actions: List<Action>,
    val candidates: List<BubbleCandidate>,
    val source: String,
    val allowWebSearch: Boolean = false,
    val allowNotebook: Boolean = false,
)

/**
 * 气泡候选条目（切片-02 §4 候选切换）。
 *
 * [query] 是回传解析链重查的裸名 / 主键，不带任何括号装饰；
 * [display] 是气泡上展示的名字，可附朝代、辖区、年代等说明。
 * 点选切换时只回传 [query]，避免展示串里的括号装饰把重查词污染成查不到的整串。
 */
data class BubbleCandidate(
    val query: String,
    val display: String,
)
