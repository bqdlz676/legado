# 反思 · Crossing 引擎 B4 兜底返工

1. **做了什么**：基线 HEAD `1681f7d`（B9 已入 main）。将 `load` 的 parse 纳入顶层 try；加 internal 装配故障桩 + 2 条测试；rivers JsonNull 补字段级 warning；实测行号勘误。改动 3 个白名单文件。

2. **踩的坑 / 教训**：
   - 白名单只含 ControllerTest，最初把 JsonNull 断言写进了 ParserTest，发现越界后撤回、迁至 ControllerTest；开工前必须先逐字核对白名单文件名。
   - catch 闭包会捕获局部 `var`，Kotlin 智能转型可能失效；parse 结果存局部 `val parsed` 再传入 assemble，避免 `!!`。

3. **可能判断错的地方**：`@JvmField internal var` 在 companion 内的字节码可见性、`degradedFallback().copy(warnings=…)` 能否编译通过，均未经 kotlinc 实跑，属推断成立，待 CI 证实。

4. **遗留未决项**：25+15 条单测未实跑；parse 漏网异常（深层递归 StackOverflow）仍无构造器可注入，修 1 仅靠 try 包裹 + grep 回证；壳层多条 warning 只展示首条未动。

5. **给下任的一句话提醒**：报告里所有行号是本单工作树实测，B9 后续再插代码会继续漂移；接手先 `grep -n` 复核函数落点，不要照抄本文件行号。
