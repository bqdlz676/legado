#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批1 删除清单构建器（只读复核，不删文件）。
输入：data_scripts/scan-orphan-res.result.json（scan-orphan-res.py 产物）
输出：data_scripts/scan-orphan-res.manifest.json
  - first_order：五口径全 0 的一阶孤儿
  - second_order：仅被一阶孤儿引用、删后新变孤立的二阶资源
复核方式：重新加载语料，对清单内每条独立跑五口径扫描；任一口径非 0 即标记 keep 并不入清单。
"""
import importlib.util
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("scan_orphan_res", os.path.join(_HERE, "scan-orphan-res.py"))
sc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sc)

ROOT = sc.ROOT
RESULT = os.path.join(ROOT, "data_scripts", "scan-orphan-res.result.json")
MANIFEST = os.path.join(ROOT, "data_scripts", "scan-orphan-res.manifest.json")

CALIBERS = ["R_ref", "at_ref", "tools_attr", "binding", "literal"]
CALIBER_CMDS = {
    "R_ref": "grep -rnE 'R\\.{typ}\\.{name}(?![A-Za-z0-9_])' app/src modules --include='*.kt' --include='*.java'",
    "at_ref": "grep -rnE '@{typ}/{name}(?![A-Za-z0-9_])' app/src/main/res app/src/main/AndroidManifest.xml --include='*.xml'",
    "tools_attr": "grep -rnE '(tools:|\\?attr/).*{name}' app/src/main/res --include='*.xml'",
    "binding": "grep -rnE '{Pascal}Binding|{Pascal}' app/src --include='*.kt' --include='*.java' --include='*.xml'",
    "literal": "grep -rnE '[\"\\'][^\"\\']*{name}[^\"\\']*[\"\\']' app/src modules data_scripts --include='*.kt' --include='*.java' --include='*.xml' --include='*.py' --include='*.sh'",
}


def main():
    data = json.load(open(RESULT, encoding="utf-8"))
    corpus = sc.load_corpus()

    orphans = data["orphans"]
    orphan_paths = {o["path"] for o in orphans}

    # 二阶候选：suspect 的全部入向引用文件都在一阶孤儿集合内
    second = []
    for s in data["suspects"]:
        ref_files = {h.split(":")[0] for v in s["hits"].values() for h in v}
        if ref_files and ref_files <= orphan_paths:
            second.append(s)

    manifest = {"first_order": [], "second_order": [], "kept_suspect": []}
    aborted = []

    # 一阶路径集合：二阶复核时把待删一阶文件从语料中剔除（模拟删后树）
    first_paths = {o["path"] for o in orphans}
    corpus_after_first = {k: v for k, v in corpus.items() if k not in first_paths}

    def enroll(item, order, ref_corpus):
        hits = sc.scan_item(item["type"], item["name"], ref_corpus)
        counts = {k: len(v) for k, v in hits.items()}
        evidence = {
            k: {
                "count": counts[k],
                "cmd": CALIBER_CMDS[k].format(
                    typ=item["type"], name=item["name"],
                    Pascal=sc.pascal(item["name"])),
                "hits": hits[k],
            }
            for k in CALIBERS
        }
        record = {
            "path": item["path"],
            "type": item["type"],
            "name": item["name"],
            "size": item["size"],
            "order": order,
            "counts": counts,
            "evidence": evidence,
        }
        if any(counts.values()):
            manifest["kept_suspect"].append(record)
            aborted.append(item["path"])
        else:
            manifest[order].append(record)

    for o in orphans:
        enroll(o, "first_order", corpus)
    for s in second:
        enroll(s, "second_order", corpus_after_first)

    manifest["summary"] = {
        "first_order": len(manifest["first_order"]),
        "second_order": len(manifest["second_order"]),
        "kept_suspect": len(manifest["kept_suspect"]),
        "aborted": aborted,
    }

    with open(MANIFEST, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)

    print(json.dumps(manifest["summary"], ensure_ascii=False, indent=2))
    if aborted:
        print("!! 复核非 0，以下条目转入存疑保留：", aborted)
        sys.exit(2)


if __name__ == "__main__":
    main()
