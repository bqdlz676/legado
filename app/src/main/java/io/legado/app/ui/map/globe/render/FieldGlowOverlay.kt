package io.legado.app.ui.map.globe.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.host.skyArgb
import kotlin.math.PI
import kotlin.math.sin

/**
 * 地球侧分野脉冲 overlay（§十一.1、§八.11；D3 前向闭环入口）。
 *
 * 职责单一：给定州域名与脉冲起算时刻，在地球仪态上画 `colorGlow` 1200ms 呼吸脉冲环
 * + 州名竖排（14sp brandGold）。不碰既有地球渲染类，不持有球壳 / 纹理状态；宿主绘制
 * 序列里只加一行调用。
 *
 * 〔示意口径〕周代州域无真实边界多边形（§十一.4 tier3 示意）：脉冲环锚在地球屏心，
 * 不冒充疆域轮廓；州名竖排仅作联动锚点标注。脉冲窗口取 4 个周期，超时自动收束不留残影。
 */
object FieldGlowOverlay {

    /** 脉冲最大展宽（px），与天球侧选中光环同口径（§十六.10）。 */
    private const val PULSE_RADIUS_PX: Float = 16f

    /** 脉冲存活窗口：4 个 1200ms 周期，之后不再描边，避免常驻光圈抢地球本体。 */
    private const val PULSE_WINDOW_MS: Long = SkyParams.FIELD_PULSE_MS * 4

    /**
     * @param regionName 选中州域名；null / 空串不绘制
     * @param pulseStartMs 脉冲起算时刻（UTC 毫秒，与帧时钟一致）
     * @param nowMs 当前时刻毫秒
     * @param w 画布宽 px；h 画布高 px；density 屏幕密度
     * @param labelPaint 宿主复用的文字画笔（字号 / 颜色由本覆盖层接管）
     */
    fun draw(
        canvas: Canvas,
        regionName: String?,
        pulseStartMs: Long,
        nowMs: Long,
        w: Float,
        h: Float,
        density: Float,
        labelPaint: Paint,
    ) {
        if (regionName.isNullOrBlank()) return
        val elapsed = nowMs - pulseStartMs
        if (elapsed < 0L || elapsed > PULSE_WINDOW_MS) return

        val breath = (1f + 0.12f * sin(elapsed * 2.0 * PI / SkyParams.FIELD_PULSE_MS)).toFloat()
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.color = skyArgb(GlobeTokens.colorGlow)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 1.5f
        val path = Path()
        path.addCircle(w / 2f, h / 2f, PULSE_RADIUS_PX * breath, Path.Direction.CW)
        canvas.drawPath(path, ring)

        // 州名竖排：14sp brandGold，落右侧安全边距内；逐字下行，不破版（§五.5、§十三.3）。
        labelPaint.textSize = SkyParams.STAR_LABEL_FONT_SP * density
        labelPaint.color = skyArgb(GlobeTokens.brandGold)
        var ty = h * 0.30f
        val tx = w - 24f * density
        regionName.forEach { ch ->
            canvas.drawText(ch.toString(), tx, ty, labelPaint)
            ty += SkyParams.STAR_LABEL_FONT_SP * density
        }
    }
}
