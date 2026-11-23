package io.legado.app.ci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 独立的 release 渠道工作流已移除，release APK 由 build.yml 统一产出；
 * versionCode 仍按基线提交以来的提交条数递增，因此 CI 必须保留全量历史。
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

    @Test
    fun `release checkout fetches full history for version code`() {
        val checkoutStep = buildWorkflowText
            .substringAfter("- name: Checkout")
            .substringBefore("- name:")

        assertTrue(checkoutStep.contains("actions/checkout@"))
        assertTrue(checkoutStep.contains("fetch-depth: 0"))
        assertTrue(buildGradleText.contains("'rev-list'"))
        assertTrue(buildGradleText.contains("versionCodeAtBase"))
    }

    @Test
    fun `release matrix only builds existing product flavors`() {
        assertTrue(buildGradleText.contains("productFlavors {"))
        assertTrue(buildGradleText.contains("app {"))
        assertFalse(buildGradleText.contains("google {"))
    }
}
