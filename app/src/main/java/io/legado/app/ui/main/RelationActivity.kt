package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.relation.PersonPinyin
import io.legado.app.help.relation.PersonProfile
import io.legado.app.help.relation.RelationExtractor
import io.legado.app.model.ReadBook
import io.legado.app.utils.printOnDebug
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 人物关系图网
 * 取当前书的正文，抽人名与共现，交给 cytoscape 画力导向图
 */
class RelationActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @Volatile
    private var lastGraph: RelationExtractor.Graph? = null

    /** 页面没加载完就先存着，onPageFinished 再画 */
    @Volatile
    private var pendingJson: String? = null

    @Volatile
    private var pageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.setBackgroundColor(0xFF05060F.toInt())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                val json = pendingJson ?: return
                render(view, json)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(PersonBridge(), "AndroidPerson")
        setContentView(webView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        webView.loadUrl("https://appassets.androidplatform.net/assets/relation/index.html")

        buildGraph()
    }

    private fun buildGraph() {
        val book = ReadBook.book
        if (book == null) {
            Toast.makeText(this, "先打开一本书", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Coroutine.async {
            PersonProfile.ensureLoaded(this@RelationActivity)
            val text = readLocalText(book.bookUrl)
            if (text.isNullOrBlank()) {
                onUi {
                    Toast.makeText(
                        this@RelationActivity,
                        "这本是书源的书，正文还没落盘；先缓存几章或导入本地 TXT",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@async
            }
            val graph = RelationExtractor.extract(listOf(book.name to text), book.name)
            lastGraph = graph
            if (graph.nodes.isEmpty()) {
                onUi {
                    Toast.makeText(
                        this@RelationActivity,
                        "没抽到稳定人名，换个书名或章节试试",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@async
            }
            val json = toJson(graph)
            pendingJson = json
            onUi {
                if (pageReady) {
                    render(webView, json)
                }
            }
        }.onError {
            it.printOnDebug()
        }
    }

    inner class PersonBridge {
        @android.webkit.JavascriptInterface
        fun open(name: String) {
            val g = lastGraph
            val app = g?.nodes?.firstOrNull { it.name == name || it.id == name }
            val arr = JSONArray()
            app?.appearances?.forEach { a ->
                val o = JSONObject()
                o.put("chapter", a.chapter)
                o.put("text", a.text)
                arr.put(o)
            }
            val intent = Intent(this@RelationActivity, PersonDetailActivity::class.java)
                .putExtra("name", name)
                .putExtra("appearances", arr.toString())
            startActivity(intent)
        }
    }

    private fun onUi(block: () -> Unit) {
        runOnUiThread(block)
    }

    /** 本地 TXT 直读，够内置古籍用 */
    private fun readLocalText(bookUrl: String): String? {
        return runCatching {
            val uri = Uri.parse(bookUrl)
            val path = uri.path ?: return null
            val f = File(path)
            if (!f.exists() || f.length() == 0L) return null
            if (f.length() > 12L * 1024 * 1024) {
                f.bufferedReader().use { r ->
                    val sb = StringBuilder(1024 * 1024)
                    var line = r.readLine()
                    while (line != null && sb.length < 4_000_000) {
                        sb.append(line).append('\n')
                        line = r.readLine()
                    }
                    sb.toString()
                }
            } else {
                f.readText()
            }
        }.getOrNull()
    }

    /**
     * 渲染入口：整串 JSON 先做 JS 字符串字面量转义，避免引号/换行把脚本截断；
     * 捕获异常并把错误回传到日志，不再静默失败。
     */
    private fun render(view: WebView, json: String) {
        val quoted = JSONObject.quote(json)
        val js = "try{" +
            "if(!window.renderGraph){throw new Error('renderGraph 未定义');}" +
            "window.renderGraph(JSON.parse($quoted));" +
            "'ok'" +
            "}catch(e){'ERR:'+e.message}"
        view.evaluateJavascript(js) { r ->
            if (r != null && r.contains("ERR")) {
                android.util.Log.e("RelationGraph", "render failed: $r")
            }
        }
    }

    private fun toJson(graph: RelationExtractor.Graph): String {
        val root = JSONObject()
        root.put("source", graph.source)
        val nodeArr = JSONArray()
        graph.nodes.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id)
            o.put("name", n.name)
            o.put("faction", n.faction)
            o.put("count", n.count)
            o.put("identity", n.identity)
            o.put("lifeSpan", n.lifeSpan)
            val al = JSONArray()
            n.aliases.forEach { al.put(it) }
            o.put("aliases", al)
            o.put("pinyin", PersonPinyin.of(this@RelationActivity, n.name))
            val ap = JSONArray()
            n.appearances.take(2).forEach { a ->
                val ao = JSONObject()
                ao.put("chapter", a.chapter)
                ao.put("text", a.text)
                ap.put(ao)
            }
            o.put("appearances", ap)
            nodeArr.put(o)
        }
        root.put("nodes", nodeArr)
        val edgeArr = JSONArray()
        graph.edges.forEach { e ->
            val o = JSONObject()
            o.put("source", e.source)
            o.put("target", e.target)
            o.put("weight", e.weight)
            o.put("label", e.label)
            edgeArr.put(o)
        }
        root.put("edges", edgeArr)
        return root.toString()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
