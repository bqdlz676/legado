package io.legado.app.ui.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * 落文 · 设置行卡片（切片-25「十二、设置行卡片」逐字落地）。
 *
 * 规格原文：每行 48dp：标题竖排 14sp 墨色 + 当前值 12sp 石青 + 箭头；分组间隔 16dp，
 * 无卡片框只靠云灰细线分隔；开关行右侧 ToggleButton（暖金 on / 云灰 off）替代箭头。
 *
 * 与其余 11 类卡片不同：本类规格明示「无卡片框」，故不复用 CardBase 阴影 / 圆角 / 颗粒
 * 基座，只在本组件内铺一行无框内容；色值 / 字号常量全部取 [CardTokens]。
 */

/** 分组间隔 16dp：规格「分组间隔 16dp」；适用：组内末行与下一组之间的纵向留白，组内行靠细线分隔。 */
private val groupGap: Dp = 16.dp

/**
 * 单条设置行：左侧竖排标题，右侧当前值 + 开关 / 箭头二选一，底部可挂云灰细线。
 *
 * @param title 行内主标题；适用：竖排 14sp 墨色，逐字正立堆叠。
 * @param value 当前值；适用：12sp 石青，位于右侧控件前；为空时不渲染当前值。
 * @param checked 开关状态；非空时右侧只渲染开关并替代箭头，为 null 时走箭头或纯展示行。
 * @param onCheckedChange 开关回调；适用：宿主把开关状态写回设置项，本卡不自持状态。
 * @param showArrow 导航箭头；适用：checked 为 null 且该行点击进入下级页面时置 true。
 * @param onClick 整行点击回调；适用：导航行拉起下级页面；开关行点击交给右侧开关，不重复触发。
 * @param showDivider 组内分隔线；适用：组内除末行外均为 true，组末行置 false 并留 [groupGap] 分组间隔。
 */
@Composable
fun SettingRowCard(
    title: String,
    value: String? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 左右 12dp 边距：沿用卡片栅格同值气口；适用：行内容与屏幕边缘不贴死。
            .padding(horizontal = 12.dp)
            // 分组间隔：仅组末行（无分隔线）在底部留 16dp；适用：与下一组拉开距离，组内不额外加空行。
            .then(if (!showDivider) Modifier.padding(bottom = groupGap) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 48dp 行高：规格「每行 48dp」；适用：设置列表单行节奏，竖排标题 / 当前值 / 控件垂直居中。
                .height(48.dp)
                // 整行可点：仅导航行（onClick 非空）挂点击；开关行点击交给右侧 Toggle，避免双重触发。
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 标题竖排：规格「标题竖排 14sp 墨色」；适用：行内主标题，逐字正立从上到下阅读。
            VerticalText(
                text = title,
                // 14sp：规格标题字号；取 CardTokens.text14 唯一来源。
                fontSize = CardTokens.text14,
                // 墨色：规格标题色；适用：主文字最高对比层级。
                color = CardTokens.ink,
                fontWeight = null,
            )

            // 中间弹性留白：把当前值与控件推到行右侧；适用：标题居左、信息居右的经典设置行结构。
            Spacer(Modifier.weight(1f))

            if (!value.isNullOrBlank()) {
                BasicText(
                    text = value,
                    style = TextStyle(
                        // 12sp：规格当前值字号；取 CardTokens.text12 唯一来源。
                        fontSize = CardTokens.text12,
                        // 石青：规格当前值色；适用：次级标注层级，弱于墨色标题。
                        color = CardTokens.stoneBlue,
                    ),
                )
                // 当前值与控件间隔 12dp：取 CardTokens.cardGap 同值气口；适用：文字与开关 / 箭头不贴死。
                Spacer(Modifier.width(CardTokens.cardGap))
            }

            // 右侧区：开关优先（规格「开关行替代箭头」）；checked 为 null 时才按 showArrow 画箭头。
            when {
                checked != null -> SettingToggle(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )

                showArrow -> SettingChevron()
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 1dp 细线：规格「云灰细线分隔」；适用：组内行与行之间的弱分隔，不构成卡片边框。
                    .height(1.dp)
                    // 云灰 0.08 透明度：规格「0.08 透明度」；适用：分隔线极淡，仅近看可辨。
                    // F-73 待规格化：分隔线透明度口径无规格出处，0.08f 为写码自拟（审计 表三#2，待重做后回填规格出处）
                    .background(CardTokens.cloudGray.copy(alpha = 0.08f)),
            )
        }
    }
}

