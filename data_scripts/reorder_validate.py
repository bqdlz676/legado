# -*- coding: utf-8 -*-
"""
零改字还原校验：
1. 按 manifest 记录的「来源卷 + 源起止行」从原文重新切块；
2. 按原始卷序(01→09)、卷内原始行序拼接，与 9 卷原文直接拼接做逐字节 cmp；
3. 剔除重排版中全部 HTML 注释行后，再与原文拼接逐字节 cmp；
4. 输出总字符数、总行数、两份 sha256 并判定是否相等。
"""
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# 仓库根 = 脚本所在目录(data_scripts)的上一级；基于 __file__ 推导，换检出路径/换机均可跑，不写死绝对路径
REPO = os.path.dirname(HERE)
SRC_DIR = os.path.join(REPO, "docs", "落文-原文")
REORDERED = os.path.join(REPO, "docs", "落文-原文-重排版.md")

with open(os.path.join(HERE, "manifest.json"), encoding="utf-8") as f:
    manifest = json.load(f)


def read_bytes(path):
    with open(path, "rb") as f:
        return f.read()


def sha(b):
    return hashlib.sha256(b).hexdigest()


# 1. 原文直接拼接（基准）
vols = manifest["vol_files"]
baseline = b""
for vol in sorted(vols):
    baseline += read_bytes(os.path.join(SRC_DIR, vols[vol]))

# 2. 按 manifest 切块
block_by_key = {}
vol_cache = {}
for b in manifest["blocks"]:
    vol = b["vol"]
    if vol not in vol_cache:
        raw = read_bytes(os.path.join(SRC_DIR, vols[vol]))
        lines = raw.split(b"\n")
        assert lines[-1] == b""
        vol_cache[vol] = lines[:-1]
    body = vol_cache[vol]
    s, e = b["start"], b["end"]
    raw_block = b"\n".join(body[s - 1:e]) + b"\n"
    # 指纹复核
    assert sha(raw_block) == b["sha256"], f"块指纹不符: {vol} {s}-{e}"
    assert len(raw_block) == b["bytes"], f"块字节数不符: {vol} {s}-{e}"
    block_by_key[(vol, s)] = raw_block

# 3. 还原回原顺序拼接（卷序 01→09，卷内按起始行排序）
rebuilt = b""
for vol in sorted(vols):
    keys = sorted(
        [(v, s) for (v, s) in block_by_key if v == vol],
        key=lambda k: k[1],
    )
    for k in keys:
        rebuilt += block_by_key[k]

# 4. 读重排版，剔除注释行；按输出顺序拼期望正文（块指纹已在上文复核）
with open(REORDERED, "rb") as f:
    reordered_raw = f.read()
kept = []
comment_lines = 0
for line in reordered_raw.split(b"\n"):
    if line.strip().startswith(b"<!--"):
        comment_lines += 1
        continue
    kept.append(line)
stripped = b"\n".join(kept)

grouped = [(k[0], k[1]) for _, keys in manifest["groups"] for k in keys]
expected_body = b"".join(block_by_key[k] for k in grouped)

print("=== 基准（9 卷原文直接拼接） ===")
print(f"字节数: {len(baseline)}")
print(f"字符数: {len(baseline.decode('utf-8'))}")
print(f"行数: {baseline.count(chr(10).encode())}")
print(f"sha256: {sha(baseline)}")
print()
print("=== 按来源卷+源起始行还原拼接 ===")
print(f"字节数: {len(rebuilt)}")
print(f"字符数: {len(rebuilt.decode('utf-8'))}")
print(f"行数: {rebuilt.count(chr(10).encode())}")
print(f"sha256: {sha(rebuilt)}")
print()
print("=== 重排版剔除注释行后（新顺序正文） ===")
print(f"注释行数: {comment_lines}")
print(f"字节数: {len(stripped)}")
print(f"字符数: {len(stripped.decode('utf-8'))}")
print(f"行数: {stripped.count(chr(10).encode())}")
print(f"sha256: {sha(stripped)}")
print()
print("=== 期望正文（按输出顺序从原文切块重拼，零新增） ===")
print(f"字节数: {len(expected_body)}")
print(f"字符数: {len(expected_body.decode('utf-8'))}")
print(f"行数: {expected_body.count(chr(10).encode())}")
print(f"sha256: {sha(expected_body)}")
print()

cmp1 = rebuilt == baseline
cmp2 = stripped == expected_body
print(f"还原拼接(原顺序) == 9 卷原文直接拼接（cmp 逐字节）: {'一致 ✅' if cmp1 else '不一致 ❌'}")
print(f"重排版剔注释 == 期望正文(新顺序切块重拼)（cmp 逐字节）: {'一致 ✅' if cmp2 else '不一致 ❌'}")
print(f"sha256 相等（还原 vs 基准）: {sha(rebuilt) == sha(baseline)}")
print(f"sha256 相等（剔注释重排 vs 期望正文）: {sha(stripped) == sha(expected_body)}")

if not (cmp1 and cmp2):
    # 定位首个差异
    targets = (("rebuilt", rebuilt, baseline), ("stripped", stripped, expected_body))
    for name, other, ref in targets:
        if other != ref:
            n = min(len(other), len(ref))
            for i in range(n):
                if other[i] != ref[i]:
                    print(f"[{name}] 首个差异字节偏移 {i}: 实际={other[max(0,i-20):i+20]!r} 参照={ref[max(0,i-20):i+20]!r}")
                    break
            else:
                print(f"[{name}] 长度不同: 实际 {len(other)} vs 参照 {len(ref)}")
    sys.exit(1)
