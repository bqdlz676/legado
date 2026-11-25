package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 搜索页契约验收（读源码文本，离线可跑）：
 * - 12 色 token 收口，hex 色值只允许出现在 SearchTokens.kt；
 * - 不引 MaterialTheme、不挂 Material ripple（rememberRipple）；
 * - 切片-13 逐字文案「搜书名、作者、或正文中的任意一句」落在 token 与输入框；
 * - 六态（Idle/Loading/Content/Empty/Error/Offline）在页面分支中齐全；
 * - 源文件清单齐全。
 */
class SearchContractTest {

    private val sourceDir = "src/main/java/io/legado/app/ui/main/search"

    private val sourceFiles = listOf(
        "SearchTokens.kt",
        "SearchModels.kt",
        "SearchHighlight.kt",
        "SearchMatcher.kt",
        "SearchHistory.kt",
        "SearchHotResolver.kt",
        "SearchHotBinder.kt",
        "SearchReducer.kt",
        "SearchPagingOrchestrator.kt",
        "SearchMotion.kt",
        "SearchInputBar.kt",
        "SearchResultList.kt",
        "SearchHistoryPanel.kt",
        "SearchFallback.kt",
        "SearchScreen.kt",
        "InkSearchViewModel.kt",
        "SearchMappers.kt",
    )

    private fun projectFile(pathInApp: String): File =
        listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")

    private fun read(file: String): String =
        projectFile("$sourceDir/$file").readText()

    @Test
    fun `all source files exist`() {
        sourceFiles.forEach { assertTrue("缺源码文件 $it", projectFile("$sourceDir/$it").isFile) }
    }

    @Test
    fun `twelve frozen color tokens are declared`() {
        val tokens = read("SearchTokens.kt")
        val colorVals = listOf(
            "BG_PAGE", "BG_SURFACE", "BG_OVERLAY",
            "TEXT_PRIMARY", "TEXT_SECONDARY", "TEXT_TERTIARY", "TEXT_ACCENT",
            "BRAND_GOLD", "BRAND_COPPER",
            "COLOR_LINK", "COLOR_DIVIDER", "COLOR_GLOW",
        )
        assertEquals(12, colorVals.size)
        colorVals.forEach { assertTrue("缺 token $it", tokens.contains("val $it")) }
    }

    @Test
    fun `hex literals live only in tokens file`() {
        sourceFiles.filterNot { it == "SearchTokens.kt" }.forEach { file ->
            val text = read(file)
            assertFalse(
                "$file 出现 hex 色值，颜色必须只取 SearchTokens",
                Regex("0x[0-9A-Fa-f]{6,8}").containsMatchIn(text),
            )
        }
    }

    @Test
    fun `no MaterialTheme import anywhere`() {
        sourceFiles.forEach { file ->
            // 契约禁止的是 MaterialTheme 的 import 与实际调用（MaterialTheme.xxx）；
            // 注释里说明“不引 MaterialTheme”的文字不算违规。
            val text = read(file)
            val leaks = text.lines().any { line ->
                val trimmed = line.trim()
                (trimmed.startsWith("import ") && trimmed.contains("MaterialTheme")) ||
                    line.contains("MaterialTheme.")
            }
            assertFalse("$file 引用了 MaterialTheme", leaks)
        }
    }

    @Test
    fun `no material ripple attachment`() {
        sourceFiles.forEach { file ->
            val text = read(file)
            assertFalse("$file 使用 rememberRipple", text.contains("rememberRipple"))
            assertFalse(
                "$file 引用 material 包",
                text.contains("androidx.compose.material."),
            )
        }
    }

    @Test
    fun `spec hint copy is verbatim in tokens and input bar`() {
        assertEquals(
            "搜书名、作者、或正文中的任意一句",
            SearchTokens.HINT_GUIDE,
        )
        assertTrue(read("SearchTokens.kt").contains(SearchTokens.HINT_GUIDE))
        assertTrue(read("SearchInputBar.kt").contains("HINT_GUIDE"))
    }

    @Test
    fun `guide line renders at 12sp in input bar`() {
        val bar = read("SearchInputBar.kt")
        assertTrue(bar.contains("12.sp"))
    }

    @Test
    fun `screen branches cover all six load states`() {
        val screen = read("SearchScreen.kt")
        listOf(
            "SearchLoadState.Idle",
            "SearchLoadState.Loading",
            "SearchLoadState.Content",
            "SearchLoadState.Empty",
            "SearchLoadState.Error",
            "SearchLoadState.Offline",
        ).forEach { assertTrue("页面缺态分支 $it", screen.contains(it)) }
    }

    @Test
    fun `offline and error states expose retry action`() {
        val screen = read("SearchScreen.kt")
        assertTrue(screen.contains("onRetry"))
        assertTrue(read("SearchTokens.kt").contains(SearchTokens.RETRY_LABEL))
    }

    @Test
    fun `result list stays plain text without cover image`() {
        val list = read("SearchResultList.kt")
        assertFalse(list.contains("Image("))
        assertFalse(list.contains("AsyncImage"))
    }

    @Test
    fun `standalone search activity removed from manifest and old entry stays gone`() {
        val manifest = manifestFile().readText()
        // D25 清剿：独立搜索壳 InkSearchActivity 已从 Manifest 摘除，搜索只走书架浮层。
        assertFalse(manifest.contains(".ui.main.search.InkSearchActivity"))
        assertFalse(manifest.contains(".ui.book.search.SearchActivity"))
    }

    @Test
    fun `search package keeps shared stack without an activity shell class`() {
        // 检索编排 / 面板 / 列表构件仍被书架浮层复用；包内不再保留独立 Activity 壳。
        val activityShell = projectFile("$sourceDir/InkSearchActivity.kt")
        assertFalse("独立搜索壳文件应已删除", activityShell.isFile)
        val viewModel = read("InkSearchViewModel.kt")
        assertTrue(viewModel.contains("fun attach(initialKey: String?, initialScope: String?)"))
    }

    @Test
    fun `no old shell binding or adapter referenced in main sources`() {
        val mainRoot = listOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
        val violations = mainRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.readText() }
            .filter { text ->
                text.contains("ActivityBookSearchBinding") ||
                    text.contains("HistoryKeyAdapter") ||
                    text.contains("io.legado.app.ui.book.search.SearchActivity")
            }
        assertTrue("旧搜索壳残留引用: $violations", violations.none())
    }

    @Test
    fun `query length cap token is declared`() {
        assertTrue(read("SearchTokens.kt").contains("QUERY_MAX_LEN"))
    }

    @Test
    fun `reveal easing duration and overlay token match motion contract`() {
        val tokens = read("SearchTokens.kt")
        // 卡片浮入 cubic-bezier(.22,.61,.36,1)，页面转场 350ms，遮罩 bg-overlay 收口。
        assertTrue(tokens.contains("0.22f, 0.61f, 0.36f, 1f"))
        assertTrue(tokens.contains("DURATION_PAGE: Long = 350L"))
        assertTrue(tokens.contains("BG_OVERLAY"))
    }

    private fun manifestFile(): File =
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .firstOrNull { it.isFile } ?: error("Missing AndroidManifest.xml")
}
