package io.legado.app.ci.screenshot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.content.res.AssetManager
import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.StarStore
import io.legado.app.ui.map.globe.astro.data.StarStoreProvider
import io.legado.app.ui.map.globe.astro.proj.StarProjector
import io.legado.app.ui.map.globe.host.SkyCanvasRenderer
import io.legado.app.ui.map.globe.offline.AssetStatus
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.offline.FallbackKind
import io.legado.app.ui.map.globe.offline.OfflineFallback
import io.legado.app.ui.map.globe.offline.TextureKind
import io.legado.app.ui.map.globe.render.FrameSnapshot
import java.io.File
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.min

/**
 * 星空八态截图夹具（判据 11 · 规格 §七）。
 *
 * 为什么单独成文件：八态用例共享同一套控制器装配 / 渲染器状态复位 / 帧视图包装；
 * 测试类只声明每态的快照覆盖与渲染器状态注入，不在每个用例里重复搭场景。
 *
 * 口径：帧必须走真实渲染路径 [SkyCanvasRenderer.draw]；本文件只构造测试侧快照实例与
 * 包装 View，不改 `app/src/main` 下任何生产签名。渲染器跨态状态面在每态出帧前复位，
 * 避免上一用例的 hoverId / pressId / offline 泄漏到下一帧。
 */
internal object SkyStateFixtures {

    /** 固定观测时刻（2026-11-28 00:00:00 UTC）：避免闪烁 / 流星相位随墙钟漂移，保证帧可复现。 */
    const val FIXED_UTC_MILLIS: Long = 1764297600000L

    /** 设备标称帧宽高（NEXUS_5，与既有快照基线一致；终值以 CI 帧 IHDR 为准）。 */
    const val FRAME_WIDTH_PX: Float = 1080f
    const val FRAME_HEIGHT_PX: Float = 1920f

