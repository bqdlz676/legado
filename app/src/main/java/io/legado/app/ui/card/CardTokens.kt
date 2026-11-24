package io.legado.app.ui.card

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 卡片基座 Token 唯一来源（切片-25「通用卡片 Token」表逐字落地）。
 *
 * 为什么收口到本对象：验收判据 2 要求纸白 / 三档阴影 / 圆角 16 / 间距 12 / 颗粒 0.03
 * 各只定义一处，具体卡片组件内不允许再硬编码色值；因此包内所有卡片只能读本对象，
 * 不得另造色值、圆角或间距。语义色与阅读页 [io.legado.app.ui.components.ComponentPalette]
 * 同口径（墨色 / 石青 / 云灰 / 暖金 / 朱砂），卡片侧在此独立声明是为了让基座包自洽、
 * 不反向依赖组件包。
 *
 * 适用条件：仅服务 [io.legado.app.ui.card.CardSurface] 及其下属 12 类具体卡片；
 * 非卡片场景（按钮 / 气泡 / Sheet）仍取各自包内 token，不要混用。
 */
object CardTokens {

    // ─────────────────────────── 底面与语义色 ───────────────────────────

    /** 卡片纸白底 #F7F1E6；适用：Default / Hover / Pressed / Disabled 四态纯色底面，Loading 态改用骨架灰。 */
    val cardPaper: Color = Color(0xFFF7F1E6)

    /** 墨色 #1A1410；适用：书名 / 标题 / 条目名称等主文字，与阅读页 TEXT_PRIMARY 同口径。 */
    val ink: Color = Color(0xFF1A1410)

    /** 石青 #3A6B66；适用：作者 / 朝代 / 副标题 / 出处斜体等次级标注文字。 */
    val stoneBlue: Color = Color(0xFF3A6B66)

    /** 云灰 #9B9687；适用：字数 / 一句话描述 / 占位辅助文字与未读进度段。 */
    val cloudGray: Color = Color(0xFF9B9687)

    /** 暖金 #D4A54A；适用：金点 / 强调标签 / 骨架屏斜光带，悬浮辉光同色系。 */
    val warmGold: Color = Color(0xFFD4A54A)

    /** 朱砂 #C23B2E；适用：点击暗影 / 已读进度 / 匹配高亮，只在交互与强调处出现，不铺大面积。 */
    val cinnabar: Color = Color(0xFFC23B2E)

    // ─────────────────────────── 三档阴影（切片-25 通用卡片 Token） ───────────────────────────

    /** 默认阴影色 rgba(212,165,74,0.3) 暖金微光；适用：Default 态常态投影，把卡片从纸面托起。 */
    val shadowDefault: Color = Color(0x4DD4A54A)

    /** 默认阴影扩散 12dp（规格 `0 0 12px`）；适用：Default 态 elevation。 */
    val shadowDefaultElevation: Dp = 12.dp

    /** 悬浮阴影色 rgba(212,165,74,0.45) 暖金加深；适用：Hover 态投影，明度加深提示可交互。 */
    val shadowHover: Color = Color(0x73D4A54A)

    /** 悬浮阴影扩散 20dp（规格 `0 0 20px`）；适用：Hover 态 elevation，扩散范围大于默认档。 */
    val shadowHoverElevation: Dp = 20.dp

    /** 悬浮轻抬 2dp；适用：Hover 态卡片沿 Y 轴上移，与阴影加深共同表达「浮起」。 */
    val hoverLift: Dp = 2.dp

    /** 点击阴影色 rgba(194,59,46,0.3) 朱砂暗影；适用：Pressed 态投影，朱砂反馈按下而非悬停。 */
    val shadowPressed: Color = Color(0x4DC23B2E)

    /** 点击阴影扩散 8dp（规格 `0 0 8px`）；适用：Pressed 态 elevation，范围收窄表达下压贴近纸面。 */
    val shadowPressedElevation: Dp = 8.dp

    // ─────────────────────────── 圆角 / 边框 / 间距 / 颗粒 ───────────────────────────

    /** 卡片圆角 16dp；适用：全部卡片外形轮廓，具体变体不得自行改圆角（分享卡等特例在规格另行注明）。 */
    val cardRadius: Dp = 16.dp

