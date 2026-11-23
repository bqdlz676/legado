package io.legado.app.ui.about

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 更新弹窗下载契约（Compose 重写后）：测试版顶部按钮走系统下载流；浏览器兜底按钮独立于下载闭包。
 * 旧 app_update.xml 菜单与 binding.toolBar 已随壳拆除，断言落到 Compose 闭包 / 按钮。
 */
class UpdateDialogDownloadContractTest {

    @Test
    fun `beta update uses the system download flow`() {
        val source = File(
            "src/main/java/io/legado/app/ui/about/UpdateDialog.kt"
        ).readText()
        val downloadClosure = source.substringAfter("onDownload = {")
            .substringBefore("onDownloadBackup")

        assertTrue(downloadClosure.contains("startDownload(args?.getString(\"url\")"))
        assertFalse(downloadClosure.contains("openUrl"))
    }

    @Test
    fun `beta update exposes browser fallback in toolbar`() {
        val source = File("src/main/java/io/legado/app/ui/about/UpdateDialog.kt").readText()
        val browserClosure = source.substringAfter("onOpenBrowser = {")
            .substringBefore("onIgnore")

        // 浏览器兜底独立成闭包：优先备份 URL，回落主 URL；旧工具栏菜单口径已拆除。
        assertTrue(browserClosure.contains("args?.getString(\"backupUrl\").orEmpty()"))
        assertTrue(browserClosure.contains("args?.getString(\"url\").orEmpty()"))
        assertTrue(source.contains("R.string.open_in_browser"))
        assertFalse(source.contains("inflateMenu(R.menu.app_update)"))
        assertFalse(source.contains("menu_open_in_browser"))
    }
}
