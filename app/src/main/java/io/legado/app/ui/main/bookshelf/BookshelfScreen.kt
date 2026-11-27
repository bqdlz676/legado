package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.components.rememberInkGesture
import io.legado.app.ui.components.resolveEventsAllowed
import io.legado.app.ui.components.compat.toVisualState
import io.legado.app.ui.main.bookshelf.search.SearchOverlayParams
import io.legado.app.ui.main.search.SearchFallbackBody
import io.legado.app.ui.main.search.SearchHistoryPanel
import io.legado.app.ui.main.search.SearchLoadState
import io.legado.app.ui.main.search.SearchLoadingDots
import io.legado.app.ui.main.search.SearchPagingState
import io.legado.app.ui.main.search.SearchResultItem
import io.legado.app.ui.main.search.SearchResultList
import io.legado.app.ui.main.search.SearchTokens
import io.legado.app.ui.main.search.SearchUiState
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * B18：书架业务态 [ShelfState] → 墨触闸快照（纯函数，无副作用，普通 JVM 单测可断言）。
 *
 * 〔为什么书架不再自持第二套「能不能点」〕旧实现 ShelfState 不读契约 contentAlpha / eventsAllowed，
 * 按钮裸手势直达回调；本快照把业务态翻译成 [rememberInkGesture] 的入参，事件许可统一由手势闸 +
 * 契约枚举裁决，书架域不另立布尔。
 *
 * 口径对齐契约六态与 B17 分闸：
 *  - [ShelfState.INIT_LOADING] / [ShelfState.REFRESHING]：加载硬闸，禁点；
 *  - [ShelfState.DISABLED]：禁用硬闸，禁点；
 *  - [ShelfState.OFFLINE]：离线闸，容器本体禁点（契约 OFFLINE.eventsAllowed=false）；
 *  - [ShelfState.EMPTY]：空数据沿用旧口径可点（契约 EMPTY.eventsAllowed=true）；
 *  - [ShelfState.ERROR]：重试是错误恢复入口，[recoveryEntry] 时按 B17 独立入口放行自身点击；
 *  - [ShelfState.CONTENT]：常态放行。
 */
internal data class ShelfGate(
    val enabled: Boolean,
    val loading: Boolean,
    val empty: Boolean,
    val offline: Boolean,
    val standaloneInteractive: Boolean,
)

internal fun shelfGateOf(state: ShelfState, recoveryEntry: Boolean): ShelfGate = when (state) {
    ShelfState.INIT_LOADING, ShelfState.REFRESHING -> ShelfGate(
        enabled = false,
        loading = true,
        empty = false,
        offline = false,
        standaloneInteractive = false,
    )

    ShelfState.ERROR -> ShelfGate(
        enabled = true,
        loading = false,
        empty = false,
        offline = false,
        standaloneInteractive = recoveryEntry,
    )

    ShelfState.DISABLED -> ShelfGate(
        enabled = false,
        loading = false,
        empty = false,
        offline = false,
        standaloneInteractive = false,
    )

    ShelfState.OFFLINE -> ShelfGate(
        enabled = true,
        loading = false,
        empty = false,
        offline = true,
        standaloneInteractive = false,
    )

    ShelfState.EMPTY -> ShelfGate(
        enabled = true,
        loading = false,
        empty = true,
        offline = false,
        standaloneInteractive = false,
    )

    ShelfState.CONTENT -> ShelfGate(
        enabled = true,
        loading = false,
        empty = false,
        offline = false,
        standaloneInteractive = false,
    )
}

