package io.legado.app.ui.autoTask

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 自动任务编辑页契约（Compose 重写后）：
 * 旧 AutoTaskEditActivity 的 XML Binding / 菜单 / 布局壳已拆除，表单编排归 AutoTaskEditScreen，
 * 草稿 / 校验 / 未保存退出归 AutoTaskEditViewModel，全屏编辑结果回写归宿主 Activity。
 */
class AutoTaskEditActivitySourceTest {

    @Test
    fun `unsaved edits prompt only after a task has been bound`() {
        val viewModel = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditViewModel.kt"
        ).readText().replace("\r\n", "\n")
        val screen = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt"
        ).readText().replace("\r\n", "\n")
        val activity = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditActivity.kt"
        ).readText().replace("\r\n", "\n")
        val loadBlock = viewModel.substringAfter("fun load(id: String?)")
            .substringBefore("fun update(")
        val saveBlock = viewModel.substringAfter("fun save(after:")
            .substringBefore("fun copyRule(")

        // 绑定已有任务时 origin 与草稿一并回填；保存成功后 origin 刷新为已存实体。
        assertTrue(loadBlock.contains("origin = loaded"))
        assertTrue(saveBlock.contains("origin = saved"))
        // 脏口径：未绑定（origin == null）视为脏；绑定后仅当草稿偏离已存实体才提示。
        assertTrue(viewModel.contains("origin?.let { it != draft } ?: true"))
        assertTrue(viewModel.contains("if (_uiState.value.dirty)"))
        // 退出确认弹窗与确认后回传 finish 接线仍在。
        assertTrue(screen.contains("state.confirmExit"))
        assertTrue(screen.contains("放弃未保存的修改？"))
        assertTrue(activity.contains("viewModel.onExit = {"))
        assertTrue(activity.contains("finish()"))
    }

    @Test
    fun `full editor routes supported code fields and keeps result state`() {
        val activity = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditActivity.kt"
        ).readText().replace("\r\n", "\n")
        val screen = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt"
        ).readText().replace("\r\n", "\n")

        // 全屏编辑仍走 CodeEditActivity；结果经 pendingField 回写草稿，不落库。
        assertTrue(activity.contains("CodeEditActivity::class.java"))
        assertTrue(activity.contains("pendingField"))
        assertTrue(activity.contains("viewModel.update { rule -> applyField(rule, pendingField, text) }"))
        assertTrue(activity.contains("getStringExtra(\"text\")"))
        assertTrue(activity.contains("getStringExtra(\"textFile\")"))
        assertTrue(activity.contains("cursorPosition"))
        // 六个代码字段在枚举声明与 Activity 回写分支中一一对应。
        assertTrue(
            screen.contains(
                "enum class AutoTaskField { SCRIPT, HEADER, JSLIB, LOGIN_URL, LOGIN_UI, LOGIN_CHECK_JS }"
            )
        )
        listOf("SCRIPT", "HEADER", "JSLIB", "LOGIN_URL", "LOGIN_UI", "LOGIN_CHECK_JS").forEach { field ->
            assertTrue(activity.contains("AutoTaskField.$field ->"))
        }
        // 旧 auto_task_edit.xml 菜单与 ic_code 图标随壳拆除；全屏入口为 Screen 工具动作。
        assertTrue(screen.contains("onFullEdit"))
        assertFalse(activity.contains("menu_fullscreen_edit"))
    }

    @Test
    fun `login url uses the same multiline code input as login ui`() {
        val screen = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt"
        ).readText().replace("\r\n", "\n")
        val activity = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditActivity.kt"
        ).readText().replace("\r\n", "\n")

        val loginUrlField = screen.substringAfter("\"登录 URL\"")
            .substringBefore("\"登录 UI\"")
        val loginUiField = screen.substringAfter("\"登录 UI\"")
            .substringBefore("\"登录校验 JS\"")

        // 登录 URL / 登录 UI / 登录校验 JS 三个字段均在表单中，且均可进入全屏代码编辑器。
        assertTrue(loginUrlField.contains("rule.loginUrl"))
        assertTrue(loginUiField.contains("rule.loginUi"))
        assertTrue(activity.contains("AutoTaskField.LOGIN_URL -> rule.copy(loginUrl = text.ifBlank { null })"))
        assertTrue(activity.contains("AutoTaskField.LOGIN_UI -> rule.copy(loginUi = text.ifBlank { null })"))
        // 登录 UI 内联仍是多行代码输入；旧 XML 的 monospace / gravity / minLines 属性随布局壳拆除。
        assertTrue(loginUiField.contains("multiLine = true"))
        assertFalse(screen.contains("activity_auto_task_edit.xml"))
    }

    @Test
    fun `copy and paste keep task identity and leave pasted values unsaved`() {
        val viewModel = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditViewModel.kt"
        ).readText().replace("\r\n", "\n")
        val screen = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt"
        ).readText().replace("\r\n", "\n")
        val copyBlock = viewModel.substringAfter("fun copyRule()")
            .substringBefore("fun pasteRule()")
        val pasteBlock = viewModel.substringAfter("fun pasteRule()")
            .substringBefore("fun requestExit()")

        assertTrue(copyBlock.contains("withContext(Dispatchers.Default)"))
        assertTrue(copyBlock.contains("AutoTask.exportJson(listOf(_uiState.value.draft))"))
        // 粘贴解析走 execute 默认 IO 调度（旧显式 withContext(Default) 包裹已并入 execute 上下文）。
        assertTrue(pasteBlock.contains("execute {"))
        assertTrue(pasteBlock.contains("GSON.fromJsonObject<AutoTaskRule>(text)"))
        assertTrue(pasteBlock.contains("GSON.fromJsonArray<AutoTaskRule>(text)"))
        assertTrue(pasteBlock.contains("singleOrNull()"))
        assertTrue(pasteBlock.contains("it.copy(draft = pasted)"))
        // 粘贴只替换草稿，不动 origin；旧壳直接覆写 task / 重置 origin 的路径不得回流。
        assertFalse(pasteBlock.contains("origin = "))
        assertFalse(pasteBlock.contains("task = pasted"))
        assertTrue(screen.contains("\"复制\""))
        assertTrue(screen.contains("\"粘贴\""))
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }
}
