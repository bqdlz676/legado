package io.legado.app.ui.book.read.ink

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import io.legado.app.constant.PreferKey
import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleResolver
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.vertical.VerticalLayoutEngine
import io.legado.app.ui.book.read.vertical.VerticalLayoutEngineFactory
import io.legado.app.ui.book.read.vertical.VerticalPage
import io.legado.app.ui.book.read.vertical.verticalBodyText
import io.legado.app.ui.book.read.vertical.verticalTitle
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import kotlin.math.roundToInt

/**
 * 阅读页三引擎接线宿主（U5-A ↔ V1 竖排 / C2 气泡 / C1 时空锚）。
 *
 * 状态源（R5，B4-06）：章 / 页 / 进度 / 样式真值唯一在 [ReadBook]——
 * 书 [ReadBook.book]、当前章 [ReadBook.curTextChapter]、章序号 [ReadBook.durChapterIndex]、
 * 章内位置 [ReadBook.durChapterPos]、页序号 [ReadBook.durPageIndex] 一律从 [ReadBook] 读；
 * 进章 / 翻页 / 进度跳转的变更一律写回 [ReadBook]（[openChapter] / moveToNextPage /
 * moveToPrevPage / moveToNextChapter / moveToPrevChapter / setPageIndex）。本类不自存第二份
 * 章文本 / 章标题 / 章标识 / 进度真值；两级缓存只作提速，渲染侧页序计数随引擎派生。
 *
 * 闭环链路：
 *  1. [syncChapterFromReadBook]：从 [ReadBook] 取当前书 / 章锚定时空总线，清气泡热缓存后重排。
 *  2. [probeChar]：触摸点先走 [VerticalLayoutEngine.hitTest]，字命中优先于边缘翻页；
 *     命中后按字位回填气泡锚点矩形。
 *  3. [requestBubble]：把命中段的 (paragraphText, pos) 喂给 [BubbleResolver.resolve]，
 *     锚点传共享总线 [ContextAnchor]；Resolver 内部 Missing 兜底，壳侧不空气泡。
 *  4. [turnPage]：掀角动画期间屏蔽新翻页；页内翻页走 [ReadBook.moveToNextPage] /
 *     [ReadBook.moveToPrevPage]，章边界续接 [ReadBook.moveToNextChapter] /
 *     [ReadBook.moveToPrevChapter]，状态由 [ReadBook] 回调回灌后再本地幂等同步。
 *  5. [switchCandidate]：候选循环切换，按候选裸查询词走 [BubbleResolver.queryToken] 重解析回填。
 *
 * 不变式（归位口径，B4-05 / B4-06）：
 *  - 三引擎编排唯一收口在本类：分页引擎·竖排模式 / 气泡 Resolver / 时空锚的接线顺序与翻页闸串联
 *    只此一份，壳层与手势层不经第二控制器重复编排；
 *  - 缓存口径：[bubbleCache] 按「章 + 页 + 段 + 字位」缓存解析结果；章正文 / 进度 / 样式
 *    真值唯一在 [ReadBook]（R5），本类不自存章正文 / 标题 / 标识 / 进度镜像；前后相邻章预热
 *    由 [ReadBook] 内部邻章加载承担，控制器不另存正文副本；
 *  - Context 注入槽：构造期传入一次；偏好读取走 applicationContext，本类不把 Activity
 *    写进任何 object / companion / 全局总线；View 构造所需 themed Context 仅存活于本实例，
 *    随宿主销毁由 [release] 摘挂；
 *  - 重复 attach 幂等：View 监听只在 init 挂一次，[syncChapterFromReadBook] 重复调用只刷新章 /
 *    缓存 / 排版，不重复注册监听、不叠第二份回调。
 *
 * 性能（切片-08）：
 *  - 前后相邻章由 [ReadBook] 预载，翻章即取已就绪邻章，不等正文二次加载，章节切换 0 白屏；
 *  - 气泡解析结果按「章 + 页 + 段 + 字位」进 [bubbleCache]，同一页反复点同一字不重走解析链。
 *
 * 边界兜底：空正文 / 空尺寸 / 越界命中 / 字典缺失全部 runCatching 降级，不崩、不空白态。
 */
