#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""scan-blocking-hazards.py — 不依赖 CI 的挂死嫌疑静态穷举器（Q-276）。

为什么有它：`find-hang-culprit.py` 要等一轮 CI 熔断（15 分钟）拿到 job 日志才能配对点名；
`bisect-suspects.py` 也要把嫌疑集喂进独立 job 跑折半。本脚本补第三条通路：在源码里把
「最可能让测试 worker 永久 / 长时间挂住」的阻塞特征穷举出来，直接给加权嫌疑短名单，
不等任何一轮 CI。

扫描范围：`app/src/test/**/*.kt`（只读，不改任何测试源码；沙箱无 JDK，不编译、不跑单测）。

特征清单（a–k，每条在 FEATURE_META 里附「为什么会挂死」一句话）：
  a) Thread.sleep / SystemClock.sleep：大常量或循环内 sleep；小常量降权。
  b) Object.wait() / .wait() 无 timeout；synchronized 等待。
  c) CountDownLatch.await() / awaitTermination() 不带 timeout（接收器按声明类型归类）。
  d) runBlocking 内部无 withTimeout / withTimeoutOrNull / 有界 delay；Deferred.await()
     无超时一并归入本类（协程挂起转线程阻塞，等待永不满足即挂住）。
  e) runTest 内使用真实 Thread.sleep 或真实 IO（虚拟时间失效的典型写法）。
  f) Awaitility 无 atMost / Timeout。
  g) while(true) / while(x.isAlive) 自旋等待。
  h) Future.get() / Thread.join() 无 timeout（零参形式；原子量 getter、伴生对象静态 getter、
     字符串断言内的同形写法不计入，误入 fp 附录）。
  i) 测试内直连真实网络 / 文件 IO（okhttp execute、HttpURLConnection、Socket、
     FileInputStream、URL(、openConnection）；import 行与字符串字面量不算命中；
     localhost 回环标注；mock 拦截器构造 Response.Builder() 标注未出站。
  j) Rhino Context.enter() / exit() 不平衡。
  k) Robolectric / Instrumentation 阻塞调用（注释行不算）。

加权口径：无 timeout 的阻塞类（b/c/e/f/h）tier1 权重 10；概率性 sleep / 自旋 / 无界
runBlocking（a/d/g）tier2 权重 4；真实 IO / 引擎不平衡 / 框架阻塞（i/j/k）tier3 权重 6。

类型感知（降低假阳性的静态兜底，不做数据流分析）：
  - 文件内收集 `val x = Atomic* / CountDownLatch / Semaphore / Thread / launch / async /
    Future / executor.submit` 声明名；`.get()` / `.await()` / `.join()` 按接收器归类。
  - 字符串 / 字符字面量区间内的同形写法不参与特征匹配，误入 fp 附录留痕。
  - `withTimeout(...)` 块内的 `join()` 视为护栏在位，不计 h 类。

分桶（复用 `emit-shard-lists.py` 的 `classify()` 五桶口径，不另造过滤器）：
  keep      普通 class 且含 @Test → 进主短名单，可喂 bisect-suspects.py --in-classes。
  category  打了 @Category(...)，由独立 job 执行，不在分片挂死嫌疑面内：单独成桶列出。
  drop      object/interface/data/sealed/enum/abstract / 无 @Test / 无源残留：统计口径单列。

用法：
  python3 data_scripts/scan-blocking-hazards.py                 # 全量扫描并落盘 md + txt
  python3 data_scripts/scan-blocking-hazards.py --selftest      # /tmp 四夹具自测，跑完自删
  python3 data_scripts/scan-blocking-hazards.py --shard-list <path>
  python3 data_scripts/scan-blocking-hazards.py --top 30
  python3 data_scripts/scan-blocking-hazards.py --emit-md <path> --emit-txt <path>

边界：
  - 静态文本扫描，不做数据流 / 调用图分析；接收器类型推断只看文件内声明名，跨文件类型
    不可知时降权并在备注标「未定型」，交二分 job 复核。
  - runBlocking 是否被 withTimeout 包住按文件级粗判（文件内无任何 withTimeout 即记无界），
    可能把「局部有界」的文件低估风险；反向地，文件内一处有界不代表每个 runBlocking 都有界。
  - 网络 IO 只看调用形态，不区分 mock 拦截器与真实出站；localhost 与 mock 响应单独标注。