/**
 * 落文 · U4 书架页入口 Composable（切片-10）。
 *
 * 纯展示层：书籍数据、数据库订阅、跳转动作由接入层（Fragment / ViewModel）注入，
 * 本子任务只负责画面、动效、状态兜底。共享导航接线留主控统一 merge。
 *
 * @param state 组件状态机（六态 + 离线）
 * @param books 已换算好的展示模型列表
 * @param newBookCount 顶部横幅「发现 N 本新书」计数
 * @param onOpenBook 点开书卷
 * @param onImport 导入本地书 / 书单
 * @param onDeleteBook 卡片碎墨消散后的删除回调
 * @param onRetry 错误态重试
 * @param searchUiState 既有搜索栈整页态（历史 / 热门 / 联网结果 / 分页），浮层只读
 * @param searchRequest 跨页检索请求（发现页 / 书详情 / 书源管理 / 分享等改道投递）；
 *  非空时打开浮层并回填初始词 / 作用域，序号变化重复触发
 * @param onAttachSearch 浮层打开时挂接搜索 ViewModel（幂等），透传初始词 / 作用域
 * @param onDetachSearch 浮层关闭时释放搜索 ViewModel 的双路流收集并暂停检索模型；
 *  仅在「曾打开过」之后的关闭分支触发，首帧关闭态不调用
 * @param onQueryChange 输入词变化（透传检索栈 QueryChange）
 * @param onSubmitSearch 提交检索（回车 / 词条点选）
 * @param onPickHistory 点选历史 / 热门词条
 * @param onRemoveHistory 删除单条历史
 * @param onClearHistory 清空全部历史
 * @param onClearQuery 清空关键词并复位分页态
 * @param onOpenSearchItem 点开一条检索结果（本地书卷 / 书源未藏书卷分流）
 * @param onRetrySearch 离线 / 失败态重试
 * @param onLoadMore 滚动到底加载下一页
 * @param onRetryLoadMore 分页失败脚注重试
 */
