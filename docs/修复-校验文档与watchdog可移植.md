锚点卡：docs/锚点/S-02-气泡协议.md

# 修复报告：校验文档内嵌源码同步 + watchdog 路径可移植化

- 仓库：`/home/user/Doubao/chats/38454122521460994/legado`，基线 HEAD `e0f54bb`。
- 本次只改两个文件：`docs/整理-原文-重排校验.md`、`data_scripts/watchdog.sh`。
- 未触碰：`app/**`、其它 docs、`data_scripts/reorder_validate.py`、`data_scripts/reorder_build.py`、`data_scripts/manifest.json`、原文数据；未执行 git add/commit/push；未编译。
- 文档末尾原有的历史结论（§7 边界与未覆盖说明）逐字保留，未改写；该文档内无独立「勘误」小节，本次仅在 §3、§4 标题下各追加一行节选注记。

## 一、事项一：校验文档两处内嵌源码与现行 .py 逐字同步

### 1.1 §3 校验脚本节选（reorder_validate.py）

改前：`docs/整理-原文-重排校验.md:49` 内嵌 `REPO = "/home/user/Doubao/chats/38454122521460994/legado"`，为旧硬编码绝对路径；`:48` 仅有 `HERE = ...` 一行，缺现行脚本的推导注释与 `REPO = os.path.dirname(HERE)`。现行 `data_scripts/reorder_validate.py:14-18` 已改为基于 `__file__` 推导。

改后：
- `docs/整理-原文-重排校验.md:34` 新增注记行：「以下为节选，路径推导等片段以现行 `data_scripts/reorder_validate.py` 为准；节选与 .py 分叉时一律以 .py 为准。」
- `:50` `HERE = os.path.dirname(os.path.abspath(__file__))`，逐字同 `reorder_validate.py:14`。
- `:51` 注释「仓库根 = 脚本所在目录(data_scripts)的上一级；基于 __file__ 推导，换检出路径/换机均可跑，不写死绝对路径」，逐字同 `reorder_validate.py:15`。
- `:52` `REPO = os.path.dirname(HERE)`，逐字同 `reorder_validate.py:16`。
- `:53` `SRC_DIR = ...` 同 `:17`；`:54` `REORDERED = ...` 同 `:18`。

实证命令与输出：
- 命令：提取文档 §3 围栏内代码，与 `data_scripts/reorder_validate.py` 做 `difflib.unified_diff` 全量逐字比对。
- 输出：`fence 0 diff lines: 0`，`IDENTICAL`（两节之外全文围栏与现行 .py 无任何差异行）。
- 路径块专项比对输出：`fence0 路径块逐字一致: True`，.py 与 doc 两侧 4 行（HERE / 注释 / REPO / SRC_DIR）逐条相等。
- 命令：`grep -n '/home/user' docs/整理-原文-重排校验.md`。
- 输出：无命中（grep 退出码 1），旧绝对路径在文档内清零。

### 1.2 §4 构建脚本节选（reorder_build.py）

改前：`docs/整理-原文-重排校验.md:177` 内嵌 `REPO = "/home/user/Doubao/chats/38454122521460994/legado"`，为旧硬编码绝对路径；节选开头直接从 `REPO` 起，缺现行脚本的 `HERE = ...` 行与推导注释。现行 `data_scripts/reorder_build.py:12-16` 已改为基于 `__file__` 推导。

改后：
- `docs/整理-原文-重排校验.md:168` 新增注记行：「以下为节选，路径推导等片段以现行 `data_scripts/reorder_build.py` 为准；节选与 .py 分叉时一律以 .py 为准。」
- `:182` `HERE = os.path.dirname(os.path.abspath(__file__))`，逐字同 `reorder_build.py:12`。
- `:183` 注释「仓库根 = 脚本所在目录(data_scripts)的上一级；相对 __file__ 推导，换检出路径/换机均可跑，不写死绝对路径」，逐字同 `reorder_build.py:13`。
- `:184` `REPO = os.path.dirname(HERE)`，逐字同 `reorder_build.py:14`。
- `:185` `SRC_DIR = ...` 同 `:15`；`:186` `OUT_DIR = ...` 同 `:16`。

