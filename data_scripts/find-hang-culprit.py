#!/usr/bin/env python3
"""CI 分片「45 分钟静默挂死」culprit 取证器（Q-269）。

为什么：分片挂死时 Gradle 不产 XML、无逐用例进度，人工只能下载 job 日志肉眼翻最后一条
STARTED。本脚本把「肉眼找 culprit」变成一条命令：按 STARTED / PASSED / FAILED / SKIPPED
四类事件配对，挑出「STARTED 之后再无终态」的用例作为挂死嫌疑，并给出挂死前最后一个已完成用例。

用法：
  python3 data_scripts/find-hang-culprit.py --job-id <job_id>      # 拉单个 job 日志
  python3 data_scripts/find-hang-culprit.py --run-id <run_id>      # 遍历 run 下全部 job
  python3 data_scripts/find-hang-culprit.py --log-file <path>      # 离线兜底：读已下载日志
  python3 data_scripts/find-hang-culprit.py --selftest             # 三份 /tmp 夹具自测，跑完自删
  可选：--top N（默认列最后 10 条嫌疑）；--repo bqdlz676/legado

凭据：只读 ~/.git-credentials 里 github.com 条目，token 不回显、不落盘。

边界：
  - 在跑 job 的 logs 接口返回 401，本脚本归类为「日志尚不可取，job 仍在跑」，不崩栈。
  - 并行 fork（maxParallelForks > 1）与 retry 重跑下，同一键可能多次 STARTED：
    配对按行序 LIFO（终态出现即清除最近一次 STARTED），重复 STARTED 单独列警告，不当嫌疑。
  - 当前仓 app/build.gradle 的 testLogging 已开 STARTED/PASSED/FAILED/SKIPPED 四事件
    （commit faa6d80，Q-277 补齐），正常可做精确配对。若仍只见到 STARTED、无任何终态事件
    （WARN_STARTED_ONLY），说明日志被截断、或任务根本没跑到测试阶段；此时未闭环列表混着
    实际通过的用例，只能作候选指向，不可定罪。
"""

import argparse
import json
import os
import pathlib
import re
import sys
import tempfile
import urllib.error
import urllib.request

REPO_DEFAULT = 'bqdlz676/legado'
EVENTS = ('STARTED', 'PASSED', 'FAILED', 'SKIPPED')
TERMINAL_EVENTS = ('PASSED', 'FAILED', 'SKIPPED')

# 事件行形态（Gradle testLogging 默认输出）：
#   io.legado.app.XxxTest > someCase STARTED
# 类名取到第一个 ` > ` 之前；用例名允许空格（JUnit5 display name）；事件词必须行尾。
RE_EVENT = re.compile(
    r'^(?P<cls>[A-Za-z0-9_$.]+)\s*>\s*(?P<case>.+?)\s+'
    r'(?P<ev>STARTED|PASSED|FAILED|SKIPPED)\s*$'
)
# GitHub job 日志可能带 ISO 时间戳前缀，先剥掉再配对（与 fetch-ci-log.sh 同口径）。
RE_TS = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s?')


# ---------- 凭据与 HTTP ----------

def read_token():
    """从 ~/.git-credentials 取 github.com token；找不到返回 None。绝不打印。"""
    cred = pathlib.Path.home() / '.git-credentials'
    if not cred.exists():
        return None
    for line in cred.read_text(errors='replace').splitlines():
        m = re.match(r'https://[^:@\s]+:([^@\s]+)@github\.com', line.strip())
        if m:
            return m.group(1)
    return None


class _NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    """跳转到签名 URL（results-receiver...）时剥离 Authorization 头。

    为什么必须剥：签名凭证已在 query 里，继续带 GitHub token 会被 403；等价于 curl 不加
    --location-trusted（-L 默认跨主机丢头，--location-trusted 才会重发）。
    """

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return urllib.request.Request(newurl, headers={'User-Agent': 'hang-culprit-forensics'})


_OPENER = urllib.request.build_opener(_NoAuthRedirect)


def api_get(url, token, raw=False):
    """GET JSON 或原始字节；401 单独抛出让上层归类「job 在跑」。"""
    headers = {
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'hang-culprit-forensics',
    }
    if token:
        headers['Authorization'] = f'token {token}'
    req = urllib.request.Request(url, headers=headers)
    try:
        body = _OPENER.open(req, timeout=60).read()
    except urllib.error.HTTPError as e:
        if e.code == 401:
            raise LogsUnavailable()
        raise
    return body if raw else json.loads(body)


