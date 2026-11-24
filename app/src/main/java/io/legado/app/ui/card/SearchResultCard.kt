package io.legado.app.ui.card

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 搜索结果卡（切片-25「六、搜索结果卡」逐字落地）。
 *
 * 规格原文：书名 14sp 墨色 + 作者·年份 10sp 石青；字数·进度 10sp 云灰；来源标签 8sp 石青；
 * 高 56dp；匹配文字朱砂高亮；在线源结果追加在本地之后 + 暖金小标签「在线」。
 *
 * 外形 / 阴影 / 圆角 / 颗粒全部走 [CardSurface] 基座，常规色值只取 [CardTokens]；
 * 本组件只补横排两行结果文案、命中词高亮与在线源小标签。列表排序（本地在前、在线追加在后）
 * 由宿主检索流负责，本卡不做排序。
 */

/**
 * 搜索结果卡数据模型。
 *
 * @param title 书名；适用：首行主标题，14sp 墨色，命中片段朱砂高亮。
 * @param author 作者；适用：次行与年份并列，10sp 石青；为空时不占分隔位。
 * @param year 年份；适用：次行作者·年份标注，10sp 石青；为空时不占分隔位。
 * @param wordCount 字数；适用：次行字数·进度标注，10sp 云灰；为空时不占分隔位。
 * @param progress 阅读进度；适用：次行字数·进度标注，10sp 云灰；为空时不占分隔位。
 * @param sourceLabel 来源标签（如「本地」）；适用：行尾 8sp 石青注记。
 * @param isOnline 是否在线源结果；适用：true 时书名行末尾追加暖金描边小标签「在线」。
 */
data class SearchResultCardData(
    val title: String,
    val author: String,
    val year: String,
    val wordCount: String,
    val progress: String,
    val sourceLabel: String,
    val isOnline: Boolean,
)

/**
 * 单张搜索结果卡：56dp 通栏结果行，点击回调 [onClick] 进书详情 / 阅读（路由由宿主实现）。
 *
 * @param data 结果数据；任一文案槽位为空时对应片段自动省略，不留多余「·」分隔符。
 * @param query 当前搜索词；适用：书名 / 作者命中片段朱砂高亮；空词或未命中时原文呈现。
 * @param state 基座五态；Loading 态由 [CardSurface] 自绘骨架灰块 + 暖金斜光带。
 * @param modifier 外部对齐 / 边距；行高 56dp 已在本组件内按规格固定。
 * @param onClick 整卡点击；适用：选中该结果。
 */
@Composable
fun SearchResultCard(
    data: SearchResultCardData,
    query: String,
    state: CardState = CardState.Default,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    // 作者·年份：两段任一为空则省略「·」，避免出现悬空分隔符；适用：老书年份缺失的兜底。
    val authorYear = listOfNotNull(
        data.author.ifBlank { null },
        data.year.ifBlank { null },
    ).joinToString(" · ")

    // 字数·进度：同上兜底；适用：未缓存字数或新书无进度的结果。
    val wordProgress = listOfNotNull(
        data.wordCount.ifBlank { null },
        data.progress.ifBlank { null },
    ).joinToString(" · ")

    CardSurface(
        state = state,
        modifier = modifier
            // 高 56dp：规格固定行高；适用：搜索结果列表等高通栏行，不随文案撑高。
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                // 横向 12dp 内边距：与基座卡片间距同值，文案不贴 16dp 圆角边缘；纵向 8dp 为规格未给的兜底值，适用：两行文案在 56dp 内垂直居中。
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                // 文字列占满剩余宽度，把行尾来源标签顶到右侧；适用：长书名 / 长作者时压缩文字列而非挤压标签。
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // 书名与在线标签间隔 6dp：标签紧贴书名右侧，不远离主标题；适用：在线源识别。
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(
                        text = highlightMatch(data.title, query),
                        style = TextStyle(
                            color = CardTokens.ink,
                            // 14sp：规格书名主标题字号；适用：行内最高一级标题。
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                    // 在线源小标签：仅 isOnline 渲染；适用：在线源结果追加在本地之后时的来源区分。
                    if (data.isOnline) {
                        OnlineSourceTag()
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        text = authorYear,
                        style = TextStyle(
                            color = CardTokens.stoneBlue,
                            // 10sp：规格作者·年份次级标注字号；适用：作者与朝代年份行。
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                    BasicText(
                        text = wordProgress,
                        style = TextStyle(
                            color = CardTokens.cloudGray,
                            // 10sp：规格字数·进度辅助标注字号；适用：字数与阅读进度行。
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                }
            }

            BasicText(
                text = data.sourceLabel,
                style = TextStyle(
                    color = CardTokens.stoneBlue,
                    // 8sp：规格来源标签字号，卡片内最低一级注记；适用：行尾来源注记。
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

/**
 * 在线源小标签：暖金 1dp 描边、圆角 4dp，文字暖金 8sp。
 * 为什么描边而非实心：规格要求「暖金小标签」，描边在纸白底上更轻，不抢书名主视觉；
 * 适用：仅在线源结果，本地结果不渲染本标签。
 */
@Composable
private fun OnlineSourceTag() {
    Box(
        modifier = Modifier
            // 1dp 暖金描边：规格指定在线标签边框；适用：标签轮廓，不铺实心暖金。
            .border(
                width = 1.dp,
                color = CardTokens.warmGold,
                // 圆角 4dp：规格指定标签圆角；适用：小标签四角轻圆，区别于卡片 16dp 大圆角。
                shape = RoundedCornerShape(4.dp),
            )
            // 横向 4dp / 纵向 1dp 内边距：规格未给的兜底值；适用：8sp 文字在描边内不贴边。
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        BasicText(
            text = "在线",
            style = TextStyle(
                color = CardTokens.warmGold,
                fontSize = 8.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/**
 * 命中词朱砂高亮：忽略大小写匹配，保留原文大小写；无命中 / 空词时原样返回。
 * 为什么字面量朱砂色：验收判据 2 允许该卡类原文指定的朱砂色在组件内落字面量，
 * 与 [CardTokens.cinnabar] 同口径（#C23B2E），不得另改明度或色相；适用：书名 / 作者命中片段。
 */
private fun highlightMatch(text: String, query: String): AnnotatedString {
    val key = query.trim()
    if (key.isEmpty()) return AnnotatedString(text)
    val start = text.lowercase().indexOf(key.lowercase())
    if (start < 0) return AnnotatedString(text)
    val end = start + key.length
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = Color(0xFFC23B2E))) {
            append(text.substring(start, end))
        }
        append(text.substring(end))
    }
}
