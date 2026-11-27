锚点卡：docs/锚点/验收-报告字段闸quotepath.md

# 验收-复核-报告字段闸 quotepath 路径识别（独立验收单）

## 一、验收对象与只读边界

- 被验件：`data_scripts/check-report-fields.py`；工作树与 HEAD 一致（`git diff HEAD --stat -- data_scripts/check-report-fields.py` 无输出，`WORKTREE==HEAD`）。旧版对照取自 `git show 4cbb174:data_scripts/check-report-fields.py`（落 /tmp 只读执行，未在主仓留副本）。
- 本单全程只读：未改任何被验脚本与产品代码，未做 git add/commit/push，未跑 gradle/kotlinc；重命名样本在 /tmp 临时迷你 git 仓内构造并还原，主仓工作区未被夹具污染。
- 验收口径：所有计数与末行由验收方自取命令实跑，不采信写码方自报；写码方登记的同类缺口清单逐条自行 grep 复核。

## 二、判据 1 · 必失败样本复现（新旧对照）

- 新版聚合实跑命令：`python3 data_scripts/check-report-fields.py`（当下工作区）。
- 新版真实末行：`REPORT-FIELDS: PASS（3 份，0 份不合格）`，rc=0；当轮在飞中文报告 `docs/验收-复核-B4-08Resolver接线.md`、`docs/验收-复核-g65消费方反查.md`、`docs/验收-复核-g66实现成员override.md` 均逐份 PASS（在飞件随并发工作区进出，聚合份数以各次实跑为准）。
- 旧版复现：把 4cbb174 旧源码经 /tmp 夹具注入 ROOT 指向主仓后直调 `main()`，聚合输出 `REPORT-FIELDS: PASS（本轮无报告改动）`，rc=0，识别到 0 份中文未跟踪报告。
- 原始 status 对照：默认口径 `git status --porcelain` 中文路径输出为 `"docs/\351\224\232\347\202\271/..."` 八进制转义串；放开口径 `git -c core.quotepath=false status --porcelain -uall` 输出 UTF-8 原文文件级条目。旧版按行首切片剥引号后拿转义串匹配前缀正则 `docs/(修复|验收|组装|分析)-.*\.md$`，必然落空。
- 判定：复现成立。旧版在中文未跟踪报告上整批漏判并误报空集 PASS；新版能发现并进入判定。

## 三、判据 2 · 放行样本四例（独立迷你仓夹具）

- 夹具一（中文+英文未跟踪报告识别、非报告不混入）：临时仓内放 `docs/修复-cn-中文报告.md`、`docs/修复-en-report.md`、`docs/机制-说明.md`、`data_scripts/foo.py`、`docs/锚点/验收-card.md`；`changed_reports()` 实得仅两份 `docs/修复-` 报告，非报告 docs、代码件、锚点卡均未混入。
- 夹具二（重命名取右侧）：临时仓内提交 `docs/修复-oldname.md` 后 `git mv` 为 `docs/修复-newname.md`；porcelain 行为 `R  docs/修复-oldname.md -> docs/修复-newname.md`，聚合实得 `docs/修复-newname.md`，旧路径 `oldname` 无残留、无重复判定；夹具内已 `git mv` 还原。
- 夹具三（显式路径直判）：合规英文词干报告经 `main([path])` 直判，输出 `PASS docs/修复-en-report.md`、`REPORT-FIELDS: PASS（1 份，0 份不合格）`，rc=0；锚点卡显式直传被分类辅助跳过，rc=0，不计不合格。
- 夹具四（空集）：空迷你仓聚合为空，`main()` 输出 `EMPTY: 未提供待检报告（空集不判 PASS；聚合口径走 changed_reports）`，rc=2。
- 判定：四例放行行为均未回退。

## 四、判据 3 · EMPTY rc=2 语义变更与下游调用方核对

