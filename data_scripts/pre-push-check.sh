#!/usr/bin/env bash
# push 前一次性体检（2026-11-26 复盘产物）：把「我记得该跑什么」变成「脚本一定跑」。
#
# 事故根因：⑤一问题一代理（文件名/台账带两个缺陷号）、⑭回执证据（报告缺 E3 结论标记）、
# ㉖接手链（快照落后 >20 提交）三项每轮各吃 1-2 次工具往返——都是纯空转，
# 且每次都发生在「准备 push 的时候」。所以固定成一条命令，顺序固定：
#   1) 清僵尸锁（.git/index.lock 0 字节且无 git 进程时才会被删）
#   2) 刷新交接快照（㉖ 的 b 新鲜度）
#   3) 批量证据检查（⑭ 的 E1/E2/E3 三件套）
#   4) 跨模块编译面预检（F1/F2/F3，见 cross-module-guard.py）
#   5) 债务棘轮（缝合怪在册数只许减不许增）
#   6) 总门禁 rule-gate.sh（末行 GATE: PASS/FAIL 为准）
#
# 用法：bash data_scripts/pre-push-check.sh [--strict]
# 退出码：0 才允许 push；非 0 就地修，不推。
#
# 诊断纪律（2026-11-26 门禁自诊断修复）：
#   - 每个子检查输出一行明确的 PASS/FAIL；FAIL 行必须带步骤名与原因，不许只翻终态。
#   - 子检查输出先落 /tmp 日志，退出码显式取子进程 $?，不经过 tail/管道判码——
#     `cmd | tail -n` 里 tail 永远退出 0，管道一旦吃掉真实退出码，门禁就会
#     "全 PASS 却 GATE: FAIL" 且说不出原因（本次事故形态）。pipefail 只作兜底。
#   - 终结行逐条列出失败步骤清单；WARN 不导致 FAIL，真 FAIL 必须退出非零。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2

STRICT="${1:-}"
FAILED=0
FAIL_STEPS=()

# run_step <步骤名> <日志尾行数> <命令...>
# 为什么先落文件再 tail：判失败只认子进程退出码；tail 只负责把结论尾行回显到控制台，
# 完整输出留 /tmp 日志供失败后回查。日志名由步骤名派生，空格/斜杠转连字符。
run_step() {
  local name="$1" tailn="$2"; shift 2
  local log="/tmp/pre-push-$(printf '%s' "$name" | tr '/ ' '--').log"
  "$@" >"$log" 2>&1
  local rc=$?
  tail -n "$tailn" "$log" 2>/dev/null || true
  if [ "$rc" -ne 0 ]; then
    FAILED=1
    local reason
    reason=$(grep -E 'FAIL|✗|ERROR=[1-9]' "$log" | head -3 | tr '\n' ' / ' | sed 's/[[:space:]]\+/ /g; s/ \/$//')
    [ -z "$reason" ] && reason="子检查退出码 $rc（完整输出见 $log）"
    FAIL_STEPS+=("$name｜$reason")
    echo "FAIL $name —— $reason"
  else
    echo "PASS $name"
  fi
}

# 1) 僵尸锁：只有 0 字节且没有 git 进程持有才删，避免误删真正在跑的 git。
if [ -f .git/index.lock ]; then
  if [ ! -s .git/index.lock ] && ! pgrep -x git >/dev/null 2>&1; then
    rm -f .git/index.lock && echo "[1/5] 清僵尸 index.lock：已删"
  else
    echo "[1/5] index.lock 非空或有 git 进程，跳过删除"
  fi
else
  echo "[1/5] 无 index.lock"
fi

# 2) 交接快照
if ! python3 data_scripts/update-handover.py >/tmp/pre-push-2-handover.log 2>&1; then
  echo "[2/5] 交接快照刷新失败（见 /tmp/pre-push-2-handover.log）"
  FAILED=1
  FAIL_STEPS+=("[2/5] 交接快照｜刷新失败，完整输出见 /tmp/pre-push-2-handover.log")
else
  echo "[2/5] 交接快照：OK"
fi

