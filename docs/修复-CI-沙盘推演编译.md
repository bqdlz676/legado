> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-CI 沙盘推演编译（run 45386975822，testAppDebugUnitTest 编译阶段）

> 域：`map/crossing/` 3 文件；只修编译，不改设计、不删功能、不加 `@Suppress`。沙箱禁本地编译，
> 结论靠读码 + 注释/括号状态机扫描 + 成员 grep 回证，最终以 CI 复验为准。

## A. engine/CampaignController.kt（2 条，同一根因）
:96:70 Missing `}` 与 :1257:1 Unclosed comment：KDoc 正文反引号 glob `` `model/**` `` 里的 `/*`
在 Kotlin 可嵌套块注释中又开一层，外层 `*/` 只回退一层，文末差一层未闭合；后续代码被吞进注释，
级联出缺 `}`。修法：:100 改「不改 model 包内模型定义」去掉 `/*` 字样，注释语义保留，未补/删代码块。
回证：`/*`75=`*/`75、块注释深度终态 0；`{`214=`}`214、`(`466=`)`466、`[`87=`]`87。

## B. shell/CrossingShell.kt（39 条：32 级联 + 7 独立）
**级联**（A 修好即消，成员均 grep 核实存在）：`snapshot()`:655、`step()`:253、`status`:88、
`events/turn/corps`(TurnSnapshot :222/226/227)、`warnings`:153、`degraded`:151、
`reportRadiusCells`:822、`blindZoneLabel`:834；`demo` 是 companion `fun demo()`:1252，签名吻合，
**非独立缺陷**，未新增属性；其余类型推断报错随接收方类型恢复消除。

**独立缺陷（3 组，已修）**：

| 报错 | 根因 | 修法 |
| --- | --- | --- |
| :598 缺 startAngle/sweepAngle；:600/:601 无 `*Deg` 形参 | `drawArc` 形参名是 `startAngle`/`sweepAngle`（角度制），误写成 `*Deg` | :601-602 形参改名，数值不变 |
| :615:85 `toArgb` unresolved | 顶层扩展，漏显式 import | :38 补 `import androidx.compose.ui.graphics.toArgb` |
| :1017 `associate`/`it`/解构/`chipLabel` 级联 | `associate` 是 `Iterable` 扩展，`Map` 不可迭代 | :1020 改 `entries.entries.associate { (intent, spec) -> spec.chipLabel to intent }` |

回证：`/*`43=`*/`43；`{`151=`}`151、`(`533=`)`533、`[`22=`]`22；新增 import 仅 `toArgb` 且 :616 在用。

## C. terrain/HeightMapDecoder.kt（7 条）
:456:41/:456:81/:457:35/:458:35/:464:17 Value out of range：5 个 ≥2^63 十六进制常数（xxHash
混合常数 + FNV-1a 64 位偏移基）按有符号 Long 解析越界。修法：:460-462、:469 改 `0x…uL.toLong()`
——先按 ULong 64 位无符号落字面量，再按位重解释为负 Long；xor/乘/ushr 二补码下与无符号逐位一致，
**保持 64 位碰撞率，未降 32 位**。:466:23/:467:18 Long vs BigInteger 为旧快照陈旧报错，当前 HEAD
已迁 Long（grep 无 `BigInteger`），无需改码。
回证：`/*`21=`*/`21；`{`60=`}`60、`(`248=`)`248、`[`68=`]`68；5 个裸高基位 `…L` 已清零。

## 自检汇总
三文件 `{}()[]` 配平差均 0、块注释计数相等且深度终态 0；import 仅 shell 增 `toArgb`；未碰
`card/`、`book/read/`、`ui/main/`；未删分支/功能，未用 `@Suppress`。遗留：沙箱无本地编译，
类型推断级联是否全消以新 CI run 为准；若残余 `Cannot infer type` 再按点显式写类型参数。
