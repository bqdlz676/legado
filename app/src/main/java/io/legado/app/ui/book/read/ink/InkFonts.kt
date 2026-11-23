package io.legado.app.ui.book.read.ink

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.legado.app.ui.font.FontLoadResult
import io.legado.app.ui.font.FontRegistry
import io.legado.app.ui.font.FontRole
import io.legado.app.ui.font.InkFontLoader

/**
 * 阅读页字体注入点（切片-09 §3.1 / 切片-14 字体系统）。
 *
 * 本类是阅读层（ink 域）对 U9 字体系统 [io.legado.app.ui.font] 的薄门面：
 *  - 候选文件名、体积红线、许可收口、缓存编排全部在 [FontRole] / [FontRegistry] /
 *    [InkFontLoader] 内；本层不写任何硬编码字体文件名，也不再自行扫 assets 目录；
 *  - 五个角色：正文 [FontRole.BODY_SERIF]、标题 [FontRole.TITLE_SERIF]、
 *    注音小字 [FontRole.LABEL_SANS]、数字年份 [FontRole.NUMBER_TABULAR]、
 *    等宽编号 [FontRole.MONO]；
 *  - 两级兜底：内置子集命中才注入句柄；缺失 / 超体积 / 损坏 / 未初始化一律回落系统字体。
 *    全程 runCatching，不抛异常、不阻塞阅读页、不出空白态。
 *
 * 既有公开属性语义保持不变：句柄为 null 表示未注入，取用侧回落系统对应字体。
 * 禁止引入 Wikipedia / OSM / CC-CEDICT 等非嵌入许可字体资产。
 */
object InkFonts {

    // ── 正文（既有公开槽位） ──────────────────────────────────────────────

    /** 竖排 View / Paint 侧宋体实例；null = 未注入，回退系统 serif。 */
    @Volatile
    var serifTypeface: Typeface? = null
        private set

    /** Compose 侧宋体族；null = 未注入，回退 [FontFamily.Serif]。 */
    @Volatile
    var serifFamily: FontFamily? = null
        private set

    // ── 标题 SemiBold ────────────────────────────────────────────────────
    @Volatile
    private var titleTypeface: Typeface? = null

    @Volatile
    private var titleFamily: FontFamily? = null

    // ── 注音 / 批注小字 sans ─────────────────────────────────────────────
    @Volatile
    private var sansTypeface: Typeface? = null

    @Volatile
    private var sansFamily: FontFamily? = null

    // ── 数字 / 年份等宽数字 ──────────────────────────────────────────────
    @Volatile
    private var numberTypeface: Typeface? = null

    @Volatile
    private var numberFamily: FontFamily? = null

    // ── 典籍编号 / CBDB ID 等宽 ──────────────────────────────────────────
    @Volatile
    private var monoTypeface: Typeface? = null

    @Volatile
    private var monoFamily: FontFamily? = null

    /** 首屏正文是否已尝试解析（缺失也只探一次，避免每帧扫资产目录）。 */
    @Volatile
    private var loadAttempted = false

    /** 宿主上下文（applicationContext）；Compose 族按资产路径构造时取用 AssetManager。 */
    @Volatile
    private var appContext: Context? = null

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
     * 走 U9 注册表编排：候选匹配、体积红线、缓存与兜底全在 [FontRegistry] /
     * [InkFontLoader] 内完成，本层不再探测资产目录。首屏两角色（正文 swap、标题阻塞）
     * 在此解析；注音 / 数字 / 等宽按策略首次取用再解析。全程 runCatching，字体异常不阻断阅读页。
     */
    fun ensureSerifLoaded(context: Context) {
        if (loadAttempted) return
        loadAttempted = true
        runCatching {
            val ctx = context.applicationContext
            appContext = ctx
            InkFontLoader.init(ctx)
            resolve(FontRole.BODY_SERIF)
            resolve(FontRole.TITLE_SERIF)
        }
    }

