package io.legado.app.ui.main

import android.annotation.SuppressLint
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
        }
        webView.webChromeClient = WebChromeClient()
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
            onUi {
                webView.evaluateJavascript("window.renderGraph && window.renderGraph($json)", null)
            }
        }.onError {
            it.printOnDebug()
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
