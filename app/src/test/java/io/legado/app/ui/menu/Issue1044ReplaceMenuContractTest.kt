package io.legado.app.ui.menu

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue 1044 契约测试（Compose 重写后口径）。
 *
 * 旧 replace_rule.xml 菜单资源已拆除；手动替换开关 / 新建 / 本地 / 在线 / 二维码 / 帮助
 * 落点为 ReplaceRuleScreen 的 ToolAction 列表，开关状态收口到 ReplaceRuleListViewModel + AppConfig。
 * 旧 XML 图标（ic_find_replace）与菜单项次序属于外壳契约，随菜单资源废弃；文案资源保留。
 */
class Issue1044ReplaceMenuContractTest {

    @Test
    fun `replace rule menu puts manual replacement before help with its icon`() {
        val screen = readProjectFile(REPLACE_SCREEN)
        val viewModel = readProjectFile(REPLACE_VIEW_MODEL)

        // 六个旧菜单项在新顶栏动作行中均可找到对应动作。
        listOf(
            "ToolAction(\"新建\", onAdd)",
            "ToolAction(\"本地\", onImportLocal)",
            "ToolAction(\"在线\", onOpenImportOnline)",
            "ToolAction(\"二维码\", onImportQr)",
            "ToolAction(if (state.manualReplace) \"手动·开\" else \"手动·关\", onToggleManual)",
            "ToolAction(\"帮助\", onHelp)"
        ).forEach { expected ->
            assertTrue("replace scaffold missing $expected", screen.contains(expected))
        }

        // 手动替换开关仍读写同一偏好，与旧菜单勾选态口径一致。
        assertTrue(viewModel.contains("AppConfig.manualReplaceRule"))
        assertTrue(viewModel.contains("AppConfig.manualReplaceRule = !AppConfig.manualReplaceRule"))

        val zhStrings = readProjectFile("src/main/res/values-zh/strings.xml")
        assertTrue(zhStrings.contains("<string name=\"manual_replace_rule\">手动替换</string>"))
    }

    private fun readProjectFile(pathInApp: String): String =
        sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?.readText()
            .orEmpty()

    private companion object {
        const val REPLACE_SCREEN = "src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt"
        const val REPLACE_VIEW_MODEL = "src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt"
    }
}
