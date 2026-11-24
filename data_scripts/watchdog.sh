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
echo "=== ④ 在飞子代理（>40 分钟无产物应判废 kill）==="
LEDGER=docs/派单台账.md
if [ -f "$LEDGER" ]; then
  grep -E '^\|.*\| *在飞' "$LEDGER" || echo "无在飞条目"
else
  echo "缺 $LEDGER"
fi

echo
echo "=== ⑤ 孤儿 git/临时进程（多轮 kill 后残留）==="
pgrep -af 'git|python3' | head -20 || echo "无"
