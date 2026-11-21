package io.legado.app.help

import android.content.Context
import org.json.JSONObject

/**
 * 古地名坐标：place_geo.json，用于「古今地图」页定位与周边同名点。
 */
object PlaceGeo {

    data class Geo(
        val name: String,
        val lat: Double,
        val lng: Double,
        val modern: String,
        val zoom: Int,
    )

    @Volatile
    private var table: Map<String, Geo>? = null

    fun ensureLoaded(context: Context) {
        if (table == null) load(context)
    }

    fun of(context: Context, name: String): Geo? = load(context)[name]

    /** 同区域参照点：按经纬度距离取最近的若干个 */
    fun nearby(context: Context, geo: Geo, count: Int = 8): List<Geo> =
        load(context).values
            .filter { it.name != geo.name }
            .sortedBy { dist2(it, geo) }
            .take(count)

    private fun dist2(a: Geo, b: Geo): Double {
        val dx = (a.lng - b.lng) * 0.85
        val dy = a.lat - b.lat
        return dx * dx + dy * dy
    }

    private fun load(context: Context): Map<String, Geo> {
        table?.let { return it }
        synchronized(this) {
            table?.let { return it }
            val map = HashMap<String, Geo>(80)
            runCatching {
                val json = context.applicationContext.assets
                    .open("place_geo.json").bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                for (key in root.keys()) {
                    val o = root.optJSONObject(key) ?: continue
                    map[key] = Geo(
                        name = key,
                        lat = o.optDouble("lat", 0.0),
                        lng = o.optDouble("lng", 0.0),
                        modern = o.optString("modern", ""),
                        zoom = o.optInt("zoom", 8),
                    )
                }
            }
            table = map
            return map
        }
    }
}
