package io.legado.app.ui.config.b3

import io.legado.app.ui.config.configPreferenceMatches

/**
 * B3 设置页 · 行模型（纯 JVM，零 Android / Compose 依赖，便于单测断言结构）。
 *
 * 设计口径（切片-03 / 切片-25）：分组标题 14sp 石青色；行高 48dp；云灰细线分隔，
 * 不套 Material 卡片；开关 spring 弹动；列表项点击 120ms 朱砂反馈。行视觉一律收口到
 * [io.legado.app.ui.card.SettingRowCard] 实例卡，本模型只持有数据与动作契约，不写 UI 编排。
 *
 * 与旧 `settings/SettingModels.kt` 的关系：旧半成品属 F-73 冻结档，本模型是 B3 新链路的
 * 行口径；搜索匹配仍委托顶层 [configPreferenceMatches] 单一纯函数，不另造第二套规则（R19）。
 */

/** 设置行密封类型。 */
sealed interface B3Row

/**
 * 开关行：[key] 对应 SharedPreferences 布尔键；[def] 为缺省值。
 *
 * @param keywords 搜索别名：标题 / 说明之外的英文词、旧偏好名等，供搜索兜底命中。
 * @param onChange 开关落盘后的副作用回调；落盘由渲染层统一完成。
 */
data class B3SwitchRow(
    val key: String,
    val title: String,
    val summary: String? = null,
    val def: Boolean = false,
    val keywords: List<String> = emptyList(),
    val onChange: ((Boolean) -> Unit)? = null,
) : B3Row

/**
 * 取值 / 导航行：右侧展示 [summary] 当前值，点击触发 [onClick]。
 *
 * @param danger 危险动作标识；点击先经确认弹层二次拦截，确认后才执行（R18）。
 */
data class B3ValueRow(
    val title: String,
    val summary: String? = null,
    val keywords: List<String> = emptyList(),
    val danger: Boolean = false,
    val onClick: () -> Unit,
) : B3Row

/** 只读说明行：不可点，用于同步内容 / 冲突口径 / 预留行位等静态说明。 */
data class B3InfoRow(
    val title: String,
    val summary: String? = null,
    val keywords: List<String> = emptyList(),
) : B3Row

/** 分组：[title] 为空时不渲染分组标题行（对齐实例卡空标题口径）。 */
data class B3SettingGroup(
    val title: String?,
    val rows: List<B3Row>,
)

/**
 * 单行搜索命中：标题 / 说明 / 关键词任一子串包含，大小写不敏感。
 * 匹配口径完全委托顶层 [configPreferenceMatches]，渲染层不另写大小写 / 空白规则（R19）。
 */
fun B3Row.matchesB3Query(query: String): Boolean = when (this) {
    is B3SwitchRow -> configPreferenceMatches(query, title, summary, keywords)
    is B3ValueRow -> configPreferenceMatches(query, title, summary, keywords)
    is B3InfoRow -> configPreferenceMatches(query, title, summary, keywords)
}

/**
 * 分组裁剪：空串 / 纯空白 query 零过滤，原分组、原顺序、原可见性全部保留；
 * 非空才按行裁剪，整组无命中则隐藏（R19 现状分支口径）。
 */
fun filterB3Groups(groups: List<B3SettingGroup>, query: String): List<B3SettingGroup> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return groups
    return groups.mapNotNull { group ->
        val rows = group.rows.filter { it.matchesB3Query(normalized) }
        if (rows.isEmpty()) null else group.copy(rows = rows)
    }
}

/** 裁剪后是否仍有可见行；供无命中兜底空态判定，不留白页（R19）。 */
fun b3GroupsHaveMatch(groups: List<B3SettingGroup>, query: String): Boolean =
    filterB3Groups(groups, query).any { it.rows.isNotEmpty() }

/**
 * 档位钳制：[value] 越界时回落 [min] / [max] 边界值（R21 步进盘 min–max 钳制口径）。
 * 纯函数，无 Android 依赖，可直接单测。
 */
fun clampB3Int(value: Int, min: Int, max: Int): Int = when {
    value < min -> min
    value > max -> max
    else -> value
}

/**
 * 布尔坏值回落：SP 中类型不符（Boolean 键被写成 String）时解析失败回落 [def]（P9）。
 * 落盘读取的主路径走 `getBooleanCompat`（try/catch 回落并回写默认值）；本纯函数供
 * 非 Compose 语境与单测复用同一口径，不在页面各自 try-catch 第二份解析。
 */
fun fallbackB3Boolean(raw: Any?, def: Boolean): Boolean =
    (raw as? Boolean) ?: (raw as? String)?.trim()?.toBooleanStrictOrNull() ?: def
