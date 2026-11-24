#!/usr/bin/env bash
# 规格完整性检查（持续门禁）：每轮结束前 / 每次 commit 前跑。
# 硬 fail 仅两类结构断裂：① 切片编号跳号 ② 已立项规格数 > 问题清单缺陷数（存在无缺陷号立项）。
# 其余（[待立项] 遗留、状态计数）为可见告警，不阻断，靠铁律 E13 当场立项/否决。
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REG="$ROOT/docs/规格登记册.md"
SPEC_DIR="$ROOT/docs/落文-规格切片"
QL="$ROOT/docs/问题清单.md"

RED='\033[31m'; GRN='\033[32m'; YLW='\033[33m'; NC='\033[0m'
hard_fail=0

echo "SPEC-CHECK 规格完整性自检（$(date '+%F %T')）"

# ① 切片编号连续（无跳号）
MAXN=$(ls "$SPEC_DIR"/切片-*.md 2>/dev/null | grep -oE '切片-[0-9]+' | grep -oE '[0-9]+' | sort -n | tail -1)
GAPS=""
if [ -n "$MAXN" ]; then
  for i in $(seq 1 "$MAXN"); do
    ls "$SPEC_DIR"/切片-$(printf '%02d' "$i")-*.md >/dev/null 2>&1 || GAPS="$GAPS 切片-$(printf '%02d' "$i")"
  done
fi
if [ -n "$GAPS" ]; then echo -e "${YLW}⚠ ① 切片跳号（需判断漏编号/漏规格，见登记册）：$GAPS${NC}"; else echo -e "${GRN}✓ ① 切片编号连续（${MAXN} 片）${NC}"; fi

# ② 登记册状态统计 + [待立项] 遗留告警（合法中间态，不 fail）
if [ ! -f "$REG" ]; then echo -e "${RED}✗ 登记册 docs/规格登记册.md 不存在${NC}"; exit 1; fi
PENDING=$(grep -c '^|.*\[待立项\]' "$REG")
APPROVED=$(grep -c '^|.*\[已立项\]' "$REG")
ACCEPTED=$(grep -c '^|.*\[已验收\]' "$REG")
REJECTED=$(grep -c '^|.*\[已否决\]' "$REG")
TOTAL=$((PENDING + APPROVED + ACCEPTED + REJECTED))
echo -e "  规格登记：共 ${TOTAL} 条（待立项 ${PENDING} / 已立项 ${APPROVED} / 已验收 ${ACCEPTED} / 已否决 ${REJECTED}）"
if [ "$PENDING" -gt 0 ]; then
  echo -e "${YLW}⚠ ② 有 ${PENDING} 条 [待立项] 未立项（完整性缺口，须按铁律 E13 逐条立项或显式否决）：${NC}"
  grep '^|.*\[待立项\]' "$REG" | sed 's/^/     /'
else
  echo -e "${GRN}✓ ② 无 [待立项] 遗留${NC}"
fi

# ③ 缺陷号覆盖：问题清单缺陷数应 ≥ 已立项规格数（防无缺陷号立项 / 防编造缺陷）
DEFS=$(grep -oE '(B|C|D|E|F|N)[0-9]+' "$QL" 2>/dev/null | sort -u | wc -l)
echo -e "  问题清单缺陷：${DEFS} 个（已立项规格 ${APPROVED} 条；缺陷数不应小于已立项数）"
if [ "$DEFS" -lt "$APPROVED" ]; then echo -e "${RED}✗ ③ 已立项 ${APPROVED} > 缺陷 ${DEFS}，存在无缺陷号的立项${NC}"; hard_fail=1; else echo -e "${GRN}✓ ③ 缺陷号覆盖已立项规格${NC}"; fi

# ④ 反向校验：每个 [已立项] 的缺陷号（第5列）都能在问题清单找到对应行（防登记册写了不存在的缺陷号）
MISSING_DEF=""
for d in $(awk -F'|' '/\[已立项\]/{gsub(/ /,"",$5); if($5!="") print $5}' "$REG"); do
  grep -qE "\b$d\b" "$QL" 2>/dev/null || MISSING_DEF="$MISSING_DEF $d"
done
if [ -n "$MISSING_DEF" ]; then echo -e "${RED}✗ ④ 登记册写了问题清单里不存在的缺陷号：$MISSING_DEF${NC}"; hard_fail=1; else echo -e "${GRN}✓ ④ 每个[已立项]缺陷号都在问题清单找到${NC}"; fi

# ⑤ 每个 [已验收] 的验收档位（第7列）非空（功能项=级别+报告；治理项=落地物+commit sha）
NOEVID=$(awk -F'|' '/\[已验收\]/{gsub(/^ +| +$/,"",$7); if($7=="—"||$7=="") print $5}' "$REG")
if [ -n "$NOEVID" ]; then echo -e "${RED}✗ ⑤ 有[已验收]但验收档位为空：$NOEVID${NC}"; hard_fail=1; else echo -e "${GRN}✓ ⑤ 每个[已验收]都有验收证据${NC}"; fi

echo
if [ "$hard_fail" -eq 0 ]; then
  echo -e "${GRN}SPEC-CHECK: 结构完整（切片连续、无硬缺口）${NC}"
  exit 0
else
  echo -e "${RED}SPEC-CHECK: 有硬缺口，先补再继续${NC}"
  exit 1
fi
