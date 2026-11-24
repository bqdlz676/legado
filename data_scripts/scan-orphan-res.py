#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清缝合怪 · 批1 资源孤儿扫描脚本（可复跑，禁编译）。

口径（五口径 + 反射风险闸）：
  ① R.<type>.<name>            —— Kotlin/Java/ProGradle 代码符号引用
  ② @<type>/<name>             —— XML / Manifest 资源引用（含 selector/shape 内部互引）
  ③ tools: / ?attr 间接引用     —— 行内带 tools: 或 ?attr/ 且命中资源名
  ④ <Camel>Binding             —— ViewBinding 生成类名（仅 layout 类型）
  ⑤ 反射风险闸：getIdentifier / 字符串字面量 "<name>" 命中即判「不可删」

文件域白名单（仅枚举候选，不代表可删）：
  app/src/main/res/ 下 drawable*/ layout*/ menu/ anim/ animator/ color/ raw/
禁碰：values*/ mipmap*/ app/src/main/java/** docs/（本脚本只读不删）。

用法：
  python3 data_scripts/scan-orphan-res.py                # 扫描 + 打印摘要 + 写 JSON
  python3 data_scripts/scan-orphan-res.py --json out.json
退出码：0 = 扫描完成（不代表可删）；2 = 域内扫描异常。
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res")

# 白名单目录基名（连字符后缀变体一并纳入：drawable-xxhdpi / layout-land 等）
WHITELIST_BASE = ("drawable", "layout", "menu", "anim", "animator", "color", "raw")

EXCLUDE_DIRS = {"build", ".git", ".gradle", "__pycache__", "_cache", ".idea", "docs"}
TEXT_EXT = {".kt", ".java", ".xml", ".pro", ".gradle", ".kts", ".sh", ".py", ".properties", ".txt"}

# 代码/配置语料根（引用来源；docs 为历史留痕，不计活引用，参照批0验收口径）
# 纳入 app/src 下全部构建变体（main/test/androidTest/cronetRuntime/pdfRuntime/debug）源码与 Manifest。
CORPUS_ROOTS = []
_app_src = os.path.join(ROOT, "app", "src")
if os.path.isdir(_app_src):
    for variant in os.listdir(_app_src):
        vdir = os.path.join(_app_src, variant)
        if not os.path.isdir(vdir):
            continue
        for sub in ("java", "kotlin"):
            if os.path.isdir(os.path.join(vdir, sub)):
                CORPUS_ROOTS.append(os.path.join(vdir, sub))
        man = os.path.join(vdir, "AndroidManifest.xml")
        if os.path.isfile(man):
            CORPUS_ROOTS.append(man)
CORPUS_ROOTS += [
    os.path.join(ROOT, "modules"),
    os.path.join(ROOT, "third_party"),
    os.path.join(ROOT, "data_scripts"),
]
# ProGuard / Gradle 配置单文件
EXTRA_FILES = [
    os.path.join(ROOT, "app", "proguard-rules.pro"),
    os.path.join(ROOT, "app", "cronet-proguard-rules.pro"),
    os.path.join(ROOT, "modules", "book", "consumer-rules.pro"),
    os.path.join(ROOT, "modules", "rhino", "consumer-rules.pro"),
    os.path.join(ROOT, "build.gradle"),
]


# 本扫描器自身及其结果/姊妹脚本属工具产物，注释中的示例名不计活引用
SELF_SKIP = {"scan-orphan-res.py", "scan-orphan-strings.py", "scan-orphan-res.result.json"}


def walk_files(path, exts):
    """收集语料文件；排除构建/缓存目录与扫描器自身产物。"""
    out = []
    if os.path.isfile(path):
        if os.path.basename(path) in SELF_SKIP:
            return out
        if os.path.splitext(path)[1] in exts or path.endswith(".xml"):
            out.append(path)
        return out
    for dirpath, dirnames, filenames in os.walk(path):
        dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]
        for fn in filenames:
            if fn in SELF_SKIP:
                continue
            if os.path.splitext(fn)[1] in exts:
                out.append(os.path.join(dirpath, fn))
    return out


def load_corpus():
    """读入全部语料：返回 {relpath: text}。XML 语料单独留一份（口径②③）。"""
    corpus = {}
    for root in CORPUS_ROOTS:
        if not os.path.exists(root):
            continue
        for f in walk_files(root, TEXT_EXT):
            try:
                with open(f, "r", encoding="utf-8", errors="replace") as fh:
                    corpus[os.path.relpath(f, ROOT)] = fh.read()
            except OSError:
                pass
    for f in EXTRA_FILES:
        if os.path.isfile(f):
            try:
                with open(f, "r", encoding="utf-8", errors="replace") as fh:
                    corpus[os.path.relpath(f, ROOT)] = fh.read()
            except OSError:
                pass
    # res 内全部 XML（含 values/ xml/ 等非白名单目录）作为「入向引用」语料
    if os.path.isdir(RES_DIR):
        for dirpath, dirnames, filenames in os.walk(RES_DIR):
            dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]
            for fn in filenames:
                if fn.endswith(".xml"):
                    f = os.path.join(dirpath, fn)
                    try:
                        with open(f, "r", encoding="utf-8", errors="replace") as fh:
                            corpus[os.path.relpath(f, ROOT)] = fh.read()
                    except OSError:
                        pass
    return corpus


def folder_type(folder):
    base = folder.split("-")[0]
    return base if base in WHITELIST_BASE else None


def pascal(name):
    return "".join(p.capitalize() for p in name.split("_") if p)


def token_re(name):
    """词边界：前后不是字母/数字/下划线，防 ic_back 命中 ic_backup。"""
    return r"(?<![A-Za-z0-9_])" + re.escape(name) + r"(?![A-Za-z0-9_])"


def scan_item(typ, name, corpus):
    """返回五口径命中明细 + 反射闸结论。"""
    hits = {"R_ref": [], "at_ref": [], "tools_attr": [], "binding": [], "literal": []}
    bind_cls = pascal(name) + "Binding"
    bind_prefix = pascal(name)

    re_r = re.compile(r"R\." + re.escape(typ) + r"\." + token_re(name))
    re_at = re.compile(r"@" + re.escape(typ) + r"/" + token_re(name))
    re_tools = re.compile(r"(tools:|\?attr/).*" + token_re(name))
    re_bind = re.compile(token_re(bind_cls) + r"|" + token_re(bind_prefix))
    # ⑤ 字符串闸：引号串内出现资源名（裸名或路径串，如测试 projectFile(".../layout/xxx.xml")）
    re_lit = re.compile(r'["\'][^"\']*' + token_re(name) + r'[^"\']*["\']')

    for rel, text in corpus.items():
        for lineno, line in enumerate(text.splitlines(), start=1):
            if re_r.search(line):
                hits["R_ref"].append(f"{rel}:{lineno}")
            if re_at.search(line):
                hits["at_ref"].append(f"{rel}:{lineno}")
            if re_tools.search(line):
                hits["tools_attr"].append(f"{rel}:{lineno}")
            if typ == "layout" and re_bind.search(line):
                hits["binding"].append(f"{rel}:{lineno}")
            if re_lit.search(line):
                hits["literal"].append(f"{rel}:{lineno}")
    return hits


def main():
    json_out = None
    if "--json" in sys.argv:
        json_out = sys.argv[sys.argv.index("--json") + 1]

    corpus = load_corpus()

    # 仓库级反射调用清单（口径⑤人工复核用）
    get_identifier = []
    for rel, text in corpus.items():
        if os.path.basename(rel) in SELF_SKIP:
            continue
        for lineno, line in enumerate(text.splitlines(), start=1):
            if "getIdentifier(" in line:
                get_identifier.append(f"{rel}:{lineno}: {line.strip()}")

    items = []
    for folder in sorted(os.listdir(RES_DIR)):
        fdir = os.path.join(RES_DIR, folder)
        if not os.path.isdir(fdir):
            continue
        typ = folder_type(folder)
        if typ is None:
            continue  # values*/ mipmap*/ xml/ 等非白名单，不枚举
        for fn in sorted(os.listdir(fdir)):
            fpath = os.path.join(fdir, fn)
            if not os.path.isfile(fpath):
                continue
            name, ext = os.path.splitext(fn)
            size = os.path.getsize(fpath)
            hits = scan_item(typ, name, corpus)
            orphan = not any(hits[k] for k in ("R_ref", "at_ref", "tools_attr", "binding", "literal"))
            items.append({
                "type": typ,
                "name": name,
                "path": os.path.relpath(fpath, ROOT),
                "size": size,
                "orphan": orphan,
                "hits": hits,
            })

    orphans = [i for i in items if i["orphan"]]
    suspects = [i for i in items if not i["orphan"]]

    # 优先级：无字面量风险（孤儿已满足）> res XML 内部无被引 > 体积小
    def sort_key(it):
        xml_internal = any(h.startswith("app/src/main/res/") for h in it["hits"]["at_ref"])
        return (xml_internal, it["size"])

    orphans.sort(key=sort_key)

    summary = {
        "scanned": len(items),
        "orphans": len(orphans),
        "suspects": len(suspects),
        "by_type": {},
        "getIdentifier_calls": get_identifier,
    }
    for it in items:
        summary["by_type"].setdefault(it["type"], {"total": 0, "orphan": 0})
        summary["by_type"][it["type"]]["total"] += 1
        if it["orphan"]:
            summary["by_type"][it["type"]]["orphan"] += 1

    print("=== 清缝合怪批1 · 资源孤儿扫描摘要 ===")
    print(f"共扫 {summary['scanned']} 个；五口径全 0 命中（候选孤儿）{summary['orphans']} 个；存疑保留 {summary['suspects']} 个")
    for t, v in sorted(summary["by_type"].items()):
        print(f"  {t}: 孤儿 {v['orphan']}/{v['total']}")
    print(f"\n仓库级 getIdentifier 调用 {len(get_identifier)} 处（人工复核类型）：")
    for g in get_identifier:
        print(f"  - {g}")
    print("\n=== 候选孤儿清单（按 无XML内部引用 > 体积小 排序）===")
    for i, it in enumerate(orphans, start=1):
        print(f"{i:3d}. {it['path']}  ({it['size']} B)")

    result = {"summary": summary, "orphans": orphans, "suspects": suspects}
    if json_out:
        with open(json_out, "w", encoding="utf-8") as fh:
            json.dump(result, fh, ensure_ascii=False, indent=2)
        print(f"\nJSON 明细已写：{json_out}")
    else:
        default_json = os.path.join(ROOT, "data_scripts", "scan-orphan-res.result.json")
        with open(default_json, "w", encoding="utf-8") as fh:
            json.dump(result, fh, ensure_ascii=False, indent=2)
        print(f"\nJSON 明细已写：{os.path.relpath(default_json, ROOT)}")


if __name__ == "__main__":
    main()
