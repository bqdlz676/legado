package io.legado.app.ci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 独立的 web 资源构建/提交工作流已随 CI 瘦身移除；
 * 资源提交脚本 push-web-assets.sh 保留，仍在单独推送前先 rebase 到目标分支。
 */
class WebWorkflowTest {

    private val repositoryRoot by lazy {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        generateSequence(File(userDir)) { it.parentFile }
            .first { File(it, ".github/workflows/build.yml").isFile }
    }

    private val webAssetScriptText by lazy {
        File(repositoryRoot, ".github/scripts/push-web-assets.sh")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `legacy web asset workflow is not retained`() {
        assertFalse(File(repositoryRoot, ".github/workflows/web.yml").exists())
    }

    @Test
    fun `retained web asset script rebases before a separate push`() {
        assertTrue(webAssetScriptText.contains("git fetch"))
        assertTrue(webAssetScriptText.contains("git rebase FETCH_HEAD"))
        assertTrue(webAssetScriptText.contains("git push"))
        assertTrue(webAssetScriptText.contains("max_attempts"))
    }
}
