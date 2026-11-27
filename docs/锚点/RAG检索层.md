# 锚点卡：docs/锚点/RAG检索层.md

> 用途：RAG 检索层开工与独立验收依据。规格：`docs/机制-RAG检索层.md`。
> 立项来源：用户 2026-11-27 口令「弄一下这个进机制里面」，附抖音科普视频（RAG 知识库：切片、重叠、元数据、混合检索）。视频要点只作输入，不作判据；判据以规格 R1–R8 与验收六条为准。

## 一、事实锚点

### 既有机制层的现状（为什么不是加向量库）

- 机制层索引是纯文本 + rg + 锚点：`docs/索引-机制.md`（GEN 区间自动生成，区间外人工区逐字保留）。
- 离线符号存在性索引：`data_scripts/dep-symbols.json`（134 包 / 2932 类 / 7543 声明），由 `data_scripts/check-dependency-symbols.py` 消费。
- 锚点回源硬规：`docs/机制-证据锚规范.md`；机检 `data_scripts/check-line-anchors.py`（棘轮，基线 `data_scripts/line-anchors-baseline.json` 3837 键）。
- 语料诚实性门禁：`data_scripts/check-test-corpus-honesty.py`（禁止把检索摘要当证据）。

### 切片与元数据口径

- 切片单位 = Markdown 小节（`#`/`##`/`###`），单节超过 500 字二次切分，相邻块 50 字重叠。
- 元数据六项：路径、小节标题、行号区间、文档类型、域、状态。
- 索引落点：`data_scripts/_cache/rag-index.sqlite`（FTS5，可删可重建，不得成为事实源）。

### 入口命令

- 建库 `python3 data_scripts/rag-index.py [--rebuild] [--stats] [--verify]`
- 查询 `python3 data_scripts/rag-query.py "关键词" [--type][--domain][--status][--since][--top]`

## 二、验收判据（六条）

1. 已知答案问题召回正确小节，锚点 `文件:行` 与原文逐字一致（抽 5 条）。
2. 增量索引只重建被改文档的块（块级 mtime 对照）。
3. 元数据过滤真实缩范围（带 `--type` 与不带结果集大小差异给数字）。
4. `--verify` 正常 PASS、改坏一行 FAIL 并点名。
5. 夹具验证 500/50：跨段判据的长小节不被切断。
6. 零网络、零模型加载（代码无网络库引用 + 运行时无外部进程）。

## 三、边界

- 只新增 `data_scripts/rag-index.py`、`data_scripts/rag-query.py`、`data_scripts/rag-synonyms.json`、`docs/机制-RAG检索层.md` 与本卡；不改既有门禁判据、不改 `check-drift.py`、不动锚点基线。
- 检索结果只作线索，写进报告必须回原文重读并给锚点（R7）。
- 沙箱无网络无模型，验收以静态与夹具为准；真实语料召回质量作为观察项登记，不伪造命中率。
