#!/usr/bin/env bash
# 一条命令恢复总控上下文 —— 上下文被压缩后只跑这个，别去翻历史对话。
# 为什么: 对话历史会被压缩/丢细节，仓库里的状态不会；本脚本把"我此刻需要知道的六件事"打出来。
# 适用: 每次 compaction 之后、每次开工之前、每次不确定"现在到哪一步"时。
set -uo pipefail
cd "$(dirname "$0")/.."

echo "════ 1. 坐标 ════"
echo "HEAD: $(git log --oneline -1)"
echo "远端: $(git status -sb | head -1)"
echo "versionCode(.versioncode): $(tr -d '[:space:]' < .versioncode 2>/dev/null || echo 缺失)"

echo
echo "════ 2. 最近 8 个提交 ════"
git log --oneline -8

echo
echo "════ 3. 工作区（谁有未提交改动 = 可能有在飞任务） ════"
git status --porcelain | awk '{print $1, $2}' | sed 's#app/src/main/java/io/legado/app/#…#; s#app/src/main/res/#res/#' | sort | uniq -c | sort -rn | head -12
echo "未提交文件总数: $(git status --porcelain | wc -l)"

echo
echo "════ 4. 清单队列（🔴 前 12 条） ════"
grep -n '| [A-M][0-9]* *|\|| **A8** |\|| **B1** |' docs/问题清单.md 2>/dev/null | grep '🔴' | head -12 | cut -c1-150
echo "清单总行项: $(grep -c '^|' docs/问题清单.md 2>/dev/null) ｜ 未派 🔴: $(grep -c '🔴' docs/问题清单.md 2>/dev/null) ｜ 在飞 🟡: $(grep -c '🟡' docs/问题清单.md 2>/dev/null)"

echo
echo "════ 5. 云端 CI 最近 3 次（需要 token，放在 ~/.git-credentials） ════"
TOKEN=$(grep -o 'ghp_[A-Za-z0-9]*' ~/.git-credentials 2>/dev/null | head -1 || true)
if [ -n "${TOKEN:-}" ]; then
  curl -s -H "Authorization: token $TOKEN" \
    "https://api.github.com/repos/bqdlz676/legado/actions/runs?per_page=3" \
   | python3 -c "import json,sys;d=json.load(sys.stdin);[print(' ',r['id'],r['head_sha'][:7],r['status'],r['conclusion']) for r in d.get('workflow_runs',[])]" 2>/dev/null || echo "  (取 CI 失败)"
else
  echo "  (无 token)"
fi

echo
echo "════ 6. 快照页与规则入口 ════"
echo "  续命页 : docs/当前状态.md （五槽现状 + 下一步队列 + 已否决清单）"
echo "  规则   : docs/落文-规矩总纲.md (铁律 48–66)"
echo "  台账   : docs/问题清单.md + docs/问题清单-附录-逐文件.md"
echo "  机制   : docs/机制-总控必读.md / 机制-子代理必读.md / 机制-验收代理必读.md"
echo "  模板   : docs/模板-派单任务书.md ；合规检查: bash data_scripts/check-report.sh <报告>"
echo
echo "提示: 若本脚打印的 HEAD 与 docs/当前状态.md 不符，以本脚本为准，顺手更新快照页。"
