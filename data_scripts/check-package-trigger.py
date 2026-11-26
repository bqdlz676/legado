#!/usr/bin/env python3
"""Package 触发对账闸（Q-291 缺口 3）：每个成功 Verify 是否都有对应 Package Debug APK run。

为什么要有这个脚本：
  Package 由 `workflow_run` 在上游 Verify 完成后触发；本仓库 `concurrency: cancel-in-progress`
  历史上取消过上游，出现过「Verify 绿了但 Package 没发 / 没跑」的情形（`49cc659` 三轮
  Package 全 skipped）。取包链 fetch-apk.py 只在「当下」查 Package 闸，不会回溯对账
  「过去每条绿 Verify 有没有触发 Package」。本脚本把这条对账固化成机检：逐条 Verify 反查
  同 head_sha、event=workflow_run 的 Package run。

判定四类（PAIRED / MISSING / NOT_GREEN / IN_PROGRESS）：
  PAIRED      存在同 sha 的 workflow_run Package run，且 completed/success。
  MISSING     完全没有对应 Package run（「绿了没包」闸；MISSING > 0 退出码 1）。
  NOT_GREEN   Package 存在但 skipped / failure / cancelled 等非 success，附人话原因。
  IN_PROGRESS Package 还在跑（queued / in_progress / waiting）：不算 MISSING——
              此刻只是尚未跑完，过早判缺会误报；等它结束后下一轮对账再闭合。

边界与口径：
  - 只查 API 里真实存在的 run；查不到不等于"从未触发"，可能是分页窗口外或已被清理。
  - 本脚本只做触发对账，不验签、不取包、不判断产物好坏；产物质量归 fetch-apk.py。
  - 沙箱无 JDK/SDK：只做 python3 + curl，不编译、不装系统包。

退出码：
  0  无 MISSING（NOT_GREEN / IN_PROGRESS 仅告警，不置红）
  1  存在 MISSING（有 Verify 没有任何对应 Package run）
  2  基础设施错误：凭据缺失 / 401 / 403 / API 异常 / JSON 解析失败

用法：
  python3 data_scripts/check-package-trigger.py              # 对账最近 10 条 Verify
  python3 data_scripts/check-package-trigger.py --limit 20   # 最近 20 条
  python3 data_scripts/check-package-trigger.py --selftest   # /tmp 假 JSON 夹具跑四类判定，夹具自删
"""
import argparse
import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time

REPO = 'bqdlz676/legado'
VERIFY_NAME = 'Verify'            # verify.yml 的 workflow name 前缀；影子单测名不含此串
PACKAGE_NAME = 'Package Debug APK'
# 绿闸口径与 fetch-apk.py 一致：只有 completed + success 才算绿。
GREEN = ('success',)
# 非绿结论逐条给人话原因，NOT_GREEN 行附带，不许笼统归 red。
NON_GREEN_REASON = {
    'skipped': 'Package 未执行（skipped：路径过滤或连带取消）',
    'neutral': 'Package 结论 neutral（未判定成功）',
    'cancelled': 'Package 被取消',
    'timed_out': 'Package 超时熔断',
    'action_required': 'Package 需人工处理',
    'stale': 'Package 结果已过期',
    'failure': 'Package 失败',
    'startup_failure': 'Package 启动失败',
}
DEFAULT_LIMIT = 10
DEFAULT_TIMEOUT = 300
# 在跑状态集合：这些状态不算 MISSING，单列 IN_PROGRESS 等下一轮闭合。
RUNNING_STATUS = ('queued', 'in_progress', 'waiting', 'pending', 'requested', 'waiting_for_approval')

ROOT = pathlib.Path(__file__).resolve().parent.parent


class InfraError(Exception):
    """基础设施错误：凭据 / 401 / 403 / 网络 / JSON 损坏。退出码 2。"""


