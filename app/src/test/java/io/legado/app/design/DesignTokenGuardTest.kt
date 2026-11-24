package io.legado.app.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S-03 设计 token 机械护栏（纯 JVM 静态单测，不依赖任何 Android 资源 / Compose 运行时）。
 *
 * 为什么需要护栏：单源约定靠人记不住，必须让 CI 在静态层机械兜住——
 * ① UI 层不得内联裸十六进制色值，颜色只许取自 token 单源；
 * ② UI 层代码不得引用 MaterialTheme（Compose 自绘，不取 Material colorScheme / ripple）。
 *
 * 基线口径：S-03 立项前已存在、本轮按硬约束「只新增不改既有文件」不能动的历史文件，
 * 登记在 [legacyBaseline]；测试双向校验「命中集 == 基线集」——新文件夹带裸色值会 FAIL，
 * 基线条目失效（文件被清理或改干净）也会 FAIL，防止护栏悄悄放宽。遗留文件的 token 收口
 * 归 D23 现状修复批次处理，不在本单测范围内。
 */
class DesignTokenGuardTest {

    @Test
    fun `color token file carries the frozen 12 anchor values verbatim`() {
        val source = File(sourceRoot, colorTokenPath).readText()

        // 锚点卡 §二参数表的 12 个语义名（kebab 原名，KDoc 内必须可回源）
        val anchorNames = listOf(
            "bg-page", "bg-surface", "bg-overlay",
            "text-primary", "text-secondary", "text-tertiary", "text-accent",
            "brand-gold", "brand-copper",
            "color-link", "color-divider", "color-glow",
        )
        anchorNames.forEach { name ->
            assertTrue("token 文件缺少锚点语义名 $name", source.contains(name))
        }

        // 锚点卡原始值逐字保留（hex 与 rgba 文本，供回源核对）
        val anchorValues = listOf(
            "#F7F1E6", "#EFE8D8", "rgba(26,20,16,.45)",
            "#1A1410", "#5C4F3A", "#9B9687", "#C23B2E",
            "#D4A54A", "#8B6914",
            "rgba(26,20,16,.08)", "rgba(212,165,74,.3)",
        )
        anchorValues.forEach { value ->
            assertTrue("token 文件缺少锚点色值 $value", source.contains(value))
        }

        // 数量硬判：恰好 12 个颜色常量，多一项少一项都 FAIL
        val valCount = source.lineSequence().count { it.trim().startsWith("val ") }
        assertEquals("颜色 token 数量必须为 12", 12, valCount)

        // text-accent 与 color-link 同为 #C23B2E 是冻结原样，两个常量都必须独立存在
        assertTrue("TEXT_ACCENT 必须独立声明", source.contains("val TEXT_ACCENT"))
        assertTrue("COLOR_LINK 必须独立声明", source.contains("val COLOR_LINK"))
    }

    @Test
    fun `motion token file carries four easings and five durations from slice 03`() {
        val source = File(sourceRoot, motionTokenPath).readText()

        // 切片-03 §动效层增补 · 缓动曲线表，四组贝塞尔系数逐条照抄
        val easings = listOf(
            "CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)",
            "CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)",
            "CubicBezierEasing(0.4f, 0f, 0.2f, 1f)",
            "CubicBezierEasing(0.4f, 0f, 0.6f, 1f)",
        )
        easings.forEach { easing ->
            assertTrue("缺少缓动曲线 $easing", source.contains(easing))
        }

        // 切片-03 duration 常数段，五档时长逐条照抄（毫秒）
        val durations = listOf(
            "DURATION_MICRO_MS: Int = 120",
            "DURATION_CARD_BUBBLE_MS: Int = 250",
            "DURATION_PAGE_TRANSITION_MS: Int = 350",
            "DURATION_MASK_MS: Int = 200",
            "DURATION_BOOT_PARTICLES_MS: Int = 2800",
        )
        durations.forEach { duration ->
            assertTrue("缺少时长常量 $duration", source.contains(duration))
        }
    }

    @Test
    fun `ui layer has no inline hex color outside the legacy baseline`() {
        // 锚点 §四.1 口径：8 位 ARGB 字面量；后缀 L/uL 的 long 位掩码 / 哈希常量不算色值
        val hexPattern = Regex("(?<![0-9A-Fa-f])-?0x[0-9A-Fa-f]{8}(?![0-9A-Fa-fLu])")
        val actualHexFiles = uiSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { hexPattern.containsMatchIn(it.readText()) }
            .map { uiSourceRoot.toPath().relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
            .toSet()

        // 双向相等：基线外出现裸色值 → FAIL；基线条目失效 → FAIL
        val unexpected = actualHexFiles - legacyBaseline
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
        const val colorTokenPath = "io/legado/app/design/DesignColorTokens.kt"
        const val motionTokenPath = "io/legado/app/design/DesignMotionTokens.kt"

        /**
         * S-03 立项前遗留、本轮不改的历史文件（含旧子域 token 副本与自绘 View）。
         * 为什么单列：硬约束本轮只新增文件；这些文件的 token 收口随 D23 现状修复批次迁移到
         * [DesignColorTokens]，迁移完成后从本基线摘除，测试会反向提醒条目失效。
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
            "io/legado/app/ui/components/ComponentPalette.kt",
            "io/legado/app/ui/components/inkslate/InkSlateModel.kt",
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
            "io/legado/app/ui/widget/anima/RefreshProgressBar.kt",
            "io/legado/app/ui/widget/InkDustView.kt",
            "io/legado/app/ui/widget/InkPaperDrawable.kt",
            "io/legado/app/ui/widget/InkPress.kt",
            "io/legado/app/ui/widget/InkProgressLineView.kt",
            "io/legado/app/ui/widget/InkReadBar.kt",
            "io/legado/app/ui/widget/InkShelfBar.kt",
            "io/legado/app/ui/widget/InkSwitchView.kt",
            "io/legado/app/ui/widget/InkThinProgress.kt",
            "io/legado/app/ui/widget/InkTimelineView.kt",
        )
    }
}
