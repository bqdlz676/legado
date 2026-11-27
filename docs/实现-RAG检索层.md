锚点卡：docs/锚点/Q454-RAG检索层.md

# 实现报告 · Q454 RAG 检索层查询层 + 同义词表 + 索引器三处缺陷修复

> 机器头：被改文件 5 份（新增 `data_scripts/rag-query.py`、`data_scripts/rag-synonyms.json`、本报告、`docs/锚点/Q454-RAG检索层.md`；修改 `data_scripts/rag-index.py`）；判据 6/6 实测通过；门禁结论见末节（交前实跑贴输出）；未验证项 2 条（见「未验证项」节）。

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「永远找问题，解决问题，提出问题」（docs/机制-总控必读.md:546）
- 适用条款：B2（grep 回证）、C3（边界兜底不降级）、F8（新发现问题节逐条带状态）、R1/R3/R5/R7/R8（规格 §三口径）

## 一、任务与范围

规格 `docs/机制-RAG检索层.md` §五 六条判据，在初版索引器 `data_scripts/rag-index.py`（已跑通一次）之上补齐查询层：两路召回、元数据过滤、同义词表，并把六条判据逐条实测跑穿。本层不出现在任何用户可见界面（规格 `docs/机制-RAG检索层.md:56`），只给下一棒工程师一条命令拿到 `文件:行` 与原文。

开工先证不是重复造：`data_scripts/` 下既有 `docs-index.py`、`mechanism-index.py` 是导航层人工可读全景（规格 `docs/机制-RAG检索层.md:42` 所称 GEN 区间），本层是执行层机器召回入口，两者同源不同用；仓内无 faiss/milvus/chroma 等向量库依赖（规格 `docs/机制-RAG检索层.md:9`），本单不引入。

## 二、改动清单（文件:行 + before/after）

### 1. 新增 `data_scripts/rag-query.py`（查询层）

- 两路召回：结构路 `data_scripts/rag-query.py:135`（标题精确 100 / 路径命中 80 / 标题包含 60 三档权重，权重口径注释在 `data_scripts/rag-query.py:43-46`）；词法路 `data_scripts/rag-query.py:161`（FTS5 trigram `MATCH` + `bm25()` 排序）。合并去重后扣长度惩罚，排序口径「结构命中 > 词法命中 > 长度惩罚」对齐规格 `docs/机制-RAG检索层.md:24`。
- 回源输出（R1）：每条结果带 `路径:行号区间` + 字符偏移 + 原文片段，片段截取见 `data_scripts/rag-query.py:182`；偏移列由索引器提供，截断边界可回原文复核。
- 元数据过滤（R3）：`--type/--domain/--status/--since/--top`，条件先拼成 WHERE 子句再进两路召回（`data_scripts/rag-query.py:104`）；未施加的过滤在头部打印实际生效集合（`data_scripts/rag-query.py:221-223`）。
- 空结果（R8）：显式打印「未命中」并返回退出码 3（`data_scripts/rag-query.py:243-245`）；FTS5 不可用退出码 2，不静默退化成 LIKE 全表扫（`data_scripts/rag-query.py:215`）。
- 同义词扩召：`data_scripts/rag-query.py:66` 读 `data_scripts/rag-synonyms.json`，命中同族词整族 OR 并入词法路；表损坏/缺失只告警不报错，不联网下载。

### 2. 新增 `data_scripts/rag-synonyms.json`（同义词表）

11 族人工词条（陀螺仪/半屏面板/名物典故/战役回放/地形栅格/阅读高亮/书架/搜索热词/证据锚点/事故复盘/翻页分页），首行 note 写明「只许人工增补，禁止自动学习」，对齐规格 `docs/机制-RAG检索层.md:24` 语义路暂缺的近似替代口径。

### 3. 修改 `data_scripts/rag-index.py`（三处缺陷修复，逐条 改前 → 改后 → 复跑证据）

**缺陷一：代码块被二次切窗，行号整体错位。**
- 改前：`split_code` 内部先调 `window_blocks` 切一次窗，`build()` 对返回块又切第二次；代码块被重复切片、行号在二次偏移上再推一次。
- 改后：`split_code` 只按顶层声明切段（`data_scripts/rag-index.py:174`），二次切窗唯一入口收归 `window_blocks`（`data_scripts/rag-index.py:149`）。
- 复跑证据：改前首轮 `--verify` 抽样 20 块 12 块 FAIL（含 `app/src/main/java/io/legado/app/help/sim/SimEngine.kt:205-216` 等代码块）；改后三轮 `--verify` 全 PASS，扩大抽样 200 块 FAIL 0。