@Composable
fun BookshelfScreen(
    state: ShelfState,
    books: List<ShelfBookUi>,
    newBookCount: Int,
    onOpenBook: (ShelfBookUi) -> Unit,
    onImport: () -> Unit,
    onDeleteBook: (ShelfBookUi) -> Unit,
    onRetry: () -> Unit,
    searchUiState: SearchUiState,
    searchRequest: ShelfSearchRequest? = null,
    onAttachSearch: (String?, String?) -> Unit,
    onDetachSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onPickHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearQuery: () -> Unit,
    onOpenSearchItem: (SearchResultItem) -> Unit,
    onRetrySearch: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    scrollTopSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyGridState()
    // 外部回顶指令（双击底部书柜 tab）：信号自增后滚回首项；首帧 0 不触发。
    LaunchedEffect(scrollTopSignal) {
        if (scrollTopSignal > 0) listState.animateScrollToItem(0)
    }
    // 列表滚动驱动的 camera parallax：卡片随滚动微前倾
    val parallax by remember(listState) {
        derivedStateOf {
            (listState.firstVisibleItemScrollOffset / 500f).coerceIn(0f, 1f)
        }
    }

    // Q441 陀螺仪视差：全页共享一对平滑 pitch/roll；开关偏好落 AppConfig，默认开。
    var parallaxOn by remember { mutableStateOf(AppConfig.shelfParallaxEnabled) }
    val parallaxState = rememberShelfParallaxState(enabled = parallaxOn)
    val parallaxScope = rememberCoroutineScope()
    var parallaxResumeJob by remember { mutableStateOf<Job?>(null) }
    // 手指滑动期间暂停视差，松手 400ms 后恢复：防手势与陀螺仪同时作用产生抖动。
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            parallaxResumeJob?.cancel()
            parallaxState.setTouchPaused(true)
        } else {
            parallaxResumeJob = parallaxScope.launch {
                delay(ShelfParallaxTokens.RESUME_DELAY_MS)
                parallaxState.resumeFromTouch()
            }
        }
    }
    val onToggleParallax: () -> Unit = {
        val next = !parallaxOn
        AppConfig.shelfParallaxEnabled = next
        parallaxOn = next
    }

    var bannerDismissed by remember { mutableStateOf(false) }
    // D25：书架页下拉搜索浮层开合状态（F-13，纯 JVM 状态机）；检索整页态（历史 / 热门 /
    // 联网结果 / 分页）由既有搜索栈 [SearchUiState] 单源供给，浮层不另立第二套检索状态。
    var searchState by remember { mutableStateOf(ShelfSearch.closed) }
    val overlayBody = ShelfSearch.bodyOf(searchState, searchUiState)
    // 浮层首次打开时挂接既有搜索 ViewModel（首建检索模型 + 历史 / 热门双路流订阅）；
    // 重复打开走 VM 的 attach 幂等分支，不重建模型、不重复订阅。关闭分支在「曾打开过」
    // 之后才释放收集：首帧关闭态不触发 detach，避免冷启空挂接后立刻被取消。
    var hasOpenedOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(searchState.isOpen) {
        // 守卫分支判定收成纯函数 [ShelfSearch.overlayTransition]；本 effect 只按结果派发回调。
        val transition = ShelfSearch.overlayTransition(searchState.isOpen, hasOpenedOverlay)
        hasOpenedOverlay = transition.hasOpened
        when (transition.action) {
            ShelfSearch.LifecycleAction.Attach -> onAttachSearch(null, null)
            ShelfSearch.LifecycleAction.Detach -> onDetachSearch()
            ShelfSearch.LifecycleAction.Idle -> Unit
        }
    }
    // 跨页检索改道：外部页面带词 / 作用域投递请求时打开浮层、回填关键词；
    // 初始词非空由 VM.attach 内部自动提交检索，这里不重复 submit。
    LaunchedEffect(searchRequest?.seq) {
        val request = searchRequest ?: return@LaunchedEffect
        searchState = ShelfSearch.updateQuery(
            ShelfSearch.open(searchState),
            request.key.orEmpty(),
        )
        onAttachSearch(request.key, request.scope)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ShelfTokens.bgPage),
    ) {
        // 底部金色微粒场（衬在卡片下层，不遮挡文字）
        GoldParticleField(modifier = Modifier.fillMaxSize())

        when (state) {
            ShelfState.INIT_LOADING -> SkeletonShelf()

            ShelfState.ERROR -> StateFallback(
                title = "卷册读取失败",
                sub = "书架数据没能取回来，可重试一次",
                actionText = "重试",
                onAction = onRetry,
                gate = shelfGateOf(ShelfState.ERROR, recoveryEntry = true),
            )

            ShelfState.DISABLED -> StateFallback(
                title = "书架暂不可用",
                sub = "权限或存储尚未就绪，稍后再试",
                actionText = "",
                onAction = {},
                gate = shelfGateOf(ShelfState.DISABLED, recoveryEntry = false),
            )

            ShelfState.OFFLINE -> OfflineShelf(
                listState = listState,
                books = books,
                parallax = parallax,
                parallaxState = parallaxState,
                parallaxOn = parallaxOn,
                onToggleParallax = onToggleParallax,
                onOpenBook = onOpenBook,
                onDeleteBook = onDeleteBook,
                onImport = onImport,
                onSearch = { searchState = ShelfSearch.open(searchState) },
            )

            else -> ShelfGrid(
                listState = listState,
                books = books,
                parallax = parallax,
                parallaxState = parallaxState,
                parallaxOn = parallaxOn,
                onToggleParallax = onToggleParallax,
                bannerDismissed = bannerDismissed,
                newBookCount = newBookCount,
                refreshing = state == ShelfState.REFRESHING,
                onBannerDismiss = { bannerDismissed = true },
                onImport = onImport,
                onOpenBook = onOpenBook,
                onDeleteBook = onDeleteBook,
                onSearch = { searchState = ShelfSearch.open(searchState) },
            )
        }

        // D25：下拉搜索浮层盖在书架上层；点 scrim / 下滑 / 选书后收起。
        if (overlayBody != ShelfSearch.OverlayBody.HIDDEN) {
            ShelfSearchOverlay(
                body = overlayBody,
                query = searchState.query,
                search = searchUiState,
                onQueryChange = {
                    searchState = ShelfSearch.updateQuery(searchState, it)
                    onQueryChange(it)
                },
                onSubmit = onSubmitSearch,
                onClose = {
                    searchState = ShelfSearch.close(searchState)
                    onClearQuery()
                },
                onPickHistory = onPickHistory,
                onRemoveHistory = onRemoveHistory,
                onClearHistory = onClearHistory,
                onClearQuery = {
                    searchState = ShelfSearch.updateQuery(searchState, "")
                    onClearQuery()
                },
                onOpenItem = { item ->
                    onOpenSearchItem(item)
                    searchState = ShelfSearch.close(searchState)
                    onClearQuery()
                },
                onRetry = onRetrySearch,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
            )
        }
    }
}

