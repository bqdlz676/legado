package io.legado.app.ui.map.globe.data

import io.legado.app.ui.map.globe.replay.FactionPalette
import io.legado.app.ui.map.globe.replay.power.PowerPolygon
import io.legado.app.ui.map.globe.replay.power.PowerRelation

/**
 * 12 套国外势力种子兜底数据（纯 JVM，切片 18）。
 *
 * 零 copyleft：只存通用势力名与公开经纬度示意边界（事实坐标），不灌入 Wikipedia /
 * OSM / CC-CEDICT 文案，也不接 Pleiades / Wikidata / Natural Earth 批量拉数管道
 * （那是独立数据管道任务）。边界为历史地图集示意环，不代表精确疆界。
 *
 * 配色：罗马暖金半透明、波斯暗铜、蒙古朱砂红，其余 9 套同色系（见 FactionPalette）。
 */
object SeedPowers {

    val all: List<PowerPolygon> = listOf(
        PowerPolygon(
            id = "rome", name = "罗马帝国", factionId = "rome",
            colorHex = FactionPalette.hexOf("rome"), relation = PowerRelation.OWN,
            pts = listOf(
                36.0 to -5.0, 44.0 to 0.0, 45.0 to 12.0, 40.0 to 20.0,
                41.0 to 30.0, 36.0 to 35.0, 31.0 to 33.0, 31.0 to 25.0,
                33.0 to 10.0, 35.0 to -2.0,
            ),
        ),
        PowerPolygon(
            id = "persia", name = "波斯帝国", factionId = "persia",
            colorHex = FactionPalette.hexOf("persia"), relation = PowerRelation.ENEMY,
            pts = listOf(
                38.0 to 45.0, 36.0 to 55.0, 32.0 to 60.0, 28.0 to 57.0,
                27.0 to 50.0, 30.0 to 44.0, 35.0 to 44.0,
            ),
        ),
        PowerPolygon(
            id = "mongol", name = "蒙古帝国", factionId = "mongol",
            colorHex = FactionPalette.hexOf("mongol"), relation = PowerRelation.ENEMY,
            pts = listOf(
                55.0 to 90.0, 50.0 to 115.0, 45.0 to 110.0, 40.0 to 105.0,
                38.0 to 95.0, 42.0 to 80.0, 48.0 to 70.0, 52.0 to 80.0,
            ),
        ),
        PowerPolygon(
            id = "macedon", name = "马其顿王国", factionId = "macedon",
            colorHex = FactionPalette.hexOf("macedon"), relation = PowerRelation.OWN,
            pts = listOf(
                41.0 to 22.0, 38.0 to 24.0, 36.0 to 28.0, 33.0 to 30.0,
                30.0 to 35.0, 28.0 to 30.0, 32.0 to 22.0, 38.0 to 20.0,
            ),
        ),
        PowerPolygon(
            id = "arab", name = "阿拉伯帝国", factionId = "arab",
            colorHex = FactionPalette.hexOf("arab"), relation = PowerRelation.NEUTRAL,
            pts = listOf(
                30.0 to 35.0, 28.0 to 45.0, 22.0 to 50.0, 15.0 to 45.0,
                12.0 to 43.0, 18.0 to 38.0, 25.0 to 35.0,
            ),
        ),
        PowerPolygon(
            id = "ottoman", name = "奥斯曼帝国", factionId = "ottoman",
            colorHex = FactionPalette.hexOf("ottoman"), relation = PowerRelation.NEUTRAL,
            pts = listOf(
                42.0 to 26.0, 40.0 to 35.0, 38.0 to 42.0, 36.0 to 38.0,
                36.0 to 28.0, 40.0 to 24.0,
            ),
        ),
        PowerPolygon(
            id = "byzantine", name = "拜占庭帝国", factionId = "byzantine",
            colorHex = FactionPalette.hexOf("byzantine"), relation = PowerRelation.NEUTRAL,
            pts = listOf(
                41.0 to 28.0, 40.0 to 33.0, 38.0 to 29.0, 37.0 to 24.0, 39.0 to 22.0,
            ),
        ),
        PowerPolygon(
            id = "carthage", name = "迦太基", factionId = "carthage",
            colorHex = FactionPalette.hexOf("carthage"), relation = PowerRelation.ENEMY,
            pts = listOf(
                36.0 to 10.0, 34.0 to 8.0, 32.0 to 10.0, 33.0 to 13.0, 35.0 to 13.0,
            ),
        ),
        PowerPolygon(
            id = "viking", name = "维京诸部", factionId = "viking",
            colorHex = FactionPalette.hexOf("viking"), relation = PowerRelation.NEUTRAL,
            pts = listOf(
                60.0 to 5.0, 57.0 to 10.0, 55.0 to 8.0, 58.0 to 2.0, 62.0 to 8.0,
            ),
        ),
        PowerPolygon(
            id = "spain", name = "西班牙王国", factionId = "spain",
            colorHex = FactionPalette.hexOf("spain"), relation = PowerRelation.OWN,
            pts = listOf(
                43.0 to -9.0, 41.0 to -2.0, 37.0 to -5.0, 36.0 to -9.0,
            ),
        ),
        PowerPolygon(
            id = "british", name = "不列颠王国", factionId = "british",
            colorHex = FactionPalette.hexOf("british"), relation = PowerRelation.NEUTRAL,
            pts = listOf(
                58.0 to -5.0, 54.0 to -3.0, 50.0 to -1.0, 52.0 to -7.0, 56.0 to -6.0,
            ),
        ),
        PowerPolygon(
            id = "frankish", name = "法兰克王国", factionId = "frankish",
            colorHex = FactionPalette.hexOf("frankish"), relation = PowerRelation.NEUTRAL,
            pts = listOf(
                50.0 to 2.0, 48.0 to 7.0, 44.0 to 8.0, 43.0 to 3.0,
                46.0 to -1.0, 49.0 to -2.0,
            ),
        ),
    )
}
