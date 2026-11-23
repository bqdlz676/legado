package io.legado.app.ui.dict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.utils.toastOnUi

/**
 * 落文 · 阅读期取词弹窗（Compose 自绘壳）。
 *
 * 保留旧构造口径 [DictDialog] (word: String)，阅读页 `showDialogFragment(DictDialog(word))`
 * 入口不变。规则订阅 / 取词执行归 [DictViewModel]，标签页与释义排版归本类 Compose 内容。
 *
 * 兜底：空词 → 提示并关闭；无启用词典 → 空态；取词失败 → 错误文案 + 重试。
 * 富文本图片：`<img>` / Markdown 图片经 [DictRichParser] 切成图片块，Glide 解码为 Bitmap 后
 * 由 Compose [androidx.compose.foundation.Image] 绘制；加载中纸色骨架、失败墨色小字 + 原文 URL，
 * 单击（无 click 动作）/ 长按走统一 [InkHalfSheet] 查看大图。
 */
class DictDialog() : DialogFragment() {

    constructor(word: String) : this() {
        arguments = Bundle().apply { putString("word", word) }
    }

    private val viewModel by viewModels<DictViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val word = arguments?.getString("word").orEmpty()
        if (word.isBlank()) {
            toastOnUi(io.legado.app.R.string.cannot_empty)
            dismiss()
        } else {
            viewModel.attach(word)
        }
        return ComposeView(requireContext()).apply {
            setContent {
                val state = viewModel.uiState.collectAsState().value
                DictLookupContent(
                    state = state,
                    onSelectRule = viewModel::selectRule,
                    onRetry = viewModel::retry,
                    onImageClick = viewModel::onButtonClick,
                    onDismiss = { dismissAllowingStateLoss() },
                )
            }
        }
    }

    @Composable
    private fun DictLookupContent(
        state: DictDialogState,
        onSelectRule: (Int) -> Unit,
        onRetry: () -> Unit,
        onImageClick: (name: String, click: String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var viewerSrc by remember { mutableStateOf<String?>(null) }
        val baseUrl = state.activeRule?.urlRule

        Column(
            Modifier
                .fillMaxWidth()
                .background(ComponentTokens.bgPage)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    state.word,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier.weight(1f),
                )
                InkButton("关闭", onDismiss, variant = InkButtonVariant.GHOST)
            }

            if (state.rules.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.rules.forEachIndexed { index, rule ->
                        val active = index == state.activeIndex
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
                                .background(
                                    if (active) ComponentTokens.brandGold
                                    else ComponentTokens.bgSurface,
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelectRule(index) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                rule.name,
                                style = TextStyle(
                                    color = if (active) ComponentTokens.bgPage
                                    else ComponentTokens.textSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif,
                                ),
                            )
                        }
                    }
                }
            }

            when {
                state.loading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "释义检索中…",
                        style = TextStyle(
                            color = ComponentTokens.textTertiary,
                            fontSize = 14.sp,
                        ),
                    )
                }

                state.error != null -> InkEmptyState(
                    message = state.error ?: "释义获取失败",
                    offline = true,
                    actionLabel = "重试",
                    onAction = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.rules.isEmpty() -> InkEmptyState(
                    message = "暂无启用的词典规则",
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> Column(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val blocks = remember(state.content, baseUrl) {
                        DictRichParser.parse(state.content, baseUrl)
                    }
                    DictRichContent(
                        blocks = blocks,
                        onImageTap = { block ->
                            // 旧链口径：图片自带 click 脚本时单击执行动作；否则单击打开大图。
                            val click = block.click
                            if (click.isNullOrBlank()) {
                                viewerSrc = block.src
                            } else {
                                onImageClick("image", click)
                            }
                        },
                        onImageLongClick = { block -> viewerSrc = block.src },
                    )
                }
            }
        }

        // 大图查看：复用统一半屏 Sheet（独立窗口模式），不新造弹层。
        InkHalfSheet(
            visible = viewerSrc != null,
            onDismiss = { viewerSrc = null },
            dialog = true,
            title = "图片",
            content = { viewerSrc?.let { DictImageViewer(it) } },
        )
    }
}