@Composable
private fun ShelfGrid(
    listState: LazyGridState,
    books: List<ShelfBookUi>,
    parallax: Float,
    parallaxState: ShelfParallaxState,
    parallaxOn: Boolean,
    onToggleParallax: () -> Unit,
    bannerDismissed: Boolean,
    newBookCount: Int,
    refreshing: Boolean,
    onBannerDismiss: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (ShelfBookUi) -> Unit,
    onDeleteBook: (ShelfBookUi) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 28.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 页眉：印信 + 书名 + 搜索入口 + 册数
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShelfHeader(
                bookCount = books.size,
                onSearch = onSearch,
                parallaxOn = parallaxOn,
                parallaxAvailable = parallaxState.available,
                onToggleParallax = onToggleParallax,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // 顶部横幅：发现 N 本新书 · 导入（可右滑关闭）
        if (!bannerDismissed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                NewBookBanner(
                    newBookCount = newBookCount,
                    onImport = onImport,
                    onDismiss = onBannerDismiss,
                )
            }
        }

        if (refreshing) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RefreshingIndicator()
            }
        }

        if (books.isEmpty()) {
            if (!refreshing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyShelf(onImport = onImport)
                }
            } else {
                // 刷新中且暂无缓存：先铺骨架，不留白
                itemsIndexed(items = List(6) { it }) { _, _ ->
                    SkeletonCard(alpha = 0.5f)
                }
            }
            return@LazyVerticalGrid
        }

        itemsIndexed(items = books, key = { _, b -> b.id }) { index, book ->
            ShelfBookCard(
                book = book,
                index = index,
                parallax = parallax,
                parallaxState = parallaxState,
                onOpen = onOpenBook,
                onDelete = onDeleteBook,
            )
        }
    }
}

@Composable
private fun ShelfHeader(
    bookCount: Int,
    onSearch: () -> Unit,
    parallaxOn: Boolean,
    parallaxAvailable: Boolean,
    onToggleParallax: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 朱砂印
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ShelfTokens.textAccent),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "藏",
                    style = TextStyle(
                        color = ShelfTokens.bgPage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ShelfTokens.serifTitle,
                    ),
                )
            }
            BasicText(
                text = "落文书架",
                style = TextStyle(
                    color = ShelfTokens.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = ShelfTokens.titleWeight,
                    fontFamily = ShelfTokens.serifTitle,
                ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Q441：陀螺仪视差两态开关——暖金=开启随倾斜浮动，灰=关闭卡片静止；
            // 无传感器设备时置灰不可点（parallaxAvailable=false）。
            ParallaxToggleButton(
                active = parallaxOn,
                enabled = parallaxAvailable,
                onToggle = onToggleParallax,
            )
            // D25：搜索入口只开这一处（书架页下拉浮层），不再跳独立搜索页。
            SearchGlyphButton(onSearch)
            BasicText(
                text = "共 ${bookCount} 卷",
                style = TextStyle(
                    color = ShelfTokens.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = ShelfTokens.serifBody,
                ),
            )
        }
    }
}

/** 页眉放大镜按钮：古铜描边圆环 + 斜柄，墨触无 ripple。 */
@Composable
private fun SearchGlyphButton(onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSearch() })
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val ring = 6.dp.toPx()
            val stroke = 1.4.dp.toPx()
            val cx = center.x - ring * 0.25f
            val cy = center.y - ring * 0.25f
            drawCircle(
                color = ShelfTokens.brandCopper,
                radius = ring,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            val handle = androidx.compose.ui.geometry.Offset(
                cx + ring * 0.72f,
                cy + ring * 0.72f,
            )
            drawLine(
                color = ShelfTokens.brandCopper,
                start = handle,
                end = androidx.compose.ui.geometry.Offset(
                    handle.x + 5.dp.toPx(),
                    handle.y + 5.dp.toPx(),
                ),
                strokeWidth = stroke,
            )
        }
    }
}

/**
 * Q441 视差开关按钮：右上角小「📱」两态——暖金=开启，云灰=关闭 / 无传感器置灰。
 * 为什么用 BasicText 字形：规格指定 📱 图标；配色只取书架 token，不用 Material 蓝紫。
 * @param enabled false（无传感器）时点击不响应，保持卡片静止，不报错。
 */
@Composable
private fun ParallaxToggleButton(
    active: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        active && enabled -> ShelfTokens.brandGold
        else -> ShelfTokens.textTertiary
    }
    Box(
        modifier = modifier
            .size(30.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(onTap = { onToggle() })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "📱",
            style = TextStyle(color = tint, fontSize = 15.sp),
        )
    }
}

