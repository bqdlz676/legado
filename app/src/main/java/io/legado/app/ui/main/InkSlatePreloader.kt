package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView

/**
 * 墨板 WebView 预热 + 单例。
 *
 * 每次 onCreate 才 new WebView，冷启动要等引擎初始化；反复进出还会留下半死的 WebView。
 * 这里全局只留一个：启动后预热一个 about:blank，进画板时 take()，退出时 putBack()，
 * 用 removeView / addView 在 Activity 间转移，不 new/destroy。
 */
object InkSlatePreloader {

    private var pooled: WebView? = null
    private var warmed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun warmUp(context: Context) {
        if (warmed || pooled != null) return
        warmed = true
        runCatching {
            val wv = WebView(context.applicationContext)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.loadUrl("about:blank")
            pooled = wv
        }
    }

    fun take(): WebView? = pooled.also { pooled = null }

    fun putBack(wv: WebView) {
        if (pooled == null) pooled = wv else runCatching { wv.destroy() }
    }

    fun clear() {
        runCatching { pooled?.destroy() }
        pooled = null
    }
}
