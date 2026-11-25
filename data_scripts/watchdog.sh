#!/usr/bin/env bash
# 失控熔断看门狗（铁律 71/86/87/89：防子代理失控吃满 2 核 / 4G）
# 为什么：子代理失控（跑重 IO/死循环/吐垃圾）是"负载高、shell 被拒"的最大根因；
#         本脚本把"该不该 kill"变成可见清单，不靠总控记性。
# 适用：每次等待子代理期间 / 每次出现"负载较高"后 / 每次收工前，跑一遍。
cd "$(dirname "$0")/.." || exit 1

echo "=== ① 负载与内存（判断是否已被打满）==="
uptime
free -h | sed -n '1,2p'

echo
echo "=== ② 吃 CPU 前 8（找失控/孤儿进程）==="
ps aux --sort=-%cpu | awk 'NR>1{printf "%5.1f%%CPU %5.1f%%MEM %s %s %s\n",$3,$4,$11,$12,$13}' | head -8

echo
echo "=== ③ 构建进程（本地禁编译，有即违规）==="
pgrep -af 'gradle|kotlinc|assemble|aapt2' || echo "无构建进程"

echo
echo "=== ④ 在飞子代理（>15 分钟无产物应判废 kill）==="
LEDGER=docs/派单台账.md
if [ -f "$LEDGER" ]; then
  grep -E '^\|.*\| *在飞' "$LEDGER" || echo "无在飞条目"
else
  echo "缺 $LEDGER"
fi

echo
echo "=== ⑤ 孤儿 git/临时进程（多轮 kill 后残留）==="
pgrep -af 'git|python3' | head -20 || echo "无"

echo
echo "=== ⑥ 请求率探针（各 agent 工具调用数，>500 判失控）==="
# 会话根目录：默认沿用现状（本机 $HOME/.doubao/.../sessions）；换机/换会话目录时用环境变量 SESSIONS_ROOT 覆盖，不写死绝对路径。
SESSIONS_ROOT="${SESSIONS_ROOT:-$HOME/.doubao/agent_mode/workspace/.sessions}"
# 会话 ID：优先环境变量 SID；否则从脚本位置推导（仓库所在 chats 目录名）；取不到留空走兜底。
SID="${SID:-$(basename "$(cd "$(dirname "$0")/../.." && pwd)" 2>/dev/null)}"
# 解析顺序：位置参数 $1 > 环境变量 AGENTS_DIR > 按 SID 推导路径 > SESSIONS_ROOT 下最新会话兜底。
AGENTS_DIR="${1:-${AGENTS_DIR:-$SESSIONS_ROOT/${SID:+$SID/}agents}}"
[ -d "$AGENTS_DIR" ] || AGENTS_DIR="$(ls -dt "$SESSIONS_ROOT"/*/agents 2>/dev/null | head -1)"
echo "解析出的 agents 目录: ${AGENTS_DIR:-（未命中）}"
if [ -d "$AGENTS_DIR" ]; then
  for t in "$AGENTS_DIR"/*/system/trajectory.jsonl; do
    [ -f "$t" ] || continue
    n=$(grep -c '"type":"function"' "$t" 2>/dev/null); n=${n:-0}
    aid=$(basename "$(dirname "$(dirname "$t")")")
    flag=""; [ "$n" -gt 500 ] && flag="  <== 失控嫌疑"
    printf "%6s  %s%s\n" "$n" "$aid" "$flag"
  done | sort -rn | head -20
else
  echo "未找到 agents 目录（可传参 bash data_scripts/watchdog.sh <agents绝对路径>，或用环境变量 AGENTS_DIR / SESSIONS_ROOT / SID 覆盖）"
fi

echo
echo "=== ⑦ 请求率快照（记录增量，回放限流临界点用）==="
RRLOG="data_scripts/.reqrate.log"
TOTAL=$(find "$AGENTS_DIR" -name 'trajectory.jsonl' -exec grep -h -c '"type":"function"' {} + 2>/dev/null | awk '{s+=$1} END{print s+0}')
NOW=$(date +%s)
if [ -f "$RRLOG" ] && [ "$TOTAL" -ge 0 ]; then
  read -r LAST_TS LAST_TOTAL < "$RRLOG"
  DT=$((NOW - LAST_TS)); DN=$((TOTAL - LAST_TOTAL))
  if [ "$DT" -gt 0 ] && [ "$DN" -ge 0 ]; then
    RATE=$(awk "BEGIN{printf \"%.1f\", $DN/$DT*60}")
  else RATE="—"; fi
  echo "上次 $(date -d @$LAST_TS '+%m-%d %H:%M:%S') 总 $LAST_TOTAL → 本次 $(date -d @$NOW '+%m-%d %H:%M:%S') 总 $TOTAL"
  echo "间隔 ${DT}s，增量 ${DN} 次，请求率 ${RATE} 次/分钟"
else
  echo "首次快照：$(date -d @$NOW '+%m-%d %H:%M:%S') 总 $TOTAL（下次运行开始算增量速率）"
fi
printf "%s %s\n" "$NOW" "$TOTAL" > "$RRLOG"
