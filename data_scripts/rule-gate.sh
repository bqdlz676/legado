#!/usr/bin/env bash
# 铁律自检门禁（用户 2026-11-24 令：保证全程绝不违规）
# 为什么：铁律靠"记得"就会漂，能机械判的必须机械判；判不了的在派单台账里靠人工勾选+验收代理复核。
# 适用：每次 commit / push / 派单前跑一遍；任何一项 FAIL 不许继续下一步。
cd "$(dirname "$0")/.." || exit 1
FAIL=0
OUT=/tmp/.gate_cur
exec > >(tee "$OUT")
say() { printf '%-42s %s\n' "$1" "$2"; }
bad() { FAIL=1; say "$1" "FAIL  $2"; }
ok()  { say "$1" "ok    $2"; }

# ① 铁律55附①：commit 前工作区自盘（不许有未提交改动被漏提）
if [ -z "$(git status --porcelain)" ]; then ok "①工作区自盘" "干净"; else say "①工作区自盘" "有未提交改动（提交前须逐条判断是否漏提）"; fi

# ② 铁律55附②：push 前判远端同步 + 外来提交（另一 AI 同仓并行提交时先检出再 push）
REMOTE_URL="https://bqdlz676:$(grep -o 'ghp_[A-Za-z0-9]*' ~/.git-credentials|head -1)@github.com/bqdlz676/legado.git"
REMOTE=$(git ls-remote "$REMOTE_URL" refs/heads/main 2>/dev/null | cut -f1)
LOCAL=$(git rev-parse HEAD)
if [ -z "$REMOTE" ]; then bad "②远端同步" "无法访问远端"
elif [ "$REMOTE" = "$LOCAL" ]; then ok "②远端同步" "${LOCAL:0:7}"
else
  git fetch -q "$REMOTE_URL" main 2>/dev/null
  if git merge-base --is-ancestor "$REMOTE" "$LOCAL" 2>/dev/null; then
    say "②远端同步" "本地领先远端（待 push）"
  elif git merge-base --is-ancestor "$LOCAL" "$REMOTE" 2>/dev/null; then
    bad "②远端同步" "远端有外来提交（另一 AI 已推 ${REMOTE:0:7}），先 pull --rebase 再 push"
  else
    bad "②远端同步" "本地/远端分叉，先合并再 push"
  fi
fi

# ③ 铁律54：当日对话流水文件存在且非空
TODAY=$(date +%F)
LOG="docs/落文-对话流水-$TODAY.md"
if [ -s "$LOG" ]; then ok "③当日流水" "$LOG"; else bad "③当日流水" "缺 $LOG（铁律54 违反）"; fi

