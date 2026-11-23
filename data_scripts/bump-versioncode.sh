#!/usr/bin/env bash
# 出包前把 versionCode +1 并回写 .versioncode —— 这是唯一权威源（app/build.gradle 与 CI 都读它）。
# 为什么: Android 只认 versionCode 判断新旧，且只升不降；同号或降号会导致覆盖安装失败。
# 适用: 每次要产出可覆盖安装的 APK 前跑一次；只 +1，不许手改小。
set -euo pipefail
cd "$(dirname "$0")/.."
step="${1:-1}"
cur="$(tr -d '[:space:]' < .versioncode)"
floor=38000
if ! [[ "$cur" =~ ^[0-9]+$ ]]; then
  echo "ERROR: .versioncode 内容不是整数: '$cur'" >&2; exit 1
fi
if (( cur < floor )); then
  echo "WARN: .versioncode=$cur 低于硬地板 $floor，先抬到地板" >&2
  cur=$floor
fi
next=$(( cur + step ))
printf '%s\n' "$next" > .versioncode
echo "versionCode: $cur -> $next"
