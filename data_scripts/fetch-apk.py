#!/usr/bin/env python3
"""出包链脚本化（Q-279）：CI 绿闸 → 取 Package Debug APK artifact → 结构校验 → 签名口径报告。

背景（为什么要有这个脚本）：
  历史上出包靠调度器记忆——CI 绿了以后人工/凭记忆去 GitHub 取 artifact、解压、核对签名与资源、
  再交付。调度器换人或上下文一断，这条链就断：出现过「CI 绿但没有包」和「报了 APK 实际没校验
  签名」两类事故。本脚本把整条链固化成一条命令，任何人任何时刻都能重现。

铁律落地：
  1. 第一道闸：Verify run 未绿绝不取包。为什么：CI 即验收，未绿先取包等于把验收挪到事后补，
     链路上任何一环断了都会把不完善的包送到交付侧（铁律「不接受任何不完善 APK」）。
  2. --wait 默认关闭。为什么：主控/调度层禁止阻塞空等；--wait 只给人手动盯包时用。
  3. 沙箱无 JDK / 无 Android SDK / 无 apksigner：本脚本只做 python/shell 侧的取包、解压、
     结构校验与哈希，不编译、不调 apksigner。

退出码：
  0  包结构校验通过（按本脚本口径）
  1  CI 未绿（red/running/none）或结构校验 FAIL
  2  基础设施错误：凭据缺失 / 401 / 403 / run 不存在 / artifact 404 过期 / zip 损坏 /
     下载字节数与标称不一致（SIZE_MISMATCH，禁止解压半个 zip）
  3  超时熔断（打印当前停在哪一步，避免又一个静默挂死）

用法：
  python3 data_scripts/fetch-apk.py                 # 查当前 HEAD
  python3 data_scripts/fetch-apk.py --sha <head>    # 指定 sha（短号自动补全 40 位）
  python3 data_scripts/fetch-apk.py --run-id <id>   # 直接指定 Package run
  python3 data_scripts/fetch-apk.py --wait          # 轮询等 Verify 绿（默认不开，仅手动用）
  python3 data_scripts/fetch-apk.py --timeout 300   # 整体超时秒数，默认 300
  python3 data_scripts/fetch-apk.py --selftest      # /tmp 造假夹具跑判定（结构闸 + 签名三态 +
                                                    # skipped/neutral 闸），夹具自删

签名三态（Q-291 缺口 2）：v1 / v2/v3-only / none。
  v2/v3 签名写在 APK Signing Block 内、zip 目录不可见；本脚本只读探测块魔数与方案 ID，
  只判结构存在性，不解密、不验签、不声称"可信"；v2/v3-only 判 WARN 通过并明示"未验签"。
"""
import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

REPO = 'bqdlz676/legado'
VERIFY_NAME = 'Verify'          # verify.yml 的 workflow name 前缀；影子单测名不含此串
PACKAGE_NAME = 'Package Debug APK'
# 绿闸口径（Q-286 复核 G-1 修复）：只有 status=completed 且 conclusion=success 才算绿。
# 为什么：skipped/neutral 表示 Verify 根本没执行或未判定成功，不等于通过；旧口径把它们
# 计入 GREEN，被跳过的 Verify 也能放过取包闸，直接破「不接受任何不完善 APK」红线。
# 适用条件：GitHub check run / workflow run 的 conclusion 字段全部走这张表归类。
GREEN = ('success',)
# 非绿结论逐条给人话原因，输出时区分「没跑」「取消」「超时」「失败」，不许笼统归 red。
NON_GREEN_REASON = {
    'skipped': 'Verify 未执行（skipped：路径过滤或连带取消），禁止取包',
    'neutral': 'Verify 结论 neutral（未判定成功），禁止取包',
    'cancelled': 'Verify 被取消，禁止取包',
    'timed_out': 'Verify 超时熔断，禁止取包',
    'action_required': 'Verify 需人工处理，禁止取包',
    'stale': 'Verify 结果已过期，禁止取包',
    'failure': 'Verify 失败，禁止取包',
    'startup_failure': 'Verify 启动失败，禁止取包',
}
DEFAULT_TIMEOUT = 300
POLL_INTERVAL = 10
# 弱网续传上限（Q-291 缺口 1）：超过即明确失败退出，不拿半个 zip 去解压；
# 适用条件：artifact 在线但传输被截断（TLS EOF / 超时），404 过期不进此循环。
MAX_RESUME_ATTEMPTS = 5
# APK Signing Block 魔数与方案 ID（Q-291 缺口 2）；只读存在性判定，不解密不验签。
APK_SIG_MAGIC = b'APK Sig Block 42'
SIG_BLOCK_IDS = {0x7109871a: 'v2', 0xf05368c0: 'v3', 0x1b93ad61: 'v3.1'}

ROOT = pathlib.Path(__file__).resolve().parent.parent


# --------------------------------------------------------------------------- #
# 凭据与本地 git
# --------------------------------------------------------------------------- #

def load_token():
    """从 ~/.git-credentials 读 github token；绝不打印 token 本体。
    为什么沿用 watch-ci.py 的读法：仓内既有脚本同一凭据源，避免再引入第二套配置。"""
    cred = pathlib.Path.home() / '.git-credentials'
    if not cred.exists():
        return None
    for line in cred.read_text(errors='replace').splitlines():
        m = re.match(r'https://[^:@\s]+:([^@\s]+)@github\.com', line)
        if m:
            return m.group(1)
    m = re.search(r'ghp_[A-Za-z0-9]+', cred.read_text(errors='replace'))
    return m.group(0) if m else None


