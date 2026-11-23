package io.legado.app.ui.book.read.ink

import android.content.Context
import android.content.Intent
import io.legado.app.help.bubble.Action
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.ui.main.PersonDetailActivity
import io.legado.app.ui.main.PlaceMapActivity
import io.legado.app.ui.main.RelationActivity

/**
 * 气泡动作分发层（切片-02 §1 Action 冻结四类）。
 *
 * 职责边界：本层只做「动作 → 目标页 / 卡片」的路由与 Intent 装配；
 * 目标页内部的数据加载、消歧、渲染由各页自己负责。未接通的动作 [route] 返回 null，
 * 气泡侧 chip 据此置灰不可点，不呈现为可点击。
 *
 * 路由口径（冻结协议）：
 * - [Action.OpenMap]      → 古今地图 [PlaceMapActivity]，地名优先取 placeName，缺省回退 placeId；
 *                           年代取共享锚点 [ContextAnchor] 当前年。
 * - [Action.OpenPerson]   → 人物档案 [PersonDetailActivity]，personId 随 extra 透传（页面暂按名检索）。
 * - [Action.OpenRelation] → 人物关系图 [RelationActivity]，图由页面按当前书正文抽取。
 * - [Action.OpenTimeline] → 纪年时间轴 [InkTimelineActivity]，year 为公元年（负 = 公元前）。
 *
 * 「联网检索 / 加入生字本」不是导航动作，由 [BubbleContent.allowWebSearch] /
 * [BubbleContent.allowNotebook] 标志位驱动，走壳层 onFallback 通道，不进本分发层。
 */
object InkActionDispatcher {

    /**
     * 动作 → 路由映射（纯函数，可单测）。
     * @param anchorYear 共享时空锚点当前年；null 表示锚点未定年。
     * @return 目标路由；null = 本动作暂无接通消费方，UI 应禁用对应按钮。
     */
    fun route(action: Action, anchorYear: Int?): InkRoute? = when (action) {
        is Action.OpenMap -> InkRoute.Map(
            placeName = action.placeName?.takeIf { it.isNotBlank() } ?: action.placeId,
            year = anchorYear,
        )

        is Action.OpenPerson -> InkRoute.Person(
            name = action.name,
            personId = action.personId,
        )

        is Action.OpenRelation -> InkRoute.Relation(name = action.name)

        is Action.OpenTimeline -> InkRoute.Timeline(year = action.year, label = action.label)
    }

    /** 该动作是否已有真实消费方；气泡 chip 启用 / 置灰据此判定。 */
    fun canDispatch(action: Action, anchorYear: Int? = ContextAnchor.getYear()): Boolean =
        route(action, anchorYear) != null

    /**
     * 分发动作；接通则拉起目标页并返回 true，未接通返回 false（调用方不应再做兜底跳转）。
     */
    fun dispatch(context: Context, action: Action): Boolean {
        val route = route(action, ContextAnchor.getYear()) ?: return false
        val intent = buildIntent(context, route)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            // 目标页缺失 / 进程受限等极端情况：不崩，回退未接通语义。
            return false
        }
        return true
    }

    /** 路由 → Intent 装配（单独拆出便于静态走查与单测核对 extra 键）。 */
    fun buildIntent(context: Context, route: InkRoute): Intent = when (route) {
        is InkRoute.Map -> Intent(context, PlaceMapActivity::class.java).apply {
            putExtra("name", route.placeName)
            route.year?.let { putExtra("year", it) }
        }

        is InkRoute.Person -> Intent(context, PersonDetailActivity::class.java).apply {
            putExtra("name", route.name)
            route.personId?.let { putExtra("personId", it) }
        }

        is InkRoute.Relation -> Intent(context, RelationActivity::class.java)

        is InkRoute.Timeline -> Intent(context, InkTimelineActivity::class.java).apply {
            putExtra("year", route.year)
            route.label?.let { putExtra("label", it) }
        }
    }
}

/** 分发层中间路由模型：与具体 Activity 解耦，便于离线单测核对映射结果。 */
sealed interface InkRoute {
    data class Map(val placeName: String, val year: Int?) : InkRoute
    data class Person(val name: String, val personId: String?) : InkRoute
    data class Relation(val name: String) : InkRoute
    data class Timeline(val year: Int, val label: String?) : InkRoute
}
