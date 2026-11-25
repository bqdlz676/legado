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

    @Test
    fun `legacy standalone beta release workflow is not retained`() {
        assertFalse(File(repositoryRoot, ".github/workflows/BetaRelease.yml").exists())
    }

    @Test
    fun `retained build workflow exposes manual dispatch alongside branch triggers`() {
        assertTrue(ciWorkflowText.contains("workflow_dispatch:"))
        assertTrue(ciWorkflowText.contains("push:"))
        assertTrue(ciWorkflowText.contains("pull_request:"))
        assertFalse(ciWorkflowText.contains("startsWith(github.event.head_commit.message"))
    }
}
