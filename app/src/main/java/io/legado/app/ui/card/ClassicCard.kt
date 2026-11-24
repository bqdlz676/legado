package io.legado.app.ui.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 典籍竖卡数据模型。
 *
 * @param title 书名；适用：竖排主标题，墨色 Bold。
 * @param dynasty 朝代；适用：竖排次级标注，石青。
 * @param wordCount 字数（如「12万字」）；适用：竖排极小注记，云灰。
 */
data class ClassicBook(
    val title: String,
    val dynasty: String,
    val wordCount: String,
)

/**
 * 落文 · 发现页典籍竖卡（切片-25「三、发现页 · 典籍竖卡」逐字落地）。
 *
 * 规格原文：书名竖排 16sp 墨色 Bold；朝代竖排 10sp 石青；字数竖排 8sp 云灰；
 * 右下金点 4dp；宽=屏宽×0.28，高 160dp；悬浮前倾 3°；栅格 3 列间距 8dp。
 *
 * 阴影 / 圆角 / 颗粒 / 五态一律走 [CardSurface]，本组件只补典籍卡专属的竖排信息列、
 * 落款金点与 3° 前倾；色值全部取 [CardTokens]，不在组件内另造色值。
 *
 * @param book 典籍数据；标题 / 朝代 / 字数任一为空时对应竖排列不渲染。
 * @param state 五态之一，透传基座决定阴影档位与骨架态。
 * @param modifier 外部尺寸 / 对齐；宽高已在本组件内按规格固定，外部一般只给对齐。
 */
@Composable
fun ClassicCard(
    book: ClassicBook,
    state: CardState,
    modifier: Modifier = Modifier,
) {
    // 屏幕宽度取当前配置；适用：卡宽 = 屏宽 × 0.28 的分母来源，旋转 / 分屏后随配置重组。
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current

    // 悬浮前倾 3°：仅 Hover 态起倾角，其余四态回正；弹簧阻尼 / 刚度沿用基座 token，与阴影抬升同曲线。
    val tilt by animateFloatAsState(
        targetValue = if (state == CardState.Hover) 3f else 0f,
        animationSpec = spring(
            dampingRatio = CardTokens.cardSpringDamping,
            stiffness = CardTokens.cardSpringStiffness,
        ),
        label = "classic-card-tilt",
    )

    Box(
        modifier = modifier
            // 宽 = 屏宽 × 0.28（规格「宽=屏宽×0.28」）；适用：三列栅格下单卡宽度随屏宽等比收缩。
            .width(screenWidth * 0.28f)
            // 高 160dp（规格「高 160dp」）；适用：典籍竖卡固定高，不随书名 / 字数长短撑高。
            .height(160.dp)
            .graphicsLayer {
                // 前倾绕 X 轴 3°；适用：悬浮时卡片朝读者方向轻倾，强化纸卡浮起感。
                rotationX = tilt
                // 相机距离按密度放大，避免小角度 3D 变换出现近大远小畸变；适用：rotationX 透视校正。
                cameraDistance = density.density * 12f
            },
    ) {
        CardSurface(state = state, modifier = Modifier.fillMaxSize()) {
            // 内边距 8dp 横 / 10dp 纵为规格未给的兜底值；适用：竖排文字不贴 16dp 圆角边缘，留出气口。
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)) {
                // 传统竖排从右向左：Row 内顺序为朝代 → 字数 → 书名，使书名列落在最右侧。
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    // 列内间距 4dp 为规格未给的兜底值；适用：三列竖排文字不贴死，保持列间呼吸。
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (book.dynasty.isNotBlank()) {
                        // 朝代竖排 10sp 石青（规格「朝代竖排 10sp 石青」）；适用：次于书名的年代标注列。
                        VerticalText(
                            text = book.dynasty,
                            fontSize = 10.sp,
                            color = CardTokens.stoneBlue,
                            fontWeight = null,
                        )
                    }
                    if (book.wordCount.isNotBlank()) {
                        // 字数竖排 8sp 云灰（规格「字数竖排 8sp 云灰」）；适用：卡片最低一级注记列。
                        VerticalText(
                            text = book.wordCount,
                            fontSize = 8.sp,
                            color = CardTokens.cloudGray,
                            fontWeight = null,
                        )
                    }
                    if (book.title.isNotBlank()) {
                        // 书名竖排 16sp 墨色 Bold（规格「书名竖排 16sp 墨色 Bold」）；适用：卡片主标题列。
                        VerticalText(
                            text = book.title,
                            fontSize = 16.sp,
                            color = CardTokens.ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // 右下金点 4dp（规格「右下金点 4dp」）；适用：落文落款印记，暖金圆点压卡片右下角。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // 金点距右 / 下边缘 6dp 为兜底留白；适用：圆点不贴圆角边线，留出落款呼吸位。
                        .padding(6.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(CardTokens.warmGold),
                )
            }
        }
    }
}

/**
 * 发现页典籍竖卡栅格：3 列，列间距 8dp（规格「栅格 3 列间距 8dp」）。
 *
 * @param books 典籍列表；空列表时栅格不出内容，由外层空态卡（切片-25 十一）兜底。
 * @param modifier 外部宽高 / 内边距；栅格自身只负责列数与间隔。
 * @param stateFor 按卡片取五态；适用：列表中个别卡片处于骨架 / 禁用态时由调用方判定。
 */
@Composable
fun ClassicGrid(
    books: List<ClassicBook>,
    modifier: Modifier = Modifier,
    stateFor: (ClassicBook) -> CardState = { CardState.Default },
) {
    LazyVerticalGrid(
        modifier = modifier,
        // 固定 3 列（规格「栅格 3 列」）；适用：发现页典籍区等分三列，不随内容流式换行。
        columns = GridCells.Fixed(3),
        // 列间距 8dp（规格「间距 8dp」）；适用：三列卡片之间的横向间隔。
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // 行间距同取 8dp 兜底：规格只指定列间距，纵向沿用同值避免上下留白失衡。
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(books) { book ->
            ClassicCard(book = book, state = stateFor(book))
        }
    }
}

/**
 * 竖排文字：逐字自上而下堆叠成一列。
 *
 * 为什么不用旋转整体文本：旋转会让汉字侧倒，竖排要求字字正立、从上到下阅读；
 * 逐字 Column 堆叠保留正立字形，也便于单列截断。适用：书名 / 朝代 / 字数三列短文本。
 *
 * @param text 原始字符串；按字符拆分逐字渲染。
 * @param fontSize 字号；适用：由调用方按规格档位传入 16 / 10 / 8sp。
 * @param color 文字色；适用：取基座语义色，不在此函数内定色。
 * @param fontWeight 字重；适用：仅书名传 [FontWeight.Bold]，其余列为常规。
 */
@Composable
private fun VerticalText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        text.forEach { ch ->
            BasicText(
                text = ch.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    // 宋体族：典籍竖卡走东方印刷字口径；平台 Serif 兜底，未注入定制字体时不崩。
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    // 行高 = 字号 × 1.25 兜底；适用：竖排字距不过挤也不过松，规格未给具体行距。
                    lineHeight = fontSize * 1.25f,
                ),
            )
        }
    }
}
