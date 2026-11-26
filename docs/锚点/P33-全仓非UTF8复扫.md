# 锚点卡 · P33 全仓非 UTF-8 复扫与字节修复（/tmp/utf8_scan.py）

> 对象：一次性扫描脚本 `/tmp/utf8_scan.py`（只落 /tmp，不进仓库）；回执报告 `docs/修复-全仓非UTF8复扫.md`。本卡对应全仓 .md/.json 严格 UTF-8 复扫单。

## 一、事实锚点（逐字，含 文件:行）

- 后缀与范围 `/tmp/utf8_scan.py:16` `SUFFIX = (".md", ".json")`；`:14` `FIXTURE_DIR = "data_scripts/fixtures"`；`:30` fixtures 不论后缀、只看不修；`:17` 排除目录 `.git/build/.gradle/.kotlin/node_modules/.idea`。
- 严格解码 `:40` `data.decode("utf-8", errors="strict")`；失败偏移 `:43` `return e.start, e.end, e.reason`；上下文 `:46` `show_context(data, off, span=40)`。
- 孤立坏字节阈值 `:19` `MAX_ISOLATED_DELETES = 2`；异编码判定 `:108` 窗口 20 字节、`:109` 高比特计数、`:110` `hi >= 6` 即中止强改、转人工判定；`:113` 每删一字记录偏移。
- 三模式 `:7` 注释：`scan` 只读列失败；`fix` 孤立字节就地删除并记偏移；`rescan` 复扫必须 0 失败（fixtures 除外）。

## 二、参数表

| 项 | 值/口径 | 出处 |
|---|---|---|
| 解码口径 | `errors='strict'`，非法序列即抛 `UnicodeDecodeError` | :40 |
| 失败定位 | `e.start` 起始偏移 + 前后 40 字节 repr | :43、:46 |
| 孤立坏字节 | 单文件删除 ≤2 字节、窗口高比特 <6；每删一字记录偏移 | :19、:108-113 |
| 整段异编码 | 高比特密集或超阈值 → 不强改，单列「需人工判定」 | :110、:115 |
| fixtures | 全后缀扫描，单列报告，禁止修复 | :30 |

## 三、结论锚点

- C1 strict 解码成功 → 不属于本单范围，不改动字节。
- C2 孤立坏字节 → 删除该字节至可严格解码，删除偏移逐条入报告；本单实际删除 0 字节。
- C3 整段别的编码 → 保留原字节，报告单列人工判定；本单实际 0 例。
- C4 fixtures 只扫不修，失败单列；本单 3 份 fixtures 解码均 OK。

## 四、回源

- 回执报告：`docs/修复-全仓非UTF8复扫.md`；一次性脚本：`/tmp/utf8_scan.py`；前棒已修两份历史坏文件复核：`docs/机制-观感UX验收-回执.md`、`docs/问题清单-附录-逐文件.md`。
