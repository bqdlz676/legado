package io.legado.app.help.bubble

import android.content.Context
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.chrono.ContextAnchor

/**
 * 纪年 / 干支 / 朔望 Provider（优先级链第 1 层，规格 §2：正则先扫，最快）。
 *
 * 负责三类命中：
 * - 年号 / 王公纪年：「贞观三年」「鲁隐公元年」→ 公元年 + 同期多政权对照 + 干支；
 * - 干支：「甲子」，后接「年」判纪年，否则按纪日；六十年一循环，单靠干支不定年；
 * - 朔望晦：朔 = 初一，望 = 十五，晦 = 月末。
 *
 * 换算出口对齐 Chronology：eraCandidates / princeYear / dayGanzhi / timelineFor。
 * 重名年号把全部候选列出并标明朝代；只有一个候选才直接给结果。强锚定命中后把公元年
 * 回写 [ContextAnchor]，供地名选层与人物同名消歧共享。
 */
object DateProvider : BubbleProvider {

    /** 预热纪年表与先秦王公世系（App 启动协程调用，失败不阻启动）。 */
    fun warm(context: Context) {
        runCatching { Chronology.ensureLoaded(context) }
    }

    private val NTH = Regex("(元|[一二三四五六七八九十]{1,3}|[0-9]{1,3})$")

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        val era = BubbleSupport.eraMentionAt(paragraph, pos)
        if (era != null) return eraContent(era, anchor)

        val ganzhi = BubbleSupport.ganzhiAt(paragraph, pos)
        if (ganzhi != null) {
            val isYear = pos + 1 < paragraph.length && paragraph[pos + 1] == '年'
            return ganzhiContent(ganzhi, isYear, anchor)
        }

        val phase = BubbleSupport.phaseAt(paragraph, pos)
        if (phase != null) return phaseContent(phase)

