package io.legado.app.ui.book.read.ink

/**
 * 阅读页规格档位与帧预算守卫（渲染层降级红线）。
 *
 * 职责：
 *  - [isLowSpec]：低端机判定（核数 / 堆内存启发式，可被测试或外部显式覆盖）；
 *  - [particleCount]：常规 / 低端两档氛围粒子数；
 *  - [recordFrame] / [shouldDegrade]：单帧绘制超预算计数，连续超阈值即降级。
 *
 * 本对象不触碰 Android 视图类，判定与计数全部为纯函数，可离线 JVM 单测。
 * 注意：[detectLowSpec] 取的是 JVM 堆上限与 CPU 核数，不是整机 RAM；
 * 整机内存档位待系统模块收口后在此替换判定口径。
 */
object InkSpec {

    /** 测试 / 外部显式覆盖档位；null 走 [detectLowSpec] 启发式。 */
    var overrideLowSpec: Boolean? = null

    /** 启发式结果缓存一次即可：进程内档位不变。 */
    private val detectedLowSpec: Boolean by lazy { detectLowSpec() }

    /** 当前是否低端档位。 */
    fun isLowSpec(): Boolean = overrideLowSpec ?: detectedLowSpec

    /** 氛围粒子数：低端减半；[full] 常规颗数，[low] 低端颗数。 */
    fun particleCount(full: Int, low: Int, lowSpec: Boolean): Int =
        if (lowSpec) low.coerceAtLeast(0) else full.coerceAtLeast(0)

    /**
     * 记录一帧绘制耗时，返回新的连续超预算计数；回到预算内归零。
     * 纯函数：不读全局时钟，耗时由调用侧采样传入，便于单测构造边界帧。
     */
    fun recordFrame(streak: Int, elapsedMs: Double, budgetMs: Double): Int =
        if (elapsedMs > budgetMs) streak + 1 else 0

    /** 连续超预算帧数达到阈值即应降级（粒子减半、跳过噪点叠层等）。 */
    fun shouldDegrade(streak: Int, threshold: Int): Boolean = streak >= threshold

    /** 启发式判定本体（抽成纯函数便于离线单测）：核数 <4 或 JVM 堆上限 <4GB 视为低端。 */
    fun heuristicLowSpec(cores: Int, heapGb: Double): Boolean = cores < 4 || heapGb < 4.0

    /**
     * 标题字尺寸识别：引擎未在 [CharCell] 上暴露标题标记，渲染层按字号判定——
     * 字高落在正文 / 标题字号中点以上即判标题，走加粗宋体。纯函数，便于离线单测。
     */
    fun isTitleCellSize(cellHeightPx: Float, bodySizePx: Float, titleSizePx: Float): Boolean =
        cellHeightPx > (bodySizePx + titleSizePx) / 2f

    private fun detectLowSpec(): Boolean {
        val cores = Runtime.getRuntime().availableProcessors()
        val heapGb = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0 / 1024.0
        return heuristicLowSpec(cores, heapGb)
    }
}
