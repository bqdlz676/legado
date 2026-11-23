package io.legado.app.ui.book.source.edit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 书源编辑页契约测试（Compose 重写后口径）。
 *
 * 旧壳（activity_book_source_edit.xml / BookSourceEditAdapter / Binding / TabLayout 编排）已拆除，
 * 本类静态断言指向新实现：SourceEditScreen（选项面板 / 标签页）、SourceEditViewModel（类型与选项装配）、
 * RssSourceEditAdapter（残留安全态回放）、CodeView（光标可见性机制）、FieldNavigationExtensions（RSS 字段导航）。
 * 断言强度对齐旧测试：类型双向映射、六个选项回填、安全态回放、光标可见性机制、主题栈隔离均保留为硬断言。
 */
class BookSourceEditLayoutTest {

    @Test
    fun `options use a compact card with one accessible header`() {
        val screen = File(repositoryRoot, SCREEN_PATH).readText()

        // 选项面板仍是单张圆角卡片，不引第三方 Material 卡片壳。
        assertTrue(screen.contains("clip(RoundedCornerShape(ComponentTokens.cardRadius))"))
        assertTrue(screen.contains(".background(ComponentTokens.bgSurface)"))
        // 整行表头可点（展开 / 收起），摘要列占满剩余宽度，箭头随展开态切换。
        assertTrue(screen.contains("clickable(onClick = onToggleExpand)"))
        assertTrue(screen.contains("Column(Modifier.weight(1f))"))
        assertTrue(screen.contains("if (expanded) \"−\" else \"+\""))
        // 摘要汇总六个选项开关的文案。
        listOf(
            "R.string.is_enable",
            "R.string.discovery",
            "R.string.auto_save_cookie",
            "R.string.review",
            "R.string.is_event_listener",
            "R.string.custom_button"
        ).forEach { label -> assertTrue("summary must include $label", screen.contains(label)) }
    }

    @Test
    fun `type and six existing switches start inside collapsed content`() {
        val screen = File(repositoryRoot, SCREEN_PATH).readText()

        // 面板初始收起（旧 XML options_content visibility=gone 的 Compose 对应物）。
        assertTrue(screen.contains("var optionsExpanded by remember { mutableStateOf(false) }"))
        // 类型 Chips 由 book_type 标签数组驱动，与旧 Spinner entries=@array/book_type 同源。
        assertTrue(screen.contains("typeLabels.forEachIndexed"))
        // 六个开关按旧顺序固化：启用 / 发现 / Cookie / 段评 / 事件监听 / 自定义按钮。
        val toggles = listOf(
            "copy(enabled = it)",
            "copy(enabledExplore = it)",
            "copy(enabledCookieJar = it)",
            "copy(enabledReview = it)",
            "copy(eventListener = it)",
            "copy(customButton = it)"
        )
        var previous = -1
        toggles.forEach { snippet ->
            val current = screen.indexOf(snippet, previous + 1)
            assertTrue("option toggle missing or out of order: $snippet", current > previous)
            previous = current
        }
    }

    @Test
    fun `activity refreshes summary and keeps every source option mapping`() {
        val viewModel = File(repositoryRoot, VIEW_MODEL_PATH).readText()
        val screen = File(repositoryRoot, SCREEN_PATH).readText()

        // 装配方向：类型下标 → BookSourceType（旧 getSource 映射的新落点）。
        listOf(
            "4 -> BookSourceType.video",
            "3 -> BookSourceType.file",
            "2 -> BookSourceType.image",
            "1 -> BookSourceType.audio",
            "else -> BookSourceType.default"
        ).forEach { mapping ->
            assertTrue("missing type mapping: $mapping", viewModel.contains(mapping))
        }
        // 回读方向：BookSourceType → 类型下标（旧 upSource 映射的新落点）。
        listOf(
            "BookSourceType.video -> 4",
            "BookSourceType.file -> 3",
            "BookSourceType.image -> 2",
            "BookSourceType.audio -> 1",
            "else -> 0"
        ).forEach { mapping ->
            assertTrue("missing type restore mapping: $mapping", viewModel.contains(mapping))
        }
        // 六个选项逐项回填到 BookSource / ReviewRule（旧 CHECK_BOX_BINDINGS + SAVE_MAPPINGS 对应物）。
        listOf(
            "source.enabled = options.enabled",
            "source.enabledExplore = options.enabledExplore",
            "source.enabledCookieJar = options.enabledCookieJar",
            "reviewRule.enabled = options.enabledReview",
            "source.eventListener = options.eventListener",
            "source.customButton = options.customButton"
        ).forEach { binding ->
            assertTrue("$binding must be restored on assemble", viewModel.contains(binding))
        }
        // 摘要随选项刷新（旧 updateOptionPanel 摘要口径保留在 Compose 摘要构建处）。
        listOf(
            "options.enabled",
            "options.enabledExplore",
            "options.enabledCookieJar",
            "options.enabledReview",
            "options.eventListener",
            "options.customButton"
        ).forEach { option -> assertTrue("summary must observe $option", screen.contains(option)) }
    }

