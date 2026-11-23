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
        "SearchReducer.kt",
        "SearchMotion.kt",
        "SearchInputBar.kt",
        "SearchResultList.kt",
        "SearchHistoryPanel.kt",
        "SearchFallback.kt",
        "SearchScreen.kt",
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
            assertFalse(
                "$file 引用了 MaterialTheme",
                read(file).contains("MaterialTheme"),
            )
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
}
