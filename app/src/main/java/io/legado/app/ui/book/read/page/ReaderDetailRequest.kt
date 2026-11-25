package io.legado.app.ui.book.read.page

/**
 * 点字详情入口类型（B1 接线）。
 *
 * 对齐旧底部弹窗四工厂：字（char）/ 人物（person）/ 纪年（year）/ 地名（place）；
 * 典故词入口归入 [CHAR] 形态（词义 + 出处）。枚举只做分派标签，不持有视图类型。
 */
enum class ReaderDetailKind {
    /** 单字 / 典故词：字义、说文、用例、词组。 */
    CHAR,

    /** 人名：本段行为、同场人物、时间轴、档案、同名候选。 */
    PERSON,

    /** 纪年 / 干支：公元对照、干支、距今、多政权对照。 */
    YEAR,

    /** 地名：沿革、古今对照、同名候选。 */
    PLACE,
}

/**
 * 点字详情请求（纯数据，平台无关层）。
 *
 * 〔为什么 View 只发请求、不弹弹窗〕
 * ContentTextView 是自定义 View；旧实现直接在触摸回调里取 FragmentManager 弹
 * Material `BottomSheetDialogFragment` 旧弹窗，返回时阅读页重建、位置丢失。
 * B1 接线后：View 层只把命中结果打包成本请求，经 `ContentTextView.CallBack.openReaderSheet`
 * 抛给宿主 Compose 层，由统一自绘 `InkHalfSheet` 覆盖层渲染；View 内不再持有任何
 * Dialog / Fragment 引用。请求对象不读写宿主滚动 / 选中 / 高亮状态，Sheet 只读不改。
 *
 * @param kind 入口类型，覆盖层按此分派人物 / 纪年 / 字词 / 地名四套标题与分区。
 * @param token 原文字段：单字、人名、地名或纪年标题（如「鲁隐公元年」）。
 * @param year 纪年入口锚定年份；非纪年入口为 null。旧纪年工厂里的
 *             `ContextAnchor.setYear` 副作用由调用方在发请求前保持，本对象不碰时空总线。
 * @param excerpt 人物入口的本段摘录（旧 person 工厂 `excerpt` 口径，截断由采集方负责）。
 * @param detail 气泡阶段已采集的释义 / 小传 / 纪年对照 / 地名沿革正文，空串由覆盖层兜底。
 * @param source 出处标注（如 CBDB 编号 / 《说文解字》版本）；空串不渲染脚注。
 * @param placeOthers 地名同名候选列表；其余入口为空。
 */
data class ReaderDetailRequest(
    val kind: ReaderDetailKind,
    val token: String,
    val year: Int? = null,
    val excerpt: String = "",
    val detail: String = "",
    val source: String = "",
    val placeOthers: List<String> = emptyList(),
)
