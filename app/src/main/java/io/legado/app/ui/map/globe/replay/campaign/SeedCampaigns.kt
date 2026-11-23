package io.legado.app.ui.map.globe.replay.campaign

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.replay.route.RouteWaypoint

/**
 * 6 场国外战役种子兜底（纯 JVM，切片 18 时间轴表）。
 *
 * 回合数严格对齐规格：汉尼拔翻越阿尔卑斯 20、亚历山大东征 30、十字军东征 20、
 * 蒙古西征 30、维京渡海 15、大航海哥伦布 10。
 *
 * 零 copyleft：途经点为公开经纬度事实，战役叙述只留通用阶段名；批量 battle 条目
 * 拉取属独立数据管道任务，本模块不依赖网络也能用这份种子跑通回放。
 */
object SeedCampaigns {

    val all: List<Campaign> = listOf(
        Campaign(
            id = "hannibal_alps",
            title = "汉尼拔翻越阿尔卑斯",
            era = "前218",
            context = "迦太基军自西班牙出发，翻越阿尔卑斯山进入意大利，转战罗马腹地。",
            rounds = 20,
            sides = listOf(
                CampaignSide("carthage", "迦太基"),
                CampaignSide("rome", "罗马"),
            ),
            waypoints = listOf(
                RouteWaypoint("迦太基", 36.85, 10.32),
                RouteWaypoint("卡塔赫纳", 37.60, -0.98),
                RouteWaypoint("南高卢", 43.30, 3.20),
                RouteWaypoint("阿尔卑斯隘口", 45.07, 7.69),
                RouteWaypoint("罗马", 41.90, 12.50),
            ),
            phases = listOf(
                CampaignPhase(0, "出兵迦太基"),
                CampaignPhase(6, "渡海抵西班牙"),
                CampaignPhase(12, "翻越阿尔卑斯"),
                CampaignPhase(18, "进入意大利"),
            ),
            dailyLi = GlobeConfig.DAILY_LI_SLOW,
            factionIds = listOf("carthage", "rome"),
        ),
        Campaign(
            id = "alexander_east",
            title = "亚历山大东征",
            era = "前334",
            context = "马其顿军自希腊出发，经小亚细亚、埃及、波斯，直抵印度河流域。",
            rounds = 30,
            sides = listOf(
                CampaignSide("macedon", "马其顿"),
                CampaignSide("persia", "波斯"),
            ),
            waypoints = listOf(
                RouteWaypoint("佩拉", 40.75, 22.52),
                RouteWaypoint("以弗所", 37.94, 27.34),
                RouteWaypoint("大马士革", 33.51, 36.30),
                RouteWaypoint("孟菲斯", 29.85, 31.25),
                RouteWaypoint("巴比伦", 32.54, 44.42),
                RouteWaypoint("波斯波利斯", 29.93, 52.89),
                RouteWaypoint("撒马尔罕", 39.65, 66.96),
                RouteWaypoint("印度河", 31.40, 73.10),
            ),
            phases = listOf(
                CampaignPhase(0, "出征希腊"),
                CampaignPhase(8, "平定小亚细亚"),
                CampaignPhase(14, "入埃及"),
                CampaignPhase(20, "灭波斯"),
                CampaignPhase(26, "抵印度河"),
            ),
            dailyLi = GlobeConfig.DAILY_LI_MARCH,
            factionIds = listOf("macedon", "persia"),
        ),
        Campaign(
            id = "crusade_jerusalem",
            title = "十字军东征",
            era = "1096",
            context = "十字军自君士坦丁堡进发，经安条克南下，围困并抵达耶路撒冷。",
            rounds = 20,
            sides = listOf(
                CampaignSide("crusader", "十字军"),
                CampaignSide("seljuk", "萨拉丁方"),
            ),
            waypoints = listOf(
                RouteWaypoint("君士坦丁堡", 41.01, 28.98),
                RouteWaypoint("安条克", 36.20, 36.16),
                RouteWaypoint("耶路撒冷", 31.78, 35.22),
            ),
            phases = listOf(
                CampaignPhase(0, "集结君堡"),
                CampaignPhase(10, "围困安条克"),
                CampaignPhase(18, "抵达耶路撒冷"),
            ),
            dailyLi = GlobeConfig.DAILY_LI_MARCH,
            factionIds = listOf("crusader", "seljuk"),
        ),
        Campaign(
            id = "mongol_west",
            title = "蒙古西征",
            era = "1219",
            context = "蒙古军自漠北西征，破中亚诸城，横扫南俄草原，兵抵波兰边境。",
            rounds = 30,
            sides = listOf(
                CampaignSide("mongol", "蒙古"),
                CampaignSide("byzantine", "东欧诸部"),
            ),
            waypoints = listOf(
                RouteWaypoint("哈拉和林", 47.19, 102.82),
                RouteWaypoint("撒马尔罕", 39.65, 66.96),
                RouteWaypoint("伏尔加河畔", 48.70, 44.50),
                RouteWaypoint("基辅", 50.45, 30.52),
                RouteWaypoint("克拉科夫", 50.06, 19.94),
            ),
            phases = listOf(
                CampaignPhase(0, "自漠北西征"),
                CampaignPhase(10, "破中亚诸城"),
                CampaignPhase(20, "横扫南俄草原"),
                CampaignPhase(28, "抵波兰边境"),
            ),
            dailyLi = GlobeConfig.DAILY_LI_FAST,
            factionIds = listOf("mongol", "byzantine"),
        ),
        Campaign(
            id = "viking_sea",
            title = "维京人渡海",
            era = "991",
            context = "维京船队自挪威峡湾出航，渡北海登陆不列颠，沿河深入腹地。",
            rounds = 15,
            sides = listOf(
                CampaignSide("viking", "维京"),
                CampaignSide("british", "不列颠"),
            ),
            waypoints = listOf(
                RouteWaypoint("奥斯陆峡湾", 59.91, 10.75),
                RouteWaypoint("丹麦海峡", 55.68, 12.57),
                RouteWaypoint("约克", 53.96, -1.08),
                RouteWaypoint("伦敦", 51.51, -0.13),
            ),
            phases = listOf(
                CampaignPhase(0, "出航挪威"),
                CampaignPhase(7, "渡北海"),
                CampaignPhase(12, "登陆不列颠"),
            ),
            dailyLi = GlobeConfig.DAILY_LI_MARCH,
            factionIds = listOf("viking", "british"),
        ),
        Campaign(
            id = "columbus_atlantic",
            title = "大航海哥伦布",
            era = "1492",
            context = "西班牙船队自帕洛斯启航，经加那利群岛补给，横渡大西洋抵达美洲。",
            rounds = 10,
            sides = listOf(
                CampaignSide("spain", "西班牙"),
            ),
            waypoints = listOf(
                RouteWaypoint("塞维利亚", 37.39, -5.99),
                RouteWaypoint("特内里费", 28.46, -16.25),
                RouteWaypoint("圣萨尔瓦多", 24.05, -74.45),
            ),
            phases = listOf(
                CampaignPhase(0, "自帕洛斯启航"),
                CampaignPhase(4, "抵加那利补给"),
                CampaignPhase(9, "横渡大西洋"),
            ),
            dailyLi = GlobeConfig.DAILY_LI_FAST,
            factionIds = listOf("spain"),
        ),
    )
}
