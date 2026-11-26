#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 60 主 AI 空等守卫（机制门禁，规格 docs/待做-主AI空等门禁.md）。

为什么需要它：调度层（主控）不派单、不提交、还在轨迹里挂起等待时，流水线整体停摆，
而用户在外面看不到任何动静——这是比单个子代理超时更上游的停摆。本项把"调度层空等"
落成四条可机械判定的判据，任一成立即判红。

四条判据（规格原文，一条不许缩）：
  ① PENDING 悬挂：任一 PENDING 行时间早于当前 30 分钟以上，台账无对应 RUNNING/DONE
     记录 → 红并点名队列 ID。
  ② 空转空槽：在飞数 < 5、队列无 PENDING、且距最近一次派单或提交超过 30 分钟 → 红。
  ③ 调度层休眠：本轮调度层轨迹出现单次挂起型等待（wait_agent 调用，或 Wait/sleep
     且 > 60 秒）→ 红并贴轨迹行号。「本轮」按行级锚定：以轨迹里最近一条 role=user
     消息为界，只扫它之后的 tool_calls；之前的挂起属上一轮留档跳过。轨迹一条 user
     行都没有时，整条从严按本轮判定（空集从严，不许放行）。
  ④ 空集不可判定：任一输入为空集（队列主表 0 行 / 台账 0 行 / 轨迹目录不存在 /
     HEAD 取不到）→ 红并写「输入为空不可判定」。

与既有门禁的边界（重叠处以谁为准，写死在下面注释里，不许互相重复判）：
  - ㊹ check-dispatch-compliance.py：审 RUNNING 与轨迹一致（僵尸 RUNNING、轨迹 10 分钟
    无更新、熔断超时）。本项不复核在飞代理轨迹新鲜度，那是 ㊹ 的轴；本项只看队列悬挂、
    空转节奏与调度层自身休眠。
  - 57 check-slot-full.py：管"有 PENDING 而 RUNNING 不满"（积压没补派）。本项判据②
    只在 PENDING=0 且槽未满时空转触发，与 57 的触发条件互斥；同一行不会两边重复计红。
  - ㉙ check-no-idle.py：管 HEAD 时间戳停滞（HEAD 超闲且队列未清）。口径冲突时：
    HEAD 时间戳停滞以 ㉙ 为准；队列悬挂 / 空转 / 轨迹休眠以本项为准。若两者结论打架，
    两门禁各自报红不互相覆盖；人工裁决时外部可观察的队列与轨迹事实优先于单一 HEAD
    时间戳（HEAD 新鲜可能只是被无关提交顶新，队列事实不会骗人）。
  - ㊳ check-agent-timeout.py：管单个在飞代理 15/60 分钟熔断。本项不算单个代理时长，
    也不做单飞熔断。
  - ㊾ check-vacuous-pass.py：静态扫描"检查器空集 return 0"的假绿结构。本项判据④是
    ㊾ 精神在运行态的同族落地：空输入不许判 PASS，必须显式写「输入为空不可判定」。
  - ㊿ check-no-wait-rule.py：扫规则文本里残留的 wait_agent 条款。本项扫的是真实轨迹
    里实际发生的挂起调用；文本条款归 ㊿，运行行为归本项。

适用条件：本项只管主控（调度层）。子代理（s_ 前缀）内部允许阻塞式调用（读大文件、
跑长脚本），其 Wait/sleep 不计入判据③；控制器前缀 o_/m_ 才在扫描范围内。

用法：
  python3 data_scripts/check-idle-empty.py            # 实跑判定
  python3 data_scripts/check-idle-empty.py --selftest # 双向自测（好样本 PASS / 坏样本判红）
退出码：0 = 四条判据全过；1 = 任一判据红（含空集不可判定）。
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---- 阈值（规格写死 30 分钟 / 60 秒；env 只给自测与未来调参留口，默认值不许改）----
STALE_MIN = int(os.environ.get('IDLE_EMPTY_STALE_MIN', '30'))   # 判据①：PENDING 悬挂线（分钟）
IDLE_MIN = int(os.environ.get('IDLE_EMPTY_IDLE_MIN', '30'))     # 判据②：空转无新动静线（分钟）
WAIT_SEC = int(os.environ.get('IDLE_EMPTY_WAIT_SEC', '60'))     # 判据③：单次挂起等待线（秒）
# 判据③「本轮」行级锚定：以最近一条 role=user 消息为界，只扫其后的 tool_calls。
# 为什么不用文件 mtime 时间窗：主控只要还在干活 mtime 就一直新鲜，历史挂起行会永久
# 落在窗内 → 门禁永久 FAIL 死锁；而用户/上级每发一条 user 消息就是新一轮，之前的
# 挂起属上一轮留档（与 ㊴ 认证书尾同族）。无 user 行的空/坏轨迹退回整条从严判定。
SLOT_CAP = 5                                                    # 并发硬顶 5（与 57/⑱ 同一事实源）

