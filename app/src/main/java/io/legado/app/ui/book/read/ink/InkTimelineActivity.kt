package io.legado.app.ui.book.read.ink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.chrono.ContextAnchor

/**
 * 纪年时间轴页（气泡 [io.legado.app.help.bubble.Action.OpenTimeline] 的消费方）。
 *
 * 规格切片-03「纪年时间轴页」+ 切片-04 §3 Timeline 组件：竖线 + 年份节点 + 政权对照行，
 * 左右箭头切到相邻年份；纪年换算走 C1 [Chronology]，本层只做渲染与切年兜底，不复制纪年逻辑。
 *
 * 数据口径：[Chronology.timelineReport] 出公元年 + 干支 + 距今 + 同年各政权在位行；
 * 某政权该年无在位记录则不出行；全部无载时保留干支 / 距今标题，展示「世系未载」兜底行，
 * 不允许空白态。起始公元年由 Intent extra `year` 传入（负数 = 公元前），缺省取共享锚点年。
 */
class InkTimelineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 本页无气泡 / 半屏 / 抽屉等页内覆盖层，返回即回阅读壳；显式回调与站内同类
        // Compose Activity（AudioPlayActivity / ReadMangaActivity）口径一致，
        // 避免依赖默认返回行为在后续加覆盖层时漏挂裁决。
        onBackPressedDispatcher.addCallback(this) { finish() }
        val year = intent.getIntExtra(EXTRA_YEAR, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        val label = intent.getStringExtra(EXTRA_LABEL)
        setContent {
            InkTimelineScreen(initialYear = year, label = label)
        }
    }

    companion object {
        const val EXTRA_YEAR = "year"
        const val EXTRA_LABEL = "label"
    }
}

/** 时间轴一行：政权 + 称号 + 在位第几年（元年 = 1）。 */
internal data class InkTimelineRow(
    val state: String,
    val title: String,
    val nth: Int,
)

/** 时间轴页纯展示状态；由 [InkTimelinePresenter] 从 C1 报告装配，Compose 层不直接算纪年。 */
internal data class InkTimelineState(
    val year: Int,
    val ganzhi: String,
    val yearsAgo: Int,
    val title: String,
    val label: String?,
    val rows: List<InkTimelineRow>,
)

/**
 * 时间轴装配纯逻辑（离线 JVM 可单测）：把 [Chronology.timelineReport] 富结构拍平成展示模型；
 * 切年边界兜底也在此收口，Compose 层只消费状态。
 */
internal object InkTimelinePresenter {

    /** 由公元年装配整页状态；[label] 为气泡带来的原文纪年串（如「鲁隐公元年」），可空。 */
    fun build(year: Int, label: String?): InkTimelineState {
        val report = Chronology.timelineReport(year)
        return InkTimelineState(
            year = report.year,
            ganzhi = report.ganzhi,
            yearsAgo = report.yearsAgo,
            title = report.title,
            label = label,
            rows = report.rows.map { InkTimelineRow(it.state, it.title, it.nth) },
        )
    }

    /**
     * 左右切年：[current] 为空（锚点未定年）时先回退锚点年，再回退纪年基准年 1950，
     * 保证箭头永远有可切的年份，不出空状态。
     */
    fun shiftYear(current: Int?, delta: Int): Int {
        val base = current ?: ContextAnchor.getYear() ?: Chronology.PRESENT_BASE
        return base + delta
    }
}

/** 年份 / 距今数字段走等宽数字角色；其余文字保留基础族。 */
private fun numberAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text[i].isDigit()) {
            val start = i
            while (i < text.length && text[i].isDigit()) i++
            withStyle(SpanStyle(fontFamily = InkFonts.composeNumberFamily)) {
                append(text.substring(start, i))
            }
        } else {
            val start = i
            while (i < text.length && !text[i].isDigit()) i++
            append(text.substring(start, i))
        }
    }
}

@Composable
private fun InkTimelineScreen(initialYear: Int?, label: String?) {
    var year by remember { mutableStateOf(initialYear ?: ContextAnchor.getYear()) }
    val state = remember(year, label) {
        InkTimelinePresenter.build(year ?: Chronology.PRESENT_BASE, label)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkTokens.BG_PAGE)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        // 年份节点头：左箭头 · 公元年 / 干支 / 距今 · 右箭头
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            InkIconButton(onClick = { year = InkTimelinePresenter.shiftYear(year, -1) }) {
                BasicText(
                    text = "‹",
                    style = TextStyle(
                        fontFamily = InkFonts.composeFamily,
                        fontSize = 22.sp,
                        color = InkTokens.BRAND_COPPER,
                    ),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!state.label.isNullOrBlank()) {
                    BasicText(
                        text = state.label,
                        style = TextStyle(
                            fontFamily = InkFonts.composeFamily,
                            fontSize = 13.sp,
                            color = InkTokens.TEXT_SECONDARY,
                        ),
                    )
                }
                BasicText(
                    text = numberAnnotated(state.title),
                    style = TextStyle(
                        fontFamily = InkFonts.composeTitleFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = InkTokens.TEXT_PRIMARY,
                    ),
                )
            }

            InkIconButton(onClick = { year = InkTimelinePresenter.shiftYear(year, 1) }) {
                BasicText(
                    text = "›",
                    style = TextStyle(
                        fontFamily = InkFonts.composeFamily,
                        fontSize = 22.sp,
                        color = InkTokens.BRAND_COPPER,
                    ),
                )
            }
        }

        // 金线分隔
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            drawLine(
                color = InkTokens.BRAND_GOLD.copy(alpha = 0.5f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 政权对照行：竖线 + 金节点 + 国号称号 + 在位第几年
        if (state.rows.isEmpty()) {
            TimelineFallbackRow()
        } else {
            state.rows.forEach { row -> TimelineRowItem(row) }
        }
    }
}

@Composable
private fun TimelineRowItem(row: InkTimelineRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 竖线 + 金节点：节点压在竖线上，行与行之间由竖线串联成时间轴。
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = InkTokens.COLOR_DIVIDER,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(InkTokens.BRAND_GOLD),
            )
        }

        BasicText(
            text = "${row.state} · ${row.title}",
            style = TextStyle(
                fontFamily = InkFonts.composeFamily,
                fontSize = 15.sp,
                color = InkTokens.TEXT_PRIMARY,
            ),
            modifier = Modifier.padding(start = 10.dp),
        )

        BasicText(
            text = "在位第 ${Chronology.chineseNumberText(row.nth)} 年",
            style = TextStyle(
                fontFamily = InkFonts.composeFamily,
                fontSize = 12.sp,
                color = InkTokens.TEXT_SECONDARY,
            ),
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
    }
}

/** 全部政权无在位记录的兜底行：不空白，标明「世系未载」，干支 / 距今仍在头部展示。 */
@Composable
private fun TimelineFallbackRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(InkTokens.TEXT_TERTIARY),
            )
        }
        BasicText(
            text = "世系未载",
            style = TextStyle(
                fontFamily = InkFonts.composeFamily,
                fontSize = 14.sp,
                color = InkTokens.TEXT_TERTIARY,
            ),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