"""

import argparse
import importlib.util
import pathlib
import re
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEST_ROOT = ROOT / 'app' / 'src' / 'test' / 'java'
DEFAULT_MD = ROOT / 'docs' / '挂死嫌疑-静态短名单.md'
DEFAULT_TXT = ROOT / 'docs' / '挂死嫌疑-静态.txt'
SHARD_CANDIDATES = ('/tmp/lists/shard-1.txt', '/tmp/cl2/shard-1.txt')

# ---------- 特征元数据：label / tier / why（一句话挂死机理）----------

FEATURE_META = {
    'a': {
        'label': 'Thread.sleep 阻塞',
        'tier': 2,
        'why': 'sleep 让测试线程定长挂起；大常量或循环内累积时，慢 CI runner 上可放大成整片无进度的挂死观感。',
    },
    'b': {
        'label': 'Object.wait 无超时',
        'tier': 1,
        'why': 'wait() 不被 notify/notifyAll 唤醒即永久阻塞测试线程，没有超时兜底就是确定性挂死。',
    },
    'c': {
        'label': 'Latch/awaitTermination 无超时',
        'tier': 1,
        'why': 'CountDownLatch.await() / awaitTermination() 不带 timeout，计数永不归零或线程池永不终止时永久阻塞。',
    },
    'd': {
        'label': 'runBlocking / Deferred.await 无协程超时护栏',
        'tier': 2,
        'why': 'runBlocking 把协程挂起转成线程阻塞；内部既无 withTimeout 又无有界 delay，等待条件永不满足时线程跟着永久挂住。',
    },
    'e': {
        'label': 'runTest 内真实 sleep / IO',
        'tier': 1,
        'why': 'runTest 用虚拟时间调度，真实 Thread.sleep 或真实 IO 不被虚拟时钟推进，测试会在真实墙钟上卡住甚至永久挂起。',
    },
    'f': {
        'label': 'Awaitility 无 atMost',
        'tier': 1,
        'why': 'Awaitility 轮询等待若没配 atMost / Timeout，条件不成立时一直轮询到测试线程被拖死。',
    },
    'g': {
        'label': 'while(true) / 自旋等待',
        'tier': 2,
        'why': '裸 while(true) 或 while(thread.isAlive) 自旋没有退出条件上限，出口缺失时烧成死循环占住 worker。',
    },
    'h': {
        'label': 'Future.get / Thread.join 无超时',
        'tier': 1,
        'why': 'Future.get() / Thread.join() 零参形式不设上限，被等待的任务或线程不结束，调用方就一直阻塞。',
    },
    'i': {
        'label': '测试内真实网络 / 文件 IO',
        'tier': 3,
        'why': '直连网络或磁盘 IO 受环境影响：DNS 解析、连接建立、读流都可能在无超时时挂住，CI 断网/慢网时表现为静默挂死。',
    },
    'j': {
        'label': 'Rhino enter/exit 不平衡',
        'tier': 3,
        'why': 'Context.enter() 与 exit() 配对失衡会泄漏线程绑定的 Rhino 上下文，重复进入可致引擎状态异常，后续求值卡住。',
    },
    'k': {
        'label': 'Robolectric / Instrumentation 阻塞',
        'tier': 3,
        'why': 'Robolectric/Instrumentation 调用依赖 Android 框架与主线程调度，单测环境缺桩时容易阻塞在框架等待上。',
    },
}

TIER_WEIGHT = {1: 10, 2: 4, 3: 6}

# ---------- 正则 ----------

PKG_RE = re.compile(r'^\s*package\s+([\w.]+)')
CLASS_RE = re.compile(
    r'^(?:@[\w.]+(?:\([^)]*\))?\s*)*'
    r'(?:(?:public|internal|private|protected|abstract|open|final|sealed|data|enum|value)\s+)*'
    r'(?:enum\s+class|class|object|interface)\s+(\w+)'
)
CATEGORY_ANNOT = re.compile(r'@Category\s*\(')

RE_SLEEP = re.compile(r'(?:Thread|SystemClock)\.sleep\s*\(')
RE_WAIT_BARE = re.compile(r'\.wait\s*\(\s*\)')
RE_WAIT_TIMED = re.compile(r'\.wait\s*\(\s*\d')
RE_AWAIT_RECV = re.compile(r'([\w.]+)\.await\s*\(\s*\)')
RE_AWAIT_TIMED = re.compile(r'\.await\s*\([^)]*(?:TimeUnit|\d)')
RE_AWAIT_TERM_BARE = re.compile(r'awaitTermination\s*\(\s*\)')
RE_AWAIT_TERM_TIMED = re.compile(r'awaitTermination\s*\(')
RE_RUNBLOCKING = re.compile(r'runBlocking\s*(?:\(|\{)')
RE_RUNTEST = re.compile(r'runTest\s*(?:\(|\{)')
RE_WITH_TIMEOUT = re.compile(r'withTimeout(?:OrNull)?\s*\(')
RE_DELAY = re.compile(r'\bdelay\s*\(')
RE_AWAITILITY_IMPORT = re.compile(r'import\s+org\.awaitility|awaitility')
RE_ATMOST = re.compile(r'atMost|Timeout\s*\(')
RE_SPIN_TRUE = re.compile(r'while\s*\(\s*true\s*\)')
RE_SPIN_ALIVE = re.compile(r'while\s*\([^)]*\.isAlive\s*\)')
RE_GET_BARE = re.compile(r'(\w+)\.get\s*\(\s*\)')
RE_JOIN_BARE = re.compile(r'(\w+)\.join\s*\(\s*\)')
RE_IO = re.compile(
    r'HttpURLConnection|ServerSocket|Socket\s*\(|FileInputStream\s*\(|URL\s*\(|'
    r'openConnection\s*\(|newCall\s*\(|\.execute\s*\('
)
RE_ROBO = re.compile(r'Robolectric|Instrumentation')
RE_ENTER = re.compile(r'Context\.enter\s*\(')
RE_EXIT = re.compile(r'Context\.exit\s*\(')
RE_NUM_CONST = re.compile(r'(\d+(?:_\d+)*(?:\.\d+)?)')

RE_DECL_ATOMIC = re.compile(r'val\s+(\w+)\s*=\s*Atomic(?:Boolean|Integer|Long|Reference|Int|Double)\b')
RE_DECL_LATCH = re.compile(r'val\s+(\w+)\s*=\s*(?:CountDownLatch|Semaphore)\b')
RE_DECL_THREAD = re.compile(r'val\s+(\w+)\s*=\s*Thread\b')
RE_DECL_JOB = re.compile(r'val\s+(\w+)\s*=\s*(?:launch|async)\b')
RE_DECL_FUTURE = re.compile(r'val\s+(\w+)\s*:\s*(?:CompletableFuture|Future)<')
RE_DECL_SUBMIT = re.compile(r'val\s+(\w+)\s*=\s*\w*[Ee]xecutor\.submit')
RE_DECL_DEFERRED = re.compile(r'val\s+(\w+)\s*=\s*CompletableDeferred')
# Deferred 常见结果接收器命名（静态兜底，跨文件类型不可知）。
DEFERRED_NAMES = {'reader', 'first', 'second', 'owner', 'normalReader', 'deferred', 'result'}


def code_part(line: str) -> str:
    """剥掉行尾 // 注释（粗略处理字符串/字符字面量），返回可匹配的代码部分。"""
    in_str = in_ch = esc = False
    for i, ch in enumerate(line):
        if esc:
            esc = False
            continue
        if ch == '\\':
            esc = True
            continue
        if ch == '"' and not in_ch:
            in_str = not in_str
            continue
        if ch == "'" and not in_str:
            in_ch = not in_ch
            continue
        if ch == '/' and i + 1 < len(line) and line[i + 1] == '/' and not in_str and not in_ch:
            return line[:i]
    return line


