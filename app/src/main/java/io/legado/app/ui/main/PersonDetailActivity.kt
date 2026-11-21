package io.legado.app.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.HanConv
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.relation.PersonPinyin
import io.legado.app.help.relation.PersonProfile
import org.json.JSONArray

/**
 * 人物详情：本名 · 生卒 · 身份 · 朝代 · 称谓 · 出场段落
 * 由关系图卡片上的「人物详情」进入，数据来自人物档案与本次抽取结果
 */
class PersonDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_detail)

        HanConv.ensureLoaded(this)
        Chronology.ensureLoaded(this)
        PersonProfile.ensureLoaded(this)

        val name = intent.getStringExtra("name").orEmpty()
        if (name.isEmpty()) {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val prof = PersonProfile.profileOf(name)
        val display = HanConv.toTrad(name)

        findViewById<TextView>(R.id.tv_name).text = display

        val py = PersonPinyin.of(this, name)
        findViewById<TextView>(R.id.tv_name).text = display
        val lifeTxt = if (py.isNotBlank()) "$py\n" + (prof?.let { PersonProfile.lifeSpan(it) }.orEmpty()) else
            (prof?.let { PersonProfile.lifeSpan(it) }.orEmpty())
        val life = lifeTxt
        findViewById<TextView>(R.id.tv_life).apply {
            text = life
            visibility = if (life.isBlank()) View.GONE else View.VISIBLE
        }

        // 朝代 · 身份 · 干支年
        val meta = buildString {
            val dyn = prof?.state.orEmpty()
            if (dyn.isNotBlank() && dyn != "?") append(dyn)
            val iden = prof?.identity.orEmpty()
            if (iden.isNotBlank()) {
                if (isNotEmpty()) append("　·　")
                append(iden)
            }
            prof?.birth?.let {
                if (isNotEmpty()) append("　·　")
                append(Chronology.yearLabel(it))
            }
        }
        findViewById<TextView>(R.id.tv_meta).apply {
            text = meta
            visibility = if (meta.isBlank()) View.GONE else View.VISIBLE
        }

        val aliases = prof?.alias.orEmpty()
        val simp = HanConv.simpLine(name)
        val aliasLine = buildString {
            if (aliases.isNotEmpty()) {
                append("称谓　").append(aliases.joinToString(" · "))
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

        renderAppearances()
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

    private fun itemText(s: String): TextView {
        return TextView(this).apply {
            text = s
            setTextColor(0xFF473E2E.toInt())
            textSize = 14.5f
            setLineSpacing(6f, 1.1f)
            setPadding(0, 10, 0, 10)
        }
    }
}