- 空集新口径：无待检报告时打印 EMPTY 并 `return 2`，不再表述为 PASS（`data_scripts/check-report-fields.py:257`-`259`）。
- 调用方逐一核对结果：
  1. `data_scripts/pre-push-check.sh:99`：`run_step "3.5-报告字段" 3 python3 data_scripts/check-report-fields.py`；`run_step` 在 `data_scripts/pre-push-check.sh:39` 以 `if [ "$rc" -ne 0 ]` 统一记失败，未把 rc=2（EMPTY 提示）与 rc=1（真红）分流。干净树本地预推时，本闸空集返回 2 会被记成步骤 FAIL 并进入 `FAIL_STEPS`，最终 `PRE-PUSH: FAIL`。严重度：中——属接线层误红；真红 rc=1 的识别路径不受影响；该脚本是本地预推体检，不是 CI 阻断闸。写码方修复报告第六节已登记此条待裁，复核成立。
  2. `data_scripts/rule-gate.sh`：grep `report-fields|报告字段` 无命中，总门禁不直接调用本闸；其拉起的是 check-evidence / check-anchor-card / check-member-consumers 等其它闸。
  3. `data_scripts/check-vacuous-pass.py:88`：仅在静态豁免三元组表中登记本闸 `paths` 条目，不按退出码调度，不构成判红；该条目随空集分支改动已成死条目（见判据 6）。
  4. `.github/workflows/package.yml`、`screenshot.yml`、`verify-shadow-single.yml`、`verify.yml`：grep 无 `report-fields` 调用；`data_scripts/` 内其它 .py 无 import 或位置调用本闸。
- 判定：唯一会把 rc=2 当红的调用点是 `pre-push-check.sh:99`，严重度中，已登记待裁；本闸自身空集语义变更没有在 CI 侧造成未分流判红。

## 五、判据 4 · is_anchor_card 漏判风险

- 实现位置：`data_scripts/check-report-fields.py:57`-`61`；判定为 `"/锚点/" in norm or norm.startswith("docs/锚点/")`，只看目录段，不看文件名前缀。
- 实测边界：`docs/锚点/验收-B4-07文字面上屏.md`、`docs/锚点/报告字段闸quotepath.md`、`docs/锚点/sub/x.md` 均判 True；`docs/验收-复核-g65消费方反查.md`、仓级文件 `docs/锚点.md` 判 False。
- 口径同构：`data_scripts/check-anchor-card.py:67` 的 `classify()` 对 `/锚点/` 路径返回 `other`，目录优先规则与本闸逐字同构，不是本闸自创的免检特例。
- 残余风险：若真报告被误置到 `docs/锚点/` 目录下，两闸会一致跳过；按仓内约定该目录只放前置设计卡（当前 231 件均为卡），当前无错置样本。风险等级低（约定级）；建议后续一旦出现报告落锚点目录的形态，再按文件名前缀收紧。
- 判定：跳过规则只看目录为有意设计，与同族守卫口径一致；未发现把在飞真报告漏判的实例。

## 六、判据 5 · 字段判据未被改动（逐字比对）

- 程序化函数级 diff：旧版与新版的 `only_backfill()`、`is_fixture()`、`check()` 三个函数体逐字一致（比对结果均为 True）。
- A 判据：首三行内 `锚点卡：` 起始，正则匹配 `docs/锚点/` 开头的 `.md` 锚点卡路径且指向文件真实存在——未改。
- B 判据：结论标记正则 `(总结论|结论)[：:]|PASS|有条件通过|FAIL`——未改。
- C 判据：修复/组装类须含「自查反例」或「改坏」，验收类须含「改坏」或「推演」——未改。
- D 判据：证据锚点正则 `[\w./]+\.(?:kt|py|sh|xml|md):\d+`，不足 5 处判 FAIL——未改。
- `only_backfill()` 纯回执补录豁免（+N/-0 且新增行全为回执留痕）、`is_fixture()` 夹具排除（目录或 `.fixture` 后缀）口径原样保留。
- 改动面仅限：status 命令补 quotepath 与 `-uall`、`_porcelain_path()` 路径解析、路径归一、`is_anchor_card()` 新增分类、空集分支表述与退出码；字段判据一行未放宽。
- 判定：通过。

## 七、判据 6 · 自测、接线与同类缺口登记复核

