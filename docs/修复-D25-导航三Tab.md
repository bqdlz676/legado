# 修复-D25 导航三 Tab + 搜索下拉浮层

基线：main HEAD `213bd6c`。沙箱禁编译，**本单未编译 / 未跑单测 / 未真机**。
导航壳现状仍是 XML menu + `FragmentStatePagerAdapter` 旧体系；按止损只改 menu、入口集合与书架既有 Compose，**导航壳 Compose 化待 D24**。

## 1. 改动清单（文件 : 行）

| 文件 | 改动 | 关键行 |
| --- | --- | --- |
| `app/src/main/res/menu/main_bnv.xml` | 4 项 → 3 项：书架 / 发现 / 设置；删画板；第三项标题 `@string/setting`；三枚图标按规格切片 `ic_tab_*` | 7-18 |
| `app/src/main/java/io/legado/app/ui/main/MainActivity.kt` | 槽位常量 `idBookshelf/idExplore/idSettings`；删 `idRss/idMy`、`menu_paint` 分支、`RssFragment` 引用；新增幂等 `switchTab`；适配器三分支 | 92-102, 192-211, 328-342, 466-468, 488-501 |
| `app/src/main/java/io/legado/app/lib/theme/view/ThemeBottomNavigationVIew.kt` | 默认图标表三槽统一指向 `ic_tab_bookshelf / discover / settings`，第三槽不再用人像标 | 36-39 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfSearch.kt` | 新增：浮层状态机 + 本地过滤（纯 JVM，可单测） | 1-43 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfScreen.kt` | 页眉放大镜触发；下拉浮层（输入即查 / 遮罩点按 / 下滑收起 / 空态占位）；结果行按卡片规格 §六（56dp、14sp 书名、10sp 作者/进度、本地标签、命中词朱砂高亮）；`onSearch` 经 `ShelfGrid/OfflineShelf` 透传 | 99-101, 136/150, 156-163, 202, 295/310-315, 663-833, 837-891 |
| `app/src/main/java/io/legado/app/ui/main/explore/ExploreInkFragment.kt` | 发现页头部独立搜索入口下线（`tvSearch` 隐藏）；卡片预填词检索保留待交接 | 40-43 |
| `res/drawable/ic_tab_bookshelf.xml` / `ic_tab_discover.xml` / `ic_tab_settings.xml`（各配 `_e`/`_s`，共 9 个） | 规格切片矢量：24×24dp、描边 1.5px、圆角端点；未选中暗铜 `#8B6914`、选中暖金 `#D4A54A`；无填充 / 阴影 / 渐变。书架=三竖线+底横线；发现=正圆+圆心点+45°射线；设置=两竖线+顶端圆点（**非齿轮**，按图标规格几何逐字落地） | selector 各 1-6 |
| 旧导航图标族清零 | 删 `ic_bottom_books(_e/_s)`、`ic_bottom_explore(_e/_s)`、`ic_bottom_setting(_e/_s)`，加上一轮已删的 `person/paint/rss_feed`；`MyInkFragment` 舆图行复用图标改用现存 `ic_cfg_other` | — |
| `app/src/test/java/io/legado/app/ui/main/bookshelf/ShelfSearchD25Test.kt` | 新增 5 条单测（菜单三 Tab / 状态机 / 过滤空态 / 幂等入栈 / 图标三枚 + 规格口径回证） | 1-154 |

## 2. grep 回证

- 菜单 Tab 数：`grep -c "<item>" main_bnv.xml` → **3**；id：`menu_bookshelf`(8) `menu_discovery`(12) `menu_my_config`(16)；无 `menu_paint`。
- 导航图标引用：`ThemeBottomNavigationVIew.kt:36-39` + `main_bnv.xml:9/13/17` 共 3 枚；selector 资源 = `ic_tab_bookshelf.xml / ic_tab_discover.xml / ic_tab_settings.xml`；旧 `ic_bottom_*`、person/paint/rss_feed 引用与文件均清零（`grep -rn ic_bottom_ app/src/main/` 空；9 个新矢量 `xml.dom.minidom` 解析全部通过）。
- 浮层四要素：触发 `SearchGlyphButton` 点按（310-315）；收起 `detectTapGestures` 遮罩（680）+ `detectVerticalDragGestures` 下滑 80dp（689-697）；输入即查 `onValueChange=onQueryChange`（708）；空态 `OverlayHint`（740-741）。
- 旧符号清零：MainActivity 内 `idMy/idRss/RssFragment/ExploreFragment/menu_paint/PaintActivity` 命中 0。

## 3. 验收判据表

