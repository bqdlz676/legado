package io.legado.app.ui.about

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateDialogLifecycleTest {

    @Test
    fun `update callbacks skip dialogs after fragment state is saved`() {
        val shared = projectFile(
            "src/main/java/io/legado/app/ui/about/CheckAppUpdate.kt"
        ).readText()

        assertGuardBeforeDialog(shared, "isAdded && !childFragmentManager.isStateSaved")
        // 旧 AboutFragment 随 Compose 重写拆除，关于页入口归 AboutActivity；MyFragment 路由保留。
        for (path in listOf("ui/about/AboutActivity.kt", "ui/main/my/MyFragment.kt")) {
            val caller = projectFile("src/main/java/io/legado/app/$path").readText()
            assertTrue(caller.contains("\"check_update\" -> checkAppUpdate()"))
            assertTrue(caller.contains("\"check_beta_update\" -> checkAppUpdate(beta = true)"))
        }
    }

    @Test
    fun `update dialog carries release metadata into the toolbar`() {
        val source = projectFile(
            "src/main/java/io/legado/app/ui/about/UpdateDialog.kt"
        ).readText()

        assertTrue(source.contains("putLong(\"size\", updateInfo.size)"))
        assertTrue(source.contains("putLong(\"createdAt\", updateInfo.createdAt)"))
        assertTrue(source.contains("ConvertUtils.formatFileSize(size)"))
        assertTrue(source.contains("DateTimeFormatter.ISO_LOCAL_DATE"))
    }

    @Test
    fun `update dialog uses the system download action for beta and formal updates`() {
        val source = projectFile(
            "src/main/java/io/legado/app/ui/about/UpdateDialog.kt"
        ).readText()
        val downloadClosure = source.substringAfter("onDownload = {")
            .substringBefore("onDownloadBackup")
        val browserClosure = source.substringAfter("onOpenBrowser = {")
            .substringBefore("onIgnore")

        assertTrue(source.contains("putBoolean(\"isBeta\", updateInfo.isBeta)"))
        // 正式 / 测试版顶部下载动作都走系统下载流 Download.start；浏览器打开仅归属 onOpenBrowser。
        assertTrue(downloadClosure.contains("startDownload(args?.getString(\"url\")"))
        assertTrue(source.contains("Download.start(requireContext(), url, name, isAppUpdate = true)"))
        assertTrue(source.contains("R.string.beta_update_now"))
        assertFalse(downloadClosure.contains("openUrl"))
        assertTrue(browserClosure.contains("openUrl"))
    }

    @Test
    fun `formal update hides only the primary toolbar download action`() {
        val source = projectFile(
            "src/main/java/io/legado/app/ui/about/UpdateDialog.kt"
        ).readText()

        // 旧工具栏菜单显隐口径随 Compose 壳拆除：正式版额外的备份 / 镜像 / 备用镜像行与忽略版本
        // 均由 `if (!isBeta)` 条件渲染；测试版顶部只保留取消 + 立即更新。
        assertTrue(source.contains("if (isBeta) {"))
        val firstNonBeta = source.indexOf("if (!isBeta)")
        val ignoreGuard = source.indexOf("if (!isBeta)", firstNonBeta + 1)
        assertTrue(firstNonBeta >= 0)
        assertTrue(ignoreGuard > firstNonBeta)
        val mirrorRow = source.substring(firstNonBeta, ignoreGuard)
        assertTrue(mirrorRow.contains("onDownloadBackup"))
        assertTrue(mirrorRow.contains("onDownloadMirror"))
        assertTrue(mirrorRow.contains("onDownloadAlternate"))
        val ignoreBlock = source.substring(ignoreGuard)
        assertTrue(ignoreBlock.contains("R.string.ignore_this_version"))
        assertFalse(source.contains("binding.toolBar.menu"))
    }

    @Test
    fun `formal update dialog exposes the backup cdn separately from github`() {
        val source = projectFile(
            "src/main/java/io/legado/app/ui/about/UpdateDialog.kt"
        ).readText()

        // 备份 CDN / 镜像 / 备用镜像三路 URL 仍由 arguments 透传，分别接到独立下载闭包。
        assertTrue(source.contains("putString(\"mirrorUrl\", updateInfo.mirrorDownloadUrl)"))
        assertTrue(
            source.contains(
                "putString(\"alternateMirrorUrl\", updateInfo.alternateMirrorDownloadUrl)"
            )
        )
        assertTrue(source.contains("putString(\"backupUrl\", updateInfo.backupDownloadUrl)"))
        assertTrue(source.contains("onDownloadBackup = { startDownload(args?.getString(\"backupUrl\")"))
        assertTrue(source.contains("onDownloadMirror = { startDownload(args?.getString(\"mirrorUrl\")"))
        assertTrue(source.contains("onDownloadAlternate = { startDownload(args?.getString(\"alternateMirrorUrl\")"))
        // 旧 app_update.xml 菜单项随壳拆除；镜像三路在 Compose 行内各自独立成按钮。
        assertFalse(source.contains("menu_download_mirror"))
        assertFalse(source.contains("menu_download_alternate_mirror"))
        assertFalse(source.contains("menu_download_backup"))
    }

    @Test
    fun `manual update errors show their message without a redundant action prefix`() {
        val shared = projectFile(
            "src/main/java/io/legado/app/ui/about/CheckAppUpdate.kt"
        ).readText()
        assertTrue(shared.contains("AppUpdate.checkBeta(lifecycleScope)"))
        assertTrue(shared.contains("AppUpdate.gitHubUpdate.check(lifecycleScope)"))
        assertTrue(shared.contains("appCtx.toastOnUi(it.localizedMessage)"))
        assertFalse(shared.contains("getString(R.string.check_update)"))
        assertFalse(shared.contains("getString(R.string.check_beta_update)"))
    }

    private fun assertGuardBeforeDialog(source: String, guard: String) {
        assertTrue(source.indexOf(guard) in 0 until source.indexOf("UpdateDialog(it)"))
    }

    private fun projectFile(pathInApp: String): File {
        return sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?: error("Project file not found: $pathInApp")
    }
}
