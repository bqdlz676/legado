锚点卡：docs/锚点/报告字段闸quotepath.md

# 修复-报告字段闸 quotepath 中文路径识别

## 一、背景与下限

两道独立验收都撞到同一处：`data_scripts/check-report-fields.py` 聚合口径调 `git status` 取本轮新增/修改报告时，没有带 `core.quotepath=false`。git 默认把非 ASCII 路径转义成八进制串（如 `"docs/\344\276\244\276\223..."`），脚本按行首切片剥引号后拿这串转义码去匹配前缀正则 `docs/(修复|验收|组装|分析)-.*\.md$`，自然匹配不到；仓内报告绝大多数是中文名，于是聚合整批漏判，直接报"本轮无报告改动"。显式传路径时不走 `git status`，所以直调能正常判——这也是问题只在聚合口径暴露的原因。

硬边界：不改 `app/src` 任何产品代码；只改 `data_scripts/check-report-fields.py` 这一个脚本与两份 docs 产物；不动其它脚本的判据；不做 git add/commit/push。

## 二、改动面清单

- `data_scripts/check-report-fields.py:57`：新增 `is_anchor_card()` 分类辅助；`docs/锚点/` 下的前置设计卡不是报告，显式直传时同样跳过，不按报告四件套判（口径同锚点守卫的 other 分支）。
- `data_scripts/check-report-fields.py:64`：新增 `_porcelain_path()` 解析辅助；重命名行 `R  old -> new` 取箭头右侧新路径，剥掉成对双引号/单引号。
- `data_scripts/check-report-fields.py:81`：聚合 status 命令补 `-c core.quotepath=false`（路径按 UTF-8 原文输出）与 `-uall`（未跟踪目录展开成文件级条目，不被折叠成 `?? docs/`）。
- `data_scripts/check-report-fields.py:89`：路径归一——反斜杠转正斜杠、`os.path.normpath` 规范化 `.`/`..` 段后再判前缀。
- `data_scripts/check-report-fields.py:252`：空集分支改报 `EMPTY`、退出码 2，不再把"没有可核验对象"表述成 PASS（空集不判 PASS，同 ⑲ 锚点卡与 55 永远找问题口径）。
- 判据原样保留：A 锚点卡、B 结论标记、C 反例/推演节、D 证据锚点五条字段判据，以及 `only_backfill()` 纯回执补录豁免、`is_fixture()` 夹具排除，一行未动。

## 三、复现与对照（修复前 → 修复后）

修复前用原口径在本仓实跑：`git status --porcelain` 输出的中文行带八进制转义，前缀正则识别到 0 份报告，聚合末行为 `REPORT-FIELDS: PASS（本轮无报告改动）`。同一条 status 放开 `-c core.quotepath=false` 后，识别到三份在飞中文报告：`docs/修复-B4-08-Resolver接线.md`、`docs/修复-g65消费方反查.md`、`docs/修复-g66实现成员override.md`。

修复后聚合口径在本仓实跑三份均 PASS；显式路径单传 `docs/修复-g65消费方反查.md` 直判 PASS，行为与修复前一致。

## 四、自测与机检真实末行

本闸自测与各门禁实跑结果（2026-11-27 本仓工作区实跑，末行为命令真实末行）：

- `python3 data_scripts/check-report-fields.py --selftest`
  末行：`[selftest] 夹具已自删，双向自测通过。`（rc=0；夹具：好样本 PASS、坏样本 FAIL、解析单元三例（重命名右侧/引号剥离/普通行）、迷你仓中文+英文未跟踪报告均识别、显式路径直判 PASS、空仓 EMPTY 退出码 2、非报告文件不混入）
- 聚合模式：`python3 data_scripts/check-report-fields.py`
  末行：`REPORT-FIELDS: PASS（3 份，0 份不合格）`（rc=0；本轮工作区在飞报告随他槽提交进出，份数以实跑为准，本单报告在列 PASS）
