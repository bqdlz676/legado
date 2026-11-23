package io.legado.app.ui.font

import android.content.Context
import android.graphics.Typeface
import io.legado.app.constant.AppLog

/**
 * U9 字体系统 Android 收口（切片-14）。
 *
 * 职责：
 *  - 启动早期 [init] 预载正文 / 标题两个首屏角色（[FontLoadStrategy.PRELOAD_SWAP] /
 *  [FontLoadStrategy.BLOCKING_TITLE]）；注音小字延迟、数字 / 等宽按需，首次取用才解析；
 *  - 内置子集命中 → 注入资产 Typeface；缺失 / 超体积 / 损坏 → 回落系统字体，不崩、不出空白态；
 *  - 每个角色只探测一次，结果缓存在 [FontRegistry]。
 *
 * 不做的事：不硬编码平台字体名；不在本层下载字体；不引入 Material 组件；颜色走语义 token。
 *
 * 与阅读页既有注入点 `InkFonts`（其他模块域）的关系：本类产出的 Typeface 可由阅读层
 * 回调注入；本类不反向修改阅读层代码。
 */
object InkFontLoader {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var source: FontAssetSource? = null

    private val handleLoader = FontHandleLoader { _, assetPath, _ ->
        val assets = appContext?.assets ?: error("InkFontLoader not initialized")
        // Typeface 只能从资产路径 / fd 构造，字节流仅用于体积校验。
        Typeface.createFromAsset(assets, assetPath)
    }

    /**
     * 初始化宿主上下文，并预载首屏角色。应在宿主 Activity `onCreate` 早期调用一次；
     * 重复调用幂等。全程异常兜底，字体问题不阻断阅读页启动。
     */
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        source = AndroidFontSource(ctx)
        // 首屏两角色：正文 swap 预载、标题阻塞预载；探测失败已在注册表内落兜底，不抛。
        FontRole.entries.filter {
            it.strategy == FontLoadStrategy.PRELOAD_SWAP ||
                it.strategy == FontLoadStrategy.BLOCKING_TITLE
        }.forEach { preload(it) }
    }

    /** 取指定角色 Typeface：命中内置子集用子集，否则回落系统兜底族。 */
    fun typefaceFor(role: FontRole): Typeface {
        val src = source ?: return fallbackTypeface(role)
        val result = FontRegistry.resolve(role, src, handleLoader)
        return when (result) {
            is FontLoadResult.Loaded -> result.handle as? Typeface ?: fallbackTypeface(role)
            is FontLoadResult.MissingAsset -> fallbackTypeface(role)
            is FontLoadResult.RejectedOversize -> {
                AppLog.putDebug(
                    "字体子集超出体积红线被拒：${role.name} ${result.assetName} " +
                        "${result.bytes}B > ${result.budgetBytes}B，回落系统字体",
                )
                fallbackTypeface(role)
            }
            is FontLoadResult.LoadFailed -> {
                AppLog.putDebug(
                    "字体子集加载失败：${role.name} ${result.assetName}，回落系统字体",
                    result.cause,
                )
                fallbackTypeface(role)
            }
        }
    }

    /** 正文宋体（预载；缺失回落系统 serif）。 */
    val bodySerif: Typeface get() = typefaceFor(FontRole.BODY_SERIF)

    /** 标题宋体 SemiBold（预载；缺失回落系统 serif 加粗）。 */
    val titleSerif: Typeface get() = typefaceFor(FontRole.TITLE_SERIF)

    /** 注音小字无衬线（延迟加载；缺失回落系统 sans）。 */
    val labelSans: Typeface get() = typefaceFor(FontRole.LABEL_SANS)

    /** 数字 / 年份等宽数字（按需；缺失回落系统 sans）。 */
    val numberTabular: Typeface get() = typefaceFor(FontRole.NUMBER_TABULAR)

    /** 编号 / ID 等宽（按需；缺失回落系统 monospace）。 */
    val mono: Typeface get() = typefaceFor(FontRole.MONO)

    /** 某角色内置子集是否已成功注入（仅供状态展示 / 测试断言）。 */
    fun isLoaded(role: FontRole): Boolean =
        FontRegistry.cachedResult(role) is FontLoadResult.Loaded

    private fun preload(role: FontRole) {
        runCatching { typefaceFor(role) }
    }

    private fun fallbackTypeface(role: FontRole): Typeface = when (role.fallback) {
        FontFallbackKind.SERIF -> Typeface.SERIF
        FontFallbackKind.SERIF_BOLD -> Typeface.create(Typeface.SERIF, Typeface.BOLD, false)
        FontFallbackKind.SANS -> Typeface.SANS_SERIF
        FontFallbackKind.SANS_TABULAR -> Typeface.SANS_SERIF
        FontFallbackKind.MONO -> Typeface.MONOSPACE
    }
}
