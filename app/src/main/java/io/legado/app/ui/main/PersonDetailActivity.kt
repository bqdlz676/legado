package io.legado.app.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.ChineseDict
import io.legado.app.help.HanConv
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.person.PersonProfile
import org.json.JSONArray

/**
 * 人物详情：本名 · 生卒 · 身份 · 朝代 · 称谓 · 出场段落
 *
 * 档案数据只走 D2 人物新栈 [PersonProfile]（别名归并 + 同名分组消歧 + 生卒 / 身份 / 出处）；
 * 旧 `help.relation.PersonProfile` 扁平表与 CBDB txt 资产栈不再读取。
 * D2 为纯 JVM 目录，无 Context 预热；查无此人返回 null，页面不凭姓名猜身份。
 */
class PersonDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_detail)

        HanConv.ensureLoaded(this)
        Chronology.ensureLoaded(this)

        val name = intent.getStringExtra("name").orEmpty()
        if (name.isEmpty()) {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // 路由层可透传 personId：同名多人时按主键精确定位，避免默认命中张冠李戴
        val personId = intent.getStringExtra("personId")
        // 文本所处年代：气泡路由透传锚点年代（朝代 · 公元年 · 干支）；
        // 路由未带时回退共享锚点总线；仍无锚点则不占行，不猜年份。
        val anchorLabel = intent.getStringExtra("anchorLabel")?.takeIf { it.isNotBlank() }
            ?: ContextAnchor.eraLabel()
        val prof = PersonProfile.profileOf(name, personId)
        val display = HanConv.toTrad(prof?.name ?: name)

        findViewById<TextView>(R.id.tv_name).text = display

        // 注音属展示辅助：D2 冻结 API 不含拼音，走通用字库 ChineseDict 取词级 / 字级读音；
        // 旧 relation 包的 PersonPinyin 随扁平栈一并断开。
        val py = ChineseDict.pinyinOfWord(this, prof?.name ?: name)

        // 生卒年：优先 D2 的 `lifeSpan(name)`（别名归并后取档案生卒），回退档案字段本身；
        // 任一年份无载对应位置为 null，页面如实标「?」，不补约数。
        val span = PersonProfile.lifeSpan(name) ?: Pair(prof?.birthYear, prof?.deathYear)
        val lifeSpan = formatLifeSpan(span.first, span.second)
        val life = if (py.isNotBlank()) "$py\n$lifeSpan" else lifeSpan
        findViewById<TextView>(R.id.tv_life).apply {
            text = life
            visibility = if (life.isBlank()) View.GONE else View.VISIBLE
        }

        // 朝代 · 国别 · 身份 · 生年干支
        val meta = buildString {
            prof?.dynasty?.takeIf { it.isNotBlank() }?.let { append(it) }
            prof?.nationality?.takeIf { it.isNotBlank() && it != "?" }?.let {
                if (isNotEmpty()) append("　·　")
                append(it)
            }
            prof?.identity?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("　·　")
                append(it)
            }
            prof?.birthYear?.let {
                if (isNotEmpty()) append("　·　")
                append(Chronology.yearLabel(it))
            }
        }
        findViewById<TextView>(R.id.tv_meta).apply {
            text = meta
            visibility = if (meta.isBlank()) View.GONE else View.VISIBLE
        }

        // 称谓：字 / 号 / 封号 / 别称归并一列；繁简不同再补一行简体对照
        val calls = buildList {
            prof?.courtesyName?.let { add("字 $it") }
            prof?.styleName?.let { add("号 $it") }
            prof?.titles?.forEach { add(it) }
            prof?.aliases?.forEach { add(it) }
        }.filter { it.isNotBlank() }.distinct()
        val simp = HanConv.simpLine(name)
        val aliasLine = buildString {
            if (calls.isNotEmpty()) {
                append("称谓　").append(calls.joinToString(" · "))
            }
            if (simp.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(simp)
            }
        }
        findViewById<TextView>(R.id.tv_alias).apply {
            text = aliasLine
            visibility = if (aliasLine.isBlank()) View.GONE else View.VISIBLE
        }

        // 所处年代（文本锚点）排在档案说明最前；与档案自身生卒朝代区分。
        anchorLabel?.let { insertAnchorEra(it) }

        // 事迹 / 小传 / 谱系 / 出处：D2 冻结字段里有、旧页没位置展示，
        // 动态插到「出场段落」之前，不改动布局 XML；字段无载则整行省略。
        prof?.let { insertProfileNotes(it) }

        renderAppearances()
    }

    /** 在出场段落列表之前插入「所处年代」行（当前文本锚定的朝代 / 公元年 / 干支）。 */
    private fun insertAnchorEra(label: String) {
        val appearBox = findViewById<LinearLayout>(R.id.layout_appear)
        val root = appearBox.parent as? LinearLayout ?: return
        val pos = root.indexOfChild(appearBox)
        root.addView(infoLine("所处年代　$label"), pos)
    }

    /** 在出场段落列表之前插入档案说明行；无载字段不占行。 */
    private fun insertProfileNotes(prof: PersonProfile) {
        val appearBox = findViewById<LinearLayout>(R.id.layout_appear)
        val root = appearBox.parent as? LinearLayout ?: return
        var pos = root.indexOfChild(appearBox)

        val notes = listOf(
            "事迹" to prof.notableEvent,
            "小传" to prof.biography,
            "谱系" to prof.lineage.joinToString("、"),
            "出处" to prof.source.label,
        )
        for ((label, value) in notes) {
            if (value.isBlank()) continue
            root.addView(infoLine("$label　$value"), pos)
            pos++
        }
    }

    private fun renderAppearances() {
        val raw = intent.getStringExtra("appearances").orEmpty()
        val box = findViewById<LinearLayout>(R.id.layout_appear)
        box.removeAllViews()

        val list = runCatching { JSONArray(raw) }.getOrNull()
        if (list == null || list.length() == 0) {
            box.addView(itemText("本书未记到可直接定位的段落。"))
            return
        }
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val ch = o.optString("chapter")
            val t = o.optString("text")
            box.addView(itemText(if (ch.isNotBlank()) "〔$ch〕$t" else t))
        }
    }

    private fun infoLine(s: String): TextView = TextView(this).apply {
        text = s
        setTextColor(0xFF473E2E.toInt())
        textSize = 14.5f
        setLineSpacing(6f, 1.1f)
        setPadding(0, 10, 0, 10)
    }

    private fun itemText(s: String): TextView = infoLine(s)

    /** 生卒年格式化：公元前用「前 N」，生卒双全都算岁数；无载标「?」。 */
    private fun formatLifeSpan(birth: Int?, death: Int?): String {
        if (birth == null && death == null) return "生卒无载"
        val b = fmtYear(birth)
        val d = fmtYear(death)
        return if (birth != null && death != null) "$b — $d（${death - birth}岁）" else "$b — $d"
    }

    private fun fmtYear(y: Int?): String =
        y?.let { if (it <= 0) "前${1 - it}" else "$it" } ?: "?"
}
