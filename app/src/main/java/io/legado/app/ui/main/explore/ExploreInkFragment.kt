package io.legado.app.ui.main.explore

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentExploreInkBinding
import io.legado.app.ui.main.search.InkSearchActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.PlaceMapActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 落文 · 发现
 *
 * 不是推荐流，是策展：战役横卡、典籍竖卡、典故通栏卡，三套卡片长度不同、节奏不同。
 */
class ExploreInkFragment() : BaseFragment(R.layout.fragment_explore_ink), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentExploreInkBinding::bind)
    private val ctx get() = requireContext()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvSearch.setOnClickListener {
            InkSearchActivity.start(ctx, "")
        }

        val campaigns = listOf(
            Campaign("赤壁之战", "三国 · 孙刘抗曹", "火光照亮赤壁", "赤壁", 208, 29.72, 113.30, "chibi"),
            Campaign("长平之战", "战国 · 秦赵", "四十万降卒", "长平", -260, 35.82, 112.90, "changping"),
            Campaign("淝水之战", "东晋 · 前秦", "风声鹤唳", "淝水", 383, 32.60, 116.78, "feishui"),
            Campaign("垓下之围", "楚汉", "四面楚歌", "垓下", -202, 33.62, 117.52, "gaixia")
        )
        binding.rvCampaign.layoutManager =
            LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCampaign.adapter = CampaignAdapter(campaigns)
        LinearSnapHelper().attachToRecyclerView(binding.rvCampaign)

        buildClassics()
        buildTopics()
        buildUpdates()
    }

    private fun openCampaign(c: Campaign) {
        val i = android.content.Intent(ctx, PlaceMapActivity::class.java)
            .putExtra("name", c.name)
            .putExtra("year", c.year)
            .putExtra("campaign", c.campaign)
        startActivity(i)
    }

    private fun buildClassics() {
        val list = listOf(
            Triple("左传", "春秋", "编年"),
            Triple("史记", "纪传", "通史"),
            Triple("诗经", "风雅", "诗歌"),
            Triple("三国志", "魏蜀吴", "断代"),
            Triple("论语", "语录", "儒家"),
            Triple("庄子", "逍遥", "道家")
        )
        val grid = binding.gridClassic
        grid.removeAllViews()
        val size = (resources.displayMetrics.widthPixels - dp(16 * 2) - dp(8 * 2)) / 3
        list.forEach { (name, tag, kind) ->
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = resources.getDrawable(R.drawable.bg_ink_classic_card)
                setPadding(dp(8), dp(16), dp(8), dp(12))
                setOnClickListener { InkSearchActivity.start(ctx, name) }
                cameraDistance = 9600f * resources.displayMetrics.density
                rotationY = 3f
            }
            card.addView(TextView(ctx).apply {
                text = name.toCharArray().joinToString("\n")
                typeface = Typeface.SERIF
                textSize = 16f
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                setTextColor(0xFFE8DCC0.toInt())
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            card.addView(TextView(ctx).apply {
                text = "$tag · $kind"
                typeface = Typeface.SANS_SERIF
                textSize = 9f
                letterSpacing = 0.1f
                gravity = Gravity.CENTER
                setTextColor(0x99D4A54A.toInt())
                setPadding(0, dp(8), 0, 0)
            })
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = size
            lp.height = dp(96)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            card.layoutParams = lp
            grid.addView(card)
        }
    }

    private fun buildTopics() {
        val topics = listOf(
            "春秋笔法 · 28 则典故" to "郑伯克段 / 退避三舍 / 一鸣惊人 / 问鼎中原",
            "史记名场面 · 36 则" to "鸿门宴 / 完璧归赵 / 负荆请罪 / 荆轲刺秦"
        )
        val box = binding.llTopics
        box.removeAllViews()
        topics.forEach { (title, sub) ->
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = resources.getDrawable(R.drawable.bg_ink_topic_card)
                setPadding(dp(14), dp(13), dp(14), dp(13))
                setOnClickListener { openTopic(title) }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(8)
                layoutParams = lp
            }
            card.addView(TextView(ctx).apply {
                text = title
                typeface = Typeface.SERIF
                textSize = 14f
                setTextColor(0xFF1A1410.toInt())
            })
            card.addView(TextView(ctx).apply {
                text = sub
                typeface = Typeface.SANS_SERIF
                textSize = 10.5f
                setTextColor(0xFF9B9687.toInt())
                setPadding(0, dp(5), 0, 0)
                letterSpacing = 0.04f
            })
            box.addView(card)
        }
    }

    private fun openTopic(title: String) {
        val items = if (title.startsWith("春秋")) listOf(
            "郑伯克段于鄢" to "《左传 · 隐公元年》",
            "退避三舍" to "《左传 · 僖公二十三年》",
            "一鸣惊人" to "《史记 · 滑稽列传》",
            "问鼎中原" to "《左传 · 宣公三年》",
            "唇亡齿寒" to "《左传 · 僖公五年》"
        ) else listOf(
            "鸿门宴" to "《史记 · 项羽本纪》",
            "完璧归赵" to "《史记 · 廉颇蔺相如列传》",
            "负荆请罪" to "《史记 · 廉颇蔺相如列传》",
            "荆轲刺秦" to "《史记 · 刺客列传》",
            "破釜沉舟" to "《史记 · 项羽本纪》"
        )
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.bg_ink_sheet)
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        root.addView(TextView(ctx).apply {
            text = title
            typeface = Typeface.SERIF
            textSize = 17f
            setTextColor(0xFF1A1410.toInt())
        })
        root.addView(View(ctx).apply {
            setBackgroundColor(0x1A8B6914)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(10); bottomMargin = dp(4) }
        })
        items.forEach { (name, from) ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(11), 0, dp(11))
                setOnClickListener {
                    sheet.dismiss()
                    InkSearchActivity.start(ctx, name)
                }
            }
            row.addView(TextView(ctx).apply {
                text = name
                typeface = Typeface.SERIF
                textSize = 14f
                setTextColor(0xFF2B2419.toInt())
            })
            row.addView(TextView(ctx).apply {
                text = from
                typeface = Typeface.SANS_SERIF
                textSize = 10f
                setTextColor(0xFF9B9687.toInt())
                setPadding(0, dp(3), 0, 0)
            })
            root.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        sheet.setContentView(root)
        sheet.show()
    }

    private fun buildUpdates() {
        val lines = listOf(
            "新增战役：长平之战 · 3D 沙盘",
            "补全人物：200 人 L1 核心库",
            "典藏：毛诗正义全文上线"
        )
        val box = binding.llUpdates
        box.removeAllViews()
        lines.forEach { s ->
            box.addView(TextView(ctx).apply {
                text = s
                typeface = Typeface.SANS_SERIF
                textSize = 12f
                setTextColor(0xFF5C4F3A.toInt())
                letterSpacing = 0.03f
                setPadding(dp(12), dp(9), 0, dp(9))
                setBackgroundColor(0x00000000)
            })
        }
    }

    private data class Campaign(
        val name: String,
        val side: String,
        val note: String,
        val place: String,
        val year: Int,
        val lat: Double,
        val lng: Double,
        val campaign: String
    )

    private inner class CampaignAdapter(private val list: List<Campaign>) :
        RecyclerView.Adapter<CampaignAdapter.H>() {

        inner class H(val v: View) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = resources.getDrawable(R.drawable.bg_ink_campaign_card)
                setPadding(dp(16), dp(14), dp(14), dp(12))
                layoutParams = RecyclerView.LayoutParams(
                    dp(262), RecyclerView.LayoutParams.MATCH_PARENT
                ).apply { rightMargin = dp(11) }
            }
            val title = TextView(ctx).apply {
                textSize = 16f
                typeface = Typeface.SERIF
                setTextColor(0xFF1A1410.toInt())
            }
            val side = TextView(ctx).apply {
                textSize = 10f
                typeface = Typeface.SANS_SERIF
                setTextColor(0xFF8B6914.toInt())
                letterSpacing = 0.08f
                setPadding(0, dp(7), 0, 0)
            }
            val note = TextView(ctx).apply {
                textSize = 11f
                typeface = Typeface.SANS_SERIF
                setTextColor(0xFF5C4F3A.toInt())
                setPadding(0, dp(4), 0, 0)
            }
            val arrow = TextView(ctx).apply {
                text = "看沙盘 →"
                textSize = 10.5f
                typeface = Typeface.SANS_SERIF
                setTextColor(0xFFC23B2E.toInt())
                setPadding(0, dp(8), 0, 0)
            }
            card.addView(title)
            card.addView(side)
            card.addView(note)
            card.addView(arrow)
            return H(card)
        }

        override fun onBindViewHolder(holder: H, position: Int) {
            val c = list[position]
            val box = holder.v as LinearLayout
            (box.getChildAt(0) as TextView).text = "${c.name} · ${c.side}"
            (box.getChildAt(1) as TextView).text = c.place
            (box.getChildAt(2) as TextView).text = c.note
            holder.v.setOnClickListener { openCampaign(c) }
        }

        override fun getItemCount(): Int = list.size
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