    /** 装配一个已进入天外视角、blend=1 的控制器；快照由 [GlobeController.nextFrame] 产出。 */
    fun newController(): GlobeController {
        val controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_HEAVEN)
        controller.setSkyBlend(1f)
        controller.setSkyPose(0.0, 0.0)
        return controller
    }

    /** 取一帧基础快照：其它地球仪态字段由门面从场景装配，星空域档位 / 兜底清单按态覆盖。 */
    fun baseSnapshot(): FrameSnapshot = newController().nextFrame(0.016, FIXED_UTC_MILLIS)

    /** 复位渲染器跨态状态面；layersLoaded / atlas / 背景位图是帧间缓存，保持复用不重置。 */
    fun resetRendererState() {
        SkyCanvasRenderer.lowPower = false
        SkyCanvasRenderer.offline = false
        SkyCanvasRenderer.loadingProgress = -1f
        SkyCanvasRenderer.errorHint = null
        // 外部金字塔进度 / 错误面一并复位：避免态 4 / 态 7 的注入值泄漏到后续帧。
        SkyCanvasRenderer.externalPyramidProgress = -1f
        SkyCanvasRenderer.externalPyramidError = null
        SkyCanvasRenderer.hoverId = -1L
        SkyCanvasRenderer.pressId = -1L
        SkyCanvasRenderer.pressStartMs = 0L
        SkyCanvasRenderer.showNames = true
        SkyCanvasRenderer.contentMode = "mode-asterism"
    }

    /** 经既有门面加载星表；assets 不可用时门面内部已收敛为占位目录，渲染侧走种子兜底。 */
    fun loadStore(assets: AssetManager?): StarStore = StarStoreProvider.get(assets)

    /** 临时金字塔缓存目录（JVM 单测用；退出即删，不污染宿主 filesDir）。 */
    fun tmpPyramidDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$prefix-${System.nanoTime()}")
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    /** 仓库根定位：从 user.dir 向上找含 app/src/main/assets/astro/stars.csv 的目录（同 bench 用例范式）。 */
    fun repoRoot(): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(cwd) { it.parentFile }
            .first { File(it, "app/src/main/assets/astro/stars.csv").isFile }
    }

    /**
     * 态 7 坏片注入：在临时目录写 `level_<level>.csv.gz` 垃圾字节，旁证哈希故意不符；
     * 对照生产 [StarCatalog.loadPyramid] 旁证校验分支，ensurePyramid 必须收敛 Corrupt。
     */
    fun writeCorruptShard(dir: File, level: Int) {
        val name = "level_$level.csv.gz"
        File(dir, name).writeBytes("not-a-gzip-payload".toByteArray(Charsets.UTF_8))
        File(dir, "$name.sha256").writeText(
            "${sha256("tampered".toByteArray())}  $name\n", Charsets.UTF_8,
        )
    }

    /**
     * 态 4 真实金字塔片：把仓库 data/pyramid 对应 level 的 gz 片与旁证拷进临时目录，
     * 走真实解析段计数（进度钩子回传的 done/total 是真实行数，不是塞进去的假常量）。
     */
    fun copyRealShard(dir: File, level: Int) {
        val src = File(repoRoot(), "data/pyramid")
        val gz = File(src, "level_$level.csv.gz")
        val side = File(src, "level_$level.csv.gz.sha256")
        File(dir, gz.name).writeBytes(gz.readBytes())
        File(dir, side.name).writeText(side.readText(Charsets.UTF_8))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * 真实进度观测记录：绑定目录进度钩子，留存最后一次回调的 done/total。
     * 口径同宿主 GlobeScreenHost.bindPyramidProgress：total<=0（总量未知）不回填假比例，ratio=-1。
     */
    class ProgressCapture {
        var done: Long = -1L
            private set
        var total: Long = -1L
            private set

        fun bind(store: StarStore) {
            store.bindPyramidProgress { _, d, t -> done = d; total = t }
        }

        /** 解析段真实比值 0..1；总量未知时 -1（不得伪造）。 */
        val ratio: Float
            get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
    }

    /**
     * 反推一颗当前姿态落在屏内的星主键，供悬停 / 按下态定点。
     * 投影口径与渲染器一致（同一 [StarProjector] 入口）；
     * 占位目录无真实主键时返回 -1L，用例不得伪造放大目标。
     */
    fun firstVisibleStarId(store: StarStore, yaw: Double, pitch: Double): Long {
        val catalog = store.catalog
        if (catalog.size == 0) return -1L
        val fov = 90.0
        val focal = (min(FRAME_WIDTH_PX, FRAME_HEIGHT_PX) / 2.0) / Math.tan(fov / 2.0 * PI / 180.0)
        val tmp = FloatArray(3)
        for (i in 0 until catalog.size) {
            val ok = StarProjector.project(
                SkyViewMode.SKY_HEAVEN,
                catalog.raAt(i),
                catalog.decAt(i),
                yaw,
                pitch,
                focal,
                FRAME_WIDTH_PX / 2.0,
                FRAME_HEIGHT_PX / 2.0,
                34.34,
                108.94,
                0.0,
                tmp,
            )
            if (ok) return catalog.idAt(i)
        }
        return -1L
    }

    /** 星空域三种兜底清单条目（用于加载 / 离线态快照覆盖）。 */
    fun skyResolutions(status: AssetStatus): List<OfflineFallback.Resolution> = listOf(
        OfflineFallback.Resolution(TextureKind.STAR_SKY, status, FallbackKind.PROCEDURAL_STAR_FIELD),
        OfflineFallback.Resolution(TextureKind.SKY_DENSITY_L9, status, FallbackKind.PROCEDURAL_STAR_FIELD),
        OfflineFallback.Resolution(TextureKind.SKY_DENSITY_L11, status, FallbackKind.PROCEDURAL_STAR_FIELD),
    )
}

/**
 * 测试侧帧视图：onDraw 转交真实 [SkyCanvasRenderer.draw]。
 *
 * 为什么用 View 包装而不是 Compose Canvas：Paparazzi 的 View 渲染链会走 measure / layout /
 * onDraw，onDraw 拿到的是 layoutlib 真 Canvas，与真机宿主调用同一渲染入口；
 * 本类只持快照与 store，不实现任何绘制像素。
 */
internal class SkyRenderView(context: Context) : View(context) {
    var snapshot: FrameSnapshot? = null
    var store: StarStore? = null
    var density: Float = 1f
    var nowMs: Long = System.currentTimeMillis()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val snap = snapshot ?: return
        SkyCanvasRenderer.draw(
            canvas,
            snap,
            width.toFloat(),
            height.toFloat(),
            labelPaint,
            store,
            density,
            nowMs,
        )
    }

    /**
     * bench 计时入口：绕开 View 受保护可见性，直接把 [onDraw] 交给计时用例逐帧调用。
     * 为什么不另画一份：计时必须走真实 onDraw 路径，不许在辅助方法里复制渲染调用。
     */
    fun drawForBench(canvas: Canvas) = onDraw(canvas)
}
