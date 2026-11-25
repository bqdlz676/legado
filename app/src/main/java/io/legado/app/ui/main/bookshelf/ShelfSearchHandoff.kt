package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.content.Intent
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.startActivity

/**
 * 跨页检索请求（纯 JVM 数据，不持有 Context / Compose 状态）。
 *
 * [key] 初始关键词：可空，空词时浮层落历史 / 热门空闲面板；
 * [scope] 搜索范围串：可空 = 全部启用书源；单源口径同 `SearchScope(source).toString()`
 * （`名称::url`），分组口径为逗号串。
 * [seq] 自增序号：浮层画面层按序号挂 `LaunchedEffect`，重复请求即使关键词相同也能再次触发。
 */
data class ShelfSearchRequest(
    val key: String?,
    val scope: String?,
    val seq: Long,
)

/**
 * 跨页检索改道单源（纯 JVM 单例，不持有 Context）。
 *
 * 背景：D25 之后搜索只收口到书架页下拉浮层，不再有独立搜索 Activity。发现页卡片、
 * 书详情作者 / 书名 / 标签、书源管理、阅读记录回搜、RSS JS 桥、外部分享等非书架页
 * 发起的全书库 / 单源检索，统一在此排队，由 [MainActivity] 切回书架 Tab 后交
 * [InkShelfFragment] 打开浮层。
 *
 * 冷启兜底：单例在进程死亡后清空，因此 [MainActivity] 同时从 Intent extras 重建请求，
 * 外部 App 冷启唤起分享入口时不依赖单例存活（见 [ShelfSearchRouter] 与 MainActivity
 * 的 `EXTRA_SEARCH_KEY / EXTRA_SEARCH_SCOPE`）。
 */
object ShelfSearchHandoff {

    @Volatile
    private var pending: ShelfSearchRequest? = null

    private var seq: Long = 0

    /** 排队一次跨页检索请求；后到的请求覆盖先到的（同一时刻只服务最新一次入口点击）。 */
    fun enqueue(key: String?, scope: String?) {
        synchronized(this) {
            seq += 1
            pending = ShelfSearchRequest(key, scope, seq)
        }
    }

    /** 取出并清空排队请求；书架 Fragment 建好后消费一次，不重复打开浮层。 */
    fun consume(): ShelfSearchRequest? = synchronized(this) {
        val current = pending
        pending = null
        current
    }
}

/**
 * 检索改道公开入口：任何页面要发起全书库 / 单源检索，统一走这里 → 书架页下拉浮层，
 * 不再 `startActivity` 独立搜索页。
 *
 * 车道已铺好的依据：浮层复用既有 `ui.main.search` 检索栈（`InkSearchViewModel.attach`
 * 支持初始词 / 初始范围，历史 / 热门 / 联网 / 分页同源），见 `修复-书架浮层搜索能力.md` 5.1。
 */
object ShelfSearchRouter {

    /**
     * 打开书架页并触发下拉浮层。
     *
     * @param context 发起页 Context；内部拉起 [MainActivity]（singleTask，已在前台时走 onNewIntent）。
     * @param key 初始关键词，可空。
     * @param scope 搜索范围串，可空 = 全部启用源。
     */
    fun openShelfSearch(context: Context, key: String? = null, scope: String? = null) {
        ShelfSearchHandoff.enqueue(key, scope)
        context.startActivity<MainActivity> {
            putExtra(MainActivity.EXTRA_SEARCH_KEY, key)
            putExtra(MainActivity.EXTRA_SEARCH_SCOPE, scope)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