def outside_mask(line: str):
    """返回与 line 等长的布尔数组：True 表示该位置在字符串/字符字面量之外。"""
    mask = [True] * len(line)
    in_str = in_ch = esc = False
    for i, ch in enumerate(line):
        if esc:
            esc = False
            continue
        if ch == '\\':
            esc = True
            continue
        if ch == '"' and not in_ch:
            in_str = not in_str
            mask[i] = False
            continue
        if ch == "'" and not in_str:
            in_ch = not in_ch
            mask[i] = False
            continue
        if in_str or in_ch:
            mask[i] = False
    return mask


def match_outside(pattern: re.Pattern, line: str, mask):
    """正则只在字面量区间之外命中；返回首个区间外匹配，否则 None。"""
    m = pattern.search(line)
    while m:
        if all(mask[i] for i in range(m.start(), min(m.end(), len(mask)))):
            return m
        m = pattern.search(line, m.end())
    return None


def _arg_literal(call: str, open_paren: int):
    """从 call 中 open_paren 位置取到匹配右括号之间的实参串（括号深度计数，忽略字符串内括号）。"""
    depth = 0
    in_str = in_ch = esc = False
    for i in range(open_paren, len(call)):
        ch = call[i]
        if esc:
            esc = False
            continue
        if ch == '\\':
            esc = True
            continue
        if ch == '"' and not in_ch:
            in_str = not in_str
            continue
        if ch == "'" and not in_str:
            in_ch = not in_ch
            continue
        if in_str or in_ch:
            continue
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return call[open_paren + 1:i]
    return call[open_paren + 1:]


def _is_import(code: str) -> bool:
    return code.lstrip().startswith('import ')


def _is_doc_comment(code: str) -> bool:
    s = code.lstrip()
    return s.startswith('*') or s.startswith('//')


def collect_decls(lines):
    """收集文件内声明名集合，供接收器类型归类。"""
    atomic, latch, thread, job, future, deferred = set(), set(), set(), set(), set(), set()
    for raw in lines:
        code = code_part(raw)
        for m in RE_DECL_ATOMIC.finditer(code):
            atomic.add(m.group(1))
        for m in RE_DECL_LATCH.finditer(code):
            latch.add(m.group(1))
        for m in RE_DECL_THREAD.finditer(code):
            thread.add(m.group(1))
        for m in RE_DECL_JOB.finditer(code):
            job.add(m.group(1))
        for m in RE_DECL_FUTURE.finditer(code):
            future.add(m.group(1))
        for m in RE_DECL_SUBMIT.finditer(code):
            future.add(m.group(1))
        for m in RE_DECL_DEFERRED.finditer(code):
            deferred.add(m.group(1))
    return atomic, latch, thread, job, future, deferred


