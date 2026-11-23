package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 管理类弹窗迁移契约测试。
 *
 * 共享纵向弹窗底座（PopupAction / PopupActionMenu）与 toolbar 溢出桥保留；部分旧 Adapter / 旧菜单 XML
 * 随 Compose 重写拆除，断言指向新落点：SourceLoginDialog（残留 toolbar 桥）、ReadRecordDialog、
 * 三个存活 Adapter、AutoTaskScreen / ReplaceRuleScreen（旧 AutoTaskAdapter / ReplaceRuleAdapter 落点）。
 */
class ManagePopupActionMigrationTest {

    @Test
    fun `shared popup adds vertical danger styling without losing existing behavior`() {
        val popup = readProjectFile("src/main/java/io/legado/app/ui/widget/PopupAction.kt")
        val builder = readProjectFile("src/main/java/io/legado/app/ui/widget/PopupActionMenu.kt")
        val row = readProjectFile("src/main/res/layout/item_popup_action.xml")

        listOf(
            "applyMd3PopupStyle()",
            "resolveDropDownYOffset(",
            "LinearLayoutManager(context)",
            "private var actionItems: List<PopupActionItem> = emptyList()",
            "items.any { it.icon != null }",
            "items.any { it.checkable }",
            "textView.measuredWidth",
            "textView.minHeight = 48.dpToPx()",
            "textView.setPadding(5.dpToPx(), 5.dpToPx(), 5.dpToPx(), 5.dpToPx())",
            "val enabled = isItemEnabled(item)",
            "item.enabled && item.value !in disabledValues",
            "context.secondaryDisabledTextColor",
            "info.isCheckable = item.checkable",
            "AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE",
            "AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE",
            "imageView.visibility = View.INVISIBLE",
            "takeIf(::isItemEnabled)"
        ).forEach { expected -> assertContains("PopupAction.kt", popup, expected) }
        listOf(
            "@+id/iv_icon",
            "@+id/text_view",
            "@+id/iv_check_end",
            "android:duplicateParentState=\"true\""
        ).forEach { expected -> assertContains("item_popup_action.xml", row, expected) }
        assertFalse(row.contains("app:tint="))

        listOf(
            "setVertical(true)",
            "setDangerValues(dangerValues)",
            "dismiss()",
            "showAsDropDown(anchor, 0, 4.dpToPx())"
        ).forEach { expected -> assertContains("PopupActionMenu.kt", builder, expected) }
    }

    @Test
    fun `toolbar overflow uses exact items and keeps native fallbacks`() {
        val bridge = readProjectFile(
            "src/main/java/io/legado/app/utils/ToolbarOverflowMenuExtensions.kt"
        )

        listOf(
            "getTag(R.id.toolbar_overflow_menu_state) as? OverflowMenuState",
            "setTag(R.id.toolbar_overflow_menu_state, newState)",
            "addOnLayoutChangeListener",
            "onPrepareMenu(menu)",
            "onOpenCustomMenu(menu)",
            "actionItems.hasUnsupportedItems()",
            "item.subMenu != null || item.actionView != null",
            "showOverflowMenu()",
            "actionItems.mapIndexed { index, item ->",
            "value = index.toString()",
            "icon = if (state.showIcons)",
            "enabled = item.isEnabled",
            "checkable = item.isCheckable",
            "checked = item.isChecked",
            "action.toIntOrNull()",
            "actionItems::getOrNull",
            "performItemAction(menuItem, 0)",
            "params.isOverflowButton",
            "abc_action_menu_overflow_description"
        ).forEach { expected ->
            assertContains("ToolbarOverflowMenuExtensions.kt", bridge, expected)
        }
        assertFalse(bridge.contains("WeakHashMap"))
        assertFalse(bridge.contains("setOnHierarchyChangeListener"))
        assertFalse(bridge.contains("performIdentifierAction"))
    }

    @Test
    fun `base activity and fragments install the toolbar overflow bridge`() {
        val activity = readProjectFile("src/main/java/io/legado/app/base/BaseActivity.kt")
        val fragment = readProjectFile("src/main/java/io/legado/app/base/BaseFragment.kt")

        listOf(
            "if (view is Toolbar) view.installMd3OverflowMenu()",
            "?: (findViewById<View>(R.id.titleBar) as? TitleBar)",
            "?.installActivityOverflowMenu()",
            "showIcons = showOpenMenuIcon",
            "onPrepareOptionsMenu(toolbarMenu)",
            "onMenuOpened(Window.FEATURE_OPTIONS_PANEL, toolbarMenu)"
        ).forEach { expected -> assertContains("BaseActivity.kt", activity, expected) }
        assertFalse(activity.contains("if (view is Toolbar) view.installActivityOverflowMenu()"))
        assertContains("BaseFragment.kt", fragment, "it.installMd3OverflowMenu()")
    }

