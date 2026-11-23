package io.legado.app.help.bubble

import android.content.Context
import splitties.init.appCtx

/**
 * 气泡层环境持有器。
 *
 * D1~D4 / C1 上游字典均为纯 Kotlin 离线数据层，查询本身不依赖 Context；
 * 只有兜底入口需要 Context：生字本落库（SharedPreferences）与联网检索（ACTION_VIEW）。
 * 这里收口到唯一入口：
 *  - 真机：App 启动预热时调 [preload] 注入 applicationContext；未注入时回退 splitties [appCtx]；
 *  - 宿主 JVM 单测：appCtx 未初始化，[ctx] 返回 null，兜底入口据此返回 false / null，
 *    不抛异常、不空白态。
 *
 * 任何 Provider 都不许自行缓存 Context 之外的全局状态；同名消歧与年代锚点一律走
 * [io.legado.app.help.chrono.ContextAnchor] 共享总线。
 */
internal object BubbleEnv {

    @Volatile
    private var appContext: Context? = null

    /** 启动预热时注入 applicationContext（幂等，可重复调用）。 */
    fun preload(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 取当前可用 Context；不可用（纯 JVM 单测 / 未预热）返回 null。
     * 返回的一定是 applicationContext，避免 Activity 泄漏。
     */
    fun ctx(): Context? {
        appContext?.let { return it }
        return runCatching { appCtx }.getOrNull()
    }
}
