package io.legado.app.ui.map.sandbox

/**
 * 沙盘种子兜底数据（纯 JVM，切片-06 点名的城池 / 水系 / 势力样板）。
 *
 * 为什么用内联样板而不是直接解析 assets/map 目录下各 .js：模型层必须在普通 JVM 单测里可构造，
 * 不引 Rhino / Android 资源；批量水系与疆域由独立数据管道灌入，本对象只保证空数据时
 * 沙盘仍有一套可渲染的春秋样板，不出现空白态。坐标为公开经纬度事实，疆域为示意边界。
 */
object SeedSandbox {

    // ── 势力样板：晋（己方）/ 秦（敌方）/ 齐（盟友）/ 周（中立）──

    val powers: List<PowerPolygon> = listOf(
        PowerPolygon(
            id = "jin", name = "晋", role = FactionRole.OWN,
            fromYear = -770, toYear = -453,
            pts = listOf(
                LatLng(35.6, 113.64), LatLng(36.57, 112.59), LatLng(37.38, 111.40),
                LatLng(36.73, 110.02), LatLng(35.60, 109.16), LatLng(34.56, 110.12),
                LatLng(33.81, 111.40), LatLng(34.47, 112.79),
            ),
        ),
        PowerPolygon(
            id = "qin", name = "秦", role = FactionRole.ENEMY,
            fromYear = -770, toYear = -207,
            pts = listOf(
                LatLng(34.5, 107.5), LatLng(35.5, 108.25), LatLng(35.5, 109.75),
                LatLng(34.5, 110.5), LatLng(33.5, 109.75), LatLng(33.5, 108.25),
            ),
        ),
        PowerPolygon(
            id = "qi", name = "齐", role = FactionRole.ALLY,
            fromYear = -770, toYear = -476,
            pts = listOf(
                LatLng(36.80, 119.53), LatLng(37.51, 118.94), LatLng(37.81, 118.05),
                LatLng(37.53, 117.13), LatLng(36.80, 116.67), LatLng(36.08, 117.16),
                LatLng(35.91, 118.05), LatLng(36.06, 118.97),
            ),
        ),
        PowerPolygon(
            id = "zhou", name = "周", role = FactionRole.NEUTRAL,
            fromYear = -770, toYear = -256,
            pts = listOf(
                LatLng(34.5, 111.0), LatLng(35.2, 111.63), LatLng(35.2, 112.88),
                LatLng(34.5, 113.5), LatLng(33.8, 112.88), LatLng(33.8, 111.63),
            ),
        ),
    )

    // ── 三级城池样板（切片-06 §二 点名示例）──

    val cities: List<CityNode> = listOf(
        CityNode("changan", "长安", 34.34, 108.94, CityTier.CAPITAL),
        CityNode("luoyang", "洛阳", 34.62, 112.45, CityTier.CAPITAL),
        CityNode("linzi", "临淄", 36.81, 118.30, CityTier.CAPITAL),
        CityNode("ying", "郢", 30.57, 112.17, CityTier.CAPITAL),
        CityNode("xianyang", "咸阳", 34.33, 108.71, CityTier.CAPITAL),
        CityNode("hanzhong", "汉中", 33.07, 107.03, CityTier.COUNTY),
        CityNode("chenliu", "陈留", 34.62, 114.45, CityTier.COUNTY),
        CityNode("nanyang", "南阳", 32.99, 112.53, CityTier.COUNTY),
        CityNode("jiangling", "江陵", 30.35, 112.24, CityTier.COUNTY),
        CityNode("hanguguan", "函谷关", 34.63, 110.94, CityTier.PASS_FERRY),
        CityNode("wuguan", "武关", 33.52, 110.15, CityTier.PASS_FERRY),
        CityNode("mengjin", "孟津", 34.82, 112.62, CityTier.PASS_FERRY),
        CityNode("maojin", "茅津", 34.72, 111.85, CityTier.PASS_FERRY),
    )

    // ── 河流样板：黄河三版改道 + 渭水 / 济水 / 汉水 / 淮河 / 长江 ──