    @Test
    fun `edit adapters replay current safety state after holder reuse`() {
        // RSS 编辑适配器仍是 RecyclerView 壳，安全态回放机制原样保留（断言强度同旧测试）。
        val rssAdapter = File(repositoryRoot, RSS_ADAPTER_PATH).readText()
        val stateUpdate = Regex(
            "(?m)^\\s*isUnsafeText\\s*=\\s*!presentation\\.isInlineEditable\\s*$"
        ).find(rssAdapter)?.range?.first ?: -1
        // 回放点取绑定方法内、状态更新之后的那次 applyInteractionState()；
        // 附加到窗口监听里的同名调用在文件中出现更早，不参与“更新后回放”的先后判定。
        val replayRegex = Regex("(?m)^\\s*applyInteractionState\\(\\)\\s*$")
        val stateReplay = if (stateUpdate >= 0) {
            replayRegex.find(rssAdapter, stateUpdate)?.range?.first ?: -1
        } else -1

        assertTrue(
            "RSS adapter must keep safety state on the holder",
            Regex("private\\s+var\\s+isUnsafeText\\s*=\\s*false").containsMatchIn(rssAdapter)
        )
        assertTrue(
            "RSS adapter must replay safety state when attached",
            Regex(
                "onViewAttachedToWindow\\([^)]*\\)\\s*\\{\\s*" +
                    "applyInteractionState\\(\\)"
            ).containsMatchIn(rssAdapter)
        )
        assertTrue("RSS adapter must update holder safety state while binding", stateUpdate >= 0)
        assertTrue("RSS adapter must replay safety state after updating it", stateReplay > stateUpdate)
        assertTrue(Regex("isFocusable\\s*=\\s*!isUnsafeText").containsMatchIn(rssAdapter))
        assertTrue(Regex("isFocusableInTouchMode\\s*=\\s*!isUnsafeText")
            .containsMatchIn(rssAdapter))

        // 书源侧已改为 Compose 列表，无 ViewHolder 复用；超长文本内联不可编辑的兜底收口到
        // EditSafety 阈值，超过阈值才走全屏编辑器文件传输（旧适配器安全态关注点的新落点）。
        val activity = File(repositoryRoot, ACTIVITY_PATH).readText()
        assertTrue(activity.contains("EditSafety.MAX_INLINE_TEXT_LENGTH"))
        assertTrue(activity.contains("CodeTextTransfer.write(this, it)"))
        assertTrue(activity.contains("CodeTextTransfer.delete(this, textPath)"))
    }

    @Test
    fun `large source fields use file transport for full editor`() {
        listOf(ACTIVITY_PATH, RSS_ACTIVITY_PATH).forEach { path ->
            val source = File(repositoryRoot, path).readText()

            assertTrue(
                "$path must opt into file transport",
                source.contains("putExtra(\"useTextFile\", true)")
            )
            assertTrue(
                "$path must write large input to a cache file",
                source.contains("CodeTextTransfer.write(this, it)")
            )
            assertTrue("$path must read a file result", source.contains("getStringExtra(\"textFile\")"))
            assertTrue(
                "$path must delete temporary files",
                source.contains("CodeTextTransfer.delete(this, path)")
            )
            assertFalse(
                "$path must not pass the source value inline",
                source.contains("putExtra(\"text\", editEntity.value.orEmpty())")
            )
        }
    }

