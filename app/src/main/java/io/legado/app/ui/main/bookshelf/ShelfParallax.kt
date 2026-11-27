package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.legado.app.help.sensor.GyroBridge
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Q441 · 书架陀螺仪视差（2D 位移 + 暖金阴影动态偏移，不做 3D 旋转）。
 *
 * 〔职责边界〕本层只做「整桥 → pitch/roll 归一化 → 平滑视差状态」：
 * 传感器采集 / 三级降级在 [GyroBridge]（Q-439），本文件不重复造桥；
 * 卡片渲染读 [ShelfParallaxState] 暴露的一对共享位移 / 阴影值，N 张卡片不做 N 次平滑计算。
 *
 * 〔数值口径（规格 §实现，锁死）〕
 *  - maxShift = 6dp：卡片最大位移；maxShadowShift = 4dp：阴影最大偏移；
 *  - 平滑系数 0.06：比沙盘更惰性，卡片不能抖；
 *  - 阴影半径 12 + |roll|×6 dp；阴影色 RGB 212,165,74，透明度 0.3 + |roll|×0.15；
 *  - 手指滑动暂停视差，松手 400ms 恢复。
 */
object ShelfParallaxTokens {

    /** 卡片最大位移 dp（规格 maxShift = 6f）。 */
    const val MAX_SHIFT_DP: Float = 6f

    /** 阴影最大偏移 dp（规格 maxShadowShift = 4f）。 */
    const val MAX_SHADOW_SHIFT_DP: Float = 4f

    /** slerp 平滑权重：0.06 惰性跟踪，抑制传感器抖动传到卡片。 */
    const val SMOOTH_ALPHA: Float = 0.06f

    /** 松手后恢复视差的延迟 ms（规格 400ms）。 */
    const val RESUME_DELAY_MS: Long = 400L

    /** 阴影基础半径 dp（规格 radius 12f）。 */
    const val SHADOW_RADIUS_BASE_DP: Float = 12f

    /** 阴影半径随 |roll| 增量 dp（规格 abs(roll)*6f）。 */
    const val SHADOW_RADIUS_GAIN_DP: Float = 6f

    /** 阴影基础透明度（规格 0.3f）。 */
    const val SHADOW_ALPHA_BASE: Float = 0.3f

    /** 阴影透明度随 |roll| 增量（规格 abs(roll)*0.15f）。 */
    const val SHADOW_ALPHA_GAIN: Float = 0.15f

    /** 阴影暖金 RGB（规格 212,165,74）。 */
    const val SHADOW_RGB_R: Int = 212
    const val SHADOW_RGB_G: Int = 165
    const val SHADOW_RGB_B: Int = 74

    /** 轮询桥队列间隔 ms：与屏幕帧率匹配，功耗可控；空队时不做任何计算。 */
    internal const val POLL_INTERVAL_MS: Long = 16L

    /** 关闭态轮询间隔 ms（D3）：只刷新按钮可用态，不 drain、不做平滑换算；2Hz 足够开关回显。 */
    internal const val IDLE_POLL_INTERVAL_MS: Long = 500L

    /** 归一化分母：±90° 倾角映射到 ±1（半 π 弧度）；非 const：Math.PI 非常量表达式。 */
    internal val HALF_PI: Float = (Math.PI / 2).toFloat()
}

/**
 * 四元数 → (roll, pitch) 归一化倾角，值域 [-1,1]。
 *
 * 〔坐标口径〕roll：-1 左倾 → +1 右倾；pitch：-1 前倾 → +1 后倾。
 * 〔为什么纯函数〕不碰 Compose / 传感器实例，普通 JVM 单测可对确定四元数断言；
 * 〔边界兜底〕缺分量、NaN / Inf、近零模长一律丢弃本帧（返回 null），不外传脏值。
 * 〔符号调谐点〕若真机实测左右 / 前后镜像，只改本函数两处取负号，渲染层与状态层不动。
 */
