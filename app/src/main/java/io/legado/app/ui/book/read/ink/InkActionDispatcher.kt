package io.legado.app.ui.book.read.ink

import android.content.Context
import android.content.Intent
import io.legado.app.help.bubble.Action
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.ui.main.PersonDetailActivity
import io.legado.app.ui.main.PlaceMapActivity
import io.legado.app.ui.main.RelationActivity

/**
 * 气泡动作分发层（切片-02 §1 Action 冻结四类；模块契约 §2 气泡契约）。
 *
 * 职责边界：本层只做「动作 → 目标页 / 卡片」的路由与 Intent 装配；
 * 目标页内部的数据加载、消歧、渲染由各页自己负责，本层不复制其逻辑。
 * 未接通的动作 [route] 返回 null，气泡侧 chip 据此置灰不可点，不呈现为可点击。
 *
 * 路由口径（冻结协议，四类穷尽分支，不许增删 Action）：
 *  - [Action.OpenMap]      → 古今地图 [PlaceMapActivity]；地名优先取 placeName，缺省回退 placeId；
 *                            年代取共享锚点 [ContextAnchor] 当前年，供地图选朝代图层。
 *  - [Action.OpenPerson]   → 人物档案 [PersonDetailActivity]；personId 随 extra 透传（页面暂按名检索）；
 *                            文本所处年代（锚点公元年 + 朝代 / 干支标签）一并透传，页面显示「所处年代」。
 *  - [Action.OpenRelation] → 人物关系图 [RelationActivity]；图由页面按当前书正文抽取；锚点年代
 *                            随 extra 透传，供图页标注所处年代，并在点节点进详情时继续带下。
 *  - [Action.OpenTimeline] → 纪年时间轴 [InkTimelineActivity]；year 为公元年（负数为公元前），label 作轴标题。
 *
 * 「联网检索 / 加入生字本」不是导航动作，由 BubbleContent.allowWebSearch /
 * allowNotebook 标志位驱动，走壳层 onFallback 通道，不进本分发层。
 */
object InkActionDispatcher {

    /**
     * 动作 → 路由映射（纯函数，可单测）。
     * @param anchor 共享时空锚点；取其当前年供地图选图层。
     * @return 目标路由；null = 本动作暂无接通消费方，UI 应禁用对应按钮。
     */
    fun route(action: Action, anchor: ContextAnchor): InkRoute? = when (action) {
        is Action.OpenMap -> InkRoute.Map(
            placeName = action.placeName?.takeIf { it.isNotBlank() } ?: action.placeId,
            year = anchor.getYear(),
        )

        is Action.OpenPerson -> InkRoute.Person(
            name = action.name,
            personId = action.personId,
            anchorYear = anchor.getYear(),
            anchorLabel = anchor.eraLabel(),
        )

        is Action.OpenRelation -> InkRoute.Relation(
            name = action.name,
            anchorYear = anchor.getYear(),
            anchorLabel = anchor.eraLabel(),
        )

        is Action.OpenTimeline -> InkRoute.Timeline(
            year = action.year,
            label = action.label,
        )
    }

    /** 该动作是否已有真实消费方；气泡 chip 启用 / 置灰据此判定。 */
    fun canDispatch(action: Action, anchor: ContextAnchor = ContextAnchor): Boolean =
        route(action, anchor) != null

    /**
     * 分发动作（冻结集成签名）。接通则拉起目标页并返回 true；未接通返回 false，
     * 调用方不应再做兜底跳转。拉起失败（目标页缺失 / 进程受限等极端情况）不崩，回 false。
     */
    fun dispatch(action: Action, context: Context, anchor: ContextAnchor): Boolean {
        val route = route(action, anchor) ?: return false
        val intent = buildIntent(context, route)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 路由 → Intent 装配（单独拆出便于静态走查与单测核对 extra 键）。 */
    internal fun buildIntent(context: Context, route: InkRoute): Intent = when (route) {
        is InkRoute.Map -> Intent(context, PlaceMapActivity::class.java).apply {
            putExtra("name", route.placeName)
            route.year?.let { putExtra("year", it) }
        }

        is InkRoute.Person -> Intent(context, PersonDetailActivity::class.java).apply {
            putExtra("name", route.name)
            route.personId?.let { putExtra("personId", it) }
            route.anchorYear?.let { putExtra("anchorYear", it) }
            route.anchorLabel?.takeIf { it.isNotBlank() }?.let { putExtra("anchorLabel", it) }
        }

        is InkRoute.Relation -> Intent(context, RelationActivity::class.java).apply {
            route.anchorYear?.let { putExtra("anchorYear", it) }
            route.anchorLabel?.takeIf { it.isNotBlank() }?.let { putExtra("anchorLabel", it) }
        }

        is InkRoute.Timeline -> Intent(context, InkTimelineActivity::class.java).apply {
            putExtra("year", route.year)
            route.label?.let { putExtra("label", it) }
        }
    }
}

/**
 * 分发层中间路由模型：与具体 Activity 解耦，便于离线 JVM 单测核对映射结果，
 * 不依赖 Android Context 即可断言字段透传与地名 / id 回退口径。
 */
sealed interface InkRoute {
    /** 地图：展示名（古名 / 今址）+ 锚点年代。 */
    data class Map(val placeName: String, val year: Int?) : InkRoute

    /** 人物：本名 + 可选人物主键 + 文本所处年代锚点（公元年可空；展示标签可空）。 */
    data class Person(
        val name: String,
        val personId: String?,
        val anchorYear: Int? = null,
        val anchorLabel: String? = null,
    ) : InkRoute

    /** 关系图：按当前书正文抽取，仅带姓名用于高亮；锚点年代随图页标注并下传详情。 */
    data class Relation(
        val name: String,
        val anchorYear: Int? = null,
        val anchorLabel: String? = null,
    ) : InkRoute

    /** 纪年时间轴：公元年（负 = 公元前）+ 可选轴标题。 */
    data class Timeline(val year: Int, val label: String?) : InkRoute
}
