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
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.vertical.VerticalLayoutEngine
import io.legado.app.ui.book.read.vertical.VerticalLayoutEngineFactory
import io.legado.app.ui.book.read.vertical.VerticalPage
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import kotlin.math.roundToInt

/**
 * 阅读页三引擎接线宿主（U5-A ↔ V1 竖排 / C2 气泡 / C1 时空锚）。
 *
 * 闭环链路：
 *  1. [bindChapter]：把当前书 / 章 / 正文写入 [ContextAnchor]，先按章标题与正文前段线索
 *     猜候选年代区间；再交竖排引擎分页排版。
 *  2. [probeChar]：触摸点先走 [VerticalLayoutEngine.hitTest]，字命中优先于边缘翻页；
 *     命中后按字位回填气泡锚点矩形。
 *  3. [requestBubble]：把命中段的 (paragraphText, pos) 喂给 [BubbleResolver.resolve]，
 *     锚点传共享总线 [ContextAnchor]；Resolver 内部 Missing 兜底，壳侧不空气泡。
 *  4. [turnPage]：掀角动画期间屏蔽新翻页；引擎 prev / next 翻页，回填进度并关闭气泡。
 *  5. [switchCandidate]：候选循环切换，按候选裸查询词走 [BubbleResolver.queryToken] 重解析回填。
 *
 * 性能（切片-08）：
 *  - 章节正文按前后各 [InkTokens.CHAPTER_CACHE_RADIUS] 章缓存在 [chapterCache]，
 *    翻章命中缓存即重排，不等正文二次加载，章节切换 0 白屏；
 *  - 气泡解析结果按「章 + 页 + 段 + 字位」进 [bubbleCache]，同一页反复点同一字不重走解析链。
 *
 * 边界兜底：空正文 / 空尺寸 / 越界命中 / 字典缺失全部 runCatching 降级，不崩、不空白态。
 */