class LogsUnavailable(Exception):
    """logs 接口 401：job 仍在跑，或日志已过保留期；不是解析失败。"""


def fetch_job_log(job_id, token, repo):
    url = f'https://api.github.com/repos/{repo}/actions/jobs/{job_id}/logs'
    raw = api_get(url, token, raw=True)
    return raw.decode('utf-8', 'replace')


def list_run_jobs(run_id, token, repo):
    url = f'https://api.github.com/repos/{repo}/actions/runs/{run_id}/jobs?per_page=100'
    data = api_get(url, token)
    return data.get('jobs', [])


# ---------- 解析与配对 ----------

def parse_log(text):
    """把日志文本解析成配对结果。

    返回 dict：
      status: CLEAN / HANG_SUSPECT / NO_STARTED / FORMAT_MISMATCH / WARN_STARTED_ONLY
      stats: 各事件计数
      unfinished: 未闭环用例列表（按 STARTED 行号升序），元素含 cls/case/line/after_lines
      last_completed: 最后一个终态事件 dict 或 None
      duplicate_starts: 同键多次未闭环 STARTED 的警告列表
      orphan_terminals: 无开放 STARTED 的终态事件列表
      samples: FORMAT_MISMATCH 时供对口径的原始样例行（≤20）
      total_lines: 日志总行数
    """
    raw_lines = text.splitlines()
    total_lines = len(raw_lines)

    # open_starts: key=(cls,case) -> list[dict(line, cls, case)]，LIFO 栈
    open_starts = {}
    terminals = []
    duplicate_starts = []
    orphan_terminals = []
    samples = []
    stats = {ev: 0 for ev in EVENTS}

    for idx, raw in enumerate(raw_lines, start=1):
        line = RE_TS.sub('', raw).rstrip()
        m = RE_EVENT.match(line)
        if m:
            cls, case, ev = m.group('cls'), m.group('case'), m.group('ev')
            stats[ev] += 1
            key = (cls, case)
            if ev == 'STARTED':
                stack = open_starts.setdefault(key, [])
                if stack:
                    # 同键已有未闭环 STARTED：并行交错或 retry 重跑。记警告，仍压栈，
                    # 终态按 LIFO 弹出最近一次；旧 STARTED 留到末尾才判嫌疑，不提前清。
                    duplicate_starts.append({
                        'cls': cls, 'case': case, 'line': idx,
                        'open_before': len(stack),
                    })
                stack.append({'line': idx, 'cls': cls, 'case': case})
            else:
                terminals.append({'line': idx, 'cls': cls, 'case': case, 'ev': ev})
                stack = open_starts.get(key)
                if stack:
                    stack.pop()  # 终态出现即清除最近一次 STARTED（LIFO）
                else:
                    orphan_terminals.append({'line': idx, 'cls': cls, 'case': case, 'ev': ev})
        else:
            # 收集疑似测试行供格式对口径：含 ` > ` 或事件关键词的原始行。
            if len(samples) < 20 and ('>' in line or any(w in line for w in EVENTS)):
                samples.append((idx, raw.rstrip()))

    started = stats['STARTED']
    terminal_total = sum(stats[e] for e in TERMINAL_EVENTS)

    unfinished = []
    for key, stack in open_starts.items():
        for rec in stack:
            unfinished.append({
                'cls': rec['cls'], 'case': rec['case'], 'line': rec['line'],
                'after_lines': total_lines - rec['line'],
            })
    unfinished.sort(key=lambda r: r['line'])

    last_completed = max(terminals, key=lambda t: t['line']) if terminals else None

    # 三态区分（任务书 §3）：
    if started == 0:
        status = 'NO_STARTED'
    elif terminal_total == 0:
        # 无任何终态行。再细分：日志里压根没出现终态关键词 → 日志截断或任务未跑到测试阶段
        # （仓内 build.gradle 已开四事件，不该再出现配置缺事件的情况）；关键词出现了但正则
        # 没匹配上 → 格式漂移。
        keyword_hit = any(any(w in ln for w in TERMINAL_EVENTS) for _, ln in samples)
        status = 'FORMAT_MISMATCH' if keyword_hit else 'WARN_STARTED_ONLY'
    else:
        status = 'HANG_SUSPECT' if unfinished else 'CLEAN'

    return {
        'status': status,
        'stats': stats,
        'unfinished': unfinished,
        'last_completed': last_completed,
        'duplicate_starts': duplicate_starts,
        'orphan_terminals': orphan_terminals,
        'samples': samples,
        'total_lines': total_lines,
    }


