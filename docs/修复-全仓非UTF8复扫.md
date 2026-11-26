# 修复-全仓非 UTF-8 复扫

- 日期：2026-11-26
- 范围：全仓 `.md` / `.json` 严格 UTF-8 解码复扫；fixtures 只看不修；不改字句，只动坏字节。
- 锚点卡：docs/锚点/P33-全仓非UTF8复扫.md
- 一次性脚本：`/tmp/utf8_scan.py`（只落 /tmp，未进仓库；三模式 `scan` / `fix` / `rescan`）。

## 一、范围与方法

1. 扫描后缀：`.md`、`.json`；目录覆盖 `docs/**`、仓库根 `*.md` / `*.json`、`.github/**`、`data_scripts/fixtures/**`；排除 `.git`、`build`、`.gradle`、`.kotlin`、`node_modules`、`.idea`。
2. 解码判据：`data.decode("utf-8", errors="strict")`，非法序列抛 `UnicodeDecodeError`，记录首败偏移 `e.start` 与前后 40 字节 repr。
3. 分类器：孤立坏字节（单文件删除 ≤2 字节、失败窗口 20 字节内高比特 <6）→ 删除该字节至可严格解码，逐条记录偏移；高比特密集或超阈值 → 判「整段别的编码」，不强改，单列需人工判定。
4. fixtures 不论后缀扫描，解码结果单列，禁止修复。

## 二、首轮扫描真实输出（scan）

```
扫描范围：docs/**、仓库根 *.md/*.json、.github/**、data_scripts/fixtures/**；排除 .git/build/.gradle/.kotlin/node_modules/.idea
文件计数：md=747 json=205 fixtures=3（合计 955）
fixtures（只看不修）：3 份，解码失败 0 份 []
扫描结论：非 fixtures 失败 0 份
```

目录分布（md+json，不含 fixtures）：docs 700、app 224、data_scripts 14、modules 8、仓库根 4、.github 2、third_party 1。

## 三、分类与处置清单

- 修复文件清单（孤立坏字节就地删除）：无。本轮删除字节 0 个，删除偏移清单为空。
- 需人工判定（整段别的编码，未强改）：无。
- fixtures 三份，解码均 OK，未改动：`data_scripts/fixtures/LeakImport.kt.fixture`、`data_scripts/fixtures/ReadOnlyAssign.kt.fixture`、`data_scripts/fixtures/修复-ZZ-门禁回归样本.md.fixture`。

## 四、分类器空跑真实输出（fix）

```
扫描范围：docs/**、仓库根 *.md/*.json、.github/**、data_scripts/fixtures/**；排除 .git/build/.gradle/.kotlin/node_modules/.idea
文件计数：md=747 json=205 fixtures=3（合计 955）
fixtures（只看不修）：3 份，解码失败 0 份 []
无可修复项。
```

## 五、复扫真实输出（rescan）

```
扫描范围：docs/**、仓库根 *.md/*.json、.github/**、data_scripts/fixtures/**；排除 .git/build/.gradle/.kotlin/node_modules/.idea
文件计数：md=748 json=205 fixtures=3（合计 956）
fixtures（只看不修）：3 份，解码失败 0 份 []
复扫结论：非 fixtures 失败 0 份
```

复扫结论：非 fixtures 失败 0 份，达到「修完复扫必须 0 失败（fixtures 除外）」的口径。

## 六、前棒已修两份历史坏文件复核

- `docs/机制-观感UX验收-回执.md`：12327 字节，strict 解码 OK。
- `docs/问题清单-附录-逐文件.md`：65758 字节，strict 解码 OK。

## 七、顺带机检真实输出

```
ACCEPT-CLAIM: PASS（39 个含「验收通过」的文件均带判据锚）
ANCHOR-INTEGRITY: PASS（锚点是最后一条处置，之后无新违规）
```

两条机检退出码均为 0。

## 八、边界与未验证

1. 本单只覆盖 `.md` / `.json`；`.kt`、`.java`、`.xml`、`.yml` 等源码与配置文件未做 UTF-8 复扫（fixture 源码样本除外，仅解码只读）。
2. 并发代理在飞新增文档导致轮次间计数漂移：首轮/空跑 955（md=747），复扫 956（md=748），中间新增 1 份 md；各轮输出以当时实跑为准，后续新增文件若接入仓库，须再跑一次 `rescan` 复核。
3. 分类器「孤立坏字节 / 整段异编码」的阈值（≤2 字节、窗口高比特 <6）只用于本轮 0 失败样本，未经过坏样本实证调参；fixtures 中的回归样本若含坏字节，属只读保留，不在修复范围。

## 守则回执

- 指纹：docs/机制-总控必读.md=6dc0592a9d44；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：`docs/机制-子代理必读.md:19`："行数、计数、字节、sha 不许估"
- 逐字引用 2：`docs/机制-验收代理必读.md:25`："别把工具差异当成缺陷"
- 逐字引用 3：`docs/机制-文档子代理必读.md:22`："再跑，再报完成（未生效不许报）。"
- 适用条款：只动文档字节、不改字句；脚本只落 /tmp 不进仓库；fixtures 只看不修；数字与计数由脚本实跑输出、不估；首轮、空跑、复扫三次真实输出均贴入报告；写码域改动先落锚点卡再出报告；未验证项显式声明。