- `python3 data_scripts/check-report-fields.py --selftest` 真实末行：`[selftest] 夹具已自删，双向自测通过。`，rc=0；六例夹具（好样本 PASS、坏样本 FAIL、解析单元三例、迷你仓中英报告识别、显式直判、空仓 EMPTY rc=2）输出齐全。
- `python3 data_scripts/check-gate-wiring.py` 真实末行：`GATE-WIRING: PASS（60 个门禁均接线；历史孤儿基线 0 个）`，rc=0。
- `bash -n data_scripts/rule-gate.sh` rc=0；附带 `bash -n data_scripts/pre-push-check.sh` rc=0。
- 写码方登记的同类缺口逐条自行 grep 复核：
  1. `data_scripts/cross-module-guard.py:38`：`run("git", "status", "--porcelain")` 未带 quotepath=false；解析切片在 `:43`，`.kt/.java/.xml` 后缀过滤在 `:45`。行号与口径准确。状态：待裁（观察），仓内代码路径当前全 ASCII，未实证误判。
  2. `data_scripts/kotlin-syntax-lint.py:474`：status 调用未带 quotepath=false；解析切片在 `:477`，`.kt` 过滤在 `:478`。行号与口径准确。状态：待裁（观察）。
  3. `data_scripts/check-vacuous-pass.py:88`：本闸豁免条目随空集分支改动已成死条目；裸跑豁免清单 8 处已不含本闸条目，扫描器不反查在册三元组是否仍有命中。状态：待裁（清理债）。
  4. `data_scripts/pre-push-check.sh:99`：EMPTY rc=2 与真红 rc=1 未分流，同判据 3。状态：待裁。
  5. `data_scripts/acceptance-evidence.py:85`：helper `:67`-`71` 已带 `-c core.quotepath=false`，但 `:85` 按 `line[3:]` 切片未取 `->` 右侧；`data_scripts/check-unpushed-reports.py:50`：helper `:34` 已带 quotepath=false，`:50` 同样未取箭头右侧。两处行号与口径准确。状态：待裁（观察）。
- 补充核对：`data_scripts/check-problem-finding.py:208` 确为同族 quotepath 放开先例（`-c core.quotepath=false ... -uall`）；`data_scripts/check-anchor-card.py:239` 确为空集 EMPTY rc=2 先例。

## 八、机检四闸真实末行（验收方实跑）

- 报告字段闸（聚合）：当轮末行 `REPORT-FIELDS: PASS（3 份，0 份不合格）`，rc=0；显式传两份在飞报告同判 PASS（后续复跑工作区件数变动，聚合份数随之增减，末行以实跑为准）。
- 锚点守卫 `python3 data_scripts/check-anchor-card.py --strict`（在飞两对 + 本单两件）：在飞四件 `汇总: PASS=4 FAIL=0（共 4 份）`，rc=0；本单两件 `汇总: PASS=2 FAIL=0（共 2 份）`，rc=0；裸跑无参口径返回 `EMPTY: 未提供待检报告（空集不判 PASS；diff 型调用请走 ㊽ 口径）`，rc=2，与该闸自身空集设计一致。
- 永远找问题 `python3 data_scripts/check-problem-finding.py`：裸跑 `PROBLEM-FINDING: PASS（现行通过 5 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`，rc=0；本单报告 `PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`，rc=0。
- 回执闸（onboarding）`python3 data_scripts/check-onboarding.py`：裸跑 `ONBOARD: PASS（3 份报告）`，rc=0；本单报告 `check_report()` 直调返回 `probs=[]`。当下磁盘指纹：`docs/机制-总控必读.md=c8fb35b71549`、`docs/机制-子代理必读.md=c6806030a6d5`、`docs/机制-验收代理必读.md=a46af85f0145`、`docs/机制-文档子代理必读.md=6dd8114a6a74`，与在飞报告所贴指纹一致；三条逐字引用在总控必读 `:278`、`:355`、`:546` 逐字命中；「适用条款」行在位。
- 附加复核：`python3 data_scripts/check-vacuous-pass.py` 末行 `VACUOUS-PASS: PASS（未发现未豁免的空集假绿结构；8 处已按哈希锚定登记豁免）`，rc=0。

## 九、改坏推演（验收类自查反例）

- 反例一：若只放开 quotepath 而不取重命名箭头右侧，`R  old -> new` 会拿已删除旧路径去 `check()`，报"文件不存在"假红；夹具二实测右侧新路径命中、旧路径不残留，推演落空。
- 反例二：若放开 quotepath 却不加 `-uall`，全新目录里的未跟踪文件仍被折叠成目录条目，文件级前缀匹配落空；迷你仓夹具一文件级条目可识别，推演落空。
- 反例三：若为迁就本单放宽 A/B/C/D 判据，等于拆闸换绿；函数级逐字比对证明 `check()` 未改，好/坏双向夹具仍走原判据，推演落空。
- 反例四：若空集分支保留旧 PASS 文案，空集假绿被 ㊾ 口径禁止；实测空集报 EMPTY、rc=2，且下游唯一调用点误红范围已在判据 3 界定，推演落空。
- 二阶影响推演：EMPTY rc=2 只影响本地 pre-push 步骤 3.5 的成败记账，不改变字段判定集合；聚合识别修复后，在飞中文报告从"不可见"变为"逐份过判据"，不会把夹具/锚点卡误纳入判定（`is_fixture` 与 `is_anchor_card` 双跳过）。