def load_token():
    """从 ~/.git-credentials 读 github token；绝不打印 token 本体。
    与 fetch-apk.py 同一凭据源，避免再引入第二套配置。"""
    cred = pathlib.Path.home() / '.git-credentials'
    if not cred.exists():
        return None
    for line in cred.read_text(errors='replace').splitlines():
        m = re.match(r'https://[^:@\s]+:([^@\s]+)@github\.com', line)
        if m:
            return m.group(1)
    m = re.search(r'ghp_[A-Za-z0-9]+', cred.read_text(errors='replace'))
    return m.group(0) if m else None


def curl_json(url, tok, timeout=30):
    """GET JSON（curl 子进程；沙箱不引入 requests 依赖）。返回解析后的 dict/list。"""
    cmd = ['curl', '-sS', '-L', '--max-time', str(max(5, int(timeout))),
           '-w', '\n%{http_code}']
    if tok:
        cmd += ['-H', 'Authorization: Bearer ' + tok]
    cmd += [url]
    r = subprocess.run(cmd, capture_output=True)
    out = r.stdout or b''
    head, _, code = out.rpartition(b'\n')
    code = code.decode(errors='replace').strip()
    if r.returncode != 0 and not head:
        raise InfraError('NETWORK: curl 失败（%s）：%s'
                         % (r.returncode, r.stderr.decode(errors='replace').strip()[:200]))
    if code == '401':
        raise InfraError('AUTH=401 token 无效或过期（~/.git-credentials）')
    if code == '403':
        raise InfraError('AUTH=403 权限不足或触发 GitHub 限流（rate limit）')
    if code == '404':
        raise InfraError('NOT_FOUND=404 repo / run 路径核对')
    if code in ('None', '') or (code.isdigit() and int(code) >= 500):
        raise InfraError('UPSTREAM=%s GitHub 服务端错误' % code)
    try:
        return json.loads(head.decode(errors='replace'))
    except json.JSONDecodeError as e:
        raise InfraError('BAD_JSON=%s 响应不是 JSON（HTTP %s）' % (e, code))


def fetch_runs(tok, limit, deadline):
    """拉取最近的 workflow runs（最多翻 2 页，保证凑够 limit 条 Verify）。
    返回原始 run dict 列表，按 API 返回顺序（新→旧）。"""
    runs = []
    for page in (1, 2):
        if time.monotonic() > deadline:
            raise InfraError('TIMEOUT: 拉取 runs 超过整体超时')
        url = ('https://api.github.com/repos/%s/actions/runs?per_page=100&page=%d'
               % (REPO, page))
        data = curl_json(url, tok)
        batch = data.get('workflow_runs', [])
        if not batch:
            break
        runs.extend(batch)
        verify_count = sum(1 for w in runs if is_verify(w))
        if verify_count >= limit or len(batch) < 100:
            break
    return runs


def is_verify(run):
    """Verify 闸口径：名含 Verify 前缀，排除影子单测；不按 event 过滤（Verify 为 push）。"""
    name = run.get('name') or ''
    return VERIFY_NAME in name and 'Shadow' not in name


def is_package(run):
    """Package 对账口径：名含 Package Debug APK，且 event 必须是 workflow_run
    ——workflow_dispatch 手动触发的同名 run 不算上游触发链，不计入配对。"""
    name = run.get('name') or ''
    return PACKAGE_NAME in name and run.get('event') == 'workflow_run'


def classify(verify_run, pkg_runs):
    """对单条 Verify 对账其 Package run。
    pkg_runs: 同 head_sha 的候选 Package run 列表。返回 dict：
    state ∈ PAIRED / MISSING / NOT_GREEN / IN_PROGRESS；pkg 为选中的 run 或 None。"""
    same = [r for r in pkg_runs if r.get('head_sha') == verify_run.get('head_sha')]
    if not same:
        # 附带上游 Verify 结论：非绿 Verify 未触发 Package 可能属预期，人工复核时不至于误读；
        # 但状态仍记 MISSING（确实没有 Package run），闸口口径由调用方/报告再区分。
        vconcl = verify_run.get('conclusion')
        note = '' if vconcl in GREEN else '（上游 Verify 结论=%s，非绿未触发可能属预期，请复核）' % vconcl
        return {'state': 'MISSING', 'pkg': None,
                'reason': '无 event=workflow_run 的 Package Debug APK run' + note}
    # 同 sha 可能有多轮（历史取消重跑）：取 id 最大（最新）一条对账。
    pkg = sorted(same, key=lambda r: r.get('id') or 0)[-1]
    status = pkg.get('status')
    conclusion = pkg.get('conclusion')
    if status != 'completed':
        # 在跑不算缺：此刻只是尚未结束，过早判 MISSING 会把进行中的包误报成"绿了没包"。
        return {'state': 'IN_PROGRESS', 'pkg': pkg,
                'reason': 'Package 状态 %s，仍在跑；下一轮对账再闭合，不计 MISSING' % status}
    if conclusion in GREEN:
        return {'state': 'PAIRED', 'pkg': pkg, 'reason': 'Package completed/success'}
    reason = NON_GREEN_REASON.get(conclusion, '结论 %s 非 success' % (conclusion or 'unknown'))
    return {'state': 'NOT_GREEN', 'pkg': pkg, 'reason': reason}


