# 复盘 · CI 九连红到首次全绿：8 例失败根因清单（2026-11-24）

> 里程碑：GitHub Actions run `45404217394`（commit `a978f15`）conclusion=success：`BUILD SUCCESSFUL`，`e: file` 计数 **0**，3185 个单测全过；产物 `legado-debug-apk`(97,949,067 B) + `legado-release-apk`(80,581,653 B) 均已生成。
> 口径：事实逐条照抄本轮 CI 记录；本文件只做台账与根因沉淀，不改代码。

## 一、九次连红时间线

| # | commit | run id | 阶段 |
|---|---|---|---|
| 1 | `f7a0e48` | — | 编译红 |
| 2 | `4508967` | — | 编译红 |
| 3 | `f08c4ba` | — | 编译红 |
| 4 | `d264ff6` | 45394901115 | 编译红 |
| 5 | `e53809e` | 45395929049 | 编译红 |
| 6 | `8d4e604` | 45396075015 | 编译红 |
| 7 | `f67ca9f` | 45396298409 | 编译红（机械守卫沉淀） |
| 8 | `2ff867d` | 45397719262 | 编译清零 → 暴露 8 个单测失败 |
| 9 | `82e1afc` | 45398476801 | 编译域 0 条 `e:`，3185 测试 8 failed |
| 全绿 | `a978f15` | 45404217394 | 8 例三路根因修复 → 全绿 |

## 二、三段收敛

1. `2ff867d`：修 5 个测试文件编译错（可空 map 键、`Double.isNaN(x)` 静态误用、反引号函数名含 `/`、`springFor<Float>` 显式类型、`encodePng` 缺 `pixel=` 具名实参）→ 编译清零，暴露 8 个单测失败。
2. `82e1afc`：PNG 扫描行 filter 字节 `Int`→`ByteArray` 需 `toByte()` → 编译域彻底干净（0 条 `e:`），3185 测试 **8 failed**。
3. `a978f15`：8 个失败用例三路根因修复 → **全绿**（3185 测试 0 failed）。

## 三、8 例失败根因表

| 用例 | 断言位置 | 根因（一句话） | 修法 | 测试自身笔误 |
|---|---|---|---|---|
| `CampaignBranchIsolationTest` B7-1 | 分支存档隔离用例 | 规则 7（兵力三倍→ADVANCE 压迫）与规则 9（未持关隘+敌远→SEIZE 抢关隘）**未排除 HOLD 意图**，驻点守军首回合被机会主义机动拖离胜利点（仿真：移 2 格、距点 9.47 km > 3 km 半径），`pointHolders`/`captureHold` 落空 | 两规则加 `corps.intent != Intent.HOLD` 闸门；据守建制只在相邻接敌/溃散/被围时易地 | 否（生产逻辑缺约束） |
| `CampaignBranchIsolationTest` B7-2 | 同上 | 同 B7-1 同一根因 | 同 B7-1 | 否（生产逻辑缺约束） |
| `CampaignBranchIsolationTest` B7-3 | 同上 | 同 B7-1 同一根因 | 同 B7-1 | 否（生产逻辑缺约束） |
| `CampaignBranchIsolationTest` B7-6 | 同上 | 同 B7-1 同一根因 | 同 B7-1 | 否（生产逻辑缺约束） |
| `CampaignControllerTest` B9-7 | 遭遇感知用例 | 同格 2v1 两场遭遇把 `b1` 士气连降两级至 BROKEN 当场解散 → 掉出感知集（`count=0`，不是重复） | 新增回合级 `moraleDowngradedThisRound` 集合，一回合每军至多降一级（`step()`/`restoreBranch()` 清零；事件直置 BROKEN 不受影响）+ `perceivedEnemies` 末尾 `distinctBy { it.id }` 兜底 | 否（生产逻辑缺约束） |
| `HeightMapDecoderTest` M2 | 解码器行优先映射用例 | **测试夹具笔误**：`encodePng(1, 2, …)` 宽 1 时 lambda 里用 `x` 区分两行（`x` 恒为 0） | 夹具改判 `y`；解码器行优先映射本身正确 | **是**（夹具坐标轴写错） |
| `HeightMapWiringTest` M5 | 1×1 灰度 PNG 接线用例 | 1×1 合法灰度 PNG 的 base64 仅 92 字符 < `BASE64_MIN_LENGTH=200`，`classify` 误判 `ASSET` → reader=null → 回落合成场 `degraded=true` | `classify` 增「短串 base64 解出后命中 PNG 八字节签名（89 50 4E 47 0D 0A 1A 0A）」探针，抽 `isBase64Charset`，字节按无符号 Int 比对；验不出签名仍走 ASSET 兜底 | 否（生产逻辑缺约束） |
| `BookSourcePartHasJsTest` | 源码扫描型断言 `strings.xml` 的 `<string name="js_source_badge">JS</string>` | 该资源已被「清孤儿字符串」删掉（实现硬编码 `"JS"`），扫描断言过时落空 | 走资源化：`strings.xml:1045` 补回 + `SourceManageRow.kt:111` 改 `stringResource(R.string.js_source_badge)`（`MetaRow` 本就是 `@Composable`）；测试同步改扫描串；孤儿扫描回归 0 | **是**（断言过时；实现侧同步补回资源化） |

## 四、结论与教训

- **8 例分类**：**2 例是测试自身写错**（M2 坐标轴、JS 角标断言过时），**6 例是生产逻辑缺约束**（B7 四条 HOLD 闸门 + B9-7 士气节流 + M5 短图签名探针）。
- **编译通过 ≠ 逻辑通过**：编译红修完后，语义红藏在 test 阶段后面——`2ff867d` 编译清零才首次暴露 8 个运行期失败。修红必须一次跑到 test 阶段真绿再报完成：`BUILD SUCCESSFUL` + `e:` 为 0 + `tests failed` 为 0，编译绿只是中间态。
- **同轮顺带闭环**：`AndroidManifest.xml:22-26` 的 `WRITE_EXTERNAL_STORAGE` 补 `android:maxSdkVersion="28"`（卡片 12 类验收判据 7 的宿主侧挂账）。