## 十、新发现问题（逐条带状态词与归属）

- 问题 1 · 状态：待裁（中危，接线层误红）。归属：`data_scripts/pre-push-check.sh` 接线层。步骤 3.5 由 `run_step` 按退出码非 0 统一记失败，未分流 EMPTY（rc=2）与真红（rc=1）；干净树本地预推会把空集提示记成步骤失败。建议 shell 层把 rc=2 识别为 EMPTY 提示放行，rc=1 才记红。写码方已登记，本单复核成立，未代改。
- 问题 2 · 状态：待裁（低危，清理债）。归属：`data_scripts/check-vacuous-pass.py` 空集假绿检测器。`check-report-fields.py` 空集分支改动后，豁免表三元组 `('data_scripts/check-report-fields.py', 'paths', '53fabe213b5e')` 已成死条目；扫描器不反查在册条目命中情况，死条目静默留存，下次维护豁免表时应摘除。
- 问题 3 · 状态：观察（待裁，口径不一致）。归属：`data_scripts/check-onboarding.py` onboarding 回执闸。该闸空集仍报 `ONBOARD: PASS（本轮无报告改动）` 且 rc=0，未跟随本闸 EMPTY rc=2 的新口径，两闸空集语义不一致；不影响本闸判定，建议后续统一空集口径。
- 问题 4 · 状态：观察（待裁，同族解析缺口）。归属：`data_scripts/check-onboarding.py` onboarding 回执闸。其 `changed_reports()` 按 `row[3:]` 切片，未取重命名 `->` 右侧，且 status 未带 `-uall`；与已登记的 `acceptance-evidence.py:85`、`check-unpushed-reports.py:50` 同族，本单一并记录，不在白名单内代改。
- 问题 5 · 状态：待裁（低危，文档锚点偏移）。归属：本单被验修复报告 `docs/修复-报告字段闸quotepath.md`。报告把空集分支锚在 `check-report-fields.py:252`（`main()` 定义行），实际 EMPTY 分支体在 `:257`-`259`；引用行号偏移约 5 行，不影响判据结论，建议回填订正。
- 问题 6 · 状态：待裁（观察，未实证）。归属：跨模块守卫 / Kotlin 语法预检。`cross-module-guard.py:38`、`kotlin-syntax-lint.py:474` 两处 status 未带 quotepath=false；当前仓内 `.kt/.java/.xml` 路径全 ASCII，未实证漏判，留待出现非 ASCII 命名代码件时复核。

## 十一、守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「永远找问题，解决问题，提出问题」（docs/机制-总控必读.md:546）
- 适用条款：B2（改后 grep 回证，本单每条夹具结论均回指命令与输出行）；C3（边界自行兜底不降级，待裁项逐条实测落空后果）；F8（新发现问题逐条带状态词与归属）；验收代理必读 §1 独立性（不采信写码方自评，计数自取）；验收代理必读 §9（新增文件结论最高有条件通过，编译面待 CI）；本单红线：不改 app/** 与 data_scripts/**、不 git 写操作、不本地编译。

## 十二、总结论

有条件通过（编译面待 CI）：六组判据复核全部成立——聚合口径已能识别在飞中文未跟踪/修改报告，显式直判、英文报告、非报告排除、重命名取右侧、空集 EMPTY rc=2 均实测通过；A/B/C/D 字段判据、补录豁免与夹具排除与旧版逐字一致；自测、接线、bash 语法三道路径绿；登记的五条同族缺口清单与行号复核准确。唯一中危项为 `pre-push-check.sh:99` 未分流 EMPTY 与真红（已登记待裁，本地预推干净树误红，不影响 CI 与真红识别）。本单未改产品代码，clean 编译、全量单测与真机行为以下一轮云端 Verify CI 为准，本验收不替 CI 背书。
