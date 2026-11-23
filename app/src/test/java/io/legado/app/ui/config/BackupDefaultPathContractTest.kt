package io.legado.app.ui.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupDefaultPathContractTest {

    @Test
    fun `default backup path is selectable and used directly`() {
        val section = source("app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt")
        val viewModel = source("app/src/main/java/io/legado/app/ui/config/ConfigViewModel.kt")
        val appConfig = source("app/src/main/java/io/legado/app/help/config/AppConfig.kt")
        val backup = source("app/src/main/java/io/legado/app/help/storage/Backup.kt")

        // 路径行仍可直接选目录；选中后回写 PreferKey.backupPath。
        assertTrue(section.contains("PreferKey.backupPath"))
        assertTrue(section.contains("actions.pickFolder"))

        // 空路径回落默认目录：AppConfig  setter 把 null / 空串清掉偏好，备份层接受可空路径。
        val setter = appConfig.substringAfter("var backupPath: String?")
            .substringBefore("var defaultBookTreeUri")
        assertTrue(setter.contains("value.isNullOrEmpty()"))
        assertTrue(setter.contains("appCtx.removePref(PreferKey.backupPath)"))

        // VM 把（可空）路径原样透传给锁定备份入口，不强制非空。
        assertTrue(viewModel.contains("suspend fun backup(backupPath: String?, uploadWebDav: Boolean)"))
        assertTrue(viewModel.contains("Backup.backupLocked(appCtx, backupPath, uploadWebDav)"))
        assertTrue(backup.contains("suspend fun backupLocked(context: Context, path: String?"))
    }

    private fun source(relativePath: String): String {
        return File(repositoryRoot(), relativePath).readText()
    }

    private fun repositoryRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDir)) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }
}
