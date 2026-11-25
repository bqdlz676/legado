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
            // 仓库根改用 settings.gradle 定位：CI 已拆成 verify/package 双车道、旧 build.yml 退役，
            // 若继续拿某个 workflow 文件名当根锚点，日后换文件名就会让整组契约测试集体误红。
            .first { File(it, "settings.gradle").isFile }
    }

    // 现行 CI 定义 = 验证车道 verify.yml + 打包车道 package.yml 的合并文本。
    // 拆分前这两部分是同一个 build.yml，所以下面的断言语义与拆分前等价、强度不放宽。
    private val ciWorkflowText by lazy {
        listOf("verify.yml", "package.yml").joinToString("\n") { name ->
            File(repositoryRoot, ".github/workflows/$name")
                .readText()
                .replace("\r\n", "\n")
        }
    }

    // 出包上传步骤在 b285dab 抽到 composite action（.github/actions/build-apks/action.yml），
    // 产物名断言的真值来源随之移到该文件；这里与 build.yml 合并后再判，断言本身不放宽。
    private val compositeActionText by lazy {
        File(repositoryRoot, ".github/actions/build-apks/action.yml")
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
        val publishedArtifacts = ciWorkflowText + compositeActionText
        assertTrue(publishedArtifacts.contains("legado-debug-apk"))
        assertTrue(publishedArtifacts.contains("legado-release-apk"))
        assertFalse(ciWorkflowText.contains("ncipollo/release-action"))
        assertFalse(ciWorkflowText.contains("lzy_web.py"))
        assertFalse(ciWorkflowText.contains("Telegram"))
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
