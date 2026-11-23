package io.legado.app.ui.book.source.edit

import android.app.Application
import android.content.Intent
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.RuleComplete
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.storage.ImportOldData
import io.legado.app.model.SharedJsScope
import io.legado.app.ui.components.ToastKind
import io.legado.app.ui.components.ToastMessage
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.jsonPath
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 落文 · 书源编辑数据主控。
 *
 * 复用：Room DAO、规则补全 [RuleComplete]、导入解析 [ImportOldData]、Cookie / JS 作用域 /
 * 探索缓存清理；新写：草稿字段状态、选项面板状态、就地校验、装配到 [BookSource] 的纯数据变换。
 */
class SourceEditViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(SourceEditUiState())
    val uiState: StateFlow<SourceEditUiState> = _uiState.asStateFlow()

    private val autoComplete: Boolean
        get() = _uiState.value.autoComplete

    fun initData(intent: Intent, onFinally: () -> Unit) {
        execute {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            val source = sourceUrl?.let { appDb.bookSourceDao.getBookSource(it) }
            source
        }.onSuccess { source ->
            _uiState.update {
                it.copy(
                    source = source,
                    options = source?.toOptions() ?: EditOptions(),
                    fields = fieldsOf(source ?: BookSource()),
                    loading = false,
                )
            }
        }.onFinally { onFinally() }
    }

    // ───────────────────────────── 字段状态 ─────────────────────────────

    fun updateField(tab: EditTab, key: String, value: String?) {
        _uiState.update { state ->
            val list = state.fields[tab].orEmpty().map { field ->
                if (field.key == key) field.copy(value = value) else field
            }
            state.copy(fields = state.fields + (tab to list))
        }
    }

    fun updateOptions(transform: EditOptions.() -> EditOptions) {
        _uiState.update { it.copy(options = transform(it.options)) }
    }

    fun updateActiveField(tab: EditTab, key: String?) {
        _uiState.update { it.copy(activeField = key?.let { k -> tab to k }) }
    }

    /** 向当前聚焦字段追加 / 替换文本（URL 参数、分组、文件内容插入落点）。 */
    fun insertIntoActiveField(text: String) {
        val (tab, key) = _uiState.value.activeField ?: return
        val current = _uiState.value.field(tab, key)?.value.orEmpty()
        updateField(tab, key, current + text)
    }

    fun resolveActiveField(): Pair<EditTab, EditField>? {
        val (tab, key) = _uiState.value.activeField ?: return null
        return _uiState.value.field(tab, key)?.let { tab to it }
    }

    fun toggleAutoComplete() {
        _uiState.update { it.copy(autoComplete = !it.autoComplete) }
    }

    fun replaceSource(source: BookSource) {
        _uiState.update {
            it.copy(
                source = source,
                options = source.toOptions(),
                fields = fieldsOf(source),
                errors = emptyMap(),
            )
        }
    }

    // ───────────────────────────── 装配 / 校验 ─────────────────────────────

    /** 把当前草稿装配成 [BookSource]；规则补全口径与旧编辑页逐字段对齐。 */
    fun assemble(): BookSource {
        val state = _uiState.value
        val source = state.source?.copy() ?: BookSource()
        val options = state.options

        source.enabled = options.enabled
        source.enabledExplore = options.enabledExplore
        source.enabledCookieJar = options.enabledCookieJar
        source.bookSourceType = when (options.typeIndex) {
            4 -> BookSourceType.video
            3 -> BookSourceType.file
            2 -> BookSourceType.image
            1 -> BookSourceType.audio
            else -> BookSourceType.default
        }
        source.eventListener = options.eventListener
        source.customButton = options.customButton

        val searchRule = SearchRule()
        val exploreRule = ExploreRule()
        val bookInfoRule = BookInfoRule()
        val tocRule = TocRule()
        val contentRule = ContentRule()
        val reviewRule = source.ruleReview?.copy() ?: ReviewRule()
        reviewRule.enabled = options.enabledReview

        fun raw(tab: EditTab, key: String): String? =
            state.field(tab, key)?.value?.takeIf { it.isNotBlank() }

        // 基本信息
        raw(EditTab.BASE, "bookSourceUrl")?.let { source.bookSourceUrl = it }
        raw(EditTab.BASE, "bookSourceName")?.let { source.bookSourceName = it }
        source.bookSourceGroup = raw(EditTab.BASE, "bookSourceGroup")
        source.bookSourceComment = raw(EditTab.BASE, "bookSourceComment")
        source.loginUrl = raw(EditTab.BASE, "loginUrl")
        source.loginUi = raw(EditTab.BASE, "loginUi")
        source.loginCheckJs = raw(EditTab.BASE, "loginCheckJs")
        source.coverDecodeJs = raw(EditTab.BASE, "coverDecodeJs")
        source.bookUrlPattern = raw(EditTab.BASE, "bookUrlPattern")
        source.header = raw(EditTab.BASE, "header")
        source.variableComment = raw(EditTab.BASE, "variableComment")
        source.concurrentRate = raw(EditTab.BASE, "concurrentRate")
        source.jsLib = raw(EditTab.BASE, "jsLib")

        // 搜索
        source.searchUrl = raw(EditTab.SEARCH, "searchUrl")
        searchRule.checkKeyWord = raw(EditTab.SEARCH, "checkKeyWord")
        searchRule.bookList = raw(EditTab.SEARCH, "bookList")
        searchRule.name = ruleComplete(raw(EditTab.SEARCH, "name"), searchRule.bookList)
        searchRule.author = ruleComplete(raw(EditTab.SEARCH, "author"), searchRule.bookList)
        searchRule.kind = ruleComplete(raw(EditTab.SEARCH, "kind"), searchRule.bookList)
        searchRule.intro = ruleComplete(raw(EditTab.SEARCH, "intro"), searchRule.bookList)
        searchRule.wordCount = ruleComplete(raw(EditTab.SEARCH, "wordCount"), searchRule.bookList)
        searchRule.lastChapter = ruleComplete(raw(EditTab.SEARCH, "lastChapter"), searchRule.bookList)
        searchRule.coverUrl = ruleComplete(raw(EditTab.SEARCH, "coverUrl"), searchRule.bookList, 3)
        searchRule.bookUrl = ruleComplete(raw(EditTab.SEARCH, "bookUrl"), searchRule.bookList, 2)

        // 发现
        source.exploreUrl = raw(EditTab.EXPLORE, "exploreUrl")
        exploreRule.bookList = raw(EditTab.EXPLORE, "bookList")
        exploreRule.name = ruleComplete(raw(EditTab.EXPLORE, "name"), exploreRule.bookList)
        exploreRule.author = ruleComplete(raw(EditTab.EXPLORE, "author"), exploreRule.bookList)
        exploreRule.kind = ruleComplete(raw(EditTab.EXPLORE, "kind"), exploreRule.bookList)
        exploreRule.intro = ruleComplete(raw(EditTab.EXPLORE, "intro"), exploreRule.bookList)
        exploreRule.wordCount = ruleComplete(raw(EditTab.EXPLORE, "wordCount"), exploreRule.bookList)
        exploreRule.lastChapter = ruleComplete(raw(EditTab.EXPLORE, "lastChapter"), exploreRule.bookList)
        exploreRule.coverUrl = ruleComplete(raw(EditTab.EXPLORE, "coverUrl"), exploreRule.bookList, 3)
        exploreRule.bookUrl = ruleComplete(raw(EditTab.EXPLORE, "bookUrl"), exploreRule.bookList, 2)

        // 详情
        bookInfoRule.init = raw(EditTab.INFO, "init")
        bookInfoRule.name = ruleComplete(raw(EditTab.INFO, "name"), bookInfoRule.init)
        bookInfoRule.author = ruleComplete(raw(EditTab.INFO, "author"), bookInfoRule.init)
        bookInfoRule.kind = ruleComplete(raw(EditTab.INFO, "kind"), bookInfoRule.init)
        bookInfoRule.intro = ruleComplete(raw(EditTab.INFO, "intro"), bookInfoRule.init)
        bookInfoRule.wordCount = ruleComplete(raw(EditTab.INFO, "wordCount"), bookInfoRule.init)
        bookInfoRule.lastChapter = ruleComplete(raw(EditTab.INFO, "lastChapter"), bookInfoRule.init)
        bookInfoRule.coverUrl = ruleComplete(raw(EditTab.INFO, "coverUrl"), bookInfoRule.init, 3)
        bookInfoRule.tocUrl = ruleComplete(raw(EditTab.INFO, "tocUrl"), bookInfoRule.init, 2)
        bookInfoRule.canReName = raw(EditTab.INFO, "canReName")
        bookInfoRule.downloadUrls = ruleComplete(raw(EditTab.INFO, "downloadUrls"), bookInfoRule.init)

        // 目录
        tocRule.preUpdateJs = raw(EditTab.TOC, "preUpdateJs")
        tocRule.chapterList = raw(EditTab.TOC, "chapterList")
        tocRule.chapterName = ruleComplete(raw(EditTab.TOC, "chapterName"), tocRule.chapterList)
        tocRule.chapterUrl = ruleComplete(raw(EditTab.TOC, "chapterUrl"), tocRule.chapterList, 2)
        tocRule.formatJs = raw(EditTab.TOC, "formatJs")
        tocRule.isVolume = raw(EditTab.TOC, "isVolume")
        tocRule.updateTime = raw(EditTab.TOC, "updateTime")
        tocRule.isVip = raw(EditTab.TOC, "isVip")
        tocRule.isPay = raw(EditTab.TOC, "isPay")
        tocRule.nextTocUrl = ruleComplete(raw(EditTab.TOC, "nextTocUrl"), tocRule.chapterList, 2)

        // 正文
        contentRule.content = ruleComplete(raw(EditTab.CONTENT, "content"))
        contentRule.nextContentUrl = ruleComplete(raw(EditTab.CONTENT, "nextContentUrl"), type = 2)
        contentRule.subContent = ruleComplete(raw(EditTab.CONTENT, "subContent"))
        contentRule.title = ruleComplete(raw(EditTab.CONTENT, "title"))
        contentRule.webJs = raw(EditTab.CONTENT, "webJs")
        contentRule.sourceRegex = raw(EditTab.CONTENT, "sourceRegex")
        contentRule.replaceRegex = raw(EditTab.CONTENT, "replaceRegex")
        contentRule.imageStyle = raw(EditTab.CONTENT, "imageStyle")
        contentRule.imageDecode = raw(EditTab.CONTENT, "imageDecode")
        contentRule.payAction = raw(EditTab.CONTENT, "payAction")
        contentRule.callBackJs = raw(EditTab.CONTENT, "callBackJs")
        contentRule.contentBatch = raw(EditTab.CONTENT, "contentBatch")
        contentRule.maxBatchSize = raw(EditTab.CONTENT, "maxBatchSize")?.toIntOrNull()

        // 段评
        reviewRule.reviewSummaryUrl = raw(EditTab.REVIEW, "reviewSummaryUrl")
        reviewRule.summaryListRule = raw(EditTab.REVIEW, "summaryListRule")
        reviewRule.summaryParagraphIndexRule = raw(EditTab.REVIEW, "summaryParagraphIndexRule")
        reviewRule.summaryCountRule = raw(EditTab.REVIEW, "summaryCountRule")
        reviewRule.summaryParagraphDataRule = raw(EditTab.REVIEW, "summaryParagraphDataRule")
        reviewRule.reviewDetailUrl = raw(EditTab.REVIEW, "reviewDetailUrl")
        reviewRule.reviewDetailNextPageUrl = raw(EditTab.REVIEW, "reviewDetailNextPageUrl")
        reviewRule.detailListRule = raw(EditTab.REVIEW, "detailListRule")
        reviewRule.detailIdRule = raw(EditTab.REVIEW, "detailIdRule")
        reviewRule.detailAvatarRule = raw(EditTab.REVIEW, "detailAvatarRule")
        reviewRule.detailNameRule = raw(EditTab.REVIEW, "detailNameRule")
        reviewRule.detailBadgeRule = raw(EditTab.REVIEW, "detailBadgeRule")
        reviewRule.detailContentRule = raw(EditTab.REVIEW, "detailContentRule")
        reviewRule.reviewQuoteUrl = raw(EditTab.REVIEW, "reviewQuoteUrl")
        reviewRule.replyListRule = raw(EditTab.REVIEW, "replyListRule")
        reviewRule.replyIdRule = raw(EditTab.REVIEW, "replyIdRule")
        reviewRule.replyAvatarRule = raw(EditTab.REVIEW, "replyAvatarRule")
        reviewRule.replyNameRule = raw(EditTab.REVIEW, "replyNameRule")
        reviewRule.replyBadgeRule = raw(EditTab.REVIEW, "replyBadgeRule")
        reviewRule.replyContentRule = raw(EditTab.REVIEW, "replyContentRule")

        source.ruleSearch = searchRule
        source.ruleExplore = exploreRule
        source.ruleBookInfo = bookInfoRule
        source.ruleToc = tocRule
        source.ruleContent = contentRule
        source.ruleReview = reviewRule.takeIf {
            state.source?.ruleReview != null || it.enabled ||
                state.fields[EditTab.REVIEW].orEmpty().any { field -> !field.value.isNullOrBlank() }
        }
        return source
    }

    /** 就地校验：返回 key → 错误文案；空 Map 表示通过。 */
    fun validate(source: BookSource): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (source.bookSourceUrl.isBlank()) {
            errors["bookSourceUrl"] = context.getString(R.string.non_null_name_url)
        }
        if (source.bookSourceName.isBlank()) {
            errors["bookSourceName"] = context.getString(R.string.non_null_name_url)
        }
        _uiState.update { it.copy(errors = errors) }
        return errors
    }

    fun save(source: BookSource, success: ((BookSource) -> Unit)? = null) {
        execute {
            if (source.bookSourceUrl.isBlank() || source.bookSourceName.isBlank()) {
                throw NoStackTraceException(context.getString(R.string.non_null_name_url))
            }
            val oldSource = _uiState.value.source ?: BookSource()
            if (!source.equal(oldSource)) {
                source.lastUpdateTime = System.currentTimeMillis()
                if (oldSource.exploreUrl != source.exploreUrl) {
                    oldSource.clearExploreKindsCache()
                }
                if (oldSource.jsLib != source.jsLib) {
                    SharedJsScope.remove(oldSource.jsLib)
                }
            }
            _uiState.value.source?.let {
                if (it.bookSourceUrl != source.bookSourceUrl) {
                    SourceHelp.deleteBookSource(it.bookSourceUrl)
                } else {
                    appDb.bookSourceDao.delete(it)
                    SourceConfig.removeSource(it.bookSourceUrl)
                }
            }
            appDb.bookSourceDao.insert(source)
            _uiState.update { state -> state.copy(source = source) }
            concurrentRecordMap.remove(source.bookSourceUrl)
            source
        }.onSuccess {
            success?.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }
    }

    // ───────────────────────────── 导入 / 剪贴板 / Cookie ─────────────────────────────

    fun pasteSource(onSuccess: (BookSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为空")
            } else {
                importSource(text)
            }
        }.onSuccess { replaceSource(it); onSuccess(it) }
            .onError { context.toastOnUi(it.localizedMessage ?: "Error"); it.printOnDebug() }
    }

    fun importSource(text: String, finally: (source: BookSource) -> Unit) {
        execute { importSource(text) }
            .onSuccess { replaceSource(it); finally(it) }
            .onError { context.toastOnUi(it.localizedMessage ?: "Error"); it.printOnDebug() }
    }

    private suspend fun importSource(text: String): BookSource {
        return when {
            text.isAbsUrl() -> {
                val body = okHttpClient.newCallStrResponse { url(text) }.body
                    ?: throw NoStackTraceException("在线源无响应")
                importSource(body)
            }

            text.isJsonArray() -> {
                if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
                    val items: List<Map<String, Any>> = jsonPath.parse(text).read("$")
                    val jsonItem = jsonPath.parse(items[0])
                    ImportOldData.fromOldBookSource(jsonItem)
                } else {
                    GSON.fromJsonArray<BookSource>(text).getOrThrow()[0]
                }
            }

            text.isJsonObject() -> {
                if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
                    val jsonItem = jsonPath.parse(text)
                    ImportOldData.fromOldBookSource(jsonItem)
                } else {
                    GSON.fromJsonObject<BookSource>(text).getOrThrow()
                }
            }

            else -> throw NoStackTraceException("格式不对")
        }
    }

    fun clearCookie(url: String) {
        execute { CookieStore.removeCookie(url) }
    }

    fun ruleComplete(rule: String?, preRule: String? = null, type: Int = 1): String? {
        if (autoComplete) return RuleComplete.autoComplete(rule, preRule, type)
        return rule
    }

    fun setVariable(variable: String?) {
        _uiState.value.source?.setVariable(variable)
    }

    fun toJson(source: BookSource): String = GSON.toJson(source)

    fun postToast(kind: ToastKind, text: String) {
        val id = System.nanoTime()
        val factory = when (kind) {
            ToastKind.SUCCESS -> ToastMessage::success
            ToastKind.FAILURE -> ToastMessage::failure
            ToastKind.INFO -> ToastMessage::info
        }
        _uiState.update { it.copy(toasts = it.toasts + factory(id, text, null)) }
    }

    fun dismissToast(id: Long) {
        _uiState.update { it.copy(toasts = it.toasts.filterNot { t -> t.id == id }) }
    }

    // ───────────────────────────── 字段定义 ─────────────────────────────

    private fun fieldsOf(source: BookSource): Map<EditTab, List<EditField>> {
        val sr = source.getSearchRule()
        val er = source.getExploreRule()
        val ir = source.getBookInfoRule()
        val tr = source.getTocRule()
        val cr = source.getContentRule()
        val rr = source.ruleReview ?: ReviewRule()
        return mapOf(
            EditTab.BASE to listOf(
                EditField("bookSourceUrl", R.string.source_url, source.bookSourceUrl),
                EditField("bookSourceName", R.string.source_name, source.bookSourceName),
                EditField("bookSourceGroup", R.string.source_group, source.bookSourceGroup),
                EditField("bookSourceComment", R.string.comment, source.bookSourceComment),
                EditField("loginUrl", R.string.login_url, source.loginUrl),
                EditField("loginUi", R.string.login_ui, source.loginUi),
                EditField("loginCheckJs", R.string.login_check_js, source.loginCheckJs),
                EditField("coverDecodeJs", R.string.cover_decode_js, source.coverDecodeJs),
                EditField("bookUrlPattern", R.string.book_url_pattern, source.bookUrlPattern),
                EditField("header", R.string.source_http_header, source.header),
                EditField("variableComment", R.string.variable_comment, source.variableComment),
                EditField("concurrentRate", R.string.concurrent_rate, source.concurrentRate),
                EditField("jsLib", R.string.js_lib, source.jsLib),
            ),
            EditTab.SEARCH to listOf(
                EditField("searchUrl", R.string.r_search_url, source.searchUrl),
                EditField("checkKeyWord", R.string.check_key_word, sr.checkKeyWord),
                EditField("bookList", R.string.r_book_list, sr.bookList),
                EditField("name", R.string.r_book_name, sr.name),
                EditField("author", R.string.r_author, sr.author),
                EditField("kind", R.string.rule_book_kind, sr.kind),
                EditField("wordCount", R.string.rule_word_count, sr.wordCount),
                EditField("lastChapter", R.string.rule_last_chapter, sr.lastChapter),
                EditField("intro", R.string.rule_book_intro, sr.intro),
                EditField("coverUrl", R.string.rule_cover_url, sr.coverUrl),
                EditField("bookUrl", R.string.r_book_url, sr.bookUrl),
            ),
            EditTab.EXPLORE to listOf(
                EditField("exploreUrl", R.string.r_find_url, source.exploreUrl),
                EditField("bookList", R.string.r_book_list, er.bookList),
                EditField("name", R.string.r_book_name, er.name),
                EditField("author", R.string.r_author, er.author),
                EditField("kind", R.string.rule_book_kind, er.kind),
                EditField("wordCount", R.string.rule_word_count, er.wordCount),
                EditField("lastChapter", R.string.rule_last_chapter, er.lastChapter),
                EditField("intro", R.string.rule_book_intro, er.intro),
                EditField("coverUrl", R.string.rule_cover_url, er.coverUrl),
                EditField("bookUrl", R.string.r_book_url, er.bookUrl),
            ),
            EditTab.INFO to listOf(
                EditField("init", R.string.rule_book_info_init, ir.init),
                EditField("name", R.string.r_book_name, ir.name),
                EditField("author", R.string.r_author, ir.author),
                EditField("kind", R.string.rule_book_kind, ir.kind),
                EditField("wordCount", R.string.rule_word_count, ir.wordCount),
                EditField("lastChapter", R.string.rule_last_chapter, ir.lastChapter),
                EditField("intro", R.string.rule_book_intro, ir.intro),
                EditField("coverUrl", R.string.rule_cover_url, ir.coverUrl),
                EditField("tocUrl", R.string.rule_toc_url, ir.tocUrl),
                EditField("canReName", R.string.rule_can_re_name, ir.canReName),
                EditField("downloadUrls", R.string.download_url_rule, ir.downloadUrls),
            ),
            EditTab.TOC to listOf(
                EditField("preUpdateJs", R.string.pre_update_js, tr.preUpdateJs),
                EditField("chapterList", R.string.rule_chapter_list, tr.chapterList),
                EditField("chapterName", R.string.rule_chapter_name, tr.chapterName),
                EditField("chapterUrl", R.string.rule_chapter_url, tr.chapterUrl),
                EditField("formatJs", R.string.format_js_rule, tr.formatJs),
                EditField("isVolume", R.string.rule_is_volume, tr.isVolume),
                EditField("updateTime", R.string.rule_update_time, tr.updateTime),
                EditField("isVip", R.string.rule_is_vip, tr.isVip),
                EditField("isPay", R.string.rule_is_pay, tr.isPay),
                EditField("nextTocUrl", R.string.rule_next_toc_url, tr.nextTocUrl),
            ),
            EditTab.CONTENT to listOf(
                EditField("content", R.string.rule_book_content, cr.content),
                EditField("nextContentUrl", R.string.rule_next_content, cr.nextContentUrl),
                EditField("subContent", R.string.rule_sub_content, cr.subContent),
                EditField("replaceRegex", R.string.rule_replace_regex, cr.replaceRegex),
                EditField("title", R.string.rule_chapter_name, cr.title),
                EditField("sourceRegex", R.string.rule_source_regex, cr.sourceRegex),
                EditField("imageStyle", R.string.rule_image_style, cr.imageStyle),
                EditField("imageDecode", R.string.rule_image_decode, cr.imageDecode),
                EditField("webJs", R.string.rule_web_js, cr.webJs),
                EditField("payAction", R.string.rule_pay_action, cr.payAction),
                EditField("callBackJs", R.string.rule_call_back, cr.callBackJs),
                EditField("contentBatch", R.string.rule_content_batch, cr.contentBatch),
                EditField("maxBatchSize", R.string.rule_max_batch_size, cr.maxBatchSize?.toString()),
            ),
            EditTab.REVIEW to listOf(
                EditField("reviewSummaryUrl", R.string.rule_review_summary_url, rr.reviewSummaryUrl),
                EditField("summaryListRule", R.string.rule_review_summary_list, rr.summaryListRule),
                EditField("summaryParagraphIndexRule", R.string.rule_review_summary_id, rr.summaryParagraphIndexRule),
                EditField("summaryCountRule", R.string.rule_review_summary_count, rr.summaryCountRule),
                EditField("summaryParagraphDataRule", R.string.rule_review_summary_key, rr.summaryParagraphDataRule),
                EditField("reviewDetailUrl", R.string.rule_review_detail_url, rr.reviewDetailUrl),
                EditField("reviewDetailNextPageUrl", R.string.rule_review_detail_next_url, rr.reviewDetailNextPageUrl),
                EditField("detailListRule", R.string.rule_review_detail_list, rr.detailListRule),
                EditField("detailIdRule", R.string.rule_review_detail_id, rr.detailIdRule),
                EditField("detailAvatarRule", R.string.rule_review_detail_avatar, rr.detailAvatarRule),
                EditField("detailNameRule", R.string.rule_review_detail_name, rr.detailNameRule),
                EditField("detailBadgeRule", R.string.rule_review_detail_badge, rr.detailBadgeRule),
                EditField("detailContentRule", R.string.rule_review_detail_content, rr.detailContentRule),
                EditField("reviewQuoteUrl", R.string.rule_review_quote, rr.reviewQuoteUrl),
                EditField("replyListRule", R.string.rule_review_reply_list, rr.replyListRule),
                EditField("replyIdRule", R.string.rule_review_reply_id, rr.replyIdRule),
                EditField("replyAvatarRule", R.string.rule_review_reply_avatar, rr.replyAvatarRule),
                EditField("replyNameRule", R.string.rule_review_reply_name, rr.replyNameRule),
                EditField("replyBadgeRule", R.string.rule_review_reply_badge, rr.replyBadgeRule),
                EditField("replyContentRule", R.string.rule_review_reply_content, rr.replyContentRule),
            ),
        )
    }

    private fun BookSource.toOptions(): EditOptions = EditOptions(
        enabled = enabled,
        enabledExplore = enabledExplore,
        enabledCookieJar = enabledCookieJar ?: false,
        enabledReview = ruleReview?.enabled ?: false,
        eventListener = eventListener,
        customButton = customButton,
        typeIndex = when (bookSourceType) {
            BookSourceType.video -> 4
            BookSourceType.file -> 3
            BookSourceType.image -> 2
            BookSourceType.audio -> 1
            else -> 0
        },
    )
}