def git_head():
    r = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'],
                       capture_output=True, text=True)
    return r.stdout.strip()


def resolve_sha(prefix):
    """短号补全 40 位：GitHub head_sha 服务端过滤只认全号，短号直查会返回空列表（假阴性）。"""
    if not prefix:
        return git_head()
    if len(prefix) >= 40:
        return prefix
    r = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', '--verify', prefix + '^{commit}'],
                       capture_output=True, text=True)
    return r.stdout.strip() or prefix


# --------------------------------------------------------------------------- #
# HTTP（curl 子进程；沙箱不引入 requests 依赖）
# --------------------------------------------------------------------------- #

class InfraError(Exception):
    """基础设施错误：凭据/401/403/404/网络/zip 损坏。退出码 2。"""


class TimeoutExit(Exception):
    """超时熔断。退出码 3；携带当前步骤名。"""


def curl(url, tok, dest=None, timeout=30):
    """返回 (http_code:int|None, body:bytes)。dest 给定时把 body 落盘。"""
    cmd = ['curl', '-sS', '-L', '--max-time', str(max(5, int(timeout))),
           '-w', '\n%{http_code}']
    if tok:
        cmd += ['-H', 'Authorization: Bearer ' + tok]
    cmd += [url]
    r = subprocess.run(cmd, capture_output=True)
    out = r.stdout or b''
    head, _, code = out.rpartition(b'\n')
    body = head if _ else out
    code = code.decode(errors='replace').strip()
    if dest is not None:
        pathlib.Path(dest).parent.mkdir(parents=True, exist_ok=True)
        pathlib.Path(dest).write_bytes(body)
    if r.returncode != 0 and not body:
        raise InfraError('NETWORK: curl 失败（%s）：%s' % (r.returncode,
                                                       r.stderr.decode(errors='replace').strip()[:200]))
    try:
        return int(code), body
    except ValueError:
        return None, body


def api_get_json(url, tok, deadline):
    """GET JSON；把 401/403/404 翻成明确报错，不允许静默当成空列表。"""
    _check_deadline('API GET ' + url)
    remain = max(5, int(deadline - time.monotonic()))
    code, body = curl(url, tok, timeout=min(remain, 30))
    if code == 401:
        raise InfraError('AUTH=401 token 无效或过期（~/.git-credentials）')
    if code == 403:
        raise InfraError('AUTH=403 权限不足或触发 GitHub 限流（rate limit）')
    if code == 404:
        raise InfraError('NOT_FOUND=404 资源不存在（repo / run / artifact 路径核对）')
    if code is None or code >= 500:
        raise InfraError('UPSTREAM=%s GitHub 服务端错误' % (code or '?'))
    try:
        return json.loads(body.decode(errors='replace'))
    except json.JSONDecodeError as e:
        raise InfraError('BAD_JSON=%s 响应不是 JSON（HTTP %s）' % (e, code))


def _check_deadline(step, deadline=None):
    if deadline is not None and time.monotonic() > deadline:
        raise TimeoutExit(step)


# --------------------------------------------------------------------------- #
# GitHub run 查询
# --------------------------------------------------------------------------- #

def list_runs(tok, sha, deadline):
    url = ('https://api.github.com/repos/%s/actions/runs?head_sha=%s&per_page=100'
           % (REPO, sha))
    data = api_get_json(url, tok, deadline)
    out = []
    for w in data.get('workflow_runs', []):
        if w.get('head_sha') == sha:   # 服务端过滤外再兜一层，防短号/窗口漂移
            out.append({
                'name': w.get('name') or '', 'id': w.get('id'),
                'status': w.get('status'), 'conclusion': w.get('conclusion'),
                'url': w.get('html_url'),
            })
    return out


def pick_run(runs, name_substr):
    """按 workflow 名子串挑 run；同名单取 id 最大（最新）。"""
    hits = [r for r in runs if name_substr in r['name']]
    if not hits:
        return None
    return sorted(hits, key=lambda r: r['id'] or 0)[-1]


def verify_gate(runs):
    """第一道闸。返回 (state, detail)：state ∈ green / running / none / 具体非绿结论。
    判序：已完成的非 success 优先判非绿（不被"还有在跑"藏住）；无坏但有在跑 → running；
    全部 completed+success → green。非绿 state 取首个坏 run 的 conclusion，便于输出区分原因。"""
    ver = [r for r in runs if VERIFY_NAME in r['name'] and 'Shadow' not in r['name']]
    if not ver:
        return 'none', '该 sha 无 Verify run（工作流未触发或被取消）'
    done = [r for r in ver if r['status'] == 'completed']
    bad = [r for r in done if r['conclusion'] != 'success']
    for r in ver:
        print('  · %-26s %-10s %-12s %s' % (r['name'], r['status'],
                                            r['conclusion'] or '-', r['url']))
    if bad:
        parts = []
        for r in bad:
            reason = NON_GREEN_REASON.get(
                r['conclusion'], '结论 %s 非 success' % (r['conclusion'] or 'unknown'))
            parts.append('%s(%s：%s)' % (r['name'], r['conclusion'] or 'unknown', reason))
        lead = bad[0]['conclusion'] or 'unknown'
        return lead, 'Verify 非绿：' + '；'.join(parts)
    if len(done) < len(ver):
        return 'running', 'Verify 仍在跑（%d/%d 完成）' % (len(done), len(ver))
    return 'green', 'Verify 全绿（%d 个 run）' % len(ver)