    @Test
    fun `dialog overflow menus use the shared vertical bridge`() {
        // SourceLoginDialog 仍是 toolbar 外壳，保留共享溢出桥接线。
        val loginDialog = readProjectFile(SOURCE_LOGIN_DIALOG)
        assertContains(SOURCE_LOGIN_DIALOG, loginDialog, "installMd3OverflowMenu(")
        assertContains(SOURCE_LOGIN_DIALOG, loginDialog, "showIcons = true")
        assertContains(
            SOURCE_LOGIN_DIALOG,
            loginDialog,
            "onOpenCustomMenu = { it.applyOpenTint(requireContext()) }"
        )

        // AppLog / CrashLogs 弹窗已改为 ComposeView 宿主，清空 / 导出是 InkButton，不再有 toolbar 溢出桥。
        listOf(APP_LOG_DIALOG, CRASH_LOGS_DIALOG).forEach { path ->
            val source = readProjectFile(path)
            assertTrue("$path must host a ComposeView", source.contains("ComposeView"))
            assertFalse("$path must not install the toolbar overflow bridge", source.contains("installMd3OverflowMenu"))
        }
    }

    @Test
    fun `dialog menu actions provide icons for the vertical menu`() {
        val menus = mapOf(
            "rss_read_record.xml" to listOf("menu_clear"),
            "source_login.xml" to listOf(
                "menu_ok",
                "menu_show_login_header",
                "menu_del_login_header",
                "menu_clear_login_info",
                "menu_log"
            )
        )
        menus.forEach { (file, ids) ->
            val source = readProjectFile("src/main/res/menu/$file")
            ids.forEach { id ->
                val item = source.substringAfter("android:id=\"@+id/$id\"")
                    .substringBefore("/>")
                assertTrue(
                    "$file/$id should declare an icon",
                    item.contains("android:icon=\"@drawable/")
                )
            }
        }
    }

    @Test
    fun `dialog action icons use the shared toolbar tint`() {
        mapOf(
            READ_RECORD_DIALOG to "toolBar.menu.applyTint(requireContext())",
            SOURCE_LOGIN_DIALOG to "binding.toolBar.menu.applyTint(requireContext())"
        ).forEach { (path, expected) ->
            assertContains(path, readProjectFile(path), expected)
        }
    }

    @Test
    fun `five management adapters use the shared vertical menu`() {
        adapterFiles.forEach { path ->
            val source = readProjectFile(path)
            assertFalse("$path should not import PopupMenu", source.contains("import android.widget.PopupMenu"))
            assertFalse("$path should not import AppCompat PopupMenu", source.contains("import androidx.appcompat.widget.PopupMenu"))
            assertContains(path, source, "popupActionMenu(context)")
            assertContains(path, source, "danger(\"delete\")")
        }
    }

    @Test
    fun `management menu actions keep their current callbacks and side effects`() {
        assertActions(
            RSS_SOURCE,
            "\"top\" -> callBack.toTop(source)",
            "\"bottom\" -> callBack.toBottom(source)",
            "callBack.del(source)",
            "selected.remove(source)"
        )
        assertActions(RULE_SUB, "callBack.delSubscription(source)")
        assertActions(
            TXT_TOC_RULE,
            "\"top\" -> callBack.toTop(source)",
            "\"bottom\" -> callBack.toBottom(source)",
            "callBack.del(source)",
            "selected.remove(source)"
        )

        // 旧 AutoTaskAdapter 拆除：日志 / 上下移 / 删除落点在 AutoTaskScreen + AutoTaskListViewModel。
        val autoScreen = readProjectFile(AUTO_TASK_SCREEN)
        assertContains(AUTO_TASK_SCREEN, autoScreen, "onShowLog(rule)")
        assertContains(AUTO_TASK_SCREEN, autoScreen, "onMove(rule, false)")
        assertContains(AUTO_TASK_SCREEN, autoScreen, "onMove(rule, true)")
        assertContains(AUTO_TASK_SCREEN, autoScreen, "onRequestDelete(rule)")
        val autoViewModel = readProjectFile(AUTO_TASK_VIEW_MODEL)
        assertContains(AUTO_TASK_VIEW_MODEL, autoViewModel, "fun showLog(rule: AutoTaskUi)")
        assertContains(AUTO_TASK_VIEW_MODEL, autoViewModel, "fun move(rule: AutoTaskUi, down: Boolean)")
        assertContains(AUTO_TASK_VIEW_MODEL, autoViewModel, "AutoTask.delete(ids, context)")

        // 旧 ReplaceRuleAdapter 拆除：置顶 / 置底 / 删除落点在 ReplaceRuleScreen + ReplaceRuleListViewModel。
        val replaceScreen = readProjectFile(REPLACE_SCREEN)
        assertContains(REPLACE_SCREEN, replaceScreen, "onTop(rule)")
        assertContains(REPLACE_SCREEN, replaceScreen, "onBottom(rule)")
        assertContains(REPLACE_SCREEN, replaceScreen, "onRequestDelete(rule)")
        val replaceViewModel = readProjectFile(REPLACE_VIEW_MODEL)
        assertContains(REPLACE_VIEW_MODEL, replaceViewModel, "fun toTop(rule: ReplaceRuleUi)")
        assertContains(REPLACE_VIEW_MODEL, replaceViewModel, "fun toBottom(rule: ReplaceRuleUi)")
    }

