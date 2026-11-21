package io.legado.app.ui.widget

import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/**
 * 落文 · 墨卡手感
 *
 * 按下时整张卡片向读者前倾 4.5°，抬起时带回弹地正回去。
 * 不用 Material 的 elevation / ripple，只有一点点绕 Y 轴的旋转。
 */
object InkCardMotion {

    private val settle = OvershootInterpolator(1.6f)
    private val revealCurve = PathInterpolator(0.22f, 0.61f, 0.36f, 1f)

    fun down(view: View) {
        view.cameraDistance = 9600f * view.resources.displayMetrics.density
        view.animate()
            .rotationY(4.5f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(120)
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .start()
    }

    fun up(view: View) {
        view.animate()
            .rotationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260)
            .setInterpolator(settle)
            .start()
    }

    fun touch(view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(view)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> up(view)
        }
    }

    /** 首次绑定时卡片从下方浮入，按顺位错开 */
    fun reveal(view: View, index: Int) {
        if (view.tag == java.lang.Boolean.TRUE) return
        view.tag = java.lang.Boolean.TRUE
        val d = view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = 26f * d
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay((index.coerceAtMost(8) * 45).toLong())
            .setDuration(380)
            .setInterpolator(revealCurve)
            .start()
    }
}
