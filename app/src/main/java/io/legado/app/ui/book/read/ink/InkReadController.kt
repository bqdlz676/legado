package io.legado.app.ui.book.read.ink

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleResolver
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.ui.book.read.vertical.VerticalLayoutEngine
import io.legado.app.ui.book.read.vertical.VerticalLayoutEngineFactory
import io.legado.app.ui.book.read.vertical.VerticalPage

/**
 * 阅读页三引擎接线宿主（模块⑦ ↔ 模块②竖排 / 模块⑥气泡 / 时空锚）。
 *
 * 闭环链路：
 *  1. [bindChapter]：把当前书 / 章 / 正文写入 [ContextAnchor]，先按章标题与正文前段线索
 *     猜候选年代区间；再交竖排引擎分页排版。
 *  2. [probeChar]：触摸点先走 [VerticalLayoutEngine.hitTest]，字命中优先于边缘翻页；
 *     命中后按字位回填气泡锚点矩形。
 *  3. [requestBubble]：把命中段的 (paragraphText, pos) 喂给 [BubbleResolver.resolve]，
 *     锚点传共享总线 [ContextAnchor]；Resolver 内部 Missing 兜底，壳侧不空气泡。
 *  4. [turnPage]：引擎 prev / next 翻页，回填进度并关闭气泡。
 *
 * 边界兜底：空正文 / 空尺寸 / 越界命中 / 字典缺失全部 runCatching 降级，不崩、不空白态。
 */
class InkReadController(
    private val context: Context,
) : ReaderShellHost {

    /** 竖排正文自绘 View，作为壳底层引擎 View 传入 [InkReaderShell]。 */
    val pageView: VerticalPageView = VerticalPageView(context)

    private var engine: VerticalLayoutEngine? = null
    private var chapterTitle: String? = null
    private var chapterBody: String = ""

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

    init {
        pageView.onSizeChanged = { w, h -> layoutIfNeeded(w, h) }
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
        chapterTitle = title?.takeIf { it.isNotBlank() }
        chapterBody = body

        runCatching {
            ContextAnchor.setChapter(bookId, chapterTitle)
            ContextAnchor.chapterId = chapterId
            // 已有强锚定（用户点年号确认）不覆盖；无锚点时按本章线索猜候选年代区间。
            if (!ContextAnchor.hasAnchor()) {
                val clue = buildString {
                    append(chapterTitle.orEmpty())
                    if (isNotEmpty()) append('\n')
                    append(body.take(CHAPTER_CLUE_CHARS))
                }
                ContextAnchor.guessFromText(clue)
            }
        }

        runCatching { BubbleResolver.clearCache() }
        bubbleContent = null
        engine?.invalidateCache()
        layoutIfNeeded(pageView.width, pageView.height)
    }

    /** 版面尺寸确定 / 变化后重排；空尺寸兜底直接返回，不崩。 */
    internal fun layoutIfNeeded(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val e = engine ?: VerticalLayoutEngineFactory
            .create(widthPx, heightPx)
            .also { engine = it }
        val page = runCatching {
            e.layout(chapterBody, widthPx, heightPx, chapterTitle)
        }.getOrNull()
        publishPage(page)
    }

    private fun publishPage(page: VerticalPage?) {
        pageView.render(page)
        progress = page?.progress ?: 0f
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
        val page = runCatching {
            when (direction) {
                EdgeDirection.Prev -> e.prev()
                EdgeDirection.Next -> e.next()
            }
        }.getOrNull() ?: return
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
        val content = runCatching {
            BubbleResolver.resolve(hit.paragraphText, hit.pos, ContextAnchor)
        }.getOrNull()
        // Resolver 内部由 Missing 兜底、正常不返回 null；极端兜底为空则关闭气泡，不出空气泡。
        bubbleContent = content
    }

    override fun dismissBubble() {
        bubbleContent = null
    }

    override fun isTextSelected(): Boolean = false

    override fun isPageAnimating(): Boolean = false

    private companion object {
        /** 章切换时取正文前段多少字参与年代线索猜测。 */
        const val CHAPTER_CLUE_CHARS = 500
    }
}

/** 宿主 Composable 侧取控制器：随 Composition 记住单例。 */
@Composable
fun rememberInkReadController(context: Context = LocalContext.current): InkReadController =
    remember { InkReadController(context) }
