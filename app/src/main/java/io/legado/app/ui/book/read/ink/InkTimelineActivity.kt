package io.legado.app.ui.book.read.ink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.legado.app.ui.main.chrono.ChronoScreenHost

/**
 * 纪年时间轴宿主（气泡 [io.legado.app.help.bubble.Action.OpenTimeline] 消费方）。
 *
 * 承载既有 [ChronoScreenHost] 纪年对照屏：同年各政权横向对照 + 干支 + 距今；
 * 起始公元年由 Intent extra `year` 传入（负数 = 公元前），缺省交 ChronoScreen 取当日。
 * 本 Activity 只做壳装配，时间轴数据与渲染全部在 chrono 模块内，本层不复制其逻辑。
 */
class InkTimelineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val year = intent.getIntExtra(EXTRA_YEAR, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        setContent {
            ChronoScreenHost(initialYear = year)
        }
    }

    companion object {
        const val EXTRA_YEAR = "year"
        const val EXTRA_LABEL = "label"
    }
}
