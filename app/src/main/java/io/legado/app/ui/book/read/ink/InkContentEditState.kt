package io.legado.app.ui.book.read.ink

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 内容编辑面板状态持有者。
 *
 * 〔职责边界〕本类只持有草稿 / 基线 / 搜索 / 离场确认的组合状态；可判定的校验、脏数据、
 * 离场裁决全部下沉 [InkContentEditRules] 纯逻辑层，普通 JVM 单测可断言。数据读写走
 * [InkContentEditStore]，本类不直接碰 DAO / 磁盘。
 *
 * 〔进程重建兜底〕[saveToBundle] / [restoreFromBundle] 把草稿、标题、搜索态、纯文本开关落
 * `savedInstanceState`：配置变更后草稿不丢；进程死亡连同 savedInstanceState 一并丢失时，
 * 回退为重新从磁盘加载，空内容由面板空态兜底，不出空白、不崩溃。
 */
class InkContentEditState {

    var visible by mutableStateOf(false)
        private set

    var target by mutableStateOf<InkEditTarget?>(null)
        private set

    // ── 草稿与基线：基线用于脏数据比对，保存成功后基线随草稿刷新 ──
    var baselineContent by mutableStateOf("")
        private set
    var content by mutableStateOf("")
    var baselineTitle by mutableStateOf("")
        private set
    var title by mutableStateOf("")

    /** 标题是否处于行内编辑态；非编辑态标题行点击进入编辑。 */
    var titleEditing by mutableStateOf(false)

    // ── 加载 / 保存 / 消息 ──
    var loading by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<EditMessage?>(null)
        private set

    // ── 正文搜索态（对照旧版搜索栏：查询 / 正则 / 大小写 / 命中列表 / 当前锚点） ──
    var searchVisible by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var regex by mutableStateOf(false)
    var matchCase by mutableStateOf(false)
    var matches by mutableStateOf<List<IntRange>>(emptyList())
    var matchIndex by mutableStateOf(-1)
    var searchError by mutableStateOf<String?>(null)

    /** 纯文本模式开关（隐藏图片 / 书评标记）；持久化由宿主写偏好，本类只持当前值。 */
    var plainText by mutableStateOf(false)

    /** 脏数据离场确认层：true 时面板叠一层「放弃改动？」确认，不直接关闭。 */
    var confirmDiscard by mutableStateOf(false)
        private set

    /** 面板是否有未保存改动（正文或标题任一相对基线变动）。 */
    val dirty: Boolean
        get() = InkContentEditRules.isDirty(baselineContent, content, baselineTitle, title)

    /** 当前正文校验结果；保存按钮可用性由它 + 加载 / 保存态共同裁决。 */
    val contentValidation: ContentValidation
        get() = InkContentEditRules.validateContent(content)

    /** 打开面板：重置一轮会话状态，目标与初始标题由宿主从 [ReadBook] 灌入。 */
    fun open(target: InkEditTarget, title: String, plainText: Boolean) {
        this.target = target
        this.baselineContent = ""
        this.content = ""
        this.baselineTitle = title
        this.title = title
        this.titleEditing = false
        this.loading = true
        this.loadError = null
        this.saving = false
        this.message = null
        this.searchVisible = false
        this.searchQuery = ""
        this.regex = false
        this.matchCase = false
        this.matches = emptyList()
        this.matchIndex = -1
        this.searchError = null
        this.plainText = plainText
        this.confirmDiscard = false
        this.visible = true
    }

    /** 磁盘 / 书源内容就绪：草稿与基线同时落值，加载态收口。 */
    fun applyLoaded(content: String, title: String) {
        this.baselineContent = content
        this.content = content
        this.baselineTitle = title
        if (this.title.isBlank()) this.title = title
        this.loading = false
        this.loadError = null
    }

    /** 加载失败兜底：保留面板可见，错误文案由面板空态区呈现，不崩溃、不留白。 */
    fun applyLoadError(message: String) {
        this.loading = false
        this.loadError = message
    }

    /**
     * 标记保存中 / 保存收口。不命名为 `setSaving`：`var saving` 已由 Kotlin 合成 JVM 方法
     * `setSaving`，手写同名同签名函数会触发平台签名冲突（Platform declaration clash）。
     */
    fun markSaving(value: Boolean) {
        saving = value
    }

    /** 正文落盘成功后：基线随草稿刷新，避免刚保存又被判脏。 */
    fun markContentSaved() {
        baselineContent = content
    }

    /** 标题改名落盘成功后：基线标题随新标题刷新。 */
    fun markTitleSaved(title: String) {
        baselineTitle = title
        this.title = title
    }