    @Test
    fun `management menu labels keep their previous order`() {
        listOf(RSS_SOURCE, TXT_TOC_RULE).forEach { path ->
            assertOrdered(
                path,
                "item(context.getString(R.string.to_top), \"top\")",
                "item(context.getString(R.string.to_bottom), \"bottom\")",
                "item(context.getString(R.string.delete), \"delete\")"
            )
        }
        assertOrdered(
            RULE_SUB,
            "item(context.getString(R.string.delete), \"delete\")"
        )

        // 新 Compose 行内动作：日志 / 上移 / 下移 / 删除各自仍在（顺序按新行内布局重排，不再沿用旧 XML 次序）。
        val autoScreen = readProjectFile(AUTO_TASK_SCREEN)
        listOf(
            "MiniAction(\"日志\", { onShowLog(rule) })",
            "MiniAction(\"上移\", { onMove(rule, false) })",
            "MiniAction(\"下移\", { onMove(rule, true) })",
            "MiniAction(\"删除\", { onRequestDelete(rule) }, danger = true)"
        ).forEach { snippet -> assertTrue("auto task screen missing $snippet", autoScreen.contains(snippet)) }

        val replaceScreen = readProjectFile(REPLACE_SCREEN)
        listOf(
            "MiniAction(\"置顶\", { onTop(rule) })",
            "MiniAction(\"置底\", { onBottom(rule) })",
            "MiniAction(\"删除\", { onRequestDelete(rule) }, danger = true)"
        ).forEach { snippet -> assertTrue("replace screen missing $snippet", replaceScreen.contains(snippet)) }
    }

    private fun assertActions(path: String, vararg expected: String) {
        val source = readProjectFile(path)
        expected.forEach { assertContains(path, source, it) }
    }

    private fun assertContains(path: String, source: String, expected: String) {
        assertTrue("$path should contain $expected", source.contains(expected))
    }

    private fun assertOrdered(path: String, vararg expected: String) {
        val source = readProjectFile(path)
        var previous = -1
        expected.forEach { snippet ->
            val current = source.indexOf(snippet, previous + 1)
            assertTrue("$path should contain $snippet after the previous item", current > previous)
            previous = current
        }
    }

    private fun readProjectFile(pathInApp: String): String =
        sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?.readText()
            .orEmpty()

    private companion object {
        const val RSS_SOURCE = "src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt"
        const val RULE_SUB = "src/main/java/io/legado/app/ui/rss/subscription/RuleSubAdapter.kt"
        const val TXT_TOC_RULE = "src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt"
        const val APP_LOG_DIALOG = "src/main/java/io/legado/app/ui/about/AppLogDialog.kt"
        const val CRASH_LOGS_DIALOG = "src/main/java/io/legado/app/ui/about/CrashLogsDialog.kt"
        const val SOURCE_LOGIN_DIALOG = "src/main/java/io/legado/app/ui/login/SourceLoginDialog.kt"
        const val READ_RECORD_DIALOG = "src/main/java/io/legado/app/ui/rss/article/ReadRecordDialog.kt"
        const val AUTO_TASK_SCREEN = "src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt"
        const val AUTO_TASK_VIEW_MODEL = "src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt"
        const val REPLACE_SCREEN = "src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt"
        const val REPLACE_VIEW_MODEL = "src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt"
        val adapterFiles = listOf(RSS_SOURCE, RULE_SUB, TXT_TOC_RULE)
    }
}
