# ic_theme_swatch.xml（主题色块）

- 规格行（切片-24 阅读页工具栏，逐字）：`实心小圆 cx=12 cy=12 r=8。外圈细线圆环 r=10。圆的填充色 = 当前主题色（动态设置）。`
- 两段 pathData：①内圆实心 `M4,12 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z`（cx=12 cy=12 r=8，fillColor=#D4A54A 默认暖金）；②外圈描边环 `M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0 Z`（r=10，strokeWidth=1.5，strokeColor=#8B6914，lineCap/lineJoin=round，无填充）。
- 同心与空隙：两圆圆心均为 (12,12)；r=8 与 r=10 之间留 2dp 空隙，环不贴内圆。
- 坐标安全：外扩 = 10 + 1.5/2 = 10.75，x/y 范围 1.25..22.75，全部落 0..24。
- tint 覆盖机制：默认内圆 #D4A54A 为兜底色；运行时 Compose `Icon(painter=..., tint=currentThemeColor)` 或 ImageView `app:tint` 对 drawable 非透明色统一着色，覆盖为当前主题色。注意：整图 tint 会同时改写外圈描边色；若需外圈固定 #8B6914，应拆为底环图层 + 单独 tint 的内圆图层，或运行时替换内圆 fillColor。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5 ×1、fillColor=#D4A54A ×1、strokeColor=#8B6914 ×1。
- 未验证项：未编译、未真机渲染。
