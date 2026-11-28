# 锚点卡 · 星空 Sheet 判据 4/12 与 N1 反查索引

> 用途：本单写码前置锚点（F-85）。逐字引文，禁转述。本单只做 Sheet 卡片回退 / 哨兵抑制 / 关闭快照测试与星点 ↔ 分区 code 双向反查索引；不越界改宿主、控制器、渲染器与既有测试。

## 一、逐字引文（文件:行）

- `docs/验收-星空天象层.md:85`：「距离哨兵未显式抑制：`SkySheet.kt:176` 守卫只判 `it.isFinite()`；哨兵 `100000.00` 是有限值，不被该守卫拦下。CSV 中 205 行距离取 `100000.00`；KDoc 声称「空/哨兵距离不渲染距离行」（`SkySheet.kt:38`），但代码未实现哨兵判等/判空。」
- `docs/验收-星空天象层.md:147`：「① 选中 id 关闭后置 null，与规格 §七 末段「选中态一字不差 / 选中 id 逐位相等」口径不符——实现选择了关闭即取消选中；② 无关闭前后快照比对测试；」
- `docs/验收-星空天象层.md:148`：「备注：代码内相机状态是 `CameraYawPitch`（yaw/pitch 双标量），规格文字称「四元数」；当前无四元数结构，快照比对口径需在测试落地时统一。」
- `docs/验收-星空天象层.md:183`：「N1（待裁，并入 D39）：正向分野触发链运行时不可达。`AsterismLayer.select(code)` 零调用方，缺 starId→asterismCode 反查索引；`FieldAllocationLayer.selectedRegion` 恒 null，`FieldGlowOverlay` 恒早退。建议修法：补 starId→code 反查映射，点选星后解析其所属星官并 `select(code)`，再接 `regionForAsterism` 脉冲。」
- `docs/验收-星空天象层.md:73`：「缺星 id → 星官 code 的反查索引（`memberIdsByAsterism` 只有 code→members 单向，`StarStore.kt:31`）。」
- `docs/星空天象层-实施规格-合并定稿.md:217`（§七 跨态约束）：「Sheet 关闭后选中态与视角一字不差；判据：关闭前后相机四元数与选中 id 逐位相等。」
- `docs/星空天象层-实施规格-合并定稿.md:314`（§十四.4）：「Sheet 三名字段齐全（有则显示，无距离不显示距离行）」
- `docs/星空天象层-实施规格-合并定稿.md:322`（§十四.12）：「Sheet 关闭前后相机四元数与选中 id 逐位相等」
- `docs/方案-剩余缺陷施工图-星空天象层.md:514`：「关闭只清 selectedId，相机与姿态一字不动（§十四.12）」

## 二、改动落点（全路径）

- `app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt`：标题三级回退链、距离哨兵具名常量与守卫。
- `app/src/main/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndex.kt`：code ↔ starId 双向反查索引（纯 JVM）。
- `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/AsterismLayer.kt`：装载期重建索引，暴露 `codeOfStar` / `membersOfCode`。
- `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt`：线段端点重建索引，暴露 `constellationOfStar` / `starsOfConstellation`。
- 测试：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt`、`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt`、`app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkySheetDismissStateTest.kt`。

## 三、判据（机械可判）

1. 标题回退任一路返回可读非空串，不含字面 `null` / 空白；哨兵 `100000.00`、null、NaN 距离不渲染距离行。
2. 关闭前后相机 yaw / pitch / zoom（距离比）三元组逐位相等，`selectedId` 回落 null。
3. code → 成员集非空；成员逐颗 `codeOf` 回查同一 code；未知 code 返回空列表，未知星返回 null。
4. 编译 / 单测以云端 CI 为唯一判据（沙箱禁本地编译）。