QUEUE_NAME = 'docs/派单队列.md'
LEDGER_NAME = 'docs/派单台账.md'

# 队列状态词：与 queue_stats.py / ㊹ 同一套词表，只取格子开头 token（备注里提到不算）。
STATUS_RE = re.compile(r'^\s*(PENDING|RUNNING|DONE|VOID|作废|待派|待办)', re.I)
ROW_RE = re.compile(r'^\s*\|\s*(Q-\d+)')
# 创建时间：主表第 3 列「2026-11-27 02:22」；不可解析时返回 None（从严按悬挂处理，见下）。
TIME_RE = re.compile(r'(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})')
QID_RE = re.compile(r'Q-\d+')
# 台账里算"已有对应记录"的收口/在飞词；只待派/待验/作废/待裁不算收口记录。
DONE_WORDS = ('已完成', '已收口', '已交', 'DONE', '✅', '已通过', '已闭环', '已处置')
# 控制器前缀：o_ = 调度层 organizer，m_ = 主代理；s_ = 子代理（§17 适用条件：内部允许阻塞）。
CONTROLLER_PREFIXES = ('o_', 'm_')
# 空集判红的固定文案（规格要求逐字写出，不许换成"跳过/豁免"）。
EMPTY_MSG = '输入为空不可判定'


def parse_time(text, now):
    """从一段文本里取首个完整时间戳；返回 epoch 秒或 None。

    为什么跨天兜底：队列里 RUNNING 行的起始时刻可能落在昨天（跨夜跑批），
    mktime 算出来在未来就回退一天；与 ㊳ 同一处理口径。
    """
    m = TIME_RE.search(text or '')
    if not m:
        return None
    y, mo, d, hh, mm = (int(x) for x in m.groups())
    lt = time.localtime(now)
    t = time.mktime((y, mo, d, hh, mm, 0, 0, 0, -1))
    if t > now:
        t -= 86400
    return t


def queue_rows(text):
    """解析主表数据行 → [{qid, status, created, line}]。

    状态定位与 ㊹ 同口径：先认"格子开头是状态词"，再退回子串；备注格里出现状态词不算。
    为什么不写死第 4 列：队列列数历史上漂过 4 列/6 列，写死列位会让整类行消失成盲区。
    """
    rows = []
    for line in text.splitlines():
        m = ROW_RE.match(line)
        if not m:
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        status_cell = next((c for c in cells if STATUS_RE.match(c.upper())), '')
        status = status_cell.split('（')[0].strip().upper()
        rows.append({
            'qid': m.group(1),
            'status': status,
            'created': parse_time(line, time.time()),
            'line': line,
        })
    return rows


def ledger_done_ids(text):
    """台账里已有 RUNNING/DONE（收口/在飞）记录的 Q 号集合。

    判据①只排除"台账已有对应在飞或收口记录"的悬挂单；待派/待验/作废/待裁行不算，
    那种单在台账里仍属悬空，队列悬挂照样计红。
    """
    ids = set()
    for line in text.splitlines():
        qm = QID_RE.search(line)
        if not qm:
            continue
        up = line.upper()
        if 'RUNNING' in up or any(w in line for w in DONE_WORDS):
            ids.add(qm.group(0))
    return ids


def head_commit_ct():
    """取 HEAD 提交 epoch 秒；取不到返回 None（判据④空集判红，不许当 0 或当前时间兜底）。"""
    try:
        r = subprocess.run(['git', 'log', '-1', '--format=%ct'], cwd=ROOT,
                           capture_output=True, text=True)
        out = [x for x in r.stdout.strip().splitlines() if x.strip().isdigit()]
        return int(out[0]) if out else None
    except Exception:
        return None


