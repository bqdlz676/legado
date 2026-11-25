package io.legado.app.ui.welcome

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import io.legado.app.ui.splash.contract.SplashParams
import kotlin.math.cos
import kotlin.math.sin

/**
 * 落文 · 启动动画场景（ui/welcome 独占）。
 *
 * 纯 Compose 自绘，不引入 MaterialTheme / Material ripple；拍板外观 / 时序值取 [SplashParams]，
 * 场景专属色与编排时间线取 [LuowenSplash]。
 * 编排由纯 JVM [SplashChoreography] 推进，本层只取状态绘制，便于单测与 bench 剥离。
 *
 * 整段编排固定 2800ms：到点必回调 [onFinish] 进书架，不等待后台初始化；
 * 不做跳过按钮 / 进度条 / 转圈 / 版本号 / Logo / 声音 / 网络检测（启动动画原文「不做的」）。
 *
 * @param onFinish 2800ms 整段编排结束回调（进书架的导航接线由主控 merge）
 */
@Composable
fun SplashScene(
    onFinish: () -> Unit,
) {
    val density = LocalDensity.current.density
    val engine = remember { SplashChoreography() }

    // 帧时钟：绝对毫秒，固定 2800ms 收口；不等待后台初始化，结束必回调。
    var ms by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.elapsedRealtimeNanos()
        while (true) {
            withFrameNanos {
                val now = android.os.SystemClock.elapsedRealtimeNanos()
                val elapsed = (now - start) / 1_000_000L
                ms = elapsed.coerceAtMost(SplashParams.TOTAL_MS.toLong())
            }
            if (ms >= SplashParams.TOTAL_MS.toLong()) break
        }
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            engine.update(ms, size.width, size.height, density)
            val cx = size.width * 0.5f
            val cy = size.height * 0.48f

            // 底：全黑
            drawRect(LuowenSplash.splashBg)

            // 远山（先画，被字与粒子压住）
            drawMountain(engine)

            // 墨滴
            if (engine.dropAlpha > 0f) drawDrop(engine)

            // 墨屑
            for (p in engine.ink) {
                if (p.alpha <= 0.01f) continue
                drawInkParticle(p, density)
            }

            // 金粉
            for (g in engine.gold) {
                if (g.alpha <= 0.01f) continue
                drawGoldParticle(g, density)
            }

            // 扫光辉光 + 金线
            drawSweep(engine, cx, cy, density)

            // 竖排「落文」
            drawGlyphs(engine, cx, cy, density)

            // 金点收束（启动动画末尾金线收成的辅助图形标识）
            if (engine.goldDotAlpha > 0.01f) {
                drawCircle(
                    color = LuowenSplash.brandGold.copy(alpha = engine.goldDotAlpha),
                    radius = 3.2f * density,
                    center = Offset(cx, cy + size.height * 0.10f),
                )
            }

            // 落款
            if (engine.sloganAlpha > 0.01f) {
                drawSlogan(engine, size.width, size.height, density)
            }

            // 全局淡出压暗
            if (engine.fadeAlpha < 1f) {
                drawRect(LuowenSplash.splashBg.copy(alpha = 1f - engine.fadeAlpha))
            }
        }
    }
}