internal fun quaternionToTilt(q: FloatArray): Pair<Float, Float>? {
    if (q.size < 4) return null
    val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]
    val values = floatArrayOf(w, x, y, z)
    if (values.any { !it.isFinite() }) return null
    val norm = sqrt(w * w + x * x + y * y + z * z)
    if (norm < 1e-6f) return null
    val wn = w / norm; val xn = x / norm; val yn = y / norm; val zn = z / norm

    // roll：绕前后轴的左右倾；pitch：绕左右轴的前后倾（标准欧拉分解，万向节奇异由钳位兜底）。
    val rollRad = atan2(2f * (wn * xn + yn * zn), 1f - 2f * (xn * xn + yn * yn))
    val pitchRad = atan2(2f * (wn * yn - zn * xn), 1f - 2f * (yn * yn + zn * zn))

    val roll = (-rollRad / ShelfParallaxTokens.HALF_PI).coerceIn(-1f, 1f)
    val pitch = (pitchRad / ShelfParallaxTokens.HALF_PI).coerceIn(-1f, 1f)
    return roll to pitch
}

/**
 * 一阶指数平滑：`smooth += (raw - smooth) × alpha`。
 * 为什么不用 slerp 球面插值：视差只取两个标量倾角，线性逼近足够；0.06 低权重即规格要求的惰性感。
 */
internal fun smoothTilt(current: Float, raw: Float, alpha: Float = ShelfParallaxTokens.SMOOTH_ALPHA): Float =
    (current + (raw - current) * alpha).coerceIn(-1f, 1f)

/**
 * 书架视差共享状态：一页书架只持有一对平滑值，所有卡片同读同写。
 *
 * 〔为什么位移 / 阴影派生值放在状态 getter 里〕卡片重组时只读 px 标量，
 * 不在每张卡片上重复做 dp→px 换算与平滑；状态本身不随卡片数量分配对象。
 *
 * @param enabled 开关偏好（用户关闭后卡片全程静止，不跟倾斜）
 */
class ShelfParallaxState internal constructor(
    private val density: Density,
    enabled: Boolean,
) {
    /** 平滑后 roll（-1 左倾 → +1 右倾），Compose State 驱动卡片层只读重组。 */
    var roll by mutableStateOf(0f)
        private set

    /** 平滑后 pitch（-1 前倾 → +1 后倾）。 */
    var pitch by mutableStateOf(0f)
        private set

    /** 传感器是否可用（桥 level>0）；无传感器设备时按钮置灰、卡片静止。 */
    var available by mutableStateOf(false)
        private set

    /** 用户开关：false 时无视差，位移 / 阴影全部回中。 */
    var enabled by mutableStateOf(enabled)
        private set

    /** 手指滑动期间冻结视差：暂停向目标值靠拢，松手 400ms 后由宿主恢复。 */
    internal var touchPaused by mutableStateOf(false)
        private set

    private var rawRoll = 0f
    private var rawPitch = 0f

    /** 桥每帧推入一条原始倾角；暂停期间只记目标值，不动当前平滑值（防滑动 + 陀螺仪叠加抖动）。 */
    internal fun onRawTilt(roll: Float, pitch: Float) {
        rawRoll = roll
        rawPitch = pitch
        if (touchPaused || !enabled || !available) return
        this.roll = smoothTilt(this.roll, rawRoll)
        this.pitch = smoothTilt(this.pitch, rawPitch)
    }

    /** 宿主轮询回调：刷新传感器可用性（桥 acquire 后 level 才 >0，首帧可能为 0）。 */
    internal fun refreshAvailability() {
        available = GyroBridge.level > 0
    }

    /** 开关切换：同时落偏好由宿主写；状态层只切生效位。 */
    internal fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            // 关闭即回中：避免下次开启时残留上一次倾斜位移
            roll = 0f
            pitch = 0f
        }
    }

    /** 手指滑动开始 / 结束；结束由宿主延迟 [ShelfParallaxTokens.RESUME_DELAY_MS] 再调 [resumeFromTouch]。 */
    internal fun setTouchPaused(paused: Boolean) {
        touchPaused = paused
    }

    /** 松手 400ms 后恢复跟踪。 */
    internal fun resumeFromTouch() {
        touchPaused = false
    }

    /** 视差是否真正生效：开 + 有传感器 + 未被手势暂停。 */
    val active: Boolean
        get() = enabled && available && !touchPaused

    private val shiftPx: Float = with(density) { ShelfParallaxTokens.MAX_SHIFT_DP.dp.toPx() }
    private val shadowShiftPx: Float = with(density) { ShelfParallaxTokens.MAX_SHADOW_SHIFT_DP.dp.toPx() }

    // D2 基础态缓存：非 active 时直接回这两个常量派生值，避免每帧新建 Color / 重复 dp 换算
    // 造成 GC 抖动；关闭 / 无传感器态阴影必须与基础态逐值一致，不留残影。
    private val baseRadiusPx: Float = with(density) { ShelfParallaxTokens.SHADOW_RADIUS_BASE_DP.dp.toPx() }
    private val baseShadowColor: Color = Color(
        red = ShelfParallaxTokens.SHADOW_RGB_R / 255f,
        green = ShelfParallaxTokens.SHADOW_RGB_G / 255f,
        blue = ShelfParallaxTokens.SHADOW_RGB_B / 255f,
        alpha = ShelfParallaxTokens.SHADOW_ALPHA_BASE,
    )

    /** 卡片横向位移 px（dx = roll × maxShift）。 */
    val translationX: Float get() = if (active) roll * shiftPx else 0f

    /** 卡片纵向位移 px（dy = pitch × maxShift）。 */
    val translationY: Float get() = if (active) pitch * shiftPx else 0f

    /** 阴影横向偏移 px：active 时倾斜反方向；非 active 精确归 0（D2，防关闭 / 无传感器态残影）。 */
    val shadowOffsetX: Float get() = if (active) -roll * shadowShiftPx else 0f

    /** 阴影纵向偏移 px：同上，非 active 归 0。 */
    val shadowOffsetY: Float get() = if (active) -pitch * shadowShiftPx else 0f

    /** 阴影模糊半径 px：active 时 12 + |roll|×6 dp；非 active 回基础半径（D2）。 */
    val shadowRadiusPx: Float
        get() = if (active) {
            with(density) {
                (ShelfParallaxTokens.SHADOW_RADIUS_BASE_DP + abs(roll) * ShelfParallaxTokens.SHADOW_RADIUS_GAIN_DP).dp.toPx()
            }
        } else baseRadiusPx

    /** 阴影暖金色：active 时透明度 0.3 + |roll|×0.15；非 active 回缓存基础色，不每帧新建 Color（D2）。 */
    val shadowColor: Color
        get() = if (active) {
            val alpha = (ShelfParallaxTokens.SHADOW_ALPHA_BASE +
                abs(roll) * ShelfParallaxTokens.SHADOW_ALPHA_GAIN).coerceIn(0f, 1f)
            Color(
                red = ShelfParallaxTokens.SHADOW_RGB_R / 255f,
                green = ShelfParallaxTokens.SHADOW_RGB_G / 255f,
                blue = ShelfParallaxTokens.SHADOW_RGB_B / 255f,
                alpha = alpha,
            )
        } else baseShadowColor
}

