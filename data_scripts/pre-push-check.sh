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
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2

STRICT="${1:-}"
FAILED=0

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
if ! python3 data_scripts/update-handover.py >/tmp/pre-push-handover.log 2>&1; then
  echo "[2/5] 交接快照刷新失败（见 /tmp/pre-push-handover.log）"; FAILED=1
else
  echo "[2/5] 交接快照：OK"
fi

# 3) 证据三件套：只查本轮改动/新增的报告，避免历史文档误伤。
DOCS=$(git status --porcelain | sed -n 's/^...//p' | tr -d '"' | grep -E '^docs/(修复|验收)-.*\.md$' || true)
if [ -n "$DOCS" ]; then
  # shellcheck disable=SC2086
  if ! python3 data_scripts/check-evidence.py $DOCS | tail -3; then
    FAILED=1
  fi
else
  echo "[3/5] 本轮无报告改动"
fi

# 3.5) 报告字段门禁（锚点卡/结论标记/自查反例/证据锚点，L1 的机检入口）
if ! python3 data_scripts/check-report-fields.py | tail -3; then
  FAILED=1
fi

# 3.6) Kotlin 语法级预检：沙箱无编译链，语法错只能靠云端 CI 发现，一次错让两路 CI 同时红
if ! python3 data_scripts/kotlin-syntax-lint.py | tail -3; then
  echo "[3.6] Kotlin 语法预检未过"; FAILED=1
fi

# 3.7) 守则回执（写了不等于读了：报告必须出示当前必读指纹 + 三条回源引用）
if ! python3 data_scripts/check-onboarding.py | tail -3; then
  echo "[3.7] 守则回执未过"; FAILED=1
fi

# 4) 跨模块编译面预检
if ! python3 data_scripts/cross-module-guard.py $STRICT | tail -6; then
  echo "[4/5] 跨模块预检未过"; FAILED=1
fi

# 5) 债务棘轮：缝合怪在册债务只许减不许增（L8）
if ! python3 data_scripts/debt-ratchet.py | tail -2; then
  FAILED=1
fi

# 6) 总门禁
bash data_scripts/rule-gate.sh 2>&1 | tail -1
bash data_scripts/rule-gate.sh >/tmp/pre-push-gate.log 2>&1 || { grep -E "FAIL" /tmp/pre-push-gate.log | head -5; FAILED=1; }

if [ "$FAILED" -ne 0 ]; then
  echo "PRE-PUSH: FAIL（就地修，不推）"; exit 1
fi
echo "PRE-PUSH: PASS（可 push）"