实证命令与输出：
- 命令：提取文档 §4 围栏内代码，与 `data_scripts/reorder_build.py` 做 `difflib.unified_diff` 全量逐字比对。
- 输出：除下述一处 Markdown 围栏转写适配外，其余全部一致；路径块专项比对 `fence1 路径块逐字一致: True`，HERE / 注释 / REPO / SRC_DIR 四行逐条相等。
- 唯一保留差异：`reorder_build.py:363` 的冲突登记字符串内含三连反引号（``` ），若逐字搬进 Markdown 的 ```python 围栏会提前闭合围栏、造成后续代码块错位；文档该处按围栏安全写法保留为「（三连反引号）」。这是围栏转写适配，不是路径分叉；语义与原句一致。
- 命令：`python3 data_scripts/reorder_validate.py`（只读复核现行脚本本身可跑通，未改写任何 .py）。
- 输出：退出码 0；「还原拼接(原顺序) == 9 卷原文直接拼接：一致 ✅」「重排版剔注释 == 期望正文：一致 ✅」，两份 sha256 相等。

## 二、事项二：watchdog.sh 会话目录去硬编码

改前：`data_scripts/watchdog.sh:35-37`
- `:35` `SID=$(basename ...)` 不可被外部覆盖。
- `:36` `AGENTS_DIR="${1:-/home/user/.doubao/agent_mode/workspace/.sessions/$SID/agents}"`，根路径写死。
- `:37` 兜底 `ls -dt /home/user/.doubao/agent_mode/workspace/.sessions/*/agents`，根路径写死。
换机或会话目录迁移后，⑥ 节探针会直接落空，且无法从外部纠偏。

改后：`data_scripts/watchdog.sh:35-42`
- `:35` 注释说明会话根目录可用环境变量覆盖。
- `:36` `SESSIONS_ROOT="${SESSIONS_ROOT:-$HOME/.doubao/agent_mode/workspace/.sessions}"`：默认值在本机解析结果与原硬编码路径一致（`$HOME` = `/home/user`），换机时随 `$HOME` 自适应，也可用 `SESSIONS_ROOT` 环境变量显式覆盖。
- `:37` 注释说明 SID 来源。
- `:38` `SID="${SID:-$(basename "$(cd "$(dirname "$0")/../.." && pwd)" 2>/dev/null)}"`：优先取环境变量 `SID`，否则仍按脚本自身位置推导（仓库所在 chats 目录名），推导逻辑与现状一致。
- `:39` 注释说明解析优先级。
- `:40` `AGENTS_DIR="${1:-${AGENTS_DIR:-$SESSIONS_ROOT/${SID:+$SID/}agents}}"`：解析顺序为位置参数 `$1` > 环境变量 `AGENTS_DIR` > 按 SID 推导路径；`SID` 为空时不拼出双斜杠。
- `:41` 兜底改为在 `"$SESSIONS_ROOT"` 下取最新会话目录，不再写死根路径。
- `:42` 新增 `echo "解析出的 agents 目录: ${AGENTS_DIR:-（未命中）}"`，把最终解析路径打印出来作为实测证据。
- `:52` 未命中提示语补充环境变量 `AGENTS_DIR / SESSIONS_ROOT / SID` 的覆盖方式。