# --------------------------------------------------------------------------- #
# 结构校验（与网络解耦，selftest 直接复用）
# --------------------------------------------------------------------------- #

def _sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b''):
            h.update(chunk)
    return h.hexdigest()


def probe_signing_block(apk_path):
    """只读探测 APK Signing Block 是否存在、含哪些方案 ID。
    返回 dict：block / v2 / v3 / zip64 / note。只判结构存在性，不解密、不验签、不声称可信。
    偏移读法（按 ZIP / APK 规范）：
      1. EOCD（签名 PK\\x05\\x06）在文件尾部；注释长度读 EOCD+20 的 uint16——有注释时
         EOCD 不在固定尾偏移，必须按注释长度回退定位，不能假设 EOCD 就是最后 22 字节。
      2. 中央目录偏移读 EOCD+16 的 uint32；为 0xFFFFFFFF 时属 zip64，真实偏移在
         zip64 extra record，本探测不解析，置 zip64 标记返回 unknown，不猜偏移。
      3. Sig Block 紧接中央目录之前：16B 魔数在 CD 偏移 -16，尾随块长度 uint64 在 -24。
    """
    res = {'block': False, 'v2': False, 'v3': False, 'zip64': False, 'note': ''}
    size = apk_path.stat().st_size
    with open(apk_path, 'rb') as f:
        back = min(size, 22 + 0xFFFF)  # EOCD 注释最长 65535 B，只在尾段回找
        f.seek(size - back)
        tail = f.read()
        eocd = tail.rfind(b'PK\x05\x06')
        if eocd < 0:
            res['note'] = '未找到 EOCD（PK0506）'
            return res
        abs_eocd = size - back + eocd
        if abs_eocd + 22 > size:
            res['note'] = 'EOCD 越界'
            return res
        comment_len = int.from_bytes(tail[eocd + 20:eocd + 22], 'little')
        if abs_eocd + 22 + comment_len != size:
            res['note'] = 'EOCD 注释长度与文件尾不一致'
            return res
        cd_off = int.from_bytes(tail[eocd + 16:eocd + 20], 'little')
        if cd_off == 0xFFFFFFFF:
            res['zip64'] = True
            res['note'] = 'zip64：中央目录偏移需读 zip64 extra record，本只读探测未解析 v2/v3'
            return res
        if cd_off < 24 or cd_off > size:
            res['note'] = '中央目录偏移越界（%d）' % cd_off
            return res
        f.seek(cd_off - 16)
        magic = f.read(16)
        if magic != APK_SIG_MAGIC:
            res['note'] = '中央目录前无 APK Sig Block 魔数'
            return res
        res['block'] = True
        f.seek(cd_off - 24)
        blk_size = int.from_bytes(f.read(8), 'little')
        # 块首 uint64 长度字段位置：尾随字段值 = 首字段之后到魔数末尾的字节数，
        # 故块首 = CD 偏移 - 8（首字段自身）- blk_size。
        lead_off = cd_off - 8 - blk_size
        if lead_off < 0:
            res['note'] = 'Sig Block 长度字段越界'
            return res
        pos = lead_off + 8
        end = cd_off - 24
        # pair 布局（apksig 规范，全小端）：uint64 pair_len（不含本字段）+ uint32 ID +
        # (pair_len-4) 字节 value；stride = 8 + pair_len。长度前缀是 uint64，勿按 uint32 读。
        while pos + 12 <= end:
            f.seek(pos)
            head = f.read(12)
            pair_len = int.from_bytes(head[0:8], 'little')
            scheme_id = int.from_bytes(head[8:12], 'little')
            if pair_len < 4 or pos + 8 + pair_len > end:
                break  # 条目长度异常：停止扫描，已读到的 ID 保留
            tag = SIG_BLOCK_IDS.get(scheme_id)
            if tag == 'v2':
                res['v2'] = True
            elif tag in ('v3', 'v3.1'):
                res['v3'] = True
            pos += 8 + pair_len
        if not (res['v2'] or res['v3']):
            res['note'] = 'Sig Block 魔数存在，但未识别到 v2/v3/v3.1 方案 ID'
    return res


def find_apks(extract_dir):
    return sorted(p for p in extract_dir.rglob('*.apk') if p.is_file())


