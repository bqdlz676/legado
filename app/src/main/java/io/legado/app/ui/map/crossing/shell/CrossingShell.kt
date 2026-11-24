package io.legado.app.ui.map.crossing.shell

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.ComponentPalette
import io.legado.app.ui.map.crossing.engine.CampaignController
import io.legado.app.ui.map.crossing.model.CorpsDiff
import io.legado.app.ui.map.crossing.model.CorpsView
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.SnapshotDiff
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

// ── B10 分支并列对比：双快照并排回放 + 逐回合差异标注 ──────────

/**
 * 分支并列对比壳：两条回放线同屏并排，逐回合 diff 高亮（§五「开一个分支跑到底，跟原历史线对比」）。
 *
 * 纯展示子组件：入参为两条已跑完的快照线（[CampaignController.runToEnd] 产物；
 * 首回合若需对照，调用方自行前置一次 `snapshot()`）。本组件不持有引擎状态、不回写控制器；
 * 分支存档读取 / 双控制器装配属 B7、B11、B14 接线范围，待合并派单。
 *
 * 两栏共用同一个回合下标：任一栏步进，两栏同步滚到对应回合；各栏按钮独立可点。
 */
@Composable
fun BranchCompareShell(
    leftLine: List<TurnSnapshot>?,
    rightLine: List<TurnSnapshot>?,
    modifier: Modifier = Modifier,
) {
    val safeLeft = leftLine.orEmpty()
    val safeRight = rightLine.orEmpty()
    val maxTurns = maxOf(safeLeft.size, safeRight.size)
    var rawIndex by remember { mutableStateOf(0) }
    // 线长在外部异步加载完成后可能由 0 变非 0：下标在读取处钳制到合法区间，避免越界取快照。
    val turnIndex = rawIndex.coerceIn(0, (maxTurns - 1).coerceAtLeast(0))

    val leftSnap = safeLeft.getOrNull(turnIndex)
    val rightSnap = safeRight.getOrNull(turnIndex)
    val diff = remember(turnIndex, safeLeft, safeRight) {
        SnapshotDiff.diff(leftSnap, rightSnap)
    }
    val diffMap = diff.corps.associateBy { it.corpsId }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompareColumn(
            title = "左线",
            snapshot = leftSnap,
            diffMap = diffMap,
            turnIndex = turnIndex,
            maxTurns = maxTurns,
            onStep = { delta ->
                rawIndex = (rawIndex + delta).coerceIn(0, (maxTurns - 1).coerceAtLeast(0))
            },
            modifier = Modifier.weight(1f),
        )
        CompareColumn(
            title = "右线",
            snapshot = rightSnap,
            diffMap = diffMap,
            turnIndex = turnIndex,
            maxTurns = maxTurns,
            onStep = { delta ->
                rawIndex = (rawIndex + delta).coerceIn(0, (maxTurns - 1).coerceAtLeast(0))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompareColumn(
    title: String,
    snapshot: TurnSnapshot?,
    diffMap: Map<String, CorpsDiff>,
    turnIndex: Int,
    maxTurns: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = Color(CrossingTokens.textPrimary),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = if (snapshot != null) "回合 ${snapshot.turn}" else "— / ${maxTurns.coerceAtLeast(1)}",
                style = TextStyle(color = Color(CrossingTokens.brandGold), fontSize = 12.sp),
            )
            Spacer(Modifier.weight(1f))
            ShellButton("上一回合", enabled = turnIndex > 0, onClick = { onStep(-1) })
            Spacer(Modifier.width(6.dp))
            ShellButton("下一回合", enabled = maxTurns > 0 && turnIndex < maxTurns - 1, onClick = { onStep(1) })
        }

        when {
            snapshot == null -> ComparePlaceholderCard()
            snapshot.corps.isEmpty() -> CompareEmptyNote()
            else -> snapshot.corps.take(6).forEach { view ->
                CompareCorpsRow(view, diffMap[view.id])
            }
        }
    }
}

/** 空侧占位卡：绢帛茶玻面 + 细分隔线圆角，文案明示「该分支未推进」，不崩不白屏。 */
@Composable
private fun ComparePlaceholderCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(CrossingTokens.bgSurface).copy(alpha = 0.6f))
            .border(1.dp, Color(CrossingTokens.colorDivider), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            text = "该分支未推进",
            style = TextStyle(
                color = Color(CrossingTokens.textSecondary),
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = "本回合无快照，等待该线推演后再对照",
            style = TextStyle(color = Color(CrossingTokens.textTertiary), fontSize = 11.sp),
        )
    }
}

@Composable
private fun CompareEmptyNote() {
    BasicText(
        text = "本回合无可视军队",
        style = TextStyle(
            color = Color(CrossingTokens.textTertiary),
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
        ),
    )
}

@Composable
private fun CompareCorpsRow(view: CorpsView, diff: CorpsDiff?) {
    val highlighted = diff?.different == true
    // 差异高亮走 spring 淡入（禁固定 delay 硬切）：alpha 随弹簧逼近目标值，快速连点不跳帧。
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = compareSpring<Float>(),
        label = "compare-row-highlight",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(CrossingTokens.brandGold).copy(alpha = 0.35f + 0.65f * highlightAlpha),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = view.name,
            style = TextStyle(
                color = pieceColor(view.role),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
            modifier = Modifier.width(64.dp),
        )
        BasicText(
            text = "兵力 ${view.strength.round1()}｜${view.morale.label()}",
            style = TextStyle(color = Color(CrossingTokens.textSecondary), fontSize = 11.sp),
            modifier = Modifier.weight(1f),
        )
        if (highlighted) {
            BasicText(
                text = diff!!.fields.joinToString("·") { it.label },
                style = TextStyle(color = Color(CrossingTokens.brandGold), fontSize = 11.sp),
                modifier = Modifier.alpha(highlightAlpha),
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
                        color = pieceColor(corps.role),
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

/**
 * 对比行差异高亮弹簧：沿用卡片入场弹簧口径（阻尼 0.80 抑制回弹过冲，刚度 360 落位干脆）。
 * 为什么不用固定 delay：B14 口径要求一切过渡走 spring；固定时长硬切在快速连点步进时会跳帧，
 * 且无法按目标值自然衰减。参数收口在这两处常量，调手感只改这里。
 */
private val CompareRowSpringDamping: Float = ComponentPalette.REVEAL_SPRING_DAMPING
private val CompareRowSpringStiffness: Float = ComponentPalette.REVEAL_SPRING_STIFFNESS

private fun <T> compareSpring(): SpringSpec<T> = spring(
    dampingRatio = CompareRowSpringDamping,
    stiffness = CompareRowSpringStiffness,
)