def resolve_agents_dir(agents_dir=None):
    """定位本轮会话的 agents 目录。

    解析顺序（与 watchdog.sh 同口径）：位置/参数 > IDLE_EMPTY_AGENTS_DIR 环境变量 >
    按 SID 推导路径 > SESSIONS_ROOT 下 mtime 最新会话兜底。SID 取仓根的上级目录名
    （chats/<sid> 与 sessions/<sid> 同名；脚本在 data_scripts/ 下，上两级即 chats 目录）。
    为什么不扫所有历史会话：历轮调度轨迹是已收口留档，重复计红等于让历史事故永久压门禁
    （边界同 ㉗/⑭/⑮/⑲ 历史留档不追改）。
    """
    if agents_dir:
        return agents_dir
    env_dir = os.environ.get('IDLE_EMPTY_AGENTS_DIR')
    if env_dir:
        return env_dir
    sessions = os.environ.get('SESSIONS_ROOT') or os.path.expanduser(
        '~/.doubao/agent_mode/workspace/.sessions')
    sid = os.environ.get('SID') or os.path.basename(os.path.dirname(ROOT))
    cand = os.path.join(sessions, sid, 'agents')
    if os.path.isdir(cand):
        return cand
    cands = glob.glob(os.path.join(sessions, '*', 'agents'))
    if not cands:
        return None
    return max(cands, key=lambda p: os.path.getmtime(p))


def active_controller(agents_dir):
    """取本轮调度层轨迹：agents_dir 下 o_/m_ 前缀目录里轨迹 mtime 最新的一个。

    为什么只取最新一个：同一会话目录保留历轮已收口/遣散的调度轨迹，那些历史等待调用
    是上一轮的留档事实，不是本轮空等；本轮调度层＝当前正在写轨迹的那个控制器。
    返回 (agent_id, trajectory_path, mtime)；无控制器轨迹返回 (None, None, None)。
    """
    best_id, best_path, best_mt = None, None, -1
    for d in sorted(glob.glob(os.path.join(agents_dir, '*'))):
        aid = os.path.basename(d)
        if not aid.startswith(CONTROLLER_PREFIXES):
            continue  # s_ 子代理：内部阻塞式调用允许，不在判据③范围（§17）
        traj = os.path.join(d, 'system', 'trajectory.jsonl')
        if os.path.isfile(traj):
            mt = os.path.getmtime(traj)
            if mt > best_mt:
                best_id, best_path, best_mt = aid, traj, mt
    return best_id, best_path, (best_mt if best_path else None)