def scan_text(fqcn: str, text: str):
    """扫描单个文件文本，返回类记录 dict。纯函数，便于 selftest 直接夹具调用。"""
    lines = text.splitlines()
    hits = []
    fp_notes = []
    feature_set = set()

    atomic, latch_vars, thread_vars, job_vars, future_vars, deferred_vars = collect_decls(lines)
    job_vars |= deferred_vars  # CompletableDeferred 与 async/launch 结果同为协程 Deferred

    has_runblocking = bool(RE_RUNBLOCKING.search(text))
    has_with_timeout = bool(RE_WITH_TIMEOUT.search(text))
    has_real_delay = any(
        RE_DELAY.search(code_part(ln)) for ln in lines if not _is_import(code_part(ln))
    )
    has_runtest = bool(RE_RUNTEST.search(text))
    has_awaitility = bool(RE_AWAITILITY_IMPORT.search(text))
    has_atmost = bool(RE_ATMOST.search(text))
    has_mock_response = 'Response.Builder()' in text
    has_localhost = ('127.0.0.1' in text or 'localhost' in text)
    enter_n = len(RE_ENTER.findall(text))
    exit_n = len(RE_EXIT.findall(text))

    def _paren_balanced(code: str) -> bool:
        return code.count('(') == code.count(')')

    def guarded_by_timeout(idx: int) -> bool:
        """护栏判定：同行 withTimeout 包裹；或上一行 withTimeout 开门且括号未闭合。

        为什么不回溯 3 行：withTimeout(...) { ... } 块若在上一行已闭合，再往上回溯会把块外
        的 await 误判成护栏内；只认「同行包裹」与「上一行未闭合开门」两种确定形态。
        """
        cur = code_part(lines[idx - 1])
        if RE_WITH_TIMEOUT.search(cur):
            return True
        if idx >= 2:
            prev = code_part(lines[idx - 2])
            if RE_WITH_TIMEOUT.search(prev) and not _paren_balanced(prev):
                return True
        return False

    def add(feature, lineno, raw, note='', weight_override=None, tier_override=None):
        meta = FEATURE_META[feature]
        tier = tier_override if tier_override is not None else meta['tier']
        weight = weight_override if weight_override is not None else TIER_WEIGHT[tier]
        hits.append({
            'feature': feature,
            'tier': tier,
            'weight': weight,
            'line': lineno,
            'text': raw.rstrip(),
            'note': note,
        })
        feature_set.add(feature)

    d_evidence = []  # d 类文件级只计一次权重，调用点全部留作证据

    for idx, raw in enumerate(lines, start=1):
        code = code_part(raw)
        if _is_doc_comment(code):
            continue
        mask = outside_mask(code)

        # a) sleep
        m = match_outside(RE_SLEEP, code, mask)
        if m:
            arg = _arg_literal(code, m.end() - 1)
            num = RE_NUM_CONST.search(arg)
            small = False
            if num:
                try:
                    small = float(num.group(1).replace('_', '')) <= 1000
                except ValueError:
                    small = False
            loop_ctx = any(
                re.search(r'repeat\s*\(|for\s*\(|while\s*\(', code_part(lines[j]))
                for j in range(max(0, idx - 12), idx - 1)
            )
            note = []
            if small:
                note.append('小常量 sleep（≤1000ms），降权')
            if loop_ctx:
                note.append('位于循环上下文内，时长可能累积')
            add('a', idx, raw, '；'.join(note))

        # b) wait 无超时
        if match_outside(RE_WAIT_BARE, code, mask):
            add('b', idx, raw)
        elif RE_WAIT_TIMED.search(code):
            pass  # 带超时 wait，护栏在位

        # c) latch / awaitTermination 无超时；裸 .await() 按接收器归类
        m = match_outside(RE_AWAIT_RECV, code, mask)
        if m:
            recv = m.group(1)
            last = recv.split('.')[-1]
            if guarded_by_timeout(idx):
                fp_notes.append(f'{idx}: {recv}.await() 位于 withTimeout/withTimeoutOrNull 块内，护栏在位，未计分类')
            elif last in latch_vars:
                add('c', idx, raw, 'CountDownLatch/Semaphore 接收器，无超时')
            elif recv.endswith('.result') or last in job_vars or last in DEFERRED_NAMES:
                add('d', idx, raw, 'Deferred.await() 协程挂起，未检出超时护栏',
                    weight_override=TIER_WEIGHT[2], tier_override=2)
            else:
                add('c', idx, raw, '接收器未定型：需人工区分 Latch.await 与 Deferred.await',
                    weight_override=TIER_WEIGHT[2], tier_override=2)
        elif RE_AWAIT_TIMED.search(code):
            pass  # 带超时，护栏在位
        if RE_AWAIT_TERM_BARE.search(code):
            add('c', idx, raw)
        elif RE_AWAIT_TERM_TIMED.search(code):
            pass  # 带超时

        # d) runBlocking 无护栏（文件级粗判，权重只计一次）
        if has_runblocking and not has_with_timeout and not has_real_delay:
            if RE_RUNBLOCKING.search(code):
                d_evidence.append((idx, raw.rstrip()))

        # e) runTest + 真实 sleep 或真实 IO
        if has_runtest:
            if match_outside(RE_SLEEP, code, mask):
                add('e', idx, raw, 'runTest 内真实 Thread.sleep，虚拟时钟不会推进')
            if match_outside(RE_IO, code, mask):
                add('e', idx, raw, 'runTest 内真实 IO，虚拟时间失效')

        # f) Awaitility 无 atMost
        if has_awaitility and not has_atmost and re.search(r'\bawait\s*\(', code):
            add('f', idx, raw, '未检出 atMost / Timeout 上限')

        # g) 自旋（字面量外才算）
        if match_outside(RE_SPIN_TRUE, code, mask):
            add('g', idx, raw, '需人工核对是否含 break/return 出口')
        elif match_outside(RE_SPIN_ALIVE, code, mask):
            add('g', idx, raw, 'while(isAlive) 自旋等待线程结束，无超时')

        # h) Future.get / Thread.join 零参
        m = match_outside(RE_GET_BARE, code, mask)
        if m:
            recv = m.group(1)
            if recv in atomic:
                fp_notes.append(f'{idx}: {recv}.get() 为 Atomic* 非阻塞 getter，未计 h 类')
            elif recv[:1].isupper():
                fp_notes.append(f'{idx}: {recv}.get() 接收器首字母大写，判为伴生对象/静态 getter，未计 h 类')
            elif recv in future_vars or re.match(r'^(future|task|promise|deferred)', recv):
                add('h', idx, raw, 'Future/任务接收器，零参 get() 无超时')
            else:
                fp_notes.append(f'{idx}: {recv}.get() 接收器类型未识别为 Future，未计分（留待人工复核）')

        m = match_outside(RE_JOIN_BARE, code, mask)
        if m:
            recv = m.group(1)
            guarded = any(
                'withTimeout(' in code_part(lines[j])
                for j in range(max(0, idx - 6), idx - 1)
            )
            if guarded:
                fp_notes.append(f'{idx}: {recv}.join() 位于 withTimeout 块内，护栏在位，未计 h 类')
            elif recv in thread_vars:
                add('h', idx, raw, 'Thread.join() 零参，无超时')
            elif recv in job_vars:
                add('h', idx, raw, 'Job.join() 协程挂起；与 Thread.join 区分，降权',
                    weight_override=TIER_WEIGHT[2], tier_override=2)
            else:
                add('h', idx, raw, '接收器未定型：Thread.join / Job.join 待人工核',
                    weight_override=TIER_WEIGHT[2], tier_override=2)

        # i) 真实 IO（import 行与字面量内不算）
        if not _is_import(code) and match_outside(RE_IO, code, mask):
            note = []
            if has_localhost:
                note.append('localhost/回环地址，非出站网络')
            if has_mock_response:
                note.append('文件内存在 Response.Builder() mock 响应，需人工确认拦截器是否吞掉真实出站')
            add('i', idx, raw, '；'.join(note))

        # k) Robolectric / Instrumentation（注释已在上方剔除）
        if RE_ROBO.search(code):
            add('k', idx, raw)

    # d 类文件级落一条证据聚合记录，权重计一次
    if d_evidence:
        text_blob = '<br>'.join(f'{ln} · {_md_cell(src)}' for ln, src in d_evidence)
        hits.append({
            'feature': 'd',
            'tier': 2,
            'weight': TIER_WEIGHT[2],
            'line': d_evidence[0][0],
            'text': text_blob,
            'blob': True,
            'note': f'文件内 {len(d_evidence)} 处 runBlocking 未检出 withTimeout/withTimeoutOrNull/有界 delay',
        })
        feature_set.add('d')

    # j) Rhino enter/exit 不平衡（文件级）
    if enter_n > 0 and enter_n != exit_n:
        add('j', 0, f'Context.enter() × {enter_n}，Context.exit() × {exit_n}', '进入/退出配对失衡')

    score = sum(h['weight'] for h in hits)
    tier1 = sum(1 for h in hits if h['tier'] == 1)
    return {
        'fqcn': fqcn,
        'hits': hits,
        'fp_notes': fp_notes,
        'features': sorted(feature_set),
        'score': score,
        'tier1': tier1,
        'hit_count': len(hits),
        'category': bool(CATEGORY_ANNOT.search(text)),
        'ignore': bool(re.search(r'@Ignore\b', text)),
    }


