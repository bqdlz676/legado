package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView

/**
 * 半屏 Sheet 里的地图 WebView 单例。
 *
 * Leaflet + WebView 每次点地名都 new 一个，开关十次就是十份 JS 堆；
 * 这里全局只留一个，在容器之间 remove/add 转移，dismiss 时脱离但不销毁。
 */
object MapWebHolder {

    private var web: WebView? = null
    private var lastUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(container: ViewGroup, url: String) {
        runCatching {
            val ctx = container.context.applicationContext
            val w = web ?: WebView(ctx).also {
                it.settings.javaScriptEnabled = true
                it.settings.domStorageEnabled = true
                it.settings.allowFileAccess = true
                it.settings.useWideViewPort = true
                it.settings.loadWithOverviewMode = true
                it.setBackgroundColor(0xFFF7F1E6.toInt())
                web = it
            }
            (w.parent as? ViewGroup)?.removeView(w)
            container.removeAllViews()
            container.addView(
                w,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            // 同一块地不重载，避免每次弹 sheet 都重跑一次 Leaflet 初始化
            if (url != lastUrl) {
                w.loadUrl(url)
                lastUrl = url
            }
        }
    }

    fun detach() {
        (web?.parent as? ViewGroup)?.removeView(web)
    }

    fun destroy() {
        runCatching {
            detach()
            web?.destroy()
            web = null
            lastUrl = ""
        }
    }
}
