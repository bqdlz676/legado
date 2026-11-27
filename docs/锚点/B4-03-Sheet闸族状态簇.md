锚点卡：docs/锚点/B4-03-Sheet闸族状态簇.md

# B4-03 Sheet 离场闸族与壳状态簇归位 · 锚点卡

本卡路径：docs/锚点/B4-03-Sheet闸族状态簇.md
对应报告：docs/修复-B4-03-Sheet闸族状态簇.md

## 验收判据

- 闸族只许一个阶段判定基类：`SheetExitPhaseGate`；I-21 `ReaderSheetExitGate` 等子类只登记 / 清存负载，不各写一套阶段判定（定界 §六二阶风险 5，docs/任务书-B4-阅读页-引擎定界.md:518）。
- 非法迁移不崩：拒收回退安全态并累加可观测计数，不抛异常；回位三场景（进程重建 / 代次闸 / 离场后重进）各有一条纯逻辑用例；四包零 diff。
- 回位铁律 R14（docs/任务书-B4-阅读页.md:48）：关闭后阅读位置、滚动位置、下划线状态一字不差；快照前后逐字段机械比对，未打开或未关闭返回 null 不可判定。

## 可验证改动面

- 主代码五件，均在 `app/src/main/java/io/legado/app/ui/book/read/ink/`：`SheetExitPhaseGate.kt:59` 基类；`ReaderSheetExitGate.kt:28` 负载子类；`ReaderSheetGuard.kt:27` 快照守卫；`ReaderSheetState.kt:68` 组合状态；`InkShellState.kt:34` 壳组合状态。
- 单测两件：`app/src/test/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGateRejectTest.kt:21`；`app/src/test/java/io/legado/app/ui/book/read/ink/ReaderSheetStateRestoreTest.kt:25`。
- 回位接缝：`ReaderSheetGuard.saveBaseline` / `restoreBaseline`；`ReaderSheetState.saveSnapshotBaseline` / `restoreAfterRecreation` / `releaseOnHostDestroy`；代次规则 `SheetGenerationPolicy.shouldAdmitRequest`。

## 依赖与边界

- 只读依赖：`ui/components` 的 `ReaderSnapshot` 与 `isRestoredExactly`；`page/api` 的 `ReaderDetailRequest`；compose runtime 可观察态薄包装。不引 navigation-compose；不改 `build.gradle`；不新增依赖。
- 冻结四包 `page/provider/**`、`page/entities/**`、`page/api/**`、`page/delegate/**` 零改动；旧阅读页实现（`ReadBookActivity`、旧 XML / Binding 侧）不动，留待 B4-31 下线。
- 并行边界：不碰 B4-02 翻页七件（`InkPageCommand` / `PageTurnGate` / `PeelSpring` / `ViewGestureBridge` / `ZoneClassifier` / `GestureRouter` / `CandidateCycle`）。

## 验收方式

- L1 静态：`kotlin-syntax-lint.py` 过七件；`check-compose-pitfalls.py` 在 ink 主码与测试目录 0 命中；grep 回证拒收计数、代次闸、回位接缝符号全部写入；四包 `git diff --name-only HEAD` 与 `git status --porcelain` 双通道空集。
- L2 运行期：纯 JVM 单测在 CI 跑通且 0 failed；沙箱禁编译，编译与单测绿红以 CI 为准，本单不报已编译通过。
- 独立验收按本卡「验收判据」逐条对照源码与用例；判低则换项目 AI、废代码重做再交验收。

## 回执

- 基线引用：B4-01 冻结基线 docs/修复-B4-01-冻结基线.md（HEAD 6075716）；四包 diff 空集见该单第二节。
- 采集时点 HEAD：`11cac19`；本单未做 git add / commit / push；未本地编译、未跑单测（E8）。
- 守则指纹与三条逐字引用回源见报告 `docs/修复-B4-03-Sheet闸族状态簇.md` 首部「守则回执」块。
