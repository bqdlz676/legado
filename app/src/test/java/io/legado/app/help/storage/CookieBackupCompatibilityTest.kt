package io.legado.app.help.storage

import io.legado.app.data.entities.Cookie
import io.legado.app.help.http.CookieStore
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class CookieBackupCompatibilityTest {

    @Test
    fun persistentCookiesRoundTripAsJson() {
        val cookies = listOf(
            Cookie("example.com", "sid=one"),
            Cookie("another.example", "token=two"),
        )

        assertEquals(
            cookies,
            GSON.fromJsonArray<Cookie>(GSON.toJson(cookies)).getOrThrow(),
        )
    }

    @Test
    fun restoredCookieValuesOverrideByNameAndPreserveOthers() {
        val merged = CookieStore.mergeCookieValues(
            "sid=old; keep=yes",
            "sid=new; added=one",
        )

        assertEquals(
            mapOf("sid" to "new", "keep" to "yes", "added" to "one"),
            CookieStore.cookieToMap(merged),
        )
    }

    @Test
    fun cookieBackupEncryptionUsesProvidedPassword() {
        val aes = BackupAES("backup-password")
        val json = """[{"url":"example.com","cookie":"sid=one"}]"""

        assertEquals(json, aes.decryptStr(aes.encryptBase64(json)))
    }

    @Test
    fun cookieBackupParserRejectsNullFieldsBeforeRestore() {
        assertEquals(
            listOf(Cookie("example.com", "sid=one")),
            parseCookieBackup("""[{"url":"example.com","cookie":"sid=one"}]"""),
        )
        assertThrows(IllegalArgumentException::class.java) {
            parseCookieBackup("""[{"url":null,"cookie":"sid=one"}]""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseCookieBackup("""[{"url":"example.com","cookie":null}]""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseCookieBackup("""[{"url":1,"cookie":"sid=one"}]""")
        }
    }

    @Test
    fun cookieBackupIsEncryptedAndRestoresThroughCookieStore() {
        // 旧 BackupConfigFragment 已随设置页 Compose 重写拆除（commit 93e1946）；
        // 备份 / WebDAV 恢复编排落在 ConfigViewModel，清单回吐落在 BackupSection。
        val backup = projectFile("src/main/java/io/legado/app/help/storage/Backup.kt").readText()
        val restore = projectFile("src/main/java/io/legado/app/help/storage/Restore.kt").readText()
        val cookieStore = projectFile(
            "src/main/java/io/legado/app/help/http/CookieStore.kt"
        ).readText()
        val configViewModel = projectFile(
            "src/main/java/io/legado/app/ui/config/ConfigViewModel.kt"
        ).readText()
        val backupSection = projectFile(
            "src/main/java/io/legado/app/ui/config/sections/BackupSection.kt"
        ).readText()
        val mainViewModel = projectFile(
            "src/main/java/io/legado/app/ui/main/MainViewModel.kt"
        ).readText()
        val config = projectFile(
            "src/main/java/io/legado/app/help/storage/BackupConfig.kt"
        ).readText()
        assertTrue(backup.contains("private suspend fun backup("))
        assertTrue(restore.contains("private suspend fun restore("))
        // VM 入口：本地 / WebDAV 备份与 WebDAV 恢复仍走同一帮助类。
        assertTrue(configViewModel.contains("suspend fun backup(backupPath: String?, uploadWebDav: Boolean)"))
        assertTrue(configViewModel.contains("Backup.backupLocked(appCtx, backupPath, uploadWebDav)"))
        assertTrue(configViewModel.contains("suspend fun restoreWebDav(name: String)"))
        assertTrue(configViewModel.contains("AppWebDav.restoreWebDav(name)"))
        // Compose 分区：备份双路按钮与恢复清单选择回写 VM。
        assertTrue(backupSection.contains("vm.backup(AppConfig.backupPath, false)"))
        assertTrue(backupSection.contains("vm.backup(AppConfig.backupPath, true)"))
        assertTrue(backupSection.contains("vm.restoreWebDav(name)"))
        val backupFunction = backup.substringAfter("private suspend fun backup(")
            .substringBefore("private suspend fun writeListToJson")
        val restoreFunction = restore.substringAfter("private suspend fun restore(")
            .substringBefore("private inline fun <reified T> fileToListT")
        val autoRestore = mainViewModel.substringAfter("fun restoreWebDav(")
            .substringBefore("private fun deleteNotShelfBook")
        val contentIsEnabled = config.substringAfter("internal fun contentIsEnabled(")
            .substringBefore("fun saveIgnoreConfig")

        assertTrue(backupFunction.contains("aes.encryptBase64(GSON.toJson(appDb.cookieDao.all))"))
        assertTrue(backupFunction.contains("BackupConfig.cookieContentKey in enabledContentKeys"))
        assertTrue(backupFunction.contains("val password = LocalConfig.password"))
        assertTrue(backupFunction.contains("password.isNullOrBlank()"))
        assertTrue(backupFunction.contains("val aes = BackupAES(password)"))
        assertTrue(
            backupFunction.indexOf("password.isNullOrBlank()") <
                backupFunction.indexOf("LocalConfig.lastBackup = System.currentTimeMillis()")
        )
        assertTrue(restoreFunction.contains("!BackupConfig.ignoreCookies"))
        assertTrue(restoreFunction.contains("val password = LocalConfig.password"))
        assertTrue(restoreFunction.contains("password.isNullOrBlank()"))
        assertTrue(restoreFunction.contains("val aes = BackupAES(password)"))
        assertTrue(restoreFunction.contains("aes.decryptStr(file.readText())"))
        assertTrue(
            restoreFunction.indexOf("val restoredCookies") <
                restoreFunction.indexOf("fileToListT<Book>")
        )
        assertTrue(restoreFunction.contains("CookieStore.restoreCookie(cookie.url, cookie.cookie)"))
        assertTrue(cookieStore.contains("internal fun restoreCookie"))
        assertTrue(cookieStore.contains("private fun saveCookie"))
        val saveCookie = cookieStore.substringAfter("private fun saveCookie").substringBefore(
            "fun setWebCookie"
        )
        assertTrue(saveCookie.contains("appDb.cookieDao.insert"))
        assertTrue(saveCookie.contains("CacheManager.putMemory"))
        assertTrue(
            saveCookie.indexOf("appDb.cookieDao.insert") <
                saveCookie.indexOf("CacheManager.putMemory")
        )
        // 恢复清单选择项回写 VM（旧 fragment 的 cancelButton / context.selector 对话框口径已拆除）。
        assertTrue(backupSection.contains("restoreNames = names"))
        assertTrue(autoRestore.contains("}.onSuccess {"))
        assertTrue(
            autoRestore.contains(
                "LocalConfig.lastBackup = maxOf(LocalConfig.lastBackup, restoredLastBackup)"
            )
        )
        assertTrue(autoRestore.contains("executeLazy {"))
        assertTrue(autoRestore.contains("}.onError {"))
        assertTrue(autoRestore.contains("}.start()"))
        assertTrue(contentIsEnabled.contains("if (key == cookieContentKey)"))
        assertTrue(contentIsEnabled.contains("ignoreConfig[key] == false"))
    }

    private fun projectFile(pathInApp: String): File =
        sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .first(File::isFile)
}
