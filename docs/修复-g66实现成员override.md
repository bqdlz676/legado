锚点卡：docs/锚点/g66实现成员override.md
> 漂移重判：原锚已失效，本结论标注为已废（2026-11-27 W-436）——原因：B.kt / Iface 为成员 override 判据的仓外 /tmp 夹具，验毕自删、仓内不存在，非源码锚点；判据以 data_scripts/check-member-override.py 现行实现为准。

# 修复-g66 同签名实现成员缺 override 静态判据闸

## 一、背景与下限

真实事故：CI 红提交 `0c9fd30` 三个 Verify job 全红，日志原句 `InkReadController.kt:141:9 'currentTextPage' hides member of supertype 'ReaderShellHost' and needs an 'override' modifier`；修红提交 `35bbb54` 只补了两个 `override` 关键字。接口 `ReaderShellHost` 在 `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderShellHost.kt:23`，声明 `currentTextPage(): TextPage?`（`:72`）与 `textPageState(): InkTextPageState`（`:78`）；实现方 `InkReadController.kt:141` / `:149` 同签名声明却没写 `override`。当时 g63 符号门禁 NODEF/ARITY/PACKAGE 全 0，g64 单测判据真值、g65 消费方反查、compose 陷阱、lint 全绿，没有一个静态闸能拦——这就是判据空白。本单把「实现方同签名重声明父类型成员必须带 `override`」机械成 g66 闸。

硬边界：不改 `app/src` 任何产品代码；只新增 `data_scripts/check-member-override.py`，在 `data_scripts/gate-levels.json:962` 与 `data_scripts/rule-gate.sh:487` 接线；不做 git add/commit/push。

## 二、改动面清单

- 新增 `data_scripts/check-member-override.py`：接口/抽象类成员表建模（`:490`）、参数签名归一（`:337`）、父类型链 BFS 追表（`:595`）、实现方反查与 MEMBER-OVERRIDE 红行（`:617`，方法 `:638`、属性 `:656`）、主判定（`:701`）、全仓 audit（`:732`）、改动面 PASS 行（`:757`）、九例自测夹具（`:784`）。
- `data_scripts/gate-levels.json:962`：登记 g66，level=blocking，scope=product-code，script=`check-member-override.py --changed`，compensation=null。
- `data_scripts/rule-gate.sh:487`：g66 段接线，调用 `python3 data_scripts/check-member-override.py --changed`，红态摘要命中 `MEMBER-OVERRIDE`。
- `docs/锚点/g66实现成员override.md`：本单锚点卡，事故下限、判据锚点、放行类别、口径边界逐条回源。

## 三、判据实现

1) 建接口表：扫仓内 `interface` 与 `abstract class` 的成员方法签名（名字 + 参数个数 + 参数类型串归一）与属性成员名。接口方法全部入表；抽象类只收 `abstract`/`open` 成员——Kotlin 类成员默认 final，final 同名不是被重写候选，进表只会误报。归一去注解、vararg、参数名、默认值（`Boolean = true` 与 `Boolean` 同签名）、可空后缀、协变/逆变标记，泛型实参保留（`List<String>` 与 `List<Long>` 源码签名不同）。
2) 扫实现方：对所有直接或间接继承该接口/抽象类的类/对象体，内层同签名声明缺 `override` 判 MEMBER-OVERRIDE 红，点名 文件:行、成员名、父类型；同名字段/属性同理。已带 `override` 的记配对计数。
3) 误报防制六类放行，各有独立夹具断言类别标签与文件行：
   - SKIP:PRIVATE：private 成员不参与 override 解析（`data_scripts/check-member-override.py:857`）。
   - SKIP:EXTENSION：不同接收者的扩展函数 `fun Recv.name(...)`（`:873`）。
   - SKIP:DIFF-SIG：同名同参个数但归一类型串不同，不算同签名（`:885`）。
   - SKIP:LOCAL：函数体内部局部函数，深于类体一层（`:933`）。
   - SKIP:COMPANION：伴生对象内同名成员（`:916`）。
   - SKIP:TOPLEVEL：接口/类体外普通同名顶层函数（`:900`）。

间接实现取舍：类 A : Iface、类 B : A，按 BFS 沿父类型链把 Iface 成员表追进 B；B 重声明同签名成员同样要 `override`（夹具九独立判红，落点 `B.kt:2`）。只追仓内 Kotlin 声明的链；Java/Android 父类（Fragment、MutableMap 等）不在声明表内，天然不误判；中间具体类自带的 `open fun` 不进父类型表，静态闸拿不到其完整 open 契约，漏判一条留人工复查，不硬凑。

口径：默认 `--changed` 只判相对 origin/main 的改动面；`--audit` 全仓只出清单，不从 0 变红墙（存量债务走台账，不进 blocking 改动面）。

## 四、自测与机检真实末行

