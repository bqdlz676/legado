package io.legado.app.ui.book.read.ink

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.ContentSaveToken
import io.legado.app.help.book.isLocal
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内容编辑目标：书 URL + 章序号 + 章内位置。
 *
 * 〔为什么独立定义〕与旧 `ContentEditTarget` 同口径，但本件属于新侧 Ink 承接件，不继承旧页
 * 实现；位置 [chapterPos] 仅用于打开时回滚阅读位置口径，编辑落盘按章粒度。
 */
data class InkEditTarget(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterPos: Int,
) {
    /** 是否命中当前阅读中的书 / 章；命中才回调 [ReadBook.loadContent] 刷新阅读页。 */
    fun matches(bookUrl: String?, chapterIndex: Int): Boolean =
        this.bookUrl == bookUrl && this.chapterIndex == chapterIndex
}

/** 加载结果：四类缺失 / 失败都带可读兜底分支，调用方不许落到空白态。 */
sealed interface EditLoadResult {
    data class Ok(val content: String, val title: String) : EditLoadResult
    data object BookMissing : EditLoadResult
    data object ChapterMissing : EditLoadResult
    data object ContentMissing : EditLoadResult
    data class Failed(val message: String) : EditLoadResult
}

/** 保存 / 改名结果；[Ok.displayTitle] 为改名回写后的展示标题，未改名时为 null。 */
sealed interface EditSaveResult {
    data class Ok(val displayTitle: String? = null) : EditSaveResult
    data object BookMissing : EditSaveResult
    data object ChapterMissing : EditSaveResult
    /**
     * 自上次加载后磁盘缓存已被外部改动（书源重拉、另一路编辑、重置落盘等）；
     * 本路草稿不覆盖磁盘，回读比对后由宿主裁决保留哪一份，不静默冲掉外部改动。
     */
    data object Conflict : EditSaveResult
    data class Failed(val message: String) : EditSaveResult
}

/**
 * 内容编辑数据桥：磁盘 / 数据库 / 书源读写全部收口在本件，面板与状态层只收结果态。
 *
 * 〔口径回源〕加载 / 保存 / 重置 / 改名链路对照旧 `ContentEditDialog.ContentEditViewModel`：
 *  - 加载：`BookHelp.getContent` → `ContentProcessor.getContent(includeTitle = false)`；
 *  - 保存：`BookHelp.saveText` 落盘；
 *  - 重置：`BookHelp.delContent`，非本地书再走 `WebBook.getContentAwait` 重新拉源；
 *  - 改名：`chapter.title` 回写 + `chapter.update()` 入 DAO。
 * 本件只搬运数据，不复制排版 / 解析逻辑；所有 IO 在 [Dispatchers.IO]，异常收敛为结果态不抛穿。
 */
class InkContentEditStore {

    /**
     * 加载时点的落盘版本戳（内存态，不是内容真源）：仅用于保存前比对磁盘缓存是否被
     * 外部改动；正文真源仍在 BookHelp 缓存文件与 DAO，本映射不落盘、不复制章节文本。
     */
    private val loadStamps = HashMap<String, ContentSaveToken>()

    private fun stampKey(target: InkEditTarget): String =
        target.bookUrl + "#" + target.chapterIndex

    /** 加载当前章可编辑正文与展示标题；任一环节缺失返回对应结果态，由面板给可读兜底。 */
    suspend fun load(target: InkEditTarget): EditLoadResult = withContext(Dispatchers.IO) {
        loadStamps.remove(stampKey(target))
        runCatching {
            val book = resolveBook(target) ?: return@withContext EditLoadResult.BookMissing
            val chapter = resolveChapter(target) ?: return@withContext EditLoadResult.ChapterMissing
            loadStamps[stampKey(target)] = BookHelp.contentSaveToken(book, chapter)
            val raw = BookHelp.getContent(book, chapter)
                ?: return@withContext EditLoadResult.ContentMissing
            val processed = ContentProcessor.get(book.name, book.origin)
                .getContent(book, chapter, raw, includeTitle = false)
                .toString()
            EditLoadResult.Ok(processed, chapter.getDisplayTitle())
        }.getOrElse { EditLoadResult.Failed(it.localizedMessage ?: it.javaClass.simpleName) }
    }