    fun showMessage(kind: EditMessageKind, text: String) {
        message = EditMessage(kind, text)
    }

    fun clearMessage() {
        message = null
    }

    /** 搜索命中回填；index 为当前命中在列表中的下标，-1 表示无命中。 */
    fun setMatches(matches: List<IntRange>, index: Int) {
        this.matches = matches
        this.matchIndex = index
    }

    /**
     * 回填正文搜索错误文案。不命名为 `setSearchError`：`var searchError` 已合成同名 JVM
     * setter，手写函数会撞平台签名；改名仅避冲突，赋值行为与时序不变。
     */
    fun updateSearchError(error: String?) {
        searchError = error
    }

    /**
     * 标题行进入 / 退出行内编辑。不命名为 `setTitleEditing`：`var titleEditing` 已合成同名
     * JVM setter，手写函数会撞平台签名；后缀 `State` 仅作命名区分，不改行为与时序。
     */
    fun setTitleEditingState(editing: Boolean) {
        titleEditing = editing
    }

    /**
     * 离场请求：交规则层裁决。需确认时挂起确认层不关闭；可直接关闭时收口会话。
     * 返回裁决结果供宿主决定是否还要做侧栏动作（如旧版取消自动保存，本单不再沿用）。
     */
    fun requestDismiss(): EditExitDecision {
        val decision = InkContentEditRules.exitDecision(dirty, saving)
        when (decision) {
            EditExitDecision.KEEP_OPEN -> Unit
            EditExitDecision.CONFIRM_DISCARD -> confirmDiscard = true
            EditExitDecision.DISMISS -> close()
        }
        return decision
    }

    /** 确认放弃改动并关闭。 */
    fun confirmDiscardClose() {
        confirmDiscard = false
        close()
    }

    /** 取消放弃确认，留在面板继续编辑。 */
    fun cancelDiscard() {
        confirmDiscard = false
    }

    /** 收口会话：隐藏面板并清目标；草稿随会话丢弃，脏数据已在 [requestDismiss] 确认过。 */
    fun close() {
        visible = false
        confirmDiscard = false
        target = null
    }

    // ── 进程重建快照（前缀隔离，避免与宿主其它 savedInstanceState 键串味） ──

    fun saveToBundle(out: Bundle, prefix: String) {
        out.putBoolean("$prefix.visible", visible)
        target?.let {
            out.putString("$prefix.bookUrl", it.bookUrl)
            out.putInt("$prefix.chapterIndex", it.chapterIndex)
            out.putInt("$prefix.chapterPos", it.chapterPos)
        }
        out.putString("$prefix.baselineContent", baselineContent)
        out.putString("$prefix.content", content)
        out.putString("$prefix.baselineTitle", baselineTitle)
        out.putString("$prefix.title", title)
        out.putBoolean("$prefix.searchVisible", searchVisible)
        out.putString("$prefix.searchQuery", searchQuery)
        out.putBoolean("$prefix.regex", regex)
        out.putBoolean("$prefix.matchCase", matchCase)
        out.putBoolean("$prefix.plainText", plainText)
        out.putBoolean("$prefix.loading", loading)
    }

    /** 从快照恢复；返回 true 表示面板此前处于打开态，宿主据此决定是否跳过二次磁盘加载。 */
    fun restoreFromBundle(state: Bundle?, prefix: String): Boolean {
        state ?: return false
        if (!state.getBoolean("$prefix.visible", false)) return false
        val bookUrl = state.getString("$prefix.bookUrl").orEmpty()
        val chapterIndex = state.getInt("$prefix.chapterIndex", 0)
        val chapterPos = state.getInt("$prefix.chapterPos", 0)
        target = InkEditTarget(bookUrl, chapterIndex, chapterPos)
        baselineContent = state.getString("$prefix.baselineContent").orEmpty()
        content = state.getString("$prefix.content").orEmpty()
        baselineTitle = state.getString("$prefix.baselineTitle").orEmpty()
        title = state.getString("$prefix.title").orEmpty()
        searchVisible = state.getBoolean("$prefix.searchVisible", false)
        searchQuery = state.getString("$prefix.searchQuery").orEmpty()
        regex = state.getBoolean("$prefix.regex", false)
        matchCase = state.getBoolean("$prefix.matchCase", false)
        plainText = state.getBoolean("$prefix.plainText", false)
        loading = state.getBoolean("$prefix.loading", true)
        visible = true
        return true
    }
}

/** 面板轻提示类别：成功暖金 / 失败朱砂 / 提示石青，与 InkToast 三色口径一致。 */
enum class EditMessageKind { SUCCESS, FAILURE, INFO }

data class EditMessage(val kind: EditMessageKind, val text: String)
