#!/usr/bin/env python3
"""测试/主源编译陷阱静态守卫（2026-11-24 CI 连红 9 次复盘产物）。

背景：本轮一整批错误在测试源码 / 主源里都可以**机械检出**，每类焚掉一次完整 CI 轮次。
本脚本只做纯文本静态检查，不启动编译器，沙箱本地即可跑；抓不到的仍由 CI 兜底。

规则分级（与事故一一对应，逐条照抄复盘结论）：
  ERROR（计入失败，退出码 1）
    R1 反引号函数名含非法字符：Kotlin 标识符（含反引号名）禁 `/ \\ : < > * ? | "`。
       事故：`` fun `B9-10 … 1/4/10 → 3/6/8`() `` → `Name contains illegal characters: /`。
    R2 静态调用实例方法：`Double.isNaN(x)` 这类把实例方法当静态调 →
       `Unresolved reference 'isNaN'`；正确写法 `x.isNaN()`。保守只报确定该报的四类：
       `Double.isNaN(` / `Float.isNaN(` / `Double.isInfinite(` / `Float.isInfinite(`。
       全限定名 `java.lang.Float.isNaN(` 是合法 Java 静态调用，用负向后顾排除。

  WARN（仅提示，不计入失败，退出码仍 0；宁可漏报也不许制造误报）
    R3 尾随 lambda 落到非函数类型参数：Kotlin 尾随 lambda 绑定到"最后一个函数类型形参"，
       当函数里还有排在函数类型形参之后的非函数形参时，尾随 lambda 会绑错位置 →
       `No value passed for parameter 'pixel'`。只在能从本文件解析出函数声明（≥1 个
       函数类型形参、其后还有别的形参）且调用点确为尾随 lambda（`) {` 收尾）时才提示。
    R4 可空 map 键：`mapOf(pointKey to 2)` 中 `pointKey: String?` →
       `Map<String?, Int>` 与 `Map<String, Int>` 不匹配；上一行 `assertNotNull(...)`
       不会让 Kotlin 智能转型。启发式：键标识符来自 `...firstOrNull()` 赋值，
       或同文件出现 `assertNotNull(<同一标识符>)`。
    R5 泛型无法推断：`springFor(SpringRole.X)` 脱离 Composable 上下文时
       `Cannot infer type for type parameter 'T'`，需显式 `springFor<Float>(...)`。
       仅在 `src/test` 下提示（主源 Composable 上下文可推断，不报）。

输出：每行 `文件:行:规则:原文`；分段 ERROR / WARN；末尾 `ERROR=n WARN=m`。
退出码：0 = 无 ERROR（WARN 不影响）；1 = 有 ERROR。

用法：python3 data_scripts/check-test-source.py [源码根，默认 app/src]
"""
import os
import re
import sys

# R1：Kotlin 标识符（含反引号名）禁止出现的字符（复盘事故原文列出）
ILLEGAL_BACKTICK_CHARS = set('/\\:<>*?|"')

# R2：确定该报的"静态调用实例方法"四类；负向后顾排除全限定 Java 静态调用（java.lang.Xxx.）
R2_PATTERNS = [
    re.compile(r'(?<![\w.])Double\.isNaN\('),
    re.compile(r'(?<![\w.])Float\.isNaN\('),
    re.compile(r'(?<![\w.])Double\.isInfinite\('),
    re.compile(r'(?<![\w.])Float\.isInfinite\('),
]

R1_FUN_BACKTICK = re.compile(r'fun\s+`([^`]*)`')
R4_FIRSTORNULL = re.compile(r'val\s+([A-Za-z_]\w*)\s*=[^\n]*firstOrNull\(')
R4_NOTNULL = re.compile(r'assertNotNull\s*\(\s*([A-Za-z_]\w*)')
R4_MAPOF_KEY = re.compile(r'mapOf\(\s*([A-Za-z_]\w*)\s+to\b')
R5_SPRINGFOR = re.compile(r'springFor\s*\(')
R5_SPRINGFOR_TYPED = re.compile(r'springFor\s*<')

