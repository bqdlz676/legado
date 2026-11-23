package io.legado.app.ui.globe

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import io.legado.app.ui.globe.core.data.GlobePlace
import io.legado.app.ui.globe.core.scene.GlobeScene
import io.legado.app.ui.globe.render.GlobeRenderer
import kotlin.math.hypot

/**
 * 落文 · 3D 地球仪对外入口（自绘 GLSurfaceView，OpenGL ES 2.0）。
 *
 * 切片 20：地球仪和沙盘是同一个球，缩大缩小的区别，不是两个页面；
 * 本 View 只承载球体渲染核心，战役回放 / 势力多边形 / 行军路线由其它模块接入。
 *
 * 手势（切片 19 / 20）：
 *  - 单指拖动：沿球面旋转；松手惯性 0.8s 停稳；
 *  - 双指捏合：缩放（全球 ↔ 区域 ↔ 战场多级 LOD）；
 *  - 双击：飞到点击位置（回调交给上层落焦点）；
 *  - 自动旋转 0.2°/s，可开关。
 *
 * 渲染层需真机 / 模拟器验证；构造与回调签名如下：
 * ```
 * val view = GlobeView(context)
 * view.onReady = { ... }
 * view.onPlaceSelected = { place -> ... }
 * view.setAutoRotate(true)
 * ```
 */
class GlobeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val scene: GlobeScene = GlobeScene()
    private val renderer: GlobeRenderer = GlobeRenderer(scene)

    /** 首帧渲染完成。 */
    var onReady: (() -> Unit)? = null

    /** 点选地名 / 光针后回调（悬浮竹简信息卡由上层实现）。 */
    var onPlaceSelected: ((GlobePlace) -> Unit)? = null

    /** 双击飞到点击位置（视图坐标 px）。 */
    var onFlyTo: ((x: Float, y: Float) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var pinchStartDist = 0f
    private var lastTapMs = 0L

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        renderer.onReady = { onReady?.invoke() }
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setAutoRotate(enabled: Boolean) {
        scene.rotator.autoRotate = enabled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
                scene.rotator.onDragStart()
                handleDoubleTap(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    pinchStartDist = distanceBetween(event)
                    dragging = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2) {
                    val dist = distanceBetween(event)
                    if (pinchStartDist > 0f) {
                        scene.zoomBy(dist / pinchStartDist)
                        pinchStartDist = dist
                    }
                } else if (dragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    scene.rotator.onDrag(dx.toDouble(), dy.toDouble())
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                scene.rotator.onDragEnd()
                pinchStartDist = 0f
            }
        }
        return true
    }

    private fun handleDoubleTap(event: MotionEvent) {
        val now = System.currentTimeMillis()
        if (now - lastTapMs < 280) {
            onFlyTo?.invoke(event.x, event.y)
            lastTapMs = 0L
        } else {
            lastTapMs = now
        }
    }

    private fun distanceBetween(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy).coerceAtLeast(1f)
    }

    /** 离开页面时释放 GL 资源（切片 08：dispose geometry/texture/material，即时回收）。 */
    fun release() {
        queueEvent { renderer.release() }
    }
}
