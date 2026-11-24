#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
delete-orphan-strings.py — 按 scan-orphan-strings.json 的 class_a 清单删除已证死键。

安全闸：
  - 只处理 app/src/main/res/values*/*.xml；
  - 只删 JSON 清单内的 string/string-array/plurals 条目；
  - translatable="false" 一律不删（清单生成时已豁免，这里再兜一层）；
  - 删后用 XML 解析器校验良构；
  - 文件删空（仅剩 resources 壳）不删文件，只登记到 empty_files。
class_b 本批为 0，脚本保留对应分支以备复跑。
"""
import json
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
JSON_PATH = os.path.join(ROOT, "data_scripts", "scan-orphan-strings.json")

ENTRY_OPEN = re.compile(
    r'<(?P<tag>string|string-array|plurals)\s+name="(?P<name>[^"]+)"(?P<attr>[^>]*)>', re.S)


def main():
    report = json.load(open(JSON_PATH, encoding="utf-8"))
    dead_a = {x["name"]: x["tag"] for x in report["class_a"]}
    dead_b = {x["name"]: x["tag"] for x in report["class_b"]}
    dead = dict(dead_a)
    for k, v in dead_b.items():
        dead.setdefault(k, v)

    per_file = {}
    empty_files = []
    removed_total = 0

    for d in sorted(os.listdir(RES)):
        if not d.startswith("values"):
            continue
        dpath = os.path.join(RES, d)
        if not os.path.isdir(dpath):
            continue
        for fn in sorted(os.listdir(dpath)):
            if not fn.endswith(".xml"):
                continue
            p = os.path.join(dpath, fn)
            rel = os.path.relpath(p, ROOT)
            text = open(p, encoding="utf-8").read()
            removed = []
            new_text = text
            for m in ENTRY_OPEN.finditer(text):
                name, tag, attr = m.group("name"), m.group("tag"), m.group("attr")
                if name not in dead or 'translatable="false"' in attr:
                    continue
                block = re.compile(
                    r'[ \t]*<%s\s+name="%s"[^>]*>.*?</%s>[ \t]*\n?'
                    % (tag, re.escape(name), tag), re.S)
                new_text, n = block.subn("", new_text)
                if n:
                    removed.append(name)

            if not removed:
                continue

            # 良构校验
            try:
                ET.fromstring(new_text)
            except ET.ParseError as e:
                print("!! XML 校验失败，未写回:", rel, e)
                continue

            open(p, "w", encoding="utf-8").write(new_text)
            per_file[rel] = len(removed)
            removed_total += len(removed)

            # 是否删空：除 XML 声明/根标签/注释外无条目
            body = re.sub(r"<\?xml.*?\?>", "", new_text)
            body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
            body = re.sub(r"</?resources[^>]*>", "", body).strip()
            if not body:
                empty_files.append(rel)

    print("=== 每文件删除条目数 ===")
    for rel, n in sorted(per_file.items()):
        print("%-55s %d" % (rel, n))
    print("删除条目总数:", removed_total)
    print("删空待整体登记(本单不删文件):", empty_files or "无")


if __name__ == "__main__":
    main()
