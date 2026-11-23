package io.legado.app.ci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * versionCode 口径（现行权威，禁止再用 git 历史计数）：
 * 仓库根 `.versioncode` 是唯一源；硬地板 38000；`data_scripts/bump-versioncode.sh` 统一 +1 回写；
 * CI `Resolve versionCode` 步骤与 app/build.gradle 都读同一个文件，低于地板直接 fail。
 */
class ReleaseWorkflowTest {

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

    private val buildGradleText by lazy {
        File(repositoryRoot, "app/build.gradle")
            .readText()
            .replace("\r\n", "\n")
    }

    private val versionCodeFile by lazy {
        File(repositoryRoot, ".versioncode")
    }

    private val bumpScriptText by lazy {
        File(repositoryRoot, "data_scripts/bump-versioncode.sh")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `version code resolves from .versioncode with the 38000 hard floor`() {
        // CI 步骤：从 .versioncode 读数，低于硬地板 38000 直接失败。
        val resolveStep = buildWorkflowText
            .substringAfter("- name: Resolve versionCode")
            .substringBefore("- name:")
        assertTrue(resolveStep.contains(".versioncode"))
        assertTrue(resolveStep.contains("38000"))
        assertTrue(buildWorkflowText.contains("actions/checkout@"))

        // gradle：同一文件为唯一源，取值不低于地板。
        assertTrue(buildGradleText.contains("def versionCodeFloor = 38000"))
        assertTrue(buildGradleText.contains("rootProject.file('.versioncode')"))
        assertTrue(buildGradleText.contains("Math.max(versionCodeFloor, raw as Integer)"))
        assertFalse(buildGradleText.contains("rev-list"))
        assertFalse(buildGradleText.contains("versionCodeAtBase"))

        //  bumps 脚本：地板 38000，只 +1 回写 .versioncode。
        assertTrue(bumpScriptText.contains("floor=38000"))
        assertTrue(bumpScriptText.contains(".versioncode"))

        // 落盘值本身必须是不低于地板的整数。
        val raw = versionCodeFile.readText().trim()
        val value = raw.toIntOrNull()
        assertTrue(".versioncode 不是整数: '$raw'", value != null)
        assertTrue(".versioncode $value 低于硬地板 38000", value!! >= 38000)
    }

    @Test
    fun `release matrix only builds existing product flavors`() {
        assertTrue(buildGradleText.contains("productFlavors {"))
        assertTrue(buildGradleText.contains("app {"))
        assertFalse(buildGradleText.contains("google {"))
    }
}
