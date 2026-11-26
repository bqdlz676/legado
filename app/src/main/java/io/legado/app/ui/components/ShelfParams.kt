package io.legado.app.ui.components

import kotlin.math.roundToInt

/**
 * S-10 书架页 · 视觉参数单源（平台无关层，纯数值）。
 *
 * 〔为什么参数集中在一处〕
 * 书名 / 作者注字号、进度竖线色、卡片倾角、氛围粒子上下限、阴影模糊半径与透明度，
 * 来自 `docs/落文-规格切片/切片-10-书架页.md` 第 8、9、22 行与
 * `docs/锚点/S-10-书架页.md` 参数表；进度竖线宽出自 `切片-25-卡片设计规格.md:19`（2dp）。
 * 集中单点定义是为了：
 *  1. 将来接入书架卡片渲染时，Composable / 绘制代码只允许引用本表，不得在页面里
 *     另写字号、线宽、倾角、粒子数或色值，避免渲染层、既有 token 副本、单测三处
 *     口径漂移；
 *  2. 单测可对确定输入断言确定输出（如进度 0.5 × 卡高 200dp → 线长 100dp）；
 *  3. 后续调参只改这一处，改动可被 grep 回证与单测同时兜住。
 *
 * 〔为什么用纯函数 / 纯数值〕
 * [progressLineHeight]、[particleCount]、[cardTiltDegrees] 只接收并返回
 * Double / Float / Int，不调用任何 Android API（dp/sp 换算、Paint、Canvas、
 * Resources），也不依赖 Compose 运行时或资源加载，普通 JVM 单测可直接运行断言；
 * sp/dp ↔ px 的单位换算留给将来渲染层接入时自行完成。
 *
 * 〔颜色口径〕
 * 全部颜色只引用既有单源 [ComponentPalette]：书名墨色 TEXT_PRIMARY、
 * 作者注云灰 TEXT_TERTIARY、进度竖线朱砂 TEXT_ACCENT；阴影暖金由
 * [ComponentPalette.BRAND_GOLD] 派生（RGB 位取自金 token，高位 alpha 按
 * [SHADOW_ALPHA] 重算），本文件不写任何十六进制色值（锚点卡判据 1）。
 *
 * 〔粒子保底为什么不能一味减半〕
 * 对齐 `docs/立项-补条款-第二批.md` 条文 1.2（L2 运行时帧率掉线档）：二级掉线时
 * 装饰粒子密度减半，一级掉线再停悬浮微粒；削减只动装饰层与渲染粒度，不碰排版。
 * 但切片-10 原文给定氛围粒子下限 15 粒，这是卡片下方氛围层在竖排卡片流里仍可见的
 * 视觉底线：若把上限 20 直接减半到 10，低于切片下限，氛围层在屏幕上近乎消失，等于
 * 在二级档就提前执行了一级档「停微粒」的动作，与 1.2 的两级阶梯矛盾，也击穿了切片
 * 给定的数量区间。因此 [particleCount] 在减半后仍按 15 保底：降级优先削减数量上限，
 * 不允许击穿原文下限；真到一级掉线，再由渲染层整体关闭微粒层，不在本函数内二次削减。
 */
object ShelfParams {

    // ── 切片-10 原文参数（锚点卡参数表；单位不许自创） ──

    /** 竖排书名 22sp，加粗（切片-10:8「竖排书名 22sp 墨色加粗」） */
    const val TITLE_FONT_SIZE_SP: Double = 22.0

    /** 书名字重：加粗（true = 加粗；渲染层接入时映射到对应字重） */
    const val TITLE_BOLD: Boolean = true

    /** 书名颜色：墨色，取 [ComponentPalette.TEXT_PRIMARY]，不写 hex */
    val titleColor: Long get() = ComponentPalette.TEXT_PRIMARY

    /** 作者 / 注疏 11sp（切片-10:9「云灰色 11sp」） */
    const val AUTHOR_FONT_SIZE_SP: Double = 11.0

    /** 作者注颜色：云灰，取 [ComponentPalette.TEXT_TERTIARY]，不写 hex */
    val authorColor: Long get() = ComponentPalette.TEXT_TERTIARY

