# 交付 · APK 静态自检（commit 98ee6d0）

> 生成时间：2026-11-23 19:xx（UTC+8）。本文件只记可复核的事实与证据，不记推断。
> 结论一句话：**签名与既定 keystore 完全一致、可覆盖安装；测试全绿；但 3D 沙盘地形当前是平地形（DEM 资产进包却零读取），属必修缺陷。**

## 1. 云端 CI

| 项 | 值 |
|---|---|
| workflow | Build Debug APK |
| run id | `45160352436` |
| commit | `98ee6d0`（fix(test): 修 CI 七条运行期断言失败） |
| 结论 | `success`（本项目首次全绿） |
| 步骤 | Clean success → Run all module unit tests and benches **success** → Build debug APK success → Build release APK success → Upload 双 artifact success |
| 测试 | 2736 tests，0 failed（日志判定，未见 failed 断言） |

## 2. 产物

| 包 | 绝对路径 | 大小 |
|---|---|---|
| release | `/home/user/Doubao/chats/38454122521460994/dist/rel/app/release/app-app-release.apk` | 90,156,299 B |
| debug | `/home/user/Doubao/chats/38454122521460994/dist/dbg/app/debug/app-app-debug.apk` | 107,620,466 B |

## 3. 签名（覆盖安装前提，逐位核对）

`keytool -printcert -jarfile <apk>` 输出，两包一致：

```
Owner: CN=Luowen, OU=Doubao, O=Luowen, L=Nanchong, ST=Sichuan, C=CN
SHA256: B6:2D:93:43:90:A2:36:CD:AE:29:46:BF:B1:5A:3F:2E:93:14:15:6C:BF:55:EA:DB:76:9A:1D:0F:B6:E1:45:E4
Signature algorithm: SHA256withRSA
```

与 `app/luowen.jks`（alias luowen）既定指纹**完全一致** → 可与既有安装包覆盖升级。

## 4. 包结构

- applicationId `com.legado.app`，minSdk 26 / targetSdk 36；versionName 取自 `version`（app/build.gradle:76），debug 带 `debug` 后缀。
- 二进制清单可见入口：`io.legado.app.ui.book.read.ink.InkReadActivity`、`io.legado.app.ui.main.BattleActivity`、`io.legado.app.ui.main.PaintActivity`、`io.legado.app.ui.map.globe.host.GlobeActivity`。
- 字体子集 5 枚随包：`Inter-TabularNumbers.ttf` 2,952 B、`JetBrainsMono-Regular.ttf` 6,592 B、`NotoSansSC-Light.otf` 106,648 B、`NotoSerifSC-Regular.otf` 149,760 B、`NotoSerifSC-SemiBold.otf` 151,492 B（均 ≤200KB 红线）。
- 地图/战役资产：`assets/map/china_height.png`（1024×1024 8-bit 灰度）、`assets/map/china_height.js`（1.4MB）、`assets/map/campaigns/**`（战役 JSON 已随包）。

## 5. 缺陷（必修，证据可复核）

**D-APK-1 3D 沙盘地形是平地形，真实 DEM 未被消费。**
- 证据 1：`grep -rn "china_height" --include=*.kt app/src/main/java` 无命中——Kotlin 侧没有任何代码读该资产。
- 证据 2：`TerrainElevation`（ui/map/sandbox/TerrainElevation.kt）只有 `object Flat` 一种实现，`SandboxScene.kt:19` 默认 `TerrainElevation.Flat`。
- 影响：势力色块、河流、箭头全部贴基准面，违反用户口径「3D 沙盘地形数据是真实 DEM，不许回退成假数据」。
- 修法（已登记任务）：新增 `DemTerrainElevation`（Android 侧 Bitmap 解码 china_height.png，等经纬度采样 + 双线性插值，0–255↔0–7000m），接进 `SandboxScene`；纯 JVM 侧用 fixture 数组测采样与插值，无数据回落 Flat。

## 6. 未验证项（必须明说，不许当已验证）

1. 真机（荣耀WIN 12GB）安装与覆盖升级未跑——本机无法装 APK。
2. 灵魂闭环关键路径（进书→竖排读→点字气泡→点「晋」出春秋晋国→点「鲁隐公元年」出前722己未）未在真机走通。
3. 帧率/内存红线（翻页≤16ms、球面 55fps、内存≤100MB（F-04 三档上限；旧 180MB 已作废，见 docs/拍板-2026-11-25.md 第 8 条））未在真机量。
4. APK 未做 zipalign/签名方案 v2-v4 细查（仅校验了证书指纹一致）。
5. release 包未做混淆后崩溃面验证（编译/测试绿 ≠ 混淆安全）。
</content>