# 函数声明：fun name( ... )  —— 形参块用括号配对提取
FUN_DECL = re.compile(r'\bfun\s+([A-Za-z_]\w*)\s*\(')


def strip_noise_lines(lines):
    """逐行去掉行注释，避免注释/文案里的样例代码造成假报；保留行号对齐。"""
    out = []
    in_block = False
    for line in lines:
        cleaned = []
        i, n = 0, len(line)
        while i < n:
            if in_block:
                end = line.find('*/', i)
                if end == -1:
                    i = n
                else:
                    in_block = False
                    i = end + 2
                continue
            if line[i:i + 2] == '/*':
                in_block = True
                i += 2
                continue
            if line[i:i + 2] == '//':
                break
            cleaned.append(line[i])
            i += 1
        out.append(''.join(cleaned))
    return out


def match_parens(src, open_idx):
    """从 `(` 位置配对到对应 `)`，返回右括号下标；不做字符串/括号语义精解，够用即可。"""
    depth = 0
    i = open_idx
    while i < len(src):
        c = src[i]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def parse_fun_params(src, decl_match):
    """解析函数形参块，返回 [(param_name, is_function_type)]。粗略按逗号切，够本脚本口径。"""
    open_idx = decl_match.end() - 1
    close_idx = match_parens(src, open_idx)
    if close_idx == -1:
        return []
    body = src[open_idx + 1:close_idx]
    params = []
    # 只按圆括号深度切逗号：`->` 箭头里的 `>`、泛型 `<...>` 都不能计入深度，
    # 否则函数类型 `(Int, Int) -> IntArray` 会被误判深度越界、整段形参粘连。
    depth = 0
    cur = ''
    for c in body:
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
        if c == ',' and depth == 0:
            params.append(cur)
            cur = ''
        else:
            cur += c
    if cur.strip():
        params.append(cur)
    result = []
    for p in params:
        p = p.strip()
        if not p:
            continue
        # 形参：name: Type，默认值忽略
        m = re.match(r'([A-Za-z_]\w*)\s*:\s*([^=]*)', p)
        if not m:
            continue
        name, typ = m.group(1), m.group(2).strip()
        result.append((name, '->' in typ))
    return result


