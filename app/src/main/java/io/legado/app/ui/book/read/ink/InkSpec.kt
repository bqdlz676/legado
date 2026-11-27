package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.vertical.VerticalTokens

/**
 * 阅读页规格档位与帧预算守卫（切片-08 性能红线 / 切片-03 低端机约束）。
 *
 * 职责：
 *  - 排版常量（版心 / 字号 / 行距 / 列距）：对齐切片-09 §3.1，数值与 V1 分页引擎
 *    [VerticalTokens] 同源委托，UI 层只从本对象取，避免同一行距两套数；
 *  - [isLowSpec]：低端机判定（核数 / 堆内存启发式，可被测试或外部显式覆盖）；
 *  - [particleCount]：常规 / 低端两档氛围粒子数；
 *  - [recordFrame] / [shouldDegrade]：单帧绘制超预算计数，连续超阈值即降级。
 *
 * 不变式（归位口径，B4-05）：
 *  - 本对象不触碰 Android 视图类与资源表（零资源 id 引用，string/drawable/color/raw/mipmap 各类均不引），
 *    判定与计数全部为纯函数，可离线 JVM 单测；
 *  - 排版常量只转发冻结 token，不在此另立数值；
 *  - 所有入参边界（核数 / 堆内存 / 帧数 / 字号非正或为负）都回落安全默认，不抛异常、不产生 NaN；
 *  - [overrideLowSpec] 为唯一外部显式覆盖位；未覆盖时才走一次性启发式缓存。
 *
 * 注意：[detectLowSpec] 取的是 JVM 堆上限与 CPU 核数，不是整机 RAM；
 * 整机内存档位待系统模块收口后在此替换判定口径。
 */
object InkSpec {

    // ── 切片-09 §3.1 排版常量（委托 V1 冻结 token，不在此另立数值） ──

    // 注：VerticalTokens 已改为转发单源的 val（非编译期常量），此处同步由 const 降级为 val，
    // 数值口径不变；调用方只读引用，无编译期常量诉求。

    /** 正文默认字号 sp（转发自 [VerticalTokens.BODY_SP]）。 */
    val BODY_SP: Float = VerticalTokens.BODY_SP

    /** 注音 / 批注小字字号 sp，无衬线（转发自 [VerticalTokens.ANNOTATION_SP]）。 */
    val ANNOTATION_SP: Float = VerticalTokens.ANNOTATION_SP

    /** 行距系数（转发自 [VerticalTokens.LINE_HEIGHT_RATIO]）。 */
    val LINE_HEIGHT_RATIO: Float = VerticalTokens.LINE_HEIGHT_RATIO

    /** 列间距系数（转发自 [VerticalTokens.COLUMN_GAP_RATIO]）。 */
    val COLUMN_GAP_RATIO: Float = VerticalTokens.COLUMN_GAP_RATIO

    /** 正文版心宽度占屏宽比例（转发自 [VerticalTokens.TEXT_BLOCK_RATIO]）。 */
    val TEXT_BLOCK_RATIO: Float = VerticalTokens.TEXT_BLOCK_RATIO

    /** 左侧注音 / 批注栏占屏宽比例（转发自 [VerticalTokens.NOTE_BAND_RATIO]）。 */
    val NOTE_BAND_RATIO: Float = VerticalTokens.NOTE_BAND_RATIO

    /** 标题比正文大的字号增量 sp（转发自 [VerticalTokens.TITLE_SIZE_OFFSET_SP]）。 */
    val TITLE_SIZE_OFFSET_SP: Float = VerticalTokens.TITLE_SIZE_OFFSET_SP

    /** 标题列与正文列横向间距 dp（转发自 [VerticalTokens.TITLE_GAP_DP]）。 */
    val TITLE_GAP_DP: Float = VerticalTokens.TITLE_GAP_DP

    /** 测试 / 外部显式覆盖档位；null 走 [detectLowSpec] 启发式。 */
    var overrideLowSpec: Boolean? = null

    /** 启发式结果缓存一次即可：进程内档位不变。 */
    private val detectedLowSpec: Boolean by lazy { detectLowSpec() }

    /** 当前是否低端档位。 */
    fun isLowSpec(): Boolean = overrideLowSpec ?: detectedLowSpec

    /** 氛围粒子数：低端减半；[full] 常规颗数，[low] 低端颗数；负值 / 缺额回落 0，不抛异常。 */
    fun particleCount(full: Int, low: Int, lowSpec: Boolean): Int =
        if (lowSpec) low.coerceAtLeast(0) else full.coerceAtLeast(0)

    /**
     * 记录一帧绘制耗时，返回新的连续超预算计数；回到预算内归零。
     * 纯函数：不读全局时钟，耗时由调用侧采样传入，便于单测构造边界帧；
     * 负 streak 先夹到 0，杜绝计数回绕成负。
     */
    fun recordFrame(streak: Int, elapsedMs: Double, budgetMs: Double): Int =
        if (elapsedMs > budgetMs) streak.coerceAtLeast(0) + 1 else 0

    /** 连续超预算帧数达到阈值即应降级（粒子减半、跳过噪点叠层等）；阈值夹到至少 1，杜绝 0 / 负阈值恒降级。 */
    fun shouldDegrade(streak: Int, threshold: Int): Boolean =
        streak.coerceAtLeast(0) >= threshold.coerceAtLeast(1)

    /**
     * 启发式判定本体（抽成纯函数便于离线单测）：核数 <4 或 JVM 堆上限 <4GB 视为低端。
     * 对齐切片-03「hardwareConcurrency < 4 或 Android ≤8 → 粒子减半」口径；
     * 核数 / 堆内存非正视为读取失败，按低端兜底（宁可降级保帧预算，不冒卡顿风险）。
     */
    fun heuristicLowSpec(cores: Int, heapGb: Double): Boolean =
        cores.coerceAtLeast(0) < 4 || heapGb.coerceAtLeast(0.0) < 4.0

    /**
     * 标题字尺寸识别：引擎未在 CharCell 上暴露标题标记，渲染层按字号判定——
     * 字高落在正文 / 标题字号中点以上即判标题，走加粗宋体。纯函数，便于离线单测；
     * 正文 / 标题字号任一非正时不判标题，回落正文口径。
     */
    fun isTitleCellSize(cellHeightPx: Float, bodySizePx: Float, titleSizePx: Float): Boolean {
        if (bodySizePx <= 0f || titleSizePx <= 0f) return false
        return cellHeightPx > (bodySizePx + titleSizePx) / 2f
    }

    private fun detectLowSpec(): Boolean {
        val cores = Runtime.getRuntime().availableProcessors()
        val heapGb = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0 / 1024.0
        return heuristicLowSpec(cores, heapGb)
    }
}
