#!/usr/bin/env bash
# CI 日志归因抓取器 —— 把"修红首步"自动化：下载指定 run（或最近一次 failure）日志 zip，
# 解压后按三类归因：编译错 / 单测失败 / 资源·打包错；≤30 行摘要上 stdout，完整命中落 docs/CI归因-<run_id>.md。
# 为什么: 修红期近 24h 50 次 run 中 41 红，人肉翻日志首步成本最高；先机械分类再看人，压缩红灯固定成本。
# 用法:
#   bash data_scripts/fetch-ci-log.sh <run_id>            # 抓取指定 run 日志归因
#   bash data_scripts/fetch-ci-log.sh latest-failure      # 自动取最近一次 failure run
#   bash data_scripts/fetch-ci-log.sh <run_id> <本地zip>  # 离线重跑：复用已下载 zip，不再请求日志接口
# 适用: bqdlz676/legado GitHub Actions；token 读 ~/.git-credentials 的 github.com 条目。
# 边界: 本脚本只做归因展示，不改任何测试、不封兜底；历史 run 日志 GitHub 保留期内均可离线复跑。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RUN_ID="${1:-latest-failure}"
LOCAL_ZIP="${2:-}"

# 从 ~/.git-credentials 解析 github.com token（不回显到日志）
TOKEN="$(python3 - <<'PY'
import re, os
try:
    for line in open(os.path.expanduser('~/.git-credentials')):
        m = re.match(r'https://([^:]+):([^@]+)@github\.com', line.strip())
        if m:
            print(m.group(2)); break
except FileNotFoundError:
    pass
PY
)"
REPO="$(git remote get-url origin 2>/dev/null | sed -E 's#https://github\.com/##; s#\.git$##')"

export CI_RUN_ID="$RUN_ID"
export CI_LOCAL_ZIP="$LOCAL_ZIP"
export CI_TOKEN="$TOKEN"
export CI_REPO="$REPO"

python3 - <<'PY'
import os, re, sys, json, zipfile, io, glob, tempfile, urllib.request, urllib.parse
from datetime import datetime

RUN_ID = os.environ['CI_RUN_ID']
LOCAL_ZIP = os.environ.get('CI_LOCAL_ZIP', '')
TOKEN = os.environ.get('CI_TOKEN', '')
REPO = os.environ['CI_REPO']
API = f'https://api.github.com/repos/{REPO}'

def api(path, raw=False):
    req = urllib.request.Request(API + path, headers={
        'Authorization': f'token {TOKEN}',
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'ci-log-attribution',
    })
    body = urllib.request.urlopen(req, timeout=60).read()
    return body if raw else json.loads(body)

# 1. 解析 run_id（latest-failure 取最近一次 failure）
if RUN_ID == 'latest-failure':
    data = api('/actions/runs?status=failure&per_page=1&branch=main')
    runs = data.get('workflow_runs', [])
    if not runs:
        print('未找到 failure run'); sys.exit(1)
    RUN_ID = str(runs[0]['id'])
    print(f'latest-failure -> run {RUN_ID}')

meta = api(f'/actions/runs/{RUN_ID}')
conclusion = meta.get('conclusion') or meta.get('status')
sha = (meta.get('head_sha') or '')[:7]
title = meta.get('display_title', '')
created = meta.get('created_at', '')
html_url = meta.get('html_url', '')

# 2. 取日志 zip：优先本地缓存 zip（离线重跑），否则走日志接口
if LOCAL_ZIP and os.path.exists(LOCAL_ZIP):
    zbytes = open(LOCAL_ZIP, 'rb').read()
    src = f'local:{LOCAL_ZIP}'
else:
    zbytes = api(f'/actions/runs/{RUN_ID}/logs', raw=True)
    src = 'actions logs api'

zf = zipfile.ZipFile(io.BytesIO(zbytes))
entries = [n for n in zf.namelist() if n.endswith('.txt')]

# 3. 选日志：优先名字含 unit test 的步骤文件；否则取 job 拼接主日志（0_*.txt / 单条非 system），模糊匹配兜底
unit = [n for n in entries if 'unit test' in n.lower()]
if unit:
    chosen = unit
else:
    main = [n for n in entries if not n.endswith('system.txt')]
    chosen = main or entries

TIMESTAMP = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s?')
lines = []
for n in chosen:
    for raw in zf.read(n).decode('utf-8', 'replace').splitlines():
        lines.append((n, TIMESTAMP.sub('', raw)))

# 4. 三类归因正则
RE_COMPILE = re.compile(r'e:\s+(?:file://)?(/\S+\.(?:kt|java)|\S+\.(?:kt|java)):(\d+):(\d+)\s*(.*)')
RE_COMPILE_TASK = re.compile(r'> Task :app:compile\S* FAILED')
RE_TEST_FAIL = re.compile(r'^([A-Za-z0-9_$.]+)\s+>\s+(.*?)\s+FAILED\s*$')
RE_TEST_SUMMARY = re.compile(r'(\d+)\s+tests completed,\s*(\d+)\s+failed')
RE_TEST_TASK = re.compile(r'> Task :app:testAppDebugUnitTest FAILED')
# 注意：不把 `::error::` 工作流注解、正常任务名（mergeReleaseResources 非 FAILED）、
# 以及脚本里的 aapt2 变量赋值/命令回显算作打包错；只命中带失败语境的资源/链接诊断。
RE_PACK = re.compile(
    r"Execution failed for task ':app:assemble|"
    r'Duplicate class|Manifest merger failed|(MergeResources|mergeReleaseResources)\s+FAILED|'
    r'Missing resource|Package .* not found|linkage error|'
    r'AAPT2? error|AndroidManifest\.xml.*(missing|error|conflict)', re.I)