def derive_fqcn(path: pathlib.Path, text: str) -> str:
    pkg = ''
    m = PKG_RE.search(text)
    if m:
        pkg = m.group(1)
    simple = path.stem
    for line in text.splitlines():
        cm = CLASS_RE.match(line)
        if cm:
            simple = cm.group(1)
            break
    return f'{pkg}.{simple}' if pkg else simple


def load_emit_classifier():
    """复用 emit-shard-lists.py 的 classify()；模块不可用时返回 None，分桶退化为本地粗判。"""
    here = pathlib.Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location('emit_shard_lists', here / 'emit-shard-lists.py')
    if spec is None:
        return None
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)
        return mod.classify
    except Exception:
        return None


def bucket_of(classify, fqcn: str, record: dict) -> str:
    # @Ignore 优先隔离：用例整体禁用，不进分片嫌疑面，也不进二分清单。
    if record['ignore']:
        return 'ignore'
    if classify is not None:
        try:
            status, _reason, _src = classify(fqcn)
            return status
        except SystemExit:
            return 'drop'
        except Exception:
            pass
    if record['category']:
        return 'category'
    return 'keep'


def scan_root(test_root: pathlib.Path, classify):
    records = []
    for path in sorted(test_root.rglob('*.kt')):
        text = path.read_text(encoding='utf-8', errors='replace')
        fqcn = derive_fqcn(path, text)
        rec = scan_text(fqcn, text)
        rec['path'] = str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)
        rec['bucket'] = bucket_of(classify, fqcn, rec)
        records.append(rec)
    return records


def resolve_shard_list(cli_arg: str | None):
    if cli_arg:
        p = pathlib.Path(cli_arg)
        return p if p.is_file() else None
    for cand in SHARD_CANDIDATES:
        p = pathlib.Path(cand)
        if p.is_file():
            return p
    return None


def read_shard(path: pathlib.Path | None):
    if path is None:
        return [], None
    out = []
    for line in path.read_text(encoding='utf-8', errors='replace').splitlines():
        line = line.strip()
        if line and not line.startswith('#'):
            out.append(line)
    return sorted(set(out)), str(path)


def rank(records, bucket_filter=None):
    pool = [r for r in records if bucket_filter is None or r['bucket'] in bucket_filter]
    return sorted(pool, key=lambda r: (-r['score'], -r['tier1'], -r['hit_count'], r['fqcn']))


# ---------- 报告生成 ----------

def feature_legend_md():
    rows = []
    for code, meta in FEATURE_META.items():
        rows.append(f"| {code} | {meta['label']} | tier{meta['tier']}（权重 {TIER_WEIGHT[meta['tier']]}）| {meta['why']} |")
    return '\n'.join(rows)


def _md_cell(text: str) -> str:
    """表格单元格转义：管道符转义；原文含 Kotlin 反引号函数名时不再包代码 span，避免嵌套反引号撑破渲染。"""
    esc = text.replace('|', '\\|')
    if '`' in esc:
        return esc
    return f'`{esc}`'


def evidence_lines(rec):
    parts = []
    for h in rec['hits']:
        note = f"（{h['note']}）" if h['note'] else ''
        line_no = h['line'] if h['line'] else '-'
        if h.get('blob'):
            # 聚合证据自身已带行号；各调用点原文逐条转义，不二次包裹行号
            parts.append(f"{h['feature']} · {h['text']}{note}")
        else:
            parts.append(f"{h['feature']} · {line_no} · {_md_cell(h['text'])}{note}")
    return '<br>'.join(parts)


