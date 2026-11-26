package io.legado.app.ui.main.shell

/**
 * B2 壳内返回决策（纯函数，普通 JVM 单测可直接断言，不触 Android 框架）。
 *
 * 〔规格口径〕R4：二级返回——非书架槽先回书架；书架槽执行双击退出。
 */
enum class ShellBackDecision {

    /** 非书架槽：返回书架槽，不起 Activity、不压栈。 */
    NAVIGATE_BOOKSHELF,

    /** 书架槽：退出壳（Activity 侧做双击间隔判定）。 */
    EXIT,
}

object ShellNavigation {

    /**
     * 当前槽位对应的返回动作。
     *
     * @param current 当前选中槽位
     * @return [ShellBackDecision.NAVIGATE_BOOKSHELF]（非书架）或 [ShellBackDecision.EXIT]（书架）
     */
    fun backDecision(current: ShellTab): ShellBackDecision =
        if (current == ShellTab.BOOKSHELF) ShellBackDecision.EXIT
        else ShellBackDecision.NAVIGATE_BOOKSHELF
}
