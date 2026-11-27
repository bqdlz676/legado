package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.ContentEditProjection

/**
 * Ink 内容编辑面板 · 纯逻辑规则层（JVM 可测，不触碰 Compose / Android 框架）。
 *
 * 〔为什么单独成层〕
 * 校验、脏数据判定、离场确认决策属于可判定纯逻辑，按验收金字塔下沉到普通 JVM 单测；
 * Compose 观感不上机不假称验证（见实现报告「未验证项」）。Composable 只做状态搬运与绘制，
 * 不在这里另写一套判定口径。
 *
 * 〔口径回源〕
 * 脏数据比对 / 离场自动保存语义对照旧 `ContentEditDialog.ContentDraftState`；本单按任务书
 * 改为「脏数据离场确认」，旧版取消即自动保存的行为不再沿用，差异在报告对照表登记。
 */
object InkContentEditRules {

    /**
     * 正文防御性上限（字符）。旧版编辑器未设显式上限；超长章节在多行输入框内会拖垮排版与
     * 滚动，取 20 万字兜底——超出给可读提示而非静默截断或崩溃。
     */
    const val MAX_CONTENT_LENGTH: Int = 200_000

    /** 章节标题防御性上限（字符）；旧版标题输入未设限，此处给同等宽松口径的夹取。 */
    const val MAX_TITLE_LENGTH: Int = 60

    /** 正文是否相对基线被改动（按原文逐字比对，不做 trim 归一化，避免首尾空白改动被吞掉）。 */
    fun contentDirty(baseline: String, current: String): Boolean = baseline != current

    /** 标题是否相对基线被改动；标题首尾空白无意义，按 trim 后比对。 */
    fun titleDirty(baseline: String, current: String): Boolean =
        baseline.trim() != current.trim()

    /** 面板整体脏数据：正文或标题任一相对基线有改动即为脏。 */
    fun isDirty(
        contentBaseline: String,
        contentCurrent: String,
        titleBaseline: String,
        titleCurrent: String,
    ): Boolean = contentDirty(contentBaseline, contentCurrent) ||
        titleDirty(titleBaseline, titleCurrent)

    /** 正文校验：空正文 / 超长 / 合法三态；空正文沿用旧版 `saveText` 空内容不落盘的口径，禁保存。 */
    fun validateContent(text: String, maxLength: Int = MAX_CONTENT_LENGTH): ContentValidation = when {
        text.length > maxLength -> ContentValidation.TooLong(text.length, maxLength)
        text.isBlank() -> ContentValidation.Empty
        else -> ContentValidation.Valid
    }

    /** 标题校验：空白保留旧标题；超长夹取提示；其余合法。 */
    fun validateTitle(title: String, maxLength: Int = MAX_TITLE_LENGTH): TitleValidation {
        val trimmed = title.trim()
        return when {
            trimmed.isEmpty() -> TitleValidation.Blank
            trimmed.length > maxLength -> TitleValidation.TooLong(trimmed.length, maxLength)
            else -> TitleValidation.Valid
        }
    }

    /**
     * 离场裁决：
     *  - 保存进行中 → [EditExitDecision.KEEP_OPEN]：半路离场会丢掉写盘结果，先按住面板；
     *  - 有改动 → [EditExitDecision.CONFIRM_DISCARD]：弹放弃确认，不直接丢草稿；
     *  - 干净 → [EditExitDecision.DISMISS]：直接关闭。
     */
    fun exitDecision(dirty: Boolean, saving: Boolean): EditExitDecision = when {
        saving -> EditExitDecision.KEEP_OPEN
        dirty -> EditExitDecision.CONFIRM_DISCARD
        else -> EditExitDecision.DISMISS
    }

    /**
     * 选区夹取（删除末段 / 切纯文本后旧选区越界兜底）：start、end 先各自收口到
     * 0..text.length，再保证 start ≤ end；返回夹取后的区间，供输入框回填选区。
     * 越界不抛异常、不归零整篇光标，空串时回落 0..0。
     */
    fun clampSelection(text: String, start: Int, end: Int): IntRange {
        val length = text.length
        val safeEnd = end.coerceIn(0, length)
        val safeStart = start.coerceIn(0, length).coerceAtMost(safeEnd)
        return safeStart..safeEnd
    }

    /**
     * 纯文本模式下的可见串 → 草稿原文映射。
     *
     * 〔为什么需要映射〕纯文本模式由 [ContentEditProjection] 隐藏图片 / 书评标记；用户在可见
     * 文本上的增删改，必须回填到保留隐藏标记的原文草稿上，不能整段重写抹掉隐藏标记。
     * 取新旧可见串的公共前缀 / 后缀，中段差异交 [ContentEditProjection.replace] 回填；
     * 前后缀无法对齐（差异区跨越隐藏标记边界等）时返回 null，调用方按兜底口径整段回写并登记。
     */
    // internal：参数类型 ContentEditProjection 是本模块 internal 类，public 函数会把内部类型
    // 外泄成新 API；同模块内 EditorBody 调用不受影响，故降可见性而非把类型提为 public。
    internal fun projectEdit(
        projection: ContentEditProjection,
        oldDisplay: String,
        newDisplay: String,
    ): String? {
        if (oldDisplay == newDisplay) return projection.raw
        val prefix = commonPrefix(oldDisplay, newDisplay)
        val suffix = commonSuffix(oldDisplay, newDisplay, prefix)
        val oldMidEnd = oldDisplay.length - suffix
        val newMidEnd = newDisplay.length - suffix
        if (prefix > oldMidEnd || prefix > newMidEnd) return null
        val newMid = newDisplay.substring(prefix, newMidEnd)
        return runCatching { projection.replace(prefix, oldMidEnd, newMid) }.getOrNull()
    }

    private fun commonPrefix(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffix(a: String, b: String, prefix: Int): Int {
        var i = 0
        val maxSuffix = minOf(a.length - prefix, b.length - prefix)
        while (i < maxSuffix && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }
}

/** 正文校验结果三态；[TooLong] 带实际长度与上限供提示文案拼装。 */
sealed interface ContentValidation {
    data object Valid : ContentValidation
    data object Empty : ContentValidation
    data class TooLong(val length: Int, val max: Int) : ContentValidation
}

/** 标题校验结果三态；[Blank] 时调用方保留旧标题不落库。 */
sealed interface TitleValidation {
    data object Valid : TitleValidation
    data object Blank : TitleValidation
    data class TooLong(val length: Int, val max: Int) : TitleValidation
}

/** 离场裁决结果；与 [InkContentEditRules.exitDecision] 一一对应。 */
enum class EditExitDecision { KEEP_OPEN, CONFIRM_DISCARD, DISMISS }
