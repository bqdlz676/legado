package io.legado.app.ui.map.crossing.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.hypot

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

    // 自动推演：单一协程逐回合驱动（不为每步另起 sleep，避免长局 N 个协程堆积）。
    // 节拍由纯函数 [beatDelayMs] 按事件档位 + 回合进度计算；状态先推进，弹簧动画跟随新状态，
    // 不挂「动画播完再下一步」的栅栏——快速连点/快进时弹簧直接改追新目标，不会错位。
    LaunchedEffect(autoPlay, host) {
        while (autoPlay) {
            snapshot = host.step(overrides)
            overrides = emptyMap()
            if (snapshot.status != io.legado.app.ui.map.crossing.model.BattleStatus.ONGOING) {
                autoPlay = false
                break
            }
            delay(beatDelayMs(snapshot.events, snapshot.turn))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(host) {
                detectTapGestures { /* 预留棋盘点选：点棋子展开意图面板 */ }
            },
    ) {
        // 回合切换过渡：Crossfade 以透明度跟随回合状态做淡入淡出（动画跟随状态，幂等）。
        // 旧快照在弹簧期间短暂保留，新快照落位后即替换；不等待动画结束再推进，故不会出现
        // 「动画未结束就进入下一回合」的错位。弹簧角色见 [SpringRole.TURN_CROSSFADE]。
        Crossfade(
            targetState = snapshot.turn,
            animationSpec = springFor(SpringRole.TURN_CROSSFADE),
            label = "turn-crossfade",
        ) { _ ->
            MapCanvas(
                host = host,
                snapshot = snapshot,
                modifier = Modifier.fillMaxSize(),
            )
        }

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

        // 全域盲区兜底：军队在册但全部未观测 → 画布不留白，给一条可读提示条。
        if (snapshot.corps.isNotEmpty() && snapshot.corps.all { !it.observed }) {
            BasicText(
                text = "全域盲区：当面之敌均未侦察到，方位不明",
                style = TextStyle(
                    color = Color(CrossingTokens.textSecondary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp),
            )
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
                onPickIntent = { id, intent -> overrides = overrides + (id to intent) },
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
        animationSpec = springFor(SpringRole.REVEAL_HIGHLIGHT),
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

    // 盲区显隐弹簧：任一部队进入 / 离开盲区状态时，透明度随 REVEAL_HIGHLIGHT 弹簧逼近目标值；
    // 禁固定 delay 硬切（与对比行高亮同一口径，弹簧角色取自既有 SpringRole 表，不另设数值）。
    val blindPresent = snapshot.corps.any { isBlindCorps(it) }
    val blindAlpha by animateFloatAsState(
        targetValue = if (blindPresent) 1f else 0f,
        animationSpec = springFor(SpringRole.REVEAL_HIGHLIGHT),
        label = "blind-zone-reveal",
    )

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

        // 行军线路：己方暖金、盟友古铜、敌方灰青。未观测敌军路线引擎已清空；这里再拦一道，
        // 防御性跳过 NaN 路点，避免盲区路线被投影到左上角格。
        snapshot.corps.filter { it.alive && it.observed }.forEach { corps ->
            if (corps.route.size >= 2) {
                val color = routeColor(corps.role)
                val pts = corps.route.mapNotNull { routeGeo ->
                    if (routeGeo.lat.isNaN() || routeGeo.lng.isNaN()) null
                    else geoToOffset(routeGeo, projection, size)
                }
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

        // 棋子：势力底色块 + 角色色圆子；溃散 / 投降压暗。盲区单位不绘子，改绘盲区圈 + 文案。
        snapshot.corps.forEach { corps ->
            if (isBlindCorps(corps)) {
                drawBlindZone(
                    corps = corps,
                    host = host,
                    projection = projection,
                    step = step,
                    alpha = blindAlpha,
                )
                return@forEach
            }
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
                style = Stroke(width = 1f),
            )
        }

    }
}

// ── B9 情报盲区：门控判定 / 盲区圈 / 文案 ──────────────────────

/** 盲区判定：未观测，或主坐标为 NaN 占位（从未侦察）。NaN 进投影会被 toInt 钳到左上角格，必须先拦。 */
private fun isBlindCorps(corps: CorpsView): Boolean =
    !corps.observed || corps.lat.isNaN() || corps.lng.isNaN()

/** 盲区圆心：优先取上次已知上报坐标；无旧报告（从未侦察）返回 null，只出文案不画圈。 */
private fun blindCenter(corps: CorpsView, projection: GeoProjection, size: Size): Offset? {
    val lat = corps.reportLat
    val lng = corps.reportLng
    if (lat == null || lng == null || lat.isNaN() || lng.isNaN()) return null
    return geoToOffset(Geo(lat, lng), projection, size)
}

/** 盲区文案缺省占位：引擎未下发 blindZoneLabel 时的中文兜底，不留空白。 */
private const val DEFAULT_BLIND_ZONE_LABEL = "情报空白区：方位不明"

/** 画布盲区文案最大字数：原生 Canvas 不换行，超长截断避免压出圈外。 */
private const val BLIND_LABEL_MAX_CHARS = 12

/**
 * 盲区绘制：暖金虚线圈（半径 = 记录级模糊半径）+ 毛玻璃弱化填充 + 盲区文案。
 * 半径 null / ≤0 时只出文案；负数夹紧为 0（见 [drawBlindZone] 内注释）。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlindZone(
    corps: CorpsView,
    host: CampaignController,
    projection: GeoProjection,
    step: Float,
    alpha: Float,
) {
    val center = blindCenter(corps, projection, size)
    // 半径：模型字段优先，缺省回读引擎内部出口；都拿不到按 0 处理（只文案）。
    val rawRadius = corps.reportRadiusCells ?: host.reportRadiusCells(corps.id) ?: 0
    // 负数夹紧为 0：半径语义是非负格数；负值多为旧档 / 异常下发，画负半径会反向缩到圆心，
    // 不如退化为纯文案提示，避免在画布上画出无意义的反向圈。
    val radiusCells = rawRadius.coerceAtLeast(0)
    val label = corps.blindZoneLabel
        ?: host.blindZoneLabel(corps.id)
        ?: DEFAULT_BLIND_ZONE_LABEL

    if (center != null && radiusCells > 0) {
        val radius = radiusCells * step
        // 毛玻璃弱化填充：绢帛面低透明度压一层，不遮挡底图地形与河线。
        drawCircle(
            color = Color(CrossingTokens.bgSurface).copy(alpha = 0.25f * alpha),
            radius = radius,
            center = center,
        )
        // 暖金虚线环：周长均分 16 段，奇偶留缺口；显隐统一由 blindAlpha 弹簧驱动。
        val dashCount = 16
        val gold = Color(CrossingTokens.brandGold).copy(alpha = 0.55f * alpha)
        for (i in 0 until dashCount) {
            if (i % 2 != 0) continue
            drawArc(
                color = gold,
                startAngle = i * 360f / dashCount,
                sweepAngle = 360f / dashCount * 0.8f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 1.4f),
            )
        }
    }

    if (center != null) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SERIF
            textAlign = Paint.Align.CENTER
            textSize = 11.sp.toPx()
            color = Color(CrossingTokens.textSecondary).copy(alpha = 0.85f * alpha).toArgb()
        }
        val text = label.take(BLIND_LABEL_MAX_CHARS)
        val labelY = center.y + (if (radiusCells > 0) radiusCells * step else 0f) + 14.sp.toPx()
        drawContext.canvas.nativeCanvas.drawText(text, center.x, labelY, paint)
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
    onPickIntent: (String, Intent) -> Unit,
) {
    // 空/缺失兜底：军队列表为空或全部溃散 → 明示「该回合无可选意图」，不崩不空白。
    val aliveCorps = snapshot.corps.filter { it.alive }
    if (aliveCorps.isEmpty()) {
        BasicText(
            text = "该回合无可选意图",
            style = TextStyle(
                color = Color(CrossingTokens.textTertiary),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
            modifier = Modifier.semantics {
                contentDescription = "该回合无可选意图：军队列表为空或均已溃散"
            },
        )
        return
    }

    var paletteFor by remember { mutableStateOf<String?>(null) }
    var dragVector by remember { mutableStateOf<Offset?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        aliveCorps.take(6).forEach { corps ->
            val override = overrides[corps.id]
            val current = override ?: corps.intent
            val spec = IntentGestureMap.entries[current]
            val dialPreview = dragVector?.let { dialIntent(it.x, it.y) }
            // 意图改写高亮走 spring（复用对比行 reveal 口径）；禁固定 delay 硬切，连拖不跳帧。
            val intentAlpha by animateFloatAsState(
                targetValue = if (override != null) 1f else 0f,
                animationSpec = springFor(SpringRole.INTENT_OVERRIDE),
                label = "intent-override-alpha",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = buildString {
                            append("${corps.name}，当前意图${current.label()}。")
                            if (spec != null) append("上下左右拖拽调整方向，长按展开意图面板。")
                        }
                    }
                    // 长按通道：静止按住展开意图面板（不改写意图）。
                    .pointerInput(corps.id) {
                        detectTapGestures(
                            onLongPress = { paletteFor = corps.id },
                        )
                    }
                    // 拖拽通道：累积向量，抬手时交纯函数按八向罗盘归位。
                    .pointerInput(corps.id) {
                        detectDragGestures(
                            onDragStart = { dragVector = Offset.Zero },
                            onDragEnd = {
                                dragVector?.let { v ->
                                    when (val r = resolveGesture(GestureInput(v.x, v.y, 0L, 1))) {
                                        is GestureResult.Pick -> onPickIntent(corps.id, r.intent)
                                        else -> Unit
                                    }
                                }
                                dragVector = null
                            },
                            onDragCancel = { dragVector = null },
                            onDrag = { change, amount ->
                                change.consume()
                                // 预览向量钳到 maxDragPx：超长拖不放大预览，判定仍只看角度。
                                val next = (dragVector ?: Offset.Zero) + amount
                                val cap = DefaultGestureThresholds.maxDragPx
                                dragVector = Offset(
                                    next.x.coerceIn(-cap, cap),
                                    next.y.coerceIn(-cap, cap),
                                )
                            },
                        )
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
                if (!corps.observed) {
                    // 盲区单位不列敌方意图（决策信息不随雾透传），改挂盲区占位文案。
                    BasicText(
                        text = corps.blindZoneLabel ?: DEFAULT_BLIND_ZONE_LABEL,
                        style = TextStyle(
                            color = Color(CrossingTokens.textTertiary),
                            fontSize = 10.sp,
                        ),
                    )
                } else {
                    BasicText(
                        text = (dialPreview ?: current).label(),
                        style = TextStyle(
                            color = Color(CrossingTokens.brandGold),
                            fontSize = 11.sp,
                        ),
                        modifier = Modifier.alpha(0.55f + 0.45f * intentAlpha),
                    )
                }
            }
        }

        // 长按展开的意图面板：12 意图命中区全列，点选直改，不再走点按循环。
        paletteFor?.let { corpsId ->
            val selected = overrides[corpsId]
                ?: aliveCorps.firstOrNull { it.id == corpsId }?.intent
            IntentPalette(
                selected = selected,
                onPick = { intent ->
                    onPickIntent(corpsId, intent)
                    paletteFor = null
                },
                onDismiss = { paletteFor = null },
            )
        }
    }
}

/**
 * 意图面板：12 枚命中区两排陈列；每枚带一句话无障碍语义，点选经纯函数校验区码后落定。
 */
@Composable
private fun IntentPalette(
    selected: Intent?,
    onPick: (Intent) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CrossingTokens.bgSurface).copy(alpha = 0.6f))
            .border(1.dp, Color(CrossingTokens.colorDivider), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IntentGestureMap.entries.keys.chunked(6).forEach { rowIntents ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowIntents.forEach { intent ->
                    val spec = IntentGestureMap.entries.getValue(intent)
                    val active = intent == selected
                    val chipAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = springFor(SpringRole.PALETTE_CHIP),
                        label = "intent-chip-alpha",
                    )
                    BasicText(
                        text = intent.label(),
                        style = TextStyle(
                            color = if (active) {
                                Color(CrossingTokens.brandGold)
                            } else {
                                Color(CrossingTokens.textSecondary)
                            },
                            fontSize = 11.sp,
                        ),
                        modifier = Modifier
                            .semantics { contentDescription = spec.semantics }
                            .background(
                                Color(CrossingTokens.brandGold).copy(alpha = 0.10f + 0.25f * chipAlpha),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .pointerInput(intent) {
                                detectTapGestures {
                                    val zone = IntentGestureMap.zoneLabel(intent)
                                    when (val r = resolveGesture(GestureInput(0f, 0f, 0L, 1, zone))) {
                                        is GestureResult.Pick -> onPick(r.intent)
                                        GestureResult.Ignored -> onDismiss()
                                        GestureResult.OpenPalette -> Unit
                                    }
                                }
                            },
                    )
                }
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
    // NaN 哨兵：盲区占位坐标禁止进投影——toInt 会把 NaN 钳到 cell(0,0) 左上角。
    // 调用方须先用 isBlindCorps / reportLat 判 NaN；此处再兜一层，返回离屏哨兵，不画到角落。
    if (geo.lat.isNaN() || geo.lng.isNaN()) return Offset(-10_000f, -10_000f)
    val cell = projection.toCell(geo)
    val n = projection.gridN
    val step = size.minDimension / n
    return Offset((cell.col + 0.5f) * step, (cell.row + 0.5f) * step)
}

// ── B11 意图手势映射与识别（纯 JVM 层，表驱动单测覆盖） ──────────

/** 手势通道：方向拖拽罗盘 / 长按静置 / 面板命中区直选。 */
enum class IntentGestureChannel { DRAG_DIAL, LONG_PRESS, TAP_ZONE }

/**
 * 单条意图的手势契约。
 * @property hitZone 主命中区描述（罗盘方位 / 长按行 / 面板条目）
 * @property chipLabel 面板短标签，同时作为命中区码供纯函数校验
 * @property rationale 为什么用这个手势（选型理由，写给接手人看）
 * @property semantics 无障碍一句话语义（动作 + 方向，不是符号缩写）
 */
internal data class IntentGestureSpec(
    val channel: IntentGestureChannel,
    val hitZone: String,
    val chipLabel: String,
    val rationale: String,
    val semantics: String,
)

/**
 * 12 意图 → 手势/命中区 集中映射表。
 * 为什么集中一处：新增意图只改这张表；交互文案、无障碍描述、单测都从这里取，
 * 避免手势口径散落在多个 Composable 里各写一套。
 */
internal object IntentGestureMap {
    val entries: Map<Intent, IntentGestureSpec> = mapOf(
        Intent.ADVANCE to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·上",
            chipLabel = "进军",
            rationale = "进军是向前压上，方向语义为正北，向上拖拽最符合军事方位直觉。",
            semantics = "令该军向北正面进军",
        ),
        Intent.RAPID to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·右上",
            chipLabel = "急行军",
            rationale = "急行军强调斜向全速穿插，归入东北象限，与正面进军区分开。",
            semantics = "令该军向东北方向快速急行军",
        ),
        Intent.SUPPORT to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·右",
            chipLabel = "支援",
            rationale = "支援是横向靠拢友军，纯水平位移，归入正东。",
            semantics = "令该军向东侧友军靠拢支援",
        ),
        Intent.ROUT to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·右下",
            chipLabel = "溃逃",
            rationale = "溃逃是脱离接触向下脱出，归入东南象限；与正南撤退的区别在于斜向离散。",
            semantics = "令该军向东南方向脱离战场溃逃",
        ),
        Intent.RETREAT to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·下",
            chipLabel = "撤退",
            rationale = "撤退是向后收兵，方向语义为正南，向下拖拽。",
            semantics = "令该军向南撤回后方",
        ),
        Intent.BREAKOUT to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·左下",
            chipLabel = "突围",
            rationale = "突围是斜向撕开包围，归入西南象限，朝敌最弱方向脱出。",
            semantics = "令该军向西南方向突围",
        ),
        Intent.FLANK to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·左",
            chipLabel = "包抄",
            rationale = "包抄是横向迂回至敌侧后，纯水平位移，归入正西。",
            semantics = "令该军向西翼迂回包抄",
        ),
        Intent.SEIZE to IntentGestureSpec(
            channel = IntentGestureChannel.DRAG_DIAL,
            hitZone = "罗盘·左上",
            chipLabel = "夺隘",
            rationale = "夺隘是斜向上抢占制高点关隘，归入西北象限。",
            semantics = "令该军向西北方向抢占关隘",
        ),
        Intent.HOLD to IntentGestureSpec(
            channel = IntentGestureChannel.LONG_PRESS,
            hitZone = "长按棋子行",
            chipLabel = "据守",
            rationale = "据守是原地不动，手指按住静止的动作语义与据守一一对应，无需方向。",
            semantics = "令该军原地据守防御",
        ),
        Intent.AMBUSH to IntentGestureSpec(
            channel = IntentGestureChannel.TAP_ZONE,
            hitZone = "意图面板·设伏",
            chipLabel = "设伏",
            rationale = "设伏依赖关隘等特定地形条件，不是纯方向动作，放面板命中区由用户确认。",
            semantics = "令该军潜伏关隘设伏",
        ),
        Intent.FIGHT to IntentGestureSpec(
            channel = IntentGestureChannel.TAP_ZONE,
            hitZone = "意图面板·应战",
            chipLabel = "应战",
            rationale = "应战是被动接战、无位移方向，罗盘拖拽会误导方位，面板直选最直接。",
            semantics = "令该军就地迎战相邻敌军",
        ),
        Intent.CONTINUE to IntentGestureSpec(
            channel = IntentGestureChannel.TAP_ZONE,
            hitZone = "意图面板·续进",
            chipLabel = "续进",
            rationale = "续进是维持原路线，本身不产生新方向，归入面板命中区。",
            semantics = "令该军沿原路继续推进",
        ),
    )

    /** 面板命中区码 → 意图；未知区码返回 null，由解析器判为忽略。 */
    private val zoneIntents: Map<String, Intent> =
        // associate 是 Iterable 扩展，Map 本身不可迭代；先取 entries 视图（Set<Entry>）再关联，
        // 键为面板短标签 chipLabel，值为对应意图，供命中区码反查。
        entries.entries.associate { (intent, spec) -> spec.chipLabel to intent }

    fun zoneIntent(zone: String): Intent? = zoneIntents[zone]

    fun zoneLabel(intent: Intent): String = entries.getValue(intent).chipLabel
}

