package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.PlaceGeo
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
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val geo = PlaceGeo.of(this, name)
        if (geo == null) {
            findViewById<TextView>(R.id.tv_empty).visibility = View.VISIBLE
            findViewById<WebView>(R.id.web).visibility = View.GONE
            return
        }

        val old = JSONArray()
        for (p in PlaceGeo.nearby(this, geo)) {
            old.put(JSONObject().apply {
                put("name", p.name)
                put("lat", p.lat)
                put("lng", p.lng)
                put("modern", p.modern)
            })
        }
        val payload = JSONObject().apply {
            put("name", geo.name)
            put("lat", geo.lat)
            put("lng", geo.lng)
            put("modern", geo.modern)
            put("zoom", geo.zoom)
            put("old", old)
        }

        val web = findViewById<WebView>(R.id.web)
        val settings = web.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        val data = java.net.URLEncoder.encode(payload.toString(), "UTF-8")
        web.loadUrl("file:///android_asset/map/index.html?d=$data")
    }
}
