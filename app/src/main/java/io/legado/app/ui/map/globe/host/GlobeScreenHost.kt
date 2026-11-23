package io.legado.app.ui.map.globe.host

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.GlobeSpecSurface
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.render.FrameSnapshot
import io.legado.app.ui.map.globe.replay.FactionPalette
import kotlin.math.min

/**
 * 3D 地球仪 Compose 自绘壳（ui/map/globe/host 独占，切片 17 / 18 / 19 / 20）。
 *
 * 本层只绑定 [GlobeHostController] 门面：每帧取回 [FrameSnapshot]，按快照组织星空、
 * 海洋球壳、势力色块、大圆路线、光针、地名标签等自绘 pass；不包裹 MaterialTheme，
 * 颜色全部走 [GlobeTokens] 12 色 token，地貌数据色取 [GlobeSpecSurface]。
 *
 * 边界兜底：离线 / 无缓存 / 无战役时门面仍返回带齐路线、光针、标签、势力的兜底快照，
 * 本壳按快照照常成图，不出现空白球；真实 GLES 渲染后端留后续切片，当前为 Compose
 * Canvas 正交投影的程序化舆图。
 */
@Composable
fun GlobeScreenHost(
    host: GlobeHostController = remember { GlobeHostController() },
    onBack: () -> Unit = {},
) {
    var snapshot by remember {
        mutableStateOf(host.frame(0.016, System.currentTimeMillis()))
    }
    var lastFrameNanos by remember { mutableStateOf(0L) }
    var viewport by remember { mutableStateOf(0f to 0f) }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            color = (GlobeTokens.bgPage and 0xFFFFFFFFL).toInt()
        }
    }

    // 帧节拍：推进门面一帧并取回不可视快照；离开组合时循环随 LaunchedEffect 取消，不白烧 GPU。
    LaunchedEffect(Unit) {
        withFrameNanos { now -> lastFrameNanos = now }
        while (true) {
            withFrameNanos { now ->
                val dtSec = ((now - lastFrameNanos).coerceAtLeast(0L)) / 1_000_000_000.0
                lastFrameNanos = now
                snapshot = host.frame(dtSec.coerceAtMost(0.1), now)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChangedCompat { w, h -> viewport = w to h }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1.0f) {
                        host.zoom(zoom)
                    } else {
                        host.drag(pan.x.toDouble(), pan.y.toDouble())
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { host.dragStart() },
                    onDrag = { _, delta -> host.drag(delta.x.toDouble(), delta.y.toDouble()) },
                    onDragEnd = { host.dragEnd() },
                    onDragCancel = { host.dragEnd() },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val (w, h) = viewport
                        if (w > 0f && h > 0f) {
                            val proj = projOf(host, w, h)
                            val dir = proj.unproject(offset.x, offset.y)
                            if (dir != null) {
                                val (lat, lng) = Geo.latLng(dir)
                                host.flyTo(lat, lng)
                            }
                        }
                    },
                )
            },
    ) {
        GlobeCanvas(host, snapshot, viewport, labelPaint)

        // 顶栏：返回 + 标题 + 兜底档位徽记。
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "←",
                style = TextStyle(
                    color = Color(GlobeTokens.textSecondary),
                    fontSize = 18.sp,
                ),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .pointerInput(Unit) { detectTapGestures { onBack() } },
            )
            BasicText(
                text = "舆图地球仪",
                style = TextStyle(
                    color = Color(GlobeTokens.textPrimary),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = host.degradationLabel(snapshot.degradation),
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }

        // 离线兜底横幅：程序化档位时明示已走种子 / 程序化舆图，纹理就位后自动替换。
        if (snapshot.degradation == DegradationLevel.PROCEDURAL_ONLY) {
            BasicText(
                text = "未联网 · 种子舆图兜底，纹理就位后自动替换",
                style = TextStyle(
                    color = Color(GlobeTokens.textSecondary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
            )
        }

        // 底部回放控制台：战役信息 + 横选 chips + 金线时间轴 + 播放控制。
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            CampaignInfo(host)
            Spacer(Modifier.height(8.dp))
            CampaignChips(host)
            Spacer(Modifier.height(8.dp))
            TimelineRow(host, snapshot)
            Spacer(Modifier.height(8.dp))
            ControlRow(host, snapshot)
            Spacer(Modifier.height(4.dp))
            PowerLegend()
        }
    }
}