def validate_apk(apk_path):
    """校验单个 apk。返回 dict：overall / reasons / checks / signature。
    reasons 用稳定大写串，便于 selftest 断言。"""
    res = {'overall': True, 'reasons': [], 'checks': [], 'signature': 'unknown',
           'size': 0, 'sha256': None}

    def fail(reason, label):
        res['overall'] = False
        res['reasons'].append(reason)
        res['checks'].append('FAIL: %s' % label)

    size = apk_path.stat().st_size
    res['size'] = size
    res['checks'].append('INFO: %s size=%d bytes' % (apk_path.name, size))
    if size == 0:
        fail('ZERO_BYTE', 'apk 0 字节：%s' % apk_path.name)
        return res
    res['sha256'] = _sha256_file(apk_path)
    res['checks'].append('INFO: sha256=%s' % res['sha256'])

    try:
        zf = zipfile.ZipFile(apk_path)
    except (zipfile.BadZipFile, OSError) as e:
        fail('CORRUPT_APK', 'apk 不是合法 zip：%s' % e)
        return res
    bad = zf.testzip()
    if bad is not None:
        fail('CORRUPT_APK', 'apk zip 条目损坏：%s' % bad)
        return res
    names = zf.namelist()

    # a) Manifest：存在优先用 aapt/aapt2 解析；工具缺失时降级为 zip 目录结构检查，
    #    必须打印 MISSING: aapt，不许静默 PASS。
    manifest = 'AndroidManifest.xml' in names
    aapt = shutil.which('aapt') or shutil.which('aapt2')
    if not manifest:
        fail('NO_MANIFEST', '缺 AndroidManifest.xml')
    elif aapt:
        parse = subprocess.run([aapt, 'dump', 'badging', str(apk_path)],
                               capture_output=True, text=True)
        if parse.returncode == 0:
            res['checks'].append('PASS: AndroidManifest.xml 可被 %s 解析' % pathlib.Path(aapt).name)
        else:
            fail('MANIFEST_UNPARSEABLE', 'aapt 解析 Manifest 失败：%s'
                 % parse.stderr.strip()[:120])
    else:
        res['checks'].append('WARN: MISSING: aapt —— AndroidManifest.xml 仅做 zip 目录结构检查，'
                             '未解析二进制 Manifest（沙箱无 Android SDK）')

    # b) resources.arsc 与 dex
    if 'resources.arsc' in names:
        res['checks'].append('PASS: resources.arsc 存在')
    else:
        fail('NO_RESOURCES_ARSC', '缺 resources.arsc')
    dex = [n for n in names if re.match(r'classes\d*\.dex$', n.split('/')[-1])]
    if dex:
        res['checks'].append('PASS: classes*.dex 存在（%d 个：%s）' % (len(dex), ', '.join(dex)))
    else:
        fail('NO_DEX', '缺 classes*.dex（至少 1 个）')

    # c) 签名三态：v1（META-INF 三件）/ v2/v3-only（仅 Sig Block 结构存在）/ none。
    #    为什么不再按 META-INF 缺失直接判 UNSIGNED：v2/v3 签名写在 APK Signing Block 内，
    #    zip 目录不可见；纯 v2/v3 包会被旧口径永久误判红。本探测只判存在性，不验签、不称可信。
    cert = [n for n in names if re.match(r'META-INF/[^/]+\.(RSA|DSA|EC)$', n)]
    sf = [n for n in names if re.match(r'META-INF/[^/]+\.SF$', n)]
    mf = [n for n in names if re.match(r'META-INF/[^/]+\.MF$', n)]
    meta_inf = any(n.startswith('META-INF/') for n in names)
    probe = probe_signing_block(apk_path)
    if cert and sf and mf:
        res['signature'] = 'v1'
        res['checks'].append('PASS: 已签名（v1）：证书 %d / SF %d / MF %d'
                             % (len(cert), len(sf), len(mf)))
        if probe['block']:
            res['checks'].append('NOTE: 另检出 APK Signing Block（v2=%s v3=%s），本环境未验签'
                                 % (probe['v2'], probe['v3']))
    elif probe['block'] and (probe['v2'] or probe['v3']):
        res['signature'] = 'v2/v3-only'
        res['checks'].append('WARN: 未检出 v1 三件；检出 v2/v3 签名块结构（v2=%s v3=%s）。'
                             '未验签，仅结构存在，不等同签名可信'
                             % (probe['v2'], probe['v3']))
    else:
        res['signature'] = 'none'
        where = '仅有 META-INF 目录、无签名三件' if meta_inf else '无 META-INF 签名件'
        fail('UNSIGNED', '未签名（v1 与 v2/v3 结构均缺失）：%s；证书 %d / SF %d / MF %d'
             % (where, len(cert), len(sf), len(mf)))
        if probe['note']:
            res['checks'].append('NOTE: %s' % probe['note'])

    res['checks'].append('NOTE: v2/v3 仅做块结构存在性探测；无 apksigner 环境不验签，'
                         '本脚本不声称签名可信，也不排除证书链问题。')
    return res


def validate_extracted(extract_dir):
    """解压目录级校验：apk 数量闸优先；再逐 apk 校验。返回汇总 dict。"""
    summary = {'overall': True, 'reasons': [], 'apks': [], 'multi_apk': []}
    apks = find_apks(extract_dir)
    if len(apks) == 0:
        summary['overall'] = False
        summary['reasons'].append('NO_APK')
        summary['apks'].append({'name': '(none)', 'overall': False,
                                'reasons': ['NO_APK'], 'checks': ['FAIL: 解压出 0 个 apk']})
        return summary
    if len(apks) > 1:
        summary['overall'] = False
        summary['reasons'].append('MULTI_APK')
        summary['multi_apk'] = [p.name for p in apks]
        summary['apks'].append({'name': '、'.join(p.name for p in apks), 'overall': False,
                                'reasons': ['MULTI_APK'],
                                'checks': ['FAIL: apk 多于 1 个（取到的可能是别的变体）：%s'
                                           % ', '.join(p.name for p in apks)]})
        return summary
    one = validate_apk(apks[0])
    summary['overall'] = one['overall']
    summary['reasons'] = one['reasons']
    summary['apks'].append({'name': apks[0].name, **one})
    return summary


