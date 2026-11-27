package io.legado.app.ui.book.read.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.chrono.ContextAnchor

/**
 * 阅读页闭环入口（U5-A 对外 Compose 面）。
 *
 * 把 [InkReadController]（分页引擎 / 气泡引擎 / 时空锚接线宿主）与 [InkReaderShell]
 * （手势遮罩 + 气泡层 + 底栏壳）组装到一起：
 *
 * 打开书 → [InkReadController.bindChapter] 锚定书 / 章与候选年代区间、竖排分页 →
 * 竖排逐字绘制 → 短按字 hitTest 命中 → BubbleResolver.resolve 出气泡；
 * 边缘空白翻页走引擎 prev / next；长按不出气泡。
 *
 * 宿主 Activity 把当前书章标题与正文文本流传进来，并接好
 * 目录 / 朗读 / 更多 / 进度跳转 / 气泡动作 / 候选切换回调；引擎细节不在此层外露。
 *
 * 不变式（归位口径，B4-05）：
 *  - 本件只做装配，不复制控制器的引擎编排 / 缓存 / 裁决逻辑；控制器经 [controller] 槽位
 *    注入，缺省由 [rememberInkReadController] 随 Composition 记住单例，宿主可自定义接管；
 *  - 动作 / 候选回调缺省实现保留：[onBubbleAction] 缺省走 [InkActionDispatcher.dispatch]
 *    单一出口，[onCandidateSelect] 缺省走控制器整词解析重查；
 *  - 空正文 / 空章节由引擎与渲染层兜底，本层不造第二份章文本 / 进度 / 样式状态（R5）；
 *  - Context 只在本 Composable 作用域经 `LocalContext` 读取交分发器，不向控制器外的
 *    全局对象回传 Activity。
 *
 * @param onBubbleAction 气泡动作回调；缺省走 [InkActionDispatcher] 分发到地图 / 人物 /
 *                       纪年 / 关系页。宿主需要自定义接管时传入自己的实现。
 * @param onCandidateSelect 候选切换：候选条目回传宿主，由控制器走整词解析重查回填。
 * @param onFallback 兜底入口回调（"web" 联网 / "notebook" 生字本）；缺省为空操作。
 */
@Composable
fun InkReaderBook(
    bookId: String,
    chapterId: String,
    title: String?,
    body: String,
    onCatalog: () -> Unit,
    onTts: () -> Unit,
    onMenu: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    controller: InkReadController = rememberInkReadController(),
    onBubbleAction: ((Action) -> Unit)? = null,
    onCandidateSelect: ((BubbleCandidate) -> Unit)? = null,
    onFallback: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current

    // 缺省动作分发：四类 Action 接对应页 / 卡片；未接通的动作 chip 置灰（见 InkActionDispatcher）。
    // 冻结签名：dispatch(action, context, anchor)，锚点取共享时空总线。
    val actionHandler: (Action) -> Unit = onBubbleAction ?: { action ->
        InkActionDispatcher.dispatch(action, context, ContextAnchor)
    }

    // 缺省候选切换：走控制器整词解析重查回填；宿主需要自定义接管时传入自己的实现。
    val candidateHandler: (BubbleCandidate) -> Unit =
        onCandidateSelect ?: controller::switchCandidate

    // 书 / 章 / 正文变化时重锚时空总线并重排分页引擎；空文本由引擎与渲染层兜底。
    LaunchedEffect(bookId, chapterId, title, body) {
        controller.bindChapter(bookId, chapterId, title, body)
    }

    InkReaderShell(
        engineView = controller.pageView,
        host = controller,
        bubbleContent = controller.bubbleContent,
        progress = controller.progress,
        pageTick = controller.pageTick,
        scrollTick = controller.scrollTick,
        onCatalog = onCatalog,
        onTts = onTts,
        onMenu = onMenu,
        onSeek = onSeek,
        onBubbleAction = actionHandler,
        onCandidateSelect = candidateHandler,
        onFallback = onFallback ?: {},
        modifier = modifier,
    )
}