- 显式路径（两份 docs 自判）：`python3 data_scripts/check-report-fields.py docs/修复-报告字段闸quotepath.md docs/锚点/报告字段闸quotepath.md`
  末行：`REPORT-FIELDS: PASS（2 份，0 份不合格）`（rc=0；报告判 PASS，锚点卡按分类辅助跳过）
- `python3 data_scripts/check-anchor-card.py --strict docs/修复-报告字段闸quotepath.md docs/锚点/报告字段闸quotepath.md`
  末行：`汇总: PASS=2 FAIL=0（共 2 份）`（rc=0）
- `python3 data_scripts/check-problem-finding.py docs/修复-报告字段闸quotepath.md`
  末行：`PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`（rc=0）；裸跑末行：`PROBLEM-FINDING: PASS（现行通过 3 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`（rc=0；份数以实跑工作区为准）
- 回执门禁（onboarding）：`check-onboarding.check_report()` 直调本单报告，probs=`[]`；裸跑 `python3 data_scripts/check-onboarding.py`
  末行：`ONBOARD: PASS（3 份报告）`（rc=0；份数以实跑工作区为准）
- 二阶复核：`python3 data_scripts/check-vacuous-pass.py`
  末行：`VACUOUS-PASS: PASS（未发现未豁免的空集假绿结构；8 处已按哈希锚定登记豁免）`（rc=0；本闸旧空集豁免条目已随分支改 return 2 自动失配，见第六节登记）

证据锚点：
- `data_scripts/check-report-fields.py:57`（`is_anchor_card` 分类跳过）
- `data_scripts/check-report-fields.py:64`（`_porcelain_path` 解析）
- `data_scripts/check-report-fields.py:81`（status 命令 quotepath 与 `-uall`）
- `data_scripts/check-report-fields.py:89`（路径归一）
- `data_scripts/check-report-fields.py:252`（空集 EMPTY 分支）
- `data_scripts/check-problem-finding.py:208`（同族 quotepath 放开先例）
- `data_scripts/check-anchor-card.py:239`（空集 EMPTY 退出码 2 先例）
- `data_scripts/cross-module-guard.py:38`（待登记同类缺口）
- `data_scripts/kotlin-syntax-lint.py:474`（待登记同类缺口）

## 五、自查反例（改坏推演）

- 反例一：若只补 quotepath 而不剥重命名箭头，`R  old -> new` 行会取到旧路径，改名报告要么匹配不到前缀、要么拿已删除的旧路径去 `check()`，报"文件不存在"假红；实现里 `_porcelain_path()` 先按 ` -> ` 切右侧再剥引号。
- 反例二：若只放开 quotepath 而不加 `-uall`，全新目录里的未跟踪文件仍被 git 折叠成 `?? docs/` 目录条目，文件级前缀匹配照样落空；迷你仓夹具三专门覆盖这一形态。
- 反例三：若顺手放宽 A/B/C/D 字段判据来迁就本单报告，等于把闸口拆了换绿灯；本单只动路径获取与空集表述，判据函数 `check()` 一字未改，夹具一/二仍分别走原判据 PASS/FAIL 双向验证。
- 反例四：若把空集分支直接 `return 0` 留着旧 PASS 文案，空集假绿被 ㊾ 口径明令禁止；改为 EMPTY + 退出码 2 后，空集不再被表述成合规，同时旧的空集豁免条目随块指纹变化自动失配（见第六节登记）。
- 反例五：若路径不归一就判前缀，反斜杠分隔或夹带 `.`/`..` 段的路径会让正则落空；归一仅做形态规范化，不改变"哪些文件算报告"的判据边界。

### 认知成本

路径获取收口到 `_porcelain_path()` 一个纯函数，与判据 `check()` 分离；后续若要新增报告前缀规则，只动 `changed_reports()` 里那一处正则。命名沿用仓内同族口径（`changed_reports` / `is_fixture` / `is_anchor_card`），未引入新抽象层，接手者读一个函数即可看清"路径从哪来、怎么剥、怎么归一"。

