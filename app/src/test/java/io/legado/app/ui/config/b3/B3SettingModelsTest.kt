package io.legado.app.ui.config.b3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 设置页行模型纯函数单测（纯 JVM，无 Android / Compose 依赖）。
 *
 * 覆盖：空 / 纯空白 query 零过滤、大小写不敏感子串匹配、整组无命中隐藏、
 * 档位钳制、布尔坏值回落；与既有 `PreferenceSearchTest` 同口径，匹配规则只回顶层纯函数。
 */
class B3SettingModelsTest {

    private fun sampleGroups(): List<B3SettingGroup> = listOf(
        B3SettingGroup(
            title = "外观",
            rows = listOf(
                B3ValueRow(
                    title = "主题模式",
                    summary = "亮色主题",
                    keywords = listOf("theme"),
                    onClick = {},
                ),
                B3SwitchRow(key = "fontScale", title = "字体大小", summary = "10"),
            ),
        ),
        B3SettingGroup(
            title = "备份与云同步",
            rows = listOf(
                B3ValueRow(
                    title = "WebDav 服务器地址",
                    summary = null,
                    keywords = listOf("webdav", "url"),
                    onClick = {},
                ),
                B3InfoRow(title = "同步内容", summary = "阅读进度 · 书签高亮"),
            ),
        ),
    )

    @Test
    fun `empty query returns all groups unchanged`() {
        val groups = sampleGroups()
        assertEquals(groups, filterB3Groups(groups, ""))
    }

    @Test
    fun `blank only query returns all groups unchanged`() {
        val groups = sampleGroups()
        assertEquals(groups, filterB3Groups(groups, "   "))
    }

    @Test
    fun `match is case insensitive substring on title`() {
        assertTrue(B3ValueRow("Theme settings", null, onClick = {}).matchesB3Query("THEME"))
        assertTrue(B3ValueRow("Other", "Network behavior", onClick = {}).matchesB3Query("network"))
    }

    @Test
    fun `match hits keyword and skips blank summary`() {
        assertTrue(
            B3ValueRow("Accent", null, keywords = listOf("Night colors"), onClick = {})
                .matchesB3Query("night"),
        )
        assertFalse(
            B3ValueRow("Theme", "Interface colors", onClick = {}).matchesB3Query("cache"),
        )
    }

    @Test
    fun `whole group hidden when no row matches`() {
        // 夹具两组的 title/summary/keywords 都不含「缓存」，故整组隐藏、返回空列表；
        // 断言必须与纯函数语义一致（原断言要求 size=1 会在 CI 单测直接抛断言失败）。
        assertTrue(filterB3Groups(sampleGroups(), "缓存").isEmpty())
    }

    @Test
    fun `whole group kept when one row matches`() {
        val visible = filterB3Groups(sampleGroups(), "webdav")
        assertEquals(1, visible.size)
        assertEquals("备份与云同步", visible.first().title)
    }

    @Test
    fun `groups have match reflects visible rows`() {
        assertTrue(b3GroupsHaveMatch(sampleGroups(), "webdav"))
        assertFalse(b3GroupsHaveMatch(sampleGroups(), "不存在的条目"))
    }

    @Test
    fun `clamp int keeps value inside min max`() {
        assertEquals(8, clampB3Int(3, 8, 16))
        assertEquals(16, clampB3Int(20, 8, 16))
        assertEquals(12, clampB3Int(12, 8, 16))
    }

    @Test
    fun `boolean fallback parses string and recovers default on bad value`() {
        assertTrue(fallbackB3Boolean(true, false))
        assertFalse(fallbackB3Boolean(false, true))
        assertTrue(fallbackB3Boolean("true", false))
        assertFalse(fallbackB3Boolean("false", true))
        assertFalse(fallbackB3Boolean("yes", false))
        assertTrue(fallbackB3Boolean(null, true))
    }
}
