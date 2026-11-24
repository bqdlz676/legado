#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
scan-orphan-strings.py — 清缝合怪批2：多语言/字符串孤儿扫描（可复跑）

口径（对应 docs/口径冻结-2026-11-24-15点后.md 与任务书）：
  基准 = app/src/main/res/values/（默认语言）全部 <string>/<string-array>/<plurals> 键。
  四口径引用搜索：
    ① R.string.<name> / R.array.<name> / R.plurals.<name>（Java/Kotlin/XML 文本）
    ② @string/<name> / @array/<name> / @plurals/<name>（XML/Manifest/主题）
    ③ getString(R.string. 拼接（并入①统计）+ getIdentifier( 反射闸：
       若反射资源类型为 string/array/plurals → 全局存疑，本脚本不自动判删
    ④ tools: 与 ?attr 间接引用：记录出现次数，不逐键判定（不指向字符串键）
  两类处置：
    a) 默认 values/ 键四口径全 0 命中 → 可删，且同批删所有 values-* 同名键
    b) values-xx/ 存在而 values/ 不存在的键（孤儿翻译）→ 只删该变体条目
  豁免：translatable="false" 键一律保留；反射闸命中全局标记存疑。

用法：python3 data_scripts/scan-orphan-strings.py [--json]
不编译、不写资源文件；仅扫描并输出清单。
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
JAVA = os.path.join(ROOT, "app", "src", "main", "java")
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")

ENTRY_RE = re.compile(
    r'<(?P<tag>string|string-array|plurals)\s+name="(?P<name>[^"]+)"(?P<attr>[^>]*)>',
    re.S,
)
TRANS_FALSE_RE = re.compile(r'translatable="false"')


def list_value_dirs():
    dirs = []
    for name in sorted(os.listdir(RES)):
        if name.startswith("values"):
            dirs.append(name)
    return dirs


def parse_entries(path):
    """返回 {name: {"tag":.., "line":.., "translatable_false":bool, "file":path}}"""
    out = {}
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    text = "".join(lines)
    for m in ENTRY_RE.finditer(text):
        name = m.group("name")
        line = text[: m.start()].count("\n") + 1
        tf = bool(TRANS_FALSE_RE.search(m.group("attr")))
        out[name] = {
            "tag": m.group("tag"),
            "line": line,
            "translatable_false": tf,
            "file": os.path.relpath(path, ROOT),
        }
    return out


def collect_corpus():
    """返回 [(relpath, text, kind)]；kind: code / manifest / res-nonvalues / res-values
    覆盖 app/src 下全部 source set（main/test/androidTest/debug/各 runtime），
    防止只在测试或 debug 资源里被引用的键被误判孤儿。"""
    corpus = []
    src_root = os.path.join(ROOT, "app", "src")
    for ss in sorted(os.listdir(src_root)):
        ss_dir = os.path.join(src_root, ss)
        if not os.path.isdir(ss_dir):
            continue
        # code
        for base, _, files in os.walk(ss_dir):
            if os.sep + "java" + os.sep not in base + os.sep:
                continue
            for fn in files:
                if fn.endswith((".kt", ".java")):
                    p = os.path.join(base, fn)
                    with open(p, encoding="utf-8", errors="replace") as f:
                        corpus.append((os.path.relpath(p, ROOT), f.read(), "code:%s" % ss))
        # res
        res_dir = os.path.join(ss_dir, "res")
        if os.path.isdir(res_dir):
            for base, _, files in os.walk(res_dir):
                for fn in files:
                    if fn.endswith(".xml"):
                        p = os.path.join(base, fn)
                        rel = os.path.relpath(p, ROOT)
                        name = os.path.basename(base)
                        kind = "res-values:%s" % ss if name.startswith("values") else "res-nonvalues:%s" % ss
                        with open(p, encoding="utf-8", errors="replace") as f:
                            corpus.append((rel, f.read(), kind))
        # manifest
        mf = os.path.join(ss_dir, "AndroidManifest.xml")
        if os.path.exists(mf):
            with open(mf, encoding="utf-8", errors="replace") as f:
                corpus.append((os.path.relpath(mf, ROOT), f.read(), "manifest:%s" % ss))
    return corpus