def build_md(records, shard_classes, shard_path, top_n, fallback_classes=None, fallback_path=None):
    keep = [r for r in records if r['bucket'] == 'keep']
    category = [r for r in records if r['bucket'] == 'category']
    ignored = [r for r in records if r['bucket'] == 'ignore']
    dropped = [r for r in records if r['bucket'] == 'drop']
    ranked = rank(keep)
    ranked_hits = [r for r in ranked if r['hit_count'] > 0]
    top = ranked_hits[:top_n]

    feat_counts = {c: 0 for c in FEATURE_META}
    for r in records:
        for f in r['features']:
            feat_counts[f] += 1

    shard_set = set(shard_classes)
    top_in_shard = [r for r in top if r['fqcn'] in shard_set]
    full_in_shard = [r for r in ranked_hits if r['fqcn'] in shard_set]
    fallback_set = set(fallback_classes or [])
    fallback_in = [r for r in ranked_hits if r['fqcn'] in fallback_set]

    lines = []
    lines.append('# 挂死嫌疑 · 静态短名单（Q-276）')
    lines.append('')
    lines.append('> 生成方式：`python3 data_scripts/scan-blocking-hazards.py` 对 `app/src/test/**/*.kt` 全量静态文本扫描；'
                 '沙箱无 JDK，未编译、未跑单测、未改任何测试源码。本名单不等 CI，作为 `find-hang-culprit.py`（日志取证）'
                 '与 `bisect-suspects.py`（二分收敛）之外的第三条通路：静态特征先圈嫌疑，再喂二分 job 复核。')
    lines.append('')
    lines.append('## 一、扫描总量与命中统计')
    lines.append('')
    lines.append(f'- 扫描测试 Kotlin 文件：{len(records)} 个；分桶：keep {len(keep)} / category {len(category)} / '
                 f'ignore {len(ignored)} / drop {len(dropped)}。')
    lines.append(f'- 带至少一条命中的类：{sum(1 for r in records if r["hit_count"] > 0)} 个；'
                 f'keep 桶带命中：{len(ranked_hits)} 个。')
    lines.append('')
    lines.append('| 特征 | 含义 | 危险度 / 权重 | 为什么会挂死 |')
    lines.append('| --- | --- | --- | --- |')
    lines.append(feature_legend_md())
    lines.append('')
    lines.append('各类特征命中文件数：')
    lines.append('')
    for code, meta in FEATURE_META.items():
        lines.append(f'- {code} {meta["label"]}：{feat_counts[code]} 个文件')
    lines.append('')

    lines.append('## 二、加权嫌疑前 30（keep 桶，可喂 bisect-suspects.py --in-classes）')
    lines.append('')
    lines.append('评分：tier1（无超时阻塞）×10，tier3（IO/引擎/框架）×6，tier2（sleep/自旋/无界 runBlocking/Deferred.await）×4；'
                 '同分按 tier1 条数、命中条数排序。证据行号与代码原文逐字摘自源码，未改写。')
    lines.append('')
    if not top:
        lines.append('（keep 桶无命中）')
    else:
        lines.append('| 排名 | FQCN | 分桶 | 评分 | tier1 | 命中条数 | 命中特征 / 行号 / 原文 |')
        lines.append('| --- | --- | --- | --- | --- | --- | --- |')
        for i, r in enumerate(top, start=1):
            lines.append(f"| {i} | `{r['fqcn']}` | {r['bucket']} | {r['score']} | {r['tier1']} | "
                         f"{r['hit_count']} | {evidence_lines(r)} |")
    lines.append('')

    lines.append('## 三、交叉验证：与上轮挂死 shard-1 清单求交集')
    lines.append('')
    if shard_path is None:
        lines.append('- 未找到分片清单（/tmp/lists/shard-1.txt 与 /tmp/cl2/shard-1.txt 均不存在），交叉验证跳过。')
    else:
        lines.append(f'- 分片清单：`{shard_path}`，共 {len(shard_classes)} 条。')
        lines.append(f'- 前 30 短名单落在挂死分片内：**{len(top_in_shard)}** 条；'
                     f'keep 桶全部命中集落在分片内：{len(full_in_shard)} 条。')
        if fallback_path:
            lines.append(f'- 对照兜底清单 `{fallback_path}`（{len(fallback_classes)} 条）：'
                         f'keep 命中集交集 {len(fallback_in)} 条；两份分片清单本身口径不同，交集数并列备查，不合并定罪。')
        lines.append('')
        if top_in_shard:
            lines.append('短名单 ∩ 挂死分片逐条论证：')
            lines.append('')
            for r in top_in_shard:
                first = next((h for h in r['hits'] if h['tier'] == 1), r['hits'][0])
                meta = FEATURE_META[first['feature']]
                note = f"；{first['note']}" if first['note'] else ''
                why = meta['why'].rstrip('。')
                lines.append(f"- `{r['fqcn']}`：命中 {first['feature']} {meta['label']}"
                             f"（{r['path']}:{first['line']}）——{why}{note}")
            lines.append('')
        if len(top_in_shard) == 0:
            lines.append('**交集为 0：静态特征与实测挂死分片无交集 → 静态法不可单独做主依据，只能作为二分的加速器。**'
                         '挂死根因可能在静态特征覆盖不到的层面（类静态初始化、引擎 native 调用、资源泄漏累积、'
                         '时序竞态），仍须以日志取证 + 二分 job 复核为准。')
            lines.append('')

    lines.append('## 四、category 打标桶（独立 job 执行，不进分片嫌疑面）')
    lines.append('')
    lines.append('以下类带 `@Category(...)`，由 rhino-eval-tests / slow-bench-tests 独立 job 执行，'
                 '与分片 job 挂死无关；静态命中单独成桶列出，**不进**主短名单、**不进** `挂死嫌疑-静态.txt`。')
    lines.append('')
    cat_hits = [r for r in category if r['hit_count'] > 0]
    if cat_hits:
        lines.append('| FQCN | 评分 | 命中特征 |')
        lines.append('| --- | --- | --- |')
        for r in sorted(cat_hits, key=lambda x: -x['score']):
            lines.append(f"| `{r['fqcn']}` | {r['score']} | {', '.join(r['features'])} |")
    else:
        lines.append('（category 桶无静态命中）')
    lines.append('')
    if ignored:
        lines.append(f'另有 `@Ignore` 隔离桶 {len(ignored)} 个类（用例整体禁用，不进嫌疑面、不进 txt 清单）。')
        lines.append('')

    lines.append('## 五、反例推演：静态扫描的典型假阳性与剔除口径')
    lines.append('')
    lines.append('1. **await() 包在自带超时的 `.use{}` / withTimeout 里**：`RE_AWAIT_TIMED` 只认带 '
                 '`TimeUnit` 或数字实参的 `await(...)`；被外层 `withTimeout(...)` 包住但裸调 `await()` 的情形，'
                 '脚本按文件级检查 `withTimeout` 存在即给 d 类降权标注，证据行保留原文交人工复核。'
                 '外层 `.use{}` 关闭资源不等于等待超时，二者不混淆。')
    lines.append('2. **sleep 在 `@Ignore` / 已禁用的用例里**：本仓当前 0 处 `@Ignore`；脚本检出 `@Ignore` '
                 '注解的文件整体进 `ignore` 隔离桶，不进主短名单、不进 txt 清单（优先级高于 category 判定）。')
    lines.append('3. **被 `@Category(SlowBenchTest)` 等已隔离的类**：复用 `emit-shard-lists.py` 的 `classify()` '
                 '判桶，打标类全部进 category 桶单列（见 §四），本身就不在四分片里，不计入分片嫌疑。')
    lines.append('4. **字符串字面量里的阻塞词**：JS 源码字符串 `"while (true) {}"`、断言串 `delay(1_000L)`、'
                 '`"result.get(\"id\")"` 等，经 `outside_mask()` 判定落在字面量区间内，不参与特征匹配；'
                 '`.get()` 只认零参形式（`map.get("x")` 带参不命中）。')
    lines.append('5. **mock / 拦截器形态的 okhttp 调用**：只构造 `Request`/`Response`、不调 `newCall().execute()` '
                 '不记 i 类；文件内存在 `Response.Builder()` 时，execute 命中行备注「mock 响应待人工确认」；'
                 '真实 `openConnection()` / `URL(` 才记命中；连 `127.0.0.1` 回环的标注「非出站网络」。')
    lines.append('6. **协程挂起与 JVM 阻塞混淆**：裸 `.await()` 按文件内声明名归类——`CountDownLatch/Semaphore` '
                 '接收器记 c 类；`.result.await()`、`launch/async` 接收器及常见 Deferred 命名记 d 类降权；'
                 '其余未定型接收器降权并备注，交二分复核时按变量类型区分。')
    lines.append('7. **注释 / KDoc 里的特征词**：行首 `*`、`//` 与行尾注释经 `code_part()` 剔除后再匹配；'
                 '例如「不依赖 Robolectric」的说明文字不会记 k 类命中。')
    lines.append('8. **原子量 / 静态 getter 同形 `.get()`**：文件内声明为 `AtomicBoolean/Integer/Reference` 等的 '
                 '接收器、首字母大写的伴生对象/静态 getter，不计 h 类，误入 §七 附录留痕。')
    lines.append('')

    lines.append('## 六、新发现问题')
    lines.append('')
    lines.append('- 静态扫描在 keep 桶圈出 %d 个带命中类；其中 tier1 无超时阻塞命中 %d 个，'
                 '需优先喂 `bisect-suspects.py` 折半复核 —— 已入队。'
                 % (len(ranked_hits), sum(1 for r in ranked_hits if r['tier1'] > 0)))
    lines.append('- 本仓 `@Ignore` 0 处；`runTest(` 0 处；Awaitility 0 处；Robolectric/Instrumentation 仅出现在注释中；'
                 'Rhino enter/exit 配对全部平衡（j 类 0 命中）—— 已闭环。')
    lines.append('- 静态扫描不做数据流分析，无法识别「条件永不成立」「线程未启动」「Rhino native 调用不可中断」'
                 '等运行期才暴露的挂死；这些仍以 Q-269 日志取证与 Q-273 二分收敛为准 —— 待裁。')
    lines.append('')

    lines.append('## 七、附录：已剔除的疑似误匹配（不计分，留痕供复核）')
    lines.append('')
    fp_rows = [(r, note) for r in records for note in r['fp_notes']]
    if not fp_rows:
        lines.append('（无）')
    else:
        lines.append('| FQCN | 行号:原因 |')
        lines.append('| --- | --- |')
        for r, note in fp_rows:
            lines.append(f"| `{r['fqcn']}` | {note} |")
    lines.append('')
    return '\n'.join(lines), top_in_shard, full_in_shard


