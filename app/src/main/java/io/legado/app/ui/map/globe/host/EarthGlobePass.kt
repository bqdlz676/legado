package io.legado.app.ui.map.globe.host

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.GlobeSpecSurface
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.replay.FactionPalette
import io.legado.app.ui.map.globe.render.FieldGlowOverlay
import io.legado.app.ui.map.globe.render.FrameSnapshot
import kotlin.math.min

/**
 * 地球仪态 pass（D45 从 [GlobeScreenHost] 下沉）：散点星场、大气辉光、海洋球壳、
 * 势力色块、大圆路线、城池光针、地名标签、云层壳、日盘、分野脉冲 overlay。
 *
 * 绘制发生在宿主 [GlobeCanvas] 既有 Canvas 的 [DrawScope] 内；画笔 [labelPaint]
 * 所有权留宿主传入，本 pass 不重建 Paint，避免标签字号 / 颜色串台。
 * 星空 pass 仍由 [SkyCanvasRenderer] 在本 pass 之前绘制，不在本文件内。
 */
internal fun EarthGlobePass(
    scope: DrawScope,
    host: GlobeHostController,
    snapshot: FrameSnapshot,
    viewport: Pair<Float, Float>,
    labelPaint: Paint,
    density: Float,
) = with(scope) {
    val (w, h) = viewport
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

    // 地球侧分野脉冲 overlay（§十一.1）：一行调用，几何 / 呼吸全在覆盖层内。
    FieldGlowOverlay.draw(
        drawContext.canvas.nativeCanvas,
        SkyCanvasRenderer.fieldRegion(),
        SkyCanvasRenderer.fieldPulseStartMs(),
        System.currentTimeMillis(),
        w,
        h,
        density,
        labelPaint,
    )
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

/** 地貌数据色（Int RGB）→ Compose Color；12 色 token 之外的地貌色只走门面 spec 派生。 */
private fun surfaceColor(rgb: Int, alpha: Float = 1f): Color {
    val r = (rgb ushr 16) and 0xFF
    val g = (rgb ushr 8) and 0xFF
    val b = rgb and 0xFF
    return Color(r, g, b).copy(alpha = alpha)
}
