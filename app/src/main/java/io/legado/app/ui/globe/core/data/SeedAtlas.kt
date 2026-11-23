package io.legado.app.ui.globe.core.data

/**
 * 内置最小种子地名 atlas（纯 JVM，离线兜底）。
 *
 * 零 copyleft：这里只存通用地名与公开经纬度坐标，不灌入 Wikipedia / OSM /
 * CC-CEDICT 的文案或条目；既有 assets/map 的 cities.js、world_places.js 只读参考，
 * 本包不依赖它们也能独立运行——无网时用这份种子兜底，不空白、不崩溃。
 *
 * 切片 19：都城（长安/罗马/雅典…）任何缩放级别显示，郡邑放大后才出现。
 */
object SeedAtlas {

    val places: List<GlobePlace> = listOf(
        // —— 华夏都城 / 名城 ——
        GlobePlace("长安", 34.26, 108.94, PlaceTier.CAPITAL, "关中"),
        GlobePlace("北京", 39.90, 116.40, PlaceTier.CAPITAL, "华北"),
        GlobePlace("洛阳", 34.62, 112.45, PlaceTier.NORMAL, "河洛"),
        GlobePlace("南京", 32.06, 118.80, PlaceTier.NORMAL, "江南"),
        GlobePlace("成都", 30.66, 104.07, PlaceTier.NORMAL, "巴蜀"),
        GlobePlace("广州", 23.13, 113.26, PlaceTier.NORMAL, "岭南"),
        GlobePlace("敦煌", 40.14, 94.66, PlaceTier.NORMAL, "河西"),
        // —— 世界古都 ——
        GlobePlace("罗马", 41.90, 12.50, PlaceTier.CAPITAL, "意大利"),
        GlobePlace("雅典", 37.98, 23.73, PlaceTier.CAPITAL, "爱琴海"),
        GlobePlace("开罗", 30.04, 31.24, PlaceTier.CAPITAL, "尼罗河"),
        GlobePlace("巴格达", 33.31, 44.37, PlaceTier.CAPITAL, "两河流域"),
        GlobePlace("君士坦丁堡", 41.01, 28.98, PlaceTier.CAPITAL, "博斯普鲁斯"),
        GlobePlace("撒马尔罕", 39.65, 66.96, PlaceTier.NORMAL, "中亚"),
        GlobePlace("德里", 28.61, 77.21, PlaceTier.CAPITAL, "印度"),
        GlobePlace("华氏城", 25.60, 85.14, PlaceTier.NORMAL, "恒河"),
        GlobePlace("京都", 35.01, 135.77, PlaceTier.NORMAL, "日本"),
        GlobePlace("吴哥", 13.41, 103.87, PlaceTier.NORMAL, "中南半岛"),
        GlobePlace("波斯波利斯", 29.93, 52.89, PlaceTier.NORMAL, "波斯"),
        GlobePlace("迦太基", 36.85, 10.32, PlaceTier.NORMAL, "北非"),
        GlobePlace("巴黎", 48.86, 2.35, PlaceTier.CAPITAL, "法兰西"),
        GlobePlace("伦敦", 51.51, -0.13, PlaceTier.CAPITAL, "不列颠"),
        GlobePlace("莫斯科", 55.76, 37.62, PlaceTier.CAPITAL, "罗斯"),
        GlobePlace("纽约", 40.71, -74.01, PlaceTier.NORMAL, "北美"),
        GlobePlace("墨西哥城", 19.43, -99.13, PlaceTier.CAPITAL, "中美"),
        GlobePlace("库斯科", -13.53, -71.97, PlaceTier.NORMAL, "南美"),
    )

    /** 一般地名浮出的距离比阈值（相机距离 / 半径）。 */
    const val NORMAL_LABEL_NEAR_RATIO: Double = 2.0

    /**
     * 按当前缩放返回应显示的地名与淡出系数。
     * @param distanceRatio 相机距离 / 球半径
     * @return 地名 → 可见透明度 0..1
     */
    fun visible(distanceRatio: Double): List<Pair<GlobePlace, Float>> = places.map { p ->
        val fade = when (p.tier) {
            PlaceTier.CAPITAL -> 1f
            PlaceTier.NORMAL -> {
                if (distanceRatio >= NORMAL_LABEL_NEAR_RATIO) 0f
                else ((NORMAL_LABEL_NEAR_RATIO - distanceRatio) / NORMAL_LABEL_NEAR_RATIO).toFloat()
            }
        }
        p to fade.coerceIn(0f, 1f)
    }
}
