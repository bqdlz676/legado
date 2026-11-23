package io.legado.app.ui.map.replay

/**
 * 回放种子兜底战役（纯 JVM，切片-07 顶栏示例「长平之战 前260年」）。
 *
 * 字段口径对齐 assets/map/campaigns/changping.json：阵营 / 兵力 / 士气 / 意图 / 事件；
 * 坐标为公开经纬度事实。批量战役由数据管道灌入，本对象只保证空数据时回放有一场
 * 可推演的样板，不出现空白态。
 */
object SeedReplay {

    val changping: Campaign = Campaign(
        id = "changping",
        title = "长平之战",
        era = "前260",
        context = "秦赵对峙长平，赵括代廉颇，秦断粮道合围。",
        rounds = 30,
        sides = listOf(
            BattleSide("qin", "秦", SideRole.OWN),
            BattleSide("zhao", "赵", SideRole.ENEMY),
        ),
        armies = listOf(
            ArmyPiece(
                id = "baiqi", name = "白起", sideId = "qin",
                lat = 35.95, lng = 112.70, strength = 14, type = "elite",
                morale = Morale.HIGH, intent = Intent.ADVANCE,
                targetLat = 35.85, targetLng = 112.85,
            ),
            ArmyPiece(
                id = "wanghe", name = "王龁", sideId = "qin",
                lat = 35.75, lng = 112.75, strength = 10, type = "regular",
                morale = Morale.STEADY, intent = Intent.ADVANCE,
                targetLat = 35.82, targetLng = 112.88,
            ),
            ArmyPiece(
                id = "sima", name = "司马梗", sideId = "qin",
                lat = 35.70, lng = 113.00, strength = 7, type = "regular",
                morale = Morale.STEADY, intent = Intent.FLANK,
                targetLat = 35.90, targetLng = 112.90,
            ),
            ArmyPiece(
                id = "qinr1", name = "秦奇兵", sideId = "qin",
                lat = 35.92, lng = 112.98, strength = 6, type = "cavalry",
                morale = Morale.HIGH, intent = Intent.FLANK,
                targetLat = 35.78, targetLng = 112.80,
            ),
            ArmyPiece(
                id = "zhaokuo", name = "赵括", sideId = "zhao",
                lat = 35.85, lng = 112.85, strength = 12, type = "regular",
                morale = Morale.STEADY, intent = Intent.ADVANCE,
                targetLat = 35.90, targetLng = 112.72,
            ),
            ArmyPiece(
                id = "lianpo", name = "廉颇", sideId = "zhao",
                lat = 35.88, lng = 112.88, strength = 9, type = "regular",
                morale = Morale.STEADY, intent = Intent.ADVANCE,
            ),
            ArmyPiece(
                id = "zhaor1", name = "赵前军", sideId = "zhao",
                lat = 35.86, lng = 112.80, strength = 7, type = "regular",
                morale = Morale.WAVERING, intent = Intent.ADVANCE,
                targetLat = 35.92, targetLng = 112.70,
            ),
            ArmyPiece(
                id = "zhaor2", name = "赵辎重", sideId = "zhao",
                lat = 35.83, lng = 112.92, strength = 5, type = "militia",
                morale = Morale.STEADY, intent = Intent.ADVANCE,
            ),
        ),
        phases = listOf(
            BattlePhase(0, "对峙", "廉颇筑垒固守，秦赵相持于长平。"),
            BattlePhase(8, "出击", "赵括代廉颇，主动出击秦军。"),
            BattlePhase(18, "合围", "秦奇兵绝赵粮道，分割包围。"),
            BattlePhase(28, "收官", "赵军断粮四十六日，降散。"),
        ),
        events = listOf(
            BattleEvent(
                turn = 16, targetId = "lianpo", action = EventAction.MORALE_CHANGE,
                value = "wavering", reason = "赵括代廉颇，军心不服",
            ),
            BattleEvent(
                turn = 22, targetId = "zhaokuo", action = EventAction.SUPPLY_CUT,
                reason = "秦奇兵二万五千绝赵军后",
            ),
            BattleEvent(
                turn = 28, targetId = "zhaokuo", action = EventAction.SURRENDER,
                reason = "赵括战死，赵军溃散",
            ),
        ),
        dailyLi = DAILY_LI_MARCH,
    )

    /** 日行里数中档 50 里；回放模块本地口径，避免反向依赖沙盘配置。 */
    private const val DAILY_LI_MARCH: Int = 50

    /** 全部种子战役（当前一场；数据管道接入后在此扩充）。 */
    val all: List<Campaign> = listOf(changping)
}
