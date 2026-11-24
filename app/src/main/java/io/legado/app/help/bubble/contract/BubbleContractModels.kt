package io.legado.app.help.bubble.contract

/**
 * S-02 气泡协议 · 冻结契约骨架（`docs/落文-规格切片/切片-02-气泡协议.md:1-18`；
 * `docs/落文-模块契约索引.md:134-136` 气泡 FROZEN 段）。
 *
 * 为什么独立 `contract` 子包：旧 `help/bubble/BubbleModels.kt` 是 C2 早期实现，已被旧阅读壳
 * （`ui/book/read/ink/InkBubble.kt`）按旧字段接线；本单只新增文件、不动旧接线，
 * 若在同包重声明 `BubbleContent / Action` 会直接造成类名冲突编译红。冻结契约先落子包立骨架，
 * 旧实现拆除与壳层切换归后续接线单（新逻辑先接好再删旧，不留兼容层）。
 *
 * 本文件纯 JVM：不 import 任何 Android / Compose 类型，保证普通 JVM 单测可直接实例化断言。
 */

/**
 * 气泡类型八值（切片-02:2 注释「字 | 词 | 典故 | 人名 | 地名 | 年号 | 干支 | 朔望」）。
 * [wire] 为落库 / 回传时的中文原文字面值，与冻结注释逐字对齐，不另造英文枚举名对外暴露。
 */
enum class Kind(val wire: String) {
    CHAR("字"),
    WORD("词"),
    ALLUSION("典故"),
    PERSON("人名"),
    PLACE("地名"),
    ERA("年号"),
    GANZHI("干支"),
    PHASE("朔望"),
}

/**
 * 气泡可执行动作 · 冻结五子类（索引:135：OpenMap / OpenPerson / OpenTimeline /
 * OpenRelation / OpenArtifact）。新增跳转意图必须先改冻结契约，本 sealed 此后不再增删。
 *
 * 主键参数逐字对齐切片-02:15-18 与索引:136；不挂路由附加信息，附加字段由后续 Provider
 * 在接线层另议，不在冻结动作构造器里扩参。
 */
sealed class Action {

    /** 打开古今地图；[placeId] 对齐时空本体 place 主键。 */
    data class OpenMap(val placeId: String) : Action()

    /** 打开人物档案页；[name] 为本名。 */
    data class OpenPerson(val name: String) : Action()

    /** 打开纪年时间轴；[year] 为公元年，负数为公元前。 */
    data class OpenTimeline(val year: Int) : Action()

    /** 打开人物关系图；[name] 为本名。 */
    data class OpenRelation(val name: String) : Action()

    /** 打开名物详情（F-07 拍板新增）；[artifactId] 名物主键，[type] 名物类别。 */
    data class OpenArtifact(val artifactId: String, val type: String) : Action()
}

/**
 * 一张气泡的完整内容。字段名、类型、顺序逐字对齐切片-02:2-10，不可调序、不可改名：
 *
 * @param kind       气泡类型，[Kind] 八值之一
 * @param token      原文片段，如「鲁隐公元年」
 * @param phonetic   注音 / 拼音，可空；无字库读音时为 null，不猜
 * @param headline   一行摘要，如「郑庄公 · 春秋初霸」
 * @param body       气泡内 2~3 行解释
 * @param detail     进半屏 sheet 的完整内容
 * @param actions    可执行动作，顺序即按钮顺序
 * @param candidates 同名 / 多义候选裸串列表；无候选为空列表，绝不塞假数据
 * @param source     出处标注，如「Harvard CBDB #xxx」「《说文解字》」
 */
data class BubbleContent(
    val kind: Kind,
    val token: String,
    val phonetic: String?,
    val headline: String,
    val body: String,
    val detail: String,
    val actions: List<Action>,
    val candidates: List<String>,
    val source: String,
)

/**
 * 消歧候选类型四值（`docs/立项草案-批次D.md:9`：place | person | era | allusion）。
 */
enum class CandidateType(val wire: String) {
    PLACE("place"),
    PERSON("person"),
    ERA("era"),
    ALLUSION("allusion"),
}

/**
 * 置信度三值常量（草案D:9：high | medium | low）。
 * 为什么收成常量对象而非裸字符串：消歧链与数据脚本共用同一组字面值，避免散落拼写漂移。
 */
object Confidence {
    const val HIGH: String = "high"
    const val MEDIUM: String = "medium"
    const val LOW: String = "low"
}

/**
 * 同名 / 多义消歧候选（Q-01 core 契约 `resolve(name, year?) → List<Candidate>` 的元素）。
 * 字段按草案D:9 十字段预置，供 S-05 纪年联动与 S-11 半屏 Sheet 直接消费。
 *
 * @param id        候选主键（人物 / 地名 / 名物库收录号；兜底条为空串）
 * @param name      本名 / 正名
 * @param type      候选类型，[CandidateType] 四值之一
 * @param tier      来源分层（L1 手校核心 / L2 多源核验 / L3 孤证存疑），与人物分层口径一致
 * @param start     起始公元年，负数为公元前；年代不可考为 null
 * @param end       讫止公元年；年代不可考为 null
 * @param aliases   别名 / 别称 / 异称列表
 * @param confidence 置信度，[Confidence] 三值之一
 * @param source    来源与版本标注；未命中固定为 [MISSING_SOURCE]
 * @param isDefault 是否可进默认命中；low 置信兜底条恒为 false
 */
data class Candidate(
    val id: String,
    val name: String,
    val type: CandidateType,
    val tier: String,
    val start: Int?,
    val end: Int?,
    val aliases: List<String>,
    val confidence: String,
    val source: String,
    val isDefault: Boolean,
)

/** 未命中条目的固定 source 取值；三兜底态（空输入 / 未命中 / 年份缺失无回）同值。 */
const val MISSING_SOURCE: String = "未命中"
