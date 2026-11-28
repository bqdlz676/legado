package io.legado.app.ui.map.globe.astro.proj

import io.legado.app.ui.map.globe.astro.contract.SkyFrame
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.StarStore
import kotlin.math.PI
import kotlin.math.min

/**
 * 投影行星源：L0 内置层 ∪ 已加载金字塔 L1/L2 双层合并（方案 §4.4 扫描顺序 L0 → 已加载 L1 → L2）。
 *
 * 〔为什么独立成文件〕渲染器 [SkyCanvasRenderer] 有 ≤400 行硬口径；投影遍历、层级短路与
 * 出口适配下沉到本文件，宿主只留帧参数装配与结果缓冲（参照 `ProjectedStars.kt` 同口径）。
 *
 * 〔连续裁剪，无整层突现（§七.1、§十四.1）〕逐颗按 `mag <= magLimit` 放行，不按层开关整批
 * 显隐；金字塔层未加载时只扫 L0，行为与既有口径逐位一致（零回归）。
 *
 * 〔层级短路（§十四.13 4ms 预算）〕L1 星等带 (6.5, 9.0]、L2 (9.0, 11.5]；`magLimit` 未越过
 * 6.5 下界时整组金字塔跳过，避免缩览帧白扫上万颗暗星。层内仍逐颗连续裁剪——短路只跳过整层
 * 必被裁掉的扫描，不减少任何达标星点、不抬高下限星等。
 *
 * 〔零分配口径（§十二.2）〕入参全标量；结果写调用方复用的 [ProjectionSink]，函数体不 new
 * 列表 / 装箱；复用单颗临时向量 [tmp]，跨帧不残留对象。
 */
class StarProjectionSource(
    private val mode: SkyViewMode,
    private val yawDeg: Double,
    private val pitchDeg: Double,
    private val focalPx: Double,
    private val cx: Double,
    private val cy: Double,
    private val latDeg: Double,
    private val lonDeg: Double,
    private val lstDeg: Double,
) {

    /** 单颗投影复用向量：`[0]`=x px、`[1]`=y px、`[2]`=深度；跨帧复用，不每帧分配。 */
    private val tmp = FloatArray(3)

    /**
     * 投影结果出口。帧间复用缓冲（宿主侧 `ProjectedStars`）实现本接口；单测接计数夹具。
     * 口径与既有 `ProjectedStars.add` 一致：px / py 屏幕 px，mag 星等，id 主键，colorBv 色指数。
     */
    interface ProjectionSink {
        fun add(px: Float, py: Float, mag: Float, id: Long, colorBv: Double)
    }

    /**
     * 把目录内通过连续裁剪且位于前半球的星写入 [sink]。
     *
     * 扫描顺序：先 L0 内置层，再叠加已并入的金字塔层（方案 §4.4：L0 → 已加载 L1 → L2）。
     *
     * @param store 星表门面；null 态由渲染器在调用前回落种子散点，本方法只接真实目录。
     * @param magLimit 当前星等裁剪上限（越小越亮的星越容易放行）。
     * @param sink 结果出口；实现方负责缓冲扩容。
     * @return 写入 [sink] 的可见颗数。
     */
    fun project(store: StarStore, magLimit: Float, sink: ProjectionSink): Int {
        var visible = 0
        val catalog = store.catalog

        // ── L0 内置层（既有口径：连续裁剪 `mag > magLimit` 跳过，前半球判定走 StarProjector）──
        val l0Count = catalog.size
        for (i in 0 until l0Count) {
            val mag = catalog.magAt(i)
            if (mag > magLimit) continue
            if (!projectOne(catalog.raAt(i), catalog.decAt(i), mag, catalog.idAt(i), catalog.bvAt(i), sink)) continue
            visible++
        }

        // ── 金字塔增量层：未加载时 pyramidSize=0，循环不进入；行为与只扫 L0 的旧实现一致 ──
        val pyramidCount = store.pyramidSize
        if (pyramidCount > 0 && magLimit > SkyParams.MAG_BRIGHT_ENTITY) {
            for (i in 0 until pyramidCount) {
                val mag = store.pyramidMagAt(i)
                if (mag > magLimit) continue
                val visibleOne = projectOne(
                    raDeg = store.pyramidRaAt(i),
                    decDeg = store.pyramidDecAt(i),
                    mag = mag,
                    id = store.pyramidIdAt(i),
                    bv = store.pyramidBvAt(i),
                    sink = sink,
                )
                if (!visibleOne) continue
                visible++
            }
        }
        return visible
    }

    /** 投影单颗；可见则写 [sink] 返回 true，被背面半球剔除返回 false。 */
    private fun projectOne(
        raDeg: Double,
        decDeg: Double,
        mag: Float,
        id: Long,
        bv: Double,
        sink: ProjectionSink,
    ): Boolean {
        val ok = StarProjector.project(
            mode = mode,
            raDeg = raDeg,
            decDeg = decDeg,
            yawDeg = yawDeg,
            pitchDeg = pitchDeg,
            focalPx = focalPx,
            cx = cx,
            cy = cy,
            latDeg = latDeg,
            lonDeg = lonDeg,
            lstDeg = lstDeg,
            out = tmp,
        )
        if (!ok) return false
        sink.add(tmp[0], tmp[1], mag, id, bv)
        return true
    }

    companion object {
        /**
         * 从帧快照装配投影源：视场 / 焦距 / 相机姿态 / 观测点默认值沿用渲染器既有口径
         *（地上观 60° 视场，天外 90°；无定位默认长安示意 34.34°N / 108.94°E，不弹权限墙）。
         */
        fun fromFrame(sky: SkyFrame, widthPx: Float, heightPx: Float): StarProjectionSource {
            val fov = if (sky.mode == SkyViewMode.SKY_EARTH) SkyParams.GROUND_VIEW_FOV_DEG else 90.0
            val focal = (min(widthPx, heightPx) / 2.0) / Math.tan(fov / 2.0 * PI / 180.0)
            val yaw = sky.cameraYawPitch?.yawDeg ?: 0.0
            val pitch = sky.cameraYawPitch?.pitchDeg ?: 0.0
            val lat = sky.observerLatLon?.latDeg ?: 34.34
            val lon = sky.observerLatLon?.lonDeg ?: 108.94
            return StarProjectionSource(
                mode = sky.mode,
                yawDeg = yaw,
                pitchDeg = pitch,
                focalPx = focal,
                cx = widthPx / 2.0,
                cy = heightPx / 2.0,
                latDeg = lat,
                lonDeg = lon,
                lstDeg = sky.siderealTimeDeg,
            )
        }
    }
}