def scan_controller_sleep(traj_path):
    """扫单条调度轨迹，返回 (hits, note)。

    hits = [(行号, 类型, 时长/参数, 说明)]；note 为本轮锚定口径留痕（进 notes 可审计）。
    行级锚定（判据③「本轮」口径）：以轨迹里最近一条 role=user 消息为界，只扫它之后的
    tool_calls；user 之前的挂起属上一轮留档，历史留档跳过。轨迹一条 user 行都没有时，
    退回整条轨迹从严按本轮判定（空集从严，不许放行）。

    判据③三类命中（只解析 JSON tool_calls 真实调用；Read 脚本文本里出现字样不算）：
      - wait_agent：任何一次调用都算挂起等待（规格未给时长豁免，主控轮询子代理也不许空等）；
      - Wait 工具：arguments.time / timeout / timeout_ms，秒 > WAIT_SEC 判红；
      - Bash sleep：命令行 `sleep <秒>`，秒 > WAIT_SEC 判红。
    """
    rows = []
    try:
        fh = open(traj_path, encoding='utf-8', errors='ignore')
    except OSError:
        return [], ''
    with fh:
        for i, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append((i, json.loads(line)))
            except json.JSONDecodeError:
                continue
    last_user = max((i for i, r in rows if r.get('role') == 'user'), default=None)
    if last_user is None:
        note = '轨迹无 user 行，整条从严按本轮判定（空集从严）'
        scope = rows
    else:
        note = '行级锚定：最后一条 user 行 @%d 之前的挂起属历史留档跳过，只扫其后 tool_calls' % last_user
        scope = [(i, r) for i, r in rows if i > last_user]

    hits = []
    for i, row in scope:
        for tc in row.get('tool_calls') or []:
            fn = (tc.get('function') or {}).get('name', '')
            args = tc['function'].get('arguments') or {}
            if fn == 'wait_agent':
                hits.append((i, 'wait_agent', '-', '主控主动挂起等待子代理'))
            elif fn == 'Wait':
                secs = args.get('time', args.get('timeout', None))
                ms = args.get('timeout_ms', None)
                if ms is not None:
                    secs = (ms / 1000.0) if not isinstance(secs, (int, float)) else secs
                if isinstance(secs, (int, float)) and secs > WAIT_SEC:
                    hits.append((i, 'Wait', '%.0fs' % secs, '单次挂起超过 %d 秒' % WAIT_SEC))
            elif fn == 'Bash':
                cmd = str(args.get('command', ''))
                m = re.search(r'sleep\s+([0-9]+(?:\.[0-9]+)?)', cmd)
                if m and float(m.group(1)) > WAIT_SEC:
                    hits.append((i, 'sleep', '%ss' % m.group(1),
                                 'Bash 挂起 sleep 超过 %d 秒' % WAIT_SEC))

    # 只认「本轮最后一次工具调用」上的挂起：若其后还有工具调用，说明当时已被真实工作接续，
    # 属历史留档。为什么加这条：行级锚定的「本轮」可能持续数小时（没有新 user 指令时），
    # 单次历史 sleep/wait 会永久判红并把 commit/push 永久卡死（2026-11-27 实测：一次 sleep 100
    # 之后整轮变红、且主控无处清除）。判据③的语义是「调度层此刻是否在空等」，
    # 不是「本轮历史上是否等过一次」；这一点与判据②（30 分钟内无新派单/提交）互补——
    # 长期空转由判据②兜，单次等待被接续后由本过滤放行。
    tc_rows = [i for i, r in scope if r.get('tool_calls')]
    if hits:
        last_tc = max(tc_rows) if tc_rows else None
        if last_tc is not None:
            stale = [h for h in hits if h[0] < last_tc]
            if stale:
                note += '；挂起 %d 次已被其后工具调用接续，按历史留档跳过（最后工具调用行 @%d）' % (len(stale), last_tc)
            hits = [h for h in hits if h[0] == last_tc]
    return hits, note


def run(queue_text, ledger_text, head_ct, agents_dir, now=None):
    """纯判定函数：输入文本/路径可注入，便于 selftest 用临时目录夹具双向验证。

    返回 (exit_code, 失败行列表, 留痕提示行列表)。exit 0 = PASS；1 = FAIL（含空集不可判定）。
    """
    now = now or time.time()
    fails, notes = [], []

    # ---- 判据④：输入为空集不许判 PASS（㊾ 同族；空输入不可判定，不是合规）----
    if queue_text is None or not queue_rows(queue_text):
        fails.append('判据④ 队列主表 0 行：%s（队列事实源缺失，无法在飞/悬挂/空转判定）' % EMPTY_MSG)
    if ledger_text is None or not QID_RE.search(ledger_text):
        fails.append('判据④ 台账 0 行：%s（台账缺失，悬挂单有无收口记录无从核对）' % EMPTY_MSG)
    if head_ct is None:
        fails.append('判据④ HEAD 取不到：%s（无法判断最近提交距今多久）' % EMPTY_MSG)
    if agents_dir is None or not os.path.isdir(agents_dir):
        fails.append('判据④ 轨迹目录不存在：%s（调度层轨迹证据缺失，休眠判据无输入）' % EMPTY_MSG)

    # 输入已缺关键项时，①②③ 失去判定基础，不再硬凑结论；空集红优先输出。
    if fails:
        return 1, fails, notes

    rows = queue_rows(queue_text)
    done_ids = ledger_done_ids(ledger_text)
    pending = [r for r in rows if r['status'] == 'PENDING']
    running = [r for r in rows if r['status'] == 'RUNNING']

    # ---- 判据①：PENDING 悬挂超 30 分钟且台账无对应在飞/收口记录 ----
    for r in pending:
        if r['created'] is None:
            # 时间不可解析：不能证明它在 30 分钟内，从严计红（边界：只对 PENDING 行生效；
            # 不可解析 ≠ 空集，故不套用判据④的「输入为空不可判定」文案）。
            fails.append('判据① %s 悬挂：创建时间不可解析，台账无对应 RUNNING/DONE 记录；'
                         '不可证明在 %d 分钟内，从严判红' % (r['qid'], STALE_MIN))
            continue
        age_min = (now - r['created']) / 60.0
        if age_min > STALE_MIN and r['qid'] not in done_ids:
            fails.append('判据① %s 悬挂 %.0f 分钟（>%d 分钟线），台账无对应 RUNNING/DONE 记录'
                         % (r['qid'], age_min, STALE_MIN))

    # ---- 判据②：槽未满、无 PENDING、距最近派单或提交 >30 分钟 → 空转 ----
    # 与 57 的边界：57 在 PENDING>0 时审槽满；本项只在 PENDING=0 时审空转，互不重叠。
    if not pending and len(running) < SLOT_CAP:
        dispatch_ct = max((r['created'] for r in rows if r['created'] is not None),
                          default=None)
        latest = max(x for x in (dispatch_ct, head_ct) if x is not None)
        idle_min = (now - latest) / 60.0
        if idle_min > IDLE_MIN:
            fails.append('判据② 空转空槽：在飞 %d/%d、PENDING 0，距最近派单或提交 %.0f 分钟'
                         '（>%d 分钟线），调度层无新动静' % (len(running), SLOT_CAP, idle_min, IDLE_MIN))

    # ---- 判据③：本轮调度层轨迹出现单次挂起型等待 ----
    aid, traj, _mt = active_controller(agents_dir)
    if traj is None:
        # 目录在但无控制器轨迹：证据集为空，按 ㊾ 精神判红，不静默 PASS。
        fails.append('判据③ 调度层控制器轨迹 0 条：%s（无轨迹可审，不能据此判定主控未休眠）' % EMPTY_MSG)
    else:
        hits, c3_note = scan_controller_sleep(traj)
        notes.append('判据③ %s：%s' % (aid, c3_note))
        for line_no, kind, dur, note in hits:
            fails.append('判据③ 主控休眠：%s 轨迹行 %d 出现 %s（%s）——%s'
                         % (aid, line_no, kind, dur, note))

    return (1 if fails else 0), fails, notes


