# 模块⑦ 阅读页 Compose 壳 + 手势（ink/）

只保留 Legado 排版引擎（View），外围 UI 全部 Compose 自绘；本目录不 import、不调用任何
`ui.book.read.page.*` 引擎类，引擎 View 以通用 `android.view.View` 传入。

## 文件清单

| 文件 | 职责 |
|---|---|
| `InkTokens.kt` | 12 色 token、缓动曲线、时长、分区尺寸（契约冻结值，唯一取用处） |
| `InkModels.kt` | CharHit / BubbleAnchor / ReaderZone / BubbleState（气泡内容复用模块⑥ `help.bubble.BubbleContent`） |
| `ZoneClassifier.kt` | 手势分区纯函数：左 1/5、中 3/5、右 1/5、底栏 60dp |
| `ReaderShellHost.kt` | 壳 ↔ 宿主桥接接口（命中检测 / 翻页 / 查字 / 选词 / 气泡解析） |
| `GestureRouter.kt` | 单击 / 长按统一路由（两种派发模式共用） |
| `BubblePlacement.kt` | 气泡定位纯函数：上方 8dp、箭头对字心、越界翻下方、不遮当前句 |
| `InkShellState.kt` | 气泡 / 底栏显隐状态 |
| `InkTouch.kt` | InkButton 墨触基类（朱砂 8% 按压，120ms 回落，无 ripple） |
| `InkBubble.kt` | 气泡自绘：注音 / 释义 / 分隔线 / 义项数 / 动作 chips / 尖角 / 入场动画 |
| `InkBottomBar.kt` | 60dp Compose 底栏：目录 / 进度金线朱砂点 / 朗读 / 更多 |
| `ViewGestureBridge.kt` | 降级 fallback：点击 / 长按挂到引擎 View 的 OnClickListener / OnLongClickListener |
| `InkReaderShell.kt` | 壳组合入口：AndroidView(引擎) + 手势遮罩 + 气泡层 + 底栏 |

## 手势分区（铁律）

- 左 1/5：点中字 → 先出气泡；点空白 → 上一页；滑动 → 回 View fling。
- 右 1/5：点中字 → 先出气泡；点空白 → 下一页；滑动 → 回 View fling。
- 中 3/5：点 = 查字 / 气泡；长按 = 选中；滑 = 翻页。
- 底栏 60dp：Compose 全吃。
- `detectTapGestures` 只吃 tap / longPress，不注册 drag 检测器；滑动不消费。
- 真机若 View 收不到滑动：`DispatchMode.ViewBridge` 走 `ViewGestureBridge`。

## 宿主接入清单（主控 merge 时做）

1. 实现 `ReaderShellHost`：
   - `probeChar(x, y)`：调引擎既有命中能力，回填 `CharHit.anchor`（壳坐标 px）；空白返回 null。
   - `turnPage(dir)`：走既有 PageDelegate 翻页通道。
   - `onCenterTap / onCenterLongPress`：接查字解析与文字选择。
   - `requestBubble(hit)`：调模块⑥ `BubbleResolver.resolve(...)` 取 `BubbleContent` 回传；
     未收录由 resolver 产出 `BubbleKind.MISSING` 兜底内容，壳只负责渲染。
   - `dismissBubble / isTextSelected / isPageAnimating`。
2. 在阅读页 `AndroidView` 之外套 `InkReaderShell`；`pageTick / scrollTick` 随翻页、滚动自增。
3. 气泡动作 `InkAction.OpenMap / OpenPerson / OpenTimeline / OpenRelation` 接对应 Sheet / 地图。

## 待主控接入的 Gradle 依赖（本目录不改动 build 文件）

- 插件：`org.jetbrains.kotlin.plugin.compose`，版本跟随 Kotlin 2.4.20；`buildFeatures { compose = true }`。
- 依赖：`androidx.compose:compose-bom`（与 Kotlin 2.4.20 配套的稳定版）、
  `androidx.compose.ui:ui`、`androidx.compose.foundation:foundation`；`activity-compose` 目录已目录化。
- 本目录代码仅用 foundation + runtime，未引用 material3，未包裹 MaterialTheme。

## 验证状态

- 静态自查：分区判定、路由优先级、气泡定位翻转、token 取值已逐项核对；括号 / 导入平衡。
- **未真机编译、未真机验证**：Compose 依赖尚未接入工程，触摸事件在 Compose/View 混用层级下的
  实际派发（尤其滑动是否回传到 View fling）需真机跑通后确认；异常时切 `ViewBridge`。
