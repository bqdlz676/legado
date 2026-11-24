#!/usr/bin/env bash
# 报告合规检查器 —— 总控在提交子代理报告前跑一遍，缺项就打回，不许"感觉还行"。
# 为什么: 铁律 59/61/63/66 要求报告必含三节 + grep 证据 + 未验证声明；靠人眼逐份看会漏。
# 证据块（2026-11-24 事故复盘）: 结构判据之外，另由 check-evidence.py 机械判 E1 命令证据 /
#   E2 文件:行 定位 / E3 实测结论标记，三条同时成立才 PASS；空话回执过不了。
# 适用: 任何子代理报告进 commit 之前；用法 `bash data_scripts/check-report.sh docs/xxx.md [更多报告...]`
# 体检: `bash data_scripts/check-report.sh --audit` 全量扫 docs/修复-*.md + docs/验收-*.md，
#   只打印 PASS/FAIL 清单留档，不据此 FAIL（历史文档缺证据是事实，等返工时补，不许改历史凑 PASS）。
set -uo pipefail
cd "$(dirname "$0")/.."

AUDIT=0
if [ "${1:-}" = "--audit" ]; then AUDIT=1; shift; fi

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

# 证据块判据：E1 命令证据 / E2 命中定位 / E3 结果标记，任一缺项整体 FAIL 并打印缺哪一项。
if [ "$AUDIT" -eq 1 ]; then
  echo "── 历史体检（只报告留档，不据此 FAIL）"
  python3 data_scripts/check-evidence.py docs/修复-*.md docs/验收-*.md
else
  EV=$(python3 data_scripts/check-evidence.py "$@" 2>&1)
  EV_RC=$?
  echo "$EV"
  if [ "$EV_RC" -ne 0 ]; then echo "  ⇒ 证据块缺项，打回"; fail=1; fi
fi
exit "$fail"
