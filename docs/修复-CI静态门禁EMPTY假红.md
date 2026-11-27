# 修复-CI 静态门禁 EMPTY 假红（set +e 被 -e 吃掉，rc=$? 永远取不到）

锚点卡：docs/锚点/Q442-CI静态门禁.md

- 主对象编号：Q-442（CI 静态门禁 EMPTY 假红）

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「5. **锚点强制**：每条结论必须带 `文件:行` 或 `commit sha`；下次要用直接 `sed -n 'X,Yp'` / `git show sha` 回源（回源成本 ≪ 记忆成本）。」（docs/机制-总控必读.md:108）
- 逐字引用 2：「5. **债务棘轮不许上调基线**：新增文件 ≤600 行是先决条件（事故四：731 行面板把 oversized_warn 顶到 51，停摆推送）。确需拆分就派"只拆不改"的单，不许 `--upgrade-baseline` 了事。」（docs/机制-总控必读.md:157）
- 逐字引用 3：「- **空闲即干活**：槽没满就派新单；槽满了主控自己做——读代码核事实、跑单脚本机检、扫工作区找破窗、写报告补锚点、给在飞单追加缺口指令。任何时候手里都要有一件正在做的事。」（docs/机制-总控必读.md:186）
- 适用条款：锚点强制；债务棘轮不许上调基线（本单不动任何 baseline）；空闲即干活（主控自修的 CI 红）；CI 即验收。

## 一、现场（CI 实测取数，不凭印象）

Verify run `45976616213`（HEAD `e6ccf0b`）结论 failure，逐 job 取数：Classify 成功、四个 shard 全 success、Aggregate success、Rhino success、Slow bench success、Screenshot success —— **只有 Static gate 一个 job 红**。

Static gate 日志（job `140429880513`）末段逐字：

```
Run python3 data_scripts/check-report-fields.py; rc=$?
python3 data_scripts/check-report-fields.py; rc=$?
shell: /usr/bin/bash -e {0}
EMPTY: 未提供待检报告（空集不判 PASS；聚合口径走 changed_reports）
##[error]Process completed with exit code 2.
```

## 二、根因

`.github/workflows/verify.yml` 里那两步的写法是：

```
run: |
  python3 data_scripts/check-report-fields.py; rc=$?
  if [ "$rc" -eq 2 ]; then echo "::notice::…EMPTY 空集，中性放行"; else exit "$rc"; fi
```

GH Actions 默认 `shell: /usr/bin/bash -e {0}`。`-e` 让 `python3 …` 一旦返回 2（EMPTY）**立刻整脚本退出**，`rc=$?` 和后面的中性放行分支根本没机会执行。于是「EMPTY 中性放行」这套设计在本地 `set -uo pipefail`（无 `-e`）下成立、到 CI 就假红——设计对、shell 选项不对。

同一根因第二次命中：`check-anchor-card.py --strict` 那步同形，这次恰好不是空集所以没红，属下一颗必爆的雷，一并修。

## 二·五、证据锚点（逐条可回源）

1. `.github/workflows/verify.yml:157` 修复后首行 `set +e`（Report fields gate 步）。
2. `.github/workflows/verify.yml:163` 修复后首行 `set +e`（Anchor card gate 步）。
3. `.github/workflows/verify.yml:158` 原写法 `python3 data_scripts/check-report-fields.py; rc=$?`（`-e` 下取不到码）。
4. `data_scripts/check-report-fields.py:257` 空集不判 PASS 的口径注释（rc=2 是设计，不是缺陷）。
5. `data_scripts/pre-push-check.sh:114` 本地链用 `run_step` 调同一脚本，无 `-e` 所以本地一直绿——同一脚本两套 shell 语义是本次假红的温床。

## 三、修法

在这两步的 run 块首行加 `set +e`，关掉 GH 注入的 `-e`，让 `rc=$?` 真正拿到退出码，EMPTY 走 notice 中性放行、真红仍 `exit "$rc"`。不改任何门禁脚本判据（脚本本身没错，错在调用它的 shell 选项）。

被拒方案：把 `rc=$?` 改成 `|| rc=$?`——`set -e` 下 `cmd || rc=$?` 确实不会退出，但同一文件里还有别的步骤用 `; rc=$?` 形态，逐处改成 `||` 会留下两套写法；统一 `set +e` 口径更可审计。

## 四、改动清单（行号实测）

- `.github/workflows/verify.yml:157` 新增 `set +e`（Report fields gate 步）
- `.github/workflows/verify.yml:163` 新增 `set +e`（Anchor card gate 步）
- 本报告与锚点卡

## 四·五、自查反例（改坏验证）

1. **把 `set +e` 删掉再跑**：本地 shell 无 `-e`，亡羊补牢看不出差异——所以这条反例只能以 CI 为真值，本机如实记「不可本地证伪」。
2. **把 `check-report-fields.py` 空集语义改成 rc=0**（假修法）：本地 `python3 data_scripts/check-report-fields.py` 会从 `EMPTY…退出码 2` 变成 0，看着绿了，但空集被当成「合规」，撞 ㊾ 空集不判 PASS；本单明确不做。
3. **在 workflow 加 `continue-on-error`**：job 会变绿但门禁实质失效，属静默降级，禁。
4. **真红是否仍能红**：`rc=1` 路径未受 `set +e` 影响——`else exit "$rc"` 仍把 1 抛出去；判据未变宽。

## 五、自测

- `python3 -c "import yaml；yaml.safe_load(...)"` → `YAML OK`（本机实跑，末行逐字）
- `grep -n "set +e" .github/workflows/verify.yml` → `157` / `163` 两处命中（回证真写入）
- 编译面与 CI 面：本机禁编译；**以云端 CI 为验收**，本次修复的验收标准是「Verify 的 Static gate job 转 success 且四 shard 不回归」。

## 六、未验证项（显式）

1. 本机无 GitHub runner，不能本地复现 GH 的 `-e` 行为，只能按 GH 默认 shell 语义修；真值以 push 后 CI 为准。
2. 未逐条检查其余 workflow 步骤是否还有 `-e` 吃掉取码的同类写法（本单只修已红的这两步与同形雷）。

## 七、新发现问题（登记，未自行修）

- N1（待裁，归属 CI 治理）：同类 `-e` 语义雷应在 CI 契约门禁里加一条机检——`run: |` 块内出现 `rc=$?` 但首行无 `set +e` 即判红，避免第三次踩。
- N2（待裁，归属门禁脚本）：`check-report-fields.py` 无参跑返回 rc=2 是「空集」语义，CI 侧却按「不传参」调用；长期应改成显式传 `--changed` 让口径与本地一致。
