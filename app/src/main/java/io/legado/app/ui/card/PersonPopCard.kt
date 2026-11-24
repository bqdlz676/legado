package io.legado.app.ui.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 人物关系卡（切片-25「九、人物关系卡（力导向图节点弹出）」逐字落地）。
 *
 * 外形 / 阴影 / 圆角 / 颗粒全部复用 [CardSurface] 基座，色值只取 [CardTokens]，不另造 token。
 * 本卡是力导向图节点的浮层气泡：节点双击弹出，宽为屏宽 0.45；「点卡片外部消失」不实现遮罩，
 * 只把 [onDismissRequest] 回调抛给宿主，由宿主在卡片外部挂点击检测后调用，本卡不铺半透明底。
 *
 * @param name 本名；规格 14sp 墨色 Bold，整卡最高一级文字。
 * @param countryTitle 国别·封号；规格 10sp 石青，紧跟本名的次级标注。
 * @param relation 与当前人物的关系；规格 10sp 云灰，弱于国别行。
 * @param quote 相关引文一句；规格 9sp 石青斜体，最低一级注释性文字。
 * @param visible 是否展示；驱动 [AnimatedVisibility] 的 200ms 淡入，反转后同路径淡出。
 * @param onDismissRequest 点卡片外部时的消失回调；本卡不实现遮罩，外部点击检测由宿主完成。
 * @param onDetailClick 点「进人物详情 →」回调；跳转人物详情页由宿主承接。
 */
@Composable
fun PersonPopCard(
    name: String,
    countryTitle: String,
    relation: String,
    quote: String,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 200ms 淡入：规格「节点双击弹出 200ms 淡入」；只给 enter，退出沿用同一可见性反转路径，不另写 fadeOut。
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 200)),
        modifier = modifier,
    ) {
        CardSurface(
            state = CardState.Default,
            modifier = Modifier
                // 宽=屏宽×0.45：关系卡为浮层小卡，半屏宽避免遮挡力导向图其余节点；适用：图上节点旁弹出。
                .fillMaxWidth(0.45f)
                // 内边距 8dp：与书架卡同口径，文字不贴 16dp 圆角边缘；规格未给内边距，兜底取既有卡片气口。
                .padding(8.dp),
        ) {
            Column {
                // 本名 14sp 墨色 Bold：规格主标题；适用：卡片首行人物姓名。
                BasicText(
                    text = name,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = CardTokens.ink,
                        fontWeight = FontWeight.Bold,
                    ),
                )

                // 行间距 4dp：主标题与次级标注之间留小气口；规格未给行距，兜底与 10sp 文字尺度匹配。
                Spacer(modifier = Modifier.height(4.dp))

                // 国别·封号 10sp 石青：规格次级标注；适用：朝代 / 爵位 / 称号一行。
                BasicText(
                    text = countryTitle,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = CardTokens.stoneBlue,
                    ),
                )

                // 行间距 4dp：国别行与关系行同档小间距；适用：两条 10sp 标注之间。
                Spacer(modifier = Modifier.height(4.dp))

                // 关系 10sp 云灰：规格弱标注；适用：与当前人物的亲疏 / 立场关系，明度低于石青行。
                BasicText(
                    text = relation,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = CardTokens.cloudGray,
                    ),
                )

                // 行间距 6dp：关系行与引文之间略拉开，引文属注释层；规格未给间距，兜底区分信息层级。
                Spacer(modifier = Modifier.height(6.dp))

                // 相关引文一句 9sp 石青斜体：规格最低一级注释；斜体区分引文与陈述文字。
                BasicText(
                    text = quote,
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = CardTokens.stoneBlue,
                        fontStyle = FontStyle.Italic,
                    ),
                )

                // 行间距 6dp：引文与入口链接之间留气口；适用：正文层与可点入口的分隔。
                Spacer(modifier = Modifier.height(6.dp))

                // 「进人物详情 →」10sp 暖金：规格强调入口；点击回调交宿主跳详情，本卡不内建路由。
                BasicText(
                    text = "进人物详情 →",
                    modifier = Modifier.clickable { onDetailClick() },
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = CardTokens.warmGold,
                    ),
                )
            }
        }
    }
}