def validate_zip(zip_path, workdir):
    """解压 artifact zip 到 workdir 并校验。损坏 zip 抛 InfraError。"""
    try:
        with zipfile.ZipFile(zip_path) as zf:
            bad = zf.testzip()
            if bad is not None:
                raise InfraError('CORRUPT_ZIP: artifact zip 条目损坏：%s' % bad)
            zf.extractall(workdir)
    except (zipfile.BadZipFile, OSError) as e:
        raise InfraError('CORRUPT_ZIP: artifact zip 无法解压：%s' % e)
    return validate_extracted(pathlib.Path(workdir))


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #

def _fetch_artifact_part(url, tok, part_path, resume_from, timeout):
    """curl 流式落盘到 part_path；resume_from>0 时带 Range: bytes=start- 只拉差额段。
    返回 HTTP code（200 整包 / 206 分段 / 404 / 其它）。
    为什么不走既有 curl() 助手：既有助手把整包读进内存，98MiB 弱网包内存压力大且无法追加；
    这里让 curl 直接写盘，续传只拉差额段，盘上残片留作下一轮 Range 起点。"""
    cmd = ['curl', '-sS', '-L', '--max-time', str(max(5, int(timeout))),
           '-w', '\n%{http_code}']
    if tok:
        cmd += ['-H', 'Authorization: Bearer ' + tok]
    if resume_from > 0:
        # Range 续传：服务端支持则回 206 分段；不支持会回 200 整包，由调用方改覆盖语义。
        cmd += ['-H', 'Range: bytes=%d-' % resume_from]
    cmd += ['-o', str(part_path), url]
    r = subprocess.run(cmd, capture_output=True)
    out = r.stdout or b''
    _, _, code = out.rpartition(b'\n')
    code = code.decode(errors='replace').strip()
    if r.returncode != 0 and not pathlib.Path(part_path).exists():
        raise InfraError('NETWORK: artifact 下载 curl 失败（%s）：%s'
                         % (r.returncode, r.stderr.decode(errors='replace').strip()[:200]))
    try:
        return int(code)
    except ValueError:
        return None


def download_artifact(tok, artifact, dest_zip, deadline):
    """下载 artifact zip：先读元数据标称字节数，流式下载 + Range 续传 + 字节硬闸。
    硬闸口径：实际字节与标称不一致时明确失败退出并打印"已收/期望"，禁止继续解压。"""
    aid = artifact['id']
    url = 'https://api.github.com/repos/%s/actions/artifacts/%s/zip' % (REPO, aid)
    # 先读 artifact 元数据拿标称 size_in_bytes：弱网截断后只有字节对账能识别"半个 zip"。
    meta = api_get_json('https://api.github.com/repos/%s/actions/artifacts/%s'
                        % (REPO, aid), tok, deadline)
    expected = meta.get('size_in_bytes') or artifact.get('size_in_bytes')
    print('STEP 2/3 下载 artifact：%s（id=%s, 标称 size=%s）'
          % (artifact.get('name'), aid, expected))
    _check_deadline('下载 artifact', deadline)

    dest = pathlib.Path(dest_zip)
    part = dest.with_suffix(dest.suffix + '.part')
    actual = 0
    range_supported = None  # None 未知 / True 收到 206 / False 收到 200（服务端不支持 Range）
    for attempt in range(1, MAX_RESUME_ATTEMPTS + 1):
        _check_deadline('下载 artifact（续传第 %d 次）' % attempt, deadline)
        resume_from = dest.stat().st_size if dest.exists() else 0
        remain = max(5, int(deadline - time.monotonic()))
        part.unlink(missing_ok=True)
        code = _fetch_artifact_part(url, tok, part, resume_from, min(remain, 120))
        if code == 404:
            # 资源不存在/过期：与"字节不足"区分开——404 不进续传循环，直接报过期，避免死重试。
            raise InfraError('ARTIFACT_EXPIRED=404 artifact 已过期或不存在（GitHub 默认保留 90 天）')
        if code == 401:
            raise InfraError('AUTH=401 token 无效或过期（~/.git-credentials）')
        if code == 403:
            raise InfraError('AUTH=403 权限不足或触发 GitHub 限流（rate limit）')
        if code is None or code >= 500:
            raise InfraError('ARTIFACT_DOWNLOAD HTTP=%s' % code)
        if code == 206 and resume_from > 0:
            # 206 分段命中：差额段追加到已有文件尾部（分块写盘，不整包进内存）。
            with open(dest, 'ab') as out, open(part, 'rb') as fh:
                for chunk in iter(lambda: fh.read(1 << 16), b''):
                    out.write(chunk)
            range_supported = True
        else:
            # 200：整包返回。首轮直接落盘；续传轮收到 200 说明服务端不支持 Range，
            # 用整包覆盖本地残片（只退化这一次，下一轮仍不足就按失败闸收口）。
            if resume_from > 0:
                print('  · 服务端未返回 206（HTTP 200），不支持 Range：整包重下覆盖残片')
            shutil.move(str(part), str(dest))
            range_supported = False
        part.unlink(missing_ok=True)
        actual = dest.stat().st_size if dest.exists() else 0

        if expected is None:
            # 元数据缺标称值（历史 artifact 字段缺失）：只兜 0 字节，字节对账交给 zip 结构校验。
            print('  · WARN: 元数据无 size_in_bytes，跳过字节数对账（仅查 0 字节）')
            break
        if actual == expected:
            print('  · 字节对账通过：已收 %d / 期望 %d' % (actual, expected))
            break
        print('  · 第 %d 次续传：已收 %d / 期望 %d（差额 %d B）'
              % (attempt, actual, expected, expected - actual))
        if actual == resume_from:
            # 本轮无任何进展：连接空转，继续重试只会死循环，直接收口到失败闸。
            print('  · 本轮下载无进展（已收=%d），停止续传' % actual)
            break
        if range_supported is False and resume_from > 0:
            # 不支持 Range 且整包重下后仍不足：再试也是整包重拉，不再循环。
            break

    if actual == 0:
        raise InfraError('CORRUPT_ZIP: 下载得到的 zip 为 0 字节')
    if expected is not None and actual != expected:
        # 硬闸：字节数不一致禁止继续解压——半个 zip 尾部缺中央目录，解压必崩或解出残包。
        raise InfraError('SIZE_MISMATCH: 下载字节不一致，已收=%d 期望=%d；禁止解压'
                         % (actual, expected))


