package io.legado.app.ui.config.b3

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.configPreferenceMatches
import io.legado.app.ui.motion.contract.MotionSpec
import io.legado.app.ui.motion.contract.motionDurationMs
import io.legado.app.ui.motion.contract.springSpecFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 设置页行模型纯函数单测（纯 JVM，无 Android / Compose 依赖）。
 *
 * 覆盖：空 / 纯空白 query 零过滤、大小写不敏感子串匹配、整组无命中隐藏、
 * 档位钳制、布尔坏值回落；R2 动效单源回 MotionSpec 真实对象、R6 音效只读镜像行、
 * R9 测试连接文案映射、R11 字体档位回落、R13 备份清单与时间戳冲突口径、
 * R14 墨水屏四项键位口径；匹配规则只回顶层纯函数，断言不实调源码字符串。
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

    @Test
    fun `R2 press feedback delegates to MotionSpec single source`() {
        assertEquals(120, motionDurationMs(B3_PRESS_MOTION, reduceMotion = false))
        assertSame(MotionSpec.PRESS, springSpecFor(B3_PRESS_MOTION, reduceMotion = false))
        assertEquals(0, motionDurationMs(B3_PRESS_MOTION, reduceMotion = true))
    }

    @Test
    fun `R6 sound mirror row is read only and carries search aliases`() {
        assertTrue(b3SoundMirrorRow.readOnly)
        assertTrue(b3SoundMirrorRow.keywords.isNotEmpty())
    }

    @Test
    fun `R9 webdav test summary maps connected failed and untested`() {
        assertEquals("已连接", b3WebDavTestSummary(true))
        assertEquals("无法连接，检查地址和密码", b3WebDavTestSummary(false))
        assertNull(b3WebDavTestSummary(null))
    }

    @Test
    fun `R11 font entry resolves stored value and falls back on unknown blank`() {
        val entries = listOf("默认宋体", "外部目录字体", "应用目录字体")
        assertEquals("外部目录字体", b3FontEntryFor("外部目录字体", entries, entries.first()))
        assertEquals(entries.first(), b3FontEntryFor("不存在的档", entries, entries.first()))
        assertEquals(entries.first(), b3FontEntryFor(null, entries, entries.first()))
        assertEquals(entries.first(), b3FontEntryFor("   ", entries, entries.first()))
    }

    @Test
    fun `R13 backup manifest separates landed contents from pending ones`() {
        val titles = b3BackupContents.map { it.title }
        assertEquals(listOf("阅读进度", "生字本", "画板批注", "书签高亮"), titles)
        assertFalse(b3BackupContents.first { it.title == "生字本" }.landed)
        assertFalse(b3BackupContents.first { it.title == "画板批注" }.landed)
        val summary = b3BackupSummary(b3BackupContents)
        assertTrue(summary.contains("阅读进度"))
        assertTrue(summary.contains("书签高亮"))
        assertTrue(summary.contains("生字本"))
        assertTrue(summary.contains("画板批注"))
        assertTrue(summary.contains("待纳入"))
    }

    @Test
    fun `R13 conflict comparator keeps later timestamp and preserves local on tie`() {
        assertEquals(200L, b3PickLaterTimestamp(100L, 200L))
        assertEquals(200L, b3PickLaterTimestamp(200L, 100L))
        assertEquals(200L, b3PickLaterTimestamp(200L, 200L))
    }

    @Test
    fun `R14 e-ink options map landed keys to real PreferKey constants`() {
        assertEquals(
            listOf("关动画", "关粒子", "关 3D", "黑白灰配色"),
            b3EInkOptions.map { it.title },
        )
        val animated = b3EInkOptions.first { it.title == "关动画" }
        val gray = b3EInkOptions.first { it.title == "黑白灰配色" }
        assertEquals(PreferKey.noAnimScrollPage, animated.key)
        assertEquals(PreferKey.enableMangaGray, gray.key)
        assertTrue(animated.landed)
        assertTrue(gray.landed)
        assertNull(b3EInkOptions.first { it.title == "关粒子" }.key)
        assertNull(b3EInkOptions.first { it.title == "关 3D" }.key)
    }

    @Test
    fun `R19 null summary and blank keywords are skipped when matching`() {
        // 四参形态只有顶层纯函数 configPreferenceMatches；B3Row.matchesB3Query 是一参扩展函数，
        // 写成四参会因接收者类型不匹配在 CI 编译期报 Candidate inapplicable。
        assertTrue(configPreferenceMatches("主题", "主题模式", null, emptyList()))
        assertFalse(configPreferenceMatches("主题", "普通标题", null, listOf("  ")))
    }

    @Test
    fun `R19 empty query preserves group order and rows`() {
        val groups = listOf(
            B3SettingGroup(
                title = "甲",
                rows = listOf(B3InfoRow("行一", "摘要一", emptyList())),
            ),
            B3SettingGroup(
                title = "乙",
                rows = listOf(B3InfoRow("行二", "摘要二", emptyList())),
            ),
        )
        assertEquals(groups, filterB3Groups(groups, ""))
        assertEquals(groups, filterB3Groups(groups, "   "))
    }

    @Test
    fun `int and string fallbacks recover defaults on bad values`() {
        assertEquals(12, fallbackB3Int(12, 10))
        assertEquals(12, fallbackB3Int("12", 10))
        assertEquals(10, fallbackB3Int("abc", 10))
        assertEquals(10, fallbackB3Int(null, 10))
        assertEquals("x", fallbackB3String("x", "d"))
        assertEquals("d", fallbackB3String("   ", "d"))
        assertEquals("d", fallbackB3String(7, "d"))
        assertNull(fallbackB3String(null, null))
    }
}
