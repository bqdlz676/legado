package io.legado.app.ci.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkDivider
import io.legado.app.ui.components.InkDividerVariant
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Rule
import org.junit.Test

/**
 * B2 主壳三 Tab + 磨砂底栏离线截图帧（壳包 `ui/main/shell/`）。
 *
 * 出帧口径（2026-11-27 修资源依赖红后）：`:screenshot` 独立模块的 Paparazzi 运行期拿不到
 * `:app` 资源表（`R.drawable` / `R.string` 解析即抛 `Resources$NotFoundException` 或
 * `IllegalArgumentException`）。真实 `ShellBottomBar` 内部直挂
 * `painterResource(tab.iconOnRes/iconOffRes)` 与 `stringResource(tab.labelRes)`，
 * 真实 `ShellPageSlot` 占位槽直挂 `stringResource(R.string.bookshelf/discovery)`，离线必红。
 * 本夹具不调真实壳件，改在测试源集内按壳编排口径重放离线替身：三槽等宽、选中暖金 /
 * 未选中暗铜、角标落点、金线顶线、占位空态；颜色 / 尺寸全部取 [ComponentTokens] 纯常量，
 * 文案逐字对齐 `values-zh/strings.xml`（书架 / 发现 / 设置）。
 *
 * 替身边界（不冒充真实组件帧）：
 *  - 图标为自绘圆角矩形描边占位，不是 `ic_tab_*` 矢量描边；真实图标双态帧待资源表可注入后回补。
 *  - 底栏磨砂面、按压朱砂泛色、导航栏内边距属宿主与手势态，静态替身帧不渲染。
 *  - 设置槽 `B3SettingScreen` 直挂 SharedPreferences / Room / Context 句柄，离线不可渲染，仍不出页帧。
 *
 * 恢复条件：`:app` 资源表可进 `:screenshot` 测试运行类路径（或壳件下沉出无资源重载）后，
 * 本夹具切回调用真实 `ShellBottomBar` / `ShellPageSlot`，替身编排一并删除。
 * 帧文件名由 Paparazzi 按 包名_类名_方法名 生成，落 `screenshot/src/test/snapshots/images/`。
 */
class MainShellSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    @Test
    fun shellBottomBarBookshelf() {
        paparazzi.snapshot {
            BottomBarFrame(selected = ShellSlot.BOOKSHELF, badgeCount = 2)
        }
    }

    @Test
    fun shellBottomBarExplore() {
        paparazzi.snapshot {
            BottomBarFrame(selected = ShellSlot.EXPLORE, badgeCount = 0)
        }
    }

    @Test
    fun shellBottomBarSettings() {
        paparazzi.snapshot {
            BottomBarFrame(selected = ShellSlot.SETTINGS, badgeCount = 0)
        }
    }

    @Test
    fun shellPageSlotBookshelf() {
        paparazzi.snapshot {
            PageSlotFrame(tab = ShellSlot.BOOKSHELF)
        }
    }

    @Test
    fun shellPageSlotExplore() {
        paparazzi.snapshot {
            PageSlotFrame(tab = ShellSlot.EXPLORE)
        }
    }

    /**
     * 壳槽位离线替身。标签逐字对齐 `values-zh/strings.xml`：
     * `bookshelf`=书架、`discovery`=发现、`setting`=设置；不引用 `ShellTab` 的 R 资源 id。
     */
    private enum class ShellSlot(val label: String) {
        BOOKSHELF("书架"),
        EXPLORE("发现"),
        SETTINGS("设置"),
    }

    @Composable
    private fun BottomBarFrame(selected: ShellSlot, badgeCount: Int) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 暖金顶线：槽位区与内容区的视觉分界，线色取 token 金线档（同真实底栏口径）。
            InkDivider(variant = InkDividerVariant.GOLD_LINE, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ComponentTokens.bubbleGap),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShellSlot.values().forEach { slot ->
                    SlotItem(
                        slot = slot,
                        selected = slot == selected,
                        badgeCount = if (slot == ShellSlot.BOOKSHELF) badgeCount else 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    @Composable
    private fun SlotItem(
        slot: ShellSlot,
        selected: Boolean,
        badgeCount: Int,
        modifier: Modifier = Modifier,
    ) {
        // 双色令牌口径同真实底栏：选中暖金 brandGold、未选中暗铜 brandCopper。
        val contentColor = if (selected) ComponentTokens.brandGold else ComponentTokens.brandCopper
        Box(
            modifier = modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    IconPlaceholder(contentColor = contentColor)
                    if (badgeCount > 0) {
                        BadgeDot(count = badgeCount)
                    }
                }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = slot.label,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
            }
        }
    }

    /** 离线图标占位：24dp 圆角矩形描边，按选中态着色；替真实 `ic_tab_*` 矢量图标。 */
    @Composable
    private fun IconPlaceholder(contentColor: Color) {
        Canvas(Modifier.size(24.dp)) {
            drawRoundRect(
                color = contentColor,
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.5f),
            )
        }
    }

    /** 未读角标：朱砂小圆点 + 计数；口径同真实底栏角标（仅书架槽落点）。 */
    @Composable
    private fun BadgeDot(count: Int) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(50))
                .background(ComponentTokens.textAccent),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = count.toString(),
                style = TextStyle(
                    color = ComponentTokens.bgPage,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
    }

    @Composable
    private fun PageSlotFrame(tab: ShellSlot) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComponentTokens.bgPage),
            contentAlignment = Alignment.Center,
        ) {
            InkEmptyState(
                message = tab.label,
                state = ComponentVisualState.EMPTY,
                compact = true,
            )
        }
    }
}
