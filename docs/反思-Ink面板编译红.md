# 反思 · Ink 面板编译红（拆 731 行面板漏带 import）

1. **做了什么**：基线 HEAD `88e62bc`，修 3 个拆分文件编译红——Editor 改 `Orientation`/`TextFieldValue` 包名、`ScrollState.value` 私有写值改 `scrollTo`/`dispatchRawDelta`；Footer 补 `dp` import；Rules 的 `projectEdit` 降 `internal`。报告 `docs/修复-Ink面板编译红.md`。

2. **踩的坑 / 教训**：
   - 拆文件时 import 不会自动随迁，手抄包名容易落到父包（`foundation` 而非 `foundation.gestures`、`ui.text` 而非 `ui.text.input`）。下任遇到 `Unresolved reference`，先 grep 仓内已编译基线同符号的 import，再解包依赖 aar 类路径实证，别凭记忆写包名。
   - `ScrollState.value` 是「读公开、写私有」，直写 setter 才报 private；读 `value`/`maxValue` 合法。滚动写入只有 `scrollTo`/`scrollBy`/`dispatchRawDelta` 三个公开入口。

3. **我可能判断错的地方**：`dispatchRawDelta` 内部夹取到 `0..maxValue` 是按公开 API 口径推断，未真机逐帧验证；若 CI 报签名不符，备选 `scrollBy(Int)`。编译是否全绿以云端 CI 为准，本单沙箱未编译。

4. **遗留未决项**：4 个拆分文件其余符号仅做基线包名对照 + 语法 lint，未过真实编译器；等 CI `compileAppDebugKotlin` 结果。

5. **给下任的一句话提醒**：`ScrollState` 的公开写入方法名与夹取口径必须回仓库依赖 aar / 基线文件复核，不要照抄本反思的结论；工作区里其他在飞 docs / 脚本改动不属于本单，不要顺手提交。