def run_check(verify_runs, all_runs, limit):
    """纯对账逻辑（与网络解耦，selftest 直接复用）。返回 (rows, counts)。"""
    pkgs = [r for r in all_runs if is_package(r)]
    rows = []
    counts = {'PAIRED': 0, 'MISSING': 0, 'NOT_GREEN': 0, 'IN_PROGRESS': 0}
    for verify in verify_runs[:limit]:
        verdict = classify(verify, pkgs)
        counts[verdict['state']] += 1
        rows.append({'verify': verify, **verdict})
    return rows, counts


def format_row(row):
    v = row['verify']
    pkg = row['pkg']
    sha = (v.get('head_sha') or '')[:12]
    pkg_id = str(pkg.get('id')) if pkg else '-'
    pkg_tail = ''
    if pkg:
        pkg_tail = ' status=%s conclusion=%s' % (pkg.get('status'), pkg.get('conclusion') or '-')
    return ('%-11s verify=%s pkg=%s sha=%s verify_conclusion=%s%s | %s'
            % (row['state'], v.get('id'), pkg_id, sha,
               v.get('conclusion') or '-', pkg_tail, row['reason']))


def main(argv=None):
    ap = argparse.ArgumentParser(description='Verify ↔ Package Debug APK 触发对账闸')
    ap.add_argument('--limit', type=int, default=DEFAULT_LIMIT,
                    help='对账最近 N 条 Verify，默认 10')
    ap.add_argument('--timeout', type=int, default=DEFAULT_TIMEOUT, help='整体超时秒数，默认 300')
    ap.add_argument('--selftest', action='store_true', help='/tmp 假 JSON 夹具跑四类判定后退出')
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()

    tok = load_token()
    if not tok:
        print('FAIL: 凭据缺失（~/.git-credentials 无 github token）')
        return 2
    deadline = time.monotonic() + args.timeout
    try:
        all_runs = fetch_runs(tok, args.limit, deadline)
    except InfraError as e:
        print('FAIL: %s' % e)
        return 2
    verify_runs = [r for r in all_runs if is_verify(r)]
    print('TRIGGER-CHECK repo=%s 最近 %d 条 Verify（共拉到 %d 条 runs）'
          % (REPO, args.limit, len(all_runs)))
    rows, counts = run_check(verify_runs, all_runs, args.limit)
    for row in rows:
        print('  · ' + format_row(row))
    print('COUNTS PAIRED=%d MISSING=%d NOT_GREEN=%d IN_PROGRESS=%d'
          % (counts['PAIRED'], counts['MISSING'], counts['NOT_GREEN'],
             counts['IN_PROGRESS']))
    if counts['MISSING'] > 0:
        print('GATE=RED: %d 条 Verify 无对应 Package run（绿了没包）' % counts['MISSING'])
        return 1
    print('GATE=GREEN: 无 MISSING（NOT_GREEN / IN_PROGRESS 仅告警，下一轮复核）')
    return 0


# --------------------------------------------------------------------------- #
# selftest：/tmp 假 JSON 夹具，覆盖 PAIRED / MISSING / NOT_GREEN / IN_PROGRESS，夹具自删
# --------------------------------------------------------------------------- #