/** 顶部横幅：发现 N 本新书 · 导入；右滑超过阈值即关闭。 */
@Composable
private fun NewBookBanner(
    newBookCount: Int,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .clip(RoundedCornerShape(12.dp))
            .background(ShelfTokens.bgSurface)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 120.dp.toPx()) onDismiss() else offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, drag ->
                        offsetX = (offsetX + drag).coerceIn(0f, size.width.toFloat())
                    },
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(ShelfTokens.brandGold, RoundedCornerShape(50)),
                )
                BasicText(
                    text = "发现 ${newBookCount} 本新书",
                    style = TextStyle(
                        color = ShelfTokens.textPrimary,
                        fontSize = 14.sp,
                        fontFamily = ShelfTokens.serifBody,
                    ),
                )
            }
            ShelfInkButton(text = "导入", accent = true, onClick = onImport)
        }
    }
}

/**
 * 加载指示器（切片-15）：三个竖排云灰小点依次亮灭，600ms 循环，不放菊花。
 */
@Composable
private fun RefreshingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shelf-refresh")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Restart),
        label = "shelf-refresh-dots",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        repeat(3) { index ->
            val phase = (sweep + index * 0.33f) % 1f
            val alpha = 0.25f + 0.75f * (1f - kotlin.math.abs(phase - 0.5f) * 2f)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .padding(vertical = 3.dp)
                    .background(
                        ShelfTokens.brandGold.copy(alpha = alpha),
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

/** 空书架：墨滴 + 案上无书 + 导入入口，不允许空白态。 */
@Composable
private fun EmptyShelf(
    onImport: () -> Unit,
    offline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 一滴落墨
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(50))
                .background(ShelfTokens.textPrimary.copy(alpha = 0.85f)),
        )
        BasicText(
            text = "案上无书",
            style = TextStyle(
                color = ShelfTokens.textPrimary,
                fontSize = 22.sp,
                fontWeight = ShelfTokens.titleWeight,
                fontFamily = ShelfTokens.serifTitle,
                textAlign = TextAlign.Center,
            ),
        )
        BasicText(
            text = "导入 txt / epub，或从书源发现新书",
            style = TextStyle(
                color = ShelfTokens.textSecondary,
                fontSize = 13.sp,
                fontFamily = ShelfTokens.serifBody,
                textAlign = TextAlign.Center,
            ),
        )
        ShelfInkButton(
            text = "导入书卷",
            accent = true,
            onClick = onImport,
            empty = true,
            offline = offline,
        )
    }
}

@Composable
private fun OfflineShelf(
    listState: LazyGridState,
    books: List<ShelfBookUi>,
    parallax: Float,
    parallaxState: ShelfParallaxState,
    parallaxOn: Boolean,
    onToggleParallax: () -> Unit,
    onOpenBook: (ShelfBookUi) -> Unit,
    onDeleteBook: (ShelfBookUi) -> Unit,
    onImport: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 离线提示条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 16.dp, 0.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ShelfTokens.bgOverlay)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(ShelfTokens.brandGold, RoundedCornerShape(50)),
            )
            BasicText(
                text = "离线中 · 仅显示本地缓存，暂不可联网更新",
                style = TextStyle(
                    color = ShelfTokens.bgPage,
                    fontSize = 12.sp,
                    fontFamily = ShelfTokens.serifBody,
                ),
            )
        }
        if (books.isEmpty()) {
            EmptyShelf(onImport = onImport, offline = true, modifier = Modifier.padding(top = 24.dp))
        } else {
            ShelfGrid(
                listState = listState,
                books = books,
                parallax = parallax,
                parallaxState = parallaxState,
                parallaxOn = parallaxOn,
                onToggleParallax = onToggleParallax,
                bannerDismissed = true,
                newBookCount = 0,
                refreshing = false,
                onBannerDismiss = {},
                onImport = onImport,
                onOpenBook = onOpenBook,
                onDeleteBook = onDeleteBook,
                onSearch = onSearch,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StateFallback(
    title: String,
    sub: String,
    actionText: String,
    onAction: () -> Unit,
    gate: ShelfGate,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = ShelfTokens.textPrimary,
                fontSize = 20.sp,
                fontWeight = ShelfTokens.titleWeight,
                fontFamily = ShelfTokens.serifTitle,
            ),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = sub,
            style = TextStyle(
                color = ShelfTokens.textSecondary,
                fontSize = 13.sp,
                fontFamily = ShelfTokens.serifBody,
                textAlign = TextAlign.Center,
            ),
        )
        if (actionText.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            ShelfInkButton(
                text = actionText,
                accent = true,
                onClick = onAction,
                enabled = gate.enabled,
                loading = gate.loading,
                empty = gate.empty,
                offline = gate.offline,
                standaloneInteractive = gate.standaloneInteractive,
            )
        }
    }
}

