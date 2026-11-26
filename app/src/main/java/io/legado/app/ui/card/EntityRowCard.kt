package io.legado.app.ui.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 名物条目卡片（详情列表行）（切片-25「八、名物条目卡片」逐字落地）。
 *
 * 规格原文：名称 14sp 墨色 Bold；年代·国别·类别 10sp 石青；一句话描述 11sp 云灰；
 * 出处 9sp 石青斜体；高自适应 60-80dp；点击半屏 Sheet 展开完整描述。
 *
 * 五态 / 阴影 / 圆角 / 颗粒一律走 [CardBase]，色值全部取 [CardTokens]，不在本组件另造
 * 色值、圆角或动效；本组件只补名称行、描述行与出处行。半屏 Sheet 由宿主实现，本卡只留
 * [onClick] 回调，不在卡片内弹层。
 */

/**
 * 单张名物条目行卡：详情列表内的一行，点击回调 [onClick] 由宿主拉起半屏 Sheet 展开完整描述。
 *
 * @param name 名物名称；适用：行内主标题，墨色 Bold 14sp。
 * @param meta 年代·国别·类别合并标注（调用方以「·」连接）；适用：名称旁次级标注行，石青 10sp。
 * @param desc 一句话描述；适用：名称下方短描述，云灰 11sp，两行内截断。
 * @param source 出处；适用：最低一级注释，石青 9sp 斜体。
 * @param state 基座五态；Loading 态由 [CardBase] 自绘骨架灰块 + 暖金斜光带。
 * @param modifier 外部对齐 / 尺寸；宽度与高度区间已在本组件内按规格固定。
 * @param onClick 整卡点击；适用：宿主拉起半屏 Sheet 展示完整描述，本卡不导航、不弹层。
 */
@Composable
fun EntityRowCard(
    name: String,
    meta: String,
    desc: String,
    source: String,
    onClick: () -> Unit,
    state: CardState = CardState.Default,
    modifier: Modifier = Modifier,
) {
    CardBase(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            // 左右各 12dp 边距：沿用卡片栅格同值气口；适用：列表行与屏幕边缘不贴死。
            .padding(horizontal = 12.dp)
            // 高自适应 60-80dp：规格「高自适应 60-80dp」；适用：按描述 / 出处行数在区间内伸缩，
            // 不足 60dp 托底、超过 80dp 封顶，避免单行过扁或长描述撑破列表节奏。
            .heightIn(min = 60.dp, max = 80.dp)
            // 整卡可点：点击交宿主拉起半屏 Sheet；按压态阴影由基座五态映射，不在此另写 ripple。
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                // 纵向内边距 10dp 为规格未给的兜底值；适用：三行文字不贴 16dp 圆角上下边缘。
                .padding(horizontal = 12.dp, vertical = 10.dp),
            // 行间距 6dp 为规格未给的兜底值；适用：名称 / 描述 / 出处三行之间保持同值呼吸感。
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 名称行：名称 + 年代·国别·类别标注横向并排，名称为主、标注为辅。
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (name.isNotBlank()) {
                    BasicText(
                        text = name,
                        style = TextStyle(
                            // 14sp：规格名称字号；适用：行内主标题，与 CardTokens.text14 同值。
                            fontSize = 14.sp,
                            // 墨色：规格名称色；适用：主文字最高对比层级。
                            color = CardTokens.ink,
                            // Bold：规格名称字重；适用：名称在一行内先被读到。
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                if (meta.isNotBlank()) {
                    // 4dp 间隔为规格未给的兜底值；适用：名称与标注同排时不贴死，保持同级气口；仅名称与标注同时存在时占位。
                    if (name.isNotBlank()) Spacer(Modifier.width(4.dp))
                    BasicText(
                        text = meta,
                        style = TextStyle(
                            // 10sp：规格年代·国别·类别字号；适用：名称旁次级标注，与 CardTokens.text10 同值。
                            fontSize = 10.sp,
                            // 石青：规格标注色；适用：次级标注不抢名称层级。
                            color = CardTokens.stoneBlue,
                        ),
                    )
                }
            }

            if (desc.isNotBlank()) {
                BasicText(
                    text = desc,
                    // 两行截断：一句话描述允许折两行；适用：高自适应区间内容纳短句，超长不再外溢。
                    maxLines = 2,
                    style = TextStyle(
                        // 11sp：规格一句话描述字号；适用：描述行，与 CardTokens.text11 同值。
                        fontSize = 11.sp,
                        // 云灰：规格描述色；适用：辅助说明层级，弱于名称与标注。
                        color = CardTokens.cloudGray,
                    ),
                )
            }

            if (source.isNotBlank()) {
                BasicText(
                    text = source,
                    style = TextStyle(
                        // 9sp：规格出处字号；适用：最低一级注释性文字，与 CardTokens.text9 同值。
                        fontSize = 9.sp,
                        // 石青：规格出处色；适用：注释标注与 meta 同色系，弱于描述。
                        color = CardTokens.stoneBlue,
                        // 斜体：规格出处斜体；适用：引文 / 出处以斜体区别于正文行。
                        fontStyle = FontStyle.Italic,
                    ),
                )
            }
        }
    }
}
