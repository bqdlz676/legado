#!/usr/bin/env bash
# 唯一允许的 push 入口（F-109/F-111，节拍批量推送）：
#   距上次 push ≥ 15 分钟才允许一次攒批推送；窗口未到直接退出 0，不阻塞本地继续 commit。
# 为什么：K-20 实证连环 push 会把在飞 run 打成 cancelled；节拍攒批把多次触发合成一次，
#   降 push 次数与 runner 分钟数。trailer（F-111）只做事后追溯，不参与本脚本分流。
# 上次 push 时间戳落盘 docs/.last_push_epoch（已 gitignore）；取不到时间戳时退化为队列 PENDING=0 守卫。
# 唯一真阻断：工作区有未提交改动且未给 commit message（避免漏文件）。
# 用法：bash data_scripts/pre-push.sh ["commit message"]
set -u
cd "$(dirname "$0")/.." || exit 1

WINDOW_MIN=15
EPOCH_FILE="docs/.last_push_epoch"
WINDOW_SEC=$((WINDOW_MIN * 60))

# 1) 真阻断：工作区不干净。给了 message 就先提交；没给则不许推，避免漏文件。
if [ -n "$(git status --porcelain)" ]; then
  if [ -n "${1:-}" ]; then git add -A && git commit -q -m "$1" || exit 1; else echo "有未提交改动：先 commit（或把 commit message 作参数传入），本次不推"; exit 1; fi
fi

# 2) 节拍窗口判定
now=$(date +%s)
last=0
if [ -s "$EPOCH_FILE" ]; then
  last=$(tr -d '[:space:]' < "$EPOCH_FILE")
  case "$last" in (*[!0-9]*) last=0;; esac
fi

if [ "$last" -gt 0 ]; then
  elapsed=$((now - last))
  if [ "$elapsed" -lt "$WINDOW_SEC" ]; then
    remain=$(( (WINDOW_SEC - elapsed) / 60 + 1 ))
    echo "窗口未到：距上次 push 不足 ${WINDOW_MIN} 分钟（还剩约 ${remain} 分钟），本地继续 commit，本次不推。"
    exit 0
  fi
else
  # 无时间戳（首次/文件丢失）：退化为队列守卫，PENDING>0 顺延到下次调用；不硬拦。
  PEND=$(python3 data_scripts/queue_stats.py 2>/dev/null | grep -oE 'PENDING=[0-9]+' | cut -d= -f2)
  if [ "${PEND:-0}" -gt 0 ]; then
    echo "无上次 push 时间戳，退化为队列守卫：PENDING=${PEND}，本地继续 commit，本次不推。"
    exit 0
  fi
fi

# 3) 无未推提交则无事可做
COMMITS=$(git log origin/main..HEAD --format='%h %s')
if [ -z "$COMMITS" ]; then echo "本地与 origin/main 无差异，无需 push"; exit 0; fi

# 4) 攒批一次推 main（节拍窗口内只允许这一次；时间戳在成功后落盘，防窗口内连推二次）
git push -q origin main || { echo "push 失败，本次未落时间戳"; exit 1; }
printf '%s\n' "$now" > "$EPOCH_FILE"

echo "PUSHED main $(git rev-parse --short HEAD)"
echo "本次推送提交："
echo "$COMMITS" | sed 's/^/  /'
echo "距下次可推窗口约 ${WINDOW_MIN} 分钟（时间戳 $EPOCH_FILE）"
