package io.legado.app.help.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.sqrt

/**
 * 陀螺仪 / 姿态传感器桥（3D 沙盘 · 地球仪）。
 *
 * 链路（规格 §一）：`SensorManager` → FIFO 缓存（上限 10 条四元数）→
 * JS 端每帧 `pull()` 取一条最新值 → Three.js 端 slerp 平滑合成。
 * 不走 DeviceOrientation API：WebView 对 `file://` 不授权，白费授权弹窗。
 *
 * 三级硬件降级（规格 §六，优先级从高到低）：
 *  1. [Sensor.TYPE_ROTATION_VECTOR]：加速度计 + 陀螺仪 + 磁力计融合，最稳，首选；
 *  2. [Sensor.TYPE_GAME_ROTATION_VECTOR]：无磁力计修正，漂移偏大，次选；
 *  3. [Sensor.TYPE_ACCELEROMETER] + [Sensor.TYPE_MAGNETIC_FIELD] 融合矩阵兜底，
 *     漂移严重、噪声大；此分支 [degraded] 为 true，JS 端强制把合成权重压到 0.04；
 *  4. 全无传感器：[acquire] 返回 false，JS 端隐藏陀螺仪按钮，模式固定为关闭。
 *
 * 本类只做纯采集与排队，不持有 WebView；WebView 销毁后再调 [drainLatest] 也不会
 * 触碰任何 JS 上下文，满足「销毁后不得再 evaluateJavascript」的安全边界。
 *
 * 〔N5 跨宿主隔离〕书架 / 古今地图 / 战役沙盘三处共用本单例；旧口径任一宿主直接
 * `stop()` 会把仍在用桥的另一宿主传感器一并注销。现改为宿主持牌引用计数：
 * [acquire] 上牌、[release] 摘牌，最后一张牌摘掉才真正注销监听。
 */
object GyroBridge {

    /** FIFO 上限：规格 §二锁定 10 条；队列初始容量 12 仅为预留槽位，不放宽上限。 */
    private const val QUEUE_CAP = 10

    /** 兜底融合矩阵分支（level 3）强制权重上限：规格 §六锁定 0.04。 */
    const val DEGRADED_WEIGHT = 0.04f

    /** 0=不可用/已停止；1=旋转向量；2=游戏旋转向量；3=加速度+磁力融合。 */
    @Volatile
    var level: Int = 0
        private set

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null

    /**
     * 持牌宿主集合（N5 引用计数）：key 为宿主标识。
     * 为什么用集合而非单个计数：三个宿主可交错申领 / 交还，集合能识别「同一宿主重入」
     * 与「未知宿主重复交还」，release 只摘自己的牌，集合非空即保留共享监听。
     */
    private val holders = HashSet<String>()

    /** FIFO：新值从队尾进，[drainLatest] 从队首出，超出 [QUEUE_CAP] 挤掉队首。 */
    private val queue = ArrayDeque<FloatArray>(QUEUE_CAP + 2)

    // —— 兜底融合分支缓存：加速度与磁力计各来一路，两路齐了才算矩阵 ——
    private var lastAccel = FloatArray(3)
    private var lastMag = FloatArray(3)
    private var haveAccel = false
    private var haveMag = false

    /** 屏幕方向（Display.rotation），侧卧/倒挂时坐标轴翻转，供 remap 适配。 */
    @Volatile
    private var displayRotation = Surface.ROTATION_0

    /** 是否落在噪声最大的兜底融合分支；JS 端据此锁权重 0.04。 */
    val degraded: Boolean
        get() = level == 3

    /** 更新屏幕方向；onResume / 配置变更时由宿主 Activity 传入。 */
    fun setDisplayRotation(rotation: Int) {
        displayRotation = rotation
    }

    /**
     * 宿主申领传感器桥（N5 引用计数入口）。
     * @param host 宿主标识：`"shelf"` 书架 / `"place-map"` 古今地图 / `"battle"` 战役沙盘
     * @return true=至少一路传感器可用；false=全无传感器，调用方应隐藏入口并退回纯手指操控。
     *
     * 时序口径：同一宿主重复申领不重复上牌；已有存活监听时新宿主直接共用一路注册，
     * 不重复 registerListener（双份事件徒增 50Hz 功耗）。
     */
    fun acquire(host: String, context: Context): Boolean {
        val fresh: Boolean
        synchronized(holders) {
            fresh = holders.add(host)
        }
        if (fresh) ensureStarted(context)
        return level > 0
    }

    /**
     * 宿主交还桥：摘掉自己的持牌；仅当最后一张牌摘掉时才真正注销监听、清队复位（N5）。
     * 未上牌的宿主调用为本轮空操作，保证 onPause / onDestroy 重复交还幂等。
     */
    fun release(host: String) {
        val empty: Boolean
        synchronized(holders) {
            empty = holders.remove(host) && holders.isEmpty()
        }
        if (empty) teardown()
    }

