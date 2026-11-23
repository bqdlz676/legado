package io.legado.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundlePayloadContractTest {

    @Test
    fun `migrated recycler payloads use platform Bundle`() {
        val sourceRoot = sourceRoot()
        val violations = payloadSources.mapNotNull { relativePath ->
            val file = File(sourceRoot, relativePath)
            val source = file.readText()
            val importsAndroidxBundleOf =
                source.contains("import androidx.core.os.bundleOf")
            val callsBundleOf = Regex("""\bbundleOf\s*\(""").containsMatchIn(source)
            val usesPlatformBundle = Regex("""\bBundle\s*\(""").containsMatchIn(source)
            if (importsAndroidxBundleOf || callsBundleOf || !usesPlatformBundle) {
                relativePath
            } else {
                null
            }
        }

        assertTrue(
            "Migrated RecyclerView payloads must use android.os.Bundle directly: $violations",
            violations.isEmpty(),
        )
    }

    /**
     * 书架单路 Compose 改造后，旧 style1/style2 双路适配器已拆除；书源 / 词典规则 / 替换规则
     * 列表也换成 Compose 自绘壳。这些画面层不携带 RecyclerView payload，因此只做负向契约：
     * 不得回退到 androidx.core.os.bundleOf（与 payload 契约同一平台口径），不强制出现 `Bundle(`。
     */
    @Test
    fun `compose list hosts do not leak androidx bundleOf`() {
        val sourceRoot = sourceRoot()
        val missing = composeHostSources.filterNot { File(sourceRoot, it).isFile }
        assertTrue("Compose host sources missing: $missing", missing.isEmpty())

        val leaks = composeHostSources.mapNotNull { relativePath ->
            val source = File(sourceRoot, relativePath).readText()
            val importsAndroidxBundleOf =
                source.contains("import androidx.core.os.bundleOf")
            val callsBundleOf = Regex("""\bbundleOf\s*\(""").containsMatchIn(source)
            if (importsAndroidxBundleOf || callsBundleOf) relativePath else null
        }
        assertTrue(
            "Compose host sources must not use androidx.core.os.bundleOf: $leaks",
            leaks.isEmpty(),
        )
    }

    /**
     * 书架单路 + 画板自绘新实现的关键入口文件（替代已拆除的旧双路适配器条目）。
     * 路径口径与 [payloadSources] 一致，相对 sourceRoot()。
     */
    @Test
    fun `bookshelf single-path entry sources exist and stay readable`() {
        val sourceRoot = sourceRoot()
        val missing = bookshelfEntrySources.filterNot { File(sourceRoot, it).isFile }
        assertTrue("Bookshelf single-path entry sources missing: $missing", missing.isEmpty())

        val leaks = bookshelfEntrySources.mapNotNull { relativePath ->
            val source = File(sourceRoot, relativePath).readText()
            val importsAndroidxBundleOf =
                source.contains("import androidx.core.os.bundleOf")
            val callsBundleOf = Regex("""\bbundleOf\s*\(""").containsMatchIn(source)
            if (importsAndroidxBundleOf || callsBundleOf) relativePath else null
        }
        assertTrue(
            "Bookshelf entry sources must not use androidx.core.os.bundleOf: $leaks",
            leaks.isEmpty(),
        )
    }

    private fun sourceRoot(): File {
        return sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private companion object {
        /** 仍由 RecyclerView 适配器 / 对话框构造 payload Bundle 的旧实现入口。 */
        val payloadSources = listOf(
            "io/legado/app/ui/book/changesource/ChangeBookSourceDialog.kt",
            "io/legado/app/ui/book/changesource/ChangeChapterSourceDialog.kt",
            "io/legado/app/ui/book/explore/ExploreShowActivity.kt",
            "io/legado/app/ui/book/manage/BookAdapter.kt",
            "io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt",
            "io/legado/app/ui/book/toc/rule/TxtTocRuleDialog.kt",
            "io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt",
        )

        /**
         * 列表壳已 Compose 化的入口：旧 BookSourceAdapter / DictRuleAdapter / ReplaceRuleAdapter
         * 拆除后，对应编排落在下列 Screen / Activity；画面层不构造 payload Bundle。
         */
        val composeHostSources = listOf(
            "io/legado/app/ui/book/source/manage/BookSourceActivity.kt",
            "io/legado/app/ui/book/source/manage/SourceManageScreen.kt",
            "io/legado/app/ui/dict/rule/DictRuleScreen.kt",
            "io/legado/app/ui/replace/ReplaceRuleScreen.kt",
        )

        /**
         * 书架单路 + 画板自绘新实现的关键入口文件（替代已拆除的旧双路适配器条目）。
         * 路径口径与 [payloadSources] 一致，相对 sourceRoot()。
         */
        val bookshelfEntrySources = listOf(
            "io/legado/app/ui/main/bookshelf/BookshelfScreen.kt",
            "io/legado/app/ui/main/bookshelf/InkShelfFragment.kt",
            "io/legado/app/ui/components/inkslate/InkSlateCanvas.kt",
            "io/legado/app/ui/components/inkslate/InkSlateBitmap.kt",
            "io/legado/app/ui/main/PaintActivity.kt",
        )
    }
}