    @Test
    fun `source editor keeps the caret visible after selection and layout changes`() {
        val codeView = File(repositoryRoot, CODE_VIEW_PATH).readText()
        val layoutManager = File(repositoryRoot, LAYOUT_MANAGER_PATH).readText()

        // CodeView 光标可见性机制原样保留（旧 initView 聚焦接线随旧 RecyclerView 壳拆除，不再断言）。
        assertTrue(codeView.contains("override fun onSelectionChanged(selStart: Int, selEnd: Int)"))
        assertTrue(codeView.contains("super.onSelectionChanged(selStart, selEnd)"))
        assertTrue(codeView.contains("override fun performClick(): Boolean"))
        assertTrue(codeView.contains("val handled = super.performClick()"))
        assertTrue(codeView.contains("removeCallbacks(selectionVisibilityRunnable)"))
        assertTrue(codeView.contains("post(selectionVisibilityRunnable)"))
        assertTrue(codeView.contains("!keepSelectionVisible || !isFocused"))
        assertTrue(codeView.contains("selectionVisibilityOffset("))
        assertTrue(codeView.contains("activeSelectionOffset"))
        assertTrue(codeView.contains("requestSelectionHandleVisible(offset)"))
        assertTrue(codeView.contains("bringPointIntoView(offset)"))
        assertTrue(codeView.contains(".coerceIn(0, text.length)"))
        assertFalse(codeView.contains("selectionStart == selectionEnd && selectionEnd >= 0"))
        assertTrue(codeView.contains("resolveSelectionHandleClearance"))
        assertFalse(codeView.contains("MotionEvent.ACTION_UP"))

        assertTrue(layoutManager.contains("override fun onRequestChildFocus("))
        assertTrue(layoutManager.contains("return true"))
        assertFalse(layoutManager.contains("requestChildRectangleOnScreen"))
    }

    @Test
    fun `ported panel stays independent from legadoT theme stack`() {
        val source = listOf(SCREEN_PATH, VIEW_MODEL_PATH, ACTIVITY_PATH)
            .joinToString("\n") { File(repositoryRoot, it).readText() }

        listOf(
            "Widget.Material3",
            "M3ColorScheme",
            "AppColorScheme",
            "FieldNavBar",
            "skin_"
        ).forEach { dependency ->
            assertFalse("unexpected dependency: $dependency", source.contains(dependency))
        }
    }

    @Test
    fun `source and auto task editors share main field tab navigation`() {
        // 书源编辑：Compose 标签页由 EditTab 枚举驱动。
        val screen = File(repositoryRoot, SCREEN_PATH).readText()
        assertTrue(screen.contains("TabRow(activeTab = activeTab"))
        assertTrue(screen.contains("EditTab.entries.forEach"))

        // RSS 编辑仍是旧壳，字段导航接线保留。
        val rssActivity = File(repositoryRoot, RSS_ACTIVITY_PATH).readText()
        assertTrue(rssActivity.contains("binding.fieldNav.bindFieldNavigation(binding.recyclerView)"))
        assertTrue(rssActivity.contains("binding.fieldNav.setFieldLabels(entities.map { it.hint })"))

        // 字段导航辅助函数（RecyclerView 与 NestedScrollView 两条同步路径）保留。
        val helper = File(repositoryRoot, FIELD_NAVIGATION_PATH).readText()
        assertTrue(helper.contains("recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE"))
        assertTrue(helper.contains("spanSizeLookup.getSpanIndex"))
        assertTrue(helper.contains("scrollView.scrollTo(0, field.top)"))
        assertTrue(helper.contains("fields.indexOfLast { it.top <= scrollY }"))
        assertTrue(helper.contains("!scrollView.canScrollVertically(1)"))
        assertFalse(helper.contains("AppColorScheme"))
    }

    private val repositoryRoot: File by lazy {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        generateSequence(File(userDir)) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }

    private companion object {
        const val SCREEN_PATH =
            "app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt"
        const val VIEW_MODEL_PATH =
            "app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditViewModel.kt"
        const val ACTIVITY_PATH =
            "app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt"
        const val RSS_ADAPTER_PATH =
            "app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditAdapter.kt"
        const val RSS_ACTIVITY_PATH =
            "app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditActivity.kt"
        const val CODE_VIEW_PATH =
            "app/src/main/java/io/legado/app/ui/widget/code/CodeView.kt"
        const val LAYOUT_MANAGER_PATH =
            "app/src/main/java/io/legado/app/ui/widget/recycler/NoChildScrollLinearLayoutManager.kt"
        const val FIELD_NAVIGATION_PATH =
            "app/src/main/java/io/legado/app/ui/widget/FieldNavigationExtensions.kt"
    }
}
