> ⚠️ **作废（2026-11-24 20:xx）**：本文件出自"一个代理批量做一批图标"的旧派单方式，已被用户口令「一图标一子代理」取代；内容仅作历史留档，**不得据此认领交付**，现行依据是 `docs/图标单卡/*.md` 与 `docs/图标单卡-派单台账.md`。

# 图标 · 批A · 导航与工具（11 个 VectorDrawable）

> 依据：`docs/落文-规格切片/切片-24-功能图标全集.md`（用户原文照录）。口径：24×24dp，描边 1.5，圆角端点/连接，默认暗铜 `#8B6914`；仅原文点名的实心元素用 `fillColor="#8B6914"`。
> 仓库 HEAD `5e6402f`；本批只写 `app/src/main/res/drawable/` 下 11 个 XML；未编译、未改 .kt、未 git 写。
> **域归属**：底部导航三 Tab `ic_tab_bookshelf / ic_tab_discover / ic_tab_settings`（含 `_e` 暗铜 / `_s` 暖金 selector 变体）归 D25 代理，**本单未动**，保留仓库 D25 版本；本单对照表与自检均不含这 3 枚。

## 一、文件清单（11）

| # | 文件名 | 域 |
|---|---|---|
| 1 | ic_toggle_layout.xml | 阅读页·古/今切换 |
| 2 | ic_bookmark.xml | 阅读页·书签 |
| 3 | ic_paint.xml | 阅读页·画板 |
| 4 | ic_music.xml | 阅读页·音乐 |
| 5 | ic_read_aloud.xml | 阅读页·朗读 |
| 6 | ic_annotate.xml | 阅读页·批注 |
| 7 | ic_theme_swatch.xml | 阅读页·主题色块 |
| 8 | ic_popup_copy.xml | 气泡·复制 |
| 9 | ic_popup_highlight.xml | 气泡·划线 |
| 10 | ic_popup_share.xml | 气泡·分享 |
| 11 | ic_popup_search.xml | 气泡·查词 |

## 二、描述要点 → pathData 对照（11 行）

| 文件 | 切片描述要点 | 落地 pathData（描边 / 实心） |
|---|---|---|
| ic_toggle_layout | 左竖线 x7 y4→20；右横线 x10→20 y16 半宽 | `M7,4 L7,20 M10,16 L20,16` |
| ic_bookmark | 五角星，外接圆 c12,12 r10；左上角断口 8° | 五星描边 `M12,2 L14.25,8.91 L21.51,8.91 L15.63,13.18 L17.88,20.09 L12,15.82 L6.12,20.09 L8.37,13.18 L2.49,8.91 L9.76,8.91 Z` |
| ic_paint | 笔杆 x12 y5→18；笔尖三角 12,18→9,20→15,20；墨点 (12,18) r1.2 实心 | `M12,5 L12,18`；`M12,18 L9,20 L15,20 Z`；点 fill r1.2 |
| ic_music | 竖线 x7 y5→19；旗 8,5→15,8→15,14→8,12；孔 (11,8) r1、(11,12) r1 | `M7,5 L7,19`；`M8,5 L15,8 L15,14 L8,12 Z`；两孔 stroke r1 |
| ic_read_aloud | 喇叭 5,8→9,6→9,18→5,16；两弧 10,7→14,12、11,5→17,12 | `M5,8 L9,6 L9,18 L5,16 Z`；`M10,7 Q13,9.5 14,12`；`M11,5 Q15,8.5 17,12` |
| ic_annotate | 轴 x12 y4→20；上横条 x8→16 y3；下横条 x8→16 y20 | `M12,4 L12,20 M8,3 L16,3 M8,20 L16,20` |
| ic_theme_swatch | 实心圆 c12,12 r8；外圈细线 r10；填充=当前主题色（动态） | 内圆 fill r8（默认 #8B6914）；外环 stroke r10 |
| ic_popup_copy | 两重叠细线方块：上 6,5→16,16；下 8,9→20 | 上 `M6,5 L16,5 L16,16 L6,16 Z`；下 `M8,9 L18,9 L18,20 L8,20 Z` |
| ic_popup_highlight | 横线 5→19 y12；两端点 r1；线上方极小倒三角 | `M5,12 L19,12`；两点 fill r1；`M11,7 L13,7 L12,10 Z` |
| ic_popup_share | 三点 (12,6)/(6,14)/(18,14) r1.5；两条连线 | 三点 fill r1.5；`M11.1,7.2 L6.5,12.8 M12.9,7.2 L17.5,12.8` |
| ic_popup_search | 圆 c10,10 r6；镜柄 14,14→18,18 | 圆 stroke r6；`M14,14 L18,18` |

