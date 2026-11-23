#!/usr/bin/env bash
# 出包闸门（铁律 73）：CI 绿 → 取 artifact → 校验 → 交付。整套动作固定，不靠记忆。
# 为什么: 交付曾漏 versionCode bump / 漏 aapt 断言 / 漏取 artifact，脚本把"记得"变成"必然执行"。
# 适用: 每次要 APK 时，先 bump-versioncode → commit → push → 本脚本轮询 → 成功即打印路径。
set -uo pipefail
cd "$(dirname "$0")/.."
TOK=$(grep -o 'ghp_[A-Za-z0-9]*' ~/.git-credentials | head -1)
REPO=${REPO:-bqdlz676/legado}
SHA=$(git rev-parse --short HEAD)
EXPECT_VC=$(cat .versioncode)
echo "① 目标提交 $SHA / 期望 versionCode $EXPECT_VC"

# ② 找该提交的 CI run（没跑就报错退出，不猜）
RUN=$(curl -s -H "Authorization: token $TOK" "https://api.github.com/repos/$REPO/actions/runs?per_page=5" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);rs=[r for r in d['workflow_runs'] if r['head_sha'].startswith('$SHA')];print(rs[0]['id'] if rs else '')")
[ -z "$RUN" ] && { echo "✗ 该提交没有 CI run，先 push"; exit 2; }

# ③ 轮询到结论（三布尔里的第二布尔）
for i in $(seq 1 40); do
  ST=$(curl -s -H "Authorization: token $TOK" "https://api.github.com/repos/$REPO/actions/runs/$RUN" \
    | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['status'],d['conclusion'])")
  case "$ST" in completed*) break;; esac; sleep 20
done
echo "② CI: $ST"
case "$ST" in *success*) : ;; *) echo "✗ CI 未绿，出包中止（铁律 69：先修红）"; exit 3;; esac

# ④ 取 artifact 并校验 versionCode（aapt2 断言缺失会装不上/被判降级）
ART=$(curl -s -H "Authorization: token $TOK" "https://api.github.com/repos/$REPO/actions/runs/$RUN/artifacts" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print('\n'.join('%s %s'%(a['id'],a['name']) for a in d['artifacts']) if d['artifacts'] else '')")
[ -z "$ART" ] && { echo "✗ 无 artifact"; exit 4; }
mkdir -p dist && while read -r id name; do
  curl -sL -H "Authorization: token $TOK" "https://api.github.com/repos/$REPO/actions/artifacts/$id/zip" -o "dist/$name.zip"
  unzip -oq "dist/$name.zip" -d dist/ && rm -f "dist/$name.zip"
done <<< "$ART"
APK=$(ls -t dist/*.apk 2>/dev/null | head -1)
[ -z "$APK" ] && { echo "✗ artifact 里没有 apk"; exit 5; }
if command -v aapt2 >/dev/null; then
  VC=$(aapt2 dump badging "$APK" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)
  [ "$VC" = "$EXPECT_VC" ] && echo "③ APK versionCode=$VC ✓" || { echo "✗ versionCode $VC ≠ $EXPECT_VC"; exit 6; }
else echo "③ aapt2 不存在，versionCode 断言跳过（未验证，不许当已验证）"; fi
echo "④ 交付: $APK"
