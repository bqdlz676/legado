#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
流程级虚拟运行自检（F6 第三层）——回答两个"静态 grep 答不了"的问题：
① 有没有「写好了却没有任何入口能到达」的页面/组件（死页面，用户点不到）；
② 有没有「数据里会出现、枚举认得、但执行层没有任何分支处理」的动作（静默吞掉，用户看不到反馈）。
为什么需要: 前两层只查"在场与被调用"，查不出"该到的到不了、该处理的没处理"——这两类恰恰是用户实际感知最强的。
口径: 只报可举证的三类结论（零引用页面 / 无处理分支的 action / 无渲染分支的 Kind），不猜。
用法: python3 data_scripts/selfcheck-flow.py
"""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/main/java/io/legado/app')
ASSETS = os.path.join(ROOT, 'app/src/main/assets')

def kt_files(sub):
    out = []
    for d, _, fs in os.walk(os.path.join(SRC, sub)):
        for f in fs:
            if f.endswith('.kt'):
                out.append(os.path.join(d, f))
    return out

def read(p):
    try:
        return open(p, encoding='utf-8', errors='ignore').read()
    except Exception:
        return ''

def strip_comments(s):
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'//[^\n]*', '', s)
    return s

ALL = {}
for sub in ('ui', 'help', 'model', 'service', 'engine'):
    for p in kt_files(sub):
        ALL[p] = strip_comments(read(p))

fail = 0
print('════ 流程级虚拟运行自检 ════')

# ── ① 死页面：声明了但全仓无人引用（排除自身文件、排除 main 清单声明的 Activity 入口）
decls = []
for p, s in ALL.items():
    for m in re.finditer(r'(?:class|object)\s+(Ink\w+(?:Screen|Page|Sheet|Dialog|Fragment|Activity))\b', s):
        decls.append((m.group(1), p))
dead = []
for name, p in decls:
    refs = 0
    for q, s2 in ALL.items():
        if q == p:
            continue
        if re.search(r'\b' + name + r'\b', s2):
            refs += 1
    if refs == 0:
        # Activity 可能只由 Manifest 启动（隐式），单独标注而非判死
        tag = 'Activity(清单入口)' if name.endswith('Activity') else '零引用'
        dead.append((name, os.path.relpath(p, SRC), tag))
if dead:
    print(f'  ✗ 页面/组件无任何代码引用: {len(dead)}')
    for name, rel, tag in dead:
        print(f'      - {name}  {rel}  [{tag}]')
    fail += len(dead)
else:
    print('  ✓ 页面/组件全部有引用')

# ── ② action 无处理分支：JSON 出现的 action → Enums 认得 → 但执行层无该字符串分支
enum_path = os.path.join(SRC, 'help/sim/EventTrigger.kt')
enum_src = ALL.get(enum_path) or read(enum_path)
enum_tokens = set(re.findall(r'"([A-Za-z][A-Za-z0-9_]*)"', enum_src))
json_actions = {}
for d, _, fs in os.walk(os.path.join(ASSETS, 'map')):
    for f in fs:
        if not f.endswith('.json'):
            continue
        p = os.path.join(d, f)
        for a in re.findall(r'"action"\s*:\s*"([A-Za-z0-9_]+)"', read(p)):
            json_actions.setdefault(a, set()).add(os.path.relpath(p, ROOT))
handlers_src = ''
for sub in ('ui/map', 'model', 'engine'):
    for p in kt_files(sub) if os.path.isdir(os.path.join(SRC, sub)) else []:
        handlers_src += ALL.get(p, '')
unregistered, unhandled = [], []
for a, files in sorted(json_actions.items()):
    if a not in enum_tokens:
        unregistered.append((a, len(files), sorted(files)[0]))
    elif not re.search(r'\.' + a + r'\b|"' + a + r'"', handlers_src):
        unhandled.append((a, len(files)))
if unregistered:
    print(f'  ✗ JSON action 枚举未登记: {len(unregistered)} 种')
    for a, n, ex in unregistered:
        print(f'      - {a}  ({n} 文件，例 {ex})')
    fail += len(unregistered)
if unhandled:
    print(f'  ✗ 枚举有、执行层无分支: {len(unhandled)} 种')
    for a, n in unhandled:
        print(f'      - {a}  ({n} 文件)')
    fail += len(unhandled)
if not unregistered and not unhandled:
    print('  ✓ 全部 action 枚举 + 执行分支齐备')

# ── ③ HalfSheetKind 枚举 vs 渲染分支
hs = read(os.path.join(SRC, 'ui/components/HalfSheetModel.kt'))
kinds = re.findall(r'(?:enum class HalfSheetKind\s*\{)([^}]*)\}', hs, re.S)
if kinds:
    names = [x.strip() for x in kinds[0].replace('\n', ',').split(',') if x.strip()]
    render = ''
    for p in kt_files('ui'):
        render += ALL.get(p, '')
    missing = [k for k in names if not re.search(r'is HalfSheetKind\.' + k + r'\b|HalfSheetKind\.' + k + r'\s*->', render)]
    if missing:
        print(f'  ✗ HalfSheetKind 无渲染分支: {missing}')
        fail += len(missing)
    else:
        print(f'  ✓ HalfSheetKind {len(names)} 种全部有渲染分支')
else:
    print('  · HalfSheetKind 未找到（跳过）')

print()
print(f'结果: {"全过" if fail == 0 else f"{fail} 项失败"}')
sys.exit(0 if fail == 0 else 1)