# ④ 铁律51：规矩总纲与契约索引含最新口令（整合版后以"反思文件交接 + 并发上限2"为最新标记）
if grep -q "D8 反思文件交接" docs/落文-规矩总纲.md; then ok "④规矩随口令" "反思交接+并发动态 在册"; else bad "④规矩随口令" "D8 缺失（规矩未随口令更新）"; fi
# ④ 索引版本≥阈值：阈值是"本轮至少升到"，索引可能被并行单多升几版（v2.37 属超额完成），
#    故按语义比较大小而非字符串精确匹配——否则超额升版反被判 FAIL（2026-11-26 实踩）
IDX_VER=$(grep -o '版本：v[0-9.]*' docs/落文-模块契约索引.md | head -1 | sed 's/版本：v//')
VER_OK=0
if [ -n "$IDX_VER" ]; then
  VER_OK=$(python3 -c "
import sys
cur=tuple(int(x) for x in '$IDX_VER'.split('.'))
base=tuple(int(x) for x in '2.36'.split('.'))
print(1 if cur>=base else 0)
")
fi
if [ "$VER_OK" = "1" ]; then ok "④索引版本" "v$IDX_VER≥v2.36"; else bad "④索引版本" "未升版（当前 v$IDX_VER，期望≥v2.36）"; fi

# ⑤ 铁律57：派单台账每行只能一个缺陷号（>1 即打包违规）
LEDGER=docs/派单台账.md
if [ -f "$LEDGER" ]; then
  # 判据：单行内出现 ≥2 个**不同**缺陷号即打包违规（同一号重复出现（如"B3 ... B3 交付后")不算）
  MULTI=$(python3 - "$LEDGER" <<'PY2'
import re,sys
n=0
for line in open(sys.argv[1],encoding='utf-8'):
    if not line.startswith('|'): continue
    ids=set(re.findall(r'(?<![A-Za-z])(B|C|D|E|F|N)(\d+R?\d*)(?![0-9])', line))
    if len({a+b for a,b in ids})>=2: n+=1
print(n)
PY2
)
  if [ "$MULTI" -eq 0 ]; then ok "⑤一问题一代理" "台账无打包行"; else bad "⑤一问题一代理" "$MULTI 行含多缺陷号"; fi
else bad "⑤一问题一代理" "缺 $LEDGER"; fi

# ⑥ 铁律45/55：每个在册缺陷必须有独立验收产物或在飞标记
#    O1：计数改用 queue_stats 单一事实源（旧版抓台账「在飞」行，与 ⑱/㉚ 数值互斥，见 C1）
if [ -f "$LEDGER" ]; then
  OPEN=$(python3 data_scripts/queue_stats.py | grep -oE 'RUNNING=[0-9]+' | cut -d= -f2)
  ok "⑥在飞计数" "RUNNING $OPEN 条（每条须有独立验收代理，禁同一代理验收多条）"
fi

# ⑦ 铁律82：禁拿 L0 当验收 —— 报告里不许出现"验收通过"而级别只有 L0
HIT=$(grep -rl "验收通过" docs/ 2>/dev/null | wc -l)
say "⑦级别自报(人工+验收代理)" "docs 含'验收通过'的文件 $HIT 个，交付前须逐个核级别"

# ⑧ 铁律79：图标资产哈希未变（防误改）
if [ -f docs/.icon_freeze.sha256 ]; then
  if sha256sum -c docs/.icon_freeze.sha256 >/dev/null 2>&1; then ok "⑧图标冻结" "哈希一致"; else bad "⑧图标冻结" "图标被改动"; fi
else say "⑧图标冻结" "无冻结哈希文件（首次需生成）"; fi

# ⑨ 铁律E9 / F-82（2026-11-24 终版）：并发档位表在册，档位与配比读 docs/并发档位.md（唯一事实源）。
#    档1 5=4写+1查；档2 6=4写+2查；档3 8=5写+3查（硬顶）。load1 ≥1.5 饱和降到档1写槽减半（2写1查，总数3）。
#    台账暂无"写/查"分列，本项先判总数 ≤ 档位总数；写查分判待台账补列（见复盘报告注明）。
TIER=docs/并发档位.md
if [ -f "$LEDGER" ] && [ -f "$TIER" ]; then
  OPEN=$(python3 data_scripts/queue_stats.py | grep -oE 'RUNNING=[0-9]+' | cut -d= -f2)
  PEND=$(python3 data_scripts/queue_stats.py | grep -oE 'PENDING=[0-9]+' | cut -d= -f2)
  TIER_LINE=$(grep -E '当前档位：' "$TIER" | head -1)
  TIER_HEAD=${TIER_LINE%%；*}
  TIER_N=$(echo "$TIER_HEAD" | grep -oE '档位：[0-9]' | grep -oE '[0-9]')
  TOTAL=$(echo "$TIER_HEAD" | grep -oE '写 [0-9] \+ 查 [0-9]' | awk '{print $2+$5}')
  LOAD1=$(awk '{print $1}' /proc/loadavg 2>/dev/null || echo 0)
  if awk "BEGIN{exit !($LOAD1 >= 1.5)}"; then CAP=3; BAND="饱和(档1减半 2写1查)"; else CAP="$TOTAL"; BAND="档位$TIER_N"; fi
  if [ -z "$TOTAL" ]; then bad "⑨并发上限" "档位文件配比解析失败";
  elif [ "$OPEN" -gt "$CAP" ]; then bad "⑨并发上限" "负载 $LOAD1($BAND) 上限 $CAP，在飞 $OPEN 超限，禁再派单";
  else ok "⑨并发上限" "负载 $LOAD1($BAND) 上限 $CAP，RUNNING $OPEN / PENDING $PEND（分槽建议 写3验2）"; fi
else bad "⑨并发上限" "缺 $LEDGER 或 $TIER"; fi

# ⑯ 台账新鲜度（F-79/F-80）：HEAD 比台账新 >60min 即 FAIL；在飞超硬上限 FAIL；无当日行仅 WARN。
# 输出纪律（M-10 收口）：结论行只保留一处 ok/FAIL 与最终 PASS 判据串；脚本内部 ok 不再回显，
# WARN 单独成行并标注「不影响 GATE 结论」，不与 ok 混排。
LED16=$(python3 data_scripts/check-dispatch-ledger.py 2>&1)
if [ $? -eq 0 ]; then ok "⑯台账新鲜度" "$(echo "$LED16" | grep -E 'PASS' | tail -1)"; else bad "⑯台账新鲜度" "$(echo "$LED16" | grep 'FAIL' | head -3 | tr '\n' ' ')"; fi
echo "$LED16" | grep 'WARN' | while read -r w; do say "⑯台账新鲜度(提示)" "$w（不影响 GATE 结论）"; done

# ⑰ 并发档位表在册（F-82）：口径冻结必须含三档串，并发档位.md 必须存在且含"当前档位："行；
#    防"只落最低档、后两档被摘要吞掉"（2026-11-24 漏改事故：口令被压缩时未落地部分被当成已完结）。
SPEC=docs/口径冻结-2026-11-24-15点后.md
TIER_OK=1
for T in "5 = 4 写 + 1 查" "6 = 4 写 + 2 查" "8 = 5 写 + 3 查"; do
  grep -qF "$T" "$SPEC" 2>/dev/null || TIER_OK=0
done
if [ -f "$TIER" ] && grep -q '当前档位：' "$TIER"; then TIER_OK2=1; else TIER_OK2=0; fi
if [ "$TIER_OK" -eq 1 ] && [ "$TIER_OK2" -eq 1 ]; then ok "⑰档位表在册" "三档串与档位文件齐全"; else bad "⑰档位表在册" "三档串/档位文件缺失（F-82 未落地）"; fi

# ⑱ 队列与满槽（F-84）：队列在册；PENDING 不超时丢单；不空槽空转；有积压不空转。
# 输出纪律（M-10）：结论行只回显最终 PASS 判据串；WARN 单列并标注不影响 GATE 结论。
QUE18=$(python3 data_scripts/check-dispatch-queue.py 2>&1)
if [ $? -eq 0 ]; then ok "⑱队列满槽" "$(echo "$QUE18" | grep -E 'PASS' | tail -1)"; else bad "⑱队列满槽" "$(echo "$QUE18" | grep 'FAIL' | head -3 | tr '\n' ' ')"; fi
echo "$QUE18" | grep 'WARN' | while read -r w; do say "⑱队列满槽(提示)" "$w（不影响 GATE 结论）"; done

# ⑲ 锚点卡（F-85）：写码回执须带存在的锚点卡；验收报告须逐字对照锚点卡；
#    机械改动 / F-73 退回设计 / 验收-选型（F-73 第二段，候选规格即锚点）豁免。
#    边界同 ⑭/⑮：只校验本次 git status 新增/修改的写码/验收报告，历史留档不追改。
CHANGED_ANCHOR=$(git -c core.quotepath=false status --porcelain docs 2>/dev/null | awk '{
  if ($0 ~ /->/) { sub(/^.*-> /, ""); } else { $1=""; }
  gsub(/^ +/, ""); gsub(/^"/, ""); gsub(/"$/, "");
  if ($0 ~ /(修复|实现|验收)-.*\.md$/) print $0
}')
if [ -z "$CHANGED_ANCHOR" ]; then
  ok "⑲锚点卡" "本次改动无新增/修改的写码/验收报告"
else
  ANC19=$(python3 data_scripts/check-anchor-card.py --strict $CHANGED_ANCHOR 2>&1)
  if [ $? -eq 0 ]; then ok "⑲锚点卡" "$(echo "$CHANGED_ANCHOR" | wc -l | tr -d ' ') 份报告锚点齐全"; else bad "⑲锚点卡" "$(echo "$ANC19" | grep '→ FAIL' | head -3)"; fi
fi

# ⑩ 铁律30/88（本地禁编译）：本地构建能力必须=0
BUILD_CAP=0
if command -v gradle >/dev/null 2>&1; then BUILD_CAP=1; fi
if [ -n "$ANDROID_HOME$ANDROID_SDK_ROOT" ]; then BUILD_CAP=1; fi
if ls "$HOME/.gradle/jdks" 2>/dev/null | grep -qv 'CACHEDIR.TAG'; then BUILD_CAP=1; fi
# ⑩ 进程探测用「不会自匹配」的模式：旧写法 pgrep -f 'gradle|kotlinc|assemble' 会把
#    检查命令自身的命令行（含这些词）当成命中，造成每次跑门禁必 FAIL 的假阳性。
#    故只匹配真实构建守护进程的特征串。
# 只匹配 comm=java 的行：任何 shell 包装器的 argv 都可能含下面这些字面量，整行匹配会自匹配
BUILD_PROCS=$(ps -eo comm=,args= 2>/dev/null | awk '$1 ~ /^java/ && /GradleDaemon|KotlinCompileDaemon|org\.gradle\.launcher\.daemon/ {print $0}')
if [ -n "$BUILD_PROCS" ]; then BUILD_CAP=1; fi
if [ "$BUILD_CAP" -eq 0 ]; then ok "⑩本地构建能力" "已封死（无gradle/SDK/JDK21，无构建进程）"; else bad "⑩本地构建能力" "检测到本地构建能力/进程，禁提交禁派单"; fi

# ⑪ 规格完整性（用户令：不断查完整性；单一实现=spec-check.sh，不重复逻辑）
SPECRES=$(bash data_scripts/spec-check.sh 2>&1)
if [ $? -eq 0 ]; then ok "⑪规格完整性" "$(echo "$SPECRES" | grep -E '共|连续' | tail -3 | tr '\n' ' ')"; else bad "⑪规格完整性" "$(echo "$SPECRES" | grep '✗' | head -1)"; fi

# ㉒ 规格流入巡检（F-88）：切片↔登记册 / 登记册↔清单 / 登记册↔索引 / 登记册↔派单踪迹 四向双向对齐；
#    任一断裂 FAIL。为什么：用户会持续贴新规格，漏登一片则后续写码/验收无锚点可回源；旧 spec-check.sh
#    不反向校验切片↔登记册，切片 21~25 漏登长期未被发现。适用：commit/push/派单前随门禁跑一遍。
FLOW22=$(python3 data_scripts/check-spec-flow.py 2>&1)
if [ $? -eq 0 ]; then ok "㉒规格流入巡检" "$(echo "$FLOW22" | grep 'SPEC-FLOW: PASS')"; else bad "㉒规格流入巡检" "$(echo "$FLOW22" | grep 'FAIL' | head -4)"; fi

# ⑫ 扩展函数 import 守卫（来源：2026-11-24 CI 连红两次均为 Compose 扩展函数漏 import：
#    CardBase.animateFloat / TopicCard.clip。语法合法、IDE 无感，只有 CI 才报，故做机械门禁。
#    为什么单独成项：编译类问题在沙箱无法本地复现，只能靠静态检查兜住。
EXT=$(python3 data_scripts/check-ext-imports.py app/src/main/java 2>&1)
EXT_RC=$?
if [ "$EXT_RC" -eq 0 ]; then ok "⑫扩展import" "齐全"; else bad "⑫扩展import" "$(echo "$EXT" | grep -F -- '->' | wc -l | tr -d ' ') 处缺失"; fi

# ⑬ 测试/主源编译陷阱静态守卫（来源：2026-11-24 CI 连红 9 次，一批错误可机械检出：
#    R1 反引号名非法字符 / R2 静态调实例方法 = ERROR 计入失败；R3 尾随 lambda 绑错形参 /
#    R4 可空 map 键 / R5 泛型无法推断 = WARN 仅提示。脚本退出码只由 ERROR 决定。）
TSTSRC=$(python3 data_scripts/check-test-source.py app/src 2>&1)
TSTSRC_RC=$?
if [ "$TSTSRC_RC" -eq 0 ]; then ok "⑬测试源码陷阱" "$(echo "$TSTSRC" | grep -F 'ERROR=')"; else bad "⑬测试源码陷阱" "$(echo "$TSTSRC" | grep -E 'R1|R2' | head -3)"; fi

# ⑭ 回执证据守卫（2026-11-24 事故复盘）：子代理零工具调用、四句空话当回执，旧结构检查拦不住；
#    由 check-evidence.py 机械判 E1 命令证据 / E2 文件:行 定位 / E3 实测结论标记，三条同时成立才 PASS。
#    边界：历史报告必然有缺证据的（体检留档 39 PASS / 68 FAIL），故门禁只校验**本次改动**
#    新增/修改的修复/验收报告；不拿全量历史文档当门禁，否则一上来就红。
# core.quotepath=false：中文文件名默认被 git 转成八进制转义，不放开就匹配不到「修复-/验收-」前缀
CHANGED_RPTS=$(git -c core.quotepath=false status --porcelain docs 2>/dev/null | awk '{
  if ($0 ~ /->/) { sub(/^.*-> /, ""); } else { $1=""; }
  gsub(/^ +/, ""); gsub(/^"/, ""); gsub(/"$/, "");
  if ($0 ~ /(修复|验收)-.*\.md$/) print $0
}')
if [ -z "$CHANGED_RPTS" ]; then
  ok "⑭回执证据" "本次改动无新增/修改报告"
else
  EV14=$(python3 data_scripts/check-evidence.py --strict $CHANGED_RPTS 2>&1)
  if [ $? -eq 0 ]; then ok "⑭回执证据" "$(echo "$CHANGED_RPTS" | wc -l | tr -d ' ') 份报告回证齐全"; else bad "⑭回执证据" "$(echo "$EV14" | grep '→ FAIL' | head -3)"; fi
fi

# ⑮ 三段流水线锚点守卫（F-78）：无规格功能必须走 设计规格→独立选型→（全部判低双换回炉）→写码；
#    实现/交付类报告首行须含三锚点 `规格：`/`选型：`/`验收：`，缺一即 FAIL；
#    选型文档须有明确"选中第 N 份 + 判低份数与理由"，无选型结论的 FAIL；
#    选型文档"全部判低"时须能查到下一轮 v2 规格候选与 v2 选型报告，否则 FAIL。
#    例外：报告显式写明"无规格→已按 F-73 退回设计"可免 `规格：` 锚点；既无锚点又无退回声明的 FAIL 并提示退回第①段。
#    边界：只校验本次 git status 新增/修改的目标报告；历史留档不追改（同 ⑭ 口径，否则一上来就红）。
CHANGED_TRIPLE=$(git -c core.quotepath=false status --porcelain docs 2>/dev/null | awk '{
  if ($0 ~ /->/) { sub(/^.*-> /, ""); } else { $1=""; }
  gsub(/^ +/, ""); gsub(/^"/, ""); gsub(/"$/, "");
  if ($0 ~ /(实现|交付)-.*\.md$/ || $0 ~ /卡片-.*\.md$/ || $0 ~ /图标单卡\/.*\.md$/) print $0
}')
if [ -z "$CHANGED_TRIPLE" ]; then
  ok "⑮三段锚点" "本次改动无新增/修改的实现/交付报告"
else
  TRI15=$(python3 data_scripts/check-triple-anchor.py $CHANGED_TRIPLE 2>&1)
  if [ $? -eq 0 ]; then ok "⑮三段锚点" "$(echo "$CHANGED_TRIPLE" | wc -l | tr -d ' ') 份报告三锚点齐全"; else bad "⑮三段锚点" "$(echo "$TRI15" | grep '→ FAIL' | head -3)"; fi
fi

# ⑳ 缝合怪清理（F-86）：扫双轨/兼容层/超大文件/废弃调用/wip 半成品分支；
#    命中须在 docs/台账-缝合怪.md 在册，未在册判 FAIL；留档豁免必须能回查到台账条目，防自报豁免。
FRANK=$(python3 data_scripts/check-frankenstein.py 2>&1)
if [ $? -eq 0 ]; then ok "⑳缝合怪" "$(echo "$FRANK" | grep -E 'FRANKENSTEIN: PASS')"; else bad "⑳缝合怪" "$(echo "$FRANK" | grep 'FRANKENSTEIN: FAIL')"; fi

# ㉖ 接手链守卫（F-91）：钥匙可解析 / 快照新鲜度 / 棒次六段齐全 / 接力棒只增不改 / 当日流水非空；
#    接手链＝总账(入口+接力棒)+门禁(本项)+溯源(快照与棒次行)；跨轮次长跑项目每棒收口必刷快照与棒次行。
HAND26=$(python3 data_scripts/check-handover.py 2>&1)
if [ $? -eq 0 ]; then ok "㉖接手链" "$(echo "$HAND26" | grep 'HANDOVER: PASS')"; else bad "㉖接手链" "$(echo "$HAND26" | grep 'FAIL' | head -4)"; fi

# ㉓ 规则总账（F-89/F-90）：总账结构 / F 编号覆盖 / 冲突裁决与替代指向 / 否决串裸奔扫描；
#    为什么：规则靠记忆维持必然漏，总账是唯一事实源，门禁机械判账在册、冲突已裁、旧口径不裸奔。
LED23=$(python3 data_scripts/check-rule-ledger.py --check ledger 2>&1)
if [ $? -eq 0 ]; then ok "㉓规则总账" "$(echo "$LED23" | grep 'RULE-LEDGER: PASS')"; else bad "㉓规则总账" "$(echo "$LED23" | grep -E '·|FAIL' | head -5)"; fi

# ㉔ 三件缺项台账（F-89 §3）：缺项在册且无超期；补齐前限权，逾期升级阻断。
GAP24=$(python3 data_scripts/check-rule-ledger.py --check gap 2>&1)
if [ $? -eq 0 ]; then ok "㉔缺项台账" "$(echo "$GAP24" | grep 'RULE-LEDGER: PASS')"; else bad "㉔缺项台账" "$(echo "$GAP24" | grep -E '·|FAIL' | head -5)"; fi

# ㉑ 组合验收金字塔（F-92）：矩阵强制入册 / 关键对 100% 覆盖 / 缺陷落层归因 / 禁跳级；
#    本项只许 FAIL 不许 WARN；待验/未落地是在册明确状态，必须带验收代理与锚点。
COMBO21=$(python3 data_scripts/check-combo-coverage.py 2>&1)
if [ $? -eq 0 ]; then ok "㉑组合验收覆盖" "$(echo "$COMBO21" | grep 'COMBO-COV: PASS')"; else bad "㉑组合验收覆盖" "$(echo "$COMBO21" | grep '·' | head -5)"; fi

# ㉗ 子代理存活/锚点回执/跑题守卫（F-93~F-97）：
#    实证 2026-11-25：两个子代理首轮零工具调用、产出与本单无关的通用文案（小红书/寒暄），
#    而派单原文确实已到达（轨迹首行可查）——事故在"首轮没有强制机械动作"，所以守卫必须机械：
#    近一轮动过的代理里，凡零工具调用且未登记 docs/台账-子代理存活.md 者判 FAIL。
LIVE27=$(python3 data_scripts/check-agent-liveness.py 2>&1)
if [ $? -eq 0 ]; then ok "㉗子代理存活" "$(echo "$LIVE27" | grep 'AGENT-LIVENESS: PASS')"; else bad "㉗子代理存活" "$(echo "$LIVE27" | grep -E 'FAIL' | head -4 | tr '\n' ' ')"; fi

# ㉘ 测试函数名合法性（F-100）：Kotlin 反引号函数名含 `. : [ ] / ; < >` 会让
#    `compileAppDebugUnitTestKotlin` 直接 FAIL（run 45453607335 冒号 / 45465010171 点号），
#    而这属于"代码看着没问题却红"，只能在 CI 花 15 分钟证实——所以在本地先机械拦住。
TFN28=$(python3 data_scripts/check-test-fun-names.py 2>&1)
if [ $? -eq 0 ]; then ok "㉘测试函数名合法性" "$(echo "$TFN28" | grep 'TEST-FUN-NAME: PASS')"; else bad "㉘测试函数名合法性" "$(echo "$TFN28" | grep -E '非法字符|FAIL' | head -4 | tr '\n' ' ')"; fi

# ㉙ 停工守卫（F-103）：用户要求"没有我的指令中途不许停工"。用外部事实判定——
#    HEAD 超 IDLE_ALLOW_MINUTES(默认 120) 分钟未动、而队列仍有 PENDING/RUNNING，就是空转。
IDLE29=$(python3 data_scripts/check-no-idle.py 2>&1)
if [ $? -eq 0 ]; then ok "㉙停工守卫" "$(echo "$IDLE29" | grep 'NO-IDLE: PASS')"; else bad "㉙停工守卫" "$(echo "$IDLE29" | grep -E 'FAIL' | head -2 | tr '\n' ' ')"; fi

# ㉚ 队首推进（F-104）：㉙ 只能发现"HEAD 不动"，但事故还有另一种形态——HEAD 在动、
#    队列却有 PENDING 而 RUNNING=0（调度层在跑却没派单）。这条专门守那种并行空转。
QR30=$(python3 data_scripts/check-queue-running.py 2>&1)
if [ $? -eq 0 ]; then ok "㉚队首推进" "$(echo "$QR30" | grep 'QUEUE-RUNNING: PASS')"; else bad "㉚队首推进" "$(echo "$QR30" | grep -E 'FAIL' | head -2 | tr '\n' ' ')"; fi

# ㉛ 事故清单完整性（F-105）：事故散落在复盘/CI归因/存活台账里，"发生过什么"只能靠人翻，
#    一旦漏登记下一棒就会重犯。这条强制"凡事故必须进 docs/事故清单.md"。
IR31=$(python3 data_scripts/check-incident-register.py 2>&1)
if [ $? -eq 0 ]; then ok "㉛事故清单完整性" "$(echo "$IR31" | grep 'INCIDENT-REG: PASS')"; else bad "㉛事故清单完整性" "$(echo "$IR31" | grep -E '未登记|FAIL' | head -4 | tr '\n' ' ')"; fi

# ㉜ Float/Double 混算（K-16 机制化）：float * double 会被推成 Double，
#    return 声明 Float 或不带 toFloat() 就编译红。这条把这些行机械抓出来。
FD32=$(python3 data_scripts/check-float-double-mix.py 2>&1)
if [ $? -eq 0 ]; then ok "㉜FloatDouble混算" "$(echo "$FD32" | grep 'FLOAT-DOUBLE-MIX: PASS')"; else bad "㉜FloatDouble混算" "$(echo "$FD32" | grep -E 'FAIL|:' | head -3 | tr '\n' ' ')"; fi

# ㉝ 本地禁止编译（F-107，用户原话"本地禁止编译"）：沙箱无 JDK21/SDK，本地跑 gradle
#    只会产出假绿假红并留下 GradleDaemon 常驻；唯一可信编译验证是 CI。
NBL=$(python3 data_scripts/check-no-local-build.py 2>&1)
if [ $? -eq 0 ]; then ok "㉝本地禁止编译" "$(echo "$NBL" | grep 'NO-LOCAL-BUILD: PASS')"; else bad "㉝本地禁止编译" "$(echo "$NBL" | grep -E 'FAIL|Gradle|gradle|build' | head -3 | tr '\n' ' ')"; fi

# ㉞ Gradle 语法（K-19 机制化）：settings.gradle/*.gradle 是 Groovy，行首 # 不是注释，
#    一红连 Task 都进不去（run 45525688582 实证）。这条把这类假注释机械抓出来。
GS34=$(python3 data_scripts/check-gradle-syntax.py 2>&1)
if [ $? -eq 0 ]; then ok "㉞Gradle语法" "$(echo "$GS34" | grep 'GRADLE-SYNTAX: PASS')"; else bad "㉞Gradle语法" "$(echo "$GS34" | grep -E ':' | head -3 | tr '\n' ' ')"; fi

# ㉟ 推送窗口提示（O3/K-20，F-111 降为提示型）：只展示在飞 run 与建议，退出码恒 0，不 bad；
#    节拍攒批由 pre-push.sh 的 15 分钟时间戳守卫，本项不阻断门禁。
PW35=$(python3 data_scripts/check-push-window.py 2>&1)
say "㉟推送窗口" "$(echo "$PW35" | head -1)"

# ㊱ 处置锚点防洗白（O4/C7）：锚点=一次处置，之后不许再冒违规；多锚点即掩盖历史。
AI36=$(python3 data_scripts/check-anchor-integrity.py 2>&1)
if [ $? -eq 0 ]; then ok "㊱锚点防洗白" "$(echo "$AI36" | grep -E 'PASS|WARN')"; else bad "㊱锚点防洗白" "$(echo "$AI36" | grep 'FAIL' | head -3 | tr '\n' ' ')"; fi

# ㊲ 验收字样（O6/C4）：含「验收通过」的文件必须带判据锚，禁空口自称通过。
AC37=$(python3 data_scripts/check-accept-claim.py 2>&1)
if [ $? -eq 0 ]; then ok "㊲验收字样" "$(echo "$AC37" | grep 'ACCEPT-CLAIM')"; else bad "㊲验收字样" "$(echo "$AC37" | grep 'FAIL' | head -2 | tr '\n' ' ')"; fi

# ⑧' 变化摘要（O8）：与上一次快照 diff，新增 FAIL 标 NEW，免每次肉眼扫 34+ 项。
CUR_FAIL=$(grep -E '^[^ ]+ +FAIL' /tmp/.gate_cur 2>/dev/null | awk '{print $1}')
# ㉕ 子代理超时熔断/工具预算：不设独立实现块。该职能已并入 ㊳ check-agent-timeout.py
#    （Q-22 结单落 ㊳：>15min WARN / >60min FAIL）。保留㉕号仅作历史归档，避免同一职能双号
#    重复判据；原三处文档引用的㉕号已统一改指 ㊳。
# ㊳ 子代理熔断（Q-22 结单，用户原话「不能超过15分钟」）：RUNNING 行 >15min 提示、>60min 判失败。
#    为什么是时长而非"无产物"：队列只有登记时间，产物有无由 ㉚/⑳ 各自覆盖。
AT38=$(python3 data_scripts/check-agent-timeout.py 2>&1)
if [ $? -eq 0 ]; then ok "㊳子代理熔断" "$(echo "$AT38" | grep -E 'PASS|WARN' | head -2 | tr '\n' ' ')"; else bad "㊳子代理熔断" "$(echo "$AT38" | grep 'FAIL' | head -2 | tr '\n' ' ')"; fi

# ㊴ 提交认证书尾（F-111，WARN 型）：Verified-by/Wip/Docs-only trailer 仅供事后追溯；
#    缺 trailer 只提示补登，不 FAIL——验收没过的 commit 也要能上云保槽，硬阻断会顶掉保槽铁律。
TR39=$(python3 data_scripts/check-commit-trailer.py 2>&1)
say "㊴认证书尾" "$(echo "$TR39" | grep -E 'PASS|WARN' | head -1)"

# ㊵ 提交路径分离（F-112）：结单/台账/文档 commit 与代码 commit 必须逐路径分开；
#    同一 commit 同时含 docs/ 与 app/src/ 即 FAIL（事故 E-05：`git add -A` 把在飞代码扫进
#    结单 commit，commit 与单号无法回源）。扫最近 5 条 commit，纯标准库 git diff-tree 判据。
SPLIT40=$(python3 data_scripts/check-commit-split.py 2>&1)
if [ $? -eq 0 ]; then ok "㊵提交路径分离" "$(echo "$SPLIT40" | grep 'COMMIT-SPLIT: PASS')"; else bad "㊵提交路径分离" "$(echo "$SPLIT40" | grep -E 'FAIL|混合' | head -3 | tr '\n' ' ')"; fi

# ㊶ 队列↔台账结构与状态一致（M-01/M-02/M-04，Q-68）：主表列数与表头一致 / 状态白名单 /
#    RUNNING 四要素（agent id、起始时间、文件域、判据锚）/ 队列与台账同 Q-xx 状态不矛盾。
#    为什么：行结构漂移会让按列解析失真；僵尸 RUNNING 曾靠人工提交擦过；双事实源两处手写必漂。
QL41=$(python3 data_scripts/check-queue-ledger.py 2>&1)
if [ $? -eq 0 ]; then ok "㊶队列台账一致" "$(echo "$QL41" | grep 'QUEUE-LEDGER: PASS')"; else bad "㊶队列台账一致" "$(echo "$QL41" | grep 'FAIL' | head -4 | tr '\n' ' ')"; fi

# ㊷ 清单状态↔修复报告一致 + 状态词白名单（M-03/M-07，Q-68）：有修复/验收报告仍标 🔴 脱钩 FAIL；
#    状态词不在五态白名单 FAIL。为什么：报告落盘没人回写清单，103 个 🔴 无法机械区分僵尸。
LS42=$(python3 data_scripts/check-ledger-status.py 2>&1)
if [ $? -eq 0 ]; then ok "㊷清单状态词" "$(echo "$LS42" | grep 'LEDGER-STATUS: PASS')"; else bad "㊷清单状态词" "$(echo "$LS42" | grep 'FAIL' | head -6 | tr '\n' ' ')"; fi

echo

echo
[ "$FAIL" -eq 0 ] && echo "GATE: PASS（可继续下一步）" || { echo "GATE: FAIL（按上面 FAIL 项先补齐）"; exit 1; }
