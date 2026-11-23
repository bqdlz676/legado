package io.legado.app.ui.map.crossing.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.ComponentPalette
import io.legado.app.ui.map.crossing.engine.CampaignController
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.TerrainKind
import io.legado.app.ui.map.crossing.model.TurnSnapshot
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import kotlinx.coroutines.delay

/**
 * 四渡赤水推演自绘壳（§1.5 绢帛军图：底图 + 渡口金柱 + 行军线路 + 棋子）。
 *
 * 本层不包裹 MaterialTheme、不取 Material colorScheme；所有颜色走 [CrossingTokens] 的
 * 12 色 token。引擎 [CampaignController] 是纯 JVM 对象，壳层只持有快照与节拍，不在模型里
 * 塞 Compose 类型。降级时显示明示标识与说明文字，不留空白态。
 */
@Composable
fun CrossingCampaignShell(
    controller: CampaignController = remember { CampaignController.demo() },
    modifier: Modifier = Modifier,
) {
    var host by remember { mutableStateOf(controller) }
    var snapshot by remember { mutableStateOf(host.snapshot()) }
    var autoPlay by remember { mutableStateOf(false) }
    var overrides by remember { mutableStateOf<Map<String, Intent>>(emptyMap()) }

    LaunchedEffect(autoPlay, host) {
        while (autoPlay) {
            snapshot = host.step(overrides)
            overrides = emptyMap()
            if (snapshot.status != io.legado.app.ui.map.crossing.model.BattleStatus.ONGOING) {
                autoPlay = false
            }
            delay(AUTO_STEP_DELAY_MS)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(host) {
                detectTapGestures { /* 预留棋盘点选：点棋子展开意图面板 */ }
            },
    ) {
        MapCanvas(
            host = host,
            snapshot = snapshot,
            modifier = Modifier.fillMaxSize(),
        )

        // 空数据兜底：军队为空时居中写明提示，画布不留白。
        if (snapshot.corps.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = "暂无可用推演军队",
                    style = TextStyle(
                        color = Color(CrossingTokens.textPrimary),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = host.warnings.firstOrNull() ?: "战役数据缺失，已按兜底战场渲染",
                    style = TextStyle(
                        color = Color(CrossingTokens.textSecondary),
                        fontSize = 11.sp,
                    ),
                )
            }
        }

        // 顶栏：标题 / 年代 / 回合 / 降级标识。
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = host.definition.title.ifBlank { "推演战场" },
                    style = TextStyle(
                        color = Color(CrossingTokens.textPrimary),
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = host.definition.era,
                    style = TextStyle(
                        color = Color(CrossingTokens.textSecondary),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                if (host.degraded) {
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        text = "演示降级",
                        style = TextStyle(
                            color = Color(CrossingTokens.textAccent),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "回合 ${snapshot.turn} / ${host.definition.duration}",
                style = TextStyle(
                    color = Color(CrossingTokens.brandGold),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }

        // 底部毛玻璃面板：事件条 + 军队意图 + 操作按钮。
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            EventLog(snapshot.events)
            Spacer(Modifier.height(8.dp))
            CorpsRoster(
                snapshot = snapshot,
                overrides = overrides,
                onCycleIntent = { id, intent -> overrides = overrides + (id to intent) },
            )
            Spacer(Modifier.height(8.dp))
            ControlRow(
                autoPlay = autoPlay,
                finished = snapshot.status != io.legado.app.ui.map.crossing.model.BattleStatus.ONGOING,
                onNext = {
                    snapshot = host.step(overrides)
                    overrides = emptyMap()
                },
                onToggleAuto = { autoPlay = !autoPlay },
                onReset = {
                    host = CampaignController.demo()
                    snapshot = host.snapshot()
                    overrides = emptyMap()
                    autoPlay = false
                },
            )
        }
    }
}

// ── 军图绘制 ─────────────────────────────────────────────────

@Composable
private fun MapCanvas(
    host: CampaignController,
    snapshot: TurnSnapshot,
    modifier: Modifier = Modifier,
) {
    val projection = host.projection
    val map = host.map
    val raster = host.raster

    Canvas(modifier = modifier) {
        val n = projection.gridN
        val step = size.minDimension / n

        // 绢帛底。
        drawRect(Color(CrossingTokens.bgPage))

        // 地形高程栅格：按地形类别落 token 浅色层，不引入地貌数据色。
        for (row in 0 until raster.height) {
            for (col in 0 until raster.width) {
                val kind = map.kindAt(io.legado.app.ui.map.crossing.model.Cell(col, row))
                val shade = terrainShade(kind)
                drawRect(
                    color = shade,
                    topLeft = Offset(col * step, row * step),
                    size = androidx.compose.ui.geometry.Size(step, step),
                )
            }
        }

        // 河道：石青细带。
        val riverColor = Color(CrossingTokens.stoneBlue).copy(alpha = 0.32f)
        map.riverCells().forEach { cell ->
            drawRect(
                color = riverColor,
                topLeft = Offset(cell.col * step, cell.row * step),
                size = androidx.compose.ui.geometry.Size(step, step),
            )
        }

        // 渡口金柱：暖金竖柱 + 外圈辉光。
        map.fordCells.forEach { cell ->
            val cx = (cell.col + 0.5f) * step
            val cy = (cell.row + 0.5f) * step
            drawCircle(Color(CrossingTokens.colorGlow), radius = step * 0.9f, center = Offset(cx, cy))
            drawLine(
                color = Color(CrossingTokens.brandGold),
                start = Offset(cx, cy - step * 0.5f),
                end = Offset(cx, cy + step * 0.5f),
                strokeWidth = step * 0.16f,
            )
        }

        // 行军线路：己方暖金、盟友古铜、敌方灰青。
        snapshot.corps.filter { it.alive }.forEach { corps ->
            if (corps.route.size >= 2) {
                val color = routeColor(corps.role)
                val pts = corps.route.map { geoToOffset(it, projection, size) }
                for (i in 1 until pts.size) {
                    drawLine(
                        color = color.copy(alpha = 0.7f),
                        start = pts[i - 1],
                        end = pts[i],
                        strokeWidth = 1.6f,
                    )
                }
            }
        }

        // 棋子：势力底色块 + 角色色圆子；溃散 / 投降压暗。
        snapshot.corps.forEach { corps ->
            val pos = geoToOffset(Geo(corps.lat, corps.lng), projection, size)
            val fillAlpha = when (corps.role) {
                FactionRole.OWN -> CrossingTokens.OWN_FILL_ALPHA
                FactionRole.ENEMY -> CrossingTokens.ENEMY_FILL_ALPHA
                FactionRole.ALLY -> CrossingTokens.ALLY_FILL_ALPHA
                FactionRole.NEUTRAL -> 0.06f
            }
            val pieceColor = pieceColor(corps.role)
            drawCircle(pieceColor.copy(alpha = fillAlpha), radius = step * 0.72f, center = pos)
            val dim = !corps.alive || corps.surrendered
            drawCircle(
                color = pieceColor.copy(alpha = if (dim) 0.35f else 1.0f),
                radius = step * 0.30f,
                center = pos,
            )
            drawCircle(
                color = Color(CrossingTokens.colorGlow),
                radius = step * 0.30f + 2f,
                center = pos,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )
        }

    }
}

// ── 底部面板 ────────────────────────────────────────────────

@Composable
private fun EventLog(events: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        events.takeLast(3).forEach { line ->
            BasicText(
                text = "· $line",
                style = TextStyle(
                    color = Color(CrossingTokens.textSecondary),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

@Composable
private fun CorpsRoster(
    snapshot: TurnSnapshot,
    overrides: Map<String, Intent>,
    onCycleIntent: (String, Intent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        snapshot.corps.take(6).forEach { corps ->
            val override = overrides[corps.id]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(corps.id) {
                        detectTapGestures {
                            onCycleIntent(corps.id, nextIntent(override ?: corps.intent))
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = corps.name,
                    style = TextStyle(
                        color = Color(pieceColor(corps.role)),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier.width(72.dp),
                )
                BasicText(
                    text = "兵力 ${corps.strength.round1()}｜${corps.morale.label()}",
                    style = TextStyle(
                        color = Color(CrossingTokens.textSecondary),
                        fontSize = 11.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    text = (override ?: corps.intent).label(),
                    style = TextStyle(
                        color = Color(CrossingTokens.brandGold),
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ControlRow(
    autoPlay: Boolean,
    finished: Boolean,
    onNext: () -> Unit,
    onToggleAuto: () -> Unit,
    onReset: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ShellButton(if (finished) "已终局" else "下一步", enabled = !finished, onClick = onNext)
        ShellButton(if (autoPlay) "暂停推演" else "自动推演", enabled = !finished, onClick = onToggleAuto)
        ShellButton("重置", enabled = true, onClick = onReset)
    }
}

@Composable
private fun ShellButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = when {
        !enabled -> Color(CrossingTokens.textTertiary)
        else -> Color(CrossingTokens.brandGold)
    }
    BasicText(
        text = text,
        style = TextStyle(color = color, fontSize = 13.sp, fontFamily = FontFamily.Serif),
        modifier = Modifier
            .pointerInput(text) { detectTapGestures(if (enabled) ({ onClick() }) else ({})) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ── 取色与小工具 ────────────────────────────────────────────

private fun terrainShade(kind: TerrainKind): Color = when (kind) {
    TerrainKind.WATER -> Color(CrossingTokens.stoneBlue).copy(alpha = 0.18f)
    TerrainKind.PLAIN -> Color(CrossingTokens.bgSurface).copy(alpha = 0.55f)
    TerrainKind.HILL -> Color(CrossingTokens.brandCopper).copy(alpha = 0.10f)
    TerrainKind.MOUNTAIN -> Color(CrossingTokens.brandCopper).copy(alpha = 0.22f)
    TerrainKind.SNOW -> Color(CrossingTokens.textTertiary).copy(alpha = 0.18f)
}

private fun pieceColor(role: FactionRole): Color = when (role) {
    FactionRole.OWN -> Color(CrossingTokens.brandGold)
    FactionRole.ENEMY -> Color(CrossingTokens.stoneBlue)
    FactionRole.ALLY -> Color(CrossingTokens.brandCopper)
    FactionRole.NEUTRAL -> Color(CrossingTokens.textTertiary)
}

private fun routeColor(role: FactionRole): Color = when (role) {
    FactionRole.OWN -> Color(CrossingTokens.brandGold)
    FactionRole.ENEMY -> Color(CrossingTokens.stoneBlue)
    FactionRole.ALLY -> Color(CrossingTokens.brandCopper)
    FactionRole.NEUTRAL -> Color(CrossingTokens.textTertiary)
}

private fun geoToOffset(geo: Geo, projection: GeoProjection, size: androidx.compose.ui.geometry.Size): Offset {
    val cell = projection.toCell(geo)
    val n = projection.gridN
    val step = size.minDimension / n
    return Offset((cell.col + 0.5f) * step, (cell.row + 0.5f) * step)
}

private fun nextIntent(current: Intent): Intent {
    val cycle = listOf(Intent.HOLD, Intent.ADVANCE, Intent.RETREAT, Intent.FIGHT, Intent.AMBUSH)
    val index = cycle.indexOf(current)
    return cycle[(index + 1).coerceAtLeast(0) % cycle.size]
}

private fun Intent.label(): String = when (this) {
    Intent.FIGHT -> "应战"
    Intent.ROUT -> "溃逃"
    Intent.BREAKOUT -> "突围"
    Intent.AMBUSH -> "设伏"
    Intent.RETREAT -> "撤退"
    Intent.SUPPORT -> "支援"
    Intent.ADVANCE -> "进军"
    Intent.FLANK -> "包抄"
    Intent.SEIZE -> "夺隘"
    Intent.RAPID -> "急行军"
    Intent.CONTINUE -> "续进"
    Intent.HOLD -> "据守"
}

private fun io.legado.app.ui.map.crossing.model.Morale.label(): String = when (this) {
    io.legado.app.ui.map.crossing.model.Morale.HIGH -> "高昂"
    io.legado.app.ui.map.crossing.model.Morale.STEADY -> "平稳"
    io.legado.app.ui.map.crossing.model.Morale.WAVERING -> "动摇"
    io.legado.app.ui.map.crossing.model.Morale.BROKEN -> "溃散"
}

private fun Double.round1(): Double = (this * 10).toInt() / 10.0

/** 自动推演每回合间隔（毫秒）：留出壳层重绘余量，避免连点式快进。 */
private const val AUTO_STEP_DELAY_MS: Long = 600L

/** 弹簧参数与组件库气泡回弹口径对齐（§1.5-C）。 */
@Suppress("unused")
private val ShellSpringDamping: Float = ComponentPalette.BUBBLE_SPRING_DAMPING

@Suppress("unused")
private val ShellSpringStiffness: Float = ComponentPalette.BUBBLE_SPRING_STIFFNESS
