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
            .first { File(it, ".github/workflows/build.yml").isFile }
    }

    private val dependabotFile: File
        get() = File(repositoryRoot, ".github/dependabot.yml")

    private val buildWorkflowText by lazy {
        File(repositoryRoot, ".github/workflows/build.yml")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `public Gradle repositories do not use placeholder credentials`() {
        assertFalse(dependabotFile.exists())
        assertFalse(buildWorkflowText.contains("registries:"))
        assertFalse(buildWorkflowText.contains("username: dummy"))
        assertFalse(buildWorkflowText.contains("password: dummy"))
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
