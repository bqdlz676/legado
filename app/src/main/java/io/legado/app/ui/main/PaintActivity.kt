package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.util.Base64
import android.view.ViewGroup
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import io.legado.app.R
import org.json.JSONObject
import java.io.File

/**
 * 墨板 · 画板
 * WebView 承载 InkSlate 前端（Excalidraw + peinten），走 appassets 域名避免 file:// 限制。
 */
class PaintActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var lastPressure = 1f

    private val bookName: String by lazy { intent.getStringExtra("bookName").orEmpty() }
    private val chapterName: String by lazy { intent.getStringExtra("chapterName").orEmpty() }
    private val pageIndex: Int by lazy { intent.getIntExtra("pageIndex", 0) }
    private val pageText: String by lazy { intent.getStringExtra("pageText").orEmpty() }

    private val doubleTap by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 双击 = 撤销，所有画板 App 的肌肉记忆
                webView.evaluateJavascript(
                    "(function(){var ev=new KeyboardEvent('keydown'," +
                        "{key:'z',code:'KeyZ',ctrlKey:true,metaKey:true,bubbles:true});" +
                        "document.dispatchEvent(ev);})()", null
                )
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastPressure = ev.pressure
        doubleTap.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bgPath = intent.getStringExtra("bgImage")
        val bookName = intent.getStringExtra("bookName") ?: ""

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/annot/", object : WebViewAssetLoader.PathHandler {
                override fun handle(path: String): WebResourceResponse? {
                    val file = File(filesDir, "annot_bg/$path")
                    if (!file.exists()) return null
                    return WebResourceResponse("image/png", "utf-8", file.inputStream())
                }
            })
            .build()

        // 预热过的直接拿，拿不到才 new；转移时先从旧 parent 摘下来
        val pooled = InkSlatePreloader.take()
        webView = pooled ?: WebView(this)
        (webView.parent as? ViewGroup)?.removeView(webView)
        // 落文主题：纸白底，不是硬黑
        webView.setBackgroundColor(0xFFF7F1E6.toInt())
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

        // 池里拿回来的要重挂桥，旧对象已被替换
        webView.addJavascriptInterface(Bridge(), "InkSlate")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
        }
        webView.webChromeClient = WebChromeClient()

        setContentView(webView, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        // 透明状态栏主题下内容会画到状态栏底下，这里补上系统栏与挖孔的内边距
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val url = if (bgPath != null) {
            val name = File(bgPath).name
            "https://appassets.androidplatform.net/assets/www/index.html" +
                "?mode=annotation&bg=/annot/$name&bookName=${Uri.encode(bookName)}" +
                "&chapter=${Uri.encode(chapterName)}&page=$pageIndex" +
                "&note=${Uri.encode(noteTitle())}"
        } else {
            "https://appassets.androidplatform.net/assets/www/index.html"
        }
        webView.loadUrl(url)
        overridePendingTransition(R.anim.ink_slide_in, R.anim.ink_none)
    }

    override fun onDestroy() {
        // 不 destroy：摘下来还回池子，下次 remove/add 转移
        (webView.parent as? ViewGroup)?.removeView(webView)
        InkSlatePreloader.putBack(webView)
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.ink_none, R.anim.ink_slide_out)
    }

    /** 导出时附在底部的出处行：让画板是阅读的一部分 */
    private fun noteTitle(): String {
        val b = bookName.ifBlank { "落文" }
        val c = chapterName.ifBlank { "" }
        return if (c.isBlank()) "《$b》第 ${pageIndex + 1} 页批注"
        else "《$b·$c》第 ${pageIndex + 1} 页批注"
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

        /** SVG 导出：无限放大的矢量图，古书批注比 PNG 更合适 */
        @JavascriptInterface
        fun saveSvg(svg: String, filename: String): String {
            return try {
                val dir = getExternalFilesDir("paint") ?: filesDir
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, filename.ifBlank { "ink.svg" })
                f.writeText(svg)
                "已保存：${f.absolutePath}"
            } catch (e: Exception) {
                "保存失败：${e.message}"
            }
        }

        /** 系统主题色推给前端，画笔跟 App 走 */
        @JavascriptInterface
        fun themeColors(): String = JSONObject().apply {
            put("bg", "#F7F1E6")
            put("surface", "#EFE8D8")
            put("text", "#1A1410")
            put("secondary", "#5C4F3A")
            put("tertiary", "#9B9687")
            put("accent", "#C23B2E")
            put("gold", "#D4A54A")
            put("copper", "#8B6914")
        }.toString()

        /** 压感通道：前端 pointermove 时拉当前压力，线宽随压力变 */
        @JavascriptInterface
        fun pointerPressure(): Float = lastPressure

        /** 当前页上下文：章节名、页码、正文，画板里可以引用原文 */
        @JavascriptInterface
        fun pageContext(): String = JSONObject().apply {
            put("book", bookName)
            put("chapter", chapterName)
            put("page", pageIndex)
            put("text", pageText.take(2000))
            put("note", noteTitle())
        }.toString()

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