class InkReadController(
    private val context: Context,
) : ReaderShellHost {

    /**
     * 偏好读取专用上下文：取 applicationContext，避免偏好链路持有 Activity；
     * 应用上下文缺失的极端封装下回退构造入参，偏好读取仍有兜底。
     */
    private val prefsContext: Context = context.applicationContext ?: context

    /** 竖排正文自绘 View，作为壳底层引擎 View 传入 [InkReaderShell]。 */
    val pageView: VerticalPageView = VerticalPageView(context)

    private var engine: VerticalLayoutEngine? = null

    /** 气泡解析结果按页缓存：同一页反复点同一字直接回填，不重走 Provider 链。 */
    private val bubbleCache = object : LinkedHashMap<String, BubbleContent>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BubbleContent>?): Boolean =
            size > BUBBLE_CACHE_MAX
    }

    // ---------- 暴露给 Compose 壳的视图状态（派生自 ReadBook，非第二份真值） ----------

    /** 当前气泡内容；null = 无气泡（Resolver 未收录时也由其产出 Missing 兜底）。 */
    var bubbleContent by mutableStateOf<BubbleContent?>(null)
        private set

    /** 阅读进度 0f..1f；由 ReadBook 当前章 / 页派生，不在壳内另存进度真值。 */
    var progress by mutableStateOf(0f)
        private set

    /** 翻页计数：变化即关气泡。 */
    var pageTick by mutableStateOf(0)
        private set

    /** 滚动计数：变化即关气泡（竖排翻页通道暂并入 pageTick，预留滚动扩展位）。 */
    var scrollTick by mutableStateOf(0)
        private set

    /** 朗读切换回调：由宿主 Activity 灌入（起 / 停朗读服务），控制器不直接碰 Service 生命周期。 */
    var onReadAloudToggle: (() -> Unit)? = null

    init {
        pageView.onSizeChanged = { w, h -> layoutIfNeeded(w, h) }
        // 掀角动画结束后由 View 复位动画中屏蔽位；此处预留对外回调扩展位。
        pageView.onTurnAnimEnd = { /* 状态已在 View 内部复位，壳侧无需额外动作 */ }
        // 宋体注入点：ensureSerifLoaded 已在宿主 onCreate 完成；View 与引擎测量共用同一字体，
        // 未注入内置子集时 InkFonts 回退系统 serif。
        pageView.typeface = InkFonts.viewTypeface
    }

    // ---------- 三路状态源接线（R5：读写均收敛到 ReadBook） ----------

    /**
     * 进章通道：委托 [ReadBook.openChapter] 打开指定章 / 位置；正文就绪由 [ReadBook] 回调
     * （`CallBack.upContent` / `pageChanged` / `contentLoadFinish`）回灌，宿主再调
     * [syncChapterFromReadBook] 同步壳视图。本方法不在壳内另存章标识 / 标题 / 正文镜像。
     *
     * @param index 章序号；@param durChapterPos 章内位置；@param success 加载完成回调。
     */
    fun openChapter(index: Int, durChapterPos: Int = 0, success: (() -> Unit)? = null) {
        ReadBook.openChapter(
            index = index,
            durChapterPos = durChapterPos,
            success = success,
        )
    }

    /**
     * 出页通道：取 [ReadBook] 当前页 [TextPage]，交 B4-07 文字面 `ContentTextView.setContent`
     * 上屏；章未加载 / 页序号越界时返回 null，壳侧空页兜底，不出空白态。本方法只做回源读取，
     * 不在控制器内另存第二份页文本。
     */
    fun currentTextPage(): TextPage? =
        ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)

    /**
     * 状态回源：从 [ReadBook] 取当前书 / 章，锚定时空总线、清气泡热缓存后重排分页引擎·竖排模式。
     * 章标识 / 标题 / 正文 / 进度全部现取现用，不落本类字段；重复调用幂等。
     */
    fun syncChapterFromReadBook() {
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter

        runCatching {
            ContextAnchor.setChapter(book?.bookUrl, chapter?.verticalTitle())
            ContextAnchor.chapterId = chapter?.chapter?.url.orEmpty()
            // 已有强锚定（用户点年号确认）不覆盖；无锚点时按本章线索猜候选年代区间。
            if (!ContextAnchor.hasAnchor()) {
                val clue = buildString {
                    append(chapter?.verticalTitle().orEmpty())
                    if (isNotEmpty()) append('\n')
                    append(chapter?.verticalBodyText().orEmpty().take(CHAPTER_CLUE_CHARS))
                }
                ContextAnchor.guessFromText(clue)
            }
        }

        runCatching { BubbleResolver.clearCache() }
        bubbleCache.clear()
        bubbleContent = null
        engine?.invalidateCache()
        layoutIfNeeded(pageView.width, pageView.height)
    }

    /** 版面尺寸确定 / 变化后重排；空尺寸兜底直接返回，不崩。正文从 [ReadBook] 当前章现取。 */
    internal fun layoutIfNeeded(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val e = engine ?: VerticalLayoutEngineFactory
            .create(widthPx, heightPx, typeface = InkFonts.viewTypeface)
            .also { engine = it }
        val chapter = ReadBook.curTextChapter
        val body = chapter?.verticalBodyText().orEmpty()
        val title = chapter?.verticalTitle()
        val page = runCatching {
            e.layout(body, widthPx, heightPx, title)
        }.getOrNull()
        publishPage(page)
    }

    private fun publishPage(page: VerticalPage?) {
        pageView.render(page)
        progress = page?.progress ?: readBookProgress()
    }

    /** 进度由 ReadBook 当前章 / 页派生：无章归零，单页归一，多页按页序号夹取 0f..1f。 */
    private fun readBookProgress(): Float {
        val chapter = ReadBook.curTextChapter ?: return 0f
        val size = chapter.pageSize
        if (size <= 1) return if (chapter.pages.isEmpty()) 0f else 1f
        return (ReadBook.durPageIndex.toFloat() / (size - 1)).coerceIn(0f, 1f)
    }

    // ---------- ReaderShellHost ----------

    override fun probeChar(x: Float, y: Float): CharHit? {
        val e = engine ?: return null
        val hit = runCatching { e.hitTest(x, y) }.getOrNull() ?: return null
        // 引擎命中只给流内偏移；气泡锚点矩形从当前页字位表按偏移找回。
        val cell = e.currentPage?.columns
            ?.flatMap { it.cells }
            ?.firstOrNull { it.offset == hit.offset }
            ?: return null
        val rect = Rect(
            left = cell.x,
            top = cell.y,
            right = cell.x + cell.width,
            bottom = cell.y + cell.height,
        )
        return CharHit(anchor = rect, engineHit = hit)
    }

    override fun turnPage(direction: EdgeDirection) {
        val e = engine ?: return
        if (pageView.isTurnAnimating()) return
        // 掀角动画自右下掀起；动画期间由 isPageAnimating 屏蔽新的翻页与气泡。
        pageView.startPageTurn(direction)
        // 页内翻页先走 ReadBook 页通道；章边界页通道落空后续接翻章通道。翻页状态真值在 ReadBook。
        when (direction) {
            EdgeDirection.Next -> {
                ReadBook.moveToNextPage() || ReadBook.moveToNextChapter(upContent = true)
            }
            EdgeDirection.Prev -> {
                ReadBook.moveToPrevPage() || ReadBook.moveToPrevChapter(upContent = true, toLast = false)
            }
        }
        bubbleContent = null
        pageTick++
        // ReadBook 回调已回灌状态；本地再按 ReadBook 现状幂等同步一次 interim 视图。
        syncChapterFromReadBook()
    }

    override fun onCenterTap(x: Float, y: Float, hit: CharHit?) {
        // 中间区单击：点中字出气泡，点空白关气泡。
        if (hit != null) requestBubble(hit) else dismissBubble()
    }

    override fun onCenterLongPress(x: Float, y: Float, hit: CharHit?) {
        // 契约：短按出气泡、长按不出；文字选择通道后续接入，此处不抢手势、不出气泡。
    }

    override fun requestBubble(hit: CharHit) {
        // 点字墨爆：独立 100ms 字面冲击，挂在字心；与气泡 250ms 入场动画互不挂接。
        val anchor = hit.anchor
        pageView.burstAt((anchor.left + anchor.right) / 2f, (anchor.top + anchor.bottom) / 2f)

        val chapterUrl = ReadBook.curTextChapter?.chapter?.url.orEmpty()
        val cacheKey = "$chapterUrl:${engine?.currentPage?.pageIndex ?: 0}:${hit.paragraphText}:${hit.pos}"
        val cached = bubbleCache[cacheKey]
        if (cached != null) {
            bubbleContent = cached
            return
        }

        val content = runCatching {
            BubbleResolver.resolve(hit.paragraphText, hit.pos, ContextAnchor)
        }.getOrNull()
        // Resolver 内部由 Missing 兜底、正常不返回 null；极端兜底为空则关闭气泡，不出空气泡。
        if (content != null) bubbleCache[cacheKey] = content
        bubbleContent = content
    }

    override fun dismissBubble() {
        bubbleContent = null
    }

    override fun isTextSelected(): Boolean = false

    override fun isPageAnimating(): Boolean = pageView.isTurnAnimating()

    // ---------- 返回统一出口（硬件键 / 手势返回 / Activity 分发器共用，禁止另写一套） ----------

    /**
     * 返回裁决单一事实源（见 [ReaderShellHost.handleBack] 与 [InkBackResult] 层级说明）。
     *
     * 边界兜底：引擎未排版 / 章节加载中 / 书未打开时 [bubbleContent] 必为 null，
     * 本方法不触碰引擎与正文，直接落到朗读 / 偏好 / 退出分支，不出空白态、不崩。
     */
    override fun handleBack(): InkBackResult {
        // 层级 1：气泡开着 → 先关气泡。半屏详情 Dialog 由其窗口自身消费返回，
        // 运行时到不了本分支；Dialog 关闭后气泡仍在时，再按本层关气泡。
        if (bubbleContent != null) {
            dismissBubble()
            return InkBackResult.BubbleDismissed
        }
        // 层级 2：朗读播放中 → 暂停（旧壳 622–626 同分支）。toggle 回调在播放态即暂停。
        if (BaseReadAloudService.isPlay()) {
            onReadAloudToggle?.invoke()
            return InkBackResult.ReadAloudPaused
        }
        // 层级 3：「禁用返回键」偏好开启 → 消费返回不退出（旧壳 631–633 同口径；
        // 旧分支附带的「菜单不可见」条件在新壳无动作菜单对应物，不移植）。
        if (prefsContext.getPrefBoolean(PREF_DISABLE_RETURN_KEY)) {
            return InkBackResult.ReturnKeyLocked
        }
        // 层级 4：无可消化覆盖层 → 交 Activity finish() 回书架。
        return InkBackResult.ExitToShelf
    }

    // ---------- 候选切换 / 进度跳转 ----------

    /**
     * 候选循环切换：按候选裸查询词走整词解析链重查，回填新气泡内容；
     * 查不到时保留当前气泡，不掉成空气泡。
     */
    fun switchCandidate(candidate: BubbleCandidate) {
        val token = candidate.query.trim()
        if (token.isEmpty()) return
        val content = runCatching {
            BubbleResolver.queryToken(token, ContextAnchor)
        }.getOrNull() ?: return
        bubbleContent = content
    }

    /**
     * 底栏进度跳转：按 0f..1f 比例收敛到当前章最近一页，页码经 [ReadBook.setPageIndex]
     * 写回状态源；越界由 ReadBook / 章节夹取兜底。章边界之外的整章跳转归目录 / 翻章通道。
     */
    fun seekTo(fraction: Float) {
        val chapter = ReadBook.curTextChapter ?: return
        val count = chapter.pageSize.coerceAtLeast(1)
        val pageIndex = (fraction.coerceIn(0f, 1f) * (count - 1))
            .roundToInt()
            .coerceIn(0, count - 1)
        ReadBook.setPageIndex(pageIndex)
        bubbleContent = null
        pageTick++
        syncChapterFromReadBook()
    }

    // ---------- 外部翻页通道：硬件键 / 媒体键（交接清单 #11 遗留） ----------

    /** 引擎是否可上翻（首页为 false）；引擎尚未排版时不可翻，交空页兜底。 */
    fun canPrev(): Boolean = engine?.canPrev == true

    /** 引擎是否可下翻（末页为 false）；引擎尚未排版时不可翻，交空页兜底。 */
    fun canNext(): Boolean = engine?.canNext == true

    /**
     * 硬件键翻页通道（宿主 Activity.onKeyDown 接入）。
     *
     * 键码 → 命令的解析规则在纯 JVM [InkPageKeys]；门禁裁决在 [PageTurnGate]，
     * 与边缘点按翻页共用同一 [turnPage] 通道，动画屏蔽 / 边界夹取口径一致。
     *
     * @return true = 按键已被阅读页消费；false = 交系统默认处理。
     */
    fun handleHardwareKey(keyCode: Int, repeatCount: Int): Boolean {
        val command = InkPageKeys.resolveHardwareKey(keyCode, repeatCount, pageKeyConfig())
            ?: return false
        return dispatchCommand(command)
    }

    /**
     * 媒体键翻页通道（宿主 Activity 内 MEDIA_BUTTON 广播接收器接入）。
     * @return true = 媒体键已被阅读页消费。
     */
    fun handleMediaKey(keyCode: Int): Boolean {
        val command = InkPageKeys.resolveMediaKey(keyCode) ?: return false
        return dispatchCommand(command)
    }

    /** 命令分发：翻页先过门禁，朗读切换交宿主回调；命令已识别即视为消费。 */
    private fun dispatchCommand(command: PageCommand): Boolean = when (command) {
        is PageCommand.Turn -> {
            val gate = PageTurnGate(
                animating = pageView.isTurnAnimating(),
                canPrev = canPrev(),
                canNext = canNext(),
            )
            if (gate.evaluate(command.direction).allowed) {
                turnPage(command.direction)
            }
            true
        }

        PageCommand.ToggleReadAloud -> {
            onReadAloudToggle?.invoke()
            true
        }
    }

    /** 从 AppConfig / 朗读服务状态拼装按键解析配置；自定义键码从偏好读取，非法值忽略。 */
    private fun pageKeyConfig(): PageKeyConfig = PageKeyConfig(
        volumeKeyEnabled = AppConfig.volumeKeyPage,
        volumeKeyPageOnPlay = AppConfig.volumeKeyPageOnPlay,
        readAloudPlaying = BaseReadAloudService.isPlay(),
        keyPageOnLongPress = AppConfig.keyPageOnLongPress,
        prevCustomKeys = customKeyCodes(PreferKey.prevKeys),
        nextCustomKeys = customKeyCodes(PreferKey.nextKeys),
    )

    private fun customKeyCodes(prefKey: String): Set<Int> =
        prefsContext.getPrefString(prefKey)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            .orEmpty()

    /**
     * 宿主销毁 / 摘挂接缝：清两级缓存与引擎排版缓存、复位气泡态，断开 View 监听闭包，
     * 避免控制器闭包持有已销毁宿主。重复 attach 前由宿主调用；未调用时实例随宿主回收，
     * 本类不进任何全局单例。release 后若再 [syncChapterFromReadBook]，引擎与缓存按新会话重建。
     * ReadBook 全局单例的注册 / 反注册由宿主 Activity 生命周期管理，不在本摘挂范围内。
     */
    fun release() {
        bubbleCache.clear()
        bubbleContent = null
        onReadAloudToggle = null
        engine?.invalidateCache()
        engine = null
        pageView.onSizeChanged = null
        pageView.onTurnAnimEnd = null
    }

    private companion object {
        /** 章切换时取正文前段多少字参与年代线索猜测。 */
        const val CHAPTER_CLUE_CHARS = 500

        /** 气泡解析结果缓存条数（够一章反复点按）。 */
        const val BUBBLE_CACHE_MAX = 32

        /**
         * 「禁用返回键」偏好原始键名，与旧壳 pref_config_read.xml 及 ReadBookActivity:631
         * 保持同一键，不另造 AppConfig 字段（新壳设置页未接，键位口径先对齐旧壳）。
         */
        const val PREF_DISABLE_RETURN_KEY = "disableReturnKey"
    }
}

/** 宿主 Composable 侧取控制器：随 Composition 记住单例。 */
@Composable
fun rememberInkReadController(context: Context = LocalContext.current): InkReadController =
    remember { InkReadController(context) }
