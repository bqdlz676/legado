# 锚点卡：docs/锚点/Q446-gyro三缺陷.md

> 用途：Q446 开工与独立验收依据。修复报告：`docs/修复-gyro三缺陷.md`。
> 上版基线：Q439 陀螺仪接管（`docs/锚点/Q439-陀螺仪接管.md`），云端 CI 4738fb4 全绿；本单只碰三份地图 JS/HTML 资产，不碰 Kotlin。

## 一、事实锚点

### 手势双通路（缺陷一）

- 跟随拖拽归属 `app/src/main/assets/map/gyro.js`：`bindFollowDrag` 第 193-235 行；来源锁 `dragSource` 第 35 行；pointer 四件第 221-224 行，touch 四件第 227-234 行（含 touchcancel 第 234 行）。
- 战役页轨道拖拽归属 `app/src/main/assets/map/battle.html`：`orbitSource` 第 339 行；`orbitStart/orbitMove/orbitEnd` 第 340-358 行；pointer 第 360-366 行，touch 第 367-375 行；poke 双通路第 389-390 行。
- 沙盘页轨道拖拽归属 `app/src/main/assets/map/three.html`：`dragSource/tapX/tapY` 第 379 行；`orbitStart/orbitMove/orbitEnd` 第 387-405 行；pointer 第 407-413 行，touch 第 414-441 行；捏合分支第 424-431 行。
- 防双计数判据：start 时锁来源，move/end 比对来源不符早退；touch 先于 pointer 派发，合成事件第二条通路早退。冒烟阈值：单份偏航分量 ≈0.030，双份 ≈0.060，验收取 0.045 为界。

### 四向过渡（缺陷二）

- 过渡结构 `{ from, t0, dur: 800, to }`：`app/src/main/assets/map/gyro.js` 第 103-105 行；插值分支第 152-165 行（手写四分量 lerp + normalize，仓内 three.min.js 无 `Quaternion.lerp`）。
- 目标分发 `composedTarget` 第 123-132 行：follow = `slerp(gyro.q, weight) × dragOffset`；off / parallax 回归 `orbitQ`。
- 基线刷新点：off 分支第 144 行；parallax premultiply 之前第 180 行。
- 起点口径：`from` 取 `camRef.quaternion.clone()`（第 104 行），含 dragOffset 与已叠加视差，不取裸 `gyro.q`。
- 四向清单：off→follow、off→parallax、follow↔parallax 双向、→off，均走 800ms；回前台恢复过渡第 257-259 行复用同一结构。

### 死字段清剿（缺陷三）

- 删除对象：`gyro.active`（旧第 21 行声明 / 旧第 69 行写入）、`drag.q`（旧第 22 行对象 / 旧第 189 行 copy）。
- 当前声明：`app/src/main/assets/map/gyro.js` 第 21 行 `var gyro = { q, weight: 0.08 }`；第 23-24 行 `dragOffset`、`finalQ`；`onQuat` 第 68-70 行只写 `gyro.q`。
- 保留同名活字段：权重滑杆局部 `active` 第 389 行，有读有写，不在清剿范围。
- sweep 结论：assets 内零读取；Kotlin 桥只暴露 `pull()/degraded()/available()`，不触碰 JS 内部字段。

## 二、验收判据（逐条核对）

1. 双通路：三份资产均同时注册 pointer 与 touch 通路；任一通路首事件后，另一通路 move/end 早退；无头冒烟单份量级不翻倍；pointercancel / touchcancel 都能释放锁。
2. 四向过渡：off→follow、off→parallax、follow→parallax、parallax→follow、follow→off、parallax→off 六次切换均起过渡；过渡起点是当前合成相机姿态；过渡中途再切不跳回旧起点；→off 回归 orbitQ，800ms 后 off 不再介入。
3. 死字段：源码 grep 无 `active: false`、`gyro.active`、`drag.q`、`var drag = {`；无「预留 / 占位」注释；UI 与桥无残留依赖。
4. 对称：three.html 与 battle.html 手势通路结构同构；跟随模式下两页轨道旋转仍被 `Gyro.getMode() === 'follow'` 短路；双指捏合只在 three.html 保留（battle 既有未实现，不新增）。
5. 边界：无传感器时 UI 不挂载、模式强制 off；无 pointer 的内核 touch 通路可用；拖到一半切后台 / 旋转方向，来源锁与拖拽态一并复位。

## 三、边界与遗留

- 沙箱无 gradle/JDK/SDK：禁跑编译；JS 只做 `node --check` 与内联脚本 `vm.Script` 解析；编译面与单测以云端 CI 为准。
- 真机合成顺序、传感器漂移与手感列入未验证项，需上机复核。
- 模式 / 权重不持久化；跟随模式位置仍走球面轨道。沿用 Q439 遗留，本单不扩范围。
