package io.legado.app.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.place.PlaceDict
import io.legado.app.help.place.PlaceGeo
import org.json.JSONArray
import org.json.JSONObject

/**
 * 古今地图：点地名进来，看它在今天哪儿、当时周边有哪些都邑山川。
 *
 * 数据接入层只走 D3 地名新栈 [PlaceDict] / [PlaceGeo]（CHGIS 两表 + 18 朝代图层 +
 * Tier 分层 + 同名消歧）；旧的 `help.PlaceGeo` / `help.PlaceTimeDict` 扁平 txt 栈不再读取。
 * D3 是纯 Kotlin / 纯 JVM 目录，无需 Context 预热，查无候选返回空列表，不塞假坐标。
 */
class PlaceMapActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_map)

        val name = intent.getStringExtra("name").orEmpty()
        findViewById<TextView>(R.id.btn_battle).setOnClickListener {
            startActivity(
                Intent(this, BattleActivity::class.java).apply {
                    putExtra("campaign", intent.getStringExtra("campaign") ?: "gaixia")
                    ContextAnchor.getYear()?.let { putExtra("year", it) }
                }
            )
        }

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

        // 锚点年优先取 Intent 透传值，其次共享时空锚点；D3 按此做同名异地的年代消歧
        val yearExtra = intent.getIntExtra("year", Int.MIN_VALUE)
        val year = if (yearExtra != Int.MIN_VALUE) yearExtra else ContextAnchor.get().year

        // D3 新栈：名字 → 全部候选（年代命中优先）→ 默认命中（非 low、非今址参考层）；
        // 默认命中为空时回退候选首项仅作参照，绝不凭空造坐标。
        val candidates = PlaceDict.find(name, year)
        val hit = PlaceDict.defaultOf(candidates) ?: candidates.firstOrNull()
        val geo = hit?.geo ?: hit?.placeId?.let { PlaceGeo.of(it) }

        val tvEmpty = findViewById<TextView>(R.id.tv_empty)
        val web = findViewById<WebView>(R.id.web)

        val lat: Double
        val lng: Double
        when {
            hit == null -> {
                // 两表均未收录：走 Missing 兜底，不崩、不空白态之外的假点位
                tvEmpty.visibility = View.VISIBLE
                web.visibility = View.GONE
                return
            }

            geo == null || geo.lat == null || geo.lng == null -> {
                // Tier3 文献推定只到县，D3 铁律不给点位坐标；沙盘/二维图都只能标点，
                // 这里把色块说明如实展示出来，不把「大概在某县」伪装成百米精度的点。
                tvEmpty.text = geo?.regionLabel?.takeIf { it.isNotBlank() }
                    ?: hit.note.takeIf { it.isNotBlank() }
                    ?: "此地文献推定到县，未收录可标点的坐标。"
                tvEmpty.visibility = View.VISIBLE
                web.visibility = View.GONE
                return
            }

            // 上面两支已挡掉 geo 为空 / 坐标为空，落到这里经分支兜底后均为非空
            else -> {
                lat = geo.lat
                lng = geo.lng
            }
        }

        // 周边参照点：从 D3 全量在册条目里取有坐标、距离最近的若干，排除自身；
        // 旧栈的 PlaceGeo.nearby 已随扁平 txt 栈废弃，近邻改在冻结 API `all()` 之上计算。
        val old = JSONArray()
        val baseLat = lat
        val baseLng = lng
        PlaceDict.all().asSequence()
            .filter { it.placeId != hit.placeId }
            .mapNotNull { e ->
                val g = e.geo ?: return@mapNotNull null
                val la = g.lat ?: return@mapNotNull null
                val ln = g.lng ?: return@mapNotNull null
                Neighbor(e.name, la, ln, e.note, dist2(la, ln, baseLat, baseLng))
            }
            .sortedBy { it.dist2 }
            .take(8)
            .forEach { n ->
                old.put(JSONObject().apply {
                    put("name", n.name)
                    put("lat", n.lat)
                    put("lng", n.lng)
                    put("modern", n.modern)
                })
            }

        // 今址 / 沿革说明：实体沿革注 → 色块推定说明 → 出处标注，逐级兜底；
        // regionLabel 本身可空，必须走安全调用再交给 takeIf。
        val modern = hit.note.takeIf { it.isNotBlank() }
            ?: geo.regionLabel?.takeIf { it.isNotBlank() }
            ?: hit.source.label

        // 点面铁律：只有可落点位的 Tier1/Tier2 点实体给高倍缩放；面 / 色块压到低倍
        val zoom = when {
            geo.canPlotPoint -> 8
            geo.isBlock -> 6
            else -> 8
        }

        val payload = JSONObject().apply {
            put("name", hit.name.ifBlank { name })
            put("lat", lat)
            put("lng", lng)
            put("modern", modern)
            put("zoom", zoom.toDouble())
            put("old", old)
            put("year", year ?: Int.MIN_VALUE)
            put("era", ContextAnchor.eraOf(year).orEmpty())
            put("kind", hit.kind.code.toString())
            put("tier", hit.tier.level)
        }

        configureWeb(web)

        val data = java.net.URLEncoder.encode(payload.toString(), "UTF-8")
        web.loadUrl("file:///android_asset/map/three.html?d=$data")
    }

    /** 近邻排序用的中间结构；平方距离（经度按 0.85 压扁，近似同纬度投影，仅用于就近排序）。 */
    private data class Neighbor(
        val name: String,
        val lat: Double,
        val lng: Double,
        val modern: String,
        val dist2: Double,
    )

    private fun dist2(lat: Double, lng: Double, baseLat: Double, baseLng: Double): Double {
        val dx = (lng - baseLng) * 0.85
        val dy = lat - baseLat
        return dx * dx + dy * dy
    }

    private fun configureWeb(web: WebView): WebView {
        val settings = web.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        return web
    }
}