    /**
     * 解析指定角色并回填内置槽位；仅内置子集命中才写槽，兜底句柄不入槽
     * （槽位语义 = 内置注入，null 时取用侧回落系统字体）。
     *
     * @return 内置子集是否命中。
     */
    private fun resolve(role: FontRole): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            InkFontLoader.init(ctx)
            // typefaceFor 内部走 FontRegistry.resolve：候选匹配 → 体积红线 → 句柄构造；
            // 缺失 / 超体积 / 损坏均在注册表内落兜底，不向外抛。
            InkFontLoader.typefaceFor(role)
            val loaded = FontRegistry.cachedResult(role) as? FontLoadResult.Loaded
                ?: return@runCatching false
            val typeface = loaded.handle as? Typeface ?: return@runCatching false
            val family = buildFamily(loaded.assetName, ctx, weightOf(role))
            cache(role, typeface, family)
            true
        }.getOrDefault(false)
    }

    private fun cache(role: FontRole, typeface: Typeface, family: FontFamily) {
        when (role) {
            FontRole.BODY_SERIF -> {
                serifTypeface = typeface
                serifFamily = family
            }
            FontRole.TITLE_SERIF -> {
                titleTypeface = typeface
                titleFamily = family
            }
            FontRole.LABEL_SANS -> {
                sansTypeface = typeface
                sansFamily = family
            }
            FontRole.NUMBER_TABULAR -> {
                numberTypeface = typeface
                numberFamily = family
            }
            FontRole.MONO -> {
                monoTypeface = typeface
                monoFamily = family
            }
        }
    }

    private fun buildFamily(assetName: String, ctx: Context, weight: FontWeight): FontFamily {
        val assetPath = "${FontRole.ASSET_DIR}/$assetName"
        return FontFamily(Font(assetPath, ctx.assets, weight))
    }

    private fun weightOf(role: FontRole): FontWeight = when (role) {
        FontRole.TITLE_SERIF -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }

    // ── View 侧取用 ──────────────────────────────────────────────────────

    /** View 侧正文宋体；未注入时系统 serif 兜底。 */
    val viewTypeface: Typeface
        get() = serifTypeface ?: run { resolve(FontRole.BODY_SERIF); serifTypeface }
            ?: Typeface.SERIF

    /** View 侧标题宋体 SemiBold；内置标题子集缺失时回落系统 serif 加粗。 */
    val viewTypefaceBold: Typeface
        get() = titleTypeface ?: run { resolve(FontRole.TITLE_SERIF); titleTypeface }
            ?: Typeface.create(Typeface.SERIF, Typeface.BOLD, false)

    /** View 侧注音 / 批注小字无衬线；内置 sans 子集缺失时回落系统 sans。 */
    val viewSansTypeface: Typeface
        get() = sansTypeface ?: run { resolve(FontRole.LABEL_SANS); sansTypeface }
            ?: Typeface.SANS_SERIF

    /** View 侧数字 / 年份等宽数字；缺失回落系统 sans。 */
    val viewNumberTypeface: Typeface
        get() = numberTypeface ?: run { resolve(FontRole.NUMBER_TABULAR); numberTypeface }
            ?: Typeface.SANS_SERIF

    /** View 侧典籍编号 / CBDB ID 等宽；缺失回落系统 monospace。 */
    val viewMonoTypeface: Typeface
        get() = monoTypeface ?: run { resolve(FontRole.MONO); monoTypeface }
            ?: Typeface.MONOSPACE

    // ── Compose 侧取用 ───────────────────────────────────────────────────

    /** Compose 侧正文宋体族；未注入时系统 serif 兜底。 */
    val composeFamily: FontFamily
        get() = serifFamily ?: run { resolve(FontRole.BODY_SERIF); serifFamily }
            ?: FontFamily.Serif

    /** Compose 侧注音小字无衬线族；子集缺失回落系统 sans。 */
    val composeSansFamily: FontFamily
        get() = sansFamily ?: run { resolve(FontRole.LABEL_SANS); sansFamily }
            ?: FontFamily.SansSerif

    /** Compose 侧标题 SemiBold 族；缺失回落系统 serif。 */
    val composeTitleFamily: FontFamily
        get() = titleFamily ?: run { resolve(FontRole.TITLE_SERIF); titleFamily }
            ?: FontFamily.Serif

    /** Compose 侧数字 / 年份族；缺失回落系统 sans。 */
    val composeNumberFamily: FontFamily
        get() = numberFamily ?: run { resolve(FontRole.NUMBER_TABULAR); numberFamily }
            ?: FontFamily.SansSerif

    /** Compose 侧编号 / ID 等宽族；缺失回落系统 monospace。 */
    val composeMonoFamily: FontFamily
        get() = monoFamily ?: run { resolve(FontRole.MONO); monoFamily }
            ?: FontFamily.Monospace
}