实证命令与输出：
- 语法检查：`bash -n data_scripts/watchdog.sh` → `SYNTAX_OK`，无语法错误。
- 场景 A：原仓库目录实跑。命令：`bash data_scripts/watchdog.sh` → `EXIT=0`；⑥ 节打印 `解析出的 agents 目录: /home/user/.doubao/agent_mode/workspace/.sessions/38454122521460994/agents`，在飞 agent 轨迹计数正常列出，⑦ 节快照正常落盘。
- 场景 B：`cp -a` 脚本到异目录 `/tmp/legado-wd-portable`（含最小 `docs/派单台账.md` 结构），默认环境运行 → `EXIT_A=0`；脚本位置推导得 `SID=tmp`，推导路径 `$SESSIONS_ROOT/tmp/agents` 不存在，兜底取最新会话，解析路径正常打印，未指向写死的原会话号。
- 场景 C：同异目录，环境变量覆盖。命令：`SESSIONS_ROOT=/tmp/fake-sessions SID=fake-1 bash data_scripts/watchdog.sh`（预置 `/tmp/fake-sessions/fake-1/agents/agent-x/system/trajectory.jsonl`，3 条 function 记录）→ `EXIT_B=0`；打印 `解析出的 agents 目录: /tmp/fake-sessions/fake-1/agents`，计数行 `3  agent-x`。
- 场景 D：同异目录，位置参数覆盖。命令：`bash data_scripts/watchdog.sh /tmp/fake-sessions/fake-1/agents` → `EXIT_C=0`；解析路径同上，计数 `3  agent-x`。
- 场景 E：同异目录，空会话根。命令：`SESSIONS_ROOT=/tmp/empty-root bash data_scripts/watchdog.sh` → `EXIT_D=0`；推导路径 `/tmp/empty-root/tmp/agents` 未命中，打印 `解析出的 agents 目录: （未命中）` 并走友好提示分支，不崩溃、不报错退出。
- 字面量清查：`grep -nE '/home/user|38454122521460994' data_scripts/watchdog.sh` → 无命中（退出码 1），脚本内不再残留原用户目录与原会话号字面量。

## 三、门禁与围栏检查

- 命令：`python3 data_scripts/check-anchor-card.py --strict docs/整理-原文-重排校验.md`。
- 输出：`汇总: PASS=1 FAIL=0（共 1 份）`，门禁退出码 0。
- 围栏成对性：文档内 ``` 标记共 10 个，即 5 对围栏（§3 python、§4 python、§5.1 text、§5.2 text、§6 bash），全部成对闭合。
- 运行 watchdog 产生的 `data_scripts/.reqrate.log` 为运行时快照，测后已还原，未作为本次改动保留。

## 四、自查反例

- 反例 1：若把 §3 / §4 内嵌片段改回旧硬编码绝对路径 `REPO = "/home/user/Doubao/chats/38454122521460994/legado"`，以下核对会失败：
  1. `grep -n '/home/user' docs/整理-原文-重排校验.md` 会重新命中原 :49 / :177 两行，路径字面量清查不再通过。
  2. 路径块专项比对 `b_py == b_doc` 由 `True` 变 `False`，与 `reorder_validate.py:16`、`reorder_build.py:14` 的 `REPO = os.path.dirname(HERE)` 重新分叉。
  3. 全量 `difflib.unified_diff` 会重新出现路径块差异行；文档示例在换机/换检出路径后与脚本实际行为不一致。
- 反例 2：若把 watchdog 的推导改回硬编码 `/home/user/.doubao/agent_mode/workspace/.sessions`，以下核对会失败：
  1. `grep -nE '/home/user|38454122521460994' data_scripts/watchdog.sh` 会重新命中，字面量清查不通过。
  2. 场景 C 的环境变量覆盖失效：`SESSIONS_ROOT=/tmp/fake-sessions SID=fake-1` 不会让解析路径变成 `/tmp/fake-sessions/fake-1/agents`，仍会指向原会话根。
  3. 场景 E 空根测试失效：硬编码根在换机后不存在，兜底 `ls` 仍只扫原根，⑥ 节只能报「未找到」且无法用环境变量纠偏。
  4. 场景 B 异目录运行时，推导 SID 已变为 `tmp`，但硬编码根会把探针钉死在原机器原会话目录，换机即静默落空。
- 反例 3：若把 `reorder_build.py:363` 字符串内的三连反引号逐字搬进 §4 Markdown 围栏，围栏会在该处提前闭合，后续索引生成代码全部错位为正文，围栏成对性检查与 `check-anchor-card.py --strict` 会判红；故该行保留「（三连反引号）」转写，属于有意的围栏安全适配。

## 五、边界说明

- 本次仅同步两节所讲的路径推导片段与注记，未整篇复制 .py；节选与现行 .py 若再分叉，以 .py 为准（注记已写入 §3、§4）。
- 工作树中另有其它并发改动的文件，不属于本次任务范围，未读取、未修改、未回退。
