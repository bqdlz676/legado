package io.legado.app.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * B2 壳内页面挂载槽：三槽各自的页面编排归后续批次，本槽只给空态兜底占位，
 * 不留空白页、不崩（C3）；数据接入前统一走 [ComponentVisualState.EMPTY] 契约档。
 *
 * 预留挂载位（页面批次接线时替换本槽内容，壳状态 / 导航不动）：
 *  - [ShellTab.BOOKSHELF] → B3 书架批次：BookshelfScreen 数据流接线；
 *  - [ShellTab.EXPLORE]   → B5 发现批次：InkExploreScreen 数据流接线；
 *  - [ShellTab.SETTINGS]  → B6 设置批次：偏好 / 关于 / 归档分区接线。
 */
@Composable
fun ShellPageSlot(
    tab: ShellTab,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
        contentAlignment = Alignment.Center,
    ) {
        val message = when (tab) {
            ShellTab.BOOKSHELF -> stringResource(R.string.bookshelf)
            ShellTab.EXPLORE -> stringResource(R.string.discovery)
            ShellTab.SETTINGS -> stringResource(R.string.setting)
        }
        InkEmptyState(
            message = message,
            state = ComponentVisualState.EMPTY,
            compact = true,
        )
    }
}