# 3) 证据三件套：只查本轮改动/新增的报告，避免历史文档误伤。
# need quotepath=false：git 默认把中文名转八进制转义，不放开就匹配不到「修复-/验收-」前缀，
# 会让本步永远"本轮无报告改动"而静默跳过 ⑭ 的证据检查（2026-11-26 实测踩到）。
DOCS=$(git -c core.quotepath=false status --porcelain | sed -n 's/^...//p' | sed 's/^"//; s/"$//' | grep -E '^docs/(修复|验收)-.*\.md$' || true)
if [ -n "$DOCS" ]; then
  # 与 rule-gate ⑭ 同一豁免（夹具 + 存量件仅头部留痕）：判据单源在 is-legacy-headonly.py，
  # 这里只做筛选、不另造一套；否则本步会拿新规回头判 230 份历史报告的头部留痕，必红且无意义。
  JUDGE_DOCS=""
  while IFS= read -r p; do
    [ -z "$p" ] && continue
    case "$p" in data_scripts/fixtures/*|*.fixture) continue;; esac
    if python3 data_scripts/is-legacy-headonly.py "$p" >/dev/null 2>&1; then
      echo "SKIP-LEGACY $p（存量件，仅头部留痕，⑭ 同源豁免）"
      continue
    fi
    JUDGE_DOCS="$JUDGE_DOCS$p"$'\n'
  done <<< "$DOCS"
  if [ -n "${JUDGE_DOCS//[$'\n']/}" ]; then
    # shellcheck disable=SC2086
    run_step "3-回执证据" 3 python3 data_scripts/check-evidence.py ${JUDGE_DOCS//[$'\n']/ }
  else
    echo "[3/5] 报告改动均为存量头部留痕，无待判"
  fi
else
  echo "[3/5] 本轮无报告改动"
fi

# 3.5) 报告字段门禁（锚点卡/结论标记/自查反例/证据锚点，L1 的机检入口）
run_step "3.5-报告字段" 3 python3 data_scripts/check-report-fields.py

# 3.6) Kotlin 语法级预检：沙箱无编译链，语法错只能靠云端 CI 发现，一次错让两路 CI 同时红
run_step "3.6-Kotlin语法" 3 python3 data_scripts/kotlin-syntax-lint.py

# 3.61) S6 依赖符号存在性：import 的包/类必须在真实依赖索引里存在（离线不编译；脚本自行只取本轮改动 kt）
run_step "3.61-依赖符号" 3 python3 data_scripts/check-dependency-symbols.py

# 3.7) 守则回执（写了不等于读了：报告必须出示当前必读指纹 + 三条回源引用）
run_step "3.7-守则回执" 3 python3 data_scripts/check-onboarding.py

# 3.8) 只读 setter 直写（ScrollState.value 这类读公开写私有的错，只有编译器认；Q-204）
run_step "3.8-只读setter" 2 python3 data_scripts/check-readonly-setter.py

# 3.9) 结论漂移棘轮：失联锚点只许降不许升（Q-199 系列；雷达全量扫约 12s）
run_step "3.9-漂移棘轮" 1 python3 data_scripts/check-drift.py --ratchet

# 4) 跨模块编译面预检
# shellcheck disable=SC2086
run_step "4-跨模块预检" 6 python3 data_scripts/cross-module-guard.py $STRICT

# 5) 债务棘轮：缝合怪在册债务只许减不许增（L8）
run_step "5-债务棘轮" 2 python3 data_scripts/debt-ratchet.py

# 6) 总门禁：只跑一次，全量输出落日志；失败项由 rule-gate.sh 写进 /tmp/.gate_failures 清单。
GATE_LOG=/tmp/pre-push-rule-gate.log
bash data_scripts/rule-gate.sh >"$GATE_LOG" 2>&1
GATE_RC=$?
tail -n 20 "$GATE_LOG" 2>/dev/null || true
if [ "$GATE_RC" -ne 0 ]; then
  FAILED=1
  if [ -s /tmp/.gate_failures ]; then
    while IFS= read -r line; do
      [ -n "$line" ] && FAIL_STEPS+=("门禁 $line")
    done < /tmp/.gate_failures
  else
    FAIL_STEPS+=("rule-gate.sh｜退出码 $GATE_RC 但未列出失败项（完整输出见 $GATE_LOG）")
  fi
fi

echo
if [ "$FAILED" -ne 0 ]; then
  echo "PRE-PUSH: FAIL —— 失败步骤清单（${#FAIL_STEPS[@]} 项）："
  for item in "${FAIL_STEPS[@]}"; do
    echo "  ✗ $item"
  done
  echo "各步完整日志：/tmp/pre-push-*.log；逐条补齐后重跑 bash data_scripts/pre-push-check.sh"
  exit 1
fi
echo "PRE-PUSH: PASS（可 push）"