### 未验证项

- 非 ASCII 文件名的代码件在 `cross-module-guard.py:38`、`kotlin-syntax-lint.py:474` 两处仅按代码扫描推断隐患，仓内无此类样本，未实证误判，留待有中文命名代码件时复核。
- `pre-push-check.sh:99` 的 EMPTY（rc=2）与真红（rc=1）分流属接线层改动，本单未动，干净树本地预推的行为变化见第六节登记。
- 他槽在飞报告的合规结论以各自门禁实跑为准，本单不代判；聚合份数随工作区进出变动。

## 六、新发现问题

- `data_scripts/cross-module-guard.py:38`（解析 :43、后缀过滤 :45）：`run("git", "status", "--porcelain")` 未带 `-c core.quotepath=false`；路径剥引号后按 `.kt/.java/.xml` 后缀过滤，非 ASCII 文件名代码件会被八进制转义串挡住后缀匹配、漏出跨模块消费方扫描的改动面。归属：跨模块守卫（删除/修改成员消费方反查）。状态：待裁（观察）——仓内代码路径当前全 ASCII，未实证误判；本单白名单外不改，仅登记。
- `data_scripts/kotlin-syntax-lint.py:474`（解析 :477、`.kt` 过滤 :478）：status 调用未带 quotepath=false，随后按 `.kt` 后缀收集文件并 `open()` 读取；非 ASCII 名的 Kotlin 文件会漏扫，落到全量 glob 兜底之外。归属：3.6 Kotlin 语法预检。状态：待裁（观察）——当前仓内 `.kt` 路径全 ASCII，未实证漏判；本单不改。
- `data_scripts/check-vacuous-pass.py:88`：本闸旧空集分支在豁免表里的条目（变量 `paths`，块指纹 `53fabe213b5e`）随空集分支改为 `return 2` 已不再被扫描命中，成为死条目；豁免表按三元组哈希锚定，死条目不影响现行判定，但下次维护该表时应摘除。归属：空集假绿检测器。状态：待裁。
- `data_scripts/pre-push-check.sh:99`：步骤 3.5 由 `run_step` 统一按退出码非 0 记失败，未分流 EMPTY（退出码 2）与真红（退出码 1）；本闸空集改退出码 2 后，干净树本地预推会把 EMPTY 提示记成步骤失败。归属：pre-push 接线层。状态：待裁——建议 shell 层把退出码 2 识别为 EMPTY 提示放行，退出码 1 才记红。
- `data_scripts/acceptance-evidence.py:85` 与 `data_scripts/check-unpushed-reports.py:50`：两处 status 包装器已放开 quotepath，但 porcelain 重命名行 `R  old -> new` 按行首切片取到旧路径，未取箭头右侧，与 55 闸 Q-221② 修过的同族缺口相同。归属：验收证据包 / 未推送报告扫描。状态：待裁——本单只登记，不在白名单内改。

## 七、守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「永远找问题，解决问题，提出问题」（docs/机制-总控必读.md:546）
- 适用条款：改动后先 grep 回证、未生效不报完成；规格未覆盖边界自行兜底不降级不砍功能；每轮登记新发现问题并逐条带处置状态；空集不判 PASS。

## 八、总结论

PASS：`data_scripts/check-report-fields.py` 聚合口径已带 `-c core.quotepath=false` 与 `-uall`，路径解析剥引号、取重命名右侧并归一后再判前缀；中文未跟踪/修改报告可被识别并进入判定，显式传路径通路不变，空集报 EMPTY 退出码 2 不判 PASS。selftest 六例双向通过，聚合与显式两份 docs 自判 PASS，锚点卡 strict、永远找问题、守则回执三闸本单报告均 PASS；vacuous-pass 复核绿。A/B/C/D 字段判据、补录豁免与夹具排除口径原样未动；仓内其它缺 quotepath 的调用点只在第六节登记，未在本单白名单内修改。未做 git add/commit/push。
