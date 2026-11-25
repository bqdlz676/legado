# 锚点卡 · B29 域外常量 visible（config 域 13 条）

> 用途：B29 分域收口第一单（config 域）开工依据。清单出自 `docs/修复-B26-常量visible接线.md` 的清单结论段。

## 一、事实锚点
- 全仓 `visible = true` 共 36 条：B26 已修 1 条（HighlightNoteDialog）、红线 1 条（HandleFileSheet，components 契约冻结）、域外 34 条待分域收口。
- 本单负责 config 域 13 条（设置页相关浮层），逐条以 B26 报告清单里的文件:行为准，开工前先自行复核行号（工作树已变动，行号可能漂移）。

## 二、判据
1. 先复核：以 B26 报告清单为线索，自己 grep 出 config 域实际命中并逐条列文件:行；与清单不符的（多出或少了）要写明。
2. 逐条判定：是「浮层可见性常量」→ 改两段式；是「条件渲染宿主」→ 说明为何无需离场动画；判断必须带代码证据（读该处上下文 20 行）。
3. 改的必须复用既有离场阶段机（`ReaderSheetExitPhase` 或 B26 的 `NoteDialogExitGate` 范式），禁造第二份枚举。
4. 离场期间拒收重复打开。
5. 每条改动至少一条纯 JVM 断言；反引号测试名禁含 . : [ ] / ; < >。
6. 不改设置页的既有交互与视觉，只补离场。

## 三、边界
- 只动 config 域（`ui/config/**`、`ui/main/setting` 相关）文件与其测试；不碰 components（HandleFileSheet 红线）、不碰 reader 域、地图、名物库、help/artifact。
- 13 条如实际判定后不足或超出，以实测为准并在报告写明差额原因。

## 四、验收
独立验收代理逐条对照；CI 唯一编译手段。
