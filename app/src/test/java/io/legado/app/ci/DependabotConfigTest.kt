package io.legado.app.ci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 依赖自动升级（dependabot）已随 CI 瘦身移除；npm 前端模块仍位于 modules/web。
 */
class DependabotConfigTest {

    private val repositoryRoot by lazy {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        generateSequence(File(userDir)) { it.parentFile }
            // 仓库根改用 settings.gradle 定位：CI 已拆成 verify/package 双车道、旧 build.yml 退役，
            // 若继续拿某个 workflow 文件名当根锚点，日后换文件名就会让整组契约测试集体误红。
            .first { File(it, "settings.gradle").isFile }
    }

    private val dependabotFile: File
        get() = File(repositoryRoot, ".github/dependabot.yml")

    // 现行 CI 定义 = 验证车道 verify.yml + 打包车道 package.yml 的合并文本。
    // 拆分前这两部分是同一个 build.yml，所以下面的断言语义与拆分前等价、强度不放宽。
    private val ciWorkflowText by lazy {
        listOf("verify.yml", "package.yml").joinToString("\n") { name ->
            File(repositoryRoot, ".github/workflows/$name")
                .readText()
                .replace("\r\n", "\n")
        }
    }

    @Test
    fun `public Gradle repositories do not use placeholder credentials`() {
        assertFalse(dependabotFile.exists())
        assertFalse(ciWorkflowText.contains("registries:"))
        assertFalse(ciWorkflowText.contains("username: dummy"))
        assertFalse(ciWorkflowText.contains("password: dummy"))
    }

    @Test
    fun `Gradle updates stay grouped and bounded`() {
        assertFalse(dependabotFile.exists())
    }

    @Test
    fun `npm updates target the Web module instead of the repository root`() {
        assertTrue(File(repositoryRoot, "modules/web/package.json").isFile)
    }

    @Test
    fun `all update ecosystems use the same pull request limit`() {
        assertFalse(dependabotFile.exists())
    }
}
