> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

# 验收 · S-03 设计 token 单源（批 0）

> 锚点卡：docs/锚点/S-03-设计token.md
> **验收方式声明（诚实交代）**：本单独立验收连续两次失败（首派 s_00074yPfdif 跑题判废、二派 s_00074yPhi5g 3 秒退化判废），按"同一任务重派上限 2 次，第三次改调度层自己做或拆更小单"的既定规则，本轮由调度层做**机械逐项核对**（只跑命令对原文，不做主观美化评价）。**结论标注为"机械核可，待独立复验"**，不冒充独立验收。

## 一、12 个 token 值（逐字对锚点卡参数表）

E1（真跑命令）：
```
$ grep -nE "const val (BG_PAGE|...|COLOR_GLOW)" app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt
19: BG_PAGE: Long = 0xFFF7F1E6
22: BG_SURFACE: Long = 0xFFEFE8D8
25: BG_OVERLAY: Long = 0x731A1410
28: TEXT_PRIMARY: Long = 0xFF1A1410
31: TEXT_SECONDARY: Long = 0xFF5C4F3A
34: TEXT_TERTIARY: Long = 0xFF9B9687
37: TEXT_ACCENT: Long = 0xFFC23B2E
40: BRAND_GOLD: Long = 0xFFD4A54A
43: BRAND_COPPER: Long = 0xFF8B6914
46: COLOR_LINK: Long = 0xFFC23B2E
49: COLOR_DIVIDER: Long = 0x141A1410
52: COLOR_GLOW: Long = 0x4DD4A54A
```
E2/E3：`app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt:19-52`，与锚点卡「bg-page #F7F1E6 … color-glow rgba(212,165,74,.3)」逐项相等（rgba(26,20,16,.45)=0x73 alpha、rgba(212,165,74,.3)=0x4D alpha 换算正确）。`TEXT_ACCENT` 与 `COLOR_LINK` 各自独立声明，未合并。
**判定：通过（12/12）。**

## 二、是否存在第二套"同值单源"

E1：
```
$ grep -rn "0xFFF7F1E6\|0xFFD4A54A" app/src/main/java/io/legado/app/ | wc -l
24
```
E2：命中分布在 `ui/components/ComponentPalette.kt`（单源）与 7 个旧副本 token 对象（`ui/book/read/ink/InkTokens.kt`、`ui/card/CardTokens.kt`、`ui/main/bookshelf/ShelfTokens.kt`、`ui/main/chrono/ChronoTokens.kt`、`ui/main/explore/InkExploreTokens.kt`、`ui/main/search/SearchTokens.kt`、`ui/welcome/LuowenSplashTokens.kt`）。
**判定：通过（"不得新建第二套单源"这一条成立：`app/src/main/java/io/legado/app/design/` 已无残留，`ls` 返回 No such file or directory；旧副本属存量基线，已加 `TODO(D23 收口)` 注释指向单源）。但**遗留收口**：24 处同值仍存在，D23 批次必须逐个转发 ComponentPalette。**

## 三、护栏单测是否有效（含 6 位 hex 后门）

E1：
```
$ grep -rEc "\b0x[0-9A-Fa-f]{6}\b" app/src/main/java/io/legado/app/ui/ | grep -v ":0" | wc -l
3
```
E2：3 个文件仍含 6 位 hex；护栏正则只覆盖 8 位 ARGB（`0x[0-9A-Fa-f]{8}`），**6 位色值是后门**。
E3：`docs/立项-补条款-第二批.md` 与写码方回执均已如实登记"6 位 hex 未纳入本轮"。
**判定：返工（护栏不闭合）。** 要求：下批把正则扩到 `0x[0-9A-Fa-f]{6}` 与 `0x[0-9A-Fa-f]{8}` 双形态，并把 3 个文件的 6 位值纳入基线清单。

## 四、CI 实证（本单真正的硬门槛）

run `45453607335`（commit `0c6c933`）= **failure**，归因见 `docs/CI归因-45453607335.md`：
E1/E2：`app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt:25:9` 编译错「Name contains illegal characters: :.」——反引号函数名 `anchor contract: 12 colors …` 里带冒号，JVM 方法名非法。
处置：已改名去掉冒号（`anchor contract 12 colors 4 easings 5 durations match frozen source`），并断言全文件无其它带 `:` 的反引号函数名。**判定：返工中（修复已提交，等待下一轮 CI 证实）。**

## 五、结论

- 通过 3 条（token 值 12/12、无双单源、CI 终态绿）；
- 返工 1 条（6 位 hex 后门，落 D23）；CI 红已三次修完并终态绿（见第六节）；
- 判废 0 条。
- 改进点 3：①护栏正则覆盖 6 位 hex；②基线 39 文件 186 处存量必须进 D23 排期，否则"豁免=永久合法化"；③`STONE_BLUE`（石青）是 12 色外增补，须回 §1.3 正式补登，否则"12 项对得上"的判据本身有缝。

## 六、CI 复验结果（2026-11-25 终态）

三次修红链路（全部已推云端，逐轮取 CI 终态）：
- `0c6c933` → run `45453607335`：**failure**（编译错：反引号测试函数名含 `:`，「Name contains illegal characters: :」，`DesignTokenGuardTest.kt:25`）。
- `43c8086` → run `45454989707`：**failure**（单测错：护栏基线路径未拼 `io/legado/app/ui/` 前缀，`relativize` 结果与基线清单永远对不上 → 全量误报 `DesignTokenGuardTest > ui layer has no inline hex color outside the legacy baseline`）。
- `d07e33e`（第二修后引入重复 `companion object`，随即 `baab3b1` 合并）→ run `45458972475`：**completed / success**，含新护栏单测的 777 项单测全绿。

**结论更新：S-03 批 0 判"通过（CI 已证实）"**；唯一遗留为 6 位 hex 后门（已列 D23 待办，不阻塞本单）。
