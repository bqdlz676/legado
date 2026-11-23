package io.legado.app.ui.replace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 替换规则分组 / 批量排序 / 批量分组契约（Compose 重写后）。
 * 旧 ReplaceRuleViewModel.kt 已拆除，主控归 ReplaceRuleListViewModel.kt；
 * 分组改名仍走 StringExtensions.renameGroupExact 精确匹配，不回退 splitNotBlank 模糊拆组。
 */
class ReplaceRuleViewModelGroupContractTest {

    private val source by lazy {
        projectFile("src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt")
            .readText()
            .replace("\r\n", "\n")
            .replace(Regex("\\s+"), " ")
    }

    @Test
    fun `group deletion delegates to exact group update`() {
        // 删除分组委托给同一套精确改名流程，目标分组名传 null。
        assertTrue(source.contains("fun deleteGroup(group: String) = renameGroup(group, null)"))
        assertTrue(source.contains("source.group.renameGroupExact(oldGroup, newGroup)"))
        assertFalse(source.contains("source.group?.splitNotBlank"))
    }

    @Test
    fun `batch moves allocate orders outside existing bounds`() {
        val top = source.substringAfter("fun topSelection")
            .substringBefore("fun bottomSelection")
        val bottom = source.substringAfter("fun bottomSelection")
            .substringBefore("fun selectionJson")

        assertTrue(top.contains("it.order = minOrder++"))
        assertFalse(top.contains("it.order = ++minOrder"))
        assertTrue(bottom.contains("var maxOrder = appDb.replaceRuleDao.maxOrder + 1"))
        assertTrue(bottom.contains("it.order = maxOrder++"))
    }

    @Test
    fun `selection group actions copy rules and update only group members`() {
        assertTrue(
            source.contains(
                "fun selectionAddToGroups(group: String)"
            )
        )
        assertTrue(
            source.contains(
                "fun selectionRemoveFromGroups(group: String)"
            )
        )
        assertTrue(source.contains("it.copy().addGroup(group)"))
        assertTrue(source.contains("it.copy().removeGroup(group)"))
        assertFalse(source.contains("scope = groups"))
        assertFalse(source.contains("excludeScope = groups"))
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }
}
