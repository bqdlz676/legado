package io.legado.app.ui.autoTask

import io.legado.app.constant.AppConst
import io.legado.app.data.entities.AutoTaskRule

/** 从未运行时的行内文案；不允许空白 / 破折号 / 1970 时间戳糊过去。 */
internal const val LAST_RUN_NEVER = "未运行"

/**
 * 列表行"最近运行时间"纯函数（C2R6）。
 *
 * - [lastRunAt] <= 0L 视为从未运行，返回 [LAST_RUN_NEVER]；
 * - 否则由 [format] 注入既有时间格式工具（生产为 [AppConst.dateFormat]，与旧版
 *   `AutoTaskAdapter` 同口径绝对时间 `yyyy/MM/dd HH:mm`）；函数本身不触 Android
 *   资源，便于 JVM 单测注入桩格式器。
 */
internal fun formatLastRunLabel(lastRunAt: Long, format: (Long) -> String): String =
    if (lastRunAt > 0L) format(lastRunAt) else LAST_RUN_NEVER

/**
 * 落文 · 自动任务列表 UI 模型（实体展示镜像，纯数据）。
 */
data class AutoTaskUi(
    val id: String,
    val name: String,
    val cron: String,
    val script: String,
    val comment: String?,
    val enable: Boolean,
    val enabledCookieJar: Boolean,
    val header: String?,
    val jsLib: String?,
    val concurrentRate: String?,
    val loginUrl: String?,
    val loginUi: String?,
    val loginCheckJs: String?,
    val customOrder: Int,
    val lastLog: String?,
    val lastError: String?,
    val lastResult: String?,
    val lastRunAt: Long,
) {
    constructor(rule: AutoTaskRule) : this(
        id = rule.id,
        name = rule.name,
        cron = rule.cron.orEmpty(),
        script = rule.script,
        comment = rule.comment,
        enable = rule.enable,
        enabledCookieJar = rule.enabledCookieJar,
        header = rule.header,
        jsLib = rule.jsLib,
        concurrentRate = rule.concurrentRate,
        loginUrl = rule.loginUrl,
        loginUi = rule.loginUi,
        loginCheckJs = rule.loginCheckJs,
        customOrder = rule.customOrder,
        lastLog = rule.lastLog,
        lastError = rule.lastError,
        lastResult = rule.lastResult,
        lastRunAt = rule.lastRunAt,
    )

    /** 行内"最近运行时间"文案；无执行记录 → [LAST_RUN_NEVER]。 */
    val lastRunLabel: String
        get() = formatLastRunLabel(lastRunAt) { AppConst.dateFormat.format(it) }

    fun toEntity(): AutoTaskRule = AutoTaskRule(
        id = id,
        name = name,
        cron = cron,
        script = script,
        comment = comment,
        enable = enable,
        enabledCookieJar = enabledCookieJar,
        header = header,
        jsLib = jsLib,
        concurrentRate = concurrentRate,
        loginUrl = loginUrl,
        loginUi = loginUi,
        loginCheckJs = loginCheckJs,
        customOrder = customOrder,
        lastLog = lastLog,
        lastError = lastError,
        lastResult = lastResult,
        lastRunAt = lastRunAt,
    )
}

data class AutoTaskListState(
    val rules: List<AutoTaskUi> = emptyList(),
    val query: String = "",
    val selection: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
    val confirmDelete: AutoTaskUi? = null,
    val logTarget: AutoTaskUi? = null,
    val batchCronOpen: Boolean = false,
    val importUrlOpen: Boolean = false,
    val helpText: String? = null,
) {
    val selectionCount: Int get() = selection.size
    val selectionMode: Boolean get() = selection.isNotEmpty()
}