| 判据 | 结论 | 证据 |
| --- | --- | --- |
| 底部 Tab 数量 = 3 | 过 | menu 3 项；适配器 `getCount=bottomMenuCount`，三分支 499-501 |
| 三 Tab 中文标签 + 选中态 | 过 | `@string/bookshelf/discovery/setting`（zh：书架/发现/设置）；选中暖金 `#D4A54A`，未用纯黑底 |
| 搜索 = 书架下拉浮层，无独立页主壳入口 | 过（主壳） | 浮层 663-833；发现头部入口已隐藏 40-43；底栏无搜索位 |
| 独立搜索页入口残留处置 | 见 §6 交接 | 主壳 0 残留；跨域调用方保留 Activity 本体待收口 |
| 图标引用数 / 资源数 = 3 | 过 | 引用 3、selector 3；线宽 1.5、圆角端点、暗铜/暖金，无填充 / 阴影 / 渐变 / 印章 / 印刷字标 / 月牙 |
| 空数据 / 断网占位 | 过 | 书架空 `EmptyShelf` 438；断网 `OfflineShelf` 477/505；搜索空 741 |
| 关键常量与“为什么”注释 | 过 | 槽位 91、状态机 `ShelfSearch.kt`、阈值 667、幂等 201-204 均带注释 |
| 单测 ≥4 | 过 | 5 条（菜单/状态机/过滤/幂等/图标） |

## 4. 二阶 / 三阶影响

- **深链 / PendingIntent / 快捷方式**：`api/ShortCuts.kt` 仅建 MainActivity 书架快捷方式，无搜索快捷方式；Manifest 中 `InkSearchActivity` 无 LAUNCHER / deep-link filter（292-299，`exported=false`）。旧入口删除不产生悬空深链。
- **默认首页偏好**：`defaultHomePage="my"` 现映射到设置槽位（452）；`"rss"` 选项旧代码即无分支，属历史遗留，交 C1 清数组项。
- **底栏皮肤**：`menuIdToSlot` 槽位键保持 `bookshelf/home/settings`，皮肤 PNG 按槽位名取图，第三槽改名不影响旧皮肤包；`notes` 槽位本就未用。
- **返回栈**：设置 Tab 只切 ViewPager 槽位，不 `startActivity`，重复点击不重复入栈（`switchTab` 205-211）；`MyInkFragment` 内行点击仍各自 `startActivity`，行级栈不变。
- **旧死 Fragment**：`ExploreFragment/MyFragment/RssFragment` 已不被适配器实例化，仍挂旧布局 / 菜单；本单未删，交旧壳清理单。

## 5. 反例与边界

- **横竖屏 / 小屏**：浮层 `fillMaxWidth`，结果区 `heightIn(max=260dp)`，不横向溢出；小屏 IME 弹起未真机走查（挂账）。
- **空书架 + 关键词**：`filter(emptyList, 词)` 返回空列表 → 浮层空态，不崩；空词返回全量走引导态 740。
- **断网**：`OFFLINE` 态沿用既有离线条 + 本地缓存；浮层只过滤本地书架，不发网络请求。
- **发现页被偏好隐藏**：`realPositions` 动态映射，`indexOf` 取不到时 `switchTab(-1)` 返回 `false`，不切页不崩。
- **无障碍**：自绘 Canvas 图标与输入框未加 semantics 标签，挂账 D24。
- **若本方案判断错**：三 Tab 中“设置”复用了原“我的”宿主页（`MyInkFragment`），未直接落地纯设置列表；若规格要求设置 Tab 直开 `ConfigActivity`，需 D24 壳改造时再定。

## 6. 交接（跨域，本单未改）

- **C1 配置域**：`arrays.xml:160-174` 首页选项仍含 RSS / 我的；`showDiscovery` 开关与固定三 Tab 口径需对齐。
- **D1~D7 检索流**：`InkSearchActivity` 本体及以下调用方保留，待检索模块统一收口——`receiver/SharedReceiverActivity.kt:57`、`ui/about/ReadRecordActivity.kt:150`、`ui/book/info/BookInfoActivity.kt:936/1126/1140/1155/1169`、`ui/book/source/edit/BookSourceEditActivity.kt:197`、`ui/book/source/manage/BookSourceActivity.kt:140`、`ui/rss/read/RssJsExtensions.kt:77/83`；`ExploreInkFragment.kt:86/196` 卡片预填词检索同属检索流。
- **旧壳清理**：`ExploreFragment.kt:328`（死 Fragment 内搜索入口）、`MyFragment/RssFragment` 及 `main_explore/main_my/main_rss` 菜单、旧布局。
- **D24**：导航壳 Compose 化；补无障碍 semantics；`menu_my_config` 命名建议改 `menu_settings`。

## 7. 未验证项

- **未编译、未跑单测、未真机**（沙箱禁 gradle/kotlinc）。
- Compose 浮层手势、IME 弹起、横竖屏切换未走查；图标矢量未量像素；皮肤换肤路径未实机验证。
- 跨域调用方是否仍引用已删图标 / 布局，未在编译期确认。