**缺陷二：只存行号不存偏移，跨行进切块整行回读对不上。**
- 改前：表结构只有 `line_start/line_end`，`verify` 把行区间整行 join 后与块正文比；跨行进切的末行是截断行，整行必然对不上。
- 改后：新增 `off_start/off_end` 字符偏移列（`data_scripts/rag-index.py:64`，旧库 ALTER 补齐在 `data_scripts/rag-index.py:68-70`）；`verify` 优先按偏移取原文子串逐字相等比对（`data_scripts/rag-index.py:284`、`:301`），行号区间只作给人看的定位。
- 复跑证据：改前 20 抽 12 FAIL；改后 200 块定向偏移比对 0 FAIL。

**缺陷三：contentless FTS5 禁止 DELETE，增量更新旧块倒排删不掉。**
- 改前：`chunks_fts` 用 `content=''` 纯无内容模式；增量更新执行 `DELETE FROM chunks_fts WHERE rowid IN (...)` 直接抛 `sqlite3.OperationalError: cannot DELETE from contentless fts5 table: chunks_fts`，旧块倒排残留在索引里成幽灵命中。
- 改后：改为外部内容表 `content='chunks', content_rowid='id'`（`data_scripts/rag-index.py:77-78`），DELETE 时回读 chunks 旧值正确移除；先删 FTS 再删 chunks 的顺序保留在 `data_scripts/rag-index.py:242`。
- 复跑证据：改前夹具增量复现上述报错；改后 `python3 data_scripts/rag-index.py` 增量重建正常完成，夹具块 mtime/sha 刷新（见判据二）。

grep 回证（改动已落盘）：

- `grep -n "content_rowid" data_scripts/rag-index.py` → `:78 content='chunks', content_rowid='id'`。
- `grep -n "off_start" data_scripts/rag-index.py` → 列定义 `:64`、迁移 `:69`、写入 `:258`、回读 `:286`。
- `grep -n "未命中" data_scripts/rag-query.py` → `:244` 空结果打印行。

## 三、六条判据实测（规格 §五，逐条陈述，不用表格）

### 判据 1：5 条已知答案问题逐条核对（查询词 → 命中锚点 → 原文该行）

1. 查询词「7543 声明」→ 命中锚点 `docs/机制-RAG检索层.md:13-16`（词法路，全量 11 块中排第 6）→ 原文该行：「全仓已有一份不依赖任何模型的符号索引 `data_scripts/dep-symbols.json`（134 包 / 2932 类 / 7543 声明），再加一个需要下载模型、需要联网、需要几 GB 显存的向量库……」（`docs/机制-RAG检索层.md:14`）。
2. 查询词「trigram」→ 命中锚点 `data_scripts/rag-index.py:67-75`（词法路召回，覆盖注释行 72；同词另命中 `:58-67`、`:340-345` 两块）→ 原文该行：「# trigram 分词：中文没有空格，unicode61 会把整句当一个词；trigram 支持「任意子串」召回（≥3 字）」（`data_scripts/rag-index.py:72`）。
3. 查询词「锚点强制可回源」→ 命中锚点 `docs/机制-RAG检索层.md:7-13`（词法路，全量 15 块中排第 14）→ 原文该行：「机制层的第一性要求是**锚点强制可回源**：任何结论必须能指到 `文件:行` 的原文。向量相似度只回答「意思像不像」，不回答「原文在哪」……」（`docs/机制-RAG检索层.md:11`）。
4. 查询词「BM25 倒排」→ 命中锚点 `docs/机制-RAG检索层.md:18-25`（词法路，2 块中排第 2）→ 原文该行：「召回：两路并行——词法路（FTS5 BM25 倒排，权重高）与结构路（小节标题/文件名/符号名精确匹配，权重最高）……」（`docs/机制-RAG检索层.md:24`）。
5. 查询词「line-anchors 基线」→ 命中锚点 `data_scripts/check-line-anchors.py:43-54`（结构路路径命中，覆盖行 48）→ 原文该行：`BASELINE_REL = 'data_scripts/line-anchors-baseline.json'`（`data_scripts/check-line-anchors.py:48`）。

五条命中块的正文均经偏移子串逐字比对与原文一致（判据 4 同一比对口径）；第 1、3 条期望锚点未进 top3，排名偏弱的根因与处置见「新发现问题」第 1 条。

### 判据 2：增量索引只重建被改文档的块（块级 mtime/sha 对照）

