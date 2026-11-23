package io.legado.app.ci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 独立的 TestRelease 渠道工作流（beta 发布 + 蓝奏上传）已随 CI 瘦身移除，
 * 构建产物统一由 build.yml 以 debug/release APK 形式上传为 Actions 构件；
 * 蓝奏上传脚本 lzy_web.py 仍作为保留脚本，其失败透传行为由第三个用例守护。
 */
class TestReleaseWorkflowTest {

    private val repositoryRoot by lazy {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        generateSequence(File(userDir)) { it.parentFile }
            .first { File(it, ".github/workflows/build.yml").isFile }
    }

    private val buildWorkflowText by lazy {
        File(repositoryRoot, ".github/workflows/build.yml")
            .readText()
            .replace("\r\n", "\n")
    }

    private val lanzouUploaderText by lazy {
        val scriptFile = File(repositoryRoot, ".github/scripts/lzy_web.py")
        scriptFile.readText().replace("\r\n", "\n")
    }

    @Test
    fun `legacy test release workflow is not retained`() {
        assertFalse(File(repositoryRoot, ".github/workflows/TestRelease.yml").exists())
    }

    @Test
    fun `retained build workflow publishes only debug and release apk artifacts`() {
        assertTrue(buildWorkflowText.contains("legado-debug-apk"))
        assertTrue(buildWorkflowText.contains("legado-release-apk"))
        assertFalse(buildWorkflowText.contains("ncipollo/release-action"))
        assertFalse(buildWorkflowText.contains("lzy_web.py"))
        assertFalse(buildWorkflowText.contains("Telegram"))
    }

    @Test
    fun `lanzou uploader propagates login and upload failures`() {
        assertTrue(lanzouUploaderText.contains("with open(file_dir, \"rb\") as upload_stream:"))
        assertTrue(lanzouUploaderText.contains("return 0 if upload(argv[0], argv[1]) else 1"))
        assertTrue(lanzouUploaderText.contains("sys.exit(main(sys.argv[1:]))"))
        assertFalse(lanzouUploaderText.contains("\"name\": '{file_name}'"))
        assertFalse(lanzouUploaderText.contains("retry_tim+"))
        assertTrue(lanzouUploaderText.contains("mydisk.php?item=files&action=index&u="))
        assertTrue(lanzouUploaderText.contains("allow_redirects=False"))
        assertTrue(lanzouUploaderText.contains("accounts.woozooo.com"))
    }
}
