package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.sensor.GyroBridge

/**
 * 3D 战役推演沙盘入口。
 *
 * 页面本体是 assets/map/battle.html：Three.js 渲染地形/棋子/兵锋箭头，
 * 推演逻辑在 assets/map/engine.js（纯 JS，离线，和 Android 无关）。
 * 这里只负责给一个能跑 WebGL 的 WebView 和战役 id / 年份。
 */
class BattleActivity : AppCompatActivity() {

    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val w = WebView(this)
        w.setBackgroundColor(Color.BLACK)
        w.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        w.webChromeClient = WebChromeClient()
        w.webViewClient = WebViewClient()
        // 仓内此前无任何 JS 桥：新建 GyroHost，供 gyro.js 每帧拉取姿态四元数
        w.addJavascriptInterface(GyroHost(), "GyroBridge")
        setContentView(w)
        web = w

        val campaign = intent.getStringExtra("campaign") ?: "gaixia"
        val extraYear = intent.getIntExtra("year", Int.MIN_VALUE)
        val year = if (extraYear != Int.MIN_VALUE) extraYear else ContextAnchor.getYear()
        val url = buildString {
            append("file:///android_asset/map/battle.html?c=").append(campaign)
            if (year != null) append("&y=").append(year)
        }
        w.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        // 切回前台重注册传感器；方向基准按当前屏幕方向重映射
        GyroBridge.setDisplayRotation(windowManager.defaultDisplay.rotation)
        GyroBridge.start(this)
    }

    override fun onPause() {
        // 切后台注销，避免后台耗电（规格 §七）
        GyroBridge.stop()
        super.onPause()
    }

    override fun onDestroy() {
        GyroBridge.stop()
        web?.apply {
            loadUrl("about:blank")
            stopLoading()
            destroy()
        }
        web = null
        super.onDestroy()
    }

    /** JS ↔ 原生桥：纯数据取出，不持有 WebView；销毁后残留调用不会再 evaluateJavascript。 */
    private inner class GyroHost {
        /** 每帧拉一条最新四元数，格式 "w,x,y,z"；空队返回空串。 */
        @android.webkit.JavascriptInterface
        fun pull(): String {
            val q = GyroBridge.drainLatest() ?: return ""
            return "${q[0]},${q[1]},${q[2]},${q[3]}"
        }

        /** 兜底融合矩阵分支：JS 端强制锁权重 0.04。 */
        @android.webkit.JavascriptInterface
        fun degraded(): Boolean = GyroBridge.degraded

        /** 是否有可用传感器；false 时 JS 端隐藏陀螺仪入口。 */
        @android.webkit.JavascriptInterface
        fun available(): Boolean = GyroBridge.level > 0
    }
}
