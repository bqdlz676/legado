package io.legado.app.ui.main.chrono

import io.legado.app.help.chrono.Chronology
import java.time.LocalDate

/**
 * 落文 · 纪年页展示模型（纯 JVM，无 Compose / Android 依赖，可直接跑单测）。
 *
 * 对接 core [Chronology]：本层只做结构换算与边界兜底，不重复实现纪年算法。
 * 边界口径（项目规则：空数据 / 年份缺失不空白不崩溃）：
 *  - year 为 null：回退到当日公历，展示当日干支，不抛异常；
 *  - 诸侯对照行为空：保留干支与当日时辰，给出「世系未载」兜底行，不渲染空白列表。
 */

/** 一条同年政权对照行 */
data class ChronoRow(
    val state: String,
    val title: String,
    val nth: Int,
    /** 在位第几年的汉字写法，如「二十二」 */
    val nthText: String,
)

/** 一页纪年状态 */
data class ChronoViewState(
    /** 公元年；null 表示年代未知，已回退当日 */
    val year: Int?,
    val yearLabel: String,
    /** 年干支（年代未知时取当日干支） */
    val ganzhi: String,
    val agoText: String,
    /** 当日日干支 */
    val dayGanzhi: String,
    val rows: List<ChronoRow>,
    /** 诸侯世系是否缺失（缺失时展示兜底行，不空白） */
    val rowsMissing: Boolean,
)

/** 时间轴一个节点：年份 + 干支 + 该年政权对照行（Timeline 组件与人物 Sheet 共用，只换数据） */
data class ChronoNode(
    val state: ChronoViewState,
    val selected: Boolean,
)

/** 十二时辰一行（含时干支，五鼠遁起时） */
data class Shichen(
    val zhi: String,
    val range: String,
    val hourGanzhi: String,
)

object ChronoPresenter {

    private val ZHI = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val RANGE = listOf(
        "23:00–01:00", "01:00–03:00", "03:00–05:00", "05:00–07:00",
        "07:00–09:00", "09:00–11:00", "11:00–13:00", "13:00–15:00",
        "15:00–17:00", "17:00–19:00", "19:00–21:00", "21:00–23:00",
    )

    /**
     * 换算一页状态。
     * @param year 公元年；null = 年代未知
     * @param rows core 给出的同年政权对照行
     * @param today 当日日期（注入便于单测）
     */
    fun build(year: Int?, rows: List<Chronology.TimelineRow>, today: LocalDate): ChronoViewState {
        val dayGanzhi = Chronology.dayGanzhi(today)
        if (year == null) {
            return ChronoViewState(
                year = null,
                yearLabel = "年代未载",
                ganzhi = dayGanzhi,
                agoText = "当日",
                dayGanzhi = dayGanzhi,
                rows = emptyList(),
                rowsMissing = true,
            )
        }
        val mapped = rows.map { row ->
            ChronoRow(
                state = row.state,
                title = row.title,
                nth = row.nth,
                nthText = Chronology.chineseNumberText(row.nth),
            )
        }
        return ChronoViewState(
            year = year,
            yearLabel = Chronology.yearLabel(year),
            ganzhi = Chronology.yearGanzhi(year),
            agoText = "距今约 ${Chronology.yearsAgo(year)} 年",
            dayGanzhi = dayGanzhi,
            rows = mapped,
            rowsMissing = mapped.isEmpty(),
        )
    }

    /** 左右切年；年代未知时从当日公历起算 */
    fun shiftYear(year: Int?, delta: Int, today: LocalDate): Int =
        (year ?: today.year) + delta

    /**
     * 十二时辰对照，时干支按五鼠遁由日干支推得：
     * 甲己还加甲 / 乙庚丙作初 / 丙辛从戊起 / 丁壬庚子居 / 戊癸壬子居。
     * 日干支非法时回退子时起甲，保证表不空白。
     */
    fun shichenTable(dayGanzhi: String): List<Shichen> {
        val startGan = startHourGan(dayGanzhi)
        val gan = "甲乙丙丁戊己庚辛壬癸"
        return ZHI.mapIndexed { i, zhi ->
            val hourGan = gan[(startGan + i) % 10]
            Shichen(
                zhi = zhi,
                range = RANGE[i],
                hourGanzhi = "$hourGan$zhi",
            )
        }
    }

    private fun startHourGan(dayGanzhi: String): Int {
        if (dayGanzhi.length != 2) return 0
        return when (dayGanzhi[0]) {
            '甲', '己' -> 0
            '乙', '庚' -> 2
            '丙', '辛' -> 4
            '丁', '壬' -> 6
            '戊', '癸' -> 8
            else -> 0
        }
    }
}
