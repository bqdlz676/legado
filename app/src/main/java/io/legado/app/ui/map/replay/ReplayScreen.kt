package io.legado.app.ui.map.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.map.sandbox.DemTerrainLoader
import io.legado.app.ui.map.sandbox.TerrainElevation

/**
 * 3D 战役回放自绘壳（ui/map/replay 独占，切片-07）。
 *
 * 不包裹 MaterialTheme：顶栏 / 仪表盘 / 信息卡颜色全部从 [ReplayTokens] 的 12 色 token
 * 转换；绢帛军图三档地貌色取 [ReplaySurface] 派生值。推演时钟、出生编排、交战效果
 * 都在 [ReplayDirector] 纯 JVM 层，本壳只按帧节拍重读状态并自绘叠加层。
 */
@Composable
fun ReplayScreenHost(
    director: ReplayDirector? = null,
    elevation: TerrainElevation? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 高程来源优先级：外部显式注入 > 外部导演自带 > 加载器取同一 DEM 实例。
    // 回放域内不二次解码 PNG；解码失败由加载器回落 Flat。显式传导演时不再触发资产解码。
    val resolvedElevation: TerrainElevation? = elevation
        ?: director?.elevation
        ?: remember(context) { DemTerrainLoader.load(context) }
    val activeDirector = director ?: remember(resolvedElevation) {
        ReplayDirector(SeedReplay.changping, elevation = resolvedElevation!!)
    }

    var tick by remember { mutableStateOf(0L) }
    var lastFrameNanos by remember { mutableStateOf(0L) }

    LaunchedEffect(activeDirector) {
        withFrameNanos { now ->
            lastFrameNanos = now
        }
        while (true) {
            withFrameNanos { now ->
                val dtMs = (now - lastFrameNanos).coerceAtLeast(0L) / 1_000_000L
                lastFrameNanos = now
                activeDirector.update(dtMs)
                tick++
            }
        }
    }

    val campaign = activeDirector.campaign
    val nowMs = System.currentTimeMillis()
    val birthStage = activeDirector.birthStage(nowMs)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    // 播放中点画面任意位置 → 暂停；其余碰屏刷新栏位空闲计时。
                    if (activeDirector.playing) activeDirector.pause()
                    activeDirector.interact()
                }
            },
    ) {
        // 绢帛暗底；出生阶段 GRID/RISE 由渲染 pass 叠经纬网与地形隆起。
        // 读取 [tick] 作为帧节拍，导演状态变更后驱动本层重读。
        Canvas(Modifier.fillMaxSize()) {
            val shimmer = 0.98f + 0.02f * ((tick and 1).toFloat())
            drawRect(Color(ReplaySurface.silkBase).copy(alpha = shimmer))
        }

        // 地形降级标志：DEM 缺失 / 解码失败回落平面时右上角一行小字提示；不阻断推演。
        if (activeDirector.terrainDegraded) {
            BasicText(
                text = "平面地形",
                style = TextStyle(
                    color = Color(ReplayTokens.textTertiary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            )
        }

        // 顶栏：无背景，战役名 16sp / 年代 12sp；3 秒无操作自动淡出。
        if (activeDirector.topBarVisible && birthStage == BirthStage.READY) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "←",
                    style = TextStyle(
                        color = Color(ReplayTokens.headerInk),
                        fontSize = 16.sp,
                    ),
                    modifier = Modifier.padding(end = 12.dp),
                )
                BasicText(
                    text = campaign.title,
                    style = TextStyle(
                        color = Color(ReplayTokens.headerInk),
                        fontSize = ReplayConfig.TITLE_SP.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = campaign.era,
                    style = TextStyle(
                        color = Color(ReplayTokens.headerInk),
                        fontSize = ReplayConfig.ERA_SP.sp,
                    ),
                )
            }
        }

        // 底部战斗仪表盘：回合进度蚕丝金线 + 阶段铜点 + 播放控制。
        if (!activeDirector.theaterMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (activeDirector.bottomBarExpanded) {
                    DashboardRow(activeDirector)
                } else {
                    // 收起态：只剩一条 4dp 高金线，点金线展开。
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(ReplayConfig.COLLAPSED_BAR_DP.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { activeDirector.interact() }
                            },
                    ) {
                        drawLine(
                            color = Color(ReplayTokens.brandGold),
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = 1f,
                        )
                    }
                }
            }
        }

        // 悬浮信息卡：3 秒自动消失。
        activeDirector.infoCard?.let { card ->
            BasicText(
                text = "${card.title}｜${card.body}",
                style = TextStyle(
                    color = Color(ReplayTokens.bgSurface),
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
private fun DashboardRow(director: ReplayDirector) {
    val campaign = director.campaign
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "回合 ${director.turn.toInt()}/${campaign.rounds}",
                style = TextStyle(
                    color = Color(ReplayTokens.brandGold),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(12.dp))
            ProgressLine(director.progress().toFloat())
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 播放 / 暂停 / 单步：自绘文字按钮，不用 Material 图标与 ripple。
            PlayPauseButton(director)
            Spacer(Modifier.width(16.dp))
            PhaseCopperDots(director)
        }
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = Color(ReplayTokens.brandCopper),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1f,
        )
        // 朱砂圆点浮在线上方 2dp，不嵌在线上。
        drawCircle(
            color = Color(ReplayTokens.textAccent),
            radius = 4f,
            center = Offset(
                size.width * progress.coerceIn(0f, 1f),
                size.height / 2 - ReplayConfig.PROGRESS_DOT_LIFT_DP.dp.toPx(),
            ),
        )
    }
}

@Composable
private fun PlayPauseButton(director: ReplayDirector) {
    val label = when {
        director.playing -> "⏸"
        else -> "▶"
    }
    BasicText(
        text = label,
        style = TextStyle(
            color = Color(ReplayTokens.brandGold),
            fontSize = 16.sp,
        ),
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    if (director.playing) director.pause() else director.play()
                },
            )
        },
    )
    // 暂停态单步前进。
    if (!director.playing) {
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = "▸▸",
            style = TextStyle(
                color = Color(ReplayTokens.textTertiary),
                fontSize = 14.sp,
            ),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { director.stepForward() }
            },
        )
    }
}

@Composable
private fun PhaseCopperDots(director: ReplayDirector) {
    val current = director.currentPhase()
    Row {
        director.campaign.phases.forEach { phase ->
            val active = phase == current
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(8.dp)) {
                drawCircle(
                    Color(if (active) ReplayTokens.brandGold else ReplayTokens.brandCopper),
                )
            }
        }
    }
}
