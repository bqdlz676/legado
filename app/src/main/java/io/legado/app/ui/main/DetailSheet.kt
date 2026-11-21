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
            // 用户主动点年号 = 最高优先级锚定；纪年 / 地名 / 人物 / 地图从此读同一个年份
            io.legado.app.help.chrono.ContextAnchor.setYear(year)
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
        dialog?.window?.setDimAmount(0.4f)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            peekHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
            isDraggable = true
        }

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
        // 单一总线：只读 ContextAnchor，不再各传一份年份
        val y = if (anchorYear == Int.MIN_VALUE) ContextAnchor.getYear() else anchorYear
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

        // 注音：古名多生僻字与多音字，先给读法
        val py = runCatching {
            io.legado.app.help.relation.PersonPinyin.of(ctx, name)
        }.getOrNull().orEmpty()
        if (py.isNotBlank()) body("读音　$py")

        val excerpt = arguments?.getString(ARG_DETAIL).orEmpty()

        // ① 本段即时信息：这段里他干了什么、和谁同场、当年几岁 —— 阅读优先
        label("本段")
        if (excerpt.isNotBlank()) {
            val evs = RelationExtractor.sentencesAbout(ctx, excerpt, name, y)
            if (evs.isEmpty()) {
                body("这一段正文里没有他再次出现的句子。")
            } else {
                evs.take(3).forEach { body("· $it") }
            }
            val co = RelationExtractor.coPresent(ctx, excerpt, name, y)
            if (co.isNotEmpty()) {
                body("同场：" + co.take(6).joinToString("、") {
                    it.name + (if (it.label.isNotBlank()) "〔${it.label}〕" else "")
                })
            }
        } else {
            body("未带入正文片段；从正文里点人名即可补上这一段的事迹与同场人物。")
        }
        if (y != null) {
            val age = PersonTimeInfo.ageAt(ctx, name, y)
            body("${Chronology.yearTitle(y)} · ${age.label}")
        } else {
            body("尚未锚定到具体年份，年龄不予推算。读到「隐公元年」这类纪年即可锚定。")
        }

        // ② 本书迷你时间轴（首次 → 关键事迹 → 末次）
        label("本书")
        body(buildString {
            append("当前章　").append(ContextAnchor.title.ifBlank { "未定位" })
            if (l1 != null && l1.deed.isNotBlank()) {
                append("\n关键事迹　").append(l1.deed)
            }
            append("\n出场索引　")
            append(
                if (io.legado.app.help.relation.PersonChapterEvents.has(ctx, name))
                    io.legado.app.help.relation.PersonChapterEvents.summary(ctx, name)
                else "尚未整本扫描；扫一遍即可给首次/末次出场。"
            )
        })

        // ③ 基础档案（查阅读到这里才开始）
        label("档案")
        body(buildString {
            if (l1 != null) {
                if (l1.zi.isNotBlank()) append("字 ").append(l1.zi).append("　")
                if (l1.hao.isNotBlank()) append("号 ").append(l1.hao).append("　")
                if (l1.fief.isNotBlank()) append("封 ").append(l1.fief)
            }
            if (isBlank()) {
                prof?.alias?.takeIf { it.isNotEmpty() }?.let { append("又称 ").append(it.take(4).joinToString("、")) }
            }
            if (isBlank()) append("字号无载")
        })
        body(buildString {
            if (l1 != null) {
                if (l1.birth != null || l1.death != null) {
                    append(if (l1.birth != null) fmt(l1.birth) else "?").append(" — ")
                    append(if (l1.death != null) fmt(l1.death) else "?")
                }
                if (l1.reignStart != null) {
                    if (isNotEmpty()) append("　")
                    append("在位 ").append(fmt(l1.reignStart))
                    if (l1.reignEnd != null) append("–").append(fmt(l1.reignEnd))
                }
            }
            if (isBlank() && prof != null) append(io.legado.app.help.relation.PersonProfile.lifeSpan(prof))
            if (isBlank()) append("生卒无载")
        })
        body(buildString {
            append(if (l1?.state?.isNotBlank() == true) l1!!.state else "国别无载")
            append("　·　").append(if (l1?.identity?.isNotBlank() == true) l1!!.identity else "身份无载")
            if (prof != null && prof.alias.isNotEmpty()) {
                append("\n其他称呼　").append(prof.alias.take(10).joinToString("、"))
            }
        })

        // ④ 同名候选：不是同一个人，别混成一条
        val dups = PersonDup.candidates(ctx, name)
        if (dups.size >= 2) {
            label("还有 ${dups.size} 个同名")
            body(dups.take(8).joinToString("\n") { "· ${it.tag()}　${it.confidence}" })
        }

        // 来源可追溯：L1 人工 / CBDB / CHGIS
        body(
            if (l1 != null) "来源　L1 人工档案（生卒、身份、事迹按史书核对）"
            else if (prof != null) "来源　CBDB（生卒不清标「约」）"
            else "来源　未收录：仅按正文出现统计，不作生平结论"
        )

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

        // 纪年时间轴：拖动吸附整十年
        label("纪年")
        val y0 = year
        val timeline = io.legado.app.ui.widget.InkTimelineView(requireContext()).apply {
            this.year = y0
            onYearChanged = { switchYear(it) }
        }
        content().addView(timeline)

        label("说明")
        body("点「全屏」可在古今地图里按这一年切换朝代图层；读《左传》锚在春秋，读《史记》锚到西汉。")

        fullButton {
            val i = Intent(requireContext(), PlaceMapActivity::class.java)
                .putExtra("year", year)
            startActivity(i)
        }
    }

    private fun switchYear(y: Int) {
        val fm = (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
        dismiss()
        if (fm != null) DetailSheet.year(y).show(fm, "detail_year")
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
                body(
                    "${ent.name} · ${ent.note.ifBlank { "坐标已收录" }}。\n" +
                        "地图不在这张纸片里挤着——点下面按钮，另开一个窗口。"
                )
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

        fullButton("在地图窗口中打开 →") {
            val i = Intent(requireContext(), PlaceMapActivity::class.java)
                .putExtra("name", name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
    }

    // ---------- 组件 ----------
    private fun sub(text: String) {
        view?.findViewById<TextView>(R.id.tv_sub)?.text = text
    }

    private fun label(text: String) {
        val tv = TextView(requireContext())
        tv.setTextAppearance(R.style.InkTextLabel)
        tv.text = text
        tv.setPadding(0, dp(14), 0, dp(3))
        content().addView(tv)
    }

    private fun body(text: String) {
        val tv = TextView(requireContext())
        tv.setTextAppearance(R.style.InkTextBody)
        tv.text = text
        tv.setTextIsSelectable(true)
        content().addView(tv)
    }

    private fun fullButton(text: String = "全屏查看 →", action: () -> Unit) {
        val tv = TextView(requireContext())
        tv.text = text
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
