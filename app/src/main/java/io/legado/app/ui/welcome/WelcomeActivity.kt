package io.legado.app.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import io.legado.app.BuildConfig
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityWelcomeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.fullScreen
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import io.legado.app.utils.windowSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    override val binding by viewBinding(ActivityWelcomeBinding::inflate)
    private var startMainJob: Job? = null

    private val broughtToFront: Boolean
        get() = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0

    override fun shouldShowWindowBackground(): Boolean =
        getPrefInt(PreferKey.welcomeShowTime, 500) != 0 && !broughtToFront

    override fun shouldCreateContentView(): Boolean {
        if (broughtToFront || getPrefInt(PreferKey.welcomeShowTime, 500) == 0) {
            if (!broughtToFront) startMainActivity()
            finish()
            return false
        }
        return true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.tvBuild.text = "构建 ${BuildConfig.VERSION_CODE} · 第7批"

        if (broughtToFront) {
            // 避免从桌面启动程序后，会重新实例化入口类的activity
            finish()
        } else {
            val welcomeShowTime = getPrefInt(PreferKey.welcomeShowTime, 500)
            if (welcomeShowTime == 0) {
                startMainActivity()
            } else {
                // 整段编排 2.8s，墨滴→炸裂→回聚→渗出→远山→金线→标语
                val animMs = 2800L
                binding.root.post { startSplashAnim(animMs) }
                startMainJob = lifecycleScope.launch {
                    delay(animMs + 150L)
                    startMainActivity()
                }
            }
        }
        binding.vwTitleLine.setBackgroundColor(0xFF2A2320.toInt())
        binding.tvTitle.setTextColor(0xFF2A2320.toInt())
    }

    override fun setupSystemBar() {
        fullScreen()
        setStatusBarColorAuto(backgroundColor, true, fullScreen)
        upNavigationBarColor()
    }

    override fun finish() {
        startMainJob?.cancel()
        startMainJob = null
        runCatching { binding.vInk.stop() }
        runCatching { binding.vShine.stop() }
        runCatching { binding.vSplash.stop() }
        super.finish()
    }

    /** 单 View 整段编排：旧版三件套（墨晕/金线/竖线书名）全部让位 */
    private fun startSplashAnim(durationMs: Long) {
        binding.tvLegado.visibility = View.GONE
        binding.vInk.visibility = View.GONE
        binding.vShine.visibility = View.GONE
        binding.vSplash.start(durationMs)
    }

    override fun upBackgroundImage() {
        val isDarkTheme = ThemeConfig.getTheme() == Theme.Dark
        val showText = if (isDarkTheme) AppConfig.welcomeShowTextDark else AppConfig.welcomeShowText
        binding.tvLegado.visible(showText)
        if (getPrefBoolean(PreferKey.customWelcome)) {
            kotlin.runCatching {
                when (ThemeConfig.getTheme()) {
                    Theme.Dark -> {
                        getPrefString(PreferKey.welcomeImageDark)?.let { path ->
                            if (path.endsWith(".9.png")) {
                                BitmapUtils.decodeNinePatchDrawable(path)?.let {
                                    window.decorView.background = it
                                }
                            } else {
                                val size = windowManager.windowSize
                                BitmapUtils.decodeBitmap(path, size.widthPixels, size.heightPixels)?.let {
                                    window.decorView.background = it.toDrawable(resources)
                                }
                            }
                        }
                        return
                    }
                    else -> {
                        getPrefString(PreferKey.welcomeImage)?.let { path ->
                            if (path.endsWith(".9.png")) {
                                BitmapUtils.decodeNinePatchDrawable(path)?.let {
                                    window.decorView.background = it
                                }
                            } else {
                                val size = windowManager.windowSize
                                BitmapUtils.decodeBitmap(path, size.widthPixels, size.heightPixels)?.let {
                                    window.decorView.background = it.toDrawable(resources)
                                }
                            }
                        }
                        return
                    }
                }
            }
        }
        super.upBackgroundImage()
    }

    private fun startMainActivity() {
        startActivity<MainActivity>()
        if (getPrefBoolean(PreferKey.defaultToRead) && appDb.bookDao.lastReadBook != null) {
            startActivity<ReadBookActivity>()
        }
        finish()
    }

}

class Launcher1 : WelcomeActivity()
class Launcher2 : WelcomeActivity()
class Launcher3 : WelcomeActivity()
class Launcher4 : WelcomeActivity()
class Launcher5 : WelcomeActivity()
class Launcher6 : WelcomeActivity()