/**
 * 自绘开关：圆角胶囊轨道 + 圆形拇指滑块；开启暖金、关闭云灰，滑块位置 200ms 平滑过渡。
 * 不引 Material Switch：本包外围 UI 全部自绘，色值只取 CardTokens。
 */
@Composable
private fun SettingToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    // F-73 待规格化：开关轨道 40×22dp / 拇指 16dp 尺寸无规格，为写码自拟兜底值（审计 表三#2，待重做后回填规格出处）
    // 轨道宽 40dp：规格未给具体尺寸，兜底为常见胶囊比例；适用：右侧开关触控块横向占位。
    val trackWidth = 40.dp
    // 轨道高 22dp：与宽度配成胶囊形；适用：轨道纵向高度，圆角取高度一半。
    val trackHeight = 22.dp
    // 拇指 16dp：略小于轨道高度，上下各留 3dp 内边距；适用：开关滑块在轨道内不贴边。
    val knobSize = 16.dp
    // 滑块行程 = 轨道宽 - 拇指宽 - 左右各 3dp 内边距；适用：拇指从左端平移到右端的可用距离。
    val travel = trackWidth - knobSize - 6.dp

    // 0→1 进度插值；200ms 过渡：规格要求开关状态 200ms 动画；适用：轨道变色与滑块位置同步切换。
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "settingToggleProgress",
    )

    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            // 轨道色：开启暖金、关闭云灰；规格「暖金 on / 云灰 off」，色值取 CardTokens。
            .background(if (checked) CardTokens.warmGold else CardTokens.cloudGray)
            // 开关可点：仅回调非空时挂点击；适用：只读展示态开关不响应触控。
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                // 左端内边距 3dp：与上下留白同值；适用：未选态拇指贴左但不压轨道边缘。
                .padding(start = 3.dp)
                // 滑块横向位移：按进度在行程内平移；适用：checked 贴右、未选贴左，过渡连续不跳变。
                .offset(x = travel * progress)
                .size(knobSize)
                .clip(CircleShape)
                // 拇指纸白色：与暖金 / 云灰轨道均形成对比；适用：滑块轮廓清晰可辨。
                .background(CardTokens.cardPaper),
        )
    }
}

/**
 * 自绘右向 chevron：Canvas 两段折线构成「>」，云灰描边；不引 material-icons 依赖。
 */
@Composable
private fun SettingChevron() {
    // F-73 待规格化：箭头 24dp 尺寸与描边 2dp 线宽无规格，为写码自拟兜底值（审计 表三#2，待重做后回填规格出处）
    // 箭头块 24dp：规格未给箭头尺寸，兜底与常见行内图标同尺寸；适用：右侧导航提示的触控 / 视觉占位。
    Canvas(modifier = Modifier.size(24.dp)) {
        // 描边宽 2dp：与图标线宽口径一致；适用：折线不至于过细在小屏发虚。
        val stroke = 2.dp.toPx()
        val path = Path().apply {
            // 折线段：左上 → 中右 → 左下，构成「>」；适用：右行进入下级页面的方向暗示。
            moveTo(size.width * 0.30f, size.height * 0.28f)
            lineTo(size.width * 0.62f, size.height * 0.50f)
            lineTo(size.width * 0.30f, size.height * 0.72f)
        }
        drawPath(
            path = path,
            color = CardTokens.cloudGray,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * 竖排文字：逐字自上而下堆叠成一列（写法口径同 ClassicCard 内私有 VerticalText，
 * 本文件内自写一份，不跨文件引用私有实现，也不改动 ClassicCard）。
 *
 * 为什么不用旋转整体文本：旋转会让汉字侧倒，竖排要求字字正立、从上到下阅读；
 * 逐字 Column 堆叠保留正立字形。适用：标题等短文本列。
 *
 * @param text 原始字符串；按字符拆分逐字渲染。
 * @param fontSize 字号；适用：由调用方按规格档位传入。
 * @param color 文字色；适用：取基座语义色，不在此函数内定色。
 * @param fontWeight 字重；适用：需要加粗的标题传入 [FontWeight.Bold]，常规传 null。
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
                    // 宋体族：东方印刷字口径；平台 Serif 兜底，未注入定制字体时不崩。
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    // 行高 = 字号 × 1.25 兜底；适用：竖排字距不过挤也不过松，规格未给具体行距。
                    // F-73 待规格化：竖排行距 ×1.25 与当前值/控件间距无规格，为写码自拟兜底值（审计 表三#2，待重做后回填规格出处）
                    lineHeight = fontSize * 1.25f,
                ),
            )
        }
    }
}