RE_PACK_SKIP = re.compile(r'\bAAPT=|\$"?AAPT\b|aapt2 \|', re.I)

compiles, test_fails, pack_hits, seen = [], [], [], set()
test_summary = None
for name, line in lines:
    if not line.strip():
        continue
    if RE_COMPILE_TASK.search(line):
        key = ('ctask', line)
        if key not in seen:
            seen.add(key); compiles.append((name, 'task', '-', '-', line))
        continue
    m = RE_COMPILE.search(line)
    if m:
        key = ('e', m.group(1), m.group(2), m.group(3))
        if key not in seen:
            seen.add(key); compiles.append((name, m.group(1), m.group(2), m.group(3), m.group(4).strip()))
        continue
    m = RE_TEST_SUMMARY.search(line)
    if m:
        test_summary = (int(m.group(1)), int(m.group(2)))
    m = RE_TEST_FAIL.search(line)
    if m:
        key = ('tf', m.group(1), m.group(2))
        if key not in seen:
            seen.add(key); test_fails.append((name, m.group(1), m.group(2)))
        continue
    if RE_TEST_TASK.search(line):
        key = ('ttask', line)
        if key not in seen:
            seen.add(key); test_fails.append((name, 'task', line))
        continue
    if RE_PACK.search(line) and not RE_PACK_SKIP.search(line):
        key = ('pk', line)
        if key not in seen:
            seen.add(key); pack_hits.append((name, line))

# 5. 写完整归因文档
out_md = os.path.join('docs', f'CI归因-{RUN_ID}.md')
os.makedirs('docs', exist_ok=True)
with open(out_md, 'w') as f:
    f.write(f'# CI 归因 · run {RUN_ID}\n\n')
    f.write(f'| 项 | 值 |\n|---|---|\n')
    f.write(f'| conclusion | {conclusion} |\n')
    f.write(f'| commit | {sha} |\n')
    f.write(f'| 标题 | {title} |\n')
    f.write(f'| 时间 | {created} |\n')
    f.write(f'| 链接 | {html_url} |\n')
    f.write(f'| 日志来源 | {src}；扫描文件 {len(chosen)} 个 |\n\n')
    f.write(f'## 摘要\n\n')
    f.write(f'- 编译错：{len(compiles)} 条\n')
    f.write(f'- 单测失败：{len(test_fails)} 条' + (f'（汇总：{test_summary[0]} tests completed, {test_summary[1]} failed）' if test_summary else '') + '\n')
    f.write(f'- 资源/打包错：{len(pack_hits)} 条\n\n')

    f.write('## 一、编译错（`e: 文件:行:列`）\n\n')
    if compiles:
        f.write('| 日志文件 | 文件 | 行 | 列 | 信息 |\n|---|---|---|---|---|\n')
        for name, path, ln, col, msg in compiles:
            if path == 'task':
                f.write(f'| {name} | `[task]` | - | - | {msg[:160]} |\n')
            else:
                f.write(f'| {name} | `{path}` | {ln} | {col} | {msg[:160]} |\n')
    else:
        f.write('无命中。\n')

    f.write('\n## 二、单测失败（`类 > 用例 FAILED`）\n\n')
    if test_fails:
        f.write('| 日志文件 | 测试类 | 用例/任务 |\n|---|---|---|\n')
        for name, cls, case in test_fails:
            f.write(f'| {name} | `{cls}` | {case[:160]} |\n')
    else:
        f.write('无命中。\n')

    f.write('\n## 三、资源 / 打包错\n\n')
    if pack_hits:
        f.write('| 日志文件 | 命中行 |\n|---|---|\n')
        for name, line in pack_hits:
            f.write(f'| {name} | `{line[:160]}` |\n')
    else:
        f.write('无命中。\n')

# 6. stdout ≤30 行摘要
print(f'run {RUN_ID} | {conclusion} | {sha} | {created}')
print(f'日志来源: {src}；扫描 {len(chosen)} 个日志文件')
print(f'编译错 {len(compiles)} | 单测失败 {len(test_fails)} | 资源/打包错 {len(pack_hits)}')
if test_summary:
    print(f'单测汇总: {test_summary[0]} tests completed, {test_summary[1]} failed')
print('--- 编译错（前 8）---')
for name, path, ln, col, msg in compiles[:8]:
    if path == 'task':
        print(f'  [task] {msg[:100]}')
    else:
        print(f'  {path}:{ln}:{col} {msg[:100]}')
print('--- 单测失败（前 8）---')
for name, cls, case in test_fails[:8]:
    print(f'  {cls} > {case[:100]}')
print('--- 资源/打包错（前 8）---')
for name, line in pack_hits[:8]:
    print(f'  {line[:120]}')
print(f'完整命中: {out_md}')
PY