def main():
    value_dirs = list_value_dirs()
    # entries[dir][name] = meta
    entries = {}
    for d in value_dirs:
        entries[d] = {}
        dpath = os.path.join(RES, d)
        for fn in sorted(os.listdir(dpath)):
            if fn.endswith(".xml"):
                entries[d].update(parse_entries(os.path.join(dpath, fn)))

    corpus = collect_corpus()

    # 反射闸：逐调用提取 (名字面量, 类型, 包)；允许参数跨行。
    # 包为 "android" 时是框架资源，不引用本 app 键，不触发存疑。
    refl_types = set()
    refl_calls = []
    refl_key_hits = {}  # name -> [file]
    call_re = re.compile(
        r'getIdentifier\(\s*'
        r'(?:"([^"]+)"|([A-Za-z_][\w.]*))\s*,\s*'
        r'"([^"]+)"\s*,\s*'
        r'(?:"([^"]+)"|([A-Za-z_][\w.]*))',
        re.S,
    )
    for rel, text, kind in corpus:
        for m in call_re.finditer(text):
            name_lit, name_var, rtype, pkg_lit, pkg_var = m.groups()
            refl_types.add(rtype)
            refl_calls.append({"file": rel, "name": name_lit or "<dynamic:%s>" % name_var,
                               "type": rtype, "pkg": pkg_lit or "<var>"})
            if rtype in {"string", "array", "plurals"} and pkg_lit != "android":
                if name_lit:
                    refl_key_hits.setdefault(name_lit, []).append(rel)
    refl_string_like = {c for c in refl_calls
                        if c["type"] in {"string", "array", "plurals"} and c["pkg"] != "android"}

    # 汇总全部键名
    all_names = sorted({n for d in entries.values() for n in d})

    def hits(name, tag):
        """四口径命中，返回 (ch1, ch2, ch3_reflection, locations[list of str])"""
        ch1 = ch2 = 0
        locs = []
        if tag == "string-array":
            r1 = re.compile(r"R\.array\.%s\b" % re.escape(name))
            r2 = re.compile(r"@array/%s\b" % re.escape(name))
        elif tag == "plurals":
            r1 = re.compile(r"R\.plurals\.%s\b" % re.escape(name))
            r2 = re.compile(r"@plurals/%s\b" % re.escape(name))
        else:
            r1 = re.compile(r"R\.string\.%s\b" % re.escape(name))
            r2 = re.compile(r"@string/%s\b" % re.escape(name))
        for rel, text, kind in corpus:
            a = len(r1.findall(text))
            b = len(r2.findall(text))
            if a:
                ch1 += a
                locs.append("%s:%s(R)" % (rel, kind))
            if b:
                ch2 += b
                locs.append("%s:%s(@)" % (rel, kind))
        return ch1, ch2, sorted(set(locs))

    baseline = set(entries["values"])
    report = {"reflection_types": sorted(refl_types),
              "reflection_calls": refl_calls,
              "reflection_app_string_calls": [c for c in refl_string_like],
              "dirs": {}, "class_a": [], "class_b": [], "kept": [], "doubt": []}

    for d in value_dirs:
        report["dirs"][d] = {"total": len(entries[d]), "delete": 0, "keep": 0, "doubt": 0}

    global_doubt = bool(refl_string_like)

    for name in all_names:
        # 取基准 tag；变体 tag 可能不同，以基准为准
        meta_base = entries["values"].get(name)
        if meta_base:
            tag = meta_base["tag"]
        else:
            tag = next(iter(
                [v["tag"] for d in entries for _, v in entries[d].items() if _ == name]
            ))
        # 基准存在与否
        in_base = name in entries["values"]
        tf = any(entries[d][name]["translatable_false"] for d in entries if name in entries[d])
        ch1, ch2, locs = hits(name, tag)

        if in_base:
            if tf:
                report["kept"].append({"name": name, "reason": "translatable=false", "dir": "values"})
                report["dirs"]["values"]["keep"] += 1
                continue
            if name in refl_key_hits:
                report["doubt"].append({"name": name, "reason": "反射按名取本 app 字符串: %s" % ",".join(refl_key_hits[name]), "locs": locs})
                report["dirs"]["values"]["doubt"] += 1
                continue
            if global_doubt:
                report["doubt"].append({"name": name, "reason": "存在 app 侧动态反射 string/array/plurals，无法静态排除", "locs": locs})
                report["dirs"]["values"]["doubt"] += 1
                continue
            if ch1 == 0 and ch2 == 0:
                # class a：基准 + 全部变体同名键可删
                report["class_a"].append({"name": name, "tag": tag, "locs": locs,
                                          "in_dirs": [d for d in entries if name in entries[d]]})
                for d in entries:
                    if name in entries[d]:
                        report["dirs"][d]["delete"] += 1
            else:
                report["kept"].append({"name": name, "reason": "ref ch1=%d ch2=%d" % (ch1, ch2), "locs": locs[:3]})
                report["dirs"]["values"]["keep"] += 1
        else:
            # class b：仅变体存在的孤儿翻译
            if tf:
                report["kept"].append({"name": name, "reason": "translatable=false", "dir": [d for d in entries if name in entries[d]]})
                for d in entries:
                    if name in entries[d]:
                        report["dirs"][d]["keep"] += 1
                continue
            report["class_b"].append({"name": name, "tag": tag,
                                      "in_dirs": [d for d in entries if name in entries[d]]})
            for d in entries:
                if name in entries[d]:
                    report["dirs"][d]["delete"] += 1

    # 汇总 arrays / plurals 单列
    report["arrays_plurals"] = {
        "a": [x for x in report["class_a"] if x["tag"] != "string"],
        "b": [x for x in report["class_b"] if x["tag"] != "string"],
    }

    print("=== 反射资源类型 ===", sorted(refl_types))
    print("=== 反射命中 string/array/plurals（全局存疑闸）===", sorted(refl_string_like) or "无")
    print("\n=== 分语言统计 ===")
    for d, s in report["dirs"].items():
        print("%-18s 总 %4d  删 %4d  保留 %4d  存疑 %3d" %
              (d, s["total"], s["delete"], s["keep"], s["doubt"]))
    print("\n=== A 类（基准零引用，基准+变体同删）===", len(report["class_a"]))
    for x in report["class_a"]:
        print("  [A] %-40s %s 见于 %s" % (x["name"], x["tag"], ",".join(x["in_dirs"])))
    print("\n=== B 类（变体孤儿翻译）===", len(report["class_b"]))
    for x in report["class_b"]:
        print("  [B] %-40s %s 仅见于 %s" % (x["name"], x["tag"], ",".join(x["in_dirs"])))
    if report["doubt"]:
        print("\n=== 存疑 ===", len(report["doubt"]))
        for x in report["doubt"]:
            print("  [?]", x["name"], x["reason"])

    out = os.path.join(ROOT, "data_scripts", "scan-orphan-strings.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print("\nJSON 清单:", os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
