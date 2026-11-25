#!/usr/bin/env bash
# 唯一允许的 push 入口（O3/F-109）：先跑门禁 + 查 CI 窗口，两者都过才推。
# 为什么：单靠"我记得攒批"已被 K-20 证伪；把纪律前移到脚本，人只需要跑这一个命令。
# 用法：bash data_scripts/pre-push.sh ["commit message"]
set -u
cd "$(dirname "$0")/.." || exit 1
if [ -n "$(git status --porcelain)" ]; then
  if [ -n "${1:-}" ]; then git add -A && git commit -q -m "$1" || exit 1; else echo "有未提交改动且未给 commit message"; exit 1; fi
fi
bash data_scripts/rule-gate.sh > /tmp/gate.out 2>&1
if ! grep -q '^GATE: PASS' /tmp/gate.out; then echo "门禁未过，拒绝 push:"; grep -E 'FAIL' /tmp/gate.out | head -6; exit 1; fi
python3 data_scripts/check-push-window.py || { echo "CI 在飞，拒绝 push（等终态）"; exit 1; }
git push -q origin main && echo "PUSHED $(git rev-parse --short HEAD)"
