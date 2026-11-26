@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.help.SourceSharePassphrase
import io.legado.app.help.SourceSharePassphraseImportPolicy
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.autoTask.ImportAutoTaskDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.ui.main.bookshelf.ShelfSearchHandoff
import io.legado.app.ui.main.shell.MainShell
import io.legado.app.ui.main.shell.ShellRestorePolicy
import io.legado.app.ui.main.shell.ShellTab
import io.legado.app.ui.main.shell.rememberMainShellState
import io.legado.app.utils.clearClip
import io.legado.app.utils.getClipText
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 落文 · 主壳宿主（B2）。
 *
 * 〔壳口径〕界面层全部由 Compose 主壳 [MainShell] 承接：三槽导航（书架 / 发现 / 设置）、
 * 磨砂底栏、槽位转场动画；本类只保留宿主职责——壳态挂载、返回 / 退出判定、口令导入、
 * 目录刷新观察者、销毁兜底。旧 ViewPager + Material BottomNavigationView 编排随本提交迁出，
 * 旧 Fragment / XML / 菜单物理清退归 B9 旧面清退批次（A3 先接后删）。
 *
 * 〔能力归属〕书源 / 书籍 / 备份能力仍在 [MainViewModel] 与对应业务域；壳内不读业务数据，
 * 能力迁出壳类归后续批次（见实现报告挂账条目）。
 */
@Suppress("PrivatePropertyName")
class MainActivity : AppCompatActivity(), MainViewModel.CallBack {

    private val viewModel: MainViewModel by viewModels()

    /** 双击退出间隔（ms）。 */
    private val exitInterval = EXIT_INTERVAL_MS
    private var exitTime: Long = 0

    /** 书架未读角标计数（LiveData 推送落点；壳内只显示，数据归书架域）。 */
    private var badgeCount by mutableStateOf(0)

    /** Activity 侧下发的目标槽位；仅活进程内显式请求（newIntent 检索改道）才同步进壳态。 */
    private var requestedTab: ShellTab = ShellTab.BOOKSHELF

    /**
     * 宿主显式请求代次（O1 恢复顺序锁）：0 = 首 composition，不覆盖 rememberSaveable 恢复态；
     * 活进程内 [switchTab] 自增后，同步回路才允许把请求槽位切进壳态。
     */
    private var shellRequestGeneration by mutableStateOf(0)

    /** 壳面刷新闸：偏好 / 菜单事件到达后自增，驱动重组重算可见槽位。 */
    private var shellRevision by mutableStateOf(0)

