# 锚点卡：docs/锚点/验收-Q446-gyro三缺陷.md

> 用途：Q446 三缺陷（手势双通路 / 四向过渡 / 死字段清剿）独立验收依据。
> 被验写码报告：`docs/修复-gyro三缺陷.md`；写码锚点卡：`docs/锚点/Q446-gyro三缺陷.md`。
> 被验提交：HEAD `7cd4d66`；被验资产仅三份：`app/src/main/assets/map/gyro.js`、`app/src/main/assets/map/battle.html`、`app/src/main/assets/map/three.html`。
> 验收纪律：只读；禁改被验代码、禁 git 写、禁跑 gradlew / Android 编译；JS 只做 `node --check`、内联脚本 `vm.Script` 解析与 Node 无头推演。

## 一、事实锚点

### 手势双通路（判据 1、8）

- `app/src/main/assets/map/gyro.js:35` 来源锁 `dragSource`；`app/src/main/assets/map/gyro.js:196-219` `start/move/end` 三闭包；`app/src/main/assets/map/gyro.js:221-224` pointer 四件；`app/src/main/assets/map/gyro.js:227-234` touch 四件（含 `touchcancel` 第 234 行）。
- `app/src/main/assets/map/battle.html:339` `orbitSource` 声明；`app/src/main/assets/map/battle.html:340-358` `orbitStart/orbitMove/orbitEnd`；`app/src/main/assets/map/battle.html:360-366` pointer 四件；`app/src/main/assets/map/battle.html:367-375` touch 四件；`app/src/main/assets/map/battle.html:389-390` 顶栏淡出 poke 双通路。
- `app/src/main/assets/map/three.html:379` `dragSource/tapX/tapY` 声明；`app/src/main/assets/map/three.html:387-405` `orbitStart/orbitMove/orbitEnd`；`app/src/main/assets/map/three.html:407-413` pointer 四件；`app/src/main/assets/map/three.html:414-442` touch 四件；`app/src/main/assets/map/three.html:424-431` 双指捏合分支。
- 防双计数口径：首事件写锁，move/end 比对来源不符早退；合成内核 touch 先于 pointer 派发，第二通路早退。无头推演量级：单份偏航分量 ≈0.030，双份 ≈0.060，分界 0.045。

### 四向过渡（判据 2、3、4）

- 过渡结构 `{ from, t0, dur: 800, to }`：`app/src/main/assets/map/gyro.js:102-104`；插值分支 `app/src/main/assets/map/gyro.js:151-164`（手写四分量 lerp + normalize）。
- 目标分发 `composedTarget`：`app/src/main/assets/map/gyro.js:123-131`；follow = `slerp(gyro.q, weight) × dragOffset`，off / parallax 回归 `orbitQ`。
- 基线刷新：off 分支 `app/src/main/assets/map/gyro.js:144`；parallax premultiply 之前 `app/src/main/assets/map/gyro.js:180`。
- 起点口径：`from` 取 `camRef.quaternion.clone()`（`app/src/main/assets/map/gyro.js:103`），含 dragOffset 与已叠加视差，不取裸 `gyro.q`。
- 四向清单：off→follow、off→parallax、follow↔parallax 双向、→off；回前台恢复过渡 `app/src/main/assets/map/gyro.js:256-258` 复用同一结构。
- →off 口径：报告称 800ms 渐变回 `orbitQ`，代码须同口径，不得报告写渐变、代码硬切。

### 死字段清剿（判据 5）

- 已删对象：`gyro.active`（旧声明 / 旧 `onQuat` 写入）、`drag.q` 与 `drag` 对象整体。
- 当前声明：`app/src/main/assets/map/gyro.js:21` `var gyro = { q, weight: 0.08 }`；`app/src/main/assets/map/gyro.js:22-23` `dragOffset`、`finalQ`；`app/src/main/assets/map/gyro.js:67-70` `onQuat` 只写 `gyro.q`。
- 保留同名活字段：`app/src/main/assets/map/gyro.js:388` 权重滑杆局部 `active`（390 写、393/394 读写），不在清剿范围。
- 桥面：`app/src/main/java/io/legado/app/ui/main/BattleActivity.kt:93-104`、`app/src/main/java/io/legado/app/ui/main/PlaceMapActivity.kt:232-243` 仅暴露 `pull()/degraded()/available()`，不触碰 JS 内部字段。

### 既有能力基线（判据 6、7）

- follow 权重 0.08：`app/src/main/assets/map/gyro.js:21`；parallax 权重 0.12：`app/src/main/assets/map/gyro.js:182`；降级锁 0.04：`app/src/main/assets/map/gyro.js:84-85`；档位表 `app/src/main/assets/map/gyro.js:39`；四金点 `app/src/main/assets/map/gyro.js:297-301`；回弹时间常数 0.18s：`app/src/main/assets/map/gyro.js:171-174`。
- 拾取：`app/src/main/assets/map/three.html:444-449` NDC 仅用客户坐标；缩放：`app/src/main/assets/map/three.html:417-420` wheel、`app/src/main/assets/map/battle.html:376-379` wheel，均不读拖拽状态。
- 跟随模式轨道短路：`app/src/main/assets/map/battle.html:349`、`app/src/main/assets/map/three.html:395`。

## 二、验收判据（九条逐条）

1. 双通路来源锁：pointer / touch 两监听齐备，move/end 按来源早退；三页同构；合成双通路场景单次拖动只计一次（无头双向推演量级落单份）。
2. 四向 800ms 过渡：off→follow、off→parallax、follow→parallax、parallax→follow、follow→off、parallax→off 均起过渡；起点为当前合成相机姿态（含 dragOffset 与视差叠加）。
3. 过渡中途再切：新 `from` 取当前中间帧姿态，不跳回旧起点；过渡对象整体替换，不叠加二次过渡超调。
4. →off 落地口径与报告措辞一致（渐变 / 硬切二者相符）。
5. 死字段：`gyro.active`、`drag.q`、`drag` 对象在 assets 内零残留读取，Kotlin 桥不引用；滑杆局部 `active` 不误判。
6. 能力零回退：0.08 / 0.12 / 0.04 锁、四金点 UI 与三档步进、回弹仍在，逐项供证行号。
7. 拾取与缩放未被新手势污染。
8. 单指拖拽与双指缩放并存，互不抢占。
9. 报告自报行号抽查 ≥6 处逐字回源，命中率记具体数字。

## 三、边界与遗留

- 沙箱无 gradle/JDK/SDK：编译面、单测面以云端 CI 为基线；本单零 Kotlin 改动。
- 真机 WebView 对 pointer/touch 合成与派发顺序的实际表现、传感器漂移与手感，列入未验证项，需上机复核。
- 写码方自报的「无头冒烟 12 项」脚本未随资产入库，验收方以自有 Node 桩推演替代，不采信其自评结论。
