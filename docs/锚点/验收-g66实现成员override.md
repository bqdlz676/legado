# 锚点卡 · 验收-g66 同签名实现成员缺 override 静态判据闸

- 自身路径：docs/锚点/验收-g66实现成员override.md
- 本卡路径：docs/锚点/验收-g66实现成员override.md
- 关联报告：docs/验收-复核-g66实现成员override.md
- 仓库根：/home/user/Doubao/chats/38454122521460994/legado
- 验收口径：独立只读验收；未改被验脚本、未改 app/src 产品代码，未做 git add/commit/push，未跑 gradle/kotlinc；数字由验收方在仓外 scratch 迷你目录与同一工作树自取，不采信写码方 `--selftest` 自评。

## 一、回源命令

- 事故镜像与放行/误伤探针：仓外 `/tmp/g66-verify/driver.py`，import 生产 `build_model()` / `evaluate()` 逐例断言标签、文件:行、成员名，夹具验完随临时目录自删。
- 全仓 audit：`python3 data_scripts/check-member-override.py --audit`
- 改动面判定：`python3 data_scripts/check-member-override.py --changed`
- 默认口径：`python3 data_scripts/check-member-override.py`（不带参数同样按改动面跑）
- 接线机检：`python3 data_scripts/check-gate-wiring.py`
- shell 语法：`bash -n data_scripts/rule-gate.sh`
- 报告字段：`python3 data_scripts/check-report-fields.py docs/验收-复核-g66实现成员override.md`
- 锚点卡：`python3 data_scripts/check-anchor-card.py --strict docs/验收-复核-g66实现成员override.md`
- 新发现问题：`python3 data_scripts/check-problem-finding.py docs/验收-复核-g66实现成员override.md`
- 守则回执：`python3 data_scripts/check-onboarding.py`

## 二、被验对象与接线落点

- 闸脚本：`data_scripts/check-member-override.py`（提交 `e42bae7` 入库；验收初读时为未跟踪状态）。
- 关键落点：成员正则 `:65`（FUN_RE）/ `:70`（PROP_RE）；参数归一 `:337`（param_key）；父类型提取 `:363`（_supertypes）；声明解析 `:490`（parse_file）；父类型表 `:576`（interface 全收、abstract 只收 abstract/open）；BFS 追表 `:595`（resolve_inherited）；实现方反查 `:617`（check_decl，方法红行 `:638`、属性红行 `:656`）；改动面采集 `:675`（git_kt_diff）；主判定 `:701`（evaluate）；audit 清单 `:732`；改动面 PASS 行 `:757`；自测夹具 `:784`（selftest）。
- 分级登记：`data_scripts/gate-levels.json:962`，g66，`level=blocking`，`scope=product-code`，`script=check-member-override.py --changed`，`nature=bad`，`compensation=null`。
- 编排接线：`data_scripts/rule-gate.sh:487`（`gate_active g66`），`:488`（`MO66=$(python3 data_scripts/check-member-override.py --changed 2>&1)`），`:489`（红态摘要 grep `MEMBER-OVERRIDE|FAIL`）。

## 三、七组验收判据（机械可判）

1. 真事故复现：仓外镜像接口声明 `currentTextPage` / `textPageState` / `title`，实现方同签名缺 override → 必红，命中 MEMBER-OVERRIDE 标签、文件行、成员名；补齐 override → 必绿且配对计数 3。
2. 真仓终态：`--audit` 候选缺 override 为 0；`InkReadController.kt:141` / `:149` 两条带 override；两个测试 FakeHost（GestureRouterTest.kt:72/:74、ViewGestureBridgeTest.kt:69/:71）带 override。
3. 放行面：PRIVATE / EXTENSION（不同接收者）/ 泛型实参不同 DIFF-SIG / LOCAL / COMPANION / TOPLEVEL 六类逐类仓外自构必绿。
4. 间接实现：类 A : Iface、类 B : A，B 重声明同签名缺 override → 沿链 BFS 追到 Iface，判红落点 B。
5. 已知边界：中间具体类 `open fun`、匿名对象 `object : Foo {}` 内重写、Java/Android 父类三种不追情形，用仓内实际代码举例，判设计内保守还是漏判风险。
6. 改动面与接线：`--changed` 末行自跑；gate-levels.json g66 仍 blocking 且 script 为 `--changed`；rule-gate.sh 红态摘要含 MEMBER-OVERRIDE；check-gate-wiring.py、`bash -n` 自跑。
7. 误伤探：同名不同参数普通函数、非 Kotlin（.java）实现、同名属性与方法并存、接口默认实现空体继承，四类均不误伤。

## 四、事故下限回源

- CI 红提交 `0c9fd30`：`InkReadController.kt:141` `fun currentTextPage(): TextPage?`、`:149` `fun textPageState(): InkTextPageState {` 均无 override（git show 该提交版本复核）；修红提交 `35bbb54` diff 仅两行：`-fun currentTextPage…` / `+override fun currentTextPage…`、`-fun textPageState…` / `+override fun textPageState…`。
- 接口声明：`ReaderShellHost.kt:23` `interface ReaderShellHost`；`:72` `fun currentTextPage(): TextPage?`；`:78` `fun textPageState(): InkTextPageState`。
- 实现方现状：`InkReadController.kt:141`、`:149` 均带 `override`。
- 测试 FakeHost：`GestureRouterTest.kt:27` `private class FakeHost : ReaderShellHost`，`:72` / `:74`；`ViewGestureBridgeTest.kt:29`，`:69` / `:71`。

## 五、新发现问题指针

- 问题 1（待裁，归属 g66 闸脚本维护单）：改动面文件过滤路径口径不匹配，dirty 工作树改动声明恒被排除。
- 问题 2（待裁，归属 g66 闸脚本维护单）：SKIP:COMPANION / SKIP:TOPLEVEL 计数器永不递增。
- 问题 3（待裁，归属 g66 闸脚本维护单）：主构造参数内 override 属性不建模。
- 问题 4（已处置，归属 g66 口径边界）：匿名对象表达式内重写不建模。
- 问题 5（已处置，归属 g66 口径边界）：中间具体类自带 open fun 不进父类型表。
- 问题 6（已闭环，归属编译链职责域）：Java/Android 父类方法不在表内。
- 问题 7（待裁，归属 g66 闸脚本维护单）：属性匹配只按名字，不核类型串。
