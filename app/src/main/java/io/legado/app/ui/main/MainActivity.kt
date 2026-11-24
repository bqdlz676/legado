@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.doOnLayout
import androidx.core.view.get
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.BottomBarSkinManager
import io.legado.app.help.SourceSharePassphrase
import io.legado.app.help.SourceSharePassphraseImportPolicy
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.help.update.AppUpdate
import io.legado.app.help.update.isIgnoredAppUpdate
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.ui.autoTask.ImportAutoTaskDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.ui.main.bookshelf.InkShelfFragment
import io.legado.app.ui.main.explore.ExploreInkFragment
import io.legado.app.ui.main.my.MyInkFragment
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.clearClip
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getClipText
import io.legado.app.utils.isCreated
import io.legado.app.utils.imeHeight
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.hours

/**
 * 主界面
 */
@Suppress("PrivatePropertyName")
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener,
    MainViewModel.CallBack {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    // D25：底部导航固定三槽位 = 书架(0) · 发现(1) · 设置(2)；旧 RSS / 我的 / 画板槽位已下线
    private val idBookshelf = 0
    private val idExplore = 1
    private val idSettings = 2
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 3
    private val EXIT_INTERVAL = 2000L
    private val realPositions = arrayOf(idBookshelf, idExplore, idSettings)
    private val menuIdToSlot = linkedMapOf(
        R.id.menu_bookshelf to "bookshelf",
        R.id.menu_discovery to "home",
        R.id.menu_my_config to "settings",
    )
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private var onUpBooksBadgeView: BadgeView? = null
    private var lastPassphraseText: String? = null
    private var pendingPassphraseRead = false
    private var passphraseReadGeneration = 0

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        upBottomBarSkin()
        onBackPressedDispatcher.addCallback(this) {
            if (pagePosition != 0) {
                binding.viewPagerMain.currentItem = 0
                return@addCallback
            }
            (fragmentMap[getFragmentId(0)] as? InkShelfFragment)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
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
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //设置回调
            viewModel.setActivityCallback(this@MainActivity)
            //自动更新书源
            binding.viewPagerMain.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            scheduleSourceSharePassphraseRead(1500)
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
            binding.viewPagerMain.postDelayed(2000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
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

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf -> switchTab(0)
            R.id.menu_discovery -> switchTab(realPositions.indexOf(idExplore))
            R.id.menu_my_config -> switchTab(realPositions.indexOf(idSettings))
            else -> false
        }
    }

    /**
     * 幂等切 Tab：已在目标页则不重复切换、不重复建 Fragment；
     * 设置 Tab 只是 ViewPager 槽位切换，不向返回栈压入 Activity 条目（重复点击不重复入栈）。
     */
    private fun switchTab(position: Int): Boolean {
        if (position < 0) return false
        if (binding.viewPagerMain.currentItem != position) {
            binding.viewPagerMain.setCurrentItem(position, false)
        }
        return true
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? InkShelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreInkFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
        }
        bottomNavigationView.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val height = windowInsets.navigationBarHeight
            view.bottomPadding = height
            view.doOnLayout {
                val location = IntArray(2)
                view.getLocationInWindow(location)
                val keyboardTop = window.decorView.height - windowInsets.imeHeight
                // The pager already ends above the bottom navigation, even when IME covers it.
                viewPagerMain.bottomPadding = (location[1] - keyboardTop).coerceAtLeast(0)
                if (windowInsets.imeHeight > 0) viewPagerMain.doOnLayout {
                    currentFocus?.takeIf { it.onCheckIsTextEditor() }?.let { input ->
                        input.requestRectangleOnScreen(Rect(0, 0, input.width, input.height), true)
                    }
                }
            }
            windowInsets.inset(0, 0, 0, height)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
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

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? InkShelfFragment)?.upSort()
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            if (onUpBooksBadgeView == null) {
                onUpBooksBadgeView = binding.bottomNavigationView.addBadgeView(0)
            }
            onUpBooksBadgeView!!.setBadgeCount(it)
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                if (it) {
                    bottomNavigationView.menu.clear()
                    bottomNavigationView.inflateMenu(R.menu.main_bnv)
                    onUpBooksBadgeView = null
                }
                upBottomMenu()
                upBottomBarSkin()
                if (it) {
                    viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
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

    private fun upBottomMenu() {
        // 发现页显隐仍走既有偏好（开关在 C1 配置域，本单不改动）；槽位集合固定为书架 / 发现 / 设置。
        val showDiscovery = AppConfig.showDiscovery
        binding.bottomNavigationView.menu.let { menu ->
            menu.findItem(R.id.menu_discovery)?.isVisible = showDiscovery
        }
        var index = 0
        if (showDiscovery) {
            index++
            realPositions[index] = idExplore
        }
        index++
        realPositions[index] = idSettings
        bottomMenuCount = index + 1
        adapter.notifyDataSetChanged()
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
            isFragmentStateSaved = supportFragmentManager.isStateSaved
        )
    }

    private fun readSourceSharePassphrase(
        delayMillis: Long,
        generation: Int = passphraseReadGeneration
    ) {
        pendingPassphraseRead = false
        binding.viewPagerMain.postDelayed(delayMillis) {
            if (generation != passphraseReadGeneration) return@postDelayed
            val hasWindowFocus = hasWindowFocus()
            if (!hasWindowFocus) {
                pendingPassphraseRead = canAwaitPassphraseWindowFocus()
            }
            if (!SourceSharePassphraseImportPolicy.canReadClipboard(
                    privacyPolicyOk = LocalConfig.privacyPolicyOk,
                    isFinishing = isFinishing,
                    isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                    isFragmentStateSaved = supportFragmentManager.isStateSaved,
                    hasWindowFocus = hasWindowFocus
                )
            ) {
                return@postDelayed
            }
            val text = getClipText()?.takeIf { it.isNotBlank() } ?: return@postDelayed
            if (text == lastPassphraseText) return@postDelayed
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

    private fun upBottomBarSkin() {
        val skin = BottomBarSkinManager.active
        if (skin.isEmpty() || !BottomBarSkinManager.hasSkin(skin)) {
            binding.bottomNavigationView.applySkin(null, 0)
            return
        }
        val sizePx = 30.dpToPx()
        val map = HashMap<Int, StateListDrawable>()
        menuIdToSlot.forEach { (id, slot) ->
            BottomBarSkinManager.getStateDrawable(skin, slot, sizePx)?.let { map[id] = it }
        }
        binding.bottomNavigationView.applySkin(map, sizePx)
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }


            "my" -> binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idSettings), false)
        }
    }

    /**
     * 书柜只走 [InkShelfFragment] 一条路；旧的分组样式双路分流已拆除，
     * 位置槽位直接映射 fragment id，不再读旧样式偏好。
     */
    private fun getFragmentId(position: Int): Int = realPositions[position]

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
            val menuId = when (realPositions[position]) {
                idExplore -> R.id.menu_discovery
                idSettings -> R.id.menu_my_config
                else -> R.id.menu_bookshelf
            }
            binding.bottomNavigationView.menu.findItem(menuId)?.isChecked = true
        }

    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private fun getId(position: Int): Int {
            return getFragmentId(position)
        }

        override fun getItemPosition(any: Any): Int {
            val position = (any as MainFragmentInterface).position
                ?: return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idBookshelf && any is InkShelfFragment)
                || (fragmentId == idExplore && any is ExploreInkFragment)
                || (fragmentId == idSettings && any is MyInkFragment)
            ) {
                return POSITION_UNCHANGED
            }
            return POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            return when (getId(position)) {
                idBookshelf -> InkShelfFragment(position)
                idExplore -> ExploreInkFragment(position)
                else -> MyInkFragment(position)
            }
        }

        override fun getCount(): Int {
            return bottomMenuCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            return fragment
        }

    }

    override fun openImportUi(type:Int, source: String) {
        when (type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(source)
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(source)
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(source)
            )
        }
    }

}