def pick_debug_artifact(artifacts):
    """优先 debug 变体；列出全部供追溯。release 包混进来时不许静默拿走。"""
    for a in artifacts:
        print('  · artifact %s：%s（size=%s）' % (a['id'], a.get('name'),
                                                a.get('size_in_bytes')))
    dbg = [a for a in artifacts if 'debug' in (a.get('name') or '').lower()]
    if dbg:
        return sorted(dbg, key=lambda a: a['id'] or 0)[-1]
    return None


def run_fetch(args):
    deadline = time.monotonic() + args.timeout
    tok = load_token()
    if not tok:
        print('FAIL: 凭据缺失（~/.git-credentials 无 github token）')
        return 2

    sha = resolve_sha(args.sha)
    print('FETCH-APK repo=%s sha=%s' % (REPO, sha[:12]))

    # --run-id 直连 Package run：先取该 run 的 head_sha，第一道闸仍按 sha 查 Verify。
    if args.run_id:
        run = api_get_json('https://api.github.com/repos/%s/actions/runs/%s'
                           % (REPO, args.run_id), tok, deadline)
        sha = run.get('head_sha') or sha
        print('  · --run-id=%s → head_sha=%s' % (args.run_id, sha[:12]))

    # ---- 第一步：Verify 绿闸（未绿绝不取包）----
    print('STEP 1/3 查 Verify run 状态')
    runs = list_runs(tok, sha, deadline)
    state, detail = verify_gate(runs)
    if state == 'running' and args.wait:
        print('  · --wait 已开：轮询等 Verify 结束（每 %ds，超时 %ds）' % (POLL_INTERVAL, args.timeout))
        while True:
            _check_deadline('等待 Verify 绿', deadline)
            time.sleep(POLL_INTERVAL)
            runs = list_runs(tok, sha, deadline)
            state, detail = verify_gate(runs)
            if state in ('green', 'red', 'none'):
                break
    if state != 'green':
        # 非绿结论单独给原因行（skipped/neutral/cancelled/...），running/none 走 detail 串。
        reason = NON_GREEN_REASON.get(state)
        if reason:
            print('CI=%s（%s）；按铁律未绿不取包' % (state, reason))
        else:
            print('CI=%s —— %s；按铁律未绿不取包' % (state, detail))
        return 1
    print('CI=green —— %s' % detail)

    # ---- 第二步：Package Debug APK run + artifacts ----
    print('STEP 2/3 查 %s run' % PACKAGE_NAME)
    if args.run_id:
        pkg = {'id': int(args.run_id), 'name': PACKAGE_NAME, 'status': run.get('status'),
               'conclusion': run.get('conclusion'), 'url': run.get('html_url')}
    else:
        pkg = pick_run(runs, PACKAGE_NAME)
    if not pkg:
        print('FAIL: 未找到 %s run（sha=%s）' % (PACKAGE_NAME, sha[:12]))
        return 1
    print('  · %s id=%s status=%s conclusion=%s %s'
          % (pkg['name'], pkg['id'], pkg['status'], pkg['conclusion'] or '-', pkg['url']))
    if pkg['status'] != 'completed':
        print('CI=running —— Package run 未结束；不取包')
        return 1
    if pkg['conclusion'] != 'success':
        # Package 闸同 Verify 口径：只有 success 放行；skipped/neutral 等一律非绿。
        reason = NON_GREEN_REASON.get(pkg['conclusion'], '结论 %s 非 success' % pkg['conclusion'])
        print('CI=%s —— Package run %s；不取包' % (pkg['conclusion'] or 'unknown', reason))
        return 1

    arts = api_get_json('https://api.github.com/repos/%s/actions/runs/%s/artifacts'
                        % (REPO, pkg['id']), tok, deadline)
    items = arts.get('artifacts', [])
    if not items:
        print('FAIL: Package run 无 artifacts（构建未上传产物）')
        return 1
    art = pick_debug_artifact(items)
    if not art:
        print('FAIL: artifacts 中无 debug 变体；全部：%s'
              % ', '.join(a.get('name', '?') for a in items))
        return 1

    out_dir = ROOT / 'dist' / sha[:7]
    out_dir.mkdir(parents=True, exist_ok=True)
    # 括号必须包住文件名拼接：Path / str 之后再 + str 会抛 TypeError（/ 与 + 同级左结合）。
    dest_zip = out_dir / ((art.get('name') or 'artifact') + '.zip')
    download_artifact(tok, art, dest_zip, deadline)

    # ---- 第三步：解压 + 结构校验 ----
    print('STEP 3/3 解压并结构校验 → %s' % out_dir)
    extract_dir = out_dir / 'unzip'
    if extract_dir.exists():
        shutil.rmtree(extract_dir)
    summary = validate_zip(dest_zip, extract_dir)
    for apk in summary['apks']:
        print('  · apk: %s' % apk['name'])
        for line in apk['checks']:
            print('    ' + line)

    if summary['overall']:
        print('RESULT=PASS：包结构与签名三态口径校验通过（v2/v3-only 为 WARN 结构存在，未验签）')
        return 0
    print('RESULT=FAIL：%s' % '；'.join(summary['reasons']))
    return 1


