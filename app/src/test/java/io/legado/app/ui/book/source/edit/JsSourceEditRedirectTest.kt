package io.legado.app.ui.book.source.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JS 书源跳转 / 结果回传契约（Compose 重写后）。
 *
 * 旧 ActivityBookSourceEditBinding + BookSourceEditAdapter + KeyboardToolPop 已拆除（commit 93e1946）；
 * 跳转检测仍在 BookSourceEditActivity 宿主内，JS 判定先后收口到 SourceEditViewModel.initData，
 * 保存成功回调归 VM.save(source, success)。本类断言指向新契约，不再引用旧 TabLayout / 软键盘工具类。
 */
class JsSourceEditRedirectTest {

    private val activitySource by lazy { mainSource("ui/book/source/edit/BookSourceEditActivity.kt") }
    private val viewModelSource by lazy { mainSource("ui/book/source/edit/SourceEditViewModel.kt") }
    private val daoSource by lazy { mainSource("data/dao/BookSourceDao.kt") }

    @Test
    fun `js source detection happens before form initialization`() {
        // 入口 URL 先按 DAO 的 JS 源判定取出重定向标记，再走 setContent / initData 编排。
        val detection = activitySource.indexOf("val redirectJsSourceUrl = intent.getStringExtra(\"sourceUrl\")")
        val setContent = activitySource.indexOf("setContent {")
        assertTrue("redirect detection missing", detection >= 0)
        assertTrue("detection must precede setContent", detection < setContent)
        assertTrue(activitySource.contains("appDb.bookSourceDao.hasJsSource(it)"))

        // 已保存实体的 JS 判定在 initData 回调内，命中后直接拉起 JS 编辑页。
        val initData = activitySource.indexOf("vm.initData(intent)")
        assertTrue("initData missing", initData >= 0)
        val initBlock = activitySource.substring(initData)
        assertTrue(initBlock.contains("isJsSource()"))
        assertTrue(initBlock.contains("jsSourceEdit.launch"))

        // 重定向守卫在首次创建时拉起 JS 编辑页并 return，不继续本页表单初始化。
        val guard = activitySource.indexOf("if (redirectJsSourceUrl != null)")
        assertTrue("redirect guard missing", guard >= 0)
        val guardBlock = activitySource.substring(guard)
            .substringBefore("if (!LocalConfig.ruleHelpVersionIsLast)")
        assertTrue(guardBlock.contains("if (savedInstanceState == null)"))
        assertTrue(guardBlock.contains("return"))
    }

    @Test
    fun `early redirect preserves activity result forwarding`() {
        val launcher = activitySource.substringAfter("private val jsSourceEdit = registerForActivityResult")
            .substringBefore("private val qrCodeResult")

        assertTrue(launcher.contains("JsSourceEditActivity::class.java"))
        assertTrue(launcher.contains("setResult(result.resultCode, result.data)"))
        assertTrue(launcher.contains("finish()"))
        assertFalse(activitySource.contains("startActivity<JsSourceEditActivity>"))
    }

    @Test
    fun `every successful source save notifies the result caller`() {
        // VM 保存成功后回调 success；宿主在回调里回写 RESULT_OK 与 origin 并 finish。
        assertTrue(viewModelSource.contains("fun save(source: BookSource, success: ((BookSource) -> Unit)? = null)"))
        assertTrue(viewModelSource.contains("success?.invoke(it)"))

        val onSave = activitySource.substringAfter("override fun onSave()")
            .substringBefore("override fun onDebug()")
        assertTrue(onSave.contains("vm.save(source) { saved ->"))
        assertTrue(onSave.contains("setResult(RESULT_OK, Intent().putExtra(\"origin\", saved.bookSourceUrl))"))
        assertTrue(onSave.contains("finish()"))

        // 调试 / 登录等旁路保存仍走同一 save 入口，不另起静默保存路径。
        val saveThen = activitySource.indexOf("private fun saveThen")
        assertTrue("saveThen wrapper missing", saveThen >= 0)
        assertTrue(activitySource.substring(saveThen).contains("vm.save(source)"))
    }

    @Test
    fun `dao uses the same blank script semantics`() {
        assertTrue(daoSource.contains("fun getMainJs(key: String): String?"))
        assertTrue(daoSource.contains("fun hasJsSource(sourceUrl: String): Boolean"))
        assertTrue(daoSource.contains("!getMainJs(key).isNullOrBlank()"))
    }

    private fun mainSource(relativePath: String): String {
        val base = "src/main/java/io/legado/app/"
        return sequenceOf(File(base + relativePath), File("app/$base$relativePath"))
            .first { it.isFile }
            .readText()
    }
}
