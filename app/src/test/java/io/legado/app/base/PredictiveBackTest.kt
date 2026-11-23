package io.legado.app.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PredictiveBackTest {

    @Test
    fun `base activity leaves default back navigation to the system`() {
        val baseActivity = File(
            "src/main/java/io/legado/app/base/BaseActivity.kt"
        ).readText()
        val blanketFinishCallback = Regex(
            """onBackPressedDispatcher\.addCallback\(this\)\s*\{\s*finish\(\)\s*}"""
        )

        assertFalse(blanketFinishCallback.containsMatchIn(baseActivity))
        assertTrue(baseActivity.contains("OnBackInvokedCallback { onBackPressedDispatcher.onBackPressed() }"))
        assertFalse(baseActivity.contains("OnBackInvokedCallback { finish() }"))

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:enableOnBackInvokedCallback="true"""))
    }

    @Test
    fun `regular activities do not consume finish without closing`() {
        listOf(
            "src/main/java/io/legado/app/ui/main/search/InkSearchActivity.kt",
            "src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt"
        ).forEach { path ->
            assertFalse(File(path).readText().contains("override fun finish()"))
        }
    }

    @Test
    fun `activities intercept back before finish may defer closing`() {
        // 旧壳 Activity：返回回调内守卫 finish()，并保留 override fun finish() 收口。
        val oldShell = listOf(
            "src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditActivity.kt",
            "src/main/java/io/legado/app/ui/code/CodeEditActivity.kt",
            "src/main/java/io/legado/app/ui/book/audio/AudioPlayActivity.kt",
            "src/main/java/io/legado/app/ui/book/manga/ReadMangaActivity.kt",
        )
        val guardedFinishCallback = Regex(
            """onBackPressedDispatcher\.addCallback\(this\)\s*\{[^}]*\bfinish\(\)"""
        )
        oldShell.forEach { path ->
            val source = File(path).readText()
            assertTrue(guardedFinishCallback.containsMatchIn(source))
            assertTrue(source.contains("override fun finish()"))
        }

        // Compose 壳：返回键收口到 VM 的 dirty 守卫（requestExit：脏草稿弹确认，干净才退出），
        // 不再依赖旧 override fun finish()；宿主只转发 onBackPressed / 回调到 VM。
        val composeShell = listOf(
            "src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt",
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditActivity.kt",
            "src/main/java/io/legado/app/ui/replace/edit/ReplaceEditActivity.kt",
        )
        composeShell.forEach { path ->
            val source = File(path).readText()
            assertTrue("$path must route back through dirty-guarded exit",
                source.contains("requestExit()") || source.contains("isDirty()"))
        }
        val bookSourceEdit = File(
            "src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt"
        ).readText()
        assertTrue(bookSourceEdit.contains("onBackPressedDispatcher.addCallback(this)"))
        assertTrue(bookSourceEdit.contains("if (isDirty()) showExitConfirm = true else finish()"))

        val dirtyGuard = Regex("""fun requestExit\(\)\s*\{\s*if \(_uiState\.value\.dirty\)""")
        listOf(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditViewModel.kt",
            "src/main/java/io/legado/app/ui/replace/edit/ReplaceEditViewModel.kt",
        ).forEach { path ->
            assertTrue(dirtyGuard.containsMatchIn(File(path).readText()))
        }
    }

    @Test
    fun `replace editor handles cursor only result after discarded code edit`() {
        val source = File(
            "src/main/java/io/legado/app/ui/replace/edit/ReplaceEditActivity.kt"
        ).readText()

        // 新壳单字段编辑：全量编辑器回传 text / textFile 草稿，经 VM 写回 pattern；
        // 启动时仍带 cursorPosition 供编辑器定位（旧 fieldId 多字段路由随旧适配器拆除）。
        val codeEdit = source.substringAfter("private val codeEdit = registerForActivityResult")
            .substringBefore("override fun onCreate")
        // 新壳先解包 data 再取 text 额外项（旧链式 result.data?.getStringExtra 的细节已迁移）。
        assertTrue(codeEdit.contains("val data = result.data ?: return@registerForActivityResult"))
        assertTrue(codeEdit.contains("data.getStringExtra(\"text\")"))
        assertTrue(codeEdit.contains("textFile"))
        assertTrue(codeEdit.contains("viewModel.update { rule -> rule.copy(pattern = text) }"))
        assertTrue(source.contains("putExtra(\"cursorPosition\", current.length)"))
    }
}
