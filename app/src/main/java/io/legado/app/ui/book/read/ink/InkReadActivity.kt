package io.legado.app.ui.book.read.ink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.help.bubble.BubbleResolver
import io.legado.app.help.bubble.NotebookStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.utils.eventObservable
import io.legado.app.utils.toastOnUi
import io.legado.app.R

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
 * 性能（切片-08）：当前章就绪后，把前后相邻章正文预热进控制器缓存，翻章 0 白屏。
 *
 * 兜底：章节尚未加载完成时正文为空串，壳与引擎按空页兜底，不出空白态、不崩；
 * 正文加载完成后回调刷新，状态自动回填。
 */
class InkReadActivity : ComponentActivity() {

    private val viewModel: ReadBookViewModel by viewModels()

    /**
     * 阅读控制器提升为 Activity 字段：硬件键 / 媒体键翻页通道在 Composable 之外也要能拿到，
     * 故不再由 remember 在组合内创建，改在 onCreate 实例化后传入 [InkReaderBook]。
     */
    private lateinit var controller: InkReadController

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

    // ── 搬运到 Compose 壳的书 / 章状态 ──────────────────────────────
    private var bookId by mutableStateOf("")
    private var chapterId by mutableStateOf("")
    private var chapterTitle by mutableStateOf<String?>(null)
    private var chapterBody by mutableStateOf("")

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
            // 相邻章正文预热：切片-08「预载前后各一章」，翻章命中缓存即重排，不等二次加载。
            LaunchedEffect(chapterId, chapterBody) {
                ReadBook.prevTextChapter?.let { prev ->
                    controller.prefetchChapter(
                        chapterId = prev.chapter.url,
                        title = prev.title,
                        body = chapterBodyText(prev),
                    )
                }
                ReadBook.nextTextChapter?.let { next ->
                    controller.prefetchChapter(
                        chapterId = next.chapter.url,
                        title = next.title,
                        body = chapterBodyText(next),
                    )
                }
            }

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
                            chapterTitle = chapterTitle.orEmpty(),
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
                bookId = bookId,
                chapterId = chapterId,
                title = chapterTitle,
                body = chapterBody,
                onCatalog = { tocLauncher.launch(bookId) },
                onTts = { runCatching { ReadAloud.play(this@InkReadActivity) } },
                // 排版 / 字体 / 更多菜单由 U1 组件库后续接入；回调位保留，当前不做空跳转。
                onMenu = { Unit },
                onSeek = { fraction -> controller.seekTo(fraction) },
                controller = controller,
                onFallback = fallbackHandler,
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
        super.onDestroy()
    }

    /** 从 [ReadBook] 全局单例回填当前书 / 章 / 正文到 Compose 状态。 */
    private fun refreshFromReadBook() {
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter
        bookId = book?.bookUrl ?: intent.getStringExtra("bookUrl").orEmpty()
        chapterId = chapter?.chapter?.url.orEmpty()
        chapterTitle = chapter?.title
        chapterBody = chapter?.let(::chapterBodyText).orEmpty()
    }

    /**
     * 章节正文抽取：优先段落项（段落边界完整）；段落项未就绪时退回各页文本拼接。
     * 图片 / 空白段落不造字，空文本交壳层兜底。
     */
    private fun chapterBodyText(chapter: TextChapter): String {
        val items = chapter.paragraphItems
        if (items.isNotEmpty()) {
            return items.joinToString("\n") { it.text.toString().trim() }
                .lines()
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }
        return chapter.pages.joinToString("\n") { it.text }
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}
