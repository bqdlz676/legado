package io.legado.app.help.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RssSortUrlsTest {

    private val sourceUrl = "https://example.com/feed"

    // 超时护栏：runBlocking 包裹脚本求值，求值回调若挂起会拖住测试线程；
    // 5s 上限在 runner 层限时，超时即判失败，不再拖到分片 45 分钟兜底。
    @Test(timeout = 5_000)
    fun `normal multi category values are parsed`() {
        assertEquals(
            listOf(
                "News" to "https://example.com/news",
                "Books" to "https://example.com/books",
                "Audio" to "https://example.com/audio",
            ),
            parseRssSortUrls(
                "News::https://example.com/news&&" +
                    "Books::https://example.com/books\r\n" +
                    "Audio::https://example.com/audio",
                sourceUrl,
            ),
        )
    }

    @Test(timeout = 5_000)
    fun `valid wrapped script result is parsed`() = runBlocking {
        var evaluatedScript: String? = null

        val result = resolveRssSortUrls(
            configuredSortUrl = "<js>return categories</js>",
            sourceUrl = sourceUrl,
        ) { script ->
            evaluatedScript = script
            "One::https://example.com/one&&Two::https://example.com/two"
        }

        assertEquals("return categories", evaluatedScript)
        assertEquals(2, result.size)
    }

    @Test(timeout = 5_000)
    fun `script exception falls back to source url`() = runBlocking {
        val result = resolveRssSortUrls(
            configuredSortUrl = "@js:throw new Error()",
            sourceUrl = sourceUrl,
        ) {
            throw IllegalStateException("script failed")
        }

        assertEquals(listOf("" to sourceUrl), result)
    }

    @Test(timeout = 5_000)
    fun `at js prefix is removed before evaluation`() = runBlocking {
        var evaluatedScript: String? = null

        resolveRssSortUrls(
            configuredSortUrl = "@js:return categories",
            sourceUrl = sourceUrl,
        ) { script ->
            evaluatedScript = script
            "One::https://example.com/one"
        }

        assertEquals("return categories", evaluatedScript)
    }

    @Test(timeout = 5_000)
    fun `script cancellation is propagated`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                resolveRssSortUrls(
                    configuredSortUrl = "@js:return categories",
                    sourceUrl = sourceUrl,
                ) {
                    throw CancellationException("cancelled")
                }
            }
        }
    }

    @Test(timeout = 5_000)
    fun `missing closing script tag falls back without evaluation`() = runBlocking {
        var evaluated = false

        val result = resolveRssSortUrls(
            configuredSortUrl = "<js>return categories",
            sourceUrl = sourceUrl,
        ) {
            evaluated = true
            "ignored::https://example.com/ignored"
        }

        assertFalse(evaluated)
        assertEquals(listOf("" to sourceUrl), result)
    }

    @Test(timeout = 5_000)
    fun `empty and invalid results fall back to source url`() = runBlocking {
        assertEquals(listOf("" to sourceUrl), parseRssSortUrls("", sourceUrl))
        assertEquals(listOf("" to sourceUrl), parseRssSortUrls("missing separator", sourceUrl))
        assertEquals(
            listOf("" to sourceUrl),
            resolveRssSortUrls("@js:return ''", sourceUrl) { "  \n  " },
        )
    }

    @Test(timeout = 5_000)
    fun `cached script result avoids evaluation`() = runBlocking {
        var evaluated = false

        val result = resolveRssSortUrls(
            configuredSortUrl = "@js:return categories",
            sourceUrl = sourceUrl,
            cachedScriptResult = "Cached::https://example.com/cached",
        ) {
            evaluated = true
            null
        }

        assertFalse(evaluated)
        assertEquals(listOf("Cached" to "https://example.com/cached"), result)
        assertTrue(result.isNotEmpty())
    }
}