/**
 * 手势冲突阈值（集中一处；调手感只改这里，禁止散落在 Composable 里写死）。
 * 优先级：双指忽略 > 面板命中区直选 > 长按静置 > 方向拖拽 > 误触忽略。
 */
internal data class GestureThresholds(
    /** 位移低于此像素视为误触/点按，不产生拖拽意图。 */
    val minDragPx: Float,
    /** 拖拽超过此像素不归零，仍按最近八向归位（超长拖不另设越界意图）。 */
    val maxDragPx: Float,
    /** 静止按住达到此毫秒数 → 长按通道，展开意图面板。 */
    val longPressMs: Long,
    /** 长按判定后位移超过此半径 → 长按作废，转入拖拽通道。 */
    val longPressCancelPx: Float,
)

internal val DefaultGestureThresholds = GestureThresholds(
    minDragPx = 24f,
    maxDragPx = 160f,
    longPressMs = 350L,
    longPressCancelPx = 18f,
)

/** 一次手势的原始采样（屏幕像素；dx 右正、dy 下正；elapsedMs 为按住时长）。 */
internal data class GestureInput(
    val dx: Float,
    val dy: Float,
    val elapsedMs: Long,
    val pointerCount: Int,
    /** 面板命中区码；非空时走 TAP_ZONE 通道，不再解析向量。 */
    val paletteZone: String? = null,
)

