package io.legado.app.ui.book.read.ink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookHighlight
import io.legado.app.data.entities.BookProgress
import io.legado.app.help.bubble.BubbleResolver
import io.legado.app.help.bubble.NotebookStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.utils.eventObservable
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.R
import kotlinx.coroutines.launch

/**
 * 落文 · 竖排阅读壳宿主（U5-A 灵魂闭环入口）。
 *
 * 职责：把 Legado 既有「书 / 章 / 正文」加载管线（[ReadBook] + [ReadBookViewModel]）
 * 接到 Compose 阅读壳 [InkReaderBook] 上——本类只做状态搬运与导航接线，
 * 不复制排版 / 解析逻辑；竖排分页、气泡解析分别由 vertical / bubble 冻结引擎完成。
 *
 * 闭环：书架 / 详情页带 bookUrl 进入本页 → ViewModel 初始化书章 →
 * [ReadBook.CallBack] 回填章节正文 → [InkReaderBook] 竖排渲染 →
 * 点字出气泡 → 动作经 [InkActionDispatcher] 分发地图 / 人物 / 纪年 / 关系页。
 *
 * 性能（切片-08）：前后相邻章由 ReadBook 预载，翻章即取已就绪邻章，翻章 0 白屏。
 *
 * 兜底：章节尚未加载完成时正文为空串，壳与引擎按空页兜底，不出空白态、不崩；
 * 正文加载完成后回调刷新，状态自动回填。
 */
class InkReadActivity : AppCompatActivity(), ContentTextView.CallBack {

    private val viewModel: ReadBookViewModel by viewModels()

    /**
     * 阅读控制器提升为 Activity 字段：硬件键 / 媒体键翻页通道在 Composable 之外也要能拿到，
     * 故不再由 remember 在组合内创建，改在 onCreate 实例化后传入 [InkReaderBook]。
     */
    private lateinit var controller: InkReadController

    /**
     * 内容编辑面板状态与数据桥（B31 承接件第 1 件）。
     * 菜单通道 [onMenu] 打开本面板；草稿 / 搜索 / 离场确认状态在 [contentEditState]，
     * 磁盘 / 书源读写收口在 [contentEditStore]，本 Activity 只做状态搬运与回调接线。
     */
    private val contentEditState = InkContentEditState()
    private val contentEditStore = InkContentEditStore()

    /** 进程重建后是否已带草稿恢复；带草稿则跳过二次磁盘加载，避免覆盖未保存改动。 */
    private var editRestoredWithDraft = false

