package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookSourceCheckState
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.model.Debug
import io.legado.app.ui.components.ComponentTokens

/**
 * 落文 · 书源管理列表行（Compose 自绘，替代旧 BookSourceAdapter / item_book_source.xml）。
 *
 * 行内信息与旧适配器逐项对齐：名称(分组)、域名归组标题、启用开关、发现角标、JS 角标、
 * 校验状态 / 实时校验文案、书架引用数、更多操作入口。点击热区 ≥48dp；颜色只取 token。
 */
@Composable
fun SourceManageRow(
    source: BookSourcePart,
    selected: Boolean,
    inSelectionMode: Boolean,
    showHostHeader: Boolean,
    hostLabel: String,
    showCheckStatus: Boolean,
    showBookshelfCount: Boolean,
    bookshelfCount: Int,
    onToggleSelect: () -> Unit,
    onOpenEdit: () -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    onToggleExplore: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (showHostHeader) {
            BasicTextLine(
                text = hostLabel,
                style = TextStyle(
                    color = ComponentTokens.brandCopper,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ComponentTokens.cardRadius))
                .background(ComponentTokens.bgSurface)
                .border(1.dp, ComponentTokens.colorDivider, RoundedCornerShape(ComponentTokens.cardRadius))
                .clickable { if (inSelectionMode) onToggleSelect() else onOpenEdit() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SelectCheck(checked = selected, onClick = onToggleSelect)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextLine(
                    text = source.getDisPlayNameGroup(),
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                MetaRow(source, showCheckStatus, showBookshelfCount, bookshelfCount)
            }

            SourceSwitch(checked = source.enabled, onChange = onToggleEnable)
            MoreButton(onClick = onMore)
        }
    }
}

@Composable
private fun MetaRow(
    source: BookSourcePart,
    showCheckStatus: Boolean,
    showBookshelfCount: Boolean,
    bookshelfCount: Int,
) {
    val badges = mutableListOf<String>()
    if (source.hasJs) badges += "JS"
    if (source.hasExploreUrl) {
        badges += if (source.enabledExplore) {
            stringResource(R.string.tag_explore_enabled)
        } else {
            stringResource(R.string.tag_explore_disabled)
        }
    }
    if (showBookshelfCount) {
        badges += stringResource(R.string.source_bookshelf_count, bookshelfCount)
    }
    val statusLine = checkStatusLine(source, showCheckStatus)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badges.forEach { badge ->
            BasicTextLine(
                text = badge,
                style = TextStyle(
                    color = ComponentTokens.brandCopper,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
        if (statusLine != null) {
            BasicTextLine(
                text = statusLine,
                style = TextStyle(
                    color = checkStatusColor(source.checkStatus),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

@Composable
private fun checkStatusLine(source: BookSourcePart, showCheckStatus: Boolean): String? {
    val liveMsg = if (Debug.isChecking) Debug.debugMessageMap[source.bookSourceUrl].orEmpty() else ""
    if (liveMsg.isNotEmpty()) return liveMsg
    if (!showCheckStatus) return null
    val status = when (source.checkStatus) {
        BookSourceCheckState.PASSED -> stringResource(R.string.source_check_passed)
        BookSourceCheckState.FAILED -> stringResource(R.string.source_check_failed)
        BookSourceCheckState.UNCHECKABLE -> stringResource(R.string.source_check_uncheckable)
        else -> stringResource(R.string.source_check_needed)
    }
    return if (source.checkDetail.isBlank()) status else "$status：${source.checkDetail}"
}

private fun checkStatusColor(status: String) = when (status) {
    BookSourceCheckState.PASSED -> ComponentTokens.brandGold
    BookSourceCheckState.FAILED -> ComponentTokens.textAccent
    BookSourceCheckState.UNCHECKABLE -> ComponentTokens.textTertiary
    else -> ComponentTokens.textSecondary
}

/** 多选勾选框：48dp 热区，选中暖金描边 + 金心。 */
@Composable
private fun SelectCheck(checked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) ComponentTokens.brandGold else ComponentTokens.bgPage,
                )
                .border(
                    1.dp,
                    if (checked) ComponentTokens.brandGold else ComponentTokens.colorDivider,
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                BasicTextLine(
                    text = "✓",
                    style = TextStyle(
                        color = ComponentTokens.bgPage,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

/** 启用开关：自绘轨道 + 滑块，不引 Material Switch；热区 48dp。 */
@Composable
private fun SourceSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(44.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (checked) ComponentTokens.brandGold else ComponentTokens.colorDivider,
                )
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ComponentTokens.bgPage),
            )
        }
    }
}

@Composable
private fun MoreButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ComponentTokens.textSecondary),
                )
            }
        }
    }
}

/** 轻量文本行：统一 Serif 与默认行高，避免散落 TextStyle。 */
@Composable
private fun BasicTextLine(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style,
        modifier = modifier,
    )
}
