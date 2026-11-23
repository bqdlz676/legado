package io.legado.app.ui.config.settings

/**
 * 落文 · 设置页行模型（纯 JVM，零 Android / Compose 类型，便于单测断言结构）。
 *
 * 旧版用 androidx Preference XML 描述每一行；新实现把「行」抽成下列密封类型，
 * 由 [SettingList] 统一渲染。颜色 / 字体 / 动效全部取自组件库 token，不在此处造色值。
 *
 * 设计口径（切片-03 设置页）：分组标题 14sp 石青色；行高 ≥48dp；云灰细线分隔分组，
 * 不套 Material 卡片；开关 spring 弹动；列表项点击 120ms 朱砂反馈。
 */

/** 设置行密封类型。 */
sealed interface SettingRow

/** 分组标题行（不参与点击）。 */
data class GroupHeader(val title: String) : SettingRow

/** 开关行：[key] 对应 SharedPreferences 布尔键；[def] 为缺省值。 */
data class SwitchRow(
    val key: String,
    val title: String,
    val summary: String? = null,
    val def: Boolean = false,
    val enabled: Boolean = true,
    /** 开关变化后的副作用（服务启停 / 主题刷新等）；落盘由渲染层统一完成。 */
    val onChange: ((Boolean) -> Unit)? = null,
) : SettingRow

/** 取值行：右侧显示 [summary] 或当前值，点击触发 [onClick]（弹数字盘 / 文本框 / 选择器 / 跳转）。 */
data class ValueRow(
    val title: String,
    val summary: String? = null,
    val key: String? = null,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val onClick: () -> Unit,
) : SettingRow

/** 列表选择行：[entries] 展示文案，[values] 落盘值；[def] 缺省值。 */
data class ListRow(
    val key: String,
    val title: String,
    val summary: String? = null,
    val entries: List<String>,
    val values: List<String>,
    val def: String,
    val onChange: (String) -> Unit,
) : SettingRow

/** 分组：[title] 为空时不渲染分组标题。 */
data class SettingGroup(val title: String?, val rows: List<SettingRow>)
