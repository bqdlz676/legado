package io.legado.app.ui.main.bookshelf

import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.savePreservingCustomCoverUrl
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** The file picker and system sharing use the same enabled-source matching. */
internal suspend fun importBookshelfJson(json: String, groupId: Long) = coroutineScope {
    val books = GSON.fromJsonArray<JsonObject>(json).getOrThrow().map { book ->
        val name = book.get("name")
        val author = book.get("author")
        require(name != null && name.isJsonPrimitive && name.asJsonPrimitive.isString)
        require(author == null || author.isJsonNull ||
            author.isJsonPrimitive && author.asJsonPrimitive.isString)
        name.asString.also { require(it.isNotBlank()) } to
            author?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    }.distinct()
    val sources = appDb.bookSourceDao.allEnabledPart
    val semaphore = Semaphore(AppConfig.threadCount)
    val failures = books.map { (name, author) ->
        async {
            runCatching {
                semaphore.withPermit {
                    if (appDb.bookDao.has(name, author)) return@withPermit
                    val book = sources.firstNotNullOfOrNull { part ->
                        part.getBookSource()?.let { WebBook.preciseSearchAwait(it, name, author).getOrNull() }
                    } ?: throw NoStackTraceException("没有搜索到<$name>$author")
                    if (groupId > 0) book.group = groupId
                    book.savePreservingCustomCoverUrl()
                }
            }.onFailure { currentCoroutineContext().ensureActive() }.exceptionOrNull()
        }
    }.awaitAll().filterNotNull()
    if (failures.isNotEmpty()) {
        throw NoStackTraceException(failures.joinToString("\n") { it.localizedMessage.orEmpty() })
    }
    Unit
}