def _fake_run(rid, name, sha, event, status, conclusion):
    return {'id': rid, 'name': name, 'event': event, 'status': status,
            'conclusion': conclusion, 'head_sha': sha,
            'html_url': 'https://example.test/runs/%d' % rid}


def selftest():
    tmp = tempfile.mkdtemp(prefix='pkg-trigger-')
    try:
        sha_a, sha_b, sha_c, sha_d, sha_e, sha_f = ('a' * 40, 'b' * 40, 'c' * 40,
                                                    'd' * 40, 'e' * 40, 'f' * 40)
        verify_runs = [
            _fake_run(101, 'Verify (unit tests)', sha_a, 'push', 'completed', 'success'),
            _fake_run(102, 'Verify (unit tests)', sha_b, 'push', 'completed', 'success'),
            _fake_run(103, 'Verify (unit tests)', sha_c, 'push', 'completed', 'success'),
            _fake_run(104, 'Verify (unit tests)', sha_d, 'push', 'completed', 'success'),
            # sha_e：同名 Package 但 event=push（手动/其它触发），必须被事件口径过滤掉 → MISSING
            _fake_run(105, 'Verify (unit tests)', sha_e, 'push', 'completed', 'success'),
            # sha_f：上游 Verify 已 cancelled、无 Package → MISSING，但原因须标注非绿属预期可能。
            _fake_run(106, 'Verify (unit tests)', sha_f, 'push', 'completed', 'cancelled'),
        ]
        package_runs = [
            _fake_run(201, 'Package Debug APK', sha_a, 'workflow_run', 'completed', 'success'),
            _fake_run(203, 'Package Debug APK', sha_c, 'workflow_run', 'completed', 'skipped'),
            _fake_run(204, 'Package Debug APK', sha_d, 'workflow_run', 'in_progress', None),
            _fake_run(205, 'Package Debug APK', sha_e, 'push', 'completed', 'success'),
        ]
        # 假 JSON 夹具落 /tmp，模拟 API 响应结构；校验脚本读 JSON 后走同一纯对账逻辑。
        fixture = pathlib.Path(tmp) / 'runs.json'
        fixture.write_text(json.dumps({'verify_runs': verify_runs,
                                       'package_runs': package_runs}),
                           encoding='utf-8')
        loaded = json.loads(fixture.read_text(encoding='utf-8'))
        all_runs = loaded['verify_runs'] + loaded['package_runs']

        rows, counts = run_check(loaded['verify_runs'], all_runs, len(verify_runs))
        expect = {101: 'PAIRED', 102: 'MISSING', 103: 'NOT_GREEN',
                  104: 'IN_PROGRESS', 105: 'MISSING', 106: 'MISSING'}
        passed = 0
        for row in rows:
            rid = row['verify']['id']
            ok = row['state'] == expect[rid]
            if rid == 106:
                ok = ok and 'cancelled' in row['reason']
            if ok:
                passed += 1
            print('SELFTEST %s: verify=%d → %s（%s）'
                  % ('PASS' if ok else 'FAIL', rid, row['state'], row['reason']))
        # 计数口径：PAIRED=1 MISSING=3 NOT_GREEN=1 IN_PROGRESS=1；MISSING>0 时闸口应置红。
        expect_counts = {'PAIRED': 1, 'MISSING': 3, 'NOT_GREEN': 1, 'IN_PROGRESS': 1}
        counts_ok = counts == expect_counts
        if counts_ok:
            passed += 1
        print('SELFTEST %s: 计数 %s（期望 %s）'
              % ('PASS' if counts_ok else 'FAIL', counts, expect_counts))
        for row in rows:
            print('  · ' + format_row(row))

        shutil.rmtree(tmp, ignore_errors=True)
        verdict = 'PASS' if passed == len(expect) + 1 else 'FAIL'
        print('%s %d/%d check-package-trigger selftest（%d/%d 通过；夹具目录 %s 已自删）'
              % (verdict, len(expect) + 1, len(expect) + 1, passed, len(expect) + 1, tmp))
        return 0 if passed == len(expect) + 1 else 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