- 改前：临时夹具 `docs/机制-RAG夹具-TEMP.md` 3 块，mtime `1795781796.725431`，sha `0be2f376df490435`；全库 63012 块。
- 追加一行后重建：夹具仍 3 块（块数差 0），mtime `1795781949.059839`，sha `0e2140dfb9a0fb98`；全库 63014 块（差 +2，追加行推动二次切块）。
- 未变样本 `docs/机制-RAG检索层.md` 的 mtime/sha 在增量后未被刷新；增量跳过判据为 mtime + sha 双匹配（`data_scripts/rag-index.py:237-239`），mtime 精度不足时 sha 兜底。
- 本轮首跑增量重建 6 份：夹具 1 份 + 工作树并行改动 5 份；二次空跑后复核库内与磁盘不一致文件 0 份。夹具测完已删除并整库重建，缓存不留临时样本。

### 判据 3：元数据过滤真实缩范围（结果集大小数字）

同一查询词「回放」，干净库（2716 份 / 63013 块）：

- 不带过滤：结构路 397 / 词法路 882 / 合并去重 **1054 块**。
- `--type 修复`：结构路 105 / 词法路 128 / 合并去重 **178 块**。
- `--type 源码`：结构路 0 / 词法路 332 / 合并去重 **332 块**。

过滤先于召回生效（`data_scripts/rag-query.py:104`），裸查结果集是 `--type 修复` 的约 5.9 倍。

### 判据 4：`--verify` PASS / 改坏一行 FAIL 并点名

- 正常索引：`python3 data_scripts/rag-index.py --verify` → `RAG-VERIFY: PASS（抽样 20 块，逐字回读一致）`，连跑三轮全 PASS。
- 人为改坏：把夹具判据句「条件三未否决」改成「条件三已否决」，不重建索引；按 verify 同一偏移比对口径定向回读夹具 3 块 → `FAIL docs/机制-RAG夹具-TEMP.md 第 3-9 行（偏移 17-517）`，1 块对不上原文，另 2 块 PASS。点名到具体块的文件、行号区间与偏移。

### 判据 5：500/50 夹具证明跨段判据不被切断

夹具单节 732 字（超 500 字二次切分线），内嵌判据句「判据甲：当且仅当条件一成立且条件二同时满足、条件三未否决，方可放行；三条缺一不可，不得在条件之间断开。」（51 字）。切块结果 3 块：文首标题块 1 块 + 小节内 2 块；判据整句完整落在小节第 2 块内，未在条件间断开；同小节相邻块重叠 104 字（≥50 字下限）。跨小节标题边界不要求重叠，重叠只约束同小节二次切出的相邻块。

### 判据 6：零网络、零模型、零外部进程（静态证据）

- 网络/模型库引用扫描：`grep -nE 'socket|http|urllib|requests|urlopen|http.client|aiohttp' data_scripts/rag-query.py data_scripts/rag-index.py` 仅命中 `data_scripts/rag-query.py:22` 的注释文字，无任何 import 或调用。
- 外部进程引用扫描：`grep -nE 'subprocess|os.system|popen|fork|execv'` 两份脚本 0 命中；查询层不调 `rg`（第二层精确落点由人或后续工具接手，R8）。
- import 清单：`rag-query.py` 仅 argparse/datetime/json/os/re/sqlite3/sys；`rag-index.py` 仅 argparse/bisect/hashlib/random/json/os/re/sqlite3/sys/time，全部 Python 标准库；无模型文件路径、无下载动作。

## 四、二阶三阶影响推演

- 直接效果：新增只读查询入口与一份可删可重建的 FTS 缓存；既有门禁判据、锚点基线、`check-drift.py` 均未改动（边界见 `docs/锚点/Q454-RAG检索层.md` 第四节）。
- 连带影响：索引器表结构加了两列偏移；旧库由 `ALTER TABLE` 补齐列，存量 chunks 偏移为空时 `verify` 退回行区间容错比对，不崩。查询层只读库，与其他代理并发走 WAL，无写锁竞争。
- 二阶后果：同义词表若被后续脚本自动学习回填，会把检索漂移固化进索引；json 首行 note 已写明人工增补，后续若加写表脚本必须保留这条禁令。
- 三阶后果：`iter_files` 的扩展名表（`data_scripts/rag-index.py:37`）是新增文档类型时的维护点；新类型文档若不进扩展名表，会落「其它」档，召回不到类型过滤下。

## 五、自查反例（假设我的方案是错的，逐条找反例与兜底）