// —— 绘制层私有扩展（不进引擎，避免引入 Android 图形依赖）——

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMountain(
    engine: SplashChoreography,
) {
    val alpha = engine.hillFadeAlpha()
    if (alpha <= 0.01f) return
    val path = Path()
    val ridge = engine.ridge
    ridge.forEachIndexed { i, (x, y) ->
        val px = x * size.width
        val py = y * size.height
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LuowenSplash.textSecondary.copy(alpha = alpha).toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    drawContext.canvas.nativeCanvas.drawPath(path, linePaint)

    // 山腰白雾：两道半透明白色横带，随 mistOffset 左右缓移
    val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.copy(alpha = alpha * 0.35f).toArgb()
    }
    val mistY = size.height * 0.72f
    val offset = engine.mistOffset
    drawContext.canvas.nativeCanvas.drawOval(
        offset - size.width * 0.15f, mistY - 10f * density,
        offset + size.width * 0.55f, mistY + 10f * density,
        mistPaint,
    )
    drawContext.canvas.nativeCanvas.drawOval(
        offset + size.width * 0.2f, mistY + 16f * density,
        offset + size.width * 0.9f, mistY + 28f * density,
        mistPaint,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDrop(
    engine: SplashChoreography,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LuowenSplash.textPrimary.copy(alpha = engine.dropAlpha).toArgb()
        style = Paint.Style.FILL
    }
    val path = Path()
    val x = engine.dropX
    val y = engine.dropY
    val r = engine.dropSize
    // 毛笔饱蘸的一滴：上圆下尖，边缘带撕裂细丝
    path.moveTo(x, y + r * 1.3f)
    path.cubicTo(x - r, y + r * 0.2f, x - r * 0.8f, y - r, x, y - r)
    path.cubicTo(x + r * 0.8f, y - r, x + r, y + r * 0.2f, x, y + r * 1.3f)
    drawContext.canvas.nativeCanvas.drawPath(path, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkParticle(
    p: SplashChoreography.InkParticle,
    density: Float,
) {
    val base = LuowenSplash.textPrimary
    val particleColor = when (p.kind) {
        SplashChoreography.InkKind.ROUND -> base.copy(alpha = p.alpha * 0.92f)
        SplashChoreography.InkKind.FILAMENT -> base.copy(alpha = p.alpha * 0.7f)
        SplashChoreography.InkKind.SPLAT -> base.copy(alpha = p.alpha * 0.85f)
    }
    when (p.kind) {
        SplashChoreography.InkKind.FILAMENT -> {
            val len = p.size * 3.2f * density
            val dx = cos(p.rot.toDouble()).toFloat() * len
            val dy = sin(p.rot.toDouble()).toFloat() * len
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = particleColor.toArgb()
                strokeWidth = p.size * density * 0.5f
                strokeCap = Paint.Cap.ROUND
            }
            drawContext.canvas.nativeCanvas.drawLine(p.x - dx, p.y - dy, p.x + dx, p.y + dy, paint)
        }
        else -> {
            val radius = p.size * density * (if (p.kind == SplashChoreography.InkKind.SPLAT) 1.15f else 1f)
            drawCircle(particleColor, radius, Offset(p.x, p.y))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGoldParticle(
    g: SplashChoreography.GoldParticle,
    density: Float,
) {
    drawCircle(
        color = LuowenSplash.brandGold.copy(alpha = g.alpha),
        radius = g.size * density,
        center = Offset(g.x, g.y),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSweep(
    engine: SplashChoreography,
    cx: Float,
    cy: Float,
    density: Float,
) {
    if (engine.sweepProgress <= 0.01f) return
    val sweepX = cx - size.width * 0.35f + engine.sweepProgress * size.width * 0.7f

    // 40dp 金色辉光（color-glow 30%）
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LuowenSplash.colorGlow.copy(alpha = engine.sweepGlow * 0.5f).toArgb()
        setShadowLayer(
            SplashParams.GLOW_RADIUS_DP * density,
            0f, 0f,
            LuowenSplash.brandGold.toArgb(),
        )
    }
    drawContext.canvas.nativeCanvas.drawCircle(sweepX, cy, 6f * density, glowPaint)

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LuowenSplash.brandGold.copy(alpha = 0.9f).toArgb()
        strokeWidth = 1.05f * density
        strokeCap = Paint.Cap.ROUND
    }
    drawContext.canvas.nativeCanvas.drawLine(
        cx - size.width * 0.35f, cy + size.height * 0.06f,
        sweepX, cy + size.height * 0.06f,
        linePaint,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyphs(
    engine: SplashChoreography,
    cx: Float,
    cy: Float,
    density: Float,
) {
    if (engine.glyphLuoAlpha <= 0.01f && engine.glyphWenAlpha <= 0.01f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.SERIF
        textAlign = Paint.Align.CENTER
        textSize = SplashParams.TITLE_FONT_SIZE_SP * density
    }
    val lineHeight = 64f * density
    if (engine.glyphLuoAlpha > 0.01f) {
        paint.color = LuowenSplash.glyphPaper.copy(alpha = engine.glyphLuoAlpha).toArgb()
        drawContext.canvas.nativeCanvas.drawText("落", cx, cy - lineHeight * 0.5f, paint)
        drawStrokeBrush(engine.luoStroke, cx, cy - lineHeight * 0.5f, density, engine.glyphLuoAlpha)
    }
    if (engine.glyphWenAlpha > 0.01f) {
        paint.color = LuowenSplash.glyphPaper.copy(alpha = engine.glyphWenAlpha).toArgb()
        drawContext.canvas.nativeCanvas.drawText("文", cx, cy + lineHeight * 0.7f, paint)
        drawStrokeBrush(engine.wenStroke, cx, cy + lineHeight * 0.7f, density, engine.glyphWenAlpha)
    }
}

/**
 * 笔顺生长示意：一笔行笔的暖光扫过字面，进度对应笔顺生长 0..1。
 * 真正的矢量笔顺描边需接入公版书法 / 篆书字库资产；当前以 SERIF 字面 + 行笔光带近似。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokeBrush(
    stroke: Float,
    cx: Float,
    baselineY: Float,
    density: Float,
    glyphAlpha: Float,
) {
    if (stroke <= 0.01f || stroke >= 0.99f) return
    val half = size.width * 0.12f
    val brushX = cx - half + stroke * half * 2f
    val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LuowenSplash.brandGold.copy(alpha = 0.35f * glyphAlpha).toArgb()
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    drawContext.canvas.nativeCanvas.drawLine(
        brushX, baselineY - 34f * density,
        brushX, baselineY + 6f * density,
        brushPaint,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlogan(
    engine: SplashChoreography,
    width: Float,
    height: Float,
    density: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(SplashParams.textTertiary).copy(alpha = engine.sloganAlpha).toArgb()
        textSize = SplashParams.SLOGAN_FONT_SIZE_SP * density
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.06f
    }
    drawContext.canvas.nativeCanvas.drawText(
        "读其文，知其人，通其时。",
        width * 0.5f, height * 0.86f, paint,
    )
}
