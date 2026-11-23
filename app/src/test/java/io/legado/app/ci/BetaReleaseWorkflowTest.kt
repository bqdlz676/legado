package io.legado.app.ci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CI 瘦身之后仓库只保留 build.yml 一条编译工作流，独立的 BetaRelease 渠道工作流已移除。
 * 这里校验该删除现状，并确认保留下来的 build.yml 仍保留手动派发入口。
 */
class BetaReleaseWorkflowTest {

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

    @Test
    fun `legacy standalone beta release workflow is not retained`() {
        assertFalse(File(repositoryRoot, ".github/workflows/BetaRelease.yml").exists())
    }

    @Test
    fun `retained build workflow exposes manual dispatch alongside branch triggers`() {
        assertTrue(buildWorkflowText.contains("workflow_dispatch:"))
        assertTrue(buildWorkflowText.contains("push:"))
        assertTrue(buildWorkflowText.contains("pull_request:"))
        assertFalse(buildWorkflowText.contains("startsWith(github.event.head_commit.message"))
    }
}