class InkReadController(
    private val context: Context,
) : ReaderShellHost {

    /** 竖排正文自绘 View，作为壳底层引擎 View 传入 [InkReaderShell]。 */
    val pageView: VerticalPageView = VerticalPageView(context)

    private var engine: VerticalLayoutEngine? = null
    private var chapterId: String = ""
    private var chapterTitle: String? = null
    private var chapterBody: String = ""

    /** 已知总页数（引擎未暴露页数访问器；随翻页回填收敛，jumpTo 内部另有越界夹取兜底）。 */
    private var knownPageCount: Int = 1

    /** 章节正文预热缓存：翻章命中即取，避免章节切换白屏（容量按前后各 3 章收口）。 */
    private val chapterCache = object : LinkedHashMap<String, CachedChapter>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedChapter>?): Boolean =
            size > CHAPTER_CACHE_MAX
    }

    /** 气泡解析结果按页缓存：同一页反复点同一字直接回填，不重走 Provider 链。 */
    private val bubbleCache = object : LinkedHashMap<String, BubbleContent>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BubbleContent>?): Boolean =
            size > BUBBLE_CACHE_MAX
    }

    // ---------- 暴露给 Compose 壳的状态 ----------

    /** 当前气泡内容；null = 无气泡（Resolver 未收录时也由其产出 Missing 兜底）。 */
    var bubbleContent by mutableStateOf<BubbleContent?>(null)
        private set

    /** 阅读进度 0f..1f。 */
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

    /**
     * 预热一章正文（切片-08「预载前后各一章」）。列表 / 相邻章就绪后调用，
     * 仅写入缓存不触发排版；真翻到该章时 [bindChapter] 直接取用。
     */
    fun prefetchChapter(chapterId: String, title: String?, body: String) {
        if (chapterId.isBlank() || body.isBlank()) return
        chapterCache[chapterId] = CachedChapter(title?.takeIf { it.isNotBlank() }, body)
    }

    /**
     * 章切换接线：锚点总线写书 / 章，按标题 + 正文前段线索锚定候选年代区间，
     * 清气泡热缓存后重排竖排引擎。
     *
     * @param bookId   当前书标识
     * @param chapterId 当前章标识
     * @param title    章节标题（可空）
     * @param body     章节正文文本流（段落以换行分隔）
     */
    fun bindChapter(bookId: String, chapterId: String, title: String?, body: String) {
        this.chapterId = chapterId
        chapterTitle = title?.takeIf { it.isNotBlank() }
        chapterBody = body.ifBlank { chapterCache[chapterId]?.body.orEmpty() }
        chapterCache[chapterId] = CachedChapter(chapterTitle, chapterBody)

        runCatching {
            ContextAnchor.setChapter(bookId, chapterTitle)
            ContextAnchor.chapterId = chapterId
            // 已有强锚定（用户点年号确认）不覆盖；无锚点时按本章线索猜候选年代区间。
            if (!ContextAnchor.hasAnchor()) {
                val clue = buildString {
                    append(chapterTitle.orEmpty())
                    if (isNotEmpty()) append('\n')
                    append(chapterBody.take(CHAPTER_CLUE_CHARS))
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

    /** 版面尺寸确定 / 变化后重排；空尺寸兜底直接返回，不崩。 */
    internal fun layoutIfNeeded(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val e = engine ?: VerticalLayoutEngineFactory
            .create(widthPx, heightPx, typeface = InkFonts.viewTypeface)
            .also { engine = it }
        val page = runCatching {
            e.layout(chapterBody, widthPx, heightPx, chapterTitle)
        }.getOrNull()
        publishPage(page)
    }

    private fun publishPage(page: VerticalPage?) {
        pageView.render(page)
        progress = page?.progress ?: 0f
        if (page != null) {
            knownPageCount = knownPageCount.coerceAtLeast(page.pageIndex + 1)
            if (engine?.canNext == false) knownPageCount = page.pageIndex + 1
        }
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
        val page = runCatching {
            when (direction) {
                EdgeDirection.Prev -> e.prev()
                EdgeDirection.Next -> e.next()
            }
        }.getOrNull()
        bubbleContent = null
        pageTick++
        publishPage(page)
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

        val cacheKey = "${chapterId}:${engine?.currentPage?.pageIndex ?: 0}:${hit.paragraphText}:${hit.pos}"
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
        if (context.getPrefBoolean(PREF_DISABLE_RETURN_KEY)) {
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

    /** 底栏进度跳转：按 0f..1f 比例收敛到最近一页；越界由引擎 jumpTo 内部夹取。 */
    fun seekTo(fraction: Float) {
        val e = engine ?: return
        val count = knownPageCount.coerceAtLeast(1)
        val pageIndex = (fraction.coerceIn(0f, 1f) * count)
            .roundToInt()
            .coerceIn(0, count - 1)
        val page = runCatching { e.jumpTo(pageIndex) }.getOrNull() ?: return
        bubbleContent = null
        pageTick++
        publishPage(page)
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
        context.getPrefString(prefKey)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            .orEmpty()

    private companion object {
        /** 章切换时取正文前段多少字参与年代线索猜测。 */
        const val CHAPTER_CLUE_CHARS = 500

        /** 章节正文缓存容量：前后各 [InkTokens.CHAPTER_CACHE_RADIUS] 章 + 当前章。 */
        val CHAPTER_CACHE_MAX = InkTokens.CHAPTER_CACHE_RADIUS * 2 + 1

        /** 气泡解析结果缓存条数（够一章反复点按）。 */
        const val BUBBLE_CACHE_MAX = 32

        /**
         * 「禁用返回键」偏好原始键名，与旧壳 pref_config_read.xml 及 ReadBookActivity:631
         * 保持同一键，不另造 AppConfig 字段（新壳设置页未接，键位口径先对齐旧壳）。
         */
        const val PREF_DISABLE_RETURN_KEY = "disableReturnKey"
    }

    private data class CachedChapter(val title: String?, val body: String)
}

/** 宿主 Composable 侧取控制器：随 Composition 记住单例。 */
@Composable
fun rememberInkReadController(context: Context = LocalContext.current): InkReadController =
    remember { InkReadController(context) }
