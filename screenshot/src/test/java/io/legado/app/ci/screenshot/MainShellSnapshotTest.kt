package io.legado.app.ci.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.legado.app.ui.main.shell.ShellBottomBar
import io.legado.app.ui.main.shell.ShellPageSlot
import io.legado.app.ui.main.shell.ShellTab
import org.junit.Rule
import org.junit.Test

/**
 * B2 主壳三 Tab + 磨砂底栏离线截图帧（壳包 `ui/main/shell/`）。
 *
 * 出帧口径：只渲染壳内可脱离 Activity 宿主的两层公开件——
 *  [ShellBottomBar]（三槽等宽 + 双态图标 + 角标落点）与 [ShellPageSlot]（书架 / 发现两槽占位空态）；
 *  底栏三槽各出一帧，覆盖书架 / 发现 / 设置三 Tab 的选中态与底栏编排；占位槽只出书架 / 发现两档。
 *
 * 迂回声明一（不上整壳 [MainShell] 的原因）：整壳宿主层含 `BackHandler` 返回拦截、
 * `rememberSaveable` 壳态与 `AnimatedContent` 转场，依赖 Activity 返回栈 owner 与状态注册表；
 * 纯 JVM Paparazzi 宿主不提供这两项，整壳帧列入 CI / 真机待验，不在本夹具内冒险渲染。
 *
 * 迂回声明二（设置槽不出页帧的原因）：`ShellPageSlot(SETTINGS)` 直挂 `B3SettingScreen`，
 * 默认动作捕获 `appDb` 单例 / FileUtils / AppWebDav / 目录句柄，开关行经
 * `PreferenceManager` 读 SharedPreferences；纯 JVM Paparazzi 宿主无 Room / Application /
 * 偏好实现，渲染即崩。设置页本体帧归集成环境 / 真机走查，不在本离线夹具内冒险渲染。
 * 帧文件名由 Paparazzi 按 包名_类名_方法名 生成，落 `screenshot/src/test/snapshots/images/`。
 */
class MainShellSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    @Test
    fun shellBottomBarBookshelf() {
        paparazzi.snapshot {
            BottomBarFrame(selected = ShellTab.BOOKSHELF, badgeCount = 2)
        }
    }

    @Test
    fun shellBottomBarExplore() {
        paparazzi.snapshot {
            BottomBarFrame(selected = ShellTab.EXPLORE, badgeCount = 0)
        }
    }

    @Test
    fun shellBottomBarSettings() {
        paparazzi.snapshot {
            BottomBarFrame(selected = ShellTab.SETTINGS, badgeCount = 0)
        }
    }

    @Test
    fun shellPageSlotBookshelf() {
        paparazzi.snapshot {
            PageSlotFrame(tab = ShellTab.BOOKSHELF)
        }
    }

    @Test
    fun shellPageSlotExplore() {
        paparazzi.snapshot {
            PageSlotFrame(tab = ShellTab.EXPLORE)
        }
    }

    @Composable
    private fun BottomBarFrame(selected: ShellTab, badgeCount: Int) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ShellBottomBar(
                tabs = ShellTab.defaultOrder,
                selected = selected,
                onSelect = {},
                badgeCount = badgeCount,
            )
        }
    }

    @Composable
    private fun PageSlotFrame(tab: ShellTab) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ShellPageSlot(tab = tab)
        }
    }
}