internal sealed interface GestureResult {
    /** 选定意图。 */
    data class Pick(val intent: Intent) : GestureResult
    /** 长按静置：展开意图面板，本次不改写意图。 */
    object OpenPalette : GestureResult
    /** 误触 / 越界 / 双指：忽略，意图保持不变。 */
    object Ignored : GestureResult
}

/** 手势向量 → 意图 的纯函数入口；无 Compose 依赖，可直接表驱动单测。 */
internal fun resolveGesture(
    input: GestureInput,
    thresholds: GestureThresholds = DefaultGestureThresholds,
): GestureResult {
    // 双指手势预留给地图缩放/平移：面板单指交互不抢占。
    if (input.pointerCount > 1) return GestureResult.Ignored
    // 面板命中区直选：唯一不经过手势向量的通道；未知区码按忽略处理。
    input.paletteZone?.let { zone ->
        val intent = IntentGestureMap.zoneIntent(zone) ?: return GestureResult.Ignored
        return GestureResult.Pick(intent)
    }
    val distance = hypot(input.dx, input.dy)
    // 先判长按（静止且够久）；位移一旦超出取消半径即作废长按，避免按住后微调被误判成长按。
    if (input.elapsedMs >= thresholds.longPressMs && distance <= thresholds.longPressCancelPx) {
        return GestureResult.OpenPalette
    }
    if (distance < thresholds.minDragPx) return GestureResult.Ignored
    return dialIntent(input.dx, input.dy, thresholds)?.let { GestureResult.Pick(it) }
        ?: GestureResult.Ignored
}

