package io.legado.app.ui.card

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 发现页「专题横条卡」（切片-25「四、发现页 · 专题横条卡」逐字落地）。
 *
 * 规格原文：标题竖排 14sp 墨色 Bold + 右箭头；预览 10sp 石青；金点横排表示典故数量
 * （如 28 颗）；满宽（左右各 12dp 边距），高 72dp；毛玻璃 + 纸白 70%；点击进典故列表页。
 *
 * 五态 / 阴影 / 圆角 / 颗粒一律走 [CardBase]，色值全部取 [CardTokens]，不在本组件另造
 * 色值、圆角或动效；本组件只补竖排标题、预览行、金点数量条与毛玻璃底层。
 */

/**
 * 专题横条卡数据模型。
 *
 * @param title 专题名；适用：竖排主标题，墨色 Bold 14sp。
 * @param preview 代表典故预览短句；适用：标题旁次级标注行，石青 10sp，单行截断。
 * @param allusionCount 典故数量；适用：横排金点颗数，超过 28 颗以「+N」收束。
 */
data class TopicCardData(
    val title: String,
    val preview: String,
    val allusionCount: Int,
)

/**
 * 单张专题横条卡：满宽通栏，点击回调 [onClick] 进典故列表页（列表页由宿主路由，本卡不导航）。
 *
 * @param data 专题数据；标题 / 预览为空时对应槽位不渲染，数量 ≤ 0 时金点条留空。
 * @param state 基座五态；Loading 态由 [CardBase] 自绘骨架灰块 + 暖金斜光带。
 * @param modifier 外部对齐 / 尺寸；宽高已在本组件内按规格固定。
 * @param onClick 整卡点击；适用：跳转典故列表页。
 */
@Composable
fun TopicCard(
    data: TopicCardData,
    state: CardState = CardState.Default,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    // 卡片外形圆角复用基座 16dp；shape 同时供毛玻璃底色 / 裁剪复用，避免各造一个 Shape。
    val shape: Shape = RoundedCornerShape(CardTokens.cardRadius)

    CardBase(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            // 左右各 12dp 边距：规格「满宽（左右各 12dp 边距）」；适用：通栏横条与屏幕边缘留同值气口。
            .padding(horizontal = 12.dp)
            // 高 72dp：规格横条卡固定高度；适用：单行通栏，不随预览长短或金点排数撑高。
            .height(72.dp)
            // 整卡可点：点击进典故列表页；按压态阴影由基座五态映射，不在此另写 ripple。
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 毛玻璃底层：纸白 70% 半透明 + 模糊降级判断（见下方 helper）。
            Box(
                Modifier
                    .fillMaxSize()
                    .topicGlassBackdrop(shape),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    // 内边距横向 12dp / 纵向 10dp 为规格未给的兜底值；适用：竖排标题与金点不贴 16dp 圆角边缘。
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 标题竖排列：14sp 墨色 Bold，右侧跟同级右箭头，表达可点进列表。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (data.title.isNotBlank()) {
                        TopicVerticalText(
                            text = data.title,
                            // 14sp：规格专题标题字号；适用：通栏卡主标题，与 CardTokens.text14 同值。
                            fontSize = 14.sp,
                            color = CardTokens.ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    BasicText(
                        text = "→",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            // 14sp：箭头与标题同级同色；适用：行末可点指示，不低于标题层级。
                            fontSize = 14.sp,
                            color = CardTokens.ink,
                        ),
                    )
                }

                // 中部信息列：预览行 + 金点数量条，占据标题右侧剩余宽度。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (data.preview.isNotBlank()) {
                        BasicText(
                            text = data.preview,
                            maxLines = 1,
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                // 10sp：规格预览字号；适用：标题旁次级标注，与 CardTokens.text10 同值。
                                fontSize = 10.sp,
                                color = CardTokens.stoneBlue,
                            ),
                        )
                    }
                    // 金点横排：暖金 2dp 圆点表示典故数量；最多 28 颗，超出以「+N」收束。
                    GoldDotRow(allusionCount = data.allusionCount)
                }
            }
        }
    }
}

