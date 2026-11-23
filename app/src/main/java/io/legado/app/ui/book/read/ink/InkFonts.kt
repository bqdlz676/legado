package io.legado.app.ui.book.read.ink

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * 阅读页字体注入点（规格切片-09 §3.1：正文 Noto Serif SC 思源宋体；标题加粗宋体；注音小字无衬线）。
 *
 * 字体策略（两级兜底，不降级、不出空白态）：
 *  1. 内置子集：U9 字体模块收口后，把 SIL OFL 许可的 Noto Serif SC 常用字子集放到
 *     `assets/fonts/` 下，[ensureSerifLoaded] 启动时从资产加载并注入；
 *  2. 系统 serif：内置子集缺失时回退系统衬线体（荣耀 WIN / HarmonyOS 的 serif 即
 *     Noto Serif CJK 宋体类），不硬编码字体名、不在本层猜测字体文件路径。
 *
 * 当前状态：仓库未打包宋体子集（离线构建无法拉取 Noto Serif SC），**当前走系统 serif，
 * 子集待 U9 字体系统收口**；资产文件就位后无需改本层代码，命中 [SERIF_ASSET_NAMES] 即注入。
 * 禁止引入 Wikipedia / OSM / CC-CEDICT 等非嵌入许可字体资产。
 *
 * 状态为进程级单例：注入一次后竖排 View 测量、正文绘制、气泡文案三处共用同一宋体。
 */
object InkFonts {

    /** 内置宋体子集所在资产目录（U9 收口落点）。 */
    private const val FONT_ASSET_DIR = "fonts"

    /** 可识别的内置宋体子集文件名（Regular 字重；大小写不敏感）。 */
    private val SERIF_ASSET_NAMES = listOf(
        "NotoSerifSC-Regular.otf",
        "NotoSerifSC-Regular.ttf",
        "SourceHanSerifSC-Regular.otf",
        "SourceHanSerifSC-Regular.ttf",
    )

    /** 竖排 View / Paint 侧宋体实例；null = 未注入，回退系统 serif。 */
    @Volatile
    var serifTypeface: Typeface? = null
        private set

    /** Compose 侧宋体族；null = 未注入，回退 [FontFamily.Serif]。 */
    @Volatile
    var serifFamily: FontFamily? = null
        private set

    /** 字体是否已尝试加载过（资产缺失也只探一次，避免每帧扫资产目录）。 */
    @Volatile
    private var loadAttempted = false

    /**
     * 注入思源宋体。字体系统模块在字体子集加载成功后回调本方法；
     * 传入 null 表示卸载 / 回退，阅读层自动落回系统 serif。
     */
    fun injectSerif(typeface: Typeface?, family: FontFamily? = null) {
        serifTypeface = typeface
        serifFamily = family
    }

    /**
     * 字体初始化：宿主 Activity 在 `onCreate` 早期调用一次。
     *
     * 先探 `assets/fonts/` 是否有内置宋体子集，有则加载注入；无资产或加载失败时
     * 保持空状态，取用侧自动回退系统 serif。全程 runCatching，字体异常不阻断阅读页。
     */
    fun ensureSerifLoaded(context: Context) {
        if (loadAttempted) return
        loadAttempted = true
        runCatching {
            val assetName = context.assets.list(FONT_ASSET_DIR)?.firstOrNull { candidate ->
                SERIF_ASSET_NAMES.any { it.equals(candidate, ignoreCase = true) }
            } ?: return@runCatching
            val typeface = Typeface.createFromAsset(context.assets, "$FONT_ASSET_DIR/$assetName")
            val family = FontFamily(Font(typeface = typeface, weight = FontWeight.Normal))
            injectSerif(typeface, family)
        }
        // 兜底：未注入时 [viewTypeface] / [composeFamily] 落系统 serif，不抛异常。
    }

    /** View 侧取用：未注入时系统 serif 兜底。 */
    val viewTypeface: Typeface get() = serifTypeface ?: Typeface.SERIF

    /** View 侧加粗宋体：内置子集按 BOLD 派生；无内置时系统 serif 粗体合成，仍缺粗重时由 Paint fakeBold 兜底。 */
    val viewTypefaceBold: Typeface
        get() = serifTypeface?.let { Typeface.create(it, Typeface.BOLD, false) }
            ?: Typeface.create(Typeface.SERIF, Typeface.BOLD, false)

    /** Compose 侧取用：未注入时系统 serif 兜底。 */
    val composeFamily: FontFamily get() = serifFamily ?: FontFamily.Serif
}