# --------------------------------------------------------------------------- #
# selftest：/tmp 造假夹具，四种判定，夹具自删
# --------------------------------------------------------------------------- #

def _write_fake_apk(path, signed=True, zero_byte=False, include_arsc=True,
                     include_dex=True, manifest=True):
    if zero_byte:
        path.write_bytes(b'')
        return
    with zipfile.ZipFile(path, 'w') as zf:
        if manifest:
            zf.writestr('AndroidManifest.xml', b'<?xml fake manifest/>')
        if include_arsc:
            zf.writestr('resources.arsc', b'\x00fake arsc')
        if include_dex:
            zf.writestr('classes.dex', b'dex\nfake')
        if signed:
            zf.writestr('META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\r\n')
            zf.writestr('META-INF/CERT.SF', b'Signature-Version: 1.0\r\n')
            zf.writestr('META-INF/CERT.RSA', b'-----BEGIN CERTIFICATE----- fake')
        else:
            # 只有 META-INF 目录占位，无 RSA/SF/MF 三件
            zf.writestr('META-INF/', b'')


def _write_fake_signed_block_apk(path):
    """造一个无 META-INF 三件、但在中央目录前注入假 APK Signing Block（含 v2 方案 ID）的 apk。
    手法：先让 zipfile 写普通包，解析 EOCD 取 CD 偏移，把块插在 CD 前并回写 EOCD 的 CD 偏移。
    用途：selftest 验证纯 v2/v3-only 三态判定；块内 value 是假数据，不构成有效签名。"""
    with zipfile.ZipFile(path, 'w') as zf:
        zf.writestr('AndroidManifest.xml', b'<?xml fake manifest/>')
        zf.writestr('resources.arsc', b'\x00fake arsc')
        zf.writestr('classes.dex', b'dex\nfake')
    data = bytearray(path.read_bytes())
    eocd = data.rfind(b'PK\x05\x06')
    cd_off = int.from_bytes(data[eocd + 16:eocd + 20], 'little')
    value = b'fake-v2-block-value'
    # pair 前缀为 uint64 长度（ID+value），与 apksig 规范一致；ID 为 uint32 小端。
    pair = ((4 + len(value)).to_bytes(8, 'little')
            + (0x7109871a).to_bytes(4, 'little') + value)
    # 尾随 uint64 块长度必须与首字段重复同一值（APK Sig Block 规范）；
    # 块长度 = pair + 尾随 8B 长度字段 + 16B 魔数，不含首 uint64 长度字段。
    blk_size = len(pair) + 8 + 16
    inner = pair + blk_size.to_bytes(8, 'little') + APK_SIG_MAGIC
    block = blk_size.to_bytes(8, 'little') + inner
    new_cd_off = cd_off + len(block)
    data[eocd + 16:eocd + 20] = new_cd_off.to_bytes(4, 'little')
    path.write_bytes(bytes(data[:cd_off]) + block + bytes(data[cd_off:]))


def _build_outer_zip(tmp, tag, inner_apks):
    """inner_apks: list of (filename, build-kwargs)。返回 outer zip 路径。"""
    outer = pathlib.Path(tmp) / ('%s.zip' % tag)
    with zipfile.ZipFile(outer, 'w') as zf:
        for fname, kw in inner_apks:
            inner = pathlib.Path(tmp) / fname
            _write_fake_apk(inner, **kw)
            zf.write(inner, arcname='app/build/outputs/apk/debug/' + fname)
    return outer


def _fake_verify_run(conclusion):
    """造一条 Verify run 夹具；只填 verify_gate 读取的字段。"""
    return {'name': 'Verify (unit tests)', 'id': 1, 'status': 'completed',
            'conclusion': conclusion, 'url': 'https://example.test/runs/1'}


