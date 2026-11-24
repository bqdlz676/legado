# 修复 CI · 导航壳与书架页编译（只修编译，不改设计）

HEAD=f08c4ba；沙箱禁本地编译，仅读码定位。域内 3 条 `e:` 全部收口。

## A. MainActivity.kt:227 — Unresolved reference 'compressExplore'

- 调用点：`MainActivity.kt:227` `(fragmentMap[1] as? ExploreInkFragment)?.compressExplore()`。
- 架构核查：`ExploreInkFragment.kt` 为 D25 重写后的 View 页（ScrollView + 横向 rvCampaign + 静态典籍/专题/更新），**未**改成 Compose 宿主；全仓无等价改名方法，旧实现仅存在于已不接线的 `ExploreFragment.kt:331`。
- 旧方法行为（`ExploreFragment.kt:331-338`）：先压缩展开的书源卡片，失败/无展开项则回滚列表到顶部。
- **选择：在第三个文件 `ExploreInkFragment.kt` 补等价方法（非纯空实现）**。依据：新架构已无可展开卡片，"压缩展开项"一半无对象；但 ScrollView 滚动位置仍在，重复点导航回顶部的行为必须保留，否则残留滚动位置错乱。
- 改动：
  - `ExploreInkFragment.kt:11` 新增 `import android.widget.ScrollView`（`View` 原有，line 7）。
  - `ExploreInkFragment.kt:67-70` 新增 `fun compressExplore()`：`rvCampaign.scrollToPosition(0)`；`rvCampaign.parent?.parent as? ScrollView` 取祖先容器 `fullScroll(View.FOCUS_UP)`。布局链：ScrollView > 竖向 LinearLayout > rvCampaign。
- MainActivity.kt 调用点未改，签名 `fun compressExplore()` 与原调用一致。

## B. BookshelfScreen.kt:722 — onClear 未传 / lambda 落到 modifier 位

- 组件签名（`BookshelfScreen.kt:866`）：`OverlayClearButton(onClear: () -> Unit, modifier: Modifier = Modifier)`，函数型参数在前、Modifier 在后；Kotlin 尾随 lambda 只能落到最后一个形参，故原写法把 `() -> Unit` 塞进了 `Modifier` 位。
- 改动：`BookshelfScreen.kt:722` 改为 `OverlayClearButton(onClear = { onQueryChange("") })`，Modifier 用默认值。
- 语义：浮层内 x 图标即独立清空入口，`onClear` ＝清空关键词，非空回调；另加注释说明。

## 自检

- 括号配平（`{}` / `()`）：ExploreInkFragment 43/43、213/213；BookshelfScreen 121/121、393/393；MainActivity 98/98、230/230。
- import 逐个核对：ExploreInkFragment 新增 ScrollView 且 line 69 实际使用；View 原有；未新增其他 import；BookshelfScreen 无新增 import。
- 未碰 `card/`、`map/crossing/`、`book/read/`；未删功能/分支；无 `@Suppress`；未 git 写、未编译。
- 第三个文件：是，`ExploreInkFragment.kt`（补 `compressExplore`，理由见 A）。