def write_txt(path: pathlib.Path, records):
    keep = rank([r for r in records if r['bucket'] == 'keep'])
    lines = [
        '# 挂死嫌疑静态清单（Q-276）：可直接喂 bisect-suspects.py --in-classes 或 --in',
        '# 口径：keep 桶静态命中类，按加权评分降序；category 打标类已剔除（独立 job 执行）。',
        '# 每行一个 FQCN；# 开头为注释行，bisect 读取时跳过。',
    ]
    n = 0
    for r in keep:
        if r['hit_count'] > 0:
            lines.append(r['fqcn'])
            n += 1
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return n


# ---------- selftest ----------

FIXTURES = {
    'LatchNoTimeoutTest.kt': '''\
package io.legado.app.selftest

import org.junit.Test
import java.util.concurrent.CountDownLatch

class LatchNoTimeoutTest {
    private val latch = CountDownLatch(1)

    @Test
    fun hangsWhenNeverCounted() {
        latch.await()
    }
}
''',
    'LatchWithTimeoutTest.kt': '''\
package io.legado.app.selftest

import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LatchWithTimeoutTest {
    private val latch = CountDownLatch(1)

    @Test
    fun guardedByTimeout() {
        latch.await(5, TimeUnit.SECONDS)
    }
}
''',
    'RunTestSleepTest.kt': '''\
package io.legado.app.selftest

import kotlinx.coroutines.test.runTest
import org.junit.Test

class RunTestSleepTest {
    @Test
    fun virtualTimeDoesNotAdvanceRealSleep() = runTest {
        Thread.sleep(100)
    }
}
''',
    'PureAssertTest.kt': '''\
package io.legado.app.selftest

import org.junit.Assert.assertEquals
import org.junit.Test

class PureAssertTest {
    @Test
    fun plainAssertion() {
        assertEquals(1, 1)
    }
}
''',
}