/**
 * 金点数量条：横排暖金圆点，一颗对应一则典故。
 *
 * 为什么封顶 28 颗：规格原文「金点横排表示典故数量（如 28 颗）」，横条卡宽度有限，
 * 超过上限继续加点会挤扁圆点；超出部分折叠为「+N」文案，N = 总数 - 28。
 *
 * @param allusionCount 典故数；负数按 0 兜底（异常缓存 / 脏数据边界），不画负颗数。
 */
@Composable
private fun GoldDotRow(allusionCount: Int) {
    // 28 颗为规格给定的圆点渲染上限；适用：金点横排可见颗数封顶值。
    val maxDots = 28
    val safeCount = allusionCount.coerceAtLeast(0)
    val visibleDots = safeCount.coerceAtMost(maxDots)
    val overflow = (safeCount - maxDots).coerceAtLeast(0)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 点间距 2dp 为规格未给的兜底值；适用：圆点横排不贴死，与 2dp 点径同口径保持均匀。
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(visibleDots) {
            Box(
                Modifier
                    // 2dp 圆点：规格金点直径；适用：横排数量指示，细小但暖金可辨。
                    .size(2.dp)
                    .clip(CircleShape)
                    .background(CardTokens.warmGold),
            )
        }
        if (overflow > 0) {
            BasicText(
                text = "+$overflow",
                style = TextStyle(
                    // 10sp：超出计数文案与预览行同级；适用：折叠尾数，不另起字号档。
                    fontSize = 10.sp,
                    color = CardTokens.warmGold,
                ),
            )
        }
    }
}

/**
 * 专题横条卡毛玻璃底层：纸白 70% 半透明 + blur 降级判断。
 *
 * 模糊实现：API 31（S）及以上走 [Modifier.blur] RenderEffect 高斯模糊；API 31 以下无
 * RenderEffect，降级为纯半透明纸白底，不伪造模糊采样（与战役横卡 / InkHalfSheet 同一口径）。
 * 注意：Compose 的 blur 模糊的是图层自身内容，窗口级 backdrop 采样由窗口层统一 mediate，
 * 此处半透明纸白 0.7f 先承担「透出底层」的视觉职责。
 */
private fun Modifier.topicGlassBackdrop(shape: Shape): Modifier {
    val supportsRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return this
        // 纸白 70%：规格「纸白 70%」；0.7f 透明度让底层内容隐约透出，避免实心白块压过纸感。
        .background(CardTokens.cardPaper.copy(alpha = 0.7f), shape)
        .then(
            if (supportsRenderEffect) {
                // 8dp 模糊半径：规格未给本卡半径，沿用战役横卡 backdrop-blur 8px 口径兜底；适用：API31+ 柔化底层。
                Modifier.blur(8.dp)
            } else {
                // 低版本降级：仅保留半透明纸白；适用：API < 31 机型，不崩、不伪造模糊。
                Modifier
            },
        )
}

/**
 * 竖排文字：逐字自上而下堆叠成一列。
 *
 * 为什么不用旋转整体文本：旋转会让汉字侧倒，竖排要求字字正立、从上到下阅读；
 * 逐字 Column 堆叠保留正立字形。适用：仅本卡竖排专题标题，其他横排卡片不要复用。
 *
 * @param text 原始字符串；按字符拆分逐字渲染。
 * @param fontSize 字号；适用：由调用方按规格档位传入。
 * @param color 文字色；适用：取基座语义色，不在此函数内定色。
 * @param fontWeight 字重；适用：标题传 [FontWeight.Bold]。
 */
@Composable
private fun TopicVerticalText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        text.forEach { ch ->
            BasicText(
                text = ch.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    // 宋体衬线：与阅读页东方排印口径一致；适用：专题标题竖排。
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    // 行高 = 字号 × 1.25 兜底；适用：竖排字距不过挤也不过松，规格未给具体行距。
                    lineHeight = fontSize * 1.25f,
                ),
            )
        }
    }
}