/** 八向罗盘：按角度归最近象限；距离过短返回 null（误触），过长仍归最近象限。 */
internal fun dialIntent(
    dx: Float,
    dy: Float,
    thresholds: GestureThresholds = DefaultGestureThresholds,
): Intent? {
    val distance = hypot(dx, dy)
    if (distance < thresholds.minDragPx) return null
    // 超长拖（distance > maxDragPx）：不归零、不产生越界意图，仍按角度归最近八向。
    val angle = compassAngleDeg(dx, dy)
    return DIAL_OCTANTS.minByOrNull { circularAngleDiff(it.centerDeg, angle) }?.intent
}

/** 屏幕向量 → 罗盘角度：0°=正上，顺时针 90°=正右，180°=正下，270°=正左。 */
internal fun compassAngleDeg(dx: Float, dy: Float): Float {
    val raw = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    return ((raw % 360f) + 360f) % 360f
}

private fun circularAngleDiff(a: Float, b: Float): Float {
    val d = ((a - b) % 360f + 360f) % 360f
    return if (d > 180f) 360f - d else d
}

private data class DialOctant(val centerDeg: Float, val intent: Intent)

private val DIAL_OCTANTS = listOf(
    DialOctant(0f, Intent.ADVANCE),
    DialOctant(45f, Intent.RAPID),
    DialOctant(90f, Intent.SUPPORT),
    DialOctant(135f, Intent.ROUT),
    DialOctant(180f, Intent.RETREAT),
    DialOctant(225f, Intent.BREAKOUT),
    DialOctant(270f, Intent.FLANK),
    DialOctant(315f, Intent.SEIZE),
)

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

