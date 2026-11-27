# 锚点卡 · 验收-Q454 RAG 检索层（独立只读验收）

> 用途：Q454 验收报告回源索引。被验对象：`data_scripts/rag-index.py`、`data_scripts/rag-query.py`、`data_scripts/rag-synonyms.json`、规格 `docs/机制-RAG检索层.md`。
> 对照规格：`docs/机制-RAG检索层.md`（R1–R8，§五 六条验收判据）；上游卡 `docs/锚点/RAG检索层.md`；执行卡 `docs/锚点/Q454-RAG检索层.md`；写码报告 `docs/实现-RAG检索层.md`。
> 基线 HEAD：`f896179a0fb1c4420c52c6473442f7c8f4739755`（f896179，已 push）。

## 一、七条必判与验收结论

1. 删库重建可恢复 — PASS：删库后 `--rebuild` 2720 份 / 63115 块 / 平均 382 字；清夹具后整库重建 2722 份 / 63168 块 / 平均 382 字；平均块长与写码方自报 382 一致。
2. 200 块抽样逐字一致 — PASS：字符偏移子串比对 200 块 0 失败；官方 CLI 默认 20 抽样 PASS。
3. 改坏一行 FAIL 点名、还原后 PASS — PASS：坏块点名 `docs/机制-RAG夹具-Q454改坏TEMP.md 第 3-6 行（偏移 36-130）`；还原重建后定向回读 3 块全 PASS。
4. 500/50 切片 — PASS：983 字小节二次切出 3 块，跨两行的判据整句完整落在同一块，同小节相邻块重叠 50 字（≥50 下限）。
5. 元数据过滤真实缩范围 — PASS：「回放」裸查 973 块 → `--type 修复` 178 块 / `--type 源码` 317 块；「复盘」裸查 479 块 → `--type 机制` 7 块。
6. 零网络、零模型、零外部进程 — PASS：禁词扫描仅注释文字命中，import 清单全部 Python 标准库，查询进程生命周期内观测无子进程。
7. 两条未进 top3 锚点复跑定裁 — 可接受（附观察项）：「7543 声明」第 4/6 名、分 10.026、差 top3 末位 -0.423；「锚点强制可回源」第 4/244 名、分 13.527、差 -0.381；两条均未漏召，锚点与原文逐字一致。

## 二、关键代码锚点（验收方 grep 回证）

- 索引器：偏移列定义 `data_scripts/rag-index.py:64`；外部内容表 `content='chunks', content_rowid='id'` 在 `data_scripts/rag-index.py:78`；二次切窗唯一入口 `data_scripts/rag-index.py:149`；源码按声明切段 `data_scripts/rag-index.py:174`；增量 mtime+sha 双判据跳过 `data_scripts/rag-index.py:240`；先删 FTS 再删 chunks `data_scripts/rag-index.py:242`；verify 偏移子串比对 `data_scripts/rag-index.py:300`；FTS5 不可用显式退出 `data_scripts/rag-index.py:331`。
- 查询层：结构路权重档 `data_scripts/rag-query.py:43`；同义词加载 `data_scripts/rag-query.py:52`；过滤 WHERE 拼接 `data_scripts/rag-query.py:104`；结构路 `data_scripts/rag-query.py:135`；词法路 `data_scripts/rag-query.py:161`；回源片段截取 `data_scripts/rag-query.py:182`；空结果「未命中」退出 `data_scripts/rag-query.py:243-244`；FTS5 不可用显式退出 `data_scripts/rag-query.py:216`。

## 三、边界与口径

- 本验收只读：未改被验四文件，未做 git add/commit/push，未跑 gradlew / kotlinc / SDK；临时夹具造删、索引库删除重建均为可逆验证动作，测完已删夹具并整库重建干净索引。
- 检索结果只作线索（R7），写进报告必须回原文重读给锚点；本卡所有计数与行号为验收方自取，写码方报告数字仅作待验主张，未直接采信。
- 真实语料规模命中率、口语化长查询召回质量为观察项，本验收不伪造命中率。
