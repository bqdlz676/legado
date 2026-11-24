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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    var bannerDismissed by remember { mutableStateOf(false) }
    // D25：书架页下拉搜索浮层状态（F-13）；输入即查本地书架，不起独立搜索页。
    var searchState by remember { mutableStateOf(ShelfSearch.closed) }
    val filteredBooks by remember(books, searchState.query) {
        derivedStateOf { ShelfSearch.filter(books, searchState.query) }
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
            )

            ShelfState.DISABLED -> StateFallback(
                title = "书架暂不可用",
                sub = "权限或存储尚未就绪，稍后再试",
                actionText = "",
                onAction = {},
            )

            ShelfState.OFFLINE -> OfflineShelf(
                listState = listState,
                books = books,
                parallax = parallax,
                onOpenBook = onOpenBook,
                onDeleteBook = onDeleteBook,
                onImport = onImport,
                onSearch = { searchState = ShelfSearch.open(searchState) },
            )

            else -> ShelfGrid(
                listState = listState,
                books = books,
                parallax = parallax,
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
        if (searchState.isOpen) {
            ShelfSearchOverlay(
                query = searchState.query,
                results = filteredBooks,
                onQueryChange = { searchState = ShelfSearch.updateQuery(searchState, it) },
                onClose = { searchState = ShelfSearch.close(searchState) },
                onPick = { book ->
                    onOpenBook(book)
                    searchState = ShelfSearch.close(searchState)
                },
            )
        }
    }
}

@Composable
private fun ShelfGrid(
    listState: LazyGridState,
    books: List<ShelfBookUi>,
    parallax: Float,
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
private fun EmptyShelf(onImport: () -> Unit, modifier: Modifier = Modifier) {
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
        ShelfInkButton(text = "导入书卷", accent = true, onClick = onImport)
    }
}

@Composable
private fun OfflineShelf(
    listState: LazyGridState,
    books: List<ShelfBookUi>,
    parallax: Float,
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
            EmptyShelf(onImport = onImport, modifier = Modifier.padding(top = 24.dp))
        } else {
            ShelfGrid(
                listState = listState,
                books = books,
                parallax = parallax,
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
            ShelfInkButton(text = actionText, accent = true, onClick = onAction)
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
 */
@Composable
private fun ShelfInkButton(
    text: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val bg = if (accent) ShelfTokens.textAccent else ShelfTokens.bgSurface
    val fg = if (accent) ShelfTokens.bgPage else ShelfTokens.textPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
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
 * 触发点：页眉放大镜；收起手势：点遮罩 / 面板下滑超阈值 / 选中结果；
 * 输入即查（本地书名 + 作者），空关键词与无命中均给可读占位，不留白、不崩。
 */
@Composable
private fun ShelfSearchOverlay(
    query: String,
    results: List<ShelfBookUi>,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPick: (ShelfBookUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 下滑收起阈值：垂直拖拽位移超过 80dp 即关闭，未达阈值回弹（dragY 仅作阈值累计）。
    var dragY by remember { mutableStateOf(0f) }
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
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotBlank()) {
                    // onClear 语义＝清空关键词；该叉按钮即浮层内独立清空入口，故不传单空回调
                    OverlayClearButton(onClear = { onQueryChange("") })
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = if (query.isBlank()) {
                    "搜书名、作者，输入即查"
                } else {
                    "在本地书卷中匹配 ${results.size} 卷"
                },
                style = TextStyle(
                    color = ShelfTokens.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = ShelfTokens.serifBody,
                ),
            )
            Spacer(Modifier.height(10.dp))
            when {
                query.isBlank() -> OverlayHint("输入书名或作者，查找本地书卷")
                results.isEmpty() -> OverlayHint("未找到相关书卷，换个词试试")
                else -> Column(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    results.forEach { book ->
                        OverlayResultRow(book, query) { onPick(book) }
                    }
                }
            }
        }
    }
}

/**
 * 浮层结果行（卡片规格 §六 搜索结果卡）：56dp；书名 14sp 墨色，作者 10sp 副文，
 * 进度 10sp 云灰，来源「本地」8sp；命中词朱砂高亮。在线源标签归 D1~D7 检索流。
 */
@Composable
private fun OverlayResultRow(
    book: ShelfBookUi,
    query: String,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onPick() })
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = highlightMatch(book.title, query),
                style = TextStyle(
                    color = ShelfTokens.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = ShelfTokens.serifTitle,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    text = highlightMatch(book.author, query),
                    style = TextStyle(
                        color = ShelfTokens.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = ShelfTokens.serifBody,
                    ),
                )
                BasicText(
                    text = book.percentText,
                    style = TextStyle(
                        color = ShelfTokens.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = ShelfTokens.serifBody,
                    ),
                )
            }
        }
        BasicText(
            text = "本地",
            style = TextStyle(
                color = ShelfTokens.textSecondary,
                fontSize = 8.sp,
                fontFamily = ShelfTokens.serifBody,
            ),
        )
    }
}

/** 命中词朱砂高亮：忽略大小写匹配，保留原文大小写；无命中 / 空词时原样返回。 */
private fun highlightMatch(text: String, query: String): AnnotatedString {
    val key = query.trim()
    if (key.isEmpty()) return AnnotatedString(text)
    val start = text.lowercase().indexOf(key.lowercase())
    if (start < 0) return AnnotatedString(text)
    val end = start + key.length
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = ShelfTokens.textAccent)) {
            append(text.substring(start, end))
        }
        append(text.substring(end))
    }
}

/** 浮层空态 / 引导态：落墨点 + 一行云灰引导，不允许空白屏。 */
@Composable
private fun OverlayHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(50))
                .background(ShelfTokens.textPrimary.copy(alpha = 0.7f)),
        )
        BasicText(
            text = text,
            style = TextStyle(
                color = ShelfTokens.textSecondary,
                fontSize = 13.sp,
                fontFamily = ShelfTokens.serifBody,
                textAlign = TextAlign.Center,
            ),
        )
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