// ── B14 推演节拍：纯 JVM 计算，表驱动单测覆盖 ──────────────────

/**
 * 节拍档位。一回合内多类事件并存时按 [BeatKind.COMBAT] > [BeatKind.EVENT] >
 * [BeatKind.MARCH] > [BeatKind.QUIET] 取最高档（战斗信息密度最高，节拍最长）。
 */
internal enum class BeatKind { COMBAT, EVENT, MARCH, QUIET }

/**
 * 各档基础节拍（毫秒）。取值依据与适用条件：
 * - COMBAT 620：遭遇/溃散/投降需要让用户看清伤亡与士气变化，信息密度最高，节拍最长；
 * - EVENT 540：脚本事件（士气/补给/兵力变动）有文案阅读需求，居中一档；
 * - MARCH 460：纯位移/转向回合信息密度低，短一档即可；
 * - QUIET 320：无事件回合快速掠过，不拖节奏。
 * 适用条件：手机竖屏单壳手动/自动推演；快进与暂停由 autoPlay 开关控制，不改本节拍表。
 */
internal val BEAT_BASE_MS: Map<BeatKind, Long> = mapOf(
    BeatKind.COMBAT to 620L,
    BeatKind.EVENT to 540L,
    BeatKind.MARCH to 460L,
    BeatKind.QUIET to 320L,
)