    /**
     * 边框：无。规格明确「卡片靠阴影拉开层次，不靠边线」，故基座不提供 stroke / border 修饰符；
     * 仅战役横卡、马克思卡、党史卡等原文指定 1px 边框的变体，由具体卡片自行在内容层追加。
     */
    val cardBorderWidth: Dp = 0.dp

    /** 卡片之间间距 12dp；适用：栅格 / 列表内卡片与卡片的横向、纵向间隔。 */
    val cardGap: Dp = 12.dp

    /** 宣纸颗粒网格粒度 2dp（规格 2px noise texture）；适用：drawBehind 逐格撒点的步长。 */
    val noiseGrid: Dp = 2.dp

    /** 宣纸颗粒不透明度 0.03；适用：噪点叠层峰值透明度，极淡只可近看，不抢正文。 */
    const val noiseAlpha: Float = 0.03f

    /** 颗粒撒点概率（规格未给，基座兜底）：每格按此概率落一粒，避免满屏椒盐；适用：noise 叠层。 */
    const val noiseProbability: Float = 0.22f

    /** 颗粒随机种子：固定种子保证跨组合、跨帧稳定，不随滚动重新掷点闪烁；适用：noise 叠层。 */
    const val noiseSeed: Long = 4102517L

    // ─────────────────────────── 骨架屏（Loading 态） ───────────────────────────

    /** 骨架灰块底色：云灰 15% 透在纸白上；适用：Loading 态同尺寸占位块，不铺实灰破坏纸感。 */
    val skeletonShade: Color = Color(0x269B9687)

    /** 骨架斜光带暖金峰值透明度 18%；适用：扫过灰块的斜向光带，亮度高于底色但不刺眼。 */
    const val skeletonBandAlpha: Float = 0.18f

    /** 斜光带循环周期 1500ms（规格 1.5s 扫过）；适用：infiniteRepeatable 的 tween 时长，禁止 delay 硬切。 */
    const val skeletonBandPeriodMs: Int = 1500

    // ─────────────────────────── 禁用态 / 弹簧 ───────────────────────────

    /** 禁用态整体透明度 30%；适用：Disabled 态整卡压淡，表达不可点；与组件库禁用口径一致。 */
    const val disabledAlpha: Float = 0.30f

    /** 基座状态弹簧阻尼比 0.80；适用：阴影 / 抬升在五态间切换的 spring，略欠阻尼，到位留一丝回弹。 */
    const val cardSpringDamping: Float = 0.80f

    /** 基座状态弹簧刚度 360f；适用：与阻尼比配套，整段切换落在 250ms 量级，不拖泥带水。 */
    const val cardSpringStiffness: Float = 360f

    // ─────────────────────────── 字号阶梯（切片-25 出现的全部 sp） ───────────────────────────

    /** 22sp：书架卡书名竖排主标题（墨色 Bold）；适用：整卡最高一级标题。 */
    val text22: TextUnit = 22.sp

    /** 18sp：党史 / 人物卡竖排标题、分享卡文段、空态主文案；适用：次级大标题与分享正文。 */
    val text18: TextUnit = 18.sp

    /** 16sp：战役横卡标题、典籍竖卡书名、名物类别名；适用：横排 / 竖排一级卡片标题。 */
    val text16: TextUnit = 16.sp

    /** 14sp：专题标题、搜索书名、名物条目名称、关系卡本名、设置行标题；适用：行内主标题。 */
    val text14: TextUnit = 14.sp

    /** 12sp：战役副标题、条目数、空态引导语、设置行当前值；适用：副标题与说明级文字。 */
    val text12: TextUnit = 12.sp

    /** 11sp：书架卡作者、一句话描述、名物条目描述；适用：作者与短描述行。 */
    val text11: TextUnit = 11.sp

    /** 10sp：字数、朝代、预览、年代国别、关系、时间标签；适用：辅助标注行。 */
    val text10: TextUnit = 10.sp

    /** 9sp：出处、相关引文斜体；适用：最低一级注释性文字。 */
    val text9: TextUnit = 9.sp

    /** 8sp：搜索来源标签、典籍卡字数；适用：标签与极小注记，低于此字号不在卡片内使用。 */
    val text8: TextUnit = 8.sp
}
