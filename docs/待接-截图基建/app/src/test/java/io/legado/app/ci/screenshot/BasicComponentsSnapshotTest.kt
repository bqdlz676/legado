package io.legado.app.ci.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.legado.app.ui.card.CardState
import io.legado.app.ui.card.CategoryCard
import io.legado.app.ui.card.EmptyKind
import io.legado.app.ui.card.EmptyStateCard
import io.legado.app.ui.card.SettingRowCard
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkCard
import io.legado.app.ui.components.InkCardVariant
import io.legado.app.ui.components.InkEmptyState
import org.junit.Rule
import org.junit.Test

/**
 * 基础通用组件截图帧：按钮两档、信息卡、空态、空态卡、设置行、类别索引卡。
 * 全部为纯展示组件，参数走离线夹具，不起手势路由 / 导航跳转。
 */
class BasicComponentsSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    @Test
    fun inkButtonPrimary() {
        paparazzi.snapshot {
            Centered {
                InkButton(text = "开始阅读", onClick = {}, variant = InkButtonVariant.PRIMARY)
            }
        }
    }

    @Test
    fun inkButtonSecondary() {
        paparazzi.snapshot {
            Centered {
                InkButton(text = "加入书架", onClick = {}, variant = InkButtonVariant.SECONDARY)
            }
        }
    }

    @Test
    fun inkCardElevated() {
        paparazzi.snapshot {
            Centered {
                InkCard(
                    title = "落文笺注",
                    subtitle = "东方美学阅读基座",
                    variant = InkCardVariant.ELEVATED,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun inkEmptyState() {
        paparazzi.snapshot {
            Centered {
                InkEmptyState(message = "架上暂无卷册", actionLabel = "导入本地书")
            }
        }
    }

    @Test
    fun emptyStateCardShelf() {
        paparazzi.snapshot {
            Centered {
                EmptyStateCard(kind = EmptyKind.EmptyShelf, onAction = {}, state = CardState.Default)
            }
        }
    }

    @Test
    fun settingRowCard() {
        paparazzi.snapshot {
            Centered {
                SettingRowCard(
                    title = "绢帛底色",
                    value = "宣纸白",
                    checked = true,
                    onCheckedChange = {},
                    showArrow = true,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun categoryCard() {
        paparazzi.snapshot {
            Centered {
                CategoryCard(
                    name = "史部",
                    count = 12,
                    previewItems = listOf("史记", "汉书", "后汉书", "三国志"),
                    onClick = {},
                    state = CardState.Default,
                )
            }
        }
    }

    @Composable
    private fun Centered(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                content()
            }
        }
    }
}
