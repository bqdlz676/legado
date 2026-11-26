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
import io.legado.app.ui.config.b3.B3SettingScreen

/**
 * B2 壳内页面挂载槽：三槽各自的页面编排归后续批次，未接线槽只给空态兜底占位，
 * 不留空白页、不崩（C3）；数据接入前统一走 [ComponentVisualState.EMPTY] 契约档。
 *
 * 预留挂载位（页面批次接线时替换本槽内容，壳状态 / 导航不动）：
 *  - [ShellTab.BOOKSHELF] → 书架批次：BookshelfScreen 数据流接线；
 *  - [ShellTab.EXPLORE]   → 发现批次：InkExploreScreen 数据流接线；
 *  - [ShellTab.SETTINGS]  → B3 设置批次：[B3SettingScreen] 已接线（偏好 / 分组 / 搜索 / 备份）。
 *
 * 编号漂移回写（N7）：缺口盘点曾称 B5、旧注释曾称 B6，以最新派单口径 B3 为准。
 *
 * @param reduceMotion 系统减少动画档，透传给已接线页面；占位槽不消费。
 */
@Composable
fun ShellPageSlot(
    tab: ShellTab,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    when (tab) {
        // B3：设置页本体；行视觉收口到 [SettingRowCard] 实例卡，壳状态 / 底栏 / 导航不动。
        ShellTab.SETTINGS -> B3SettingScreen(modifier = modifier, reduceMotion = reduceMotion)

        ShellTab.BOOKSHELF, ShellTab.EXPLORE -> Box(
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
}
