package io.legado.app.ui.about

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.BuildConfig
import io.legado.app.R

/**
 * 落文 · 关于
 *
 * 竖排「落文」两字居中，下面一道朱砂短竖与版本号，
 * 底部一行字幕来回缓走：「读其文，知其人，通其时。」
 */
class AboutInkActivity : AppCompatActivity() {

    private var marquee: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_ink)

        findViewById<TextView>(R.id.tv_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_version).text =
            "版本 ${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}"

        startMarquee(findViewById(R.id.fl_motto), findViewById(R.id.tv_motto))
    }

    private fun startMarquee(box: android.view.View, text: TextView) {
        box.post {
            val w = box.width.toFloat()
            val tw = text.paint.measureText(text.text.toString())
            val start = w
            val end = -tw - dp(20)
            marquee?.cancel()
            marquee = ValueAnimator.ofFloat(start, end).apply {
                duration = ((w + tw) / dp(26) * 1000).toLong().coerceIn(6000L, 22000L)
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { text.translationX = it.animatedValue as Float }
                start()
            }
        }
    }

    override fun onDestroy() {
        marquee?.cancel()
        marquee = null
        super.onDestroy()
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density
}