- `python3 data_scripts/check-member-override.py --selftest`
  末行：`[selftest] 夹具已自删，双向自测通过（事故镜像红/补 override 绿；PRIVATE/EXTENSION/DIFF-SIG/TOPLEVEL/COMPANION/LOCAL 六类放行；间接实现沿链判红）。`（rc=0；九例夹具：事故镜像三红、补 override 三配对、private 放行、扩展接收者放行、泛型实参异签名放行、顶层同名放行、伴生同名放行、局部函数放行、间接实现沿链红。每个坏样本与放行样本单独跑并断言类别标签 + 文件行，不只比退出码。）
- `python3 data_scripts/check-member-override.py --changed`
  末行：`MEMBER-OVERRIDE: PASS（改动面 0 个实现声明；缺 override 0；已配对 override 0；放行 ）`（rc=0；本轮改动面无 .kt 产品代码）
- `python3 data_scripts/check-member-override.py --audit`
  末行：`MEMBER-OVERRIDE AUDIT: 全仓清单（接口/抽象类 130 个 / 实现声明 227 个 / 候选缺 override 0 处 / 已配对 override 527 处；存量只登记不判红）`（rc=0；全仓 audit 只出清单，不挡 push）
- `python3 data_scripts/check-gate-wiring.py`
  末行：`GATE-WIRING: PASS（60 个门禁均接线；历史孤儿基线 0 个）`
- `bash -n data_scripts/rule-gate.sh`
  结果：`bash -n OK`（语法通过，无输出错误）

真仓回源复核：`InkReadController` 对 `ReaderShellHost` 11 个成员全部配对 override，0 红；两个测试 FakeHost（`GestureRouterTest.kt:72` / `:74`、`ViewGestureBridgeTest.kt:69` / `:71`）同样 11 个全配对，0 红。

## 五、自查反例（改坏推演）

- 反例一：若配平直接复用剥注释后的文本，字符转义 `'\n'` 的第二字符被吃掉，闭引号会被掩码当转义吞掉，括号深度逐级漂移，类成员被误判成局部函数（实测 `InkReadController` 一度 24 个方法落 LOCAL）。实现改为配平在原文上做、成员正则才用剥注释后的体（`data_scripts/check-member-override.py:490`），掩码等长替换串内字符（`:164`），转义位双空格保长度。
- 反例二：若抽象类所有成员都进父类型表，Kotlin 默认 final 的同名方法会被误报成缺 override。实现只把抽象类的 `abstract`/`open` 成员入表，普通 final 成员放行。
- 反例三：若类型串保留默认值（`Boolean = true`），接口带默认参、实现不带默认参的真实 override 会被算成异签名漏判（实测六例 `pauseReadAloud` / `upSpeechRate` / `upContent` 一度落 DIFF-SIG）。归一在 `param_key` 里切掉 `=` 之后的默认值（`:337`），六例回到配对。
- 反例四：若全仓存量一上来就 blocking 红，227 个实现声明里的历史形态会把闸压成红墙。实现默认只判改动面，全仓走 `--audit` 清单；存量候选 0 处也只登记，不挡 push。
- 反例五：若把扩展函数、局部函数、伴生同名、顶层同名都当类成员匹配，同名不同义的写法会成片误红。六类放行各有独立夹具打标签，标签计数进末行，放行可审计。

## 六、新发现问题

- 中间具体类自带 `open fun` 重声明未纳入父类型表 —— 待裁：静态闸只拿到仓内接口/抽象类标注，拿不到具体类 open 成员的完整契约；若后续 CI 出现具体类链上的 hides-member 红，再扩表追具体类 open 成员。归属：g66 后续迭代。
- 匿名对象表达式 `object : Foo { }` 内的重写成员未独立建模 —— 待裁：当前按类体一层成员扫描，匿名对象体随属性初始化落在局部层；真红样本出现后再补匿名对象块递归。归属：g66 后续迭代。
- 主构造参数含嵌套括号或函数类型时，参数列表括号正则可能截断，父类型识别落空 —— 已处置：兜底为该声明不纳入子类集合，漏判一条留人工复查，不在本单扩成全括号解析（与 g65 同一边界口径）。归属：g66/g65 共用解析边界。
- Java/Android 父类方法的 override（如 `removeEldestEntry`）不在本闸表内 —— 已处置：这是有意边界，Java 契约不在仓内 Kotlin 声明表，天然不误判，也不归本闸拦。归属：编译链职责域。

## 七、守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「永远找问题，解决问题，提出问题」（docs/机制-总控必读.md:546）
- 适用条款：B2（改后 grep 回证再跑机检）、C3（边界自行兜底不降级不砍功能）、F8（新发现问题逐条带处置状态）、g66 本闸判据（接口表 + 实现方反查 + 六类放行 + 改动门口径）。

## 八、总结论

PASS：g66 同签名实现成员缺 override 闸脚本、gate-levels.json 登记、rule-gate.sh 接线、锚点卡与本报告全部落盘；selftest 九例双向通过，改动面 PASS，全仓 audit 清单 0 候选不挡 push，接线 60 门禁全绿，shell 语法通过。未改 app/src 产品代码，未做 git add/commit/push。
