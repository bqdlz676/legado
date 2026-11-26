package io.legado.app.ci.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.legado.app.ui.components.ComponentVisualState
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkDivider
import io.legado.app.ui.components.InkDividerVariant
import io.legado.app.ui.components.InkProgress
import io.legado.app.ui.components.InkProgressVariant
import org.junit.Rule
import org.junit.Test

/**
 * 组件层七态契约帧 + 新增分割线 / 进度线变体帧（`ui/components/`）。
 *
 * 七态口径：契约枚举 [ComponentVisualState] 默认 / 悬浮 / 按压 / 选中 / 禁用 / 加载 / 空 / 离线
 * （选中档为契约补充档，随七态一并出帧，不留缺档）；统一取 PRIMARY 按钮承载可视态，
 * 离线 / 禁用 / 加载 / 空态的透明度、文案与光带全部由组件内部按契约解析，夹具只灌态不接管手势。
 *
 * 变体口径：[InkDivider] 云灰细线 / 金线 / 朱砂线三变体；[InkProgress] 阅读朱砂竖线 / 时间轴蚕丝金线两变体。
 * 全部纯展示离屏出帧，不起路由 / 网络 / 订阅。帧名由 Paparazzi 按 包名_类名_方法名 生成。
 */
class InkComponentsSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    // ---- 组件七态（契约枚举八值，含补充 SELECTED 档）----

    @Test
    fun inkButtonStateDefault() = snapButton(ComponentVisualState.DEFAULT, "开始阅读")

    @Test
    fun inkButtonStateHovered() = snapButton(ComponentVisualState.HOVERED, "悬停预览")

    @Test
    fun inkButtonStatePressed() = snapButton(ComponentVisualState.PRESSED, "按压中")

    @Test
    fun inkButtonStateSelected() = snapButton(ComponentVisualState.SELECTED, "选中档")

    @Test
    fun inkButtonStateDisabled() = snapButton(ComponentVisualState.DISABLED, "暂不可用")

    @Test
    fun inkButtonStateLoading() = snapButton(ComponentVisualState.LOADING, "加载中")

    @Test
    fun inkButtonStateEmpty() = snapButton(ComponentVisualState.EMPTY, "空数据")

    @Test
    fun inkButtonStateOffline() = snapButton(ComponentVisualState.OFFLINE, "离线重试")

    // ---- InkDivider 三变体 ----

    @Test
    fun inkDividerCloudLine() = snapDivider(InkDividerVariant.CLOUD_LINE, 1.dp)

    @Test
    fun inkDividerGoldLine() = snapDivider(InkDividerVariant.GOLD_LINE, 1.5.dp)

    @Test
    fun inkDividerCinnabarLine() = snapDivider(InkDividerVariant.CINNABAR_LINE, 1.5.dp)

    // ---- InkProgress 两变体 ----

    @Test
    fun inkProgressReaderVertical() =
        snapProgress(InkProgressVariant.READER_VERTICAL, 0.62f, 6.dp)

    @Test
    fun inkProgressTimelineGold() =
        snapProgress(InkProgressVariant.TIMELINE_GOLD, 0.35f, 3.dp)

    @Composable
    private fun snapButton(state: ComponentVisualState, label: String) {
        Centered {
            InkButton(
                text = label,
                onClick = {},
                variant = InkButtonVariant.PRIMARY,
                state = state,
            )
        }
    }

    @Composable
    private fun snapDivider(variant: InkDividerVariant, thickness: androidx.compose.ui.unit.Dp) {
        Centered {
            Box(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                InkDivider(variant = variant, thickness = thickness)
            }
        }
    }

    @Composable
    private fun snapProgress(variant: InkProgressVariant, progress: Float, thickness: androidx.compose.ui.unit.Dp) {
        Centered {
            InkProgress(
                variant = variant,
                progress = progress,
                thickness = thickness,
                modifier = Modifier.width(thickness).height(120.dp),
            )
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
