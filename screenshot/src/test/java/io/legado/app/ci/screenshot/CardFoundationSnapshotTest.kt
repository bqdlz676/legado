package io.legado.app.ci.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.legado.app.ui.card.CardState
import io.legado.app.ui.card.CardBase
import org.junit.Rule
import org.junit.Test

/**
 * 卡片基座五态截图帧（锚点屏 3/3：自绘 Canvas 阴影 / 颗粒 / 骨架光带路径）。
 *
 * 五态：Default / Hover / Pressed / Loading / Disabled，与 [CardState] 一一对应。
 * Loading 态基座自绘骨架灰块 + 暖金斜光带，不再渲染内容槽；其余四态叠宣纸噪点层。
 */
class CardFoundationSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    @Test
    fun cardStateDefault() = snap(CardState.Default)

    @Test
    fun cardStateHover() = snap(CardState.Hover)

    @Test
    fun cardStatePressed() = snap(CardState.Pressed)

    @Test
    fun cardStateLoading() = snap(CardState.Loading)

    @Test
    fun cardStateDisabled() = snap(CardState.Disabled)

    private fun snap(state: CardState) {
        paparazzi.snapshot {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CardBase(
                    state = state,
                    modifier = Modifier.width(240.dp).height(140.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BasicText("卡片基座 · ${state.name}")
                    }
                }
            }
        }
    }
}
