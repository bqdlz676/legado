#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
截图帧清单生成器（P0 截图基建产物；P1 观感判定 / P3 基线冻结的统一输入格式）。

口径：
  - 扫描帧目录下全部 PNG，逐帧记录 屏名 / 像素尺寸 / sha256 / 字节数 / 生成时间。
  - 输出 frames.json（机器读）与 Markdown 摘要（人读，逐条陈述，不用表格）。
  - 格式一旦冻结不许随手改字段名；下游 check-visual-baseline.py 与观感代理都按此 schema 取数。

帧名口径：Paparazzi 落盘文件名为 `<包名>_<测试类>_<方法名>[_<标签>].png`；
`name` 取去扩展名后的完整文件名（可回源到具体测试方法），`method` 取最后一段便于人读。

尺寸读取：优先 PIL；PIL 缺失时回落解析 PNG IHDR 头（宽高在字节 16..24，大端序），不强制依赖第三方库。

用法：
  python3 data_scripts/frame-manifest.py \
      [--dir app/build/outputs/screenshots] \
      [--json app/build/outputs/screenshots/frames.json] \
      [--md app/build/outputs/screenshots/frames.md]

退出码：0 = 成功产出清单且至少 1 帧；1 = 帧目录缺失 / 无 PNG / 读取异常（CI 据此判失败，不许空传）。
"""

import argparse
import hashlib
import json
import os
import struct
import sys
from datetime import datetime, timezone

SCHEMA_VERSION = 1
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def parse_png_size(path: str):
    """返回 (width, height)；先试 PIL，失败再解析 IHDR。读不出返回 (None, None)。"""
    try:
        from PIL import Image  # type: ignore

        with Image.open(path) as img:
            return img.size[0], img.size[1]
    except Exception:
        pass

    try:
        with open(path, "rb") as fh:
            head = fh.read(24)
        if len(head) >= 24 and head[:8] == PNG_MAGIC:
            # IHDR：宽度字节 16-19，高度字节 20-23，大端序
            width, height = struct.unpack(">II", head[16:24])
            return width, height
    except Exception:
        pass
    return None, None


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            digest.update(chunk)
    return digest.hexdigest()


def iso_utc(ts: float) -> str:
    return datetime.fromtimestamp(ts, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def collect_frames(frame_dir: str):
    frames = []
    for name in sorted(os.listdir(frame_dir)):
        if not name.lower().endswith(".png"):
            continue
        path = os.path.join(frame_dir, name)
        if not os.path.isfile(path):
            continue
        width, height = parse_png_size(path)
        stat = os.stat(path)
        stem = name[:-4]
        method = stem.rsplit("_", maxsplit=1)[-1]
        frames.append(
            {
                "name": stem,
                "method": method,
                "file": os.path.join(os.path.basename(frame_dir), name),
                "width": width,
                "height": height,
                "sha256": sha256_file(path),
                "bytes": stat.st_size,
                "generatedAt": iso_utc(stat.st_mtime),
            }
        )
    return frames


def write_markdown(md_path: str, frame_dir: str, frames, generated_at: str) -> None:
    lines = []
    lines.append("# 截图帧清单")
    lines.append("")
    lines.append(f"- 生成时间（UTC）：{generated_at}")
    lines.append(f"- 帧目录：`{frame_dir}`")
    lines.append(f"- 帧总数：{len(frames)}")
    lines.append("")
    if not frames:
        lines.append("- 未发现任何 PNG 帧（CI 应判失败）。")
    for idx, f in enumerate(frames, start=1):
        size = f"{f['width']}×{f['height']}" if f["width"] else "尺寸未知"
        digest = f["sha256"][:12]
        lines.append(
            f"- {idx}. {f['method']}（{f['name']}）：{size}，"
            f"{f['bytes']} 字节，sha256 `{digest}…`，生成于 {f['generatedAt']}。"
        )
    with open(md_path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate screenshot frame manifest.")
    parser.add_argument("--dir", default="app/build/outputs/screenshots")
    parser.add_argument("--json", default="app/build/outputs/screenshots/frames.json")
    parser.add_argument("--md", default="app/build/outputs/screenshots/frames.md")
    args = parser.parse_args()

    if not os.path.isdir(args.dir):
        print(f"::error::帧目录不存在：{args.dir}", file=sys.stderr)
        return 1

    try:
        frames = collect_frames(args.dir)
    except Exception as exc:  # 读取异常必须让 CI 失败，不许静默出空清单
        print(f"::error::扫描帧目录失败：{exc}", file=sys.stderr)
        return 1

    if not frames:
        print(f"::error::帧目录下没有任何 PNG：{args.dir}", file=sys.stderr)
        return 1

    generated_at = iso_utc(datetime.now(tz=timezone.utc).timestamp())
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": generated_at,
        "frameDir": args.dir,
        "totalFrames": len(frames),
        "frames": frames,
    }

    os.makedirs(os.path.dirname(os.path.abspath(args.json)), exist_ok=True)
    with open(args.json, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)

    write_markdown(args.md, args.dir, frames, generated_at)

    print(f"帧总数={len(frames)} -> {args.json}")
    for f in frames:
        size = f"{f['width']}×{f['height']}" if f["width"] else "未知尺寸"
        print(f"  - {f['method']}: {size} {f['bytes']}B sha256={f['sha256'][:12]}…")
    return 0


if __name__ == "__main__":
    sys.exit(main())