    val rivers: List<RiverLine> = listOf(
        RiverLine(
            id = "huanghe_preqin", name = "黄河", eraLabel = "唐前",
            fromYear = -1046, toYear = 617, version = "唐前",
            pts = listOf(
                LatLng(36.0, 103.8), LatLng(38.5, 106.3), LatLng(40.5, 108.0),
                LatLng(40.1, 111.1), LatLng(35.7, 110.6), LatLng(34.6, 110.3),
                LatLng(34.8, 111.2), LatLng(35.2, 113.2), LatLng(36.0, 115.0),
                LatLng(37.7, 115.7), LatLng(39.1, 117.2),
            ),
            valleyWidth = 1.2,
        ),
        RiverLine(
            id = "huanghe_tang", name = "黄河", eraLabel = "唐宋",
            fromYear = 618, toYear = 1047, version = "唐宋",
            pts = listOf(
                LatLng(36.0, 103.8), LatLng(38.5, 106.3), LatLng(40.5, 108.0),
                LatLng(40.1, 111.1), LatLng(35.7, 110.6), LatLng(34.6, 110.3),
                LatLng(34.8, 113.0), LatLng(35.6, 114.8), LatLng(36.3, 115.5),
                LatLng(37.5, 116.5), LatLng(38.3, 117.5), LatLng(39.0, 118.2),
            ),
            valleyWidth = 1.2,
        ),
        RiverLine(
            id = "huanghe_qing", name = "黄河", eraLabel = "明清",
            fromYear = 1855, toYear = 1911, version = "明清",
            pts = listOf(
                LatLng(36.0, 103.8), LatLng(38.5, 106.3), LatLng(40.5, 108.0),
                LatLng(40.1, 111.1), LatLng(35.7, 110.6), LatLng(34.6, 110.3),
                LatLng(34.8, 113.0), LatLng(34.8, 114.3), LatLng(35.4, 115.4),
                LatLng(36.6, 116.8), LatLng(37.5, 117.5), LatLng(37.8, 118.5),
                LatLng(37.5, 119.2),
            ),
            valleyWidth = 1.2,
        ),
        RiverLine(
            id = "weishui", name = "渭水", eraLabel = "all",
            fromYear = null, toYear = null, version = null,
            pts = listOf(
                LatLng(34.6, 104.5), LatLng(34.6, 106.0), LatLng(34.3, 106.8),
                LatLng(34.6, 108.2), LatLng(34.8, 109.5),
            ),
            valleyWidth = 0.7,
        ),
        RiverLine(
            id = "jishui", name = "济水", eraLabel = "唐前",
            fromYear = -1046, toYear = 900, version = null,
            pts = listOf(
                LatLng(35.0, 112.0), LatLng(34.9, 113.5), LatLng(35.2, 115.0),
                LatLng(35.4, 116.5), LatLng(35.7, 117.5), LatLng(37.0, 118.5),
            ),
            valleyWidth = 0.6,
        ),
        RiverLine(
            id = "hanshui", name = "汉水", eraLabel = "all",
            fromYear = null, toYear = null, version = null,
            pts = listOf(
                LatLng(33.1, 106.0), LatLng(32.8, 108.0), LatLng(32.5, 110.0),
                LatLng(32.8, 111.5), LatLng(30.5, 112.3),
            ),
            valleyWidth = 0.8,
        ),
        RiverLine(
            id = "huaihe", name = "淮河", eraLabel = "all",
            fromYear = null, toYear = null, version = null,
            pts = listOf(
                LatLng(32.4, 113.0), LatLng(32.3, 114.5), LatLng(32.5, 116.0),
                LatLng(32.2, 117.5), LatLng(32.0, 119.0), LatLng(32.2, 120.3),
            ),
            valleyWidth = 0.8,
        ),
        RiverLine(
            id = "changjiang", name = "长江", eraLabel = "all",
            fromYear = null, toYear = null, version = null,
            pts = listOf(
                LatLng(30.5, 104.0), LatLng(30.7, 106.5), LatLng(30.5, 108.5),
                LatLng(30.5, 110.0), LatLng(30.3, 112.5), LatLng(30.5, 114.5),
                LatLng(30.4, 117.0), LatLng(31.2, 120.0),
            ),
            valleyWidth = 1.0,
        ),
    )

    // ── 关隘 / 峡谷 ──

    val chokes: List<ChokePoint> = listOf(
        ChokePoint("hanguguan_choke", "函谷关", 34.63, 110.94, headingDeg = 80.0),
        ChokePoint("wuguan_choke", "武关", 33.52, 110.15, headingDeg = 65.0),
        ChokePoint("mengjin_choke", "孟津渡", 34.82, 112.62, headingDeg = 95.0),
        ChokePoint("maojin_choke", "茅津渡", 34.72, 111.85, headingDeg = 90.0),
    )

    // ── 战场遗址方钉样板 ──

    val sites: List<SitePin> = listOf(
        SitePin("yinxu", "殷墟", 36.13, 114.32, "遗址", "商代晚期都城，甲骨出土地"),
        SitePin("youlicheng", "羑里城", 35.95, 114.38, "遗址", "文王演周易之地"),
        SitePin("zhaoge", "朝歌", 35.61, 114.20, "城址", "商末别都"),
        SitePin("xinya", "崤之战遗址", 34.60, 111.20, "战场", "晋襄公伏兵殽山，歼秦师",
            report = "僖公三十三年，秦师过周北门，晋御于殽，获其三帅。"),
    )
}
