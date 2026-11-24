package io.legado.app.ui.main.bookshelf.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-13 书架下拉浮层 · 检索纯函数验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖本单断言：
 *  ① 书名命中；② 作者命中；③ 正文片段命中；
 *  ④ 优先级排序：书名 > 作者 > 正文，同组按书名升序；
 *  ⑤ 空 query 返回空结果不抛异常；⑥ 全空白 query（含全角空格）返回空结果不抛异常；
 *  ⑦ 无命中返回空列表且 hint 非空、非 null；
 *  ⑧ 命中片段起止索引可用 substring 正确切出子串；
 *  ⑨ 大小写不敏感；⑩ 空候选列表安全回落。
 */
class ShelfSearchContractTest {

    private fun seed(title: String, author: String, body: String) =
        ShelfItemLite(title = title, author = author, bodySnippet = body)

    @Test
    fun `title hit produces title segment with matching substring`() {
        val items = listOf(seed("春秋左传", "左丘明", "庄公十年春"))

        val result = searchBooks("左传", items)

        assertEquals("书名命中必须入选", 1, result.hits.size)
        val hit = result.hits.first()
        assertEquals(SearchField.TITLE, hit.rank)
        val seg = hit.segments.first { it.field == SearchField.TITLE }
        assertEquals("书名片段应切出查询词", "左传", hit.item.title.substring(seg.start, seg.end))
    }

    @Test
    fun `author hit produces author segment`() {
        val items = listOf(seed("史记", "司马迁", "通古今之变"))

        val result = searchBooks("司马迁", items)

        assertEquals("作者命中必须入选", 1, result.hits.size)
        val hit = result.hits.first()
        assertEquals(SearchField.AUTHOR, hit.rank)
        val seg = hit.segments.first { it.field == SearchField.AUTHOR }
        assertEquals("作者片段应切出查询词", "司马迁", hit.item.author.substring(seg.start, seg.end))
    }

    @Test
    fun `body snippet hit produces body segment`() {
        val items = listOf(seed("三国志", "陈寿", "赤壁之战，曹军大败"))

        val result = searchBooks("赤壁", items)

        assertEquals("正文片段命中必须入选", 1, result.hits.size)
        val hit = result.hits.first()
        assertEquals(SearchField.BODY, hit.rank)
        val seg = hit.segments.first { it.field == SearchField.BODY }
        assertEquals("正文片段应切出查询词", "赤壁", hit.item.bodySnippet.substring(seg.start, seg.end))
    }

    @Test
    fun `title outranks author outranks body and same group sorts by title ascending`() {
        val bodyOnly = seed("乙篇", "作者乙", "正文关键词片段")
        val authorOnly = seed("丙篇", "关键词作者", "正文无")
        val titleA = seed("关键词甲", "作者甲", "正文无")
        val titleB = seed("关键词乙", "作者甲", "正文无")

        val result = searchBooks("关键词", listOf(bodyOnly, authorOnly, titleB, titleA))

        val titles = result.hits.map { it.item.title }
        assertEquals(
            "书名组在前且同组按书名升序，作者组次之，正文组垫底",
            // 同组按 Unicode 码点升序："乙"(U+4E59) 码点小于 "甲"(U+7532)，故乙在前（K-17，排序定义非 bug）
            listOf("关键词乙", "关键词甲", "丙篇", "乙篇"),
            titles,
        )
        assertEquals(SearchField.TITLE, result.hits[0].rank)
        assertEquals(SearchField.AUTHOR, result.hits[2].rank)
        assertEquals(SearchField.BODY, result.hits[3].rank)
    }

    @Test
    fun `empty query returns empty result without throwing`() {
        val items = listOf(seed("史记", "司马迁", "正文"))

        val result = searchBooks("", items)

        assertTrue("空 query 必须返回空列表", result.hits.isEmpty())
        assertNotNull("hint 不许为 null", result.hint)
        assertEquals("空 query 不给无结果占位文案", "", result.hint)
    }

    @Test
    fun `blank query including full width space returns empty result without throwing`() {
        val items = listOf(seed("史记", "司马迁", "正文"))

        val result = searchBooks("　 　", items)

        assertTrue("全空白 query 必须返回空列表", result.hits.isEmpty())
        assertNotNull("hint 不许为 null", result.hint)
    }

    @Test
    fun `no match returns empty list with non null non empty hint`() {
        val items = listOf(seed("史记", "司马迁", "正文片段"))

        val result = searchBooks("庄子", items)

        assertTrue("无命中必须返回空列表", result.hits.isEmpty())
        assertNotNull("hint 不许为 null", result.hint)
        assertTrue("无命中 hint 必须非空", result.hint.isNotEmpty())
        assertEquals(NO_MATCH_HINT, result.hint)
    }

    @Test
    fun `segment indices cut correct substring via substring`() {
        val body = "原文片段：垓下歌 项羽 力拔山兮"
        val items = listOf(seed("汉书", "班固", body))

        val result = searchBooks("垓下歌", items)

        assertEquals(1, result.hits.size)
        val seg = result.hits.first().segments.first { it.field == SearchField.BODY }
        assertTrue("start 必须非负", seg.start >= 0)
        assertTrue("end 必须大于 start", seg.end > seg.start)
        assertEquals("按起止索引切出的子串必须等于查询词", "垓下歌", body.substring(seg.start, seg.end))
    }

    @Test
    fun `matching is case insensitive for title and author`() {
        val items = listOf(seed("Analects", "Confucius", "body snippet"))

        val lowerTitle = searchBooks("analects", items)
        val upperAuthor = searchBooks("CONFUCIUS", items)

        assertEquals("小写查询应命中英文书名", 1, lowerTitle.hits.size)
        assertEquals("大写查询应命中英文作者", 1, upperAuthor.hits.size)
    }

    @Test
    fun `empty candidate list returns empty result with no match hint`() {
        val result = searchBooks("左传", emptyList())

        assertTrue("空候选列表必须返回空列表", result.hits.isEmpty())
        assertNotNull("hint 不许为 null", result.hint)
        assertEquals(NO_MATCH_HINT, result.hint)
    }
}