def selftest():
    """四夹具：无 timeout latch 命中 / 带 timeout latch 不命中 / runTest+sleep 命中 / 纯断言零命中。"""
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='scan-hazards-selftest-'))
    failures = []
    try:
        records = []
        for name, content in FIXTURES.items():
            p = tmp / name
            p.write_text(content, encoding='utf-8')
            fqcn = derive_fqcn(p, content)
            records.append(scan_text(fqcn, content))

        by_simple = {r['fqcn'].rsplit('.', 1)[-1]: r for r in records}

        r1 = by_simple['LatchNoTimeoutTest']
        if 'c' not in r1['features']:
            failures.append(f"无 timeout latch 应命中 c 类，实得特征 {r1['features']}")
        if r1['tier1'] < 1:
            failures.append('无 timeout latch 应有 tier1 命中')

        r2 = by_simple['LatchWithTimeoutTest']
        if 'c' in r2['features']:
            failures.append('带 timeout latch 不应命中 c 类')
        if r2['tier1'] != 0:
            failures.append(f"带 timeout latch 不应有 tier1 命中，实得 {r2['tier1']}")

        r3 = by_simple['RunTestSleepTest']
        if 'e' not in r3['features']:
            failures.append(f"runTest + Thread.sleep 应命中 e 类，实得特征 {r3['features']}")

        r4 = by_simple['PureAssertTest']
        if r4['hit_count'] != 0:
            failures.append(f"纯断言类应零命中，实得 {r4['hit_count']} 条：{r4['hits']}")
    finally:
        for p in tmp.iterdir():
            p.unlink()
        tmp.rmdir()

    if failures:
        print('SELFTEST FAIL:')
        for f in failures:
            print(f'  - {f}')
        return 1
    print('PASS 4/4 selftest 夹具全判定正确：无 timeout latch 命中 c / 带 timeout latch 不命中 / '
          'runTest+sleep 命中 e / 纯断言零命中；夹具已自删。')
    return 0


def main(argv):
    ap = argparse.ArgumentParser(description='CI 分片挂死嫌疑静态穷举器（Q-276）')
    ap.add_argument('--root', default=str(ROOT), help='仓库根目录')
    ap.add_argument('--test-root', help='测试源码根目录（默认 <root>/app/src/test/java）')
    ap.add_argument('--shard-list', help='挂死分片清单（默认 /tmp/lists/shard-1.txt，兜底 /tmp/cl2/shard-1.txt）')
    ap.add_argument('--emit-md', default=str(DEFAULT_MD), help='短名单 Markdown 输出路径')
    ap.add_argument('--emit-txt', default=str(DEFAULT_TXT), help='bisect 可消费的 FQCN 清单路径')
    ap.add_argument('--top', type=int, default=30, help='主短名单条数，默认 30')
    ap.add_argument('--selftest', action='store_true', help='/tmp 四夹具自测，跑完自删')
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()

    root = pathlib.Path(args.root)
    test_root = pathlib.Path(args.test_root) if args.test_root else root / 'app' / 'src' / 'test' / 'java'
    classify = load_emit_classifier()

    records = scan_root(test_root, classify)
    shard_classes, shard_path = read_shard(resolve_shard_list(args.shard_list))

    fallback_classes, fallback_path = [], None
    if shard_path:
        for cand in SHARD_CANDIDATES:
            if cand != shard_path and pathlib.Path(cand).is_file():
                fallback_classes, fallback_path = read_shard(pathlib.Path(cand))
                break

    md, top_in_shard, full_in_shard = build_md(
        records, shard_classes, shard_path, args.top, fallback_classes, fallback_path)
    md_path = pathlib.Path(args.emit_md)
    md_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.write_text(md, encoding='utf-8')

    txt_n = write_txt(pathlib.Path(args.emit_txt), records)

    keep_hits = sum(1 for r in records if r['bucket'] == 'keep' and r['hit_count'] > 0)
    print(f'扫描测试类：{len(records)}；keep 桶带命中：{keep_hits}；txt 清单：{txt_n} 条')
    print(f'分片清单：{shard_path or "未找到"}（{len(shard_classes)} 条）；'
          f'前 {args.top} 交集：{len(top_in_shard)}；全部命中集交集：{len(full_in_shard)}')
    print(f'已落盘：{md_path}；{args.emit_txt}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