## 三、自检输出（11 个）

- 良构：11/11 `xml.dom.minidom.parse` 通过，无异常。
- 描边规格：每文件 `strokeWidth="1.5"` ≥1、`strokeLineCap="round"` ≥1、`strokeLineJoin="round"` ≥1。实测 sw/cap/join：toggle 1、bookmark 1、paint 2、music 4、read_aloud 3、annotate 1、theme_swatch 1、copy 2、highlight 2、share 1、search 2，均达标。
- 视口：`grep -L 'viewportWidth="24"'` 命中为空；11 文件 `viewportWidth/Height=24`。
- 坐标：绝对坐标全部落在 0..24；描边外扩 0.75 后未越界 >24.75。
- 填充：全批仅 7 处 `fillColor="#8B6914"`，均为原文点名实心元素（画板墨点、色块内圆、划线两端点、分享三圆点）；无其他色值填充、无阴影、无渐变。
- 3 枚 D25 selector 另做良构解析通过（本单不改其内部）。

## 四、歧义与边界处置（止损记录）

- ic_bookmark：原文"断口 8°（startAngle=-60, sweepAngle=340）"与五星闭合多边形口径冲突；**描述歧义，按坐标**（外接圆 r10 c12,12）实现闭合描边，未在星角上做 8° 缺口。
- ic_theme_swatch：动态主题色无法在静态 VectorDrawable 内解析，内圆先落默认暗铜 `#8B6914`；主题色覆写由组件层 tint/`setTintList` 完成。
- ic_popup_copy：下层方块 x 终点原文未给，按与上层等宽 10dp 取 x=8→18（0.5dp 级微调，不改语义）。
- ic_music：两个"小圆孔"以 r1 描边圆表达；静态 vector 无透明孔挖空能力。

## 五、二阶 / 三阶影响

- 已被引用 2 个：`ic_bookmark` ← `MyInkFragment.kt`、`pref_main.xml`；`ic_read_aloud` ← `view_read_menu.xml`、`view_read_aloud_float_bar.xml`。
- 其余 9 个（toggle_layout/paint/music/annotate/theme_swatch/popup_*）当前无 .kt/.layout 引用，属待后续 UI 批次接线；缺失时对应按钮不显图标，不崩溃。
- 三枚 Tab 的引用方 `ThemeBottomNavigationVIew.kt`、`menu/main_bnv.xml` 属 D25 域，本单未触碰。
- 三阶：图标全部单色描边，换肤/夜间模式只需换 tint 色值，无需改 vector 本体。

## 六、反例边界

- 0.5dp 微调（如 copy 下层 x 终点、弧线控制点）不改变图形语义；后续按像素比对时允许此容差。
- 反例自查：实心点 r1.2/1.5/r1 若误改为描边，小尺寸下会糊成环；本批已按 fill 落地。坐标越界 >24.75 会被裁角，已逐个核范围。

## 七、认知成本

- 命名沿用切片前缀（music/paint/annotate/popup…），与文件域白名单一一对应；新工程师按文件名即可定位语义，无需翻历史猜测名。
- 本批无跨模块抽象、无共享 shape；每个 vector 自包含，读单文件即可理解全貌。

## 八、未验证项

- **未编译、未真机渲染走查；xml 良构已验证。** 未跑 gradle/kotlinc，未在设备或模拟器上目视比对线宽、圆角与实心点尺寸；弧面旗、声波弧的曲率为按坐标的近似实现。