    /**
     * MEDIA_BUTTON 广播接收器（交接清单 #11 遗留：新壳此前未订阅媒体键）。
     * 耳机线控 / 蓝牙遥控的上一曲 / 下一曲在此翻页，播放暂停键交朗读切换；
     * 键码 → 命令的纯解析规则在 [InkPageKeys]，本接收器只取 KeyEvent 转发。
     */
    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return
            @Suppress("DEPRECATION")
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            controller.handleMediaKey(event.keyCode)
        }
    }

    // ── 书 / 章状态真值唯一在 ReadBook（R5，B4-06）；本 Activity 不再存第二份镜像 ──

    private val tocLauncher = registerForActivityResult(TocActivityResult()) { result ->
        result ?: return@registerForActivityResult
        val index = result[0] as Int
        val chapterPos = result[1] as Int
        val highlightLayoutTitleLength = (result.getOrNull(5) as? Int)?.takeIf { it >= 0 }
        val highlightAnchorText = result.getOrNull(6) as? String
        val pdfPageIndex = (result.getOrNull(7) as? Int)?.takeIf { it >= 0 }
        viewModel.openChapter(
            index = index,
            durChapterPos = chapterPos,
            highlightLayoutTitleLength = highlightLayoutTitleLength,
            highlightAnchorText = highlightAnchorText,
            pdfPageIndex = pdfPageIndex,
        )
    }

    /**
     * ReadBook 全局回调适配：只承接「章节内容就绪 / 翻页 / 书变更」等刷新信号，
     * 其余 Legacy 菜单 / 排版回调在新壳内无对应物，留空实现不抢手势。
     */
    private val readCallback = object : ReadBook.CallBack {
        override fun upMenuView() = Unit

        override fun loadChapterList(book: Book) = Unit

        override fun upContent(
            relativePosition: Int,
            resetPageOffset: Boolean,
            readPositionVersion: Long?,
            success: (() -> Unit)?,
        ) {
            refreshFromReadBook()
            success?.invoke()
        }

        override suspend fun upContentAwait(
            relativePosition: Int,
            resetPageOffset: Boolean,
            readPositionVersion: Long?,
            success: (() -> Unit)?,
        ) {
            refreshFromReadBook()
            success?.invoke()
        }

        override fun pageChanged() {
            refreshFromReadBook()
        }

        override fun contentLoadFinish() {
            refreshFromReadBook()
        }

        override fun upPageAnim(upRecorder: Boolean) = Unit

        override fun notifyBookChanged() {
            refreshFromReadBook()
        }

        override fun sureNewProgress(progress: BookProgress) = Unit

        override fun cancelSelect() = Unit

        override fun onLayoutPageCompleted(index: Int, page: io.legado.app.ui.book.read.page.entities.TextPage) = Unit

        override fun onLayoutCompleted() {
            refreshFromReadBook()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 宋体注入：先探 assets/fonts/ 内置子集，缺失则回退系统 serif（子集待 U9 收口）。
        InkFonts.ensureSerifLoaded(applicationContext)

        // 预热字典 / 纪年 / 人物 / 地名 Provider，避免首点气泡冷启动卡顿。
        runCatching { BubbleResolver.preload(this) }

        ReadBook.register(readCallback)
        viewModel.initData(intent)
        refreshFromReadBook()

        // 进程重建恢复编辑面板草稿：带草稿直接沿用；无草稿（加载中被重建）回磁盘重载兜底。
        val editRestored = contentEditState.restoreFromBundle(savedInstanceState, STATE_EDIT_PANEL)
        editRestoredWithDraft = editRestored && contentEditState.content.isNotEmpty()
        if (editRestored && !editRestoredWithDraft) reloadContentEdit()

        // 控制器在组合外实例化，供 onKeyDown / 媒体键通道直接调用；朗读切换回调接服务起停。
        controller = InkReadController(this)
        controller.onReadAloudToggle = {
            if (BaseReadAloudService.isRun) ReadAloud.pause(this) else ReadAloud.play(this)
        }

        // 返回键 / 系统手势返回统一出口：层级裁决全部在 controller.handleBack()，
        // 与手势路由 GestureRouter.onBack 走同一函数；Activity 只按裁决结果做副作用
        // （暂停提示 / 退出回书架），不在此复制气泡 / 朗读 / 偏好判定。
        onBackPressedDispatcher.addCallback(this) {
            when (controller.handleBack()) {
                InkBackResult.BubbleDismissed -> Unit
                InkBackResult.ReadAloudPaused -> toastOnUi(R.string.read_aloud_pause)
                InkBackResult.ReturnKeyLocked -> Unit
                InkBackResult.ExitToShelf -> finish()
            }
        }

        // MEDIA_BUTTON 总线订阅（对齐旧 ReadBookActivity 的 observeEvent(MEDIA_BUTTON)）：
        // true = 起朗读；false = 按当前暂停态反切。观察者随 Activity 生命周期自动注销。
        eventObservable<Boolean>(EventBus.MEDIA_BUTTON).observe(this, Observer { playing ->
            if (playing) {
                ReadAloud.play(this)
            } else {
                ReadBook.readAloud(!BaseReadAloudService.pause)
            }
        })

        setContent {
            // 兜底入口真实接线：web → 浏览器网页搜索当前气泡词条；notebook → 生字本本地落库并回执。
            val fallbackHandler: (String) -> Unit = fallbackHandler@{ kind ->
                val token = controller.bubbleContent?.token?.trim().orEmpty()
                if (token.isEmpty()) return@fallbackHandler
                when (kind) {
                    "web" -> runCatching {
                        val url = "https://www.baidu.com/s?wd=" + Uri.encode(token)
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    "notebook" -> {
                        val ok = NotebookStore.add(
                            context = this@InkReadActivity,
                            token = token,
                            bookName = ReadBook.book?.name.orEmpty(),
                            chapterTitle = ReadBook.curTextChapter?.title.orEmpty(),
                        )
                        Toast.makeText(
                            this@InkReadActivity,
                            if (ok) "已加入生字本" else "生字本已有该条",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }

            InkReaderBook(
                onCatalog = { tocLauncher.launch(ReadBook.book?.bookUrl.orEmpty()) },
                onTts = { runCatching { ReadAloud.play(this@InkReadActivity) } },
                // 菜单通道：打开内容编辑半屏面板（B31 承接件第 1 件）；其余排版 / 字体菜单后续批次接入。
                onMenu = { openContentEdit() },
                onSeek = { fraction -> controller.seekTo(fraction) },
                controller = controller,
                onFallback = fallbackHandler,
            )

            // 内容编辑面板覆盖层：状态在 contentEditState，数据读写走 contentEditStore。
            InkContentEditSheet(
                state = contentEditState,
                onSave = { saveContentEdit() },
                onReset = { resetContentEdit() },
                onRename = { renameContentEdit(it) },
                onCopyAll = { copyAllContent() },
                onTogglePlainText = { togglePlainText(it) },
                onReload = { reloadContentEdit() },
                onDismiss = { handleContentEditDismiss() },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // 动态注册媒体键接收器；NOT_EXPORTED：只消费系统 MEDIA_BUTTON 广播，不对外暴露入口。
        ContextCompat.registerReceiver(
            this,
            mediaButtonReceiver,
            IntentFilter(Intent.ACTION_MEDIA_BUTTON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(mediaButtonReceiver) }
        super.onStop()
    }

    /**
     * 硬件键翻页通道：音量键 / 方向键 / 翻页键 / 空格上翻下翻；未识别键交系统默认处理。
     * 键位解析与翻页门禁在控制器纯逻辑层，此处只转发键码，不重复判定手势分区。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (controller.handleHardwareKey(keyCode, event.repeatCount)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        ReadBook.unregister(readCallback)
        // 控制器摘挂回路（B4-05 验收遗留 O1）：清两级缓存 / 引擎缓存与 View 监听闭包，
        // 避免控制器闭包持有已销毁宿主；ReadBook 全局单例反注册在上一行已完成。
        if (::controller.isInitialized) controller.release()
        super.onDestroy()
    }

    // ── 内容编辑面板接线（B31 承接件第 1 件） ──────────────────────────────

    /** 菜单入口：从 [ReadBook] 取当前书 / 章 / 标题构造编辑目标，打开面板并回读磁盘正文。 */
    private fun openContentEdit() {
        val book = ReadBook.book ?: return
        val chapterIndex = ReadBook.durChapterIndex
        val title = ReadBook.curTextChapter
            ?.takeIf { it.chapter.bookUrl == book.bookUrl && it.chapter.index == chapterIndex }
            ?.title
            ?: book.durChapterTitle?.takeIf { book.durChapterIndex == chapterIndex }
            ?: ""
        val target = InkEditTarget(book.bookUrl, chapterIndex, ReadBook.durChapterPos)
        contentEditState.open(target, title.orEmpty(), getPrefBoolean(PREF_PLAIN_TEXT, false))
        reloadContentEdit()
    }

    /** 回读当前章可编辑正文；书 / 章 / 内容缺失与异常各走可读兜底，不留白、不崩。 */
    private fun reloadContentEdit() {
        val target = contentEditState.target ?: return
        lifecycleScope.launch {
            when (val result = contentEditStore.load(target)) {
                is EditLoadResult.Ok -> contentEditState.applyLoaded(result.content, result.title)
                EditLoadResult.BookMissing ->
                    contentEditState.applyLoadError(getString(R.string.book_not_exist))
                EditLoadResult.ChapterMissing ->
                    contentEditState.applyLoadError(getString(R.string.content_empty))
                EditLoadResult.ContentMissing ->
                    contentEditState.applyLoadError(getString(R.string.content_empty))
                is EditLoadResult.Failed -> contentEditState.applyLoadError(
                    result.message.ifBlank { getString(R.string.unknown_error) },
                )
            }
        }
    }

    /** 保存草稿正文；成功后基线刷新并收口面板，失败留在面板给可读提示。 */
    private fun saveContentEdit() {
        val target = contentEditState.target ?: return
        contentEditState.markSaving(true)
        lifecycleScope.launch {
            when (val result = contentEditStore.save(target, contentEditState.content)) {
                is EditSaveResult.Ok -> {
                    contentEditState.markContentSaved()
                    contentEditState.showMessage(EditMessageKind.SUCCESS, getString(R.string.success))
                    contentEditState.close()
                }
                EditSaveResult.BookMissing ->
                    contentEditState.showMessage(EditMessageKind.FAILURE, getString(R.string.book_not_exist))
                EditSaveResult.ChapterMissing ->
                    contentEditState.showMessage(EditMessageKind.FAILURE, getString(R.string.content_empty))
                EditSaveResult.Conflict ->
                    contentEditState.showMessage(
                        EditMessageKind.FAILURE,
                        getString(R.string.content_edit_conflict),
                    )
                is EditSaveResult.Failed -> contentEditState.showMessage(
                    EditMessageKind.FAILURE,
                    result.message.ifBlank { getString(R.string.unknown_error) },
                )
            }
            contentEditState.markSaving(false)
        }
    }

    /** 标题改名落盘；成功回填展示标题并刷新基线，失败给失败提示。 */
    private fun renameContentEdit(newTitle: String) {
        val target = contentEditState.target ?: return
        lifecycleScope.launch {
            when (val result = contentEditStore.rename(target, newTitle)) {
                is EditSaveResult.Ok -> {
                    contentEditState.markTitleSaved(result.displayTitle ?: newTitle)
                    contentEditState.showMessage(EditMessageKind.SUCCESS, getString(R.string.success))
                }
                else -> contentEditState.showMessage(
                    EditMessageKind.FAILURE,
                    getString(R.string.unknown_error),
                )
            }
        }
    }

    /** 重置当前章：清本地缓存并按书源重新拉取，回读成功后刷新草稿与基线。 */
    private fun resetContentEdit() {
        val target = contentEditState.target ?: return
        lifecycleScope.launch {
            when (val result = contentEditStore.reset(target)) {
                is EditLoadResult.Ok -> contentEditState.applyLoaded(result.content, result.title)
                else -> contentEditState.showMessage(
                    EditMessageKind.FAILURE,
                    getString(R.string.unknown_error),
                )
            }
        }
    }

    /** 复制全部：标题 + 正文进剪贴板，回执轻提示。 */
    private fun copyAllContent() {
        sendToClip("${contentEditState.title}\n${contentEditState.content}")
        contentEditState.showMessage(EditMessageKind.INFO, getString(R.string.copy_complete))
    }

    /** 纯文本模式开关：状态切换 + 偏好持久化，键与旧版一致，老用户设置不丢。 */
    private fun togglePlainText(plain: Boolean) {
        contentEditState.plainText = plain
        putPrefBoolean(PREF_PLAIN_TEXT, plain)
    }

    /** 遮罩 / 取消关闭请求：离场裁决在状态层，脏数据先弹放弃确认。 */
    private fun handleContentEditDismiss() {
        contentEditState.requestDismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        contentEditState.saveToBundle(outState, STATE_EDIT_PANEL)
    }

    /**
     * ReadBook 全局回调刷新：交控制器从 ReadBook 回源当前章 / 页；
     * 控制器尚未初始化（initData 早于控制器实例化）时跳过，等控制器就位后的挂载效应回源。
     */
    private fun refreshFromReadBook() {
        if (!::controller.isInitialized) return
        controller.syncChapterFromReadBook()
    }

    // ── ContentTextView.CallBack（B4-07 文字面宿主桥接） ──────────────────────────
    // 旧壳由 ReadView 承接 pageFactory / pageDelegate；新壳 C 阶段文字面为静态单页上屏，
    // 工厂经 ReadBookTextDataSource 只读映射 ReadBook 唯一状态源。图片长按 / 点评 / 高亮 /
    // 选区 / 详情半屏等壳桩回调归后续剥桩批次（B4-08~B4-10），此处给安全空实现：
    // 不抢手势、不弹旧对话框、不出空气泡。

    /** 旧分页引擎工厂：懒构造，数据源只读映射 ReadBook；不另存章 / 页镜像（R5）。 */
    private val textPageFactory: TextPageFactory by lazy {
        TextPageFactory(ReadBookTextDataSource { refreshFromReadBook() })
    }

    override val headerHeight: Int = 0

    override val imgBgPaddingStart: Int = 0

    override val pageFactory: TextPageFactory get() = textPageFactory

    override val pageDelegate: PageDelegate? = null

    override val isScroll: Boolean = false

    override var isSelectingSearchResult: Boolean = false

    override fun onReadScroll(offset: Int) = Unit

    override fun upSelectedStart(x: Float, y: Float, top: Float) = Unit

    override fun upSelectedEnd(x: Float, y: Float) = Unit

    override fun onImageLongPress(x: Float, y: Float, src: String) = Unit

    override fun onCancelSelect() = Unit

    override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean = false

    override fun oldClickImg(src: String): Boolean = false

    override fun clickImg(click: String, src: String) = Unit

    override fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int) = Unit

    override fun onHighlightClick(highlight: BookHighlight, x: Float, y: Float) = Unit

    override fun onHighlightRuleClick(ruleId: Long, x: Float, y: Float) = Unit

    override fun openReaderSheet(request: ReaderDetailRequest) = Unit

    companion object {
        /** 纯文本模式偏好键，与旧 ContentEditDialog 同键，老用户设置不丢。 */
        private const val PREF_PLAIN_TEXT = "contentEditPlainText"

        /** 编辑面板 savedInstanceState 前缀，隔离宿主其它状态键。 */
        private const val STATE_EDIT_PANEL = "inkContentEdit"
    }
}
