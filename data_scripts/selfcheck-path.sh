#!/usr/bin/env bash
# 接线级虚拟运行自检 v2 —— 不跑 App，也把关键路径"每一环到底接上没有"过一遍。
# 为什么: 没有运行时，最常见的故障不是逻辑错，而是"写了没接上"（组件在、没人调；入口在、兜底没写）。
# 口径: 每个节点给 1–N 个**真实符号**（名字全部经 grep 核实，不许猜）；命中即 PASS。
# 适用: 每轮收尾跑一次；整个 App 交付前必须 0 FAIL（清单 F6）。
# 用法: bash data_scripts/selfcheck-path.sh
set -uo pipefail
cd "$(dirname "$0")/.."
S=app/src/main/java/io/legado/app
fail=0; pass=0

node() { # $1=路径 $2=环节 $3=说明 $4=限定目录 $5...=候选正则（任一命中即 PASS）
  local path="$1" stage="$2" desc="$3" scope="$4"; shift 4
  local pat hit=""
  for pat in "$@"; do
    hit=$(grep -rElm1 "$pat" "$scope" 2>/dev/null | head -1)
    [ -n "$hit" ] && { printf '  ✓ %-14s %-8s %s → %s\n' "$path" "$stage" "$desc" "${hit#$S/}"; pass=$((pass+1)); return; }
  done
  printf '  ✗ %-14s %-8s %s（缺）\n' "$path" "$stage" "$desc"; fail=$((fail+1))
}
call() { # $1=路径 $2=环节 $3=被调符号 $4=调用方目录 $5=最少次数
  local n; n=$(grep -rh "$3" $4 --include=*.kt 2>/dev/null | grep -cv '^import\|^ *\*')
  if [ "$n" -ge "${5:-1}" ]; then printf '  ✓ %-14s %-8s %s ×%s\n' "$1" "$2" "$3" "$n"; pass=$((pass+1))
  else printf '  ✗ %-14s %-8s %s 调用 %s < %s\n' "$1" "$2" "$3" "$n" "${5:-1}"; fail=$((fail+1)); fi
}

echo "════ 关键路径接线自检 v2 ════"

node 进书 入口 "书架→阅读" "$S/ui/main/bookshelf" 'startActivityForBook'
node 进书 分发 "阅读壳启动" "$S/ui/book/read/ink" 'class InkReadActivity'
node 进书 数据 "书对象" "$S/model" "object ReadBook"
node 进书 渲染 "竖排页视图" "$S/ui/book/read/ink" 'VerticalPageView'
node 进书 兜底 "空章兜底" "$S/ui/book/read/ink" '空页|兜底'

node 竖排排版 引擎 "VerticalLayoutEngine.layout" "$S/ui/book/read/vertical" 'fun layout\('
call 竖排排版 调用 'VerticalLayoutEngine' "$S/ui/book/read" 2
node 竖排排版 缓存 "invalidateCache" "$S/ui/book/read/vertical" 'invalidateCache'

node 点字命中 命中 "几何命中" "$S/ui/book/read/vertical" 'fun hitTest'
node 点字命中 分发 "probeChar" "$S/ui/book/read/ink" 'probeChar'

node 气泡解析 契约 "BubbleResolver.resolve" "$S/help/bubble" 'fun resolve\('
call 气泡解析 调用 'BubbleResolver' "$S/ui/book/read/ink" 2
node 气泡解析 渲染 "InkBubble" "$S/ui/book/read/ink" 'InkBubble\('

node 查人 数据 "help/person" "$S/help/person" 'PersonDirectory|PersonAssets'
node 查人 接线 "气泡→人物" "$S/help/bubble" 'PersonProvider|PersonProfileWiring'

node 纪年 锚定 "ContextAnchor" "$S/help/chrono" 'ContextAnchor'
node 纪年 干支 "Chronology 干支" "$S/help/chrono" '干支'

node 半屏 组件 "InkHalfSheet 定义" "$S/ui/components" 'fun InkHalfSheet'
call 半屏 调用 'InkHalfSheet(' "$S/ui" 3
node 半屏 模型 "HalfSheetSection" "$S/ui/components" 'data class HalfSheetSection'

node 退书 手势 "GestureRouter" "$S/ui/book/read/ink" 'GestureRouter'
node 退书 返回 "新壳返回键处理" "$S/ui/book/read/ink" 'BackHandler|onBackPressedDispatcher'

echo
echo "结果: 通过 $pass 环 / 缺 $fail 环"
[ "$fail" -eq 0 ] || exit 1
