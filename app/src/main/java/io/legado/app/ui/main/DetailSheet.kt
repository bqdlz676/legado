package io.legado.app.ui.main

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import io.legado.app.help.NameResolver
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.main.detail.DetailAction
import io.legado.app.ui.main.detail.DetailSection
import io.legado.app.ui.main.detail.DetailTarget
import io.legado.app.ui.main.detail.DetailSheetContent
import io.legado.app.ui.main.detail.DetailSheetState
import io.legado.app.ui.main.detail.DetailSheetStateBuilder

/**
 * 底部半屏详情：原文不离眼（B1 重做）。
 *
 * 原来点字、点人、点地名都是 launch 一个全屏 Activity，返回时阅读页重建、位置丢。
 * 半屏层保留这一初衷：阅读页一直在场，返回不需要重建。
 *
 * B1 贴皮拆除：旧 Material 底部弹窗壳、旧 XML 布局、视图绑定与列表适配编排全部废弃；
 * 壳只保留 Fragment 生命周期与既有四工厂 / show(fm, tag) 入口形状（四个调用点零改动），
 * 内部由 [ComposeView] 承载统一半屏组件 [InkHalfSheet]，
 * 数据采集归 [DetailSheetStateBuilder]，渲染归 [DetailSheetContent]。
 *
 * 需要看全的再从底部动作行跳原全屏页面（人物详情 / 古今地图）。
 */
class DetailSheet : DialogFragment() {

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

    /** 采集好的不可变快照；onCreateView 阶段构建，组合期只读不再重算。 */
    private var state: DetailSheetState? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        val args = arguments
        val type = args?.getString(ARG_TYPE) ?: "char"
        val name = args?.getString(ARG_NAME).orEmpty()
        // place 的 ARG_EXTRA 是同名候选串，person/year 才是整型；按型拆包，避免串/Int 互读崩。
        val extraInt = if (type == "place") {
            Int.MIN_VALUE
        } else {
            args?.getInt(ARG_EXTRA) ?: Int.MIN_VALUE
        }
        val detail = args?.getString(ARG_DETAIL).orEmpty()
        val placeOthers = if (type == "place") {
            args?.getString(ARG_EXTRA).orEmpty().split('\u0001').filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        state = runCatching {
            DetailSheetStateBuilder.build(ctx, type, name, extraInt, detail, placeOthers)
        }.getOrElse {
            fallbackState(name)
        }

        return ComposeView(ctx).apply {
            setContent {
                val snapshot = state ?: fallbackState(name)
                InkHalfSheet(
                    visible = true,
                    onDismiss = { dismissAllowingStateLoss() },
                    dialog = true,
                    title = snapshot.title,
                    content = {
                        DetailSheetContent(
                            state = snapshot,
                            onTimelineYear = ::switchYear,
                            onAction = ::runAction,
                        )
                    },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 遮罩 / 暗化统一由 InkHalfSheet 茶玻层绘制；本 Fragment 窗口只做透明承载，不叠弹窗默认遮罩。
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setDimAmount(0f)
    }

    override fun onDestroyView() {
        MapWebHolder.detach()
        state = null
        super.onDestroyView()
    }

    /** 纪年时间轴拖到新十年：关闭本屏后按新年份重开同型半屏（口径同旧 switchYear）。 */
    private fun switchYear(y: Int) {
        val fm = (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
        dismiss()
        if (fm != null) DetailSheet.year(y).show(fm, "detail_year")
    }

    /** 底部动作行：跳全屏原页面，随后关闭半屏；宿主已销毁则不启 Activity、只收口。 */
    private fun runAction(action: DetailAction) {
        val ctx = context
        val hostAlive = activity?.let { !it.isFinishing && !it.isDestroyed } ?: (ctx != null)
        if (ctx != null && hostAlive) {
            when (val target = action.target) {
                is DetailTarget.PersonDetail -> ctx.startActivity(
                    Intent(ctx, PersonDetailActivity::class.java)
                        .putExtra("name", target.name)
                        .putExtra("appearances", "[]")
                )

                is DetailTarget.PlaceMap -> {
                    val i = Intent(ctx, PlaceMapActivity::class.java)
                    if (target.year != null) i.putExtra("year", target.year)
                    if (target.name.isNotBlank()) i.putExtra("name", target.name)
                    if (target.newTask) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }
            }
        }
        dismissAllowingStateLoss()
    }

    /** 采集异常兜底：不出空白态，给一条可读提示（R61 边界）。 */
    private fun fallbackState(title: String): DetailSheetState = DetailSheetState(
        type = "fallback",
        title = title,
        subtitle = "",
        sections = listOf(DetailSection("提示", listOf("内容加载失败，请返回后重试。"))),
        action = null,
        timelineYear = null,
    )
}
