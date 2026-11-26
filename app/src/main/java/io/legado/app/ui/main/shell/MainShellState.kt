package io.legado.app.ui.main.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * B2 壳态持有：当前槽位（配置变更后由 [rememberSaveable] 存活）、幂等切换。
 *
 * 〔口径〕壳内只存导航态：当前槽位 / 返回决策；书源、书籍、备份能力回数据层与业务域，
 * 不进本状态对象（任务书 §3.1）。
 */
class MainShellState internal constructor(
    private val holder: MutableState<ShellTab>,
) {

    /** 当前选中槽位。 */
    val selectedTab: ShellTab get() = holder.value

    /** 选中槽位；重复点当前槽位不触发切换（幂等，不起 Activity、不压栈）。 */
    fun select(tab: ShellTab) {
        if (holder.value != tab) holder.value = tab
    }

    /**
     * 按位置切槽（旧壳 position 口径回落保留）；越界 / 损坏位置回落书架兜底（C3）。
     * 已在目标槽位时不重复切换。
     */
    fun switchTab(position: Int) {
        if (selectedTab.ordinal != position) {
            holder.value = ShellTab.values().getOrElse(position) { ShellTab.BOOKSHELF }
        }
    }
}

/** 创建壳态；枚举可序列化，配置变更后经 saveable 恢复。初始槽位先过 [ShellRestorePolicy.sanitizeTab] 兜底。 */
@Composable
fun rememberMainShellState(initialTab: ShellTab): MainShellState {
    val holder = rememberSaveable { mutableStateOf(ShellRestorePolicy.sanitizeTab(initialTab)) }
    return remember(holder) { MainShellState(holder) }
}
