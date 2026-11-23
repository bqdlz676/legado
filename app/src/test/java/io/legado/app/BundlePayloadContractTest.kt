package io.legado.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundlePayloadContractTest {

    @Test
    fun `migrated recycler payloads use platform Bundle`() {
        val sourceRoot = sourceRoot()
        val violations = migratedPayloadSources.mapNotNull { relativePath ->
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
     * 书架单路 Compose 改造后，旧 style1/style2 双路适配器已拆除。
     * 这里收口新单路实现的关键入口文件：必须真实存在、可读，且不得回退到
     * androidx.core.os.bundleOf（与上方 payload 契约同一平台口径）。
     * Compose 画面层本身不携带 payload，因此这里不强制出现 `Bundle(`。
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
        val migratedPayloadSources = listOf(
            "io/legado/app/ui/book/changesource/ChangeBookSourceDialog.kt",
            "io/legado/app/ui/book/changesource/ChangeChapterSourceDialog.kt",
            "io/legado/app/ui/book/explore/ExploreShowActivity.kt",
            "io/legado/app/ui/book/manage/BookAdapter.kt",
            "io/legado/app/ui/book/source/manage/BookSourceActivity.kt",
            "io/legado/app/ui/book/source/manage/BookSourceAdapter.kt",
            "io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt",
            "io/legado/app/ui/book/toc/rule/TxtTocRuleDialog.kt",
            "io/legado/app/ui/dict/rule/DictRuleAdapter.kt",
            "io/legado/app/ui/replace/ReplaceRuleAdapter.kt",
            "io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt",
        )

        /**
         * 书架单路 + 画板自绘新实现的关键入口文件（替代已拆除的旧双路适配器条目）。
         * 路径口径与 [migratedPayloadSources] 一致，相对 sourceRoot()。
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
