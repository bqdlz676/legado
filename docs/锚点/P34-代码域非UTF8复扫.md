# 锚点卡 · P34 代码/配置域非 UTF-8 复扫（/tmp/utf8_scan_code.py）

> 对象：一次性扫描脚本 `/tmp/utf8_scan_code.py`（只落 /tmp，不进仓库）；回执报告 `docs/修复-代码域非UTF8复扫.md`。本卡对应代码与配置后缀的严格 UTF-8 复扫单。

## 一、事实锚点（逐字，含 文件:行）

- 范围分组 `/tmp/utf8_scan_code.py:31` app/modules 下 `.kt`；`:33` `data_scripts/` 下 `.py`；`:35` `.yml/.yaml`；`:37` `.xml`；`:39` `.gradle/.gradle.kts`；`:29` fixtures 单独归 fx 只读。
- 排除目录 `:17` `EXCLUDE_DIRS = {".git", "build", ".gradle", ".kotlin", "node_modules", ".idea"}`。
- 严格解码 `:61` `data.decode("utf-8", errors="strict")`；失败偏移 `:64` `return e.start, e.end, e.reason`。
- 阈值沿用文档域：`:20` `MAX_ISOLATED_DELETES = 2`；`:22` `HI_DENSE_THRESHOLD = 6`；`:127` 窗口 20 字节；`:129` `hi >= HI_DENSE_THRESHOLD` 中止强改；`:132` `deletes.append(e.start)` 记偏移。

## 二、参数表

| 项 | 值/口径 | 出处 |
|---|---|---|
| 解码口径 | `errors='strict'`，非法序列即抛 `UnicodeDecodeError` | :61 |
| 失败定位 | `e.start` 起始偏移 + 前后 40 字节 repr | :64 |
| 孤立坏字节 | 单文件删除 ≤2 字节、窗口高比特 <6；每删一字记录偏移 | :20、:127-132 |
| 整段异编码 | 高比特密集或超阈值 → 不强改，单列「需人工判定」 | :22、:129 |
| fixtures | `data_scripts/fixtures/**` 不论后缀，只读不修 | :29 |
| 阈值调整纪律 | 阈值若误报，须在报告记明误报样本与调整值，不静默放宽 | 本卡结论 C5 |

## 三、结论锚点

- C1 strict 解码成功 → 不属于本单范围，不改动字节。
- C2 孤立坏字节 → 删除该字节至可严格解码，删除偏移逐条入报告；本单实际删除 0 字节。
- C3 整段别的编码 → 保留原字节，报告单列人工判定；本单实际 0 例。
- C4 fixtures 只扫不修，失败单列；本单 3 份 fixtures 解码均 OK。
- C5 阈值沿用文档域；若后续坏样本证明阈值误报/漏报，调整必须留痕（样本、旧值、新值、理由），禁止静默放宽。

## 四、回源

- 回执报告：`docs/修复-代码域非UTF8复扫.md`；一次性脚本：`/tmp/utf8_scan_code.py`；文档域前单：`docs/修复-全仓非UTF8复扫.md`、`docs/锚点/P33-全仓非UTF8复扫.md`。
