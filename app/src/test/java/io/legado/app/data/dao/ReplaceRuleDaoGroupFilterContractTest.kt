package io.legado.app.data.dao

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 替换规则分组过滤契约（Compose 重写后）。
 * SQL 完整规范化成员匹配仍在 ReplaceRuleDao 的 REPLACE_RULE_GROUP_FILTER 常量内；
 * `group:` 前缀解析与 flowGroupSearch 接线从旧 ReplaceRuleActivity 迁至 ReplaceRuleListViewModel，
 * 分组 Chip 回填查询归 ReplaceRuleScreen。
 */
class ReplaceRuleDaoGroupFilterContractTest {

    private val daoSource by lazy {
        normalizedSource("src/main/java/io/legado/app/data/dao/ReplaceRuleDao.kt")
    }

    private val viewModelSource by lazy {
        normalizedSource("src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt")
    }

    private val screenSource by lazy {
        normalizedSource("src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt")
    }

    private val groupQuery by lazy {
        queryBefore("fun flowGroupSearch(groupName: String)")
    }

    private val groupFilter by lazy {
        val start = daoSource.indexOf("private const val REPLACE_RULE_GROUP_FILTER")
        val end = daoSource.indexOf("@Dao", start)
        require(start >= 0 && end > start)
        daoSource.substring(start, end)
    }

    @Test
    fun `replace rule group filter matches complete normalized members`() {
        assertTrue(groupQuery.contains("replace_rules AS t2"))
        assertTrue(groupQuery.contains("+ REPLACE_RULE_GROUP_FILTER +"))
        assertFalse(groupQuery.contains(" like ", ignoreCase = true))

        assertTrue(groupFilter.contains("trim(:groupName, \$GROUP_TRIM_CHARACTERS) <> ''"))
        assertTrue(
            groupFilter.contains("with recursive replace_rule_groups(group_name, rest) as (")
        )
        assertTrue(
            groupFilter.contains(
                "replace(replace(replace(coalesce(t2.`group`, ''), ';', ','), " +
                    "'\uFF0C', ','), '\uFF1B', ',') || ','"
            )
        )
        assertTrue(
            groupFilter.contains(
                "trim(substr(rest, 1, instr(rest, ',') - 1), \$GROUP_TRIM_CHARACTERS)"
            )
        )
        assertTrue(groupFilter.contains("where rest <> ''"))
        assertTrue(
            groupFilter.contains(
                "group_name = trim(:groupName, \$GROUP_TRIM_CHARACTERS)"
            )
        )
        assertFalse(groupFilter.contains(" like ", ignoreCase = true))

        // 前缀解析 / DAO 调用归 ViewModel；Chip 点击构造 `group:` 查询串归 Screen。
        assertTrue(viewModelSource.contains("query.substringAfter(\"group:\")"))
        assertTrue(viewModelSource.contains("flowGroupSearch("))
        assertFalse(viewModelSource.contains("flowGroupSearch(\"%"))
        assertTrue(screenSource.contains("ReplaceFilter.Group(group)"))
        assertTrue(viewModelSource.contains("\"group:\${filter.name}\""))
    }

    private fun queryBefore(functionMarker: String): String {
        val end = daoSource.indexOf(functionMarker)
        val start = daoSource.lastIndexOf("@Query(", end)
        require(start >= 0 && end > start)
        return daoSource.substring(start, end)
    }

    private fun normalizedSource(pathInApp: String): String {
        return projectFile(pathInApp)
            .readText()
            .replace("\r\n", "\n")
            .replace(Regex("\\s+"), " ")
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }
}
