package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File

/**
 * 墨板 · 画板
 * WebView 承载 InkSlate 前端（Excalidraw + peinten），走 appassets 域名避免 file:// 限制。
 */
class PaintActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this)
        webView.setBackgroundColor(0xFF0E0F12.toInt())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(Bridge(), "InkSlate")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
        }
        webView.webChromeClient = WebChromeClient()

        setContentView(webView, MATCH_PARENT, MATCH_PARENT)
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun saveBase64ToFile(base64: String, filename: String): File {
        val dir = getExternalFilesDir("paint") ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, filename)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        f.writeBytes(bytes)
        return f
    }

    inner class Bridge {

        @JavascriptInterface
        fun saveFile(base64: String, filename: String, mime: String): String {
            return try {
                val f = saveBase64ToFile(base64, filename)
                "已保存：${f.absolutePath}"
            } catch (e: Exception) {
                "保存失败：${e.message}"
            }
        }

        @JavascriptInterface
        fun shareFile(base64: String, filename: String, mime: String): String {
            return try {
                val f = saveBase64ToFile(base64, filename)
                val uri: Uri = FileProvider.getUriForFile(
                    this@PaintActivity,
                    "${packageName}.fileProvider",
                    f
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime.ifBlank { "application/octet-stream" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, filename))
                "已分享：$filename"
            } catch (e: Exception) {
                "分享失败：${e.message}"
            }
        }

        @JavascriptInterface
        fun deviceInfo(): String {
            return JSONObject().apply {
                put("platform", "android")
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("sdk", Build.VERSION.SDK_INT)
                put("userAgent", webView.settings.userAgentString)
            }.toString()
        }

        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread { Toast.makeText(this@PaintActivity, message, Toast.LENGTH_SHORT).show() }
        }
    }
}
