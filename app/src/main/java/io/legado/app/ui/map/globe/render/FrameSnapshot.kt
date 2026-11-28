package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.data.SeedAtlas
import io.legado.app.ui.map.globe.astro.contract.SkyFrame
import io.legado.app.ui.map.globe.math.Vec3
import io.legado.app.ui.map.globe.mesh.LodLevel
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.offline.FallbackDomain
import io.legado.app.ui.map.globe.offline.OfflineFallback
import io.legado.app.ui.map.globe.replay.marker.GlobeMarker
import io.legado.app.ui.map.globe.replay.marker.GlobePlace
import io.legado.app.ui.map.globe.replay.power.PowerLayer
import io.legado.app.ui.map.globe.replay.route.GlobeRoute
import io.legado.app.ui.map.globe.scene.AtmosphereSpec
import io.legado.app.ui.map.globe.scene.CloudSpec
import io.legado.app.ui.map.globe.scene.GlobeScene
import io.legado.app.ui.map.globe.scene.LabelScheme
import io.legado.app.ui.map.globe.scene.OceanSpec

/**
 * 一帧的不可视描述快照（纯 JVM）。
 *
 * 渲染层每帧从 [GlobeScene] 拍一份快照，按快照组织 pass；快照不可变，
 * 避免绘制过程中场景状态被手势 / 回放线程改写造成一帧内前后不一致。
 *
 * 离线兜底：[degradation] 决定走真实纹理还是程序化 / 种子；任何档位都带齐
 * 路线、光针、标签、势力的兜底数据，不出现空白 pass。
 *
 * 性能：势力贴球壳色块（128×128 采样）不在快照装配时重算，只把 [powerLayer]
 * 引用交给渲染层，由渲染层按 LOD 缓存色块，避免每帧射线法扫全网格。
 */
data class FrameSnapshot(
    val lod: LodLevel,
    val cameraDistance: Float,
    val distanceRatio: Float,
    val sunDir: Vec3,
    val cloudRotationDeg: Double,
    val degradation: DegradationLevel,
    /** 星空域独立档位：只由 SKY 种类集合算档，不被地球域缺档拉低（§十三.8）。 */
    val skyDegradation: DegradationLevel,
    val atmosphere: AtmosphereSpec,
    val clouds: CloudSpec,
    val ocean: OceanSpec,
    val route: GlobeRoute?,
    val routeProgress: Double,
    val markers: List<GlobeMarker>,
    val visibleLabels: List<Pair<GlobePlace, Float>>,
    val powerLayer: PowerLayer,
    val fallbackResolutions: List<OfflineFallback.Resolution>,
    val sky: SkyFrame? = null,
) {

    companion object {

        fun from(scene: GlobeScene): FrameSnapshot {
            val replay = scene.replay
            val route = replay.route()
            val resolutions = scene.fallback.resolveAll()
            val labels = LabelScheme.visible(SeedAtlas.places, scene.distanceRatio.toDouble())

            return FrameSnapshot(
                lod = scene.currentLod,
                cameraDistance = scene.cameraDistance,
                distanceRatio = scene.distanceRatio,
                sunDir = scene.sunDir,
                cloudRotationDeg = scene.cloudRotationDeg,
                degradation = scene.fallback.degradation(FallbackDomain.EARTH),
                skyDegradation = scene.fallback.degradation(FallbackDomain.SKY),
                atmosphere = scene.atmosphere,
                clouds = scene.clouds,
                ocean = scene.ocean,
                route = route,
                routeProgress = replay.routeProgress(),
                markers = replay.markers(),
                visibleLabels = labels,
                powerLayer = replay.powers,
                fallbackResolutions = resolutions,
                sky = scene.sky,
            )
        }
    }
}
