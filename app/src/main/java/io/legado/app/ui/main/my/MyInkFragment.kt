package io.legado.app.ui.main.my

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.FragmentMyInkBinding
import io.legado.app.databinding.ItemInkMyBinding
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.service.AutoTaskScheduler
import io.legado.app.service.McpService
import io.legado.app.service.WebService
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.about.checkAppUpdate
import io.legado.app.ui.autoTask.AutoTaskActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.showHelp
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 落文 · 我的
 *
 * 整页自绘：朱砂短竖 + 宋体标题，下面是自己排的列表行，
 * 不用 androidx Preference 那套 Material 行。动作全部原地接回原有页面。
 */
class MyInkFragment() : BaseFragment(R.layout.fragment_my_ink), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentMyInkBinding::bind)

    private val ctx get() = requireContext()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvHelp.setOnClickListener { showHelp("appHelp") }
        binding.rvMy.layoutManager = LinearLayoutManager(ctx)
        binding.rvMy.adapter = Adapter(buildRows())
    }

    private fun open(cls: Class<*>) {
        startActivity(Intent(ctx, cls))
    }

    private fun openConfig(tag: String) {
        startActivity(Intent(ctx, ConfigActivity::class.java).putExtra("configTag", tag))
    }

    private fun buildRows(): List<Row> {
        val rows = mutableListOf<Row>()

        rows += Row(Row.HEAD, "书 库")
        rows += Row(Row.NAV, "书源管理", "新建、导入、编辑或管理书源", R.drawable.ic_cfg_source) {
            open(BookSourceActivity::class.java)
        }
        rows += Row(Row.NAV, "书签", "全部书籍的书签", R.drawable.ic_bookmark) {
            open(AllBookmarkActivity::class.java)
        }
        rows += Row(Row.NAV, "阅读记录", "读过什么、读了多少", R.drawable.ic_history) {
            open(ReadRecordActivity::class.java)
        }
        rows += Row(Row.NAV, "文件管理", "本地书籍与缓存文件", R.drawable.ic_folder) {
            open(FileManageActivity::class.java)
        }

        rows += Row(Row.HEAD, "系 统")
        rows += Row(Row.NAV, "定时任务", "管理按计划执行的 JavaScript 任务", R.drawable.ic_auto_page) {
            open(AutoTaskActivity::class.java)
        }
        rows += Row(
            Row.SWITCH, "运行定时任务", "重启后保留计划；系统可能延后实际执行时间",
            R.drawable.ic_auto_page, PreferKey.autoTaskService
        )
        rows += Row(Row.NAV, "替换管理", "管理替换规则", R.drawable.ic_cfg_replace) {
            open(ReplaceRuleActivity::class.java)
        }
        rows += Row(Row.NAV, "字典规则", "配置字典规则", R.drawable.ic_translate) {
            open(DictRuleActivity::class.java)
        }
        rows += Row(Row.NAV, "TXT 目录规则", "配置 TXT 目录识别规则", R.drawable.ic_cfg_source) {
            open(TxtTocRuleActivity::class.java)
        }
        rows += Row(Row.VALUE, "主题模式", "日间 / 夜间 / 跟随系统", R.drawable.ic_cfg_theme, "", "跟随系统") {
            openConfig(ConfigTag.THEME_CONFIG)
        }
        rows += Row(
            Row.SWITCH, "Web 服务", "用浏览器写源或看书", R.drawable.ic_cfg_web,
            PreferKey.webService
        )
        rows += Row(
            Row.SWITCH, "MCP 服务", "书源开发工具", R.drawable.ic_cfg_web,
            PreferKey.mcpService
        )

        rows += Row(Row.HEAD, "其 他")
        rows += Row(Row.NAV, "备份与恢复", "WebDAV 与本地备份", R.drawable.ic_cfg_backup) {
            openConfig(ConfigTag.BACKUP_CONFIG)
        }
        rows += Row(Row.NAV, "其他设置", "阅读以外的所有开关", R.drawable.ic_cfg_other) {
            openConfig(ConfigTag.OTHER_CONFIG)
        }
        rows += Row(Row.NAV, "关于落文", "版本、开源许可", R.drawable.ic_cfg_about) {
            open(AboutActivity::class.java)
        }
        rows += Row(Row.NAV, "检查更新", "看看有没有新版本", R.drawable.ic_update) {
            checkAppUpdate()
        }
        rows += Row(Row.NAV, "退出", "", R.drawable.ic_exit) {
            activity?.finish()
        }
        return rows
    }

    private data class Row(
        val kind: Int,
        val title: String,
        val sub: String = "",
        val icon: Int = 0,
        val key: String = "",
        val valueText: String = "",
        val action: (() -> Unit)? = null
    ) {
        companion object {
            const val HEAD = 0
            const val NAV = 1
            const val SWITCH = 2
            const val VALUE = 3
        }
    }

    private inner class Adapter(private val rows: List<Row>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int =
            if (rows[position].kind == Row.HEAD) 1 else 0

        override fun getItemCount(): Int = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 1) {
                val tv = android.widget.TextView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    textSize = 10f
                    letterSpacing = 0.22f
                    setTextColor(0xFFA2967F.toInt())
                    setPadding(dp(21), dp(19), dp(20), dp(6))
                    typeface = android.graphics.Typeface.SANS_SERIF
                }
                HeadHolder(tv)
            } else {
                val v = LayoutInflater.from(ctx).inflate(R.layout.item_ink_my, parent, false)
                RowHolder(ItemInkMyBinding.bind(v))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            if (holder is HeadHolder) {
                holder.tv.text = row.title
                return
            }
            holder as RowHolder
            val b = holder.b
            b.tvTitle.text = row.title
            b.tvSub.text = row.sub
            b.tvSub.visibility = if (row.sub.isEmpty()) View.GONE else View.VISIBLE

            if (row.icon != 0) {
                b.ivIcon.setImageResource(row.icon)
                b.ivIcon.setColorFilter(0xFF8B6914.toInt())
                b.ivIcon.visibility = View.VISIBLE
            } else {
                b.ivIcon.visibility = View.GONE
            }

            b.swToggle.visibility = View.GONE
            b.tvValue.visibility = View.GONE
            b.tvArrow.visibility = View.GONE

            when (row.kind) {
                Row.SWITCH -> {
                    b.swToggle.visibility = View.VISIBLE
                    val v = ctx.getPrefBoolean(row.key, false)
                    b.swToggle.setInitial(v)
                    b.swToggle.onCheckedChanged = { checked ->
                        ctx.putPrefBoolean(row.key, checked)
                        when (row.key) {
                            PreferKey.webService ->
                                if (checked) WebService.start(ctx) else WebService.stop(ctx)

                            PreferKey.mcpService ->
                                if (checked) McpService.start(ctx) else McpService.stop(ctx)

                            PreferKey.autoTaskService -> {
                                val app = ctx.applicationContext
                                if (checked) Coroutine.async {
                                    AutoTaskScheduler.refresh(app)
                                } else AutoTaskScheduler.cancelAll(app)
                            }
                        }
                    }
                    b.root.setOnClickListener {
                        b.swToggle.checked = !b.swToggle.checked
                        b.swToggle.onCheckedChanged?.invoke(b.swToggle.checked)
                    }
                }

                Row.VALUE -> {
                    b.tvValue.visibility = View.VISIBLE
                    b.tvValue.text = row.valueText
                    b.tvArrow.visibility = View.VISIBLE
                    b.root.setOnClickListener { row.action?.invoke() }
                }

                else -> {
                    b.tvArrow.visibility = View.VISIBLE
                    b.root.setOnClickListener { row.action?.invoke() }
                }
            }
        }

        inner class HeadHolder(val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv)
        inner class RowHolder(val b: ItemInkMyBinding) : RecyclerView.ViewHolder(b.root)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
