#!/usr/bin/env bash
# 唯一允许的 push 入口（O3/F-109/F-111）：门禁 → CI 窗口守卫 → 认证书尾分流，三者都过才推。
# 为什么：单靠"我记得攒批"已被 K-20 证伪；trailer 把"验没验"写进 commit，去向由脚本机械分流，不靠记忆。
# 分流（F-111 docs/铁律-提交认证书尾.md）：
#   全部 Verified-by/Docs-only  → 推 main（仍受 CI 窗口守卫）
#   含 Wip: 且无不合规          → 推 origin HEAD:wip/<YYYYMMDD-HHMM>，main 保持绿
#   缺 trailer / 多行 / 自评    → 两种推法都拒绝，打印短号让调度层补 trailer
# 用法：bash data_scripts/pre-push.sh ["commit message"]
set -u
cd "$(dirname "$0")/.." || exit 1
if [ -n "$(git status --porcelain)" ]; then
  if [ -n "${1:-}" ]; then git add -A && git commit -q -m "$1" || exit 1; else echo "有未提交改动且未给 commit message"; exit 1; fi
fi

# 1) 门禁（含 ㊴ 认证书尾硬校验）
bash data_scripts/rule-gate.sh > /tmp/gate.out 2>&1
if ! grep -q '^GATE: PASS' /tmp/gate.out; then echo "门禁未过，拒绝 push:"; grep -E 'FAIL' /tmp/gate.out | head -6; exit 1; fi

# 2) CI 窗口守卫：在飞 run 时任何推法都暂缓（K-20：push 会把在飞 run 打成 cancelled，main/wip 同此规矩）
python3 data_scripts/check-push-window.py || { echo "CI 在飞，拒绝 push（main/wip 都等终态）"; exit 1; }

# 3) 认证书尾分流（F-111）
python3 data_scripts/check-commit-trailer.py --classify > /tmp/trailer.out 2>&1
TRC=$?
cat /tmp/trailer.out
VERDICT=$(grep -oE 'TRAILER-VERDICT: [A-Z-]+' /tmp/trailer.out | awk '{print $2}')
if [ "$TRC" -ne 0 ] || [ "$VERDICT" = "MISSING" ]; then
  echo "分流拒绝：存在缺/不合规 trailer 的提交，main 与 wip 都不推；按上面短号补 trailer 后再走本入口。"
  exit 1
fi

case "$VERDICT" in
  ALL-VERIFIED)
    git push -q origin main && echo "PUSHED main $(git rev-parse --short HEAD)"
    ;;
  HAS-WIP)
    BRANCH="wip/$(date +%Y%m%d-%H%M)"
    # 分叉提示：统计领先 origin/main 的提交数，防 wip 堆积被误判成 main 最新（F-111 §六.1）
    AHEAD=$(git rev-list --count origin/main..HEAD 2>/dev/null || echo '?')
    git push -q origin "HEAD:$BRANCH" || { echo "wip 推送失败"; exit 1; }
    echo "PUSHED wip/$BRANCH $(git rev-parse --short HEAD)（未验收提交承载于 wip，main 保持绿）"
    echo "分流原因：存在 Wip: trailer 的未验收提交，按 F-111 不进 main。"
    echo "分叉提示：本地领先 origin/main $AHEAD 个提交；判 HEAD 最新一律以 origin/main 为准，验收通过后须合并回 main。"
    # 队列登记 wip 分支名，供调度层跟踪回并（防 wip 长期不销账）
    REG="docs/台账-wip分支.md"
    if [ ! -s "$REG" ]; then
      printf '# wip 分支登记（F-111）\n\n| 时间 | 分支 | 短号 | 主题 | 状态 |\n| --- | --- | --- | --- | --- |\n' > "$REG"
    fi
    printf '| %s | %s | %s | %s | 待验收回 main |\n' \
      "$(date +%F\ %H:%M)" "$BRANCH" "$(git rev-parse --short HEAD)" \
      "$(git log -1 --format=%s | tr '|' '/')" >> "$REG"
    echo "WIP-QUEUE: 分支 $BRANCH 已登记 $REG"
    ;;
  *)
    echo "分流未知 verdict: ${VERDICT:-空}，拒绝 push"; exit 1
    ;;
esac
