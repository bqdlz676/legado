package io.legado.app.ui.map.globe.astro.contract

/**
 * 星空双视角（§九.1、§八.10-11）：
 * [SKY_HEAVEN] 天外上帝视角——赤道坐标 + 自由旋转，地球悬中央，星空为背景；
 * [SKY_EARTH] 地上观——地平坐标，视场 60°，地平线下压暗，上方 5° 暖辉。
 */
enum class SkyViewMode { SKY_HEAVEN, SKY_EARTH }

/** 相机偏航 / 俯仰快照（双标量不可变，避免数组被回写）。单位：度。 */
data class CameraYawPitch(val yawDeg: Double, val pitchDeg: Double)

/** 观测点纬度 / 经度快照（等价不可变表达；不引入 Android `LatLng`）。单位：度。 */
data class ObserverLatLon(val latDeg: Double, val lonDeg: Double)

/**
 * 星空帧快照（§十二.1）。
 *
 * 〔为什么用标量 + id 不用星对象列表（§十二.2）〕一帧 700 颗若每帧 new 列表，
 * 软渲染 GC 抖动吃满 16ms；本快照只装标量与选中 id，星体由数据单的 `StarStore`
 * 按 id 取，快照本身零额外分配。
 *
 * 〔不可变口径〕全部 `val`；装配后渲染层不得回写本快照。既有 `FrameSnapshot`
 * 增挂 `sky: SkyFrame? = null` 属接线单域，本单只定义本类。
 *
 * @property mode 视角：天外 / 地上，默认天外。
 * @property skyBlend 星空混合标量 0→1（§十.3）：0 地球视觉直径 400px，1 缩到 40px 锚。
 * @property cameraYawPitch 相机偏航 / 俯仰（度）；天外自由旋转，地上平移方位。
 * @property observerLatLon 观测点经纬度；地上观无定位时默认长安并标“示意”（§十三.5）。
 * @property siderealTimeDeg 地方恒星时（度）；地上观赤道→地平换算用，一期固定 J2000（§〇.7）。
 * @property magLimit 当前星等裁剪上限；由 [SkyParams.magLimitFromBlend] 连续算出。
 * @property pyramidMix 金字塔两级连续混合权重 0→1（§十.6），渲染层按权重插值顶点。
 * @property selectedId 选中星 / 星官 / 星座 / 分野 id；未选中为 null。
 * @property horizonDipDeg 地平线下压暗倾角（度）；地上观专用，天外为 0。
 */
data class SkyFrame(
    val mode: SkyViewMode = SkyViewMode.SKY_HEAVEN,
    val skyBlend: Float = 0f,
    val cameraYawPitch: CameraYawPitch? = null,
    val observerLatLon: ObserverLatLon? = null,
    val siderealTimeDeg: Double = 0.0,
    val magLimit: Float = SkyParams.MAG_BRIGHT_ENTITY,
    val pyramidMix: Float = 0f,
    val selectedId: Long? = null,
    val horizonDipDeg: Float = 0f,
)
