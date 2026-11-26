# 台账 · CI 红率（自动生成，`ci-red-rate.py --write` 刷新）

> 红因分类只认日志硬特征：compile（`e: file://` / Unresolved reference）／test（断言失败）／env（JDK·SDK·网络）／infra（取消·artifact）。
> 规矩：**每一次 compile 类红，当晚必须落成一条机检或规则**；落不成的，这次复盘算失败，不许写"已复盘"。

- 本次统计：失败 8 次，其中 compile 6、test 2、env 0、infra 0、unknown 0

## 明细
- 2026-11-26T03:03｜88e62bc｜Screenshot frames (Paparazzi, independent)｜failure｜红因=compile｜job=Render screenshot frames (Paparazzi, :screenshot)｜e: file:///home/runner/work/legado/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt:4:36 Unresolved reference 'Orientation'.
- 2026-11-26T03:03｜88e62bc｜Verify (unit tests)｜failure｜红因=compile｜job=Discover test classes (shard lists)｜e: file:///home/runner/work/legado/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt:4:36 Unresolved reference 'Orientation'.
- 2026-11-26T02:49｜16bc12b｜Screenshot frames (Paparazzi, independent)｜success｜红因=ok｜job=-｜-
- 2026-11-26T02:49｜16bc12b｜Verify (unit tests)｜cancelled｜红因=test｜job=Unit tests shard 4/4｜com.x.MyTest > 方法名 FAILED
- 2026-11-26T01:38｜417bad0｜Screenshot frames (Paparazzi, independent)｜success｜红因=ok｜job=-｜-
- 2026-11-26T01:38｜417bad0｜Verify (unit tests)｜failure｜红因=test｜job=Unit tests shard 2/4｜com.x.MyTest > 方法名 FAILED
- 2026-11-26T01:34｜27812e5｜Verify (unit tests)｜failure｜红因=compile｜job=Discover test classes (shard lists)｜e: file:///home/runner/work/legado/legado/app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:501:17 None of the following candidates is appl
- 2026-11-26T01:34｜27812e5｜Screenshot frames (Paparazzi, independent)｜failure｜红因=compile｜job=Render screenshot frames (Paparazzi, :screenshot)｜e: file:///home/runner/work/legado/legado/app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:501:17 None of the following candidates is appl
- 2026-11-26T00:35｜1688520｜Screenshot frames (Paparazzi, independent)｜success｜红因=ok｜job=-｜-
- 2026-11-26T00:35｜1688520｜Verify (unit tests)｜failure｜红因=test｜job=Unit tests shard 3/4｜com.x.MyTest > 方法名 FAILED
- 2026-11-26T00:31｜b15d7a4｜Verify (unit tests)｜cancelled｜红因=ok｜job=-｜-
- 2026-11-26T00:31｜b15d7a4｜Screenshot frames (Paparazzi, independent)｜success｜红因=ok｜job=-｜-
- 2026-11-26T00:21｜38960a6｜Verify (unit tests)｜failure｜红因=compile｜job=Discover test classes (shard lists)｜e: file:///home/runner/work/legado/legado/app/src/main/java/io/legado/app/ui/main/search/SearchPagingOrchestrator.kt:75:14 Syntax error: Expecting ').
- 2026-11-26T00:21｜38960a6｜Screenshot frames (Paparazzi, independent)｜failure｜红因=compile｜job=Render screenshot frames (Paparazzi, :screenshot)｜e: file:///home/runner/work/legado/legado/app/src/main/java/io/legado/app/ui/main/search/SearchPagingOrchestrator.kt:75:14 Syntax error: Expecting ').
- 2026-11-26T00:18｜76ac03b｜Verify (unit tests)｜cancelled｜红因=ok｜job=-｜-
- 2026-11-26T00:18｜76ac03b｜Screenshot frames (Paparazzi, independent)｜cancelled｜红因=ok｜job=-｜-
- 2026-11-26T00:17｜71fef11｜Verify (unit tests)｜cancelled｜红因=ok｜job=-｜-
- 2026-11-26T00:17｜71fef11｜Screenshot frames (Paparazzi, independent)｜cancelled｜红因=ok｜job=-｜-
- 2026-11-26T00:16｜f2ce70d｜Screenshot frames (Paparazzi, independent)｜cancelled｜红因=ok｜job=-｜-