/** 长局压缩起点：回合 ≥ 此值进入压缩档（长平/赤水 60 回合场景），避免长局逐个长 delay 堆积。 */
internal const val LONG_CAMPAIGN_TURN: Int = 50

/** 长局节拍地板（毫秒）：再长也不低于此值，每步仍可辨识，不做瞬切。 */
internal const val BATCH_BEAT_FLOOR_MS: Long = 180L

/** 超过起点后每回合的压缩斜率（0~1）：50 回合后节拍按比例向地板收拢，固定帧率节奏不堆积。 */
internal const val LONG_CAMPAIGN_COMPRESS: Double = 0.012

/** 战斗关键词：命中即按战斗档节拍（遭遇/伤亡/溃散/失守等结算）。 */
private val COMBAT_KEYWORDS = listOf(
    "遭遇", "伏击", "折损", "伤亡", "溃散", "投降", "被俘", "覆没",
    "失守", "殉国", "破城", "崩溃", "火攻", "兵败", "解散", "脱离水阵",
)

/** 脚本事件关键词：事件「」前缀或士气/补给/兵力变动类结算。 */
private val EVENT_KEYWORDS = listOf("事件「", "士气变为", "补给断绝", "兵力变动", "未生效")

/** 行军/转向关键词：位移、改变意图、目标点更新。 */
private val MARCH_KEYWORDS = listOf("转入", "包抄", "追击", "渡河", "目标点更新", "意图调整", "设伏")

