package io.legado.app.ui.autoTask

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import io.legado.app.ui.association.jsonImportType
import org.junit.Test
import java.io.File

class AutoTaskImportContractTest {

    @Test
    fun `automatic task import is routed from files and links`() {
        val base = projectFile(
            "src/main/java/io/legado/app/ui/association/BaseAssociationViewModel.kt"
        )
        val online = projectFile(
            "src/main/java/io/legado/app/ui/association/OnLineImportActivity.kt"
        )
        val file = projectFile(
            "src/main/java/io/legado/app/ui/association/FileAssociationActivity.kt"
        )
        // 旧菜单 R.id.menu_import_local / menu_import_on_line 随 Compose 壳拆除；
        // 本地 / 在线两入口接线归宿主 Activity 的 importDoc / openImportUrl 回调。
        val activity = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskActivity.kt"
        )

        assertTrue(base.contains("map.containsKey(\"cron\") && map.containsKey(\"script\")"))
        assertEquals("autoTask", jsonImportType(mapOf("cron" to "0 * * * *", "script" to "test")))
        assertTrue(base.contains("successLive.postValue(type to uri.toString())"))
        assertTrue(online.contains("\"/autoTask\" -> showDialogFragment("))
        assertTrue(online.contains("\"/auto\" -> viewModel.determineType("))
        assertTrue(online.contains("\"autoTask\" -> showDialogFragment("))
        assertTrue(file.contains("\"autoTask\" -> showDialogFragment("))
        assertTrue(activity.contains("onImportLocal"))
        assertTrue(activity.contains("onOpenImportOnline"))
    }

    @Test
    fun `automatic task share passphrase routes import and export`() {
        val main = projectFile("src/main/java/io/legado/app/ui/main/MainActivity.kt")
        val activity = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskActivity.kt"
        )
        val viewModel = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt"
        )

        // 解码（粘贴口令导入）路由保留在 MainActivity。
        assertTrue(main.contains("SourceSharePassphrase.Type.AUTO_TASK ->"))
        assertTrue(main.contains("showDialogFragment(ImportAutoTaskDialog(value.url))"))
        // 导出侧旧 ActionBar 口令按钮 / adapter.selection 随壳拆除；
        // 所选任务导出归 ViewModel.selectionJson()，文件落盘归宿主 exportDoc。
        assertTrue(activity.contains("private val exportDoc = registerForActivityResult"))
        assertTrue(activity.contains("viewModel.selectionJson()"))
        assertTrue(viewModel.contains("AutoTask.exportJson("))
        assertFalse(activity.contains("sourceSharePassphraseButton("))
        assertFalse(activity.contains("adapter.selection"))
    }

    @Test
    fun `automatic task import batches storage and scheduler refresh`() {
        val model = projectFile("src/main/java/io/legado/app/model/AutoTask.kt")
        val block = model.substringAfter("fun importRules(")
            .substringBefore("fun delete(")

        assertTrue(block.contains("if (rules.isEmpty()) return emptyList()"))
        assertTrue(block.contains("prepareImportedAutoTasks(all(), rules)"))
        assertTrue(block.contains("appDb.autoTaskRuleDao.upsert(*imported.toTypedArray())"))
        assertTrue(block.contains("AutoTaskScheduler.refresh(context)"))
    }

    @Test
    fun `automatic task import is single shot and validates editable fields`() {
        val viewModel = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskViewModel.kt"
        )

        assertTrue(viewModel.contains("if (importStarted) return"))
        assertTrue(viewModel.contains("importStarted = false"))
        assertTrue(viewModel.contains("validateImportedTask"))
        assertTrue(viewModel.contains("AutoTask.DEFAULT_CRON"))
        assertTrue(viewModel.contains("CronSchedule.parse(normalized.cron.orEmpty())"))
        assertTrue(viewModel.contains("AutoTask.normalizeScript(normalized.script).isBlank()"))

        val importBlock = viewModel.substringAfter("fun importSelect(")
            .substringBefore("fun importSource(")
        val editBlock = viewModel.substringAfter("fun updateTaskAt(")
            .substringBefore("private suspend fun importSourceAwait(")
        assertTrue(importBlock.contains(".map(::validateImportedTask)"))
        assertTrue(editBlock.contains("validateImportedTask(task)"))
    }

    @Test
    fun `automatic task import keeps the dialog title readable`() {
        val dialog = projectFile(
            "src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt"
        )
        // 旧 Fragment onFragmentCreated + binding.toolBar 背景先行标题的口径随 Compose 壳拆除；
        // 新弹窗标题行在 ImportContent 顶部，标题色取主文本色令牌，保证可读。
        val titleRow = dialog.substringAfter("Row(verticalAlignment = Alignment.CenterVertically)")
            .substringBefore("when {")

        assertTrue(titleRow.contains("导入自动任务"))
        assertTrue(titleRow.contains("ComponentTokens.textPrimary"))
        assertFalse(dialog.contains("binding.toolBar"))
    }

    private fun projectFile(pathInApp: String): String {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("Missing project file: $pathInApp")
    }
}
