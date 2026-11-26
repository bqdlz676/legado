package io.legado.app.ui.main.shell

import io.legado.app.R

/**
 * B2 主壳三槽：书架 / 发现 / 设置（顺序即规格 P-11「书架 · 发现 · 设置」，不增第四槽）。
 *
 * 〔状态口径〕槽位定义只持有标签与双态图标资源；选中 / 按压可视态解析归契约枚举
 * [io.legado.app.ui.components.contract.ComponentVisualState]，本枚举不另造档位。
 *
 * @property labelRes 槽位标签字符串资源
 * @property iconOffRes 未选中图标（暗铜线描边）
 * @property iconOnRes 选中图标（暖金线描边）
 */
enum class ShellTab(
    val labelRes: Int,
    val iconOffRes: Int,
    val iconOnRes: Int,
) {

    /** 书架槽：书目列表 / 阅读跳转，页面编排归 B3 书架批次。 */
    BOOKSHELF(R.string.bookshelf, R.drawable.ic_tab_bookshelf_e, R.drawable.ic_tab_bookshelf_s),

    /** 发现槽：书源 / RSS 入口，页面编排归 B5 发现批次。 */
    EXPLORE(R.string.discovery, R.drawable.ic_tab_discover_e, R.drawable.ic_tab_discover_s),

    /** 设置槽：偏好 / 关于 / 归档，页面编排归 B3 设置批次（编号漂移回写 N7）。 */
    SETTINGS(R.string.setting, R.drawable.ic_tab_settings_e, R.drawable.ic_tab_settings_s);

    companion object {

        /** 固定槽位顺序：书架 → 发现 → 设置（规格 P-11，菜单 / 图标 / 顺序三处一致）。 */
        val defaultOrder: List<ShellTab> = values().toList()

        /**
         * 默认主页偏好回槽位。
         *
         * 偏好取值沿旧壳口径：`explore` → 发现、`my` → 设置；其余（空值 / 未知名 /
         * 损坏偏好）一律回落书架，保证边界场景不落空白页或崩溃（C3）。
         */
        fun initialTab(homePage: String?): ShellTab = when (homePage?.trim()?.lowercase()) {
            "explore" -> EXPLORE
            "my" -> SETTINGS
            else -> BOOKSHELF
        }
    }
}
