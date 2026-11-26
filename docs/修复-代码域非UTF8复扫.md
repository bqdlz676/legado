# 修复-代码域非 UTF-8 复扫

- 日期：2026-11-26
- 范围：代码与配置后缀严格 UTF-8 解码复扫；fixtures 只读不修；不改字句，只动坏字节。
- 锚点卡：docs/锚点/P34-代码域非UTF8复扫.md
- 一次性脚本：`/tmp/utf8_scan_code.py`（只落 /tmp，未进仓库；三模式 `scan` / `fix` / `rescan`）。
- 前序文档域复扫：`docs/修复-全仓非UTF8复扫.md`（.md/.json 已归零）。

## 一、范围与方法

1. 扫描后缀与路径：`app/**/*.kt`、`modules/**/*.kt`、`data_scripts/**/*.py`、`*.yml/*.yaml`、`*.xml`、`*.gradle*`（含 `.gradle.kts`）。
2. 排除目录：`.git`、`build`、`.gradle`、`.kotlin`、`node_modules`、`.idea`。
3. 解码判据：`data.decode("utf-8", errors="strict")`，非法序列抛 `UnicodeDecodeError`，记录首败偏移 `e.start` 与前后 40 字节 repr。
4. 分类器沿用文档域阈值：单文件删除 ≤2 字节、失败窗口 20 字节内高比特 <6 判孤立坏字节；高比特密集或超阈值判「整段别的编码」，不强改，单列人工判定。阈值若出现误报，须在报告记明样本与调整值，不静默放宽。
5. fixtures（`data_scripts/fixtures/**`）不论后缀扫描，解码结果单列，禁止修复。

## 二、首轮扫描真实输出（scan）

```
扫描范围：app/**/*.kt、modules/**/*.kt、data_scripts/**/*.py、*.yml/*.yaml、*.xml、*.gradle*；排除 .git/build/.gradle/.kotlin/node_modules/.idea；fixtures 只读不修
文件计数：kt=2138 py=76 yml=10 xml=593 gradle=11 fixtures=3（合计 2831）
fixtures（只看不修）：3 份，解码失败 0 份 []
扫描结论：非 fixtures 失败 0 份 → PASS（首轮 strict 解码 2828 份非 fixtures 文件，0 失败）
```

分组/目录分布：
- kt：app 2120、modules 18，合计 2138。
- py：data_scripts 76。
- yml：.github 8、docs 1、modules 1，合计 10。
- xml：app 590、modules 2、screenshot 1，合计 593。
- gradle：仓库根 2、.github 3、app 3、modules 2、screenshot 1，合计 11。

## 三、分类与处置清单

- 修复文件清单（孤立坏字节就地删除）：无。本轮删除字节 0 个，删除偏移清单为空 → PASS（本轮无字节删除）。
- 需人工判定（整段别的编码，未强改）：无 → PASS（无异编码段转人工）。
- 阈值误报记录：本轮 0 失败样本，分类器未触发，阈值（≤2 字节、窗口高比特 <6）无需调整；沿用文档域原值 → PASS（阈值未触发，无调整留痕项）。
- fixtures 三份，解码均 OK，未改动：`data_scripts/fixtures/LeakImport.kt.fixture`、`data_scripts/fixtures/ReadOnlyAssign.kt.fixture`、`data_scripts/fixtures/修复-ZZ-门禁回归样本.md.fixture` → PASS（3/3 fixtures strict 解码 OK）。

## 四、分类器空跑真实输出（fix）

```
扫描范围：app/**/*.kt、modules/**/*.kt、data_scripts/**/*.py、*.yml/*.yaml、*.xml、*.gradle*；排除 .git/build/.gradle/.kotlin/node_modules/.idea；fixtures 只读不修
文件计数：kt=2138 py=76 yml=10 xml=593 gradle=11 fixtures=3（合计 2831）
fixtures（只看不修）：3 份，解码失败 0 份 []
无可修复项。 → PASS（分类器空跑，0 候选）
```

## 五、复扫真实输出（rescan）

```
扫描范围：app/**/*.kt、modules/**/*.kt、data_scripts/**/*.py、*.yml/*.yaml、*.xml、*.gradle*；排除 .git/build/.gradle/.kotlin/node_modules/.idea；fixtures 只读不修
文件计数：kt=2138 py=76 yml=10 xml=593 gradle=11 fixtures=3（合计 2831）
fixtures（只看不修）：3 份，解码失败 0 份 []
复扫结论：非 fixtures 失败 0 份 → PASS
```

复扫结论：非 fixtures 失败 0 份，达到「修完复扫必须 0 失败（fixtures 除外）」的口径 → PASS。

## 六、范围外文件与未覆盖后缀

### 6.1 同后缀但路径不在本单口径内（未扫描）

- `.py` 5 份：`.github/scripts/lzy_web.py`、`.github/scripts/prepare-pdf-zoom-fixture.py`、`.github/scripts/tg_bot.py`、`.github/scripts/verify-cronet-dynamic.py`、`app/src/main/assets/fonts/subset_generate.py`。
- `.kt` 5 份：`screenshot/src/test/java/io/legado/app/ci/screenshot/` 下 5 份快照测试与 `ScreenshotFixtures.kt`。

### 6.2 未覆盖的文本类后缀（本轮未做 UTF-8 判定）

