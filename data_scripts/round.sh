#!/usr/bin/env bash
# 每轮调度第一动作：一眼看清"功能在不在动、队列有没有卡死"
# 为什么要有它：2026-11-26 诊断发现机制吃产能，靠人记得跑仪表不可靠，所以给一条命令。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2
python3 data_scripts/progress-meter.py 24
echo ---
python3 data_scripts/queue-sync.py "$@"