        return null
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val clean = BubbleSupport.cleanToken(token)
        if (clean.isEmpty()) return null
        if (clean.endsWith("年")) return eraContent(clean, anchor)
        if (clean.length == 2 && Chronology.ganzhiIndex(clean) != null) {
            return ganzhiContent(clean, false, anchor)
        }
        if (clean.length == 1 && clean[0] in "朔望晦") return phaseContent(clean)
        return null
    }

    // ---------- 年号 / 王公纪年 ----------

    private fun eraContent(mention: String, anchor: ContextAnchor): BubbleContent? {
        // 无 Context（纯 JVM 单测）时不读 assets，Chronology 内置种子世系仍可换算先秦王公纪年。
        BubbleEnv.ctx()?.let { runCatching { Chronology.ensureLoaded(it) } }

        val body = mention.removeSuffix("年")
        val nthMatch = NTH.find(body) ?: return null
        val nthText = nthMatch.groupValues[1]
        val nth = Chronology.chineseNumber(nthText) ?: nthText.toIntOrNull() ?: return null
        val prefix = body.substring(0, nthMatch.range.first)
        if (prefix.isEmpty()) return null

        // 1) 年号候选（重名年号并列标朝代）；按各政权自身在位区间算公元年，越界剔除。
        val eraHits = runCatching { Chronology.eraCandidates(prefix) }
            .getOrNull()
            .orEmpty()
            .mapNotNull { e ->
                if (nth <= 0 || nth > e.endYear - e.startYear + 1) return@mapNotNull null
                val y = e.startYear + nth - 1
                if (y > e.endYear) return@mapNotNull null
                e to y
            }

        // 2) 先秦王公纪年（国名省略时 Chronology 内部按鲁兜底）。
        val princeYear = runCatching { Chronology.princeYear(prefix, nth) }.getOrNull()

        val year: Int
        val candidates: List<String>
        val source: String
        when {
            eraHits.isNotEmpty() -> {
                val anchored = anchor.getYear()
                val default = eraHits.firstOrNull { anchored != null && anchored in it.first.startYear..it.first.endYear }
                    ?: eraHits.first()
                year = default.second
                candidates = eraHits.map { "${it.first.name}（${it.first.dynasty}）" }
                source = "纪年表 era.json（公版年号世系）"
            }
            princeYear != null -> {
                year = princeYear
                candidates = emptyList()
                source = "先秦王公世系 princes.txt（公版史料）"
            }
            else -> return null
        }

        // 强锚定回写共享总线：地名选层 / 人物消歧都读这一个锚点。
        runCatching { anchor.setYear(year, prefix.firstOrNull()?.toString()) }

        val report = runCatching { Chronology.timelineReport(year) }.getOrNull()
        val title = report?.title ?: Chronology.yearTitle(year)

        val bodyText = buildString {
            append("距今约").append(Chronology.yearsAgo(year)).append("年")
            report?.rows?.take(3)?.let { rows ->
                if (rows.isNotEmpty()) {
                    append("　对照：")
                    append(rows.joinToString("、") { "${it.state}·${it.title}${Chronology.chineseNumberText(it.nth)}年" })
                }
            }
        }

        val detailText = buildString {
            append(title)
            report?.rows?.let { rows ->
                if (rows.isNotEmpty()) {
                    append("\n\n同年各政权对照：")
                    rows.forEach { r ->
                        append("\n").append(r.state).append("·").append(r.title)
                            .append("　第").append(Chronology.chineseNumberText(r.nth)).append("年")
                    }
                }
            }
        }

        return BubbleContent(
            kind = BubbleKind.ERA,
            token = mention,
            phonetic = null,
            headline = title,
            body = bodyText,
            detail = detailText,
            actions = listOf(Action.OpenTimeline(year, title)),
            candidates = candidates,
            source = source,
        )
    }

    // ---------- 干支 ----------

    private fun ganzhiContent(gz: String, isYear: Boolean, anchor: ContextAnchor): BubbleContent {
        val index = Chronology.ganzhiIndex(gz)
        val anchorYear = anchor.getYear()
        val anchorGanzhi = anchorYear?.let { runCatching { Chronology.yearGanzhi(it) }.getOrNull() }

        val body = buildString {
            append(if (isYear) "干支纪年" else "干支纪日")
            append("，六十甲子循环")
            if (anchorGanzhi != null) {
                append("；本章锚定年份干支为 ").append(anchorGanzhi)
                if (anchorGanzhi == gz) append("，与所点干支相合")
            }
        }

        val detail = buildString {
            append(gz)
            if (index != null) append("（六十甲子第 ").append(index + 1).append(" 位）")
            append("\n干支六十年一循环，单靠干支无法定到具体年份；需结合年号 / 王公纪年锚定。")
            if (anchorYear != null) {
                append("\n锚定公元年：").append(Chronology.yearTitle(anchorYear))
            }
        }

        val actions = if (anchorYear != null) {
            listOf(Action.OpenTimeline(anchorYear, Chronology.yearTitle(anchorYear)))
        } else {
            emptyList()
        }

        return BubbleContent(
            kind = BubbleKind.GANZHI,
            token = gz,
            phonetic = null,
            headline = "$gz · ${if (isYear) "纪年" else "纪日"}",
            body = body,
            detail = detail,
            actions = actions,
            candidates = emptyList(),
            source = "干支纪法（天文纪年）",
        )
    }

    // ---------- 朔望晦 ----------

    private fun phaseContent(phase: String): BubbleContent {
        val (meaning, detail) = when (phase) {
            "朔" -> "农历每月初一" to "朔为月始，月球位于日地之间，肉眼不可见；对应当月初一。"
            "望" -> "农历每月十五（或十六）" to "望为月满，地球位于日月之间，圆月可见；通常落在十五，个别月份十六。"
            "晦" -> "农历每月月末" to "晦为月尽，月末之日；次月初一即为朔。"
            else -> phase to "纪日用字。"
        }
        return BubbleContent(
            kind = BubbleKind.PHASE,
            token = phase,
            phonetic = null,
            headline = phase,
            body = meaning,
            detail = detail + "\n无年月锚点时不推具体公历日期。",
            actions = emptyList(),
            candidates = emptyList(),
            source = "历法常识（朔望晦）",
        )
    }
}