def scan_file(path, src, is_test):
    """返回 (errors, warns)，元素均为 (行号, 规则, 原文)。"""
    errors, warns = [], []
    lines = src.splitlines()
    code_lines = strip_noise_lines(lines)

    # ---- R1：反引号名非法字符（ERROR）----
    for ln, line in enumerate(code_lines, 1):
        for m in R1_FUN_BACKTICK.finditer(line):
            name = m.group(1)
            bad = sorted(set(ch for ch in name if ch in ILLEGAL_BACKTICK_CHARS))
            if bad:
                errors.append((ln, 'R1', '反引号名含非法字符 %s：fun `%s`' % (''.join(bad), name)))

    # ---- R2：静态调用实例方法（ERROR）----
    for ln, line in enumerate(code_lines, 1):
        for pat in R2_PATTERNS:
            if pat.search(line):
                errors.append((ln, 'R2', '静态调用实例方法（应为 x.方法()）：%s' % line.strip()))

    # ---- R5：泛型无法推断（WARN，仅 test 目录）----
    if is_test:
        for ln, line in enumerate(code_lines, 1):
            # 跳过声明行（fun <T> springFor(...) 是定义不是调用，不存在推断问题）
            if re.search(r'\bfun\b.*springFor', line):
                continue
            if R5_SPRINGFOR.search(line) and not R5_SPRINGFOR_TYPED.search(line):
                warns.append((ln, 'R5', 'springFor 缺显式类型实参（脱离 Composable 上下文无法推断 T）：%s' % line.strip()))

    # ---- R4：可空 map 键（WARN）----
    nullable_ids = set(R4_FIRSTORNULL.findall(src))
    nullable_ids.update(R4_NOTNULL.findall(src))
    for ln, line in enumerate(code_lines, 1):
        for m in R4_MAPOF_KEY.finditer(line):
            ident = m.group(1)
            if ident in nullable_ids:
                warns.append((ln, 'R4', 'mapOf 键 `%s` 可空（firstOrNull/assertNotNull 不触发智能转型，需 !! 或显式类型参数）：%s' % (ident, line.strip())))

    # ---- R3：尾随 lambda 绑错形参（WARN，极保守，只报"同文件内自相矛盾"的调用点）----
    # 背景事故：encodePng 形参序为 width,height,colorType,channels,pixel(fn),filter,palette，
    # 调用写成 `encodePng(1, 2, colorType = 0, channels = 1) { … }`，尾随 lambda 没绑到
    # pixel 而是落到后面的非函数形参 → `No value passed for parameter 'pixel'`。
    # 判据（宁漏勿误）：Kotlin 常规 Compose 调用大量使用尾随 lambda 且编译正常，单独看一个
    # 调用点无法判错；只有当**同一文件里该函数已有具名写法 `pixel = {`**，而另一调用点是
    # 未具名尾随 lambda 时，两者不一致才提示。声明行本身不计入调用点。
    risky = {}
    for m in FUN_DECL.finditer(src):
        params = parse_fun_params(src, m)
        if not params:
            continue
        fn_idx = [i for i, (_, is_fn) in enumerate(params) if is_fn]
        if fn_idx and max(fn_idx) < len(params) - 1:
            risky[m.group(1)] = [params[i][0] for i in fn_idx]

    calls = {}  # fname -> [ (line, arg_text, trailing, named_fn) ]
    for fname, fn_names in risky.items():
        sites = []
        for m in re.finditer(r'\b%s\s*\(' % re.escape(fname), src):
            before = src[:m.start()].rstrip()
            if before.endswith('fun'):
                continue  # 函数声明，不是调用点
            open_idx = m.end() - 1
            close_idx = match_parens(src, open_idx)
            if close_idx == -1:
                continue
            arg_text = src[open_idx + 1:close_idx]
            tail = src[close_idx + 1:close_idx + 3].lstrip()
            trailing = tail.startswith('{')
            named = [n for n in fn_names if re.search(r'\b%s\s*=' % re.escape(n), arg_text)]
            ln = src[:m.start()].count('\n') + 1
            sites.append((ln, arg_text, trailing, named))
        calls[fname] = (fn_names, sites)

    for fname, (fn_names, sites) in calls.items():
        has_named = any(named for _, _, _, named in sites)
        if not has_named:
            continue  # 全文件都没用具名写法，无法判定矛盾，不报（避免 Compose 常规尾随 lambda 误报）
        for ln, arg_text, trailing, named in sites:
            if trailing and not named:
                warns.append((ln, 'R3', '%s(...) 尾随 lambda 未具名，而同文件另有 `%s =` 写法；尾随 lambda 可能绑错形参，建议改为具名传入' % (fname, fn_names[0])))

    return errors, warns


def main(root: str) -> int:
    errors, warns = [], []
    kt_files = []
    for base, _, files in os.walk(root):
        for fn in files:
            if fn.endswith('.kt'):
                kt_files.append(os.path.join(base, fn))
    kt_files.sort()

    for path in kt_files:
        with open(path, encoding='utf-8', errors='ignore') as f:
            src = f.read()
        is_test = os.sep + 'test' + os.sep in path or '/test/' in path
        errs, wrns = scan_file(path, src, is_test)
        for ln, rule, msg in errs:
            errors.append((path, ln, rule, msg))
        for ln, rule, msg in wrns:
            warns.append((path, ln, rule, msg))

    if errors:
        print('ERROR（计入失败）：')
        for path, ln, rule, msg in errors:
            print('  %s:%d:%s:%s' % (path, ln, rule, msg))
    else:
        print('ERROR：0')

    if warns:
        print('WARN（仅提示，不计入失败）：')
        for path, ln, rule, msg in warns:
            print('  %s:%d:%s:%s' % (path, ln, rule, msg))
    else:
        print('WARN：0')

    print('ERROR=%d WARN=%d' % (len(errors), len(warns)))
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'app/src'))