/** 画布主层：星空 → 大气辉光 → 海洋球壳 → 势力色块 → 大圆路线 → 光针 → 标签。 */
@Composable
private fun GlobeCanvas(
    host: GlobeHostController,
    snapshot: FrameSnapshot,
    viewport: Pair<Float, Float>,
    labelPaint: Paint,
) {
    val (w, h) = viewport
    Canvas(Modifier.fillMaxSize()) {
        if (w <= 0f || h <= 0f) return@Canvas
        val radiusPx = min(w, h) * 0.32f
        val proj = projOf(host, w, h)

        // 绢帛暗底（墨色 token 压底），星空浮在其上。
        drawRect(Color(GlobeTokens.textPrimary))

        // 程序化星空：固定不动，地球在里面自转（切片 19）。
        val stars = host.controller.scene.starField.stars
        stars.forEach { star ->
            val p = proj.project(star.direction)
            if (p.depth > 0f) {
                val starColor = if (star.gold) {
                    Color(GlobeTokens.brandGold)
                } else {
                    Color(GlobeTokens.bgPage)
                }
                drawCircle(
                    color = starColor.copy(alpha = star.brightness),
                    radius = star.sizePx,
                    center = Offset(p.x, p.y),
                )
            }
        }

        val center = Offset(w / 2f, h / 2f)

        // 外圈极淡辉光（大气外圈，透明度 0.08 口径）。
        drawCircle(
            color = Color(GlobeTokens.colorGlow),
            radius = radiusPx * 1.06f,
            center = center,
            alpha = GlobeConfig.ATMOS_GLOW_ALPHA,
        )
        // 海洋球壳：深蓝底色。
        drawCircle(
            color = surfaceColor(GlobeSpecSurface.OCEAN_DEEP),
            radius = radiusPx,
            center = center,
        )

        // 势力色块：贴球壳半透明多边形（己方金 / 敌方石青 / 盟友铜 / 中立灰褐）。
        snapshot.powerLayer.powers.forEach { power ->
            val corners = power.pts.map { (lat, lng) -> proj.project(Geo.direction(lat, lng)) }
            if (corners.all { it.visible }) {
                val path = Path()
                corners.forEachIndexed { i, c ->
                    if (i == 0) path.moveTo(c.x, c.y) else path.lineTo(c.x, c.y)
                }
                path.close()
                val (r, g, b) = FactionPalette.channels(power.colorHex)
                drawPath(
                    path,
                    Color(r, g, b).copy(alpha = power.alpha()),
                )
            }
        }

        // 大圆行军路线：高圆弧采样连线 + 锋芒 + 流动金粒子。
        val route = snapshot.route
        if (route != null) {
            val worldRadius = GlobeConfig.RADIUS.toDouble()
            val sampled = route.sampledPoints(worldRadius)
            val projected = sampled.map { proj.project(it) }
            val (rr, gg, bb) = FactionPalette.channels(route.colorHex)
            val routeColor = Color(rr, gg, bb)
            if (projected.size >= 2) {
                val path = Path()
                projected.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(
                    path,
                    routeColor.copy(alpha = 0.85f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.5f,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
            // 箭头锋芒：落在当前进度处。
            val head = route.arrowHead(snapshot.routeProgress, worldRadius)
            val headP = proj.project(head.position)
            if (headP.visible) {
                drawCircle(routeColor, radius = 4.5f, center = Offset(headP.x, headP.y))
            }
            // 路线流动粒子。
            route.particles(worldRadius).forEach { particle ->
                val pp = proj.project(particle)
                if (pp.visible) {
                    drawCircle(
                        Color(GlobeTokens.brandGold).copy(alpha = 0.7f),
                        radius = 2f,
                        center = Offset(pp.x, pp.y),
                    )
                }
            }
        }

        // 城池光针：沿法线穿出球面 + 顶部金点（切片 18）。
        snapshot.markers.forEach { marker ->
            val base = proj.project(marker.needleBase(GlobeConfig.RADIUS.toDouble()))
            val tip = proj.project(
                marker.needleTip(GlobeConfig.RADIUS.toDouble()),
            )
            if (base.visible && tip.visible) {
                drawLine(
                    Color(GlobeTokens.brandGold).copy(alpha = 0.9f),
                    start = Offset(base.x, base.y),
                    end = Offset(tip.x, tip.y),
                    strokeWidth = 1.6f,
                )
                drawCircle(
                    Color(GlobeTokens.brandGold),
                    radius = GlobeConfig.NEEDLE_TOP_DOT_RADIUS,
                    center = Offset(tip.x, tip.y),
                )
            }
        }

        // 地名标签：分级淡出，都城恒显（切片 19）。
        snapshot.visibleLabels.forEach { (place, fade) ->
            val p = proj.project(Geo.direction(place.lat, place.lng))
            if (p.visible && fade > 0f) {
                labelPaint.alpha = (fade * 235).toInt()
                labelPaint.color = (GlobeTokens.bgPage and 0xFFFFFFFFL).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    place.name,
                    p.x,
                    p.y - 8f,
                    labelPaint,
                )
            }
        }

        // 云层壳：极淡白环，独立 0.9 倍速旋转由门面状态推进，这里只画兜底薄壳。
        drawCircle(
            Color(GlobeTokens.bgPage).copy(alpha = GlobeConfig.CLOUD_OPACITY * 0.25f),
            radius = radiusPx * 1.012f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f),
        )

        // 太阳方向指示：金盘方位随 UTC 走，画在投影方向边缘。
        val sunP = proj.project(snapshot.sunDir)
        if (sunP.depth > 0f) {
            drawCircle(
                Color(GlobeTokens.brandGold).copy(alpha = 0.55f),
                radius = GlobeConfig.SUN_DISC_DIAMETER,
                center = Offset(sunP.x, sunP.y),
            )
        }
    }
}

/** 战役信息卡：标题 / 年代 / 双方 / 叙述（切片 18 信息卡；无战役时回退不空白）。 */
@Composable
private fun CampaignInfo(host: GlobeHostController) {
    val campaign = host.currentCampaign ?: return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = campaign.title,
                style = TextStyle(
                    color = Color(GlobeTokens.brandGold),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = campaign.era,
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = host.sidesLabel(),
            style = TextStyle(
                color = Color(GlobeTokens.textSecondary),
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = campaign.context,
            style = TextStyle(
                color = Color(GlobeTokens.textTertiary),
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/** 势力图例：己方暖金 / 敌方石青 / 盟友古铜 / 中立灰褐，颜色全走 token。 */
@Composable
private fun PowerLegend() {
    val items = listOf(
        "己方" to GlobeTokens.brandGold,
        "敌方" to GlobeTokens.stoneBlue,
        "盟友" to GlobeTokens.brandCopper,
        "中立" to GlobeTokens.textTertiary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        items.forEach { (label, token) ->
            BasicText(
                text = "●",
                style = TextStyle(color = Color(token), fontSize = 10.sp),
            )
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = label,
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** 战役横选 chips：选中暖金，未选茶褐；超出可横滑。 */
@Composable
private fun CampaignChips(host: GlobeHostController) {
    val currentId = host.currentCampaign?.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        host.campaigns.forEach { campaign ->
            val active = campaign.id == currentId
            BasicText(
                text = campaign.title,
                style = TextStyle(
                    color = Color(if (active) GlobeTokens.brandGold else GlobeTokens.textSecondary),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .pointerInput(campaign.id) {
                        detectTapGestures { host.selectCampaign(campaign.id) }
                    },
            )
        }
    }
}

/** 金线时间轴：进度朱砂圆点 + 阶段篆章铜点；点按时间轴跳到对应回合。 */
@Composable
private fun TimelineRow(host: GlobeHostController, snapshot: FrameSnapshot) {
    val campaign = host.currentCampaign
    val rounds = campaign?.rounds ?: 0
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "回合 ${host.turn.toInt()}/${rounds}",
                style = TextStyle(
                    color = Color(GlobeTokens.brandGold),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                text = host.phaseLabel().orEmpty(),
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .pointerInput(rounds) {
                    detectTapGestures { offset ->
                        if (rounds > 0) {
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            host.seekTurn((ratio * rounds).toDouble())
                        }
                    }
                },
        ) {
            drawLine(
                color = Color(GlobeTokens.brandCopper),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1f,
            )
            val progress = snapshot.routeProgress.coerceIn(0.0, 1.0).toFloat()
            drawCircle(
                Color(GlobeTokens.textAccent),
                radius = 4f,
                center = Offset(size.width * progress, size.height / 2),
            )
        }
    }
}

/** 播放 / 暂停 / 重置 / 自动旋转 控制行，外加路线与素材汇总。 */
@Composable
private fun ControlRow(host: GlobeHostController, snapshot: FrameSnapshot) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = if (host.playing) "⏸" else "▶",
            style = TextStyle(
                color = Color(GlobeTokens.brandGold),
                fontSize = 16.sp,
            ),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures {
                    if (host.playing) host.pause() else host.play()
                }
            },
        )
        Spacer(Modifier.width(16.dp))
        BasicText(
            text = "↺",
            style = TextStyle(
                color = Color(GlobeTokens.textSecondary),
                fontSize = 16.sp,
            ),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { host.resetReplay() }
            },
        )
        Spacer(Modifier.width(16.dp))
        BasicText(
            text = if (host.autoRotate) "自转·开" else "自转·关",
            style = TextStyle(
                color = Color(if (host.autoRotate) GlobeTokens.brandGold else GlobeTokens.textTertiary),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { host.toggleAutoRotate() }
            },
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = host.routeSummary(snapshot),
            style = TextStyle(
                color = Color(GlobeTokens.textTertiary),
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/** 按门面当前姿态构造投影；独立成函数便于单测与双击飞行复用。 */
private fun projOf(host: GlobeHostController, w: Float, h: Float): GlobeProjection {
    val rotator = host.controller.scene.rotator
    return GlobeProjection(
        viewportWidth = w,
        viewportHeight = h,
        radiusPx = min(w, h) * 0.32f,
        yawDeg = rotator.yawDeg,
        pitchDeg = rotator.pitchDeg,
    )
}

/** onSizeChanged 的 Compose 兼容包装，避免本文件直接依赖 Modifier 扩展版本差异。 */
private fun Modifier.onSizeChangedCompat(onSize: (Float, Float) -> Unit): Modifier =
    this.then(
        Modifier.onSizeChanged { size ->
            onSize(size.width.toFloat(), size.height.toFloat())
        },
    )

/** 地貌数据色（Int RGB）→ Compose Color；12 色 token 之外的地貌色只走门面 spec 派生。 */
private fun surfaceColor(rgb: Int, alpha: Float = 1f): Color {
    val r = (rgb ushr 16) and 0xFF
    val g = (rgb ushr 8) and 0xFF
    val b = rgb and 0xFF
    return Color(r, g, b).copy(alpha = alpha)
}