/** 事件文案 → 节拍档位纯函数；空事件列表返回 [BeatKind.QUIET]。 */
internal fun classifyBeat(events: List<String>): BeatKind {
    if (events.isEmpty()) return BeatKind.QUIET
    val joined = events.joinToString(" ")
    if (COMBAT_KEYWORDS.any { joined.contains(it) }) return BeatKind.COMBAT
    if (EVENT_KEYWORDS.any { joined.contains(it) }) return BeatKind.EVENT
    if (MARCH_KEYWORDS.any { joined.contains(it) }) return BeatKind.MARCH
    return BeatKind.QUIET
}

/**
 * 回合节拍纯函数：先按事件档位取基础节拍；长局（turn ≥ [LONG_CAMPAIGN_TURN]）按斜率向
 * [BATCH_BEAT_FLOOR_MS] 压缩。回合 0（首回合）与短局不压缩；战役总回合上限在引擎层截断，
 * 节拍函数不重复接收 duration。
 */
internal fun beatDelayMs(events: List<String>, turn: Int): Long {
    val base = BEAT_BASE_MS.getValue(classifyBeat(events))
    if (turn <= 0 || turn < LONG_CAMPAIGN_TURN) return base
    val over = turn - LONG_CAMPAIGN_TURN
    val compressed = base * (1.0 - LONG_CAMPAIGN_COMPRESS * over)
    return compressed.toLong().coerceAtLeast(BATCH_BEAT_FLOOR_MS)
}

// ── B14 过渡弹簧：参数集中一处，角色化选型，禁止散落 magic number ──

/**
 * 过渡弹簧角色。阻尼/刚度全部取自 [ComponentPalette] 既有口径；[rationale] 写明该角色
 * 为什么用这组阻尼/刚度，调手感只改本表，Composable 里不再写死数值。
 */
internal enum class SpringRole(
    val dampingRatio: Float,
    val stiffness: Float,
    val rationale: String,
) {
    /** 对比行差异高亮：高阻尼抑制 overshoot，高刚度快速落位，连点步进不晃。 */
    REVEAL_HIGHLIGHT(
        ComponentPalette.REVEAL_SPRING_DAMPING,
        ComponentPalette.REVEAL_SPRING_STIFFNESS,
        "差异高亮：阻尼 0.80 抑制回弹过冲，刚度 360 落位干脆，连点不晃。",
    ),
    /** 意图改写高亮：同 reveal 口径，状态切换要求稳，拖拽松手后不弹动。 */
    INTENT_OVERRIDE(
        ComponentPalette.REVEAL_SPRING_DAMPING,
        ComponentPalette.REVEAL_SPRING_STIFFNESS,
        "意图改写：同高亮口径，稳落位，避免拖拽松手后弹动。",
    ),
    /** 意图面板选中 chip：同 reveal 口径，高频点选要干脆。 */
    PALETTE_CHIP(
        ComponentPalette.REVEAL_SPRING_DAMPING,
        ComponentPalette.REVEAL_SPRING_STIFFNESS,
        "面板选中态：高频点选，干脆落位。",
    ),
    /** 回合切换淡入：气泡 overshoot 口径，跨内容切换允许轻微回弹，保留呼吸感。 */
    TURN_CROSSFADE(
        ComponentPalette.BUBBLE_SPRING_DAMPING,
        ComponentPalette.BUBBLE_SPRING_STIFFNESS,
        "回合淡入：允许轻微 overshoot，内容切换有呼吸感。",
    ),
}

/** 按角色取弹簧规格纯函数；泛型保留给 Float/Offset 等动画目标类型。 */
internal fun <T> springFor(role: SpringRole): SpringSpec<T> =
    spring(dampingRatio = role.dampingRatio, stiffness = role.stiffness)
