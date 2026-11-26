#!/usr/bin/env bash
# 种子自检：一条命令看清「机制是否在岗、哪一块缺了」。
# 为什么要它：仓库里已有 50+ 门禁，全量跑必出 3~6 项历史红，接手者分不清
#   「哪些红是我造成的、哪些是历史噪声」，几天后就会开始忽略红（报警疲劳），
#   门禁退化成装饰。所以种子只跑「阻断级 + 关键自测」，并把缺失模块以 MISSING
#   显式暴露——缺失绝不静默跳过，静默跳过就是假绿。
# 用法：bash data_scripts/seed-check.sh
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2

PASS=0; FAIL=0; MISS=0; UNKNOWN=0
# UNKNOWN：CI 状态不可取（凭据缺/网络断/API 错）单列计数。为什么不并入 FAIL：
# unknown 不是"门禁跑红"，而是"可信性无法确认"；单列便于末行区分原因，但终判同样阻断 READY——
# 两布尔的 CI 腿空着时报 READY 就是假绿（Q-286 复核 A-1 修复）。
step(){ # step <名称> <命令...>  —— 只看末行 + rc；rc≠0 记 FAIL
  local name="$1"; shift
  local out rc
  out=$("$@" 2>&1); rc=$?
  printf '%-38s %s (rc=%d)\n' "$name" "$(printf '%s\n' "$out" | tail -n1)" "$rc"
  if [ "$rc" -eq 0 ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi
}
skip(){ # skip <名称> <原因>  —— 模块不存在时显式记账，不算通过
  printf '%-38s MISSING (%s)\n' "$1" "$2"; MISS=$((MISS+1))
}
need(){ # need <名称> <文件> <命令...>  —— 文件在才跑，不在记 MISSING
  local name="$1" file="$2"; shift 2
  if [ -e "$file" ]; then step "$name" "$@"; else skip "$name" "$file 不存在"; fi
}

echo "==== 种子自检 $(date '+%F %H:%M') ===="

# 1) 构建可信：源码能不能编译、清单里有没有空条目、提交能不能回源
need "门禁接线(孤儿)"        data_scripts/check-gate-wiring.py        python3 data_scripts/check-gate-wiring.py
need "分片清单净化(自测)"    data_scripts/emit-shard-lists.py         python3 data_scripts/emit-shard-lists.py --selftest
need "提交来源(自测)"        data_scripts/check-commit-provenance.py  python3 data_scripts/check-commit-provenance.py --selftest
need "提交分桶(自测)"        data_scripts/check-commit-split.py       python3 data_scripts/check-commit-split.py --selftest
need "空集假绿(自测)"        data_scripts/check-vacuous-pass.py       python3 data_scripts/check-vacuous-pass.py --selftest
need "主AI空等(自测)"        data_scripts/check-idle-empty.py         python3 data_scripts/check-idle-empty.py --selftest
need "workflow契约(自测)"    data_scripts/check-workflow-contracts.py python3 data_scripts/check-workflow-contracts.py --selftest
need "永远找问题(自测)"      data_scripts/check-problem-finding.py    python3 data_scripts/check-problem-finding.py --selftest

# 2) 失败可定位：挂死时能点名 culprit；二分可把 161 条压到 1 条
need "挂死culprit取证(自测)" data_scripts/find-hang-culprit.py       python3 data_scripts/find-hang-culprit.py --selftest
need "挂死二分(自测)"        data_scripts/bisect-suspects.py         python3 data_scripts/bisect-suspects.py --selftest

# 3) 体验可判定：设计 token 契约（颜色/线宽/辉光/时长/内存预算）
need "设计token契约"         data_scripts/check-design-tokens.py     python3 data_scripts/check-design-tokens.py
# 契约 verify 绿不能证明负样本能判红，必须再跑 --selftest 通路（Q-286 复核 H-3）。
need "设计token契约(自测)"   data_scripts/check-design-tokens.py     python3 data_scripts/check-design-tokens.py --selftest
# 静态嫌疑面是挂死三条通路之一，该件自带 4/4 自测，按种子规矩（有 selftest 的机制件应在岗检查）入列。
need "静态嫌疑扫描(自测)"    data_scripts/scan-blocking-hazards.py   python3 data_scripts/scan-blocking-hazards.py --selftest
# 出包链是交付闸门，其 6/6 自测必须在岗；否则取包闸口径退化无人发现。
need "取包链(自测)"          data_scripts/fetch-apk.py               python3 data_scripts/fetch-apk.py --selftest

# 4) 降噪：阻断级门禁（历史噪声只进审计，不拦 push）
if [ -e data_scripts/gate-levels.json ]; then
  step "阻断级门禁(blocking)" bash data_scripts/rule-gate.sh --blocking-only
else
  skip "阻断级门禁(blocking)" "data_scripts/gate-levels.json 未建（门禁尚未分级）"
fi
# 分级表自身可信度：audit 补偿必须引用在册渠道、blocking 补偿必须为 null（Q-286 复核 C-1/C-2）。
need "补偿渠道在册(自检)"    data_scripts/gate-levels.json           bash data_scripts/rule-gate.sh --check-compensation

# 5) 两布尔：HEAD 与 CI 绿红（不报进度百分比、不报包）
HEAD_SHA=$(git log --format='%h' -1)
CI_STATE=$(python3 - <<'PY' 2>/dev/null
import json, re, urllib.request
try:
    pw = re.search(r'https://[^:]+:([^@]+)@github\.com', open('/home/user/.git-credentials').read()).group(1)
except Exception:
    print('unknown'); raise SystemExit
def get(u):
    r = urllib.request.Request('https://api.github.com' + u, headers={'Authorization': 'Bearer ' + pw})
    return json.load(urllib.request.urlopen(r, timeout=15))
try:
    sha = __import__('subprocess').check_output(['git','log','--format=%H','-1'], text=True).strip()
    runs = [x for x in get('/repos/bqdlz676/legado/actions/runs?per_page=10')['workflow_runs']
            if x['name'].upper().startswith('VERIFY')]
    hit = [x for x in runs if x['head_sha'] == sha]
    if not hit:
        print('none')
    else:
        r = hit[0]
        if r['status'] != 'completed':
            print('running')
        else:
            print('green' if r['conclusion'] == 'success' else 'red')
except Exception:
    print('unknown')
PY
)
printf '%-38s HEAD=%s  CI=%s\n' "两布尔" "$HEAD_SHA" "$CI_STATE"
# unknown 单列 UNKNOWN 计数并阻断 READY：CI 状态不可取时无法确认构建可信，
# 不许让种子在两布尔 CI 腿空缺的情况下报绿（Q-286 复核 A-1 修复）。
if [ "$CI_STATE" = "unknown" ]; then
  UNKNOWN=$((UNKNOWN+1))
  printf '%-38s CI 状态不可取，无法确认构建可信（凭据缺/网络断/API 错）\n' "两布尔"
fi

echo "----"
if [ "$FAIL" -eq 0 ] && [ "$MISS" -eq 0 ] && [ "$UNKNOWN" -eq 0 ]; then
  echo "SEED: READY（pass=$PASS）"
  exit 0
else
  echo "SEED: INCOMPLETE（fail=$FAIL missing=$MISS unknown=$UNKNOWN pass=$PASS）—— 不是绿，先补缺再谈功能；unknown 原因：CI 状态不可取，无法确认构建可信"
  exit 1
fi