/** 骨架屏：细金线 + 蒙层呼吸，首帧加载兜底。 */
@Composable
private fun SkeletonShelf(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shelf-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shelf-skeleton-alpha",
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(items = List(6) { it }) { _, _ -> SkeletonCard(alpha = alpha) }
    }
}

@Composable
private fun SkeletonCard(alpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(0.66f)
            .clip(RoundedCornerShape(ShelfTokens.CARD_RADIUS_DP.dp))
            .background(ShelfTokens.bgSurface.copy(alpha = alpha)),
    )
}

/**
 * 墨触按钮（InkButton 基类的 Compose 自绘版）：
 * 按下泛朱砂 0→8%、120ms 回落；不用 Material ripple。
 *
 * B18：点击一律走 [rememberInkGesture] 墨触闸，不再裸 [detectTapGestures]；
 * 许可由契约 [io.legado.app.ui.components.contract.ComponentVisualState.eventsAllowed]
 * 与 [resolveEventsAllowed] 裁决。不可点时底 / 字切云灰档并按 contentAlpha 压淡，
 * 不允许「看起来能点、点不动」的静默失效。
 */
@Composable
private fun ShelfInkButton(
    text: String,
    accent: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    empty: Boolean = false,
    offline: Boolean = false,
    standaloneInteractive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val gesture = rememberInkGesture(
        enabled = enabled,
        loading = loading,
        empty = empty,
        offline = offline,
        standaloneInteractive = standaloneInteractive,
        onClick = onClick,
    )
    val visual = gesture.interaction.resolve().toVisualState()
    val eventsAllowed = resolveEventsAllowed(
        visual = visual,
        standaloneInteractive = standaloneInteractive,
        enabled = enabled,
        loading = loading,
    )
    val pressed = gesture.interaction.pressed
    val bg = when {
        !eventsAllowed -> ShelfTokens.bgSurface
        accent -> ShelfTokens.textAccent
        else -> ShelfTokens.bgSurface
    }
    val fg = when {
        !eventsAllowed -> ShelfTokens.textTertiary
        accent -> ShelfTokens.bgPage
        else -> ShelfTokens.textPrimary
    }
    Box(
        modifier = modifier
            .then(gesture.gestureModifier)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .alpha(visual.contentAlpha)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = fg,
                fontSize = 13.sp,
                fontFamily = ShelfTokens.serifBody,
            ),
        )
        if (pressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ShelfTokens.textPrimary.copy(alpha = ShelfTokens.PRESS_ALPHA)),
            )
        }
    }
}

/**
 * D25 · 书架页下拉搜索浮层（F-13）。
 *
 * 触发点：页眉放大镜；收起手势：点遮罩 / 面板下滑超阈值 / 选中结果。
 * 面板分区由 [ShelfSearch.bodyOf] 纯函数推导；历史 / 热门 / 联网结果 / 分页全部复用既有
 * 搜索栈单源（[SearchUiState] 与其 ViewModel），浮层不复制热门三元回落或翻页逻辑。
 * 结果列表自身是 LazyColumn，超面板高度可内部滚动到底并触发加载更多，不再设硬限高裁切。
 */