/**
 * 记住书架视差状态：单协程轮询 [GyroBridge] 队列，平滑值全页共享。
 *
 * 〔生命周期配合〕桥的注册 / 注销归宿主 Fragment onResume/onPause/onHiddenChanged；
 * 本 effect 只在 composition 存活期间取数，离开组合即停轮询，不触碰传感器注册。
 *
 * @param enabled 用户开关（初值由宿主 Fragment 读偏好后传入，本层不碰 AppConfig）
 */
@Composable
fun rememberShelfParallaxState(enabled: Boolean): ShelfParallaxState {
    val density = LocalDensity.current
    // 不加 density 键：Density 非稳定相等键，避免重组时重建状态把平滑值清零造成跳变。
    val state = remember { ShelfParallaxState(density, enabled) }

    LaunchedEffect(enabled) {
        state.setEnabled(enabled)
        if (enabled) {
            // 开启态：单轮询协程与屏幕帧率匹配，无论卡片多少张，桥队列只被这一条循环消费一次。
            while (true) {
                state.refreshAvailability()
                val q = GyroBridge.drainLatest()
                if (q != null) {
                    quaternionToTilt(q)?.let { (roll, pitch) -> state.onRawTilt(roll, pitch) }
                }
                delay(ShelfParallaxTokens.POLL_INTERVAL_MS)
            }
        } else {
            // 关闭态（D3）：桥已由宿主释放、无传感器注册；这里降到 2Hz 只刷新按钮可用态，
            // 不 drain 队列、不做倾角换算与平滑，避免旧口径每 16ms 空转扣电。
            while (true) {
                state.refreshAvailability()
                delay(ShelfParallaxTokens.IDLE_POLL_INTERVAL_MS)
            }
        }
    }
    return state
}