- `.java` 99、`.sh` 26、`.js` 46、`.vue` 21、`.css` 20、`.html` 15、`.dtd` 5、`.ent` 7、`.mod` 36、`.properties` 4、`.pro` 4、`.sql` 1、`.toml` 1、`.bat` 1、`.rb` 1、`.mjs` 1、`.cjs` 1、`.pom` 1，另含 `.orig`、`.sha256`、`.log` 各 1。
- `.geojson` 20 份属地理数据文本，未在本单后缀口径内。

### 6.3 二进制资产（不适用 UTF-8 文本解码判定）

- 图片/字体/压缩包/固件类：`.png`、`.jpg`、`.ttf`、`.otf`、`.woff`、`.jar`、`.epub`、`.pdf`、`.mp3`、`.zip`、`.tgz`、`.jks`、`.bin`、`.ico`、`.pyc` 等，未参与 strict 解码。

## 七、边界与未验证

1. 本单判据只认 strict UTF-8；GBK/GB18030 等本地编码源码若存在，会被首轮判为失败并转人工，本轮未出现此类样本 → 未验证（无坏样本可实证）。
2. 代码域只做字节级解码检查，未编译、未跑测试；坏字节即使删除也需编译与单测复核，本轮无删除故不涉及 → 未验证（编译/单测未执行）。
3. 并发代理在飞新增的代码/配置文件以本单末次扫描时点为准；后续新增文件须再跑一次 `rescan` 复核 → 未验证（时点之后的新增文件不在本轮计数内）。
4. 分类器阈值未经坏样本实证调参；若后续扫出真坏字节，须先核对窗口高比特分布再决定删除或转人工，不得凭单字节表象强改 → 未验证（阈值防误报/漏报能力未经坏样本检验）。

## E1 命令证据（实跑命令与退出码）

下列命令均为本单实跑，退出码取自终端真实返回。/tmp 一次性脚本不属 `data_scripts/` 门禁入口，门禁机检命令单列在后。

- `python3 /tmp/utf8_scan_code.py scan` → rc=0（首轮扫描，非 fixtures 失败 0 份）
- `python3 /tmp/utf8_scan_code.py fix` → rc=0（分类器空跑，无可修复项）
- `python3 /tmp/utf8_scan_code.py rescan` → rc=0（复扫，非 fixtures 失败 0 份）
- `grep -n "P34-代码域非UTF8复扫.md\|kt=2138 py=76\|复扫结论\|删除字节 0\|f103a149eb76" docs/修复-代码域非UTF8复扫.md` → rc=0（关键字回证命中）
- `python3 data_scripts/check-evidence.py docs/修复-代码域非UTF8复扫.md` → rc=0（E1/E2/E3 三件套 PASS）
- `python3 data_scripts/check-onboarding.py` → rc=0（必读指纹与三条引用机检 PASS）
- `python3 data_scripts/check-anchor-card.py --strict docs/修复-代码域非UTF8复扫.md` → rc=0（锚点卡反查 PASS；F8 次要提及为 WARN，不计 FAIL）
- `python3 data_scripts/check-accept-claim.py` → rc=0（PASS，43 个含「验收通过」的文件均带判据锚）
- `python3 data_scripts/check-anchor-integrity.py` → rc=0（PASS，锚点是最后一条处置，之后无新违规）

## E3 结果标记汇总

- 首轮扫描 2828 份非 fixtures 文件 strict 解码：PASS（0 失败）。
- 孤立坏字节修复：PASS（删除 0 字节，偏移清单为空）。
- 整段异编码人工判定：PASS（0 例）。
- fixtures 3 份只读解码：PASS（3/3 OK）。
- 复扫归零：PASS（非 fixtures 失败 0 份）。
- check-evidence 证据三件套：PASS。
- check-onboarding 必读指纹与引用机检：PASS。
- check-anchor-card 锚点卡反查：PASS（1 条 WARN 次要提及，非 FAIL）。
- check-accept-claim：PASS；check-anchor-integrity：PASS。
- 编译/单测复核：未验证；阈值坏样本实证：未验证；时点之后新增文件：未验证。

## 守则回执

- 指纹：docs/机制-总控必读.md=f103a149eb76；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：`docs/机制-子代理必读.md:19`："行数、计数、字节、sha 不许估"
- 逐字引用 2：`docs/机制-验收代理必读.md:25`："别把工具差异当成缺陷"
- 逐字引用 3：`docs/机制-文档子代理必读.md:22`："再跑，再报完成（未生效不许报）。"
- 适用条款：只动文档/字节不改字句；脚本只落 /tmp 不进仓库；fixtures 只读不修；数字与计数由脚本实跑输出、不估；首轮、空跑、复扫三次真实输出均贴入报告；写码域改动先落锚点卡再出报告；阈值调整须留痕不静默放宽；未验证项显式声明。

## 新发现问题（2026-11-26 追补，F8）

1. 本单只按后缀白名单扫，同后缀但在白名单外仍有 5 份 `.py`、5 份 `.kt` 未覆盖 —— 已入队：下一单按"全仓文本后缀"而非目录白名单取文件。
2. 未扫文本后缀 20 类（.java 99、.sh 26、.js 46、.vue 21 等）体量约 300 份，仍是盲区 —— 待裁：是否把非 UTF-8 检查并入门禁 49/51 家族，做成常驻门禁而非一次性报告。
3. 阈值（≤2 字节、窗口高比特 <6）未用坏样本调参，本轮 0 失败等于未证伪 —— 已入队：Q-221 之后补坏样本夹具。
