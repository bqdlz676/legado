package io.legado.app.ui.main

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.help.ChineseDict
import io.legado.app.help.NameResolver
import io.legado.app.help.PlaceTimeDict
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.relation.PersonDup
import io.legado.app.help.relation.PersonTimeInfo
import io.legado.app.help.relation.RelationExtractor
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 底部半屏详情：原文不离眼。
 *
 * 原来点字、点人、点地名都是 launch 一个全屏 Activity，返回时阅读页重建、位置丢。
 * 这里改成 BottomSheetDialogFragment：原文一直在场，返回不需要重建。
 * 需要看全的再从右上「全屏」跳原页面。
 */
class DetailSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_NAME = "name"
        private const val ARG_DETAIL = "detail"
        private const val ARG_EXTRA = "extra"

        fun char(name: String): DetailSheet = DetailSheet().apply {
            arguments = bundleOf(ARG_TYPE to "char", ARG_NAME to name)
        }

        fun person(name: String, anchorYear: Int?, excerpt: String? = null): DetailSheet =
            DetailSheet().apply {
                arguments = bundleOf(
                    ARG_TYPE to "person",
                    ARG_NAME to name,
                    ARG_EXTRA to (anchorYear ?: Int.MIN_VALUE),
                    ARG_DETAIL to (excerpt?.take(4000) ?: "")
                )
            }

        fun year(year: Int): DetailSheet = DetailSheet().apply {
            arguments = bundleOf(ARG_TYPE to "year", ARG_NAME to "", ARG_EXTRA to year)
        }

        fun place(hit: NameResolver.Hit): DetailSheet = DetailSheet().apply {
            arguments = bundleOf(
                ARG_TYPE to "place",
                ARG_NAME to hit.name,
                ARG_DETAIL to hit.detail,
                ARG_EXTRA to hit.others.joinToString("\u0001")
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        (dialog as? BottomSheetDialog)?.behavior?.peekHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt()

        val type = arguments?.getString(ARG_TYPE) ?: "char"
        val name = arguments?.getString(ARG_NAME).orEmpty()
        val content = view.findViewById<LinearLayout>(R.id.content)
        view.findViewById<TextView>(R.id.tv_title).text = name

        when (type) {
            "char" -> renderChar(view, content, name)
            "person" -> renderPerson(view, content, name, (arguments?.getInt(ARG_EXTRA) ?: Int.MIN_VALUE))
            "year" -> renderYear(view, content, arguments?.getInt(ARG_EXTRA) ?: 0)
            else -> renderPlace(view, content, name)
        }

        view.findViewById<TextView>(R.id.tv_sub).visibility =
            if (view.findViewById<TextView>(R.id.tv_sub).text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        MapWebHolder.detach()
        super.onDestroyView()
    }

    // ---------- 字 ----------
    private fun renderChar(root: View, box: LinearLayout, ch: String) {
        val ctx = requireContext()
        val info = ChineseDict.getChar(ctx, ch)
        val sw = ChineseDict.getShuowen(ctx, ch)
        val usage = ChineseDict.getUsage(ctx, ch)
        val reads = ChineseDict.getPinyin(ctx, ch)

        sub(
            buildString {
                if (reads.isNotEmpty()) append(reads.joinToString("　"))
                else info?.pinyin?.let { append(it) }
                info?.radical?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("　·　")
                    append("部首 ").append(it)
                }
                info?.strokes?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("　·　")
                    append("笔画 ").append(it)
                }
            }
        )

        if (sw?.text?.isNotBlank() == true) {
            label("说文")
            body("《说文解字》：${sw.text}")
        }
        if (info?.meaning?.isNotBlank() == true) {
            label("义项")
            body(info.meaning.replace('\u0001', '\n'))
        }
        if (usage != null && usage.sentence.isNotBlank()) {
            label("书证")
            body("${usage.source}　${usage.sentence}")
        }
        val words = ChineseDict.wordsStartingWith(ctx, ch.firstOrNull() ?: ' ')
        if (words.isNotEmpty()) {
            label("词组")
            body(words.joinToString("　"))
        }
        if (info == null) {
            label("提示")
            body("此字未收，只按字形显示。")
        }

        fullButton {
            startActivity(
                Intent(requireContext(), CharDetailActivity::class.java).putExtra("char", ch)
            )
        }
    }

    // ---------- 人 ----------
    private fun renderPerson(root: View, box: LinearLayout, name: String, anchorYear: Int) {
        val ctx = requireContext()
        val y = if (anchorYear == Int.MIN_VALUE) ContextAnchor.get().year else anchorYear
        val l1 = PersonTimeInfo.of(ctx, name)
        val prof = io.legado.app.help.relation.PersonProfile.profileOf(name)

        val conf = PersonTimeInfo.confidenceOf(ctx, name)
        sub(buildString {
            if (l1 != null && l1.state.isNotBlank()) append(l1.state)
            if (l1 != null && l1.identity.isNotBlank()) {
                if (isNotEmpty()) append("　·　")
                append(l1.identity)
            }
            if (isNotEmpty()) append("　·　")
            append(conf)
        })

        // 基础档案
        label("档案")
        val idLine = buildString {
            if (l1 != null) {
                if (l1.zi.isNotBlank()) append("字 ").append(l1.zi).append("　")
                if (l1.hao.isNotBlank()) append("号 ").append(l1.hao).append("　")
                if (l1.fief.isNotBlank()) append("封 ").append(l1.fief)
            }
            if (isBlank()) {
                prof?.alias?.takeIf { it.isNotEmpty() }?.let { append("又称 ").append(it.take(4).joinToString("、")) }
            }
            if (isBlank()) append("字号无载")
        }
        body(idLine)

        val life = buildString {
            val p = l1
            if (p != null) {
                if (p.birth != null || p.death != null) {
                    append(if (p.birth != null) fmt(p.birth) else "?").append(" — ")
                    append(if (p.death != null) fmt(p.death) else "?")
                }
                if (p.reignStart != null) {
                    if (isNotEmpty()) append("　")
                    append("在位 ").append(fmt(p.reignStart))
                    if (p.reignEnd != null) append("–").append(fmt(p.reignEnd))
                }
            }
            if (isBlank() && prof != null) append(io.legado.app.help.relation.PersonProfile.lifeSpan(prof))
            if (isBlank()) append("生卒无载")
        }
        body(life)

        // 本年状态
        if (y != null) {
            label("此年（${Chronology.yearTitle(y)}）")
            val age = PersonTimeInfo.ageAt(ctx, name, y)
            body(age.label)
        } else {
            label("此年")
            body("尚未锚定到具体年份。读到「隐公元年」这类纪年，或在地图页选一年，这里就能算年龄。")
        }

        // 本段事迹 / 本段同场：阅读优先于查阅，先看「这段里他干了什么」
        val excerpt = arguments?.getString(ARG_DETAIL).orEmpty()
        if (excerpt.isNotBlank()) {
            val evs = RelationExtractor.sentencesAbout(ctx, excerpt, name, y)
            label("本段事迹")
            if (evs.isEmpty()) {
                body("这一段正文里没有他再次出现的句子。")
            } else {
                evs.forEach { body("· ${it}") }
            }
            val co = RelationExtractor.coPresent(ctx, excerpt, name, y)
            if (co.isNotEmpty()) {
                label("本段同场")
                body(co.joinToString("\n") { "· ${it.name}（${it.hits} 句同现）" })
            }
        } else {
            label("本段事迹")
            body("未带入正文片段；从正文里改走人名或地名即可补上这一段的事迹与同场人物。")
        }

        // 最著名一事
        if (l1 != null && l1.deed.isNotBlank()) {
            label("最著名一事")
            body(l1.deed)
        }

        // 同名候选：不是同一个人，别混成一条
        val dups = PersonDup.candidates(ctx, name)
        if (dups.size >= 2) {
            label("同名 ${dups.size} 人（按时代与身份分）")
            body(dups.take(8).joinToString("\n") { "· ${it.tag()}　${it.confidence}" })
        }

        if (prof != null && prof.alias.isNotEmpty()) {
            label("其他称呼")
            body(prof.alias.take(10).joinToString("、"))
        }

        fullButton {
            startActivity(
                Intent(requireContext(), PersonDetailActivity::class.java)
                    .putExtra("name", name)
                    .putExtra("appearances", "[]")
            )
        }
    }

    // ---------- 年 ----------
    private fun renderYear(root: View, box: LinearLayout, year: Int) {
        val ctx = requireContext()
        Chronology.ensureLoaded(ctx)
        root.findViewById<TextView>(R.id.tv_title).text =
            if (year <= 0) "前${1 - year}年" else "${year}年"
        sub(Chronology.yearTitle(year))

        val rows = Chronology.timelineFor(year)
        if (rows.isEmpty()) {
            label("同年对照")
            body("这一年没有收录到在位君主（先秦年表只到战国）。")
        } else {
            label("同年各国（在位第几年）")
            body(rows.joinToString("\n") { (state, nth) ->
                "$state · " + Chronology.chineseNumberText(nth) + "年"
            })
        }

        label("说明")
        body("点「全屏」可在古今地图里按这一年切换朝代图层；读《左传》锚在春秋，读《史记》锚到西汉。")

        fullButton {
            val i = Intent(requireContext(), PlaceMapActivity::class.java)
                .putExtra("year", year)
            startActivity(i)
        }
    }

    // ---------- 地 ----------
    private fun renderPlace(root: View, box: LinearLayout, name: String) {
        val detail = arguments?.getString(ARG_DETAIL).orEmpty()
        val others = arguments?.getString(ARG_EXTRA).orEmpty()
            .split('\u0001').filter { it.isNotBlank() }

        val lines = detail.split('\n')
        sub(lines.firstOrNull().orEmpty())

        label("沿革")
        body(lines.drop(1).joinToString("\n").ifBlank { "暂无可考" })

        if (others.isNotEmpty()) {
            label("还有 ${others.size} 个同名")
            body(others.joinToString("\n"))
        }

        // 时空本体：点画点，面画辖区；锚点为空不猜
        val ctxP = requireContext()
        val yearP = ContextAnchor.get().year
        PlaceTimeDict.ensureLoaded(ctxP)
        val st = PlaceTimeDict.resolve(ctxP, name, yearP)
        val ent = st.hit
        when {
            ent != null && ent.isPoint -> {
                label("位置（${ent.era.takeIf { e -> e.isNotBlank() } ?: "年代未锚定"}·${ent.tierLabel}）")
                val box = FrameLayout(ctxP)
                box.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(200)
                )
                content().addView(box)
                val payload = JSONObject().apply {
                    put("name", ent.name)
                    put("lat", ent.lat)
                    put("lng", ent.lng)
                    put("modern", ent.note)
                    put("zoom", 7)
                    put("old", org.json.JSONArray())
                    put("year", yearP ?: Int.MIN_VALUE)
                    put("era", ContextAnchor.eraOf(yearP).orEmpty())
                    put("still", 1)
                }
                val url = "file:///android_asset/map/index.html?d=" +
                    URLEncoder.encode(payload.toString(), "UTF-8")
                MapWebHolder.attach(box, url)
            }
            ent != null -> {
                label("位置（${ent.era}·辖区）")
                body("这是面（辖区），不是一座城。它的治所落在 ${ent.name}，全屏图上看范围。")
            }
            else -> {
                label("位置")
                body("此地在正文里出现，但坐标未收录到时空本体，按三级推定也不给点。")
            }
        }

        val anchor = ContextAnchor.get()
        if (anchor.year != null) {
            label("当前锚点")
            body(Chronology.yearTitle(anchor.year))
        }

        fullButton {
            startActivity(
                Intent(requireContext(), PlaceMapActivity::class.java)
                    .putExtra("name", name)
            )
        }
    }

    // ---------- 组件 ----------
    private fun sub(text: String) {
        view?.findViewById<TextView>(R.id.tv_sub)?.text = text
    }

    private fun label(text: String) {
        val tv = TextView(requireContext())
        tv.text = text
        tv.textSize = 12f
        tv.setTextColor(0xFFA2967F.toInt())
        tv.setPadding(0, dp(14), 0, dp(3))
        content().addView(tv)
    }

    private fun body(text: String) {
        val tv = TextView(requireContext())
        tv.text = text
        tv.textSize = 15f
        tv.setTextColor(0xFF2B2419.toInt())
        tv.setLineSpacing(dp(4).toFloat(), 1f)
        tv.setTextIsSelectable(true)
        content().addView(tv)
    }

    private fun fullButton(action: () -> Unit) {
        val tv = TextView(requireContext())
        tv.text = "全屏查看 →"
        tv.textSize = 13f
        tv.setTextColor(0xFF8A6A3E.toInt())
        tv.setPadding(0, dp(18), 0, dp(4))
        tv.setOnClickListener { action(); dismiss() }
        content().addView(tv)
    }

    private fun content(): LinearLayout =
        requireView().findViewById(R.id.content)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(y: Int) = if (y <= 0) "前${1 - y}" else "$y"
}