# ---------- 报告输出 ----------

STATUS_ADVICE = {
    'NO_STARTED': '无 STARTED 行：testLogging 未生效，或该 job 根本没跑到单测阶段。'
                  '核对 app/build.gradle 的 testOptions.unitTests.all.testLogging 是否挂到目标变体；'
                  '确认该 job 执行了 :app:testAppDebugUnitTest 之类的单测任务，而非仅编译/打包。',
    'FORMAT_MISMATCH': '有 STARTED 但终态行对不上正则：事件格式与假设不符。'
                       '用下方样例行与脚本 RE_EVENT 对口径（类名/用例名/事件词分隔符可能变了）。',
    'WARN_STARTED_ONLY': '日志只有 STARTED、没有任何终态关键词：当前仓 build.gradle 已开 '
                         'PASSED/SKIPPED（四事件齐备），若仍出现此警告，说明日志被截断，或任务 '
                         '未跑到测试阶段。未闭环列表里混着实际通过的用例，不能直接当挂死嫌疑；'
                         '先核对日志完整性与任务是否执行了单测，再只盯最后几个 STARTED 所在类静态排查。',
    'LOGS_UNAVAILABLE': '日志 401 取不到：job 仍在跑（logs 接口对在跑 job 返回 401），'
                        '或日志已过 GitHub 保留期。等 job 收尾后重跑本脚本；离线可先 --log-file 兜底。',
}


def report_result(label, result, top):
    print(f'=== {label} ===')
    s = result['stats']
    print(f"状态: {result['status']}")
    print(f"统计: 总行数 {result['total_lines']} | STARTED {s['STARTED']} | "
          f"PASSED {s['PASSED']} | FAILED {s['FAILED']} | SKIPPED {s['SKIPPED']} | "
          f"未闭环 {len(result['unfinished'])} | 重复STARTED {len(result['duplicate_starts'])} | "
          f"孤儿终态 {len(result['orphan_terminals'])}")

    if result['status'] in STATUS_ADVICE:
        print(f"建议: {STATUS_ADVICE[result['status']]}")

    lc = result['last_completed']
    if lc:
        print(f"最后已完成: {lc['cls']} > {lc['case']} {lc['ev']} @ line {lc['line']}")
    else:
        print('最后已完成: （日志中无任何终态事件）')

    if result['unfinished']:
        tail = result['unfinished'][-top:]
        # 标题随状态收敛：HANG_SUSPECT 才是精确配对后的挂死嫌疑；WARN_STARTED_ONLY 下
        # 未闭环混着实际通过的用例，只称「末段未闭环候选（不可定罪，仅指向）」，避免定性词误导。
        if result['status'] == 'WARN_STARTED_ONLY':
            print(f"末段未闭环候选（不可定罪，仅指向；最后 {len(tail)} 条）:")
        else:
            print(f"挂死嫌疑（STARTED 后无终态，最后 {len(tail)} 条）:")
        for i, rec in enumerate(tail, start=1):
            print(f"  {i}. line {rec['line']} | 其后 {rec['after_lines']} 行 | "
                  f"{rec['cls']} > {rec['case']}")

    if result['duplicate_starts']:
        print('重复 STARTED 警告（并行交错 / retry 重跑，未按唯一键误清）:')
        for d in result['duplicate_starts']:
            print(f"  - line {d['line']} | 此前未闭环 {d['open_before']} 次 | "
                  f"{d['cls']} > {d['case']}")

    if result['orphan_terminals']:
        print('孤儿终态（无开放 STARTED 配对，供核对交错情况）:')
        for o in result['orphan_terminals']:
            print(f"  - line {o['line']} | {o['cls']} > {o['case']} {o['ev']}")

    if result['status'] == 'FORMAT_MISMATCH':
        print('原始样例行（前 20 条，供对口径）:')
        for ln, raw in result['samples']:
            print(f"  {ln}: {raw}")
    print()


# ---------- 入口 ----------

def analyze_text(label, text, top):
    result = parse_log(text)
    report_result(label, result, top)
    return result


