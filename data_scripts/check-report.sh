#!/usr/bin/env bash
# 报告合规检查器 —— 总控在提交子代理报告前跑一遍，缺项就打回，不许"感觉还行"。
# 为什么: 铁律 59/61/63/66 要求报告必含三节 + grep 证据 + 未验证声明；靠人眼逐份看会漏。
# 适用: 任何子代理报告进 commit 之前；用法 `bash data_scripts/check-report.sh docs/xxx.md [更多报告...]`
set -uo pipefail
cd "$(dirname "$0")/.."

fail=0
for f in "$@"; do
  if [ ! -f "$f" ]; then echo "✗ 缺文件: $f"; fail=1; continue; fi
  echo "── $f"
  miss=0
  need() { # $1=标签 $2=正则
    if grep -qE "$2" "$f"; then echo "  ✓ $1"; else echo "  ✗ $1"; miss=$((miss+1)); fi
  }
  need "二阶三阶影响" '二阶|三阶|连带影响'
  need "反例/边界"    '反例|边界'
  need "认知成本"     '认知成本'
  need "未验证项"     '未验证'
  need "grep/命中证据" 'grep|命中|0 命中'
  # 原式只认 ASCII 文件名；本项目文档多为中文名，放宽为非空白字符+扩展名+行号，仍要求「文件:行」形态不放水
  need "文件:行 落点"  '[^ 	`|()]+\.(kt|xml|gradle|md|txt):[0-9]+'
  if [ "$miss" -gt 0 ]; then echo "  ⇒ 缺 $miss 项，打回"; fail=1; else echo "  ⇒ 合规"; fi
done
exit "$fail"
