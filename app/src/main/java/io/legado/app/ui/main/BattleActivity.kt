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

    override fun onDestroy() {
        web?.apply {
            loadUrl("about:blank")
            stopLoading()
            destroy()
        }
        web = null
        super.onDestroy()
    }
}
