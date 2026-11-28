package io.legado.app.ui.map.globe.host

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.data.StarStore
import io.legado.app.ui.map.globe.astro.data.StarStoreProvider
import io.legado.app.ui.map.globe.astro.ui.CsvSkyNameProvider
import io.legado.app.ui.map.globe.astro.ui.SkySheet
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.render.FrameSnapshot

/**
 * 3D 地球仪 Compose 自绘壳（ui/map/globe/host 独占，切片 17 / 18 / 19 / 20）。
 *
 * 本层只绑定 [GlobeHostController] 门面：每帧取回 [FrameSnapshot]，按快照组织星空、
 * 海洋球壳、势力色块、大圆路线、光针、地名标签等自绘 pass；不包裹 MaterialTheme，
 * 颜色全部走 [GlobeTokens] 12 色 token，地貌数据色取门面 spec 派生。
 *
 * D45 拆分：回放控制台五组件下沉 [ReplayConsole]，地球仪态 pass 下沉 [EarthGlobePass]；
 * 本壳只留状态提升、帧节拍、三棵手势 pointerInput、顶栏、兜底横幅与星空 Sheet 挂载。
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
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    var starStore by remember { mutableStateOf(StarStoreProvider.get(context.assets)) }
    val skyHandler = remember { SkyInputHandler(host.controller, SkyCanvasRenderer) }
    SkyCanvasRenderer.retryAction = { StarStoreProvider.invalidate(); starStore = StarStoreProvider.get(context.assets) }

    // 帧节拍：推进门面一帧并取回不可视快照；离开组合时循环随 LaunchedEffect 取消，不白烧 GPU。
    LaunchedEffect(Unit) {
        withFrameNanos { now -> lastFrameNanos = now }
        while (true) {
            withFrameNanos { now ->
                val dtSec = ((now - lastFrameNanos).coerceAtLeast(0L)) / 1_000_000_000.0
                lastFrameNanos = now
                // now 是帧纳秒（since boot, ns），只用于 dtSec 差分；太阳方位必须按真实 UTC 毫秒
                // epoch 取时，纳秒值不得再作为 utcMillis 传入门面（切片 19：太阳位置按真实 UTC 计算）。
                snapshot = host.frame(dtSec.coerceAtMost(0.1), System.currentTimeMillis())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChangedCompat { w, h ->
                viewport = w to h
                // D39 反向链路：把视图尺寸注入手势处理器，地球首击反投影反查才有 viewport（未双击也不为 0）。
                skyHandler.setViewport(w, h)
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    skyHandler.onZoom(zoom)
                    if (zoom == 1.0f) skyHandler.onDrag(pan.x, pan.y)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> skyHandler.onDragStart(offset.x, offset.y) },
                    onDrag = { _, delta -> skyHandler.onDrag(delta.x, delta.y) },
                    onDragEnd = { skyHandler.onDragEnd() },
                    onDragCancel = { skyHandler.onDragEnd() },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset -> skyHandler.onTap(offset.x, offset.y) },
                    onLongPress = { offset -> skyHandler.onLongPress(offset.x, offset.y) },
                    onDoubleTap = { offset -> skyHandler.onDoubleTap(offset.x, offset.y, viewport) },
                    onPress = { offset ->
                        skyHandler.onPressStart(offset.x, offset.y); awaitRelease(); skyHandler.onPressEnd()
                    },
                )
            },
    ) {
        GlobeCanvas(host, snapshot, viewport, labelPaint, starStore, density)

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

        // 底部回放控制台：战役信息 + 横选 chips + 金线时间轴 + 播放控制 + 势力图例。
        ReplayConsole(
            host,
            snapshot,
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        )

        // 星空 Sheet（§九.3）：仅选中星时出现；关闭只清 selectedId，相机与姿态一字不动（§十四.12）。
        val skyNames = remember {
            runCatching {
                CsvSkyNameProvider(
                    context.assets.open("astro/star_names.csv").bufferedReader().use { it.readLines() },
                )
            }.getOrNull()
        }
        snapshot.sky?.selectedId?.let { id ->
            SkySheet(
                selectedId = id,
                store = starStore,
                nameProvider = skyNames,
                onDismiss = { host.controller.selectStar(null) },
            )
        }
    }
}

/** 画布主层编排：星空 pass 先行（renderer 自持），地球仪态 pass 交 [EarthGlobePass]。 */
@Composable
private fun GlobeCanvas(
    host: GlobeHostController,
    snapshot: FrameSnapshot,
    viewport: Pair<Float, Float>,
    labelPaint: Paint,
    store: StarStore,
    density: Float,
) {
    val (w, h) = viewport
    Canvas(Modifier.fillMaxSize()) {
        if (w <= 0f || h <= 0f) return@Canvas
        SkyCanvasRenderer.offline = !host.controller.scene.fallback.online
        SkyCanvasRenderer.draw(drawContext.canvas.nativeCanvas, snapshot, w, h, labelPaint, store = store, density = density)
        EarthGlobePass(this, host, snapshot, viewport, labelPaint, density)
    }
}

/** onSizeChanged 的 Compose 兼容包装，避免本文件直接依赖 Modifier 扩展版本差异。 */
private fun Modifier.onSizeChangedCompat(onSize: (Float, Float) -> Unit): Modifier =
    this.then(
        Modifier.onSizeChanged { size ->
            onSize(size.width.toFloat(), size.height.toFloat())
        },
    )
