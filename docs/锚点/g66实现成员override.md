# 锚点卡 · g66 同签名实现成员缺 override 静态判据

- 自身路径：docs/锚点/g66实现成员override.md
- 本卡路径：docs/锚点/g66实现成员override.md
- 关联报告：docs/修复-g66实现成员override.md
- 仓库根：/home/user/Doubao/chats/38454122521460994/legado
- 改动面（本单允许写的文件域）：`data_scripts/check-member-override.py`、`data_scripts/rule-gate.sh`、`data_scripts/gate-levels.json`、本报告与本卡；未改 `app/src` 任何产品代码，未做 git add/commit/push。

## 一、回源命令

- 门禁自测：`python3 data_scripts/check-member-override.py --selftest`
- 改动面判定：`python3 data_scripts/check-member-override.py --changed`
- 全仓 audit 清单：`python3 data_scripts/check-member-override.py --audit`
- 门禁接线：`python3 data_scripts/check-gate-wiring.py`
- shell 语法：`bash -n data_scripts/rule-gate.sh`
- 报告字段：`python3 data_scripts/check-report-fields.py docs/修复-g66实现成员override.md`
- 锚点卡：`python3 data_scripts/check-anchor-card.py --strict docs/修复-g66实现成员override.md`
- 新发现问题：`python3 data_scripts/check-problem-finding.py docs/修复-g66实现成员override.md`
- 守则回执：`python3 data_scripts/check-onboarding.py docs/修复-g66实现成员override.md`

## 二、事故下限（真红回源）

- CI 红提交 `0c9fd30`：日志原句 `InkReadController.kt:141:9 'currentTextPage' hides member of supertype 'ReaderShellHost' and needs an 'override' modifier`；修红提交 `35bbb54` 只补两个 override 关键字。
- 接口声明：`app/src/main/java/io/legado/app/ui/book/read/ink/ReaderShellHost.kt:23`（`interface ReaderShellHost`），`currentTextPage(): TextPage?` 在 `:72`，`textPageState(): InkTextPageState` 在 `:78`。
- 实现方现状（已补 override）：`app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt:141` 与 `:149`。
- 测试 FakeHost 本来就有 override：`app/src/test/java/io/legado/app/ui/book/read/ink/GestureRouterTest.kt:72` / `:74`；`app/src/test/java/io/legado/app/ui/book/read/ink/ViewGestureBridgeTest.kt:69` / `:71`。
- 当时 g63（NODEF/ARITY/PACKAGE 全 0）、g64、g65、compose 陷阱、lint 全绿，无静态闸能拦——判据空白由本闸补。

## 三、判据锚点（文件:行）

- `data_scripts/check-member-override.py:164`：`_mask` 等长掩码，串内字符换空格，括号计数只认代码位，转义字符不再让深度漂移。
- `data_scripts/check-member-override.py:250`：`_balanced_block` 按掩码配平取类体，内层花括号保留供嵌套深度统计。
- `data_scripts/check-member-override.py:337`：`param_key` 参数表归一（个数 + 类型串；去注解、vararg、参数名、默认值、可空后缀、协变逆变标记）。
- `data_scripts/check-member-override.py:490`：`parse_file` 抽接口/抽象类/类/对象声明；配平在原文上做，成员正则用剥注释后的体。
- `data_scripts/check-member-override.py:595`：`resolve_inherited` 沿父类型链 BFS 追接口/抽象类成员表（间接实现口径）。
- `data_scripts/check-member-override.py:617`：`check_decl` 实现方反查；同签名缺 `override` 落 MEMBER-OVERRIDE 红行（方法 `:638`、属性 `:656`）。
- `data_scripts/check-member-override.py:701`：`evaluate` 主判定；改动面文件集与放行计数汇总。
- `data_scripts/check-member-override.py:732`：`--audit` 全仓清单行，只登记不判红。
- `data_scripts/check-member-override.py:757`：改动面 PASS 末行 `MEMBER-OVERRIDE: PASS`。
- `data_scripts/check-member-override.py:784`：`selftest` 九例夹具（事故镜像红 / 补 override 绿 / 六类放行 / 间接实现沿链红）。
- `data_scripts/rule-gate.sh:487`：g66 段接线，调 `check-member-override.py --changed`，红态摘要命中 `MEMBER-OVERRIDE`。
- `data_scripts/gate-levels.json:962`：g66 登记，level=blocking，scope=product-code，script=`check-member-override.py --changed`，compensation=null。

## 四、放行类别（误报防制，均有独立夹具断言）

- SKIP:PRIVATE：private 成员不参与 override 解析（`check-member-override.py:857`）。
- SKIP:EXTENSION：不同接收者的扩展函数 `fun Recv.name(...)`（`:873`）。
- SKIP:DIFF-SIG：同名同参个数但归一类型串不同（泛型实参不同等），不算同签名（`:885`）。
- SKIP:LOCAL：函数体内部局部函数，深于类体一层（`:933`）。
- SKIP:COMPANION：伴生对象内同名成员（`:916`）。
- SKIP:TOPLEVEL：接口/类体外普通同名顶层函数（`:900`）。

## 五、口径边界与取舍

- 间接实现（A : Iface、B : A）：沿父类型链 BFS 追到 Iface 成员表，B 重声明同签名同样要 `override`（夹具九）。
- 不纳入表内：Java/Android 父类（Fragment、MutableMap 等）方法；中间具体类自带的 `open fun` 未进父类型表——静态闸拿不到其完整 open 契约，漏判一条留人工复查，不硬凑。
- 默认 `--changed` 只判相对 origin/main 的改动面；`--audit` 全仓只出清单，存量不从 0 变红墙。
