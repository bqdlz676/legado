package io.legado.app.ui.book.read.ink

/**
 * 落文 · 阅读页外部翻页命令与键位解析（纯 JVM，不绑定 Android，可离线单测）。
 *
 * 背景：交接清单 #11 遗留——旧 ReadBookActivity 的媒体键 / 音量键 / 方向键翻页通道
 * 在新壳 InkReadActivity 尚未接入。本文件把「键码 → 翻页命令」的解析规则抽成纯逻辑，
 * Android 绑定（BroadcastReceiver / onKeyDown）只负责取键码与回调，判定全部在此收口。
 *
 * 口径对齐旧 ReadBookActivity：
 *  - 上翻：音量上 / 方向上 / 方向左 / 翻页上 / 自定义上翻键 / 媒体上一曲；
 *  - 下翻：音量下 / 方向下 / 方向右 / 翻页下 / 空格 / 自定义下翻键 / 媒体下一曲；
 *  - 长按连发仅在开启「长按翻页」时生效，否则忽略 repeat；
 *  - 音量键受总开关约束；朗读播放中若未开「播放时音量键翻页」，音量键让给朗读。
 *
 * 铁律 #39 不受本通道影响：媒体键 / 硬件键是定向翻页，不存在「点中字」竞争；
 * 点字优先于翻页仍由 GestureRouter 边缘命中探测通道保证。
 */

/** 阅读页外部输入命令。 */
sealed interface PageCommand {

    /** 翻一页；方向复用手势分区契约 [EdgeDirection]（Prev 上翻，Next 下翻）。 */
    data class Turn(val direction: EdgeDirection) : PageCommand

    /** 朗读播放 / 暂停切换（媒体播放暂停键 / 耳机线控键）。 */
    data object ToggleReadAloud : PageCommand
}

/**
 * 按键解析配置（宿主从 AppConfig / 朗读服务状态灌入；纯数据，本层不读 SharedPreferences）。
 *
 * @param volumeKeyEnabled 音量键翻页总开关（AppConfig.volumeKeyPage）。
 * @param volumeKeyPageOnPlay 朗读播放中是否仍允许音量键翻页（AppConfig.volumeKeyPageOnPlay）。
 * @param readAloudPlaying 当前朗读是否在播放（BaseReadAloudService.isPlay()）。
 * @param keyPageOnLongPress 是否允许长按连发翻页（AppConfig.keyPageOnLongPress）。
 * @param prevCustomKeys 用户自定义上翻键码集合。
 * @param nextCustomKeys 用户自定义下翻键码集合。
 */
data class PageKeyConfig(
    val volumeKeyEnabled: Boolean,
    val volumeKeyPageOnPlay: Boolean,
    val readAloudPlaying: Boolean,
    val keyPageOnLongPress: Boolean,
    val prevCustomKeys: Set<Int>,
    val nextCustomKeys: Set<Int>,
) {
    companion object {
        /** 测试 / 兜底用默认配置：音量键开、播放中可翻、长按不连发、无自定义键。 */
        val DEFAULT = PageKeyConfig(
            volumeKeyEnabled = true,
            volumeKeyPageOnPlay = true,
            readAloudPlaying = false,
            keyPageOnLongPress = false,
            prevCustomKeys = emptySet(),
            nextCustomKeys = emptySet(),
        )
    }
}

/**
 * 硬件键 / 媒体键 → [PageCommand] 解析器（纯函数，无状态）。
 *
 * 键码取值对齐 AOSP `KeyEvent` 常量数值并在此冻结，使纯 JVM 层无需 import
 * `android.view.KeyEvent`；宿主把 `event.keyCode` 原样传入即可。
 */
object InkPageKeys {

    // ── AOSP KeyEvent 键码（frameworks/base/core/java/android/view/KeyEvent.java）──

    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_SPACE = 62
    const val KEYCODE_HEADSETHOOK = 79
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_PAGE_UP = 92
    const val KEYCODE_PAGE_DOWN = 93

    /** 内置上翻硬件键：方向上 / 方向左 / 翻页上。 */
    private val PREV_KEYS = setOf(KEYCODE_DPAD_UP, KEYCODE_DPAD_LEFT, KEYCODE_PAGE_UP)

    /** 内置下翻硬件键：方向下 / 方向右 / 翻页下 / 空格。 */
    private val NEXT_KEYS = setOf(
        KEYCODE_DPAD_DOWN,
        KEYCODE_DPAD_RIGHT,
        KEYCODE_PAGE_DOWN,
        KEYCODE_SPACE,
    )

    /**
     * 解析硬件按键（音量键 / 方向键 / 翻页键 / 空格 / 用户自定义键）。
     *
     * @param keyCode    [android.view.KeyEvent.getKeyCode] 原值。
     * @param repeatCount [android.view.KeyEvent.getRepeatCount]；>0 表示长按连发。
     * @param config     按键开关与朗读状态配置。
     * @return 翻页 / 朗读切换命令；未识别或被开关 / 连发规则屏蔽时返回 null。
     */
    fun resolveHardwareKey(keyCode: Int, repeatCount: Int, config: PageKeyConfig): PageCommand? {
        val command = when (keyCode) {
            in PREV_KEYS, in config.prevCustomKeys -> PageCommand.Turn(EdgeDirection.Prev)
            in NEXT_KEYS, in config.nextCustomKeys -> PageCommand.Turn(EdgeDirection.Next)
            KEYCODE_VOLUME_UP -> resolveVolume(EdgeDirection.Prev, config)
            KEYCODE_VOLUME_DOWN -> resolveVolume(EdgeDirection.Next, config)
            else -> null
        } ?: return null

        // 旧版口径：未开「长按翻页」时，连发（repeatCount>0）不进翻页通道，忽略长按重复。
        if (command is PageCommand.Turn && repeatCount > 0 && !config.keyPageOnLongPress) return null
        return command
    }

    /**
     * 解析媒体键（耳机线控 / 蓝牙遥控）。
     * 上一曲 → 上翻，下一曲 → 下翻；播放暂停 / 线控键 → 朗读切换；其余媒体键返回 null。
     */
    fun resolveMediaKey(keyCode: Int): PageCommand? = when (keyCode) {
        KEYCODE_MEDIA_PREVIOUS -> PageCommand.Turn(EdgeDirection.Prev)
        KEYCODE_MEDIA_NEXT -> PageCommand.Turn(EdgeDirection.Next)
        KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_HEADSETHOOK -> PageCommand.ToggleReadAloud
        else -> null
    }

    private fun resolveVolume(direction: EdgeDirection, config: PageKeyConfig): PageCommand? {
        if (!config.volumeKeyEnabled) return null
        // 朗读播放中且未开「播放时音量键翻页」：音量键归朗读，不抢翻页。
        if (config.readAloudPlaying && !config.volumeKeyPageOnPlay) return null
        return PageCommand.Turn(direction)
    }
}