    /** 注册传感器并开始采集；已有存活注册时直接复用，避免重复注册。 */
    private fun ensureStarted(context: Context) {
        if (listener != null && sensorManager != null) return
        teardown() // 清掉上一轮残留监听 / 脏队列（旧 start() 幂等重入口径保留）
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm

        val rotationVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gameVec = rotationVec ?: sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val accel = if (gameVec == null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val mag = if (accel != null) sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

        when {
            rotationVec != null -> level = 1
            gameVec != null -> level = 2
            accel != null && mag != null -> level = 3
            else -> {
                level = 0
                return // 全无传感器：不注册任何监听，available 由 level=0 上报 JS / 书架
            }
        }

        val lister = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR,
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        // getQuaternionFromVector 输出 [w, x, y, z]，与 JS 端 onQuat(w,x,y,z) 对齐
                        val q = FloatArray(4)
                        SensorManager.getQuaternionFromVector(q, event.values)
                        enqueue(q)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lastAccel = event.values.clone()
                        haveAccel = true
                        fuseAccelMag()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lastMag = event.values.clone()
                        haveMag = true
                        fuseAccelMag()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // 精度变化不单独上报；精度差 / 抖动由 JS 端 slerp 权重平滑吸收，见 gyro.js
            }
        }
        listener = lister

        // SENSOR_DELAY_GAME ≈ 50Hz：与 3D 渲染帧率匹配，功耗可控（规格 §七）
        when (level) {
            1 -> sm.registerListener(lister, rotationVec, SensorManager.SENSOR_DELAY_GAME)
            2 -> sm.registerListener(lister, gameVec, SensorManager.SENSOR_DELAY_GAME)
            3 -> {
                sm.registerListener(lister, accel, SensorManager.SENSOR_DELAY_GAME)
                sm.registerListener(lister, mag, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    /** 取队首一条四元数；空队返回 null（JS 端本帧不更新姿态）。 */
    fun drainLatest(): FloatArray? = synchronized(queue) { queue.removeFirstOrNull() }

    /** 注销全部监听并清空缓存；最后持牌者交还 / 桥重建前调用，杜绝泄漏。 */
    private fun teardown() {
        val sm = sensorManager
        val lister = listener
        if (sm != null && lister != null) {
            runCatching { sm.unregisterListener(lister) }
        }
        listener = null
        sensorManager = null
        synchronized(queue) { queue.clear() }
        haveAccel = false
        haveMag = false
        level = 0
    }

    /** 入队 FIFO：超上限挤掉队首（最旧一条）。 */
    private fun enqueue(q: FloatArray) = synchronized(queue) {
        queue.addLast(q)
        while (queue.size > QUEUE_CAP) queue.removeFirst()
    }

    /** 兜底分支：加速度 + 磁力计 → 旋转矩阵 → remap 坐标轴 → 四元数入队。 */
    private fun fuseAccelMag() {
        if (!haveAccel || !haveMag) return
        val rot = FloatArray(9)
        val inclination = FloatArray(9)
        // getRotationMatrix 在磁力计过弱 / 姿态异常时返回 false；此时丢弃本帧，等下一帧
        if (!SensorManager.getRotationMatrix(rot, inclination, lastAccel, lastMag)) return
        remapForDisplay(rot)
        enqueue(matrixToQuaternion(rot))
    }

    /** 屏幕旋转 90°/180°/270° 时设备坐标轴翻转，按显示方向重映射矩阵（规格 §六）。 */
    private fun remapForDisplay(R: FloatArray) {
        val out = FloatArray(9)
        val (xAxis, yAxis) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(R, xAxis, yAxis, out)
        System.arraycopy(out, 0, R, 0, 9)
    }

    /** 3×3 旋转矩阵 → 单位四元数 [w, x, y, z]（标准迹线分支法，避免万向节奇异除零）。 */
    private fun matrixToQuaternion(R: FloatArray): FloatArray {
        val tr = R[0] + R[4] + R[8]
        var w: Double
        var x: Double
        var y: Double
        var z: Double
        when {
            tr > 0.0 -> {
                val s = sqrt(tr + 1.0) * 2.0
                w = 0.25 * s
                x = (R[7] - R[5]) / s
                y = (R[2] - R[6]) / s
                z = (R[3] - R[1]) / s
            }
            R[0] > R[4] && R[0] > R[8] -> {
                val s = sqrt(1.0 + R[0] - R[4] - R[8]) * 2.0
                w = (R[7] - R[5]) / s
                x = 0.25 * s
                y = (R[1] + R[3]) / s
                z = (R[6] + R[2]) / s
            }
            R[4] > R[8] -> {
                val s = sqrt(1.0 + R[4] - R[0] - R[8]) * 2.0
                w = (R[2] - R[6]) / s
                x = (R[1] + R[3]) / s
                y = 0.25 * s
                z = (R[5] + R[7]) / s
            }
            else -> {
                val s = sqrt(1.0 + R[8] - R[0] - R[4]) * 2.0
                w = (R[3] - R[1]) / s
                x = (R[6] + R[2]) / s
                y = (R[5] + R[7]) / s
                z = 0.25 * s
            }
        }
        val norm = sqrt(w * w + x * x + y * y + z * z).coerceAtLeast(1e-9)
        return floatArrayOf((w / norm).toFloat(), (x / norm).toFloat(), (y / norm).toFloat(), (z / norm).toFloat())
    }
}
