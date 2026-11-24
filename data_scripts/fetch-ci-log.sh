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

# 日志接口 302 到 results-receiver.actions.githubusercontent.com 的签名 URL：
# 签名凭证已在 query 里，继续带 GitHub Authorization 头会被 403。跨主机跳转时剥离该头。
class _NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return urllib.request.Request(newurl, headers={'User-Agent': 'ci-log-attribution'})

_OPENER = urllib.request.build_opener(_NoAuthRedirect)

def api(path, raw=False):
    req = urllib.request.Request(API + path, headers={
        'Authorization': f'token {TOKEN}',
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'ci-log-attribution',
    })
    body = _OPENER.open(req, timeout=60).read()
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
    # 文档里不留沙箱绝对路径：离线重跑只标记来源类型，路径属本机临时信息。
    src = 'local zip（offline rerun）'
else:
    zbytes = api(f'/actions/runs/{RUN_ID}/logs', raw=True)
    src = 'actions logs api'

zf = zipfile.ZipFile(io.BytesIO(zbytes))
entries = [n for n in zf.namelist() if n.endswith('.txt')]

# 3. 选日志：扫全部非 system 条目（job 拼接主日志 + per-job 分步目录下的步骤 txt）。
# 为什么不再只挑名字含 "unit test" 的文件：配置阶段失败发生在 Discover 的
# "Compile unit test sources" 分步里，而依赖解析/DSL 错可能落在任何 job 的任意步骤；
# 拼接主日志与分步日志内容重复，下游分类用 seen 集合按内容去重，不会重复计数。
chosen = [n for n in entries if not n.endswith('system.txt')] or entries

TIMESTAMP = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s?')
lines = []
for n in chosen:
    for raw in zf.read(n).decode('utf-8', 'replace').splitlines():
        lines.append((n, TIMESTAMP.sub('', raw)))

# 4. 三类归因正则
# Kotlin/Java 编译错：兼容 file:/// 绝对路径、/ 开头绝对路径与仓库相对路径（e: app/src/.../Foo.kt:109:36 ...）
RE_COMPILE = re.compile(r'e:\s+(?:file://)?(/\S+\.(?:kt|java)|\S+\.(?:kt|java)):(\d+):(\d+)\s*(.*)')
RE_COMPILE_TASK = re.compile(r'> Task :app:compile\S* FAILED')
# 编译任务整体失败 / Gradle "What went wrong" 段头：Discover 编译炸时先于 e: 行出现，必须能抓到
RE_TASK_FAIL = re.compile(r"Execution failed for task '(:app:\S+)'")
RE_WHAT_WRONG = re.compile(r'^\*\s*What went wrong:')
# 异常堆栈行（类名开头 / Caused by）不重复计入任务失败行
RE_STACK = re.compile(r'^(org\.gradle\.|java\.|kotlin\.|sun\.|jdk\.|Caused by:)')
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
# 配置/脚本评估阶段错：还没进任何 Task 就炸（DSL 属性名、插件/仓库解析、build.gradle 行号）。
# 与 Kotlin 编译 `e: file.kt:行:列` 区分开——这类错在所有跑 :app 的 job 上都会先炸。
RE_CONFIG = re.compile(
    r"Could not set unknown property|A problem occurred evaluating project|"
    r"MissingPropertyException|No such property|Could not resolve|"
    r"Build file '.*' line: \d+", re.I)
RE_CONFIG_SKIP = re.compile(r'^\s*at |^\s*\.\.\. \d+ more|DirectMethodHandle|sun\.reflect|jdk\.internal')

compiles, test_fails, pack_hits, config_hits, seen = [], [], [], [], set()
test_summary = None
pending_wrong = False  # What went wrong 段头缓存，等下一条 Execution failed 行决定归编译类还是单测类
pending_wrong_line = ''
for name, line in lines:
    if not line.strip():
        continue
    if RE_WHAT_WRONG.search(line):
        pending_wrong = True
        pending_wrong_line = line
        continue
    m_task = RE_TASK_FAIL.search(line)
    if pending_wrong and (line.lstrip().startswith('>') or m_task):
        pending_wrong = False
        tname = m_task.group(1) if m_task else ''
        # 编译任务归编译错；测试任务归单测失败；段头与摘要行同桶
        bucket = compiles if tname.startswith(':app:compile') else test_fails
        for ln in (pending_wrong_line, line.strip()):
            key = ('ww', ln)
            if key in seen:
                continue
            seen.add(key)
            if bucket is compiles:
                compiles.append((name, 'task', '-', '-', ln))
            else:
                test_fails.append((name, 'task', ln))
        continue
    pending_wrong = False
    if RE_COMPILE_TASK.search(line) and not RE_STACK.search(line):
        key = ('ctask', line)
        if key not in seen:
            seen.add(key); compiles.append((name, 'task', '-', '-', line))
        continue
    if m_task and not RE_STACK.search(line):
        tname = m_task.group(1)
        key = ('ttask2', tname)
        if key not in seen:
            seen.add(key)
            if tname.startswith(':app:compile'):
                compiles.append((name, 'task', '-', '-', line))
            else:
                test_fails.append((name, 'task', line))
        continue
    m = RE_COMPILE.search(line)
    if m:
        key = ('e', m.group(1), m.group(2), m.group(3), m.group(4).strip())
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
    if RE_CONFIG.search(line) and not RE_CONFIG_SKIP.search(line):
        key = ('cfg', line)
        if key not in seen:
            seen.add(key); config_hits.append((name, line))

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
    f.write(f'- 资源/打包错：{len(pack_hits)} 条\n')
    f.write(f'- 配置/脚本评估错：{len(config_hits)} 条\n\n')

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

    f.write('\n## 四、Gradle 配置 / 脚本评估错（未进 Task 即炸）\n\n')
    if config_hits:
        f.write('| 日志文件 | 命中行 |\n|---|---|\n')
        for name, line in config_hits:
            f.write(f'| {name} | `{line[:160]}` |\n')
    else:
        f.write('无命中。\n')

# 6. stdout ≤30 行摘要
print(f'run {RUN_ID} | {conclusion} | {sha} | {created}')
print(f'日志来源: {src}；扫描 {len(chosen)} 个日志文件')
print(f'编译错 {len(compiles)} | 单测失败 {len(test_fails)} | 资源/打包错 {len(pack_hits)} | 配置/脚本评估错 {len(config_hits)}')
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
print('--- 配置/脚本评估错（前 8）---')
for name, line in config_hits[:8]:
    print(f'  {line[:120]}')
print(f'完整命中: {out_md}')
PY
