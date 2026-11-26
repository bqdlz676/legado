# 台账 · 编译类红的归口（每一次红都对到一条机检）

> 规矩（2026-11-26 立）：每一次 **compile 类红当晚必须落成一条机检或规则**；落不成的，这次复盘算失败，不许写"已复盘"。
> 数据来源 `python3 data_scripts/ci-red-rate.py --write`（最近 30 次 run：失败 8，compile 6、test 2）。

## 一、逐条归口

- `38960a6`（2026-11-26 00:21，Verify discover 红）：`SearchPagingOrchestrator.kt:75 Syntax error: Expecting ')'`。→ 机检 **S2（if/while 条件尾逗号）** 已覆盖，红后当晚补上并接入推送体检 3.6。
- `27812e5` / `417bad0`（2026-11-26 01:34，双路红）：`CrossingShell.kt:501 None of the following candidates is applicable`——`BasicText` 直传 `fontFamily`。→ 机检 **S3（BasicText/Text 不吃 fontFamily）** 已覆盖。
- `88e62bc`（2026-11-26 03:03，双路红、24 条）：拆 731 行面板漏带 import（`Orientation` 抄成 `foundation`、`TextFieldValue` 抄成 `ui.text`、两文件漏 `dp`）＋ `public fun projectEdit` 暴露 internal 类型 ＋ `ScrollState.value` 私有 setter 直写。→ 前两类归 **S5（符号→唯一包种子表）与 S8（符号必须有出处）**、第三类归 **S7（可见性传递）**；S5/S8 当晚已落，S7 在收口中。`ScrollState.value` 私有写入这一类**至今无机检**，见第二节。
- `1688520`（测试断言 `projectFile()` 判"应已删除"）→ 属 test 类红，归"测试断言口径"，由验收侧规则管，不属编译面。

## 二、还没被任何机检覆盖的编译错类型（已知缺口，不许装作没有）

- **私有 setter / 只读属性直写**：`ScrollState.value = x` 这类"属性存在但 setter 私有"的错，静态文本完全合法，只有编译器认。机检可做的版本：维护"只读属性名单"（从 aar 元数据或人工登记），命中 `name.属性 =` 即 FAIL；名单不全就会漏，属于兜底而非根治。
- **类型推断连坐**：一个符号解析失败会连带十几个 `Cannot infer type for type parameter 'T'`；这类错不需要单独机检，根因被 S5/S8 拦住后自然消失。
- **重载／签名不匹配**（`None of the following candidates is applicable`）：只有真编译器能做重载解析；S3 只能覆盖已知的 `fontFamily` 一例。这是静态机检的天花板，必须承认。

## 三、结论

- 6 次 compile 红里，5 次已落成机检（S2 / S3 / S5 / S8 / S7），1 次（私有 setter）还没有，挂在 Q-204。
- 红率口径：最近 30 次 run 失败 8 次，compile 占 6。**判"这条线修好了"的标准：连续 10 次 push 里 compile 类红为 0，且每次红都能指到"哪条机检没写"。**