    /**
     * 保存草稿正文；落盘成功后若命中当前章，回调阅读页重载。空正文由面板校验层拦截，
     * 不到这里。版本戳已漂移（外部已改磁盘）时返回 [EditSaveResult.Conflict]，不覆盖。
     */
    suspend fun save(target: InkEditTarget, content: String): EditSaveResult = withContext(Dispatchers.IO) {
        runCatching {
            val book = resolveBook(target) ?: return@withContext EditSaveResult.BookMissing
            val chapter = resolveChapter(target) ?: return@withContext EditSaveResult.ChapterMissing
            val stamp = loadStamps[stampKey(target)]
            if (stamp != null && !BookHelp.isContentSaveCurrent(stamp)) {
                return@withContext EditSaveResult.Conflict
            }
            BookHelp.saveText(book, chapter, content)
            loadStamps[stampKey(target)] = BookHelp.contentSaveToken(book, chapter)
            refreshReaderIfCurrent(target)
            EditSaveResult.Ok()
        }.getOrElse { EditSaveResult.Failed(it.localizedMessage ?: it.javaClass.simpleName) }
    }

    /** 回写章节标题入 DAO；返回改名后的展示标题供面板回填。 */
    suspend fun rename(target: InkEditTarget, title: String): EditSaveResult = withContext(Dispatchers.IO) {
        runCatching {
            val book = resolveBook(target) ?: return@withContext EditSaveResult.BookMissing
            val chapter = resolveChapter(target) ?: return@withContext EditSaveResult.ChapterMissing
            chapter.title = title
            chapter.update()
            refreshReaderIfCurrent(target)
            EditSaveResult.Ok(chapter.getDisplayTitle())
        }.getOrElse { EditSaveResult.Failed(it.localizedMessage ?: it.javaClass.simpleName) }
    }

    /**
     * 重置当前章：删本地缓存；非本地书按书源重新拉取；随后回读正文。
     * 拉源失败不抛穿：回读缺失时返回 [EditLoadResult.ContentMissing] / [Failed]，面板兜底。
     */
    suspend fun reset(target: InkEditTarget): EditLoadResult = withContext(Dispatchers.IO) {
        loadStamps.remove(stampKey(target))
        runCatching {
            val book = resolveBook(target) ?: return@withContext EditLoadResult.BookMissing
            val chapter = resolveChapter(target) ?: return@withContext EditLoadResult.ChapterMissing
            BookHelp.delContent(book, chapter)
            if (!book.isLocal) {
                val source = ReadBook.bookSource?.takeIf { it.bookSourceUrl == book.origin }
                    ?: appDb.bookSourceDao.getBookSource(book.origin)
                source?.let { WebBook.getContentAwait(it, book, chapter) }
            }
            refreshReaderIfCurrent(target)
            val raw = BookHelp.getContent(book, chapter)
                ?: return@withContext EditLoadResult.ContentMissing
            val processed = ContentProcessor.get(book.name, book.origin)
                .getContent(book, chapter, raw, includeTitle = false)
                .toString()
            loadStamps[stampKey(target)] = BookHelp.contentSaveToken(book, chapter)
            EditLoadResult.Ok(processed, chapter.getDisplayTitle())
        }.getOrElse { EditLoadResult.Failed(it.localizedMessage ?: it.javaClass.simpleName) }
    }

    private fun resolveBook(target: InkEditTarget): Book? =
        ReadBook.book?.takeIf { it.bookUrl == target.bookUrl }
            ?: appDb.bookDao.getBook(target.bookUrl)

    private fun resolveChapter(target: InkEditTarget): BookChapter? =
        appDb.bookChapterDao.getChapter(target.bookUrl, target.chapterIndex)

    private fun refreshReaderIfCurrent(target: InkEditTarget) {
        if (target.matches(ReadBook.book?.bookUrl, ReadBook.durChapterIndex)) {
            ReadBook.loadContent(target.chapterIndex, resetPageOffset = false)
        }
    }
}
