# 锚点卡 · B1 点字详情改走 InkHalfSheet

> 用途：B1 写码单前置锚点（F-85）。逐字引文，禁转述。本单只做「点字三入口接线」，不做观感返工、不动书架/启动页。

## 一、逐字引文（文件:行）

- `docs/问题清单.md:50`：「**点字弹出的仍是旧 Material `BottomSheetDialogFragment`（`DetailSheet`），不是统一 `InkHalfSheet`** ｜ `ui/book/read/page/ContentTextView.kt:586 / 618 / 640 / 1378`；`ui/main/DetailSheet.kt:35` ｜ 🔴 ｜ 阅读链路主贴皮，规格「界面全部原生自绘」；气泡/人物/纪年三入口必须走 `InkHalfSheet` + `InkBubble`，旧类删除」
- `docs/落文-规格切片/切片-11-半屏Sheet.md:9`：「弹起时阅读页整体下沉 8dp + 暗化 40%（茶色玻璃罩）Sheet 背景 = 纸白 #F7F1E6 + 金色边框 1px顶部 4dp 宽金色横杠（不是圆角条），两端各一粒金点Sheet 最大高度 = 屏幕 × 60%跟手下拉关闭：Sheet 下降，阅读页同时上升恢复亮度」
- `docs/落文-规格切片/切片-11-半屏Sheet.md:20`：「5.2 人物 Sheet（顺序固定）① 本段行为（一句加粗）── … ② 本段同场人物 … ③ 本书时间轴（竖排时间线：首次出场→关键事迹→末次）… ④ 档案（本名/字/号/封号/生卒/谱系 表格对齐）… ⑤ 同名的其他 N 人 ›」
- `docs/落文-规格切片/切片-11-半屏Sheet.md:23`：「5.3 字词 Sheet ① 拼音 + 部首 + 笔画 + 全义项 … ② 说文释文 + 反切 … ③ 古籍用例（≤3 条）… ④ 以该字开头的常见词（最多 14 个）」
- `docs/落文-规格切片/切片-11-半屏Sheet.md:24`：「5.4 关闭后阅读位置、滚动位置、下划线状态一字不差。这是验收铁律。」
- `docs/落文-规格切片/切片-02-气泡协议.md:19`：「半屏 sheet：详情占屏 ≤ 60%，原文自动滚到不被遮的位置；关闭后滚动位置与文字一字不差。」

## 二、参数表

| 项 | 值 | 出处 |
|---|---|---|
| 宿主下沉 / 暗化 | 8dp / 40% 茶色玻璃罩 | 切片-11:9 |
| Sheet 底 / 边框 | 纸白 #F7F1E6 / 金色 1px | 切片-11:9 |
| 拖拽横杠 | 4dp 金横杠 + 两端金点 | 切片-11:9 |
| 最大高度 | 屏高 × 60% | 切片-11:9、切片-02:19 |
| 现有实现入口 | `ui/components/InkHalfSheet.kt:90` `fun InkHalfSheet(visible,onDismiss,modifier,state,title,source,model,dialog,host,content,onLinkSelected)` | 源码实测 |
| 旧路径（待废） | `ui/main/DetailSheet.kt`（Material `BottomSheetDialogFragment`）+ `ContentTextView.kt:586/618/640/1378` | 问题清单:50 |

## 三、判据（机械可判）

1. `grep -rn "DetailSheet" app/src/main/java/io/legado/app/ui/book/read/` 命中 0；`grep -rn "InkHalfSheet" app/src/main/java/io/legado/app/ui/book/read/` 命中 ≥1，且三入口（人物/纪年/典故-字词）各有调用点。
2. 接线后 `grep -rn "DetailSheet" app/src/main/java` 若为 0，则同单删 `ui/main/DetailSheet.kt` 与 `ui/main/DetailSheetFragment*`（若有）并把 grep 证据写进报告；若仍有引用则保留旧类、列残留清单交下一单，禁留两个可见入口。
3. Sheet 关闭后阅读位置/滚动/下划线不变：以既有阅读页状态持有者为唯一权威，Sheet 只读不改；单测覆盖「打开→关闭→位置字段相等」（纯 JVM 可测部分）。
4. 不引入 Material 主题/ripple/elevation；颜色只引 token 常量，无新增 8 位裸 hex。
5. 本地不编译；编译结论标「未验证，等 CI」。