@Composable
private fun ShelfSearchOverlay(
    body: ShelfSearch.OverlayBody,
    query: String,
    search: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onPickHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearQuery: () -> Unit,
    onOpenItem: (SearchResultItem) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 下滑收起阈值：垂直拖拽位移超过 80dp 即关闭，未达阈值回弹（dragY 仅作阈值累计）。
    var dragY by remember { mutableStateOf(0f) }
    // 面板最大高度取外观参数单源的屏高比例；列表区在该高度内滚动，不再用固定 dp 限高裁切条目。
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp *
        SearchOverlayParams.MAX_HEIGHT_RATIO).dp
    Box(modifier = modifier.fillMaxSize()) {
        // 半透明墨色遮罩：点按收起浮层。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ShelfTokens.bgOverlay)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClose() })
                },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxPanelHeight)
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(ShelfTokens.bgSurface)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > 80.dp.toPx()) onClose()
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                        onVerticalDrag = { _, drag ->
                            dragY = (dragY + drag).coerceIn(0f, size.height.toFloat())
                        },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ShelfTokens.textPrimary,
                        fontSize = 16.sp,
                        fontFamily = ShelfTokens.serifBody,
                    ),
                    cursorBrush = SolidColor(ShelfTokens.textAccent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSubmit() },
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotBlank()) {
                    // 清空走既有搜索栈 ClearQuery 归约：复位关键词与分页态，回到空闲面板。
                    OverlayClearButton(onClear = onClearQuery)
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = if (body == ShelfSearch.OverlayBody.RESULTS) {
                    "在本地书卷与书源中匹配 ${search.visibleResults.size} 卷"
                } else {
                    SearchTokens.HINT_GUIDE
                },
                style = TextStyle(
                    color = ShelfTokens.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = ShelfTokens.serifBody,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.weight(1f, fill = false)) {
                when (body) {
                    ShelfSearch.OverlayBody.HISTORY_HOT -> Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        SearchHistoryPanel(
                            history = search.history,
                            hot = search.hot,
                            hotSource = search.hotSource,
                            onPick = onPickHistory,
                            onRemove = onRemoveHistory,
                            onClearHistory = onClearHistory,
                        )
                    }

                    ShelfSearch.OverlayBody.LOADING -> SearchLoadingDots()

                    ShelfSearch.OverlayBody.RESULTS -> SearchResultList(
                        results = search.visibleResults,
                        query = query,
                        blocking = false,
                        paging = search.paging,
                        hasMore = search.hasMore,
                        onItemClick = onOpenItem,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore,
                    )

                    ShelfSearch.OverlayBody.EMPTY ->
                        SearchFallbackBody(SearchTokens.EMPTY_MESSAGE)

                    ShelfSearch.OverlayBody.ERROR -> OverlayFallbackWithCache(
                        message = (search.loadState as? SearchLoadState.Error)
                            ?.message?.ifBlank { SearchTokens.ERROR_MESSAGE }
                            ?: SearchTokens.ERROR_MESSAGE,
                        offline = false,
                        cached = search.visibleResults,
                        onRetry = onRetry,
                        onOpenItem = onOpenItem,
                    )

                    ShelfSearch.OverlayBody.OFFLINE -> OverlayFallbackWithCache(
                        message = SearchTokens.OFFLINE_MESSAGE,
                        offline = true,
                        cached = search.visibleResults,
                        onRetry = onRetry,
                        onOpenItem = onOpenItem,
                    )

                    ShelfSearch.OverlayBody.HIDDEN -> Unit
                }
            }
        }
    }
}

/**
 * 离线 / 失败兜底：文案 + 重试；有缓存结果时在下方列出。缓存列表不可翻页——
 * 分页态空闲、收口、到底与重试回调均置空，口径与旧独立搜索页一致。
 */
@Composable
private fun OverlayFallbackWithCache(
    message: String,
    offline: Boolean,
    cached: List<SearchResultItem>,
    onRetry: () -> Unit,
    onOpenItem: (SearchResultItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SearchFallbackBody(
            message = message,
            offline = offline,
            error = !offline,
            actionLabel = SearchTokens.RETRY_LABEL,
            onAction = onRetry,
        )
        if (cached.isNotEmpty()) {
            SearchResultList(
                results = cached,
                query = "",
                blocking = true,
                paging = SearchPagingState.Idle,
                hasMore = false,
                onItemClick = onOpenItem,
                onLoadMore = {},
                onRetryLoadMore = {},
            )
        }
    }
}

/** 浮层清除叉：两笔斜线自绘，墨触无 ripple。 */
@Composable
private fun OverlayClearButton(onClear: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClear() })
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val stroke = 1.3.dp.toPx()
            val pad = 4.dp.toPx()
            drawLine(
                color = ShelfTokens.textTertiary,
                start = androidx.compose.ui.geometry.Offset(pad, pad),
                end = androidx.compose.ui.geometry.Offset(size.width - pad, size.height - pad),
                strokeWidth = stroke,
            )
            drawLine(
                color = ShelfTokens.textTertiary,
                start = androidx.compose.ui.geometry.Offset(pad, size.height - pad),
                end = androidx.compose.ui.geometry.Offset(size.width - pad, pad),
                strokeWidth = stroke,
            )
        }
    }
}
