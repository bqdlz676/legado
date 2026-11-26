package io.legado.app.ui.main.shell

/**
 * B2 壳态恢复口径（纯函数，普通 JVM 单测可直接断言，不触 Compose 运行时与 Android 框架）。
 *
 * 〔顺序锁——O1〕进程重建 / 配置变更后，`rememberSaveable` 的保存态优先：
 *  - 已有保存态 → 宿主偏好默认槽不得覆盖；
 *  - 无保存态 → 冷启动默认槽由 [rememberMainShellState] 的 `initialTab` 兜底。
 *
 * 宿主只在活进程内被显式请求（newIntent 检索改道等）时才允许切槽；首 composition
 * 请求代次为 0，同步回路不得覆盖恢复态。
 */
object ShellRestorePolicy {

    /**
     * 是否允许宿主请求覆盖壳态。
     *
     * @param requestGeneration 宿主显式请求代次：0 = 首 composition（冷启动 / 进程重建），
     *        不覆盖 saveable 恢复态；>0 = 活进程内 newIntent 等显式下发，允许同步进壳态
     * @return true 才执行宿主请求切槽；false 时保留当前（恢复态或默认槽）
     */
    fun shouldApplyHostRequest(requestGeneration: Int): Boolean = requestGeneration > 0

    /**
     * 槽位合法性兜底（C3）：null / 非法映射一律回落书架，不抛异常、不留错槽。
     *
     * @param tab 待校验槽位（可空）；恢复态 / 偏好映射产出非法值时走本回落
     */
    fun sanitizeTab(tab: ShellTab?): ShellTab = tab ?: ShellTab.BOOKSHELF
}
