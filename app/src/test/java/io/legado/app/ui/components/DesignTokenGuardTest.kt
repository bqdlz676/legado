package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S-03 设计 token 机械护栏（纯 JVM 静态单测，不依赖任何 Android 资源 / Compose 运行时）。
 *
 * 单源落点：[ComponentPalette] 是 12 色语义 token、四条缓动曲线、五档时长的唯一真值源
 * （平台无关的 Long / FloatArray / Int 常数，Compose 侧 [ComponentTokens] 只做类型转换）。
 * 本测试不新建第二套 token，只做两件事：
 * ① 契约断言：12 色 / 4 曲线 / 5 时长与锚点卡、切片-03 原文逐一吻合；
 * ② 静态护栏：UI 层基线外不得出现 8 位裸色值，代码不得引用 MaterialTheme。
 *
 * 基线口径：S-03 立项前已存在、本轮按「只加不删、旧值留 TODO」不能动的历史文件，
 * 登记在 [legacyBaseline]；测试双向校验「命中集 == 基线集」——新文件夹带裸色值会 FAIL，
 * 基线条目失效（文件被清理或改干净）也会 FAIL，防止护栏悄悄放宽。遗留文件的 token 收口
 * 归 D23 现状修复批次处理，不在本单测范围内。
 */
class DesignTokenGuardTest {

    @Test
    fun `anchor contract 12 colors 4 easings 5 durations match frozen source`() {
        // 12 色主 token 数量硬判：多一项少一项都 FAIL
        assertEquals("主 token 数量必须为 12", 12, ComponentPalette.mainTokens.size)

        // text-accent 与 color-link 同为 #C23B2E 是冻结原样，两个常量必须独立存在，禁止合并
        assertTrue("TEXT_ACCENT 必须独立声明", ComponentPalette.TEXT_ACCENT == 0xFFC23B2EL)
        assertTrue("COLOR_LINK 必须独立声明", ComponentPalette.COLOR_LINK == 0xFFC23B2EL)
        val paletteSource = File(sourceRoot, palettePath).readText()
        assertTrue("TEXT_ACCENT 必须独立声明", paletteSource.contains("val TEXT_ACCENT"))
        assertTrue("COLOR_LINK 必须独立声明", paletteSource.contains("val COLOR_LINK"))

        // 四条缓动曲线：系数逐条照切片-03 动效层增补表
        assertEasing(ComponentPalette.CARD_ENTER_EASING, 0.22f, 0.61f, 0.36f, 1f)
        assertEasing(ComponentPalette.BUBBLE_EASING, 0.34f, 1.56f, 0.64f, 1f)
        assertEasing(ComponentPalette.PAGE_TURN_EASING, 0.4f, 0f, 0.2f, 1f)
        assertEasing(ComponentPalette.MASK_EASING, 0.4f, 0f, 0.6f, 1f)

        // 五档时长：逐条照切片-03 duration 常数段（毫秒）
        assertEquals(120, ComponentPalette.DURATION_MICRO)
        assertEquals(250, ComponentPalette.DURATION_CARD)
        assertEquals(250, ComponentPalette.DURATION_BUBBLE)
        assertEquals(350, ComponentPalette.DURATION_PAGE)
        assertEquals(200, ComponentPalette.DURATION_MASK)
        assertEquals(2800, ComponentPalette.DURATION_BOOT)

        // 锚点卡 kebab 语义名与原始 rgba / hex 文本必须留在单源文件 KDoc 内，供回源核对
        listOf(
            "bg-page", "bg-surface", "bg-overlay",
            "text-primary", "text-secondary", "text-tertiary", "text-accent",
            "brand-gold", "brand-copper",
            "color-link", "color-divider", "color-glow",
        ).forEach { name ->
            assertTrue("单源文件缺少锚点语义名 $name", paletteSource.contains(name))
        }
        listOf(
            "#F7F1E6", "#EFE8D8", "rgba(26,20,16,.45)",
            "#1A1410", "#5C4F3A", "#9B9687", "#C23B2E",
            "#D4A54A", "#8B6914",
            "rgba(26,20,16,.08)", "rgba(212,165,74,.3)",
        ).forEach { value ->
            assertTrue("单源文件缺少锚点色值 $value", paletteSource.contains(value))
        }
    }

    @Test
    fun `ui layer has no inline hex color outside the legacy baseline`() {
        // 锚点 §四.1 口径：8 位 ARGB 字面量；后缀 L/uL 的 long 位掩码 / 哈希常量不算色值
        val hexPattern = Regex("(?<![0-9A-Fa-f])-?0x[0-9A-Fa-f]{8}(?![0-9A-Fa-fLu])")
        val actualHexFiles = uiSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { hexPattern.containsMatchIn(it.readText()) }
            // 基线清单用的是仓库内相对路径（io/legado/app/ui/...），此处必须补同前缀，
            // 否则 relativize 得到的 "book/read/..." 与基线永远对不上 → 全量误报 FAIL（CI run 45454989707 实测）
            .map { UI_PREFIX + uiSourceRoot.toPath().relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
            .toSet()

        // 单源定义文件本身豁免；其余命中必须全部落在遗留基线内
        val unexpected = actualHexFiles - tokenSourceFiles - legacyBaseline
        assertTrue("基线外文件夹带裸十六进制色值：$unexpected", unexpected.isEmpty())
        val stale = legacyBaseline - actualHexFiles
        assertTrue("基线条目已无裸色值却未移出基线：$stale", stale.isEmpty())
    }

    @Test
    fun `ui layer code references no MaterialTheme`() {
        val violations = mutableListOf<String>()
        uiSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val rel = uiSourceRoot.toPath().relativize(file.toPath()).toString()
                file.readLines().forEachIndexed { index, rawLine ->
                    val codeLine = stripComment(rawLine)
                    if (codeLine.contains("MaterialTheme")) {
                        violations.add("$rel:${index + 1}: $codeLine")
                    }
                }
            }
        assertTrue("UI 层代码不得引用 MaterialTheme：$violations", violations.isEmpty())
    }

