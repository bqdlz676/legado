# 立项草案 · 批次D（契约规格 Q-01~Q-07，仅草案，不改登记册/问题清单/代码）

> 规则依据 `docs/立项规则.md` §2/§4；契约类 DoD 默认 L3（冻结签名/边界逐字对上），可落 CI 的加 L2。

## 一、7 行立项草案（可直接粘进问题清单）

| 规格号 | 名称 | 缺陷号 | DoD | 验收档位 | 规格出处（文件:行 + 逐字引文） |
|---|---|---|---|---|---|
| Q-01 | 落文-core-契约 | 复用 U12（core:engine/core:sim 消歧·推演域） | `resolve(name, year?)` 签名与 `Candidate` 九字段逐字实现；`resolve("晋",-700)` 等三例用例可回源；低置信 `isDefault=false` 兜底不猜 | L3 + L2（编译绿+单测） | `落文-core-契约.md:7`「`resolve(name, year?) → List<Candidate>`」；`:8`「Candidate { id, name, type(place\|person\|era\|allusion), tier, start, end, aliases, confidence(high\|medium\|low), source, isDefault }」 |
| Q-02 | 落文-设计系统-契约 | 复用 D23（颜色 token / 排版校准走查域） | 12 色 token 值 + 字体栈逐字落资源；UI 代码不得另发明 token/曲线/字体；`#F7F1E6` 等 12 值在 `colors_ink.xml` 可 grep 回源 | L3 + L2（编译绿） | `落文-设计系统-契约.md:3`「本文件是所有 UI 子任务的**唯一视觉与交互基线**」；`:8`「`--bg-page | #F7F1E6`」；`:33`「正文：Noto Serif SC」 |
| Q-03 | 落文-微交互动效-契约 | 新立 C11（9 项微交互 + 手势分层） | 9 项动效曲线/时长逐条实现；手势分区「点=气泡、长按=放大镜、滑=翻页」边界 0.35s 可回源；旧「长按=选中」口径按本契约作废 | L3（L4 真机不强制） | `落文-微交互动效-契约.md:58`「**重要变更**：长按行为从 M7 原「长按=选中」改为「长按=放大镜」，以本契约为准。」；`:41`「分界线 0.35s——pointerInput 的 onTap 和 onLongPress 自然区分，不冲突。」 |
| Q-04 | 落文-数据schema-契约 | 复用 D24（Data 层共享 / 字段基线域） | 七张核心表字段名逐字冻结；每条数据带 `source`+`confidence`(high/medium/low)；三源交叉打分规则落脚本可回源；零 copyleft 许可表逐项对上 | L3 + L2（数据脚本/CI bench） | `落文-数据schema-契约.md:3`「所有数据文件必须带 `source`（来源）与 `confidence`（high\|medium\|low）标签。字段名不可自行发明。」；`:47`「2/3 一致 → medium → 收录标「待校」」 |
| Q-05 | （登记册占位：名物库档案） | 不新立 —— 已被 D16 吸收归档 | 契约内容归 D16 既有 DoD，本批不再重复派单 | 沿用 D16 档位 | `legado/docs/规格登记册.md:54`「Q-05 \| 落文-名物库档案（六类范围→已扩为十七类…） \| D16 \| [已立项]」；文件 `落文-名物库档案.md:67`「名物库是四板块之一」 |
| Q-06 | 落文-实现规格-v3 | 新立 C12（19 模块边界 + 性能红线 + 边界兜底） | 19 模块清单归属与切分逐字对齐；§6 性能红线 8 项逐条进 CI bench；§5 五类边界（空数据/断网/缺年份/消歧失败/帧率掉线）各有迂回方案不空白态 | L3 + L2（bench 红线 CI 绿） | `落文-实现规格-v3.md:31`「空数据、网络断开、年份缺失、同名消歧失败、帧率掉线——自行判断给合理实现，**不降级、不砍功能、不牺牲效果**。」；`:34`「翻页 ≤16ms/帧、气泡冷启 ≤300ms/热 ≤50ms、字典加载 ≤15MB、启动→书架 ≤1.2s…」 |
| Q-07 | 落文-板块结构 | 新立 C13（四板块归属铁律 / 入口收口） | 阅读器/名物库/笔记/检索四板块职责逐条对上；考点库挂笔记+检索不单独成板块；四板块之外不新增入口；新功能先问归属、答不上不做 | L3（入口 grep 回源） | `落文-板块结构.md:50`「**四板块之外不新增入口。**」；`:56`「答不上来 → 不做。」 |

## 二、名称不符（登记册名 ↔ 真实文件名，Glob/ls 逐字核对）

| 规格号 | 登记册/任务名 | 磁盘真实文件名 | 结论 |
|---|---|---|---|
| Q-01 | 落文-core-契约 | `落文-core-契约.md`（3266B / 50 行） | 一致 |
| Q-02 | 落文-设计系统-契约 | `落文-设计系统-契约.md`（2704B / 60 行） | 一致 |
| Q-03 | 落文-微交互动效-契约 | `落文-微交互动效-契约.md`（7108B / 59 行）；glob `*微互动*` 0 命中、`*动效*契约*` 命中本文件 | 一致（任务 glob 前缀「微互动」是简称，实际词序为「微交互」） |
| Q-04 | 落文-数据schema-契约 | `落文-数据schema-契约.md`（3227B / 56 行） | 一致（schema 小写） |
| Q-05 | 占位「登记册缺失，编号可能跳号」 | 无 `落文-*Q-05*` 文件；`落文-名物库档案.md`（4159B / 76 行）已在登记册:54 占 Q-05 号并立 D16 | **号不缺、文件不缺**：Q-05 已被名物库档案占用，非跳号 |
| Q-06 | 落文-实现规格-v3 | `落文-实现规格-v3.md`（4472B / 43 行） | 一致 |
| Q-07 | 落文-板块结构 | `落文-板块结构.md`（1849B / 56 行） | 一致 |

## 三、冲突（契约 ↔ 已实现代码，两侧 `文件:行`）

| # | 契约侧（冻结口径） | 代码侧（现状） | 处置建议 |
|---|---|---|---|
| 1 | `落文-core-契约.md:7-9`：`resolve(name, year?) → List<Candidate>`，`Candidate` 含 `id / name / type(place\|person\|era\|allusion) / tier / start / end / aliases / confidence / source / isDefault` | `app/src/main/java/io/legado/app/help/NameResolver.kt:14-32`：返回 `Hit(name,kind,meta,detail,pinyin,wlat,wlng,placeId,placeKind,tier,others,anchorYear)`；`Kind` 枚举仅 `PERSON, PLACE`（`:32`），无 `id/end/confidence/source/isDefault/era/allusion` 字段 | 归 Q-01/U12：按契约补字段或修订契约签名，二选一须主控拍板，不许代码侧自行漂移 |
| 2 | `落文-微交互动效-契约.md:58`：长按=放大镜（旧「长按=选中」作废） | `app/src/main/java/io/legado/app/ui/book/read/ink/GestureRouter.kt:35` 注释「长按路由：进入文字选择」；`:36` `onLongPress(...)` 走 `onCenterLongPress` | 归 Q-03/C11：路由注释与出口按契约改为放大镜；若仍保留选区，须在契约侧补回选区口径 |