    private var lastPassphraseText: String? = null
    private var pendingPassphraseRead = false
    private var passphraseReadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTab = ShellTab.initialTab(AppConfig.defaultHomePage)
        setContent {
            shellRevision // 事件到达后驱动重组，重算可见槽位 / 角标
            val state = rememberMainShellState(initialTab = requestedTab)
            // O1 恢复顺序：首 composition（代次 0）不覆盖 rememberSaveable 恢复态；
            // 无保存态时默认槽已由 initialTab 兜底。仅活进程显式请求代次自增后才同步进壳态。
            LaunchedEffect(shellRequestGeneration) {
                if (ShellRestorePolicy.shouldApplyHostRequest(shellRequestGeneration)) {
                    state.switchTab(requestedTab.ordinal)
                }
            }
            MainShell(
                state = state,
                visibleTabs = visibleTabs(),
                badgeCount = badgeCount,
                reduceMotion = reduceMotion(),
                onExit = ::onShellExit,
            )
        }
        observeLiveBus()
        lifecycleScope.launch {
            viewModel.setActivityCallback(this@MainActivity)
            // 自动更新书源（延迟回落口径沿旧壳，不阻塞壳首帧）。
            delay(1000)
            viewModel.ruleSubsUp()
            scheduleSourceSharePassphraseRead(1500)
            val isAutoRefreshedBook = savedInstanceState?.getBoolean(KEY_AUTO_REFRESHED_BOOK) ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                delay(2000)
                viewModel.upAllBookToc()
            }
            delay(3000)
            viewModel.postLoad()
        }
        // 跨页检索改道：外部分享 / 其他页面经 Intent extras 带词进入时，切回书架槽打开浮层。
        handleSearchIntent(intent)
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSearchIntent(intent)
    }

    /**
     * 消费跨页检索 extras：有词 / 有范围时排队到 [ShelfSearchHandoff] 并切回书架槽。
     * 书架槽页面尚未接线（B3）时请求留在单例，由书架批次挂载位消费，避免外部唤起后浮层不弹。
     */
    private fun handleSearchIntent(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_SEARCH_KEY)
        val scope = intent?.getStringExtra(EXTRA_SEARCH_SCOPE)
        if (key.isNullOrBlank() && scope.isNullOrBlank()) return
        ShelfSearchHandoff.enqueue(key, scope)
        switchTab(0)
    }

    /**
     * 幂等切槽：已在目标槽位不重复切换、不起 Activity、不压栈；越界位置回落书架兜底（C3）。
     * 自增请求代次，驱动同步回路把显式请求切进壳态（首 composition 代次 0 不触发覆盖）。
     */
    private fun switchTab(position: Int): Boolean {
        if (position < 0) return false
        requestedTab = ShellTab.values().getOrElse(position) { ShellTab.BOOKSHELF }
        shellRequestGeneration++
        return true
    }

    override fun onResume() {
        super.onResume()
        if (SourceSharePassphraseImportPolicy.shouldScheduleOnResume(
                privacyPolicyOk = LocalConfig.privacyPolicyOk
            )
        ) {
            scheduleSourceSharePassphraseRead(500)
        }
    }

    override fun onPause() {
        super.onPause()
        pendingPassphraseRead = false
        passphraseReadGeneration++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingPassphraseRead && canAwaitPassphraseWindowFocus()) {
            readSourceSharePassphrase(200)
        } else if (hasFocus) {
            pendingPassphraseRead = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean(KEY_AUTO_REFRESHED_BOOK, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /** 壳书架槽请求退出：双击间隔判定；朗读暂停时直接 finish，否则回落后台。 */
    private fun onShellExit() {
        if (System.currentTimeMillis() - exitTime > exitInterval) {
            toastOnUi(R.string.double_click_exit)
            exitTime = System.currentTimeMillis()
        } else {
            if (BaseReadAloudService.pause) {
                finish()
            } else {
                moveTaskToBack(true)
            }
        }
    }

    private fun visibleTabs(): List<ShellTab> {
        // 发现页显隐仍走既有偏好（开关在 C1 配置域，本单不改动）；槽位集合固定书架 / 发现 / 设置。
        return if (AppConfig.showDiscovery) {
            ShellTab.defaultOrder
        } else {
            ShellTab.defaultOrder.filter { it != ShellTab.EXPLORE }
        }
    }

    /** 系统 reduce-motion（动画时长尺度为 0）→ 壳内转场 / 按压回落同步归零。 */
    private fun reduceMotion(): Boolean = try {
        val scale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    } catch (_: Throwable) {
        false
    }

    private fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            badgeCount = it
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            // 偏好变更后刷新可见槽位；旧底栏菜单重建 / 皮肤挂载随新壳收口，见挂账条目。
            shellRevision++
            upBottomBarSkin()
        }
        observeEvent<String>(EventBus.BOTTOM_BAR_SKIN) {
            upBottomBarSkin()
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
        observeEvent<List<Book>>(EventBus.UP_BOOKS_TOC) {
            viewModel.upToc(
                it,
                onlyUpdateRead = false,
                policy = TocUpdatePolicy.SKIP_PRE_DOWNLOAD,
                refreshBookInfo = true,
            )
        }
    }

    /**
     * 旧 Material 底栏皮肤挂载钩子：新 Compose 壳不走皮肤槽位；本方法保留为事件接收落点，
     * 皮肤外观迁移到组件层 frostedSurface 时随 B1 批次收口（N6 挂账），当前为空实现不留兼容层。
     */
    private fun upBottomBarSkin() {
        // 预留：组件层磨砂 / 皮肤定型后在此取 active 皮肤映射；当前不读旧底栏视图。
    }

    private fun scheduleSourceSharePassphraseRead(delayMillis: Long) {
        passphraseReadGeneration++
        pendingPassphraseRead = true
        if (hasWindowFocus()) {
            readSourceSharePassphrase(delayMillis, passphraseReadGeneration)
        }
    }

    private fun canAwaitPassphraseWindowFocus(): Boolean {
        return SourceSharePassphraseImportPolicy.canAwaitWindowFocus(
            privacyPolicyOk = LocalConfig.privacyPolicyOk,
            isFinishing = isFinishing,
            isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            isFragmentStateSaved = supportFragmentManager.isStateSaved,
        )
    }

    private fun readSourceSharePassphrase(
        delayMillis: Long,
        generation: Int = passphraseReadGeneration,
    ) {
        pendingPassphraseRead = false
        lifecycleScope.launch {
            delay(delayMillis)
            if (generation != passphraseReadGeneration) return@launch
            val hasWindowFocus = hasWindowFocus()
            if (!hasWindowFocus) {
                pendingPassphraseRead = canAwaitPassphraseWindowFocus()
            }
            if (!SourceSharePassphraseImportPolicy.canReadClipboard(
                    privacyPolicyOk = LocalConfig.privacyPolicyOk,
                    isFinishing = isFinishing,
                    isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                    isFragmentStateSaved = supportFragmentManager.isStateSaved,
                    hasWindowFocus = hasWindowFocus,
                )
            ) {
                return@launch
            }
            val text = getClipText()?.takeIf { it.isNotBlank() } ?: return@launch
            if (text == lastPassphraseText) return@launch
            when (val result = SourceSharePassphrase.decode(text)) {
                SourceSharePassphrase.DecodeResult.NotFound -> {
                    lastPassphraseText = null
                }

                SourceSharePassphrase.DecodeResult.Invalid -> {
                    lastPassphraseText = text
                    toastOnUi(R.string.shibboleth_invalid)
                }

                SourceSharePassphrase.DecodeResult.Expired -> {
                    lastPassphraseText = text
                    toastOnUi(R.string.shibboleth_expired)
                }

                is SourceSharePassphrase.DecodeResult.Success -> {
                    lastPassphraseText = text
                    clearClip()
                    val value = result.value
                    when (value.type) {
                        SourceSharePassphrase.Type.BOOK_SOURCE ->
                            showDialogFragment(ImportBookSourceDialog(value.url))

                        SourceSharePassphrase.Type.RSS_SOURCE ->
                            showDialogFragment(ImportRssSourceDialog(value.url))

                        SourceSharePassphrase.Type.DICT_RULE ->
                            showDialogFragment(ImportDictRuleDialog(value.url))

                        SourceSharePassphrase.Type.REPLACE_RULE ->
                            showDialogFragment(ImportReplaceRuleDialog(value.url))

                        SourceSharePassphrase.Type.TOC_RULE ->
                            showDialogFragment(ImportTxtTocRuleDialog(value.url))

                        SourceSharePassphrase.Type.TTS_RULE ->
                            showDialogFragment(ImportHttpTtsDialog(value.url))

                        SourceSharePassphrase.Type.AUTO_TASK ->
                            showDialogFragment(ImportAutoTaskDialog(value.url))
                    }
                }
            }
        }
    }

    override fun openImportUi(type: Int, source: String) {
        when (type) {
            0 -> showDialogFragment(ImportBookSourceDialog(source))
            1 -> showDialogFragment(ImportRssSourceDialog(source))
            2 -> showDialogFragment(ImportReplaceRuleDialog(source))
        }
    }

    companion object {
        /** 跨页检索改道 extras：初始关键词 / 搜索范围串（口径同 ShelfSearchRouter）。 */
        const val EXTRA_SEARCH_KEY = "shelfSearchKey"
        const val EXTRA_SEARCH_SCOPE = "shelfSearchScope"

        private const val EXIT_INTERVAL_MS: Long = 2000L
        private const val KEY_AUTO_REFRESHED_BOOK = "isAutoRefreshedBook"
    }
}
