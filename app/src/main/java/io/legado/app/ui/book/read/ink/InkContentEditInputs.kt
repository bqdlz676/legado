package io.legado.app.ui.book.read.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.book.read.cycleContentMatchIndex
import io.legado.app.ui.book.read.findContentMatches
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.rememberInkGesture
import kotlinx.coroutines.delay

/**
 * 内容编辑面板 · 输入与搜索区组件。
 *
 * 〔职责边界〕本件只装标题行内改名与正文搜索两簇输入控件；校验口径走
 * [InkContentEditRules]，脏数据 / 离场裁决在状态层，本件不另写判定。
 * 切分理由：标题输入、搜索输入与芯片 / 箭头小控件同属「文本录入 + 命中导航」内聚簇，
 * 与面板骨架、正文编辑区、底部动作区各自独立，便于按职责单独阅读。
 */

/** 标题行：常态展示标题，点击入行内编辑；校验在规则层，空白 / 超长给本地提示不落库。 */
@Composable
internal fun TitleRow(state: InkContentEditState, onRename: (String) -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(state.title) }
    var localError by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!state.titleEditing) {
            BasicText(
                text = state.title.ifBlank { context.getString(R.string.title) },
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(
                        rememberInkGesture(enabled = true) {
                            draft = state.title
                            localError = null
                            state.setTitleEditingState(true)
                        }.gestureModifier,
                    ),
            )
            BasicText(
                text = context.getString(R.string.edit),
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        } else {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it; localError = null },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.width(8.dp))
            InkButton(
                text = context.getString(R.string.ok),
                variant = InkButtonVariant.PRIMARY,
                enabled = !state.saving,
            ) {
                when (InkContentEditRules.validateTitle(draft)) {
                    TitleValidation.Valid -> {
                        onRename(draft.trim())
                        state.setTitleEditingState(false)
                    }
                    TitleValidation.Blank -> localError = context.getString(R.string.title)
                    is TitleValidation.TooLong -> localError = context.getString(R.string.title)
                }
            }
            Spacer(Modifier.width(8.dp))
            InkButton(
                text = context.getString(R.string.cancel),
                variant = InkButtonVariant.GHOST,
            ) {
                state.setTitleEditingState(false)
                localError = null
            }
        }
    }
    if (localError != null) {
        BasicText(
            text = localError!!,
            style = TextStyle(
                color = ComponentTokens.brandCopper,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** 搜索栏：查询 + 正则 / 大小写开关 + 上一条 / 下一条 + 计数；正则非法给错误提示。 */
@Composable
internal fun SearchBar(state: InkContentEditState) {
    val context = LocalContext.current

    LaunchedEffect(state.searchVisible, state.searchQuery, state.regex, state.matchCase, state.content) {
        if (!state.searchVisible || state.searchQuery.isBlank()) {
            state.setMatches(emptyList(), -1)
            state.updateSearchError(null)
            return@LaunchedEffect
        }
        // 120ms 防抖：与旧版 scheduleSearch 同口径，输入停顿后再跑正则扫描。
        delay(120)
        val matches = runCatching {
            findContentMatches(state.content, state.searchQuery, state.regex, state.matchCase)
        }.getOrElse {
            state.updateSearchError(context.getString(R.string.content_search_invalid_regex))
            state.setMatches(emptyList(), -1)
            return@LaunchedEffect
        }
        state.updateSearchError(null)
        val index = matches.indexOfFirst { it.first >= 0 }
            .let { if (it >= 0) it else if (matches.isEmpty()) -1 else 0 }
        state.setMatches(matches, index)
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                ),
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 8.dp)) { inner() }
                },
            )
            Chip(
                label = context.getString(R.string.regex),
                checked = state.regex,
            ) { state.regex = !state.regex }
            Chip(
                label = context.getString(R.string.content_search_match_case),
                checked = state.matchCase,
            ) { state.matchCase = !state.matchCase }
            NavArrow("‹", enabled = state.matches.isNotEmpty()) {
                val next = cycleContentMatchIndex(state.matchIndex, -1, state.matches.size)
                state.setMatches(state.matches, next)
            }
            NavArrow("›", enabled = state.matches.isNotEmpty()) {
                val next = cycleContentMatchIndex(state.matchIndex, 1, state.matches.size)
                state.setMatches(state.matches, next)
            }
            BasicText(
                text = if (state.matches.isEmpty()) "0/0" else
                    "${state.matchIndex + 1}/${state.matches.size}",
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            NavArrow("✕", enabled = true) { state.searchVisible = false }
        }
        state.searchError?.let { err ->
            BasicText(
                text = err,
                style = TextStyle(
                    color = ComponentTokens.brandCopper,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

/** 搜索开关小芯片：选中暖金底，未选绢帛描边；墨触手势，无 Material ripple。 */
@Composable
private fun Chip(label: String, checked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .background(if (checked) ComponentTokens.brandGold else ComponentTokens.bgSurface)
            .border(1.dp, ComponentTokens.colorDivider, RoundedCornerShape(ComponentTokens.buttonRadius))
            .then(rememberInkGesture(enabled = true, onClick = onClick).gestureModifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (checked) ComponentTokens.bgPage else ComponentTokens.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/** 搜索上一条 / 下一条 / 关闭箭头按钮；禁用态压淡 30%。 */
@Composable
private fun NavArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else ComponentTokens.disabledAlpha)
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .then(rememberInkGesture(enabled = enabled, onClick = onClick).gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}
