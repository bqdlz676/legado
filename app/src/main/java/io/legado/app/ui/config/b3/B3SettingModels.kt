package io.legado.app.ui.config.b3

import io.legado.app.constant.PreferKey
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

/**
 * 整型坏值回落（O-13）：SP 中类型不符（Int 键被写成 String / 其他类型）时解析失败回落 [def]，
 * 不把 ClassCastException 抛到重组线程。纯函数，供桥监听回调与单测复用同一口径。
 */
fun fallbackB3Int(raw: Any?, def: Int): Int =
    (raw as? Int) ?: (raw as? String)?.trim()?.toIntOrNull() ?: def

/**
 * 字符串坏值回落（O-13）：String 去首尾空白后用之，空白串回落 [def]；其余类型回落 [def]。
 */
fun fallbackB3String(raw: Any?, def: String?): String? =
    (raw as? String)?.trim()?.ifBlank { def } ?: def

// ────────────────────────────────────────────────────────────────────────────
// R2 动效单源：行点触反馈回 MotionSpec 单源 PRESS（120ms 朱砂浸染），页面不自造时长 / 刚度裸值。
// ────────────────────────────────────────────────────────────────────────────

/** R2 行点触反馈动效名；对应 MotionSpec.PRESS，单测实调顶层纯函数取时长与弹簧规格。 */
const val B3_PRESS_MOTION: String = "PRESS"

// ────────────────────────────────────────────────────────────────────────────
// R6/R7/R8 音效镜像行：D27 后端落地前只留只读说明位，页面不持有第二份开关布尔。
// ────────────────────────────────────────────────────────────────────────────

/**
 * 音效反馈行口径描述。[readOnly] = 本行只能装配为只读说明行；后续接入 D27 同源读取位时，
 * 不得在本页预埋第二份本地布尔（R7：设置→阅读→底栏三态双向同步，状态源唯一）。
 */
data class B3SoundMirrorRow(val readOnly: Boolean, val keywords: List<String>)

/** 音效镜像行静态描述；keywords 与装配行别名一致，供搜索兜底命中与单测谓词复用。 */
val b3SoundMirrorRow: B3SoundMirrorRow = B3SoundMirrorRow(
    readOnly = true,
    keywords = listOf("sound", "音效", "声音", "InkSoundPool"),
)

// ────────────────────────────────────────────────────────────────────────────
// R9 WebDAV 测试连接结果文案（纯映射，动作侧只回 Boolean，文案收口在本函数）。
// ────────────────────────────────────────────────────────────────────────────

/**
 * R9 测试连接结果文案：连上显「已连接」；连不上显失败文案；null = 尚未测试，渲染层落占位空态。
 */
fun b3WebDavTestSummary(ok: Boolean?): String? = when (ok) {
    true -> "已连接"
    false -> "无法连接，检查地址和密码"
    null -> null
}

// ────────────────────────────────────────────────────────────────────────────
// R11 阅读字体档位：已存值回落口径（落盘键位取值依据见返工报告 §R11）。
// ────────────────────────────────────────────────────────────────────────────

/**
 * R11 字体档位回落：已存值命中条目则原样返回；未知名 / 空串 / null 回落 [fallback]。
 * 坏值防御：SP 被写成档位之外的字符串时不崩、不落错档。
 */
fun b3FontEntryFor(stored: String?, entries: List<String>, fallback: String): String {
    if (stored.isNullOrBlank()) return fallback
    return entries.firstOrNull { it == stored } ?: fallback
}

// ────────────────────────────────────────────────────────────────────────────
// R13 备份内容清单与冲突合并口径（原文-03:11-15 / :22）。
// ────────────────────────────────────────────────────────────────────────────

/**
 * 备份内容条目。[landed] = 底层 Backup 清单当前是否已纳入；false 为待备份 / 画板批次落地，
 * 页面文案不得把待纳入项宣称成已备份（文案与动作实现一致，不许只改文案）。
 */
data class B3BackupContent(val title: String, val landed: Boolean)

/** R13 规格四类同步内容；生字本 / 画板批注底层实体与导出分支待备份 / 画板批次落地。 */
val b3BackupContents: List<B3BackupContent> = listOf(
    B3BackupContent("阅读进度", true),
    B3BackupContent("生字本", false),
    B3BackupContent("画板批注", false),
    B3BackupContent("书签高亮", true),
)

/**
 * R13 备份行摘要：已纳入项前置，待纳入项显式标注「待纳入」，避免把未落地项写成已备份。
 */
fun b3BackupSummary(contents: List<B3BackupContent>): String {
    val landed = contents.filter { it.landed }.joinToString(" · ") { it.title }
    val pending = contents.filter { !it.landed }.joinToString(" · ") { it.title }
    return if (pending.isBlank()) landed else "$landed（待纳入：$pending）"
}

/**
 * R13 冲突合并口径：最后修改时间戳晚的覆盖早的；同戳保持本地；不做 diff 合并。
 * 纯函数，恢复侧（备份批次）接入本判定前，供页面契约与单测复用同一口径。
 */
fun b3PickLaterTimestamp(localTs: Long, backupTs: Long): Long =
    if (backupTs > localTs) backupTs else localTs

// ────────────────────────────────────────────────────────────────────────────
// R14 墨水屏模式四项口径（原文-05:39：关动画 / 关粒子 / 关 3D / 黑白灰配色）。
// ────────────────────────────────────────────────────────────────────────────

/**
 * 墨水屏单项描述。[key] 为既有偏好键；[landed] = 本页已有对应开关行。
 * 关粒子 / 关 3D 无既有偏好键，待地图 / 沙盘批次落键位后补开关行。
 */
data class B3EInkOption(val title: String, val key: String?, val landed: Boolean)

/** R14 四项口径，顺序与规格切片-05:39 一致。 */
val b3EInkOptions: List<B3EInkOption> = listOf(
    B3EInkOption("关动画", PreferKey.noAnimScrollPage, true),
    B3EInkOption("关粒子", null, false),
    B3EInkOption("关 3D", null, false),
    B3EInkOption("黑白灰配色", PreferKey.enableMangaGray, true),
)
