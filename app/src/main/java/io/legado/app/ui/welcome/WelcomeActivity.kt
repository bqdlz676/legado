package io.legado.app.ui.welcome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.legado.app.ui.main.MainActivity

/**
 * 落文 · 启动动画宿主（ui/welcome 独占）。
 *
 * 旧版 View 三件套（墨晕 / 金线 / 竖线书名）全部废弃，本页由 [SplashScene] 纯 Compose 自绘。
 * 整段编排固定 2800ms，结束后进入书架；不做跳过按钮、不放进度条 / 转圈 / 版本号 / Logo，
 * 不检测网络、不播放声音（启动动画原文「不做的」）。
 *
 * 导航说明：最后阅读直达分支（旧版 lastReadBook → ReadBookActivity）属共享入口接线，
 * 留主控 merge 时接回；本类只负责动画收口后落 [MainActivity]。
 */
open class WelcomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 已在前台被桌面再次拉起：不重播动画，直接收口
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            SplashScene(onFinish = ::enterMain)
        }
    }

    private fun enterMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** 多图标入口占位：类名由 LauncherIconHelp 按 ComponentName 反射引用，不可改名 */
class Launcher1 : WelcomeActivity()
class Launcher2 : WelcomeActivity()
class Launcher3 : WelcomeActivity()
class Launcher4 : WelcomeActivity()
class Launcher5 : WelcomeActivity()
class Launcher6 : WelcomeActivity()