    /**
     * 书架卡片进度竖线宽 2dp（切片-25-卡片设计规格.md:19「右侧朱砂进度竖线 2dp」）。
     * 为什么两个同名常量值不同：本常量属书架卡片域；阅读页右缘正文细线在
     * [VerticalReaderParams.PROGRESS_LINE_WIDTH_DP]（1.0，切片-09:7），是两根不同的线，
     * 规格出处与视觉层级都不同，禁止合并或互相“统一”。切片-10:22 只写「书名右侧一道
     * 朱砂竖线，线长 = 阅读进度比例」，未给宽度，旧注释误引该处，2026-11-26 用户裁决纠正。
     */
    const val PROGRESS_LINE_WIDTH_DP: Double = 2.0

    /** 进度竖线颜色：朱砂，取 [ComponentPalette.TEXT_ACCENT]，不写 hex */
    val progressLineColor: Long get() = ComponentPalette.TEXT_ACCENT

    /** 卡片 3D 前倾 5°（切片-10:22「卡片微微 3D 前倾旋转 5°」） */
    const val CARD_TILT_DEGREES: Double = 5.0

    /** 氛围粒子下限 15 粒（切片-10:22「15-20 粒极淡金色微粒，缓慢随机飘动」） */
    const val PARTICLE_MIN: Int = 15

    /** 氛围粒子上限 20 粒 */
    const val PARTICLE_MAX: Int = 20

    /** 阴影模糊半径 12dp（切片-10:22「0 0 12px rgba(212,165,74,0.3)」） */
    const val SHADOW_BLUR_DP: Double = 12.0

    /** 阴影透明度 0.3（同切片原文 0.3 阿尔法；非黑灰阴影） */
    const val SHADOW_ALPHA: Double = 0.3

    /**
     * 阴影色：暖金由 [ComponentPalette.BRAND_GOLD] 派生——RGB 位取自金 token，
     * 高位 alpha 按 [SHADOW_ALPHA]（0.3）重算；本函数不内联任何 hex 色值。
     */
    val shadowColor: Long
        get() {
            val rgb = ComponentPalette.BRAND_GOLD and 0xFFFFFFL
            val alpha = (SHADOW_ALPHA * 255.0).roundToInt().toLong()
            return (alpha shl 24) or rgb
        }
}

/**
 * 进度竖线长度（dp）= 阅读进度比例 × 卡片高度 dp。
 *
 * 边界兜底：progress 为 0、负数、大于 1、NaN 时一律夹到 [0,1]，不抛异常、不产生
 * NaN / Infinity；cardHeightDp ≤ 0 时线长记 0。本函数只用 Double / Float，
 * 不含任何 Android 或 Compose 类型。
 */
fun progressLineHeight(progress: Float, cardHeightDp: Double): Double {
    val clamped = when {
        progress.isNaN() -> 0.0f
        progress < 0f -> 0f
        progress > 1f -> 1f
        else -> progress
    }
    val safeHeight = if (cardHeightDp > 0.0) cardHeightDp else 0.0
    return clamped.toDouble() * safeHeight
}

/**
 * 书架氛围粒子数，返回值恒落在 [ShelfParams.PARTICLE_MIN, ShelfParams.PARTICLE_MAX]。
 *
 * 档位口径（对齐 S-08 条文 1.2 与补条款第二批 §一.2）：
 *  - qualityLevel ≥ 2：常规档，取上限 [ShelfParams.PARTICLE_MAX]（20 粒）；
 *  - qualityLevel ≤ 1：降级档（L1 低端机静态档 / L2 二级帧率掉线），粒子密度减半；
 *    减半后若低于切片下限 15，按 15 保底——理由见 [ShelfParams] 头部注释：15 粒是
 *    氛围层可见底线，减半不得击穿切片给定区间；一级掉线的「停微粒」由渲染层在更高
 *    档位处理，不在本函数内再次削减。
 */
fun particleCount(qualityLevel: Int): Int {
    val halved = qualityLevel < 2
    val raw = if (halved) ShelfParams.PARTICLE_MAX / 2 else ShelfParams.PARTICLE_MAX
    return raw.coerceAtLeast(ShelfParams.PARTICLE_MIN)
        .coerceAtMost(ShelfParams.PARTICLE_MAX)
}

/** 卡片 3D 前倾角度（度），恒为 5.0（切片-10:22）。 */
fun cardTiltDegrees(): Double = ShelfParams.CARD_TILT_DEGREES