def selftest() -> int:
    """双向自测：好样本必须 PASS，五个坏样本必须判红；另加留档选择/行级锚定两边界夹具。

    夹具一律造在 tempfile 临时目录，finally 自删，禁污染真实 docs/派单队列.md 与
    docs/派单台账.md（与 ㊾/㊹ 负样本只许进 /tmp 同一规矩）。
    """
    tmp = tempfile.mkdtemp(prefix='idle-empty-')
    ok = True
    try:
        qdir = os.path.join(tmp, 'docs')
        os.makedirs(qdir, exist_ok=True)
        adir = os.path.join(tmp, 'agents')
        os.makedirs(os.path.join(adir, 'o_active', 'system'), exist_ok=True)
        now = time.time()
        fresh = time.strftime('%Y-%m-%d %H:%M', time.localtime(now))
        stale = time.strftime('%Y-%m-%d %H:%M', time.localtime(now - 35 * 60))

        queue_head = '| 序号 | 派单标题 | 创建时间 | 状态 | 依赖 | 文件域/判据锚 |\n|---|---|---|---|---|---|\n'
        ledger_head = '| 缺陷号 | 标题 | 状态 | 代理 | 验收 | 提交 | 备注 |\n|---|---|---|---|---|---|---|\n'

        def write_queue(text):
            p = os.path.join(qdir, '派单队列.md')
            open(p, 'w', encoding='utf-8').write(text)
            return p

        def write_ledger(text):
            p = os.path.join(qdir, '派单台账.md')
            open(p, 'w', encoding='utf-8').write(text)
            return p

        def write_traj(agent, rows):
            d = os.path.join(adir, agent, 'system')
            os.makedirs(d, exist_ok=True)
            p = os.path.join(d, 'trajectory.jsonl')
            open(p, 'w', encoding='utf-8').write('\n'.join(json.dumps(r, ensure_ascii=False) for r in rows) + '\n')
            return p

        # 好样本：1 条 RUNNING 新鲜在飞，台账有对应收口记录；活跃控制器轨迹无挂起调用。
        good_q = queue_head + '| Q-300 | 好样本在飞单 | %s | RUNNING（s_test，起始 %s） | 无 | 占位 |\n' % (fresh, fresh)
        good_l = ledger_head + '| Q-300 | 好样本在飞单 | RUNNING | s_test | 待验 | — | L0 |\n'
        write_queue(good_q)
        write_ledger(good_l)
        write_traj('o_active', [{'role': 'assistant', 'tool_calls': [
            {'function': {'name': 'Read', 'arguments': {'file_path': 'x'}}}]}])
        rc, msgs, _ = run(open(write_queue(good_q), encoding='utf-8').read(),
                          open(write_ledger(good_l), encoding='utf-8').read(),
                          int(now), adir, now)
        if rc != 0:
            ok = False
            print('  SELFTEST FAIL 好样本应 PASS：', msgs)
        else:
            print('  好样本 PASS：RUNNING 新鲜 + 台账在飞记录 + 轨迹无挂起，四条判据全过')

        # 坏样本 1（判据①）：PENDING 挂 35 分钟，台账无对应记录 → 必须判红点名 Q-301。
        bad1_q = queue_head + '| Q-301 | 悬挂单 | %s | PENDING | 无 | 占位 |\n' % stale
        bad1_l = ledger_head + '| Q-299 | 无关收口单 | 已收口 | s_x | ✅ | abc123 | L0 |\n'
        rc1, m1, _ = run(bad1_q, bad1_l, int(now), adir, now)
        if rc1 != 1 or not any('Q-301' in x and '判据①' in x for x in m1):
            ok = False
            print('  SELFTEST FAIL 坏样本 1 应判红点名 Q-301：', rc1, m1)
        else:
            print('  坏样本 1 FAIL：%s' % m1[0])

        # 坏样本 2（判据②）：无 PENDING、在飞 0、最近派单/提交 40 分钟前 → 必须判红。
        old = time.strftime('%Y-%m-%d %H:%M', time.localtime(now - 40 * 60))
        bad2_q = queue_head + '| Q-302 | 早已收口单 | %s | DONE（commit abc） | 无 | 占位 |\n' % old
        bad2_l = ledger_head + '| Q-302 | 早已收口单 | 已收口 | s_x | ✅ | abc123 | L0 |\n'
        rc2, m2, _ = run(bad2_q, bad2_l, int(now - 40 * 60), adir, now)
        if rc2 != 1 or not any('判据②' in x for x in m2):
            ok = False
            print('  SELFTEST FAIL 坏样本 2 应判红空转：', rc2, m2)
        else:
            print('  坏样本 2 FAIL：%s' % m2[0])

        # 坏样本 3a（判据③ 本轮内）：最后一条 user 之后出现 wait_agent → 必须判红贴行号。
        write_traj('o_active', [
            {'role': 'user', 'content': '新一轮指令'},
            {'role': 'assistant', 'tool_calls': []},
            {'role': 'assistant', 'tool_calls': [
                {'function': {'name': 'wait_agent', 'arguments': {'wait_timeout': 60}}}]}])
        rc3, m3, _ = run(good_q, good_l, int(now), adir, now)
        if rc3 != 1 or not any('判据③' in x and 'wait_agent' in x for x in m3):
            ok = False
            print('  SELFTEST FAIL 坏样本 3a user 之后 wait_agent 应判红：', rc3, m3)
        else:
            print('  坏样本 3a FAIL：%s' % m3[0])

        # 坏样本 3b（判据③ 无 user 行从严）：轨迹一条 user 行都没有，wait_agent 整条按本轮 → 判红。
        write_traj('o_active', [{'role': 'assistant', 'tool_calls': [
            {'function': {'name': 'wait_agent', 'arguments': {}}}]}])
        rc3b, m3b, _ = run(good_q, good_l, int(now), adir, now)
        if rc3b != 1 or not any('判据③' in x for x in m3b):
            ok = False
            print('  SELFTEST FAIL 坏样本 3b 无 user 行应从严判红：', rc3b, m3b)
        else:
            print('  坏样本 3b FAIL：%s' % m3b[0])

        # 坏样本 4（判据④）：队列 0 行 → 必须判红并写「输入为空不可判定」。
        rc4, m4, _ = run('', ledger_head, int(now), adir, now)
        if rc4 != 1 or not any(EMPTY_MSG in x for x in m4):
            ok = False
            print('  SELFTEST FAIL 坏样本 4 空队列应判红：', rc4, m4)
        else:
            print('  坏样本 4 FAIL：%s' % m4[0])

        # 边界夹具 A：同目录历轮旧控制器轨迹里的 wait_agent 不算本轮（历史留档不追改）；
        # 活跃控制器按 mtime 取最新，旧目录不被选中。
        olddir = os.path.join(adir, 'o_legacy', 'system')
        os.makedirs(olddir, exist_ok=True)
        old_traj = os.path.join(olddir, 'trajectory.jsonl')
        open(old_traj, 'w', encoding='utf-8').write(
            json.dumps({'role': 'assistant', 'tool_calls': [
                {'function': {'name': 'wait_agent', 'arguments': {}}}]} , ensure_ascii=False) + '\n')
        write_traj('o_active', [{'role': 'assistant', 'tool_calls': []}])
        rc5, m5, _ = run(good_q, good_l, int(now), adir, now)
        if rc5 != 0:
            ok = False
            print('  SELFTEST FAIL 旧控制器留档不应计入本轮：', rc5, m5)
        else:
            print('  边界夹具 A PASS：历轮 o_legacy 的 wait_agent 留档不计入本轮空等')

        # 边界夹具 B（行级锚定反向）：wait_agent 在最后一条 user 之前 → 历史留档跳过，
        # PASS 且留痕「历史留档跳过」；user 之后等待必红已由坏样本 3a 证明。
        shutil.rmtree(os.path.join(adir, 'o_legacy'), ignore_errors=True)
        write_traj('o_active', [
            {'role': 'assistant', 'tool_calls': [
                {'function': {'name': 'wait_agent', 'arguments': {}}}]} ,
            {'role': 'user', 'content': '新一轮指令（其后无挂起）'}])
        rc6, m6, n6 = run(good_q, good_l, int(now), adir, now)
        if rc6 != 0 or any('判据③' in x for x in m6) or not any('历史留档跳过' in x for x in n6):
            ok = False
            print('  SELFTEST FAIL user 之前历史等待不应判红：', rc6, m6, n6)
        else:
            print('  边界夹具 B PASS：user 行之前的历史 wait_agent 不判红（留痕：%s）' % n6[0])

        # 边界夹具 C（接续判据）：挂起之后仍有真实工具调用 → 不判红，按历史留档跳过。
        # 防的是「一次历史等待把整轮永久判红」的死锁（判据② 负责兜长期空转）。
        write_traj('o_active', [
            {'role': 'assistant', 'tool_calls': [
                {'function': {'name': 'wait_agent', 'arguments': {}}}]} ,
            {'role': 'assistant', 'tool_calls': [
                {'function': {'name': 'Bash', 'arguments': {'command': 'echo work'}}}]}])
        rc7, m7, n7 = run(good_q, good_l, int(now), adir, now)
        if rc7 != 0 or any('判据③' in x for x in m7) or not any('接续' in x for x in n7):
            ok = False
            print('  SELFTEST FAIL 被接续的挂起不应判红：', rc7, m7, n7)
        else:
            print('  边界夹具 C PASS：挂起后被真实工具调用接续，按历史留档跳过（留痕：%s）' % n7[0])

        print('SELFTEST: ' + ('PASS（好样本 1 PASS；坏样本 5 例判红；留档选择/行级锚定/接续三夹具 OK）'
                              if ok else 'FAIL'))
        return 0 if ok else 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main() -> int:
    if '--selftest' in sys.argv[1:]:
        return selftest()

    queue_path = os.path.join(ROOT, QUEUE_NAME)
    ledger_path = os.path.join(ROOT, LEDGER_NAME)
    queue_text = open(queue_path, encoding='utf-8', errors='ignore').read() \
        if os.path.isfile(queue_path) else None
    ledger_text = open(ledger_path, encoding='utf-8', errors='ignore').read() \
        if os.path.isfile(ledger_path) else None
    head_ct = head_commit_ct()
    agents_dir = resolve_agents_dir()

    rc, msgs, notes = run(queue_text, ledger_text, head_ct, agents_dir)
    for n in notes:
        print('  · INFO %s' % n)
    if rc == 0:
        print('IDLE-EMPTY: PASS（四条判据全过：无悬挂 PENDING、无空转空槽、主控轨迹无挂起等待、输入非空）')
        return 0
    print('IDLE-EMPTY: FAIL（%d 项空等判红）' % len(msgs))
    for m in msgs:
        print('  - %s' % m)
    return 1


if __name__ == '__main__':
    sys.exit(main())