def selftest():
    tmp = tempfile.mkdtemp(prefix='fetch-apk-')
    try:
        # A：正常包，1 apk，v1 三件齐全 → 应 PASS
        a_zip = _build_outer_zip(tmp, 'good', [('app-debug.apk', dict(signed=True))])
        # B：两个 apk 变体混在一个 artifact → 应 MULTI_APK FAIL
        b_zip = _build_outer_zip(tmp, 'multi', [('app-debug.apk', dict(signed=True)),
                                                ('app-release.apk', dict(signed=True))])
        # C：0 字节 apk → 应 ZERO_BYTE FAIL
        c_zip = _build_outer_zip(tmp, 'zerobyte', [('app-debug.apk', dict(zero_byte=True))])
        # D：有 META-INF 占位、无签名三件、无 Sig Block → 应 UNSIGNED FAIL（none）
        d_zip = _build_outer_zip(tmp, 'unsigned', [('app-debug.apk', dict(signed=False))])
        # G：无 v1 三件，但中央目录前注入假 Sig Block（v2 ID）→ 应 WARN 通过，判 v2/v3-only
        g_inner = pathlib.Path(tmp) / 'app-debug.apk'
        _write_fake_signed_block_apk(g_inner)
        g_zip = pathlib.Path(tmp) / 'block.zip'
        with zipfile.ZipFile(g_zip, 'w') as zf:
            zf.write(g_inner, arcname='app/build/outputs/apk/debug/app-debug.apk')

        expectations = [
            ('A 正常单包+v1签名', a_zip, True, [], 'v1'),
            ('B 双 apk 变体', b_zip, False, ['MULTI_APK'], None),
            ('C 0 字节 apk', c_zip, False, ['ZERO_BYTE'], None),
            ('D META-INF 无签名件', d_zip, False, ['UNSIGNED'], 'none'),
            ('G 假 Sig Block 纯 v2/v3-only', g_zip, True, [], 'v2/v3-only'),
        ]
        passed = 0
        for label, zpath, expect_ok, expect_reasons, expect_sig in expectations:
            work = pathlib.Path(tmp) / (zpath.stem + '-x')
            work.mkdir()
            summary = None
            try:
                summary = validate_zip(zpath, work)
                reasons = summary['reasons']
                sig = summary['apks'][0].get('signature') if summary['apks'] else None
                ok = summary['overall'] == expect_ok and all(
                    r in reasons for r in expect_reasons)
                if expect_sig is not None:
                    ok = ok and sig == expect_sig
            except InfraError as e:
                ok = False
                reasons = ['INFRA:' + str(e)]
                sig = None
            status = 'PASS' if ok else 'FAIL'
            if ok:
                passed += 1
            print('SELFTEST %s: %s（判定 overall=%s signature=%s reasons=%s）'
                  % (status, label, summary['overall'] if summary else '?',
                     sig, ','.join(reasons) or '-'))

        # E：Verify completed/skipped（根本没跑）→ 必须非绿，且原因写明「未执行，禁止取包」。
        # 为什么单列夹具：旧口径 skipped 计入 GREEN，被跳过的 Verify 也能过取包闸（G-1 漏洞）。
        e_state, e_detail = verify_gate([_fake_verify_run('skipped')])
        e_ok = e_state != 'green' and 'skipped' in e_detail and '禁止取包' in e_detail
        print('SELFTEST %s: E Verify=skipped（判定 state=%s detail=%s）'
              % ('PASS' if e_ok else 'FAIL', e_state, e_detail))
        if e_ok:
            passed += 1

        # F：Verify completed/neutral（未判定成功）→ 必须非绿，原因写明 neutral。
        f_state, f_detail = verify_gate([_fake_verify_run('neutral')])
        f_ok = f_state != 'green' and 'neutral' in f_detail and '禁止取包' in f_detail
        print('SELFTEST %s: F Verify=neutral（判定 state=%s detail=%s）'
              % ('PASS' if f_ok else 'FAIL', f_state, f_detail))
        if f_ok:
            passed += 1

        shutil.rmtree(tmp, ignore_errors=True)
        verdict = 'PASS' if passed == 7 else 'FAIL'
        print('%s 7/7 fetch-apk selftest（%d/7 通过；夹具目录 %s 已自删）' % (verdict, passed, tmp))
        return 0 if passed == 7 else 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main(argv=None):
    ap = argparse.ArgumentParser(description='出包链脚本：CI 绿闸 → 取 debug APK artifact → 结构校验')
    ap.add_argument('--sha', default=None, help='head sha（默认 git HEAD；短号自动补全）')
    ap.add_argument('--run-id', default=None, help='直接指定 Package Debug APK 的 run id')
    ap.add_argument('--wait', action='store_true', help='轮询等 Verify 绿（默认不开，仅手动用）')
    ap.add_argument('--timeout', type=int, default=DEFAULT_TIMEOUT, help='整体超时秒数，默认 300')
    ap.add_argument('--selftest', action='store_true', help='/tmp 造假夹具跑结构/签名三态/绿闸判定后退出')
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()

    try:
        return run_fetch(args)
    except TimeoutExit as e:
        print('TIMEOUT: 整体 %ds 熔断，停在步骤「%s」' % (args.timeout, e))
        return 3
    except InfraError as e:
        print('FAIL: %s' % e)
        return 2


if __name__ == '__main__':
    sys.exit(main())