def main(argv):
    ap = argparse.ArgumentParser(description='CI 分片挂死 culprit 取证器')
    ap.add_argument('--run-id', help='GitHub Actions run id，遍历其下全部 job')
    ap.add_argument('--job-id', help='GitHub Actions job id，取单个 job 日志')
    ap.add_argument('--log-file', help='离线兜底：直接读本地已下载日志')
    ap.add_argument('--top', type=int, default=10, help='嫌疑列表条数，默认 10')
    ap.add_argument('--repo', default=REPO_DEFAULT, help='仓库 owner/name')
    ap.add_argument('--selftest', action='store_true', help='三份 /tmp 夹具自测，跑完自删')
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()

    if not (args.run_id or args.job_id or args.log_file):
        ap.error('必须三选一：--run-id / --job-id / --log-file')

    if args.log_file:
        text = pathlib.Path(args.log_file).read_text(errors='replace')
        r = analyze_text(f'log-file {args.log_file}', text, args.top)
        return 1 if r['unfinished'] and r['status'] == 'HANG_SUSPECT' else 0

    token = read_token()
    if not token:
        print('凭据缺失：~/.git-credentials 无 github.com 条目；离线请用 --log-file。')
        return 4

    try:
        if args.job_id:
            text = fetch_job_log(args.job_id, token, args.repo)
            r = analyze_text(f'job {args.job_id}', text, args.top)
            return 1 if r['unfinished'] and r['status'] == 'HANG_SUSPECT' else 0
        else:
            jobs = list_run_jobs(args.run_id, token, args.repo)
            if not jobs:
                print(f'run {args.run_id} 下无 job。')
                return 2
            any_suspect = False
            any_unavailable = True
            for job in jobs:
                jid = job['id']
                name = job.get('name', '')
                try:
                    text = fetch_job_log(jid, token, args.repo)
                    any_unavailable = False
                    r = analyze_text(f'job {jid} ({name})', text, args.top)
                    if r['unfinished'] and r['status'] == 'HANG_SUSPECT':
                        any_suspect = True
                except LogsUnavailable:
                    report_result(f'job {jid} ({name})',
                                  {'status': 'LOGS_UNAVAILABLE', 'stats': {e: 0 for e in EVENTS},
                                   'unfinished': [], 'last_completed': None,
                                   'duplicate_starts': [], 'orphan_terminals': [],
                                   'samples': [], 'total_lines': 0}, args.top)
            if any_suspect:
                return 1
            if any_unavailable:
                return 3
            return 0
    except LogsUnavailable:
        print(STATUS_ADVICE['LOGS_UNAVAILABLE'])
        return 3
    except urllib.error.HTTPError as e:
        print(f'HTTP {e.code}：请求失败（token 权限不足或资源不存在）。')
        return 2


# ---------- 自测夹具 ----------

FIXTURE_NORMAL = """\
> Task :app:testAppDebugUnitTest
io.legado.app.help.source.SortUrlsTest > normal multi category values are parsed STARTED
io.legado.app.help.source.SortUrlsTest > normal multi category values are parsed PASSED
io.legado.app.web.mcp.McpApplicationTest > validTokenReachesTheSdkRoute STARTED
io.legado.app.web.mcp.McpApplicationTest > validTokenReachesTheSdkRoute PASSED
io.legado.app.model.jsSource.JsSourceConfigTest > picks first valid rule STARTED
io.legado.app.model.jsSource.JsSourceConfigTest > picks first valid rule SKIPPED
io.legado.app.RhinoEvalTest > evalErrorIncludesSourceContext STARTED
io.legado.app.RhinoEvalTest > evalErrorIncludesSourceContext FAILED
io.legado.app.RhinoEvalTest > evalErrorIncludesSourceContext > expected: <5> but was: <6>
> Task :app:testAppDebugUnitTest FAILED
"""

FIXTURE_HANG = """\
> Task :app:testAppDebugUnitTest
io.legado.app.help.source.SortUrlsTest > normal multi category values are parsed STARTED
io.legado.app.help.source.SortUrlsTest > normal multi category values are parsed PASSED
io.legado.app.web.mcp.McpApplicationTest > validTokenReachesTheSdkRoute STARTED
io.legado.app.web.mcp.McpApplicationTest > validTokenReachesTheSdkRoute PASSED
io.legado.app.model.jsSource.JsSourceConfigTest > picks first valid rule STARTED
io.legado.app.model.jsSource.JsSourceConfigTest > picks first valid rule PASSED
io.legado.app.RhinoEvalTest > evalErrorIncludesSourceContext STARTED
io.legado.app.RhinoEvalTest > evalErrorIncludesSourceContext PASSED
io.legado.app.web.mcp.McpServiceContractTest > listRoutesRequiresAuth STARTED
io.legado.app.web.mcp.McpServiceContractTest > listRoutesRequiresAuth PASSED
io.legado.app.model.ReadRecordTest > recordKeepsLastReadChapter STARTED
io.legado.app.ui.book.read.page.PageLayoutTest > layoutBreaksLongLine STARTED
"""

