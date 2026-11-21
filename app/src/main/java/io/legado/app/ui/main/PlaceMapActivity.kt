package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.PlaceGeo
import io.legado.app.help.PlaceTimeDict
import io.legado.app.help.chrono.ContextAnchor
import org.json.JSONArray
import org.json.JSONObject

/**
 * 古今地图：点地名进来，看它在今天哪儿、当时周边有哪些都邑山川。
 */
class PlaceMapActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_map)

        val name = intent.getStringExtra("name").orEmpty()

        val wlat = intent.getDoubleExtra("wlat", Double.NaN)
        val wlng = intent.getDoubleExtra("wlng", Double.NaN)
        val isWorld = !wlat.isNaN() && !wlng.isNaN()

        if (isWorld) {
            val payload = JSONObject().apply {
                put("name", name)
                put("lat", wlat)
                put("lng", wlng)
                put("wlat", wlat)
                put("wlng", wlng)
                put("zoom", 5)
                put("modern", "")
                put("note", "世界历史地名")
                put("old", JSONArray())
            }
            val w = findViewById<WebView>(R.id.web)
            configureWeb(w)
            w.loadUrl("file:///android_asset/map/three.html?d=" +
                java.net.URLEncoder.encode(payload.toString(), "UTF-8"))
            return
        }

        // 先按时空本体定位（同名靠年份筛，点面分开）；未收录再退回旧坐标表
        val yearExtra = intent.getIntExtra("year", Int.MIN_VALUE)
        val year = if (yearExtra != Int.MIN_VALUE) yearExtra else ContextAnchor.get().year
        PlaceTimeDict.ensureLoaded(this)
        val st = PlaceTimeDict.resolve(this, name, year)
        val geo = PlaceGeo.of(this, name)
        if (st.hit == null && geo == null) {
            findViewById<TextView>(R.id.tv_empty).visibility = View.VISIBLE
            findViewById<WebView>(R.id.web).visibility = View.GONE
            return
        }

        val hit = st.hit
        val lat = hit?.lat ?: geo!!.lat
        val lng = hit?.lng ?: geo!!.lng

        val old = JSONArray()
        for (p in PlaceGeo.nearby(this, geo ?: PlaceGeo.Geo(name, lat, lng, "", 8))) {
            old.put(JSONObject().apply {
                put("name", p.name)
                put("lat", p.lat)
                put("lng", p.lng)
                put("modern", p.modern)
            })
        }
        val payload = JSONObject().apply {
            put("name", hit?.name ?: geo?.name ?: name)
            put("lat", lat)
            put("lng", lng)
            put("modern", hit?.note ?: geo?.modern.orEmpty())
            put("zoom", (hit?.let { if (it.isPoint) 8 else 6 } ?: geo?.zoom ?: 8).toDouble())
            put("old", old)
            put("year", year ?: Int.MIN_VALUE)
            put("era", ContextAnchor.eraOf(year).orEmpty())
            put("kind", hit?.kind?.toString().orEmpty())
            put("tier", hit?.tier ?: Int.MIN_VALUE)
        }

        val web = findViewById<WebView>(R.id.web)
        configureWeb(web)

        val data = java.net.URLEncoder.encode(payload.toString(), "UTF-8")
        web.loadUrl("file:///android_asset/map/three.html?d=$data")
    }

    private fun configureWeb(web: WebView): WebView {
        val settings = web.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        return web
    }
}
