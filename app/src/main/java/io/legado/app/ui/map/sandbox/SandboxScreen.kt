package io.legado.app.ui.map.sandbox

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 3D 沙盘自绘壳（ui/map/sandbox 独占，切片-06）。
 *
 * 本层不包裹 MaterialTheme、不取 Material colorScheme，背景 / 线条 / 按钮色全部从
 * [SandboxTokens] 的 12 色 token 转换；地形三维渲染由 GL / Canvas 渲染 pass 消费
 * [SandboxScene] 的核算结果，本壳只负责手势映射、时间轴与悬浮信息层。
 *
 * 为什么状态类是普通 JVM 对象：配额核算、判点、相机钳制都在纯 JVM 层可单测；
 * 壳层用一个帧节拍 [tick] 驱动重读模型，不在模型里塞 Compose 快照类型。
 */
@Composable
fun SandboxScreenHost(
    scene: SandboxScene = rememberSandboxScene(),
    interaction: SandboxInteraction = remember { SandboxInteraction() },
    rig: CameraRig = remember { CameraRig(focus = LatLng(35.6, 112.0)) },
    modifier: Modifier = Modifier,
) {
    var tick by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 单指拖动 → 旋转；双指捏合 → 缩放；双指同滑 → 平移并锁旋转。
                    val twoFinger = zoom != 1.0f
                    if (twoFinger) {
                        rig.pinchZoom(zoom.toDouble())
                        rig.panBy((pan.x * PAN_SPEED_KM).toDouble(), (pan.y * PAN_SPEED_KM).toDouble())
                    } else {
                        rig.rotateBy((pan.x * ROTATE_SPEED_DEG).toDouble(), (pan.y * ROTATE_SPEED_DEG).toDouble())
                    }
                    interaction.zoomDuringPlay()
                    tick++
                }
            },
    ) {
        // 绢帛暗底 + 边缘雾气底色；地形 mesh 由渲染 pass 叠加在这层之上。
        // 读取 [tick] 作为帧节拍，手势 / 模型变更后驱动本层重读状态。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val shimmer = 0.98f + 0.02f * ((tick and 1).toFloat())
            drawRect(Color(SandboxTokens.textPrimary).copy(alpha = shimmer))
        }

        // 顶栏：无背景，字浮画面上方（切片-07 同款顶栏口径，沙盘沿用）。
        if (!interaction.theaterMode) {
            BasicText(
                text = "春秋沙盘",
                style = TextStyle(
                    color = Color(SandboxTokens.textSecondary),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }

        // 底部金线时间轴 + 阶段铜点；剧场模式下整层锁定隐藏。
        if (!interaction.theaterMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "回合 ${interaction.turn.toInt()}",
                        style = TextStyle(
                            color = Color(SandboxTokens.brandGold),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    PhaseDots(interaction)
                }
                Spacer(Modifier.height(8.dp))
                TimelineLine(interaction.turn, campaignRounds = 30)
            }
        } else {
            // 剧场模式唯一出口：左下角半透明「退出剧场」。
            BasicText(
                text = "退出剧场",
                style = TextStyle(
                    color = Color(SandboxTokens.colorGlow),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { interaction.exitTheater() }
                    },
            )
        }

        // 悬浮信息卡：3 秒自动消失（超时回收由 interaction.update 驱动）。
        interaction.update()?.let { card ->
            BasicText(
                text = "${card.title}｜${card.body}",
                style = TextStyle(
                    color = Color(SandboxTokens.bgSurface),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun PhaseDots(interaction: SandboxInteraction) {
    // 阶段铜点由战役阶段驱动；沙盘种子阶段点在时间轴下方，当前阶段点亮暖金，其余暗。
    Row {
        repeat(4) { index ->
            val active = interaction.currentPhaseLabel != null && index == 0
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(8.dp)) {
                drawCircle(
                    Color(if (active) SandboxTokens.brandGold else SandboxTokens.brandCopper),
                )
            }
        }
    }
}

@Composable
private fun TimelineLine(turn: Double, campaignRounds: Int) {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = Color(SandboxTokens.brandCopper),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1f,
        )
        val progress = if (campaignRounds <= 0) 0f else (turn / campaignRounds).toFloat()
        drawCircle(
            color = Color(SandboxTokens.textAccent),
            radius = 4f,
            center = Offset(size.width * progress.coerceIn(0f, 1f), size.height / 2 - 2f),
        )
    }
}

/** 默认场景：进入沙盘时延迟加载 DEM 栅格；资产缺失由加载器回落平面地形。 */
@Composable
private fun rememberSandboxScene(): SandboxScene {
    val context = LocalContext.current
    return remember(context) { defaultScene(context) }
}

private fun defaultScene(context: Context): SandboxScene = SandboxScene(
    powers = PowerLayer(SeedSandbox.powers),
    cities = SeedSandbox.cities,
    rivers = SeedSandbox.rivers,
    sites = SeedSandbox.sites,
    chokes = SeedSandbox.chokes,
    trails = emptyList(),
    elevation = DemTerrainLoader.load(context),
)

private const val ROTATE_SPEED_DEG: Float = 0.2f
private const val PAN_SPEED_KM: Float = 0.05f