FIXTURE_NO_STARTED = """\
> Task :app:compileAppDebugUnitTestKotlin
e: app/src/test/java/io/legado/app/foo/FooTest.kt:12:5 Unresolved reference 'bar'
> Task :app:compileAppDebugUnitTestKotlin FAILED
Execution failed for task ':app:compileAppDebugUnitTestKotlin'.
* What went wrong:
Compilation error.
"""


def selftest():
    """三份夹具：全闭环 / 中途挂死 2 条无终态 / 无 STARTED。try/finally 自删。"""
    tmp = tempfile.mkdtemp(prefix='hang-culprit-selftest-')
    failures = []
    try:
        paths = {}
        for name, content in (('normal', FIXTURE_NORMAL), ('hang', FIXTURE_HANG),
                              ('no_started', FIXTURE_NO_STARTED)):
            p = os.path.join(tmp, f'{name}.log')
            with open(p, 'w', encoding='utf-8') as f:
                f.write(content)
            paths[name] = p

        # 夹具 1：正常全终态 → CLEAN，未闭环 0，最后已完成 = 失败那条 FAILED
        r1 = parse_log(pathlib.Path(paths['normal']).read_text())
        if r1['status'] != 'CLEAN':
            failures.append(f"normal 状态应为 CLEAN，实得 {r1['status']}")
        if r1['unfinished']:
            failures.append(f"normal 不应有未闭环，实得 {len(r1['unfinished'])} 条")
        if not r1['last_completed'] or r1['last_completed']['case'] != 'evalErrorIncludesSourceContext':
            failures.append('normal 最后已完成用例识别错误')

        # 夹具 2：中途挂死 → HANG_SUSPECT，必须点名最后 2 条无终态
        r2 = parse_log(pathlib.Path(paths['hang']).read_text())
        expected = {'recordKeepsLastReadChapter', 'layoutBreaksLongLine'}
        got = {u['case'] for u in r2['unfinished']}
        if r2['status'] != 'HANG_SUSPECT':
            failures.append(f"hang 状态应为 HANG_SUSPECT，实得 {r2['status']}")
        if got != expected:
            failures.append(f"hang 未闭环集合不符：期望 {sorted(expected)}，实得 {sorted(got)}")
        # 最后已完成应为 listRoutesRequiresAuth PASSED（其后两条 STARTED 再无终态）
        if not r2['last_completed'] or r2['last_completed']['case'] != 'listRoutesRequiresAuth':
            failures.append('hang 最后已完成用例识别错误')
        # after_lines 抽查：最后一条 STARTED 之后行数 = 总行数 - 该行号
        last = r2['unfinished'][-1]
        if last['after_lines'] != r2['total_lines'] - last['line']:
            failures.append('hang after_lines 计算错误')

        # 夹具 3：无 STARTED → NO_STARTED
        r3 = parse_log(pathlib.Path(paths['no_started']).read_text())
        if r3['status'] != 'NO_STARTED':
            failures.append(f"no_started 状态应为 NO_STARTED，实得 {r3['status']}")

        # 双向抽查：重复 STARTED / 孤儿终态配对逻辑
        overlap = (
            "io.legado.app.FooTest > retryCase STARTED\n"
            "io.legado.app.FooTest > retryCase STARTED\n"
            "io.legado.app.FooTest > retryCase PASSED\n"
        )
        ro = parse_log(overlap)
        if len(ro['duplicate_starts']) != 1:
            failures.append(f"重复 STARTED 警告应 1 条，实得 {len(ro['duplicate_starts'])}")
        if len(ro['unfinished']) != 1:
            failures.append('retry 第二次 STARTED 应残留 1 条未闭环（LIFO 只清最近一次）')

    finally:
        for p in pathlib.Path(tmp).iterdir():
            p.unlink()
        pathlib.Path(tmp).rmdir()

    if failures:
        print('SELFTEST FAIL:')
        for f in failures:
            print(f'  - {f}')
        return 1
    print('PASS 3/3 selftest 夹具全判定正确：normal 全闭环 / hang 点名末尾 2 条未闭环嫌疑'
          ' / no-started 正确归类；retry 重复 STARTED 走 LIFO 残留校验通过；夹具已自删。')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