    private fun assertEasing(actual: FloatArray, x1: Float, y1: Float, x2: Float, y2: Float) {
        assertEquals("缓动曲线必须为四参数", 4, actual.size)
        assertEquals(x1, actual[0], 1e-6f)
        assertEquals(y1, actual[1], 1e-6f)
        assertEquals(x2, actual[2], 1e-6f)
        assertEquals(y2, actual[3], 1e-6f)
    }

    /** 去掉行注释与 KDoc 行；注释里「不包裹 MaterialTheme」这类否定说明不算代码引用。 */
    private fun stripComment(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return ""
        return line.substringBefore("//")
    }

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private val uiSourceRoot: File by lazy {
        File(sourceRoot, "io/legado/app/ui")
    }

    private companion object {
        /** 基线清单用的仓库内相对路径前缀；与 uiSourceRoot.relativize 结果拼接后才可比（漏拼会全量误报） */
        const val UI_PREFIX = "io/legado/app/ui/"

        const val palettePath = "io/legado/app/ui/components/ComponentPalette.kt"

        /** token 单源定义文件：色值字面量允许出现在这里，其余位置禁止内联。 */
        val tokenSourceFiles = setOf(palettePath)

        /**
         * S-03 立项前遗留、本轮不改的历史文件（旧子域 token 副本与自绘 View）。
         * 为什么单列：硬约束本轮只加不删；这些文件的 token 收口随 D23 现状修复批次迁移到
         * [ComponentPalette]，迁移完成后从本基线摘除，测试会反向提醒条目失效。
         * 内含少量 0x7FFFFFFF 等位掩码 / 哈希常量，非色值，一并登记避免误报。
         */
        val legacyBaseline = setOf(
            "io/legado/app/ui/book/read/HighlightStyleDialog.kt",
            "io/legado/app/ui/book/read/ReadBookActivity.kt",
            "io/legado/app/ui/book/read/ink/InkTokens.kt",
            "io/legado/app/ui/book/read/page/ContentTextView.kt",
            "io/legado/app/ui/book/read/page/HighlightDraw.kt",
            "io/legado/app/ui/book/read/page/InkMoodView.kt",
            "io/legado/app/ui/book/read/page/ReadView.kt",
            "io/legado/app/ui/book/read/page/delegate/CoverPageDelegate.kt",
            "io/legado/app/ui/book/read/page/delegate/SimulationPageDelegate.kt",
            "io/legado/app/ui/card/CardTokens.kt",
            "io/legado/app/ui/card/SearchResultCard.kt",
            "io/legado/app/ui/card/TopicMarxCard.kt",
            // S-12 接线后该文件裸 hex 已收口到 InkDerivedColors（K-18），故从基线移除；
            // 保持"命中集==基线集"双向对账，防止旧基线变成永远对不上的脏数据。
            "io/legado/app/ui/main/MapWebHolder.kt",
            "io/legado/app/ui/main/PersonDetailActivity.kt",
            "io/legado/app/ui/main/RelationActivity.kt",
            "io/legado/app/ui/main/bookshelf/ShelfTokens.kt",
            "io/legado/app/ui/main/chrono/ChronoTokens.kt",
            "io/legado/app/ui/main/explore/ExploreInkFragment.kt",
            "io/legado/app/ui/main/explore/InkExploreTokens.kt",
            "io/legado/app/ui/main/my/MyInkFragment.kt",
            "io/legado/app/ui/main/search/SearchTokens.kt",
            "io/legado/app/ui/map/globe/astro/StarField.kt",
            "io/legado/app/ui/map/globe/terrain/FbmNoise.kt",
            "io/legado/app/ui/map/replay/BattleArrow.kt",
            "io/legado/app/ui/map/replay/ClashEffect.kt",
            "io/legado/app/ui/map/replay/PieceVisual.kt",
            "io/legado/app/ui/map/sandbox/CityNode.kt",
            "io/legado/app/ui/welcome/LuowenSplashTokens.kt",
            "io/legado/app/ui/widget/InkDustView.kt",
            "io/legado/app/ui/widget/InkPaperDrawable.kt",
            "io/legado/app/ui/widget/InkPress.kt",
            "io/legado/app/ui/widget/InkProgressLineView.kt",
            "io/legado/app/ui/widget/InkReadBar.kt",
            "io/legado/app/ui/widget/InkShelfBar.kt",
            "io/legado/app/ui/widget/InkSwitchView.kt",
            "io/legado/app/ui/widget/InkThinProgress.kt",
            "io/legado/app/ui/widget/InkTimelineView.kt",
            "io/legado/app/ui/widget/anima/RefreshProgressBar.kt",
        )
    }
}