- 反例 1：FTS5 不可用时若退化成全表 LIKE，会把「索引坏了」变成「查得慢」，问题被性能表象掩盖。实现为显式退出码 2（`data_scripts/rag-query.py:215`、`data_scripts/rag-index.py:326`），不静默降级。
- 反例 2：空结果若静默返回空列表，就是把「没找到」当「不存在」，违反 R8。实现为打印「未命中」+ 退出码 3（`data_scripts/rag-query.py:244`），并提示第二层 rg/Glob 落点。
- 反例 3：切片若在多条件判据的条件之间断开，前提丢失即另一种造假（R2）。夹具证明整句完整落块、同小节重叠 104 字；单节内多条件判据整节保留，宁可超 500 字不在条件间断。
- 反例 4：若把索引库当事实源，库删重建后旧结论就悬空。口径为库可整库删除重建（R4），查询结果只作线索，写报告必须回原文重读给锚点（R7，`docs/机制-RAG检索层.md:35`）。
- 反例 5：若词法 BM25 压过标题精确命中，作者显式命名的小节/符号锚点会被正文高频词淹没。现结构路三档固定高分在词法分之上（`data_scripts/rag-query.py:43-46`）；结构路未命中才纯走词法排序。
- 边界兜底：查询词为单字/双字时 trigram 无 ≥3 字片段，词法路打印显式声明后只走结构路；同义词表缺失/损坏时告警并按原词召回；`--since` 日期格式错直接退出码 2，不放宽解析。

## 六、新发现问题

1. BM25 长度偏置压长正文：短块（锚点卡、JSON 文件头）在罕见词查询中排名压过长正文规格块，如「7543 声明」规格块排第 6/11、「锚点强制可回源」规格块排第 14/15。处置状态：待裁（是否加块长归一化或上调标题命中加权，留待下一棒定口径）。
2. docs 域抽取与类型重复：`domain_of` 对 Markdown 取文件名首段，域前八里出现「验收/修复」这类本属类型档的值，`--domain` 对文档侧区分度低。处置状态：已入队（后续按 docs 二级目录或主题词重抽域字段）。
3. 状态词只取文件前 40 行：状态写在 40 行之后的文档记 `none`（当前 none 51681/63013），`--status` 召回不全。处置状态：待裁（是否放宽到全文首现或专扫文末状态行）。
4. trigram 词长 ≥3，单字/双字查询词法路不可用：只能走结构路，已在脚本内显式打印声明，未静默降级。处置状态：已处置（语义路暂缺口径下的边界兜底；后续靠同义词表扩充覆盖常见双字别名）。
5. 增量计数受并行工作树影响：首跑增量重建 6 份，除夹具外 5 份为工作树并行改动文件；二次空跑后库盘一致性复核 0 不符，mtime+sha 双判据本身成立。处置状态：已闭环（未变样本 sha 未刷新，增量跳过口径有效）。
6. 同义词族 OR 扩召带宽词：族内常见词（如「地形」）与主词同权 OR，会放大召回集；实测「高度图 栅格」词法路 1400 块，top 命中仍准但结果集偏宽。处置状态：待裁（是否区分主词与别名、别名降权，或对扩召词加长度/频率门槛）。

## 七、未验证项

- 未跑真机、未跑编译/单测：本层是纯脚本与静态索引，无 UI 面；按铁律本地禁编译，编译与 CI 全绿归云端。
- 真实语料召回命中率未做规模评测：五条人工核对只覆盖已知答案问题，长查询/口语化提问的召回质量为观察项，本单不伪造命中率（边界见 `docs/锚点/RAG检索层.md:39`）。

## 八、门禁实跑留痕（交前）

命令与末行原文输出（逐字抄录，未改写）：

- `python3 data_scripts/check-report-fields.py docs/实现-RAG检索层.md` → 末行：`REPORT-FIELDS: PASS（1 份，0 份不合格）`
- `python3 data_scripts/check-anchor-card.py docs/实现-RAG检索层.md` → 末行：`汇总: PASS=1 FAIL=0（共 1 份）`（两条 WARN：次要提及 C3/F8 无锚点卡，系规则条款编号非缺陷号，不判红）
- `python3 data_scripts/check-problem-finding.py docs/实现-RAG检索层.md` → 末行：`PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`
- `python3 data_scripts/check-onboarding.py` → 末行：`ONBOARD: PASS（5 份报告）`
- 索引自证：`python3 data_scripts/rag-index.py --stats` → `RAG-INDEX: 文档 2716 份 / 块 63013 个 / 平均块长 382 字`；`python3 data_scripts/rag-index.py --verify` → `RAG-VERIFY: PASS（抽样 20 块，逐字回读一致）`

## 九、交接（发现但未改的跨模块问题）

- `data_scripts/docs-index.py`、`mechanism-index.py` 两份导航索引未与本层缓存建立新鲜度校验；本层缓存删除重建后导航层无感知。建议后续单补一条「缓存陈旧」提示，不属本单文件域，未动手改。
