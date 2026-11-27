package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.ContentEditProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ink 内容编辑面板纯逻辑单测（普通 JVM；不触碰 Compose / Android 框架）。
 *
 * 覆盖规则层：正文 / 标题脏数据判定、正文三态校验、标题三态校验、离场裁决、
 * 纯文本模式可见串 → 草稿原文映射。改坏任一判定，对应断言转红。
 */
class InkContentEditRulesTest {

    // ── 脏数据判定 ──────────────────────────────────────────────────────

    @Test
    fun `正文逐字比对 首尾空白改动也算脏`() {
        assertTrue(InkContentEditRules.contentDirty("原文", "原文 "))
        assertFalse(InkContentEditRules.contentDirty("原文", "原文"))
    }

    @Test
    fun `标题 trim 后比对 首尾空白不算改动`() {
        assertTrue(InkContentEditRules.titleDirty("旧标题", "新标题"))
        assertFalse(InkContentEditRules.titleDirty("旧标题", " 旧标题 "))
    }

    @Test
    fun `整体脏数据 正文或标题任一变动即脏`() {
        assertTrue(
            InkContentEditRules.isDirty(
                contentBaseline = "正文", contentCurrent = "正文改",
                titleBaseline = "标题", titleCurrent = "标题",
            ),
        )
        assertTrue(
            InkContentEditRules.isDirty(
                contentBaseline = "正文", contentCurrent = "正文",
                titleBaseline = "标题", titleCurrent = "标题改",
            ),
        )
        assertFalse(
            InkContentEditRules.isDirty(
                contentBaseline = "正文", contentCurrent = "正文",
                titleBaseline = "标题", titleCurrent = "标题",
            ),
        )
    }

    // ── 正文校验 ────────────────────────────────────────────────────────

    @Test
    fun `正文校验 合法 空白 超长三态`() {
        assertTrue(InkContentEditRules.validateContent("一段正文") is ContentValidation.Valid)
        assertTrue(InkContentEditRules.validateContent("   ") is ContentValidation.Empty)
        val tooLong = InkContentEditRules.validateContent(
            "x".repeat(InkContentEditRules.MAX_CONTENT_LENGTH + 1),
        )
        assertTrue(tooLong is ContentValidation.TooLong)
        assertEquals(InkContentEditRules.MAX_CONTENT_LENGTH, (tooLong as ContentValidation.TooLong).max)
    }

    @Test
    fun `正文校验 恰好在上限长度内合法`() {
        val ok = InkContentEditRules.validateContent(
            "x".repeat(InkContentEditRules.MAX_CONTENT_LENGTH),
        )
        assertTrue(ok is ContentValidation.Valid)
    }

    // ── 标题校验 ────────────────────────────────────────────────────────

    @Test
    fun `标题校验 合法 空白 超长三态`() {
        assertTrue(InkContentEditRules.validateTitle("第一章") is TitleValidation.Valid)
        assertTrue(InkContentEditRules.validateTitle("  ") is TitleValidation.Blank)
        val tooLong = InkContentEditRules.validateTitle(
            "y".repeat(InkContentEditRules.MAX_TITLE_LENGTH + 1),
        )
        assertTrue(tooLong is TitleValidation.TooLong)
        assertEquals(InkContentEditRules.MAX_TITLE_LENGTH, (tooLong as TitleValidation.TooLong).max)
    }

    // ── 离场裁决 ────────────────────────────────────────────────────────

    @Test
    fun `离场裁决 保存进行中一律留面板`() {
        assertEquals(EditExitDecision.KEEP_OPEN, InkContentEditRules.exitDecision(dirty = true, saving = true))
        assertEquals(EditExitDecision.KEEP_OPEN, InkContentEditRules.exitDecision(dirty = false, saving = true))
    }

    @Test
    fun `离场裁决 脏数据弹确认 干净直接关闭`() {
        assertEquals(EditExitDecision.CONFIRM_DISCARD, InkContentEditRules.exitDecision(dirty = true, saving = false))
        assertEquals(EditExitDecision.DISMISS, InkContentEditRules.exitDecision(dirty = false, saving = false))
    }

    // ── 选区夹取（删除末段后光标越界兜底） ──────────────────────────────

    @Test
    fun `选区夹取 区间在长度内原样保留`() {
        val range = InkContentEditRules.clampSelection("一段正文", 1, 3)
        assertEquals(1, range.first)
        assertEquals(3, range.last)
    }

    @Test
    fun `选区夹取 end 越过末尾被夹到长度`() {
        val range = InkContentEditRules.clampSelection("短文", 1, 99)
        assertEquals(1, range.first)
        assertEquals(2, range.last)
    }

    @Test
    fun `选区夹取 start 越过末尾回落至 end`() {
        val range = InkContentEditRules.clampSelection("短文", 99, 99)
        assertEquals(2, range.first)
        assertEquals(2, range.last)
    }

    @Test
    fun `选区夹取 start 大于 end 时 start 压到 end`() {
        val range = InkContentEditRules.clampSelection("一段正文", 4, 2)
        assertEquals(2, range.first)
        assertEquals(2, range.last)
    }

    @Test
    fun `选区夹取 空文本回落零区间`() {
        // IntRange 是闭区间：0..0 是折叠到零点的光标，不是 empty 区间（isEmpty 只在 last<first 成立）。
        // 所以这里只能断言首末都收口到 0，不能断言 isEmpty —— 断言 isEmpty 会必红（CI 实测）。
        val range = InkContentEditRules.clampSelection("", 3, 5)
        assertEquals(0, range.first)
        assertEquals(0, range.last)
        assertTrue(range.first == range.last)
    }

    @Test
    fun `选区夹取 负坐标收口到零`() {
        val range = InkContentEditRules.clampSelection("正文", -4, -1)
        assertEquals(0, range.first)
        assertEquals(0, range.last)
    }

    // ── 纯文本模式映射 ──────────────────────────────────────────────────

    @Test
    fun `映射 可见串未变时原样返回草稿`() {
        val raw = "前<img src=\"a.jpg\">后"
        val projection = ContentEditProjection(raw)
        assertEquals(raw, InkContentEditRules.projectEdit(projection, projection.text, projection.text))
    }

    @Test
    fun `映射 中段插入保留隐藏图片标记`() {
        val raw = "前<img src=\"a.jpg\">后"
        val projection = ContentEditProjection(raw)
        // 可见文本「前后」→「前X后」：差异中段回填，隐藏图片标记不得被抹掉。
        val mapped = InkContentEditRules.projectEdit(projection, "前后", "前X后")
        requireNotNull(mapped)
        assertTrue(mapped.contains("<img src=\"a.jpg\">"))
        assertTrue(mapped.contains("X"))
    }

    @Test
    fun `映射 整段替换仍保留隐藏图片标记`() {
        val raw = "前<img src=\"a.jpg\">后"
        val projection = ContentEditProjection(raw)
        // 无公共前后缀的整段替换：replace 路径兜底回写，图片标记随原文区间保留。
        val mapped = InkContentEditRules.projectEdit(projection, "前后", "完全不同的一段")
        requireNotNull(mapped)
        assertTrue(mapped.contains("<img src=\"a.jpg\">"))
        assertTrue(mapped.contains("完全不同的一段"))
    }
}
