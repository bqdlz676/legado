package io.legado.app.help.bubble

import android.content.Context
import splitties.init.appCtx

/**
 * 气泡层环境持有器。
 *
 * 各字典（字 / 词 / 典故 / 人物 / 地名 / 纪年）读 assets 都需要 [Context]，但冻结的
 * Provider 契约 [BubbleProvider.query] 不接收 Context；这里收口到唯一入口：
 *  - 真机：App 启动预热时调 [preload] 注入 applicationContext；未注入时回退 splitties [appCtx]；
 *  - 宿主 JVM 单测：appCtx 未初始化，[ctx] 返回 null，Provider 据此降级返回 null，
 *    由 Missing 兜底，不抛异常、不空白态。
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
