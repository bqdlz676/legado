锚点卡：docs/锚点/Q446-gyro三缺陷.md

# 修复报告 · Q446 陀螺仪三缺陷（手势双通路 / 四向过渡 / 死字段清剿）

- 被改文件数：3（`app/src/main/assets/map/gyro.js`、`app/src/main/assets/map/battle.html`、`app/src/main/assets/map/three.html`）
- 判据：3/3 条缺陷逐条闭环；双通路防双计数、四向过渡无跳变、死字段无占位。
- 真跑门禁结论：`node --check app/src/main/assets/map/gyro.js` PASS；两 HTML 内联脚本 `vm.Script` 解析 PASS；无头冒烟 12 项全 PASS。未跑 gradlew / JDK / SDK（沙箱禁令），Kotlin 编译与单测面以云端 CI 4738fb4 全绿为基线，本单未碰 Kotlin。
- 未验证项数：2（真机 WebView 对 pointer/touch 合成顺序的实际表现；真机传感器漂移与手感，需上机验收）。

## 守则回执：

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「报告写清这条判据对应哪份原始事实的哪一行；锚点可回源。」（docs/机制-文档子代理必读.md:16）
- 适用条款：B2 改动后 grep 回证；C3 边界自行兜底不降级不砍；删字段前 sweep 三分命中（代码引用 / 编译锁 / 注释文档）；报告判据带 `文件:行` 可回源。

## 一、缺陷一：pointer / touch 双通路，防一次拖动算两遍

### 修前证据行（HEAD 4738fb4，只读 `git show` 取证）

- `app/src/main/assets/map/gyro.js` 第 174 行：`canvas.addEventListener('pointerdown', function (e) {`
- `app/src/main/assets/map/gyro.js` 第 180 行：`canvas.addEventListener('pointermove', function (e) {`
- `app/src/main/assets/map/gyro.js` 第 193 行：`canvas.addEventListener('pointerup', release);`
- `app/src/main/assets/map/battle.html` 第 338 行：`canvas.addEventListener('touchstart', function (e) {`
- `app/src/main/assets/map/battle.html` 第 342 行：`canvas.addEventListener('touchmove', function (e) {`
- `app/src/main/assets/map/battle.html` 第 356 行：`canvas.addEventListener('touchend', function () { last = null; }, { passive: true });`
- `app/src/main/assets/map/three.html` 第 385 行：`canvas.addEventListener('pointerdown', function (e) {`；第 389 行 pointermove；第 398 行 pointerup；touch 侧第 410 行 / 第 419 行只留双指捏合，单指轨道拖动无 touch 兜底。

判死原因：gyro.js 跟随拖拽只挂 pointer 通路；battle.html 轨道拖拽只挂 touch 通路。在不把 touch 合成成 pointer 事件的旧 WebView 内核上，跟随模式临时偏移拖不动；three.html 单指轨道拖动同理无 touch 兜底。两页能力不对称。

### 修后证据行（工作区当前状态）

- `app/src/main/assets/map/gyro.js` 第 193 行起 `bindFollowDrag`：第 221 行 pointerdown、第 222 行 pointermove、第 223 行 pointerup、第 224 行 pointercancel；第 227 行 touchstart、第 230 行 touchmove、第 233 行 touchend、第 234 行 touchcancel。
- `app/src/main/assets/map/gyro.js` 第 35 行：`var dragSource = null;`（首事件来源锁）；第 197-204 行 `start`、第 205-215 行 `move`、第 216-220 行 `end`。
- `app/src/main/assets/map/battle.html` 第 339 行：`var down = null, last = null, orbitSource = null;`；第 340-343 行 `orbitStart`、第 344-354 行 `orbitMove`、第 355-358 行 `orbitEnd`；第 360-366 行 pointer 四件、第 367-375 行 touch 四件；第 389-390 行顶栏淡出 poke 同步补 pointerdown。
- `app/src/main/assets/map/three.html` 第 379 行：`var dragging = false, dragSource = null, lastX = 0, lastY = 0, tapX = 0, tapY = 0, ...`；第 387-388 行 `orbitStart`、第 390-397 行 `orbitMove`、第 399-405 行 `orbitEnd`；第 407-413 行 pointer 四件、第 414-441 行 touchstart / touchmove / touchend / touchcancel。

### 判定依据（为什么不会双计数）

- 首事件锁：手势按下的第一个事件（pointerdown 或 touchstart）把来源写进 `dragSource` / `orbitSource`；此后 move、up、cancel 都先比对来源，来源不符直接早退（`gyro.js` 第 199 行、第 206 行、第 217 行；`battle.html` 第 345 行、第 356 行；`three.html` 第 391 行、第 400 行）。
- 标准事件顺序：W3C 规定 touchstart 先于由它合成的 pointerdown 派发。同时合成两类事件的内核上，首事件落 touch，随后合成的 pointerdown 见拖拽已激活、来源不符，早退；一次拖动只累一遍偏移。
- 顺序反例兜底：即便个别内核 pointer 先到，锁落 pointer，后到的 touchstart 同样早退；锁与先后顺序无关，只认「第一条通路」，两条通路不可能同时计数。
- 多指隔离：第二指按下时首事件锁已激活，新 touchstart 早退，不切换来源；双指捏合仍走页面既有 touchmove 两指分支（`three.html` 第 424-431 行；`battle.html` 未实现捏合缩放，属既有范围，本单不新增）。

### 实测方式（可复现）

- 无头冒烟（Node + 桩 THREE.Quaternion + 桩 GyroBridge）：同一手势先后派发 touchstart 与 pointerdown、touchmove 与 pointermove，单通路 10px 偏航对应四元数轴分量 ≈ sin(0.03) ≈ 0.030；双计数应到 ≈ sin(0.06) ≈ 0.060。实测偏移量落在 0.030 一档，未到双份阈值 0.045；反向用例先 pointerdown 后 touchstart，touch move 同样早退，偏移量未超阈值。冒烟脚本 12 项断言全 PASS。
- 真机复核项：在不合成 pointer 的旧内核上验证 touch 通路单指可拖；在 Chromium 内核上验证合成事件下偏移不翻倍。此项列入未验证项，需上机。

## 二、缺陷二：follow ↔ parallax ↔ off 四向切换统一 800ms 过渡

### 修前证据行（HEAD）

- `app/src/main/assets/map/gyro.js` 第 100 行：`if (mode === 'off' && next !== 'off' && camRef) {`——只有 off→follow / off→parallax 起过渡；follow↔parallax 直接切姿态，→off 直接释放，两向都存在单帧跳变。
- `app/src/main/assets/map/gyro.js` 第 119-121 行：旧 `targetQuat()` 只按 `mode === 'parallax'` 取 `baseline` 或 `gyro.q`，过渡起点 / 目标都不含手指临时偏移 dragOffset。

### 修后口径（四种切换逐条）

过渡结构统一为 `{ from, t0, dur: 800, to }`，落点 `app/src/main/assets/map/gyro.js` 第 103-105 行；插值仍是规格 §四的四分量线性 lerp + normalize（本仓 three.min.js 的 Quaternion 无 `lerp` 方法），落点第 152-165 行。

1. off→follow：从当前相机合成姿态渐变到 follow 目标 `slerp(gyro.q, weight) × dragOffset`（`gyro.js` 第 125-129 行）。
2. off→parallax：从当前相机合成姿态渐变到轨道基线 `orbitQ`；叠加视差层在过渡结束后的稳态帧再 premultiply 上去（`gyro.js` 第 178-185 行）。
3. follow↔parallax（双向）：同样起 800ms 过渡，起点取切换瞬间相机姿态，目标按新模式算；过渡中途再切模式，新 `from` 取当前中间帧姿态，不跳回旧起点。
4. →off：走 800ms 渐变回到轨道基线 `orbitQ`，不做立即硬切。

### →off 为什么渐变而不是立即回中

- 立即释放的代价：follow / parallax 稳态下相机姿态被陀螺仪四元数覆盖，而宿主页面的轨道姿态（battle.html 的 cam.yaw/pitch、three.html 的 rotY/rotX）只驱动位置与 lookAt；若切 off 当帧停止介入，残留的陀螺仪姿态与宿主 lookAt 姿态之间会出现一帧跳变，正属验收口径禁止的单帧跳变。
- `orbitQ` 是回归目标：off 模式每帧把宿主自己算出的相机四元数记入 `orbitQ`（`gyro.js` 第 144 行）；parallax 稳态在 premultiply 视差之前把去视差前姿态记入 `orbitQ`（`gyro.js` 第 180 行）。→off 即从当前姿态 lerp 回这条轨道基线，800ms 后 off 完全释放，本文件不再介入镜头。
- 语义边界：这 800ms 是一次性归还控制，不是 off 模式下持续接管；过渡结束后 off 分支仍每帧只记 `orbitQ`、清 `dragOffset`，不写相机姿态（`gyro.js` 第 142-147 行）。

### 过渡起点为什么取合成后四元数（含 dragOffset）

- `from` 在 `setMode` 里直接 `camRef.quaternion.clone()`（`gyro.js` 第 104 行）。上一帧稳态产物：follow 是 `finalQ = slerp(gyro.q, weight) × dragOffset` 后拷给相机（`gyro.js` 第 169-177 行），parallax 是视差 premultiply 后的相机姿态。克隆相机姿态即把拖动临时偏移、已叠加视差一并带走；若只取裸 `gyro.q`，拖到一半切模式，临时偏移被丢掉，姿态会往陀螺仪裸值跳一步。
- 无相机引用的边界：`camRef` 尚未 attach 时不建过渡，直接切模式；首帧 attach 后 off 分支即开始记基线，不会悬空（`gyro.js` 第 140 行、第 403 行 `attach`）。

### 修后证据行

- `app/src/main/assets/map/gyro.js` 第 95-108 行 `setMode`：去掉 `mode === 'off'` 前置条件，四种切换统一建过渡。
- `app/src/main/assets/map/gyro.js` 第 123-132 行 `composedTarget(targetMode)`：过渡 / 稳态共用目标四元数计算。
- `app/src/main/assets/map/gyro.js` 第 144 行、第 180 行：`orbitQ` 两个刷新点。
- `app/src/main/assets/map/gyro.js` 第 257-259 行：回前台恢复过渡沿用同一结构，`to: mode` 交 `composedTarget` 分发。

## 三、缺陷三：死字段 `gyro.active` 与 `drag.q` 清剿

### 修前证据行（HEAD）

- `app/src/main/assets/map/gyro.js` 第 21 行：`var gyro = { q: new THREE.Quaternion(), active: false, weight: 0.08 };`
- `app/src/main/assets/map/gyro.js` 第 22 行：`var drag = { q: new THREE.Quaternion() };      // 单次拖动的增量四元数（规格符号保留）`
- `app/src/main/assets/map/gyro.js` 第 69 行：`gyro.active = true;`（只写不读）
- `app/src/main/assets/map/gyro.js` 第 189 行：`drag.q.copy(yawQ);`（只 copy 不读）

### 处置决策：删除，不接线占位

- sweep 三分命中结果：代码引用——全仓 `app/src/main/assets/map/` 除 gyro.js 自身外，battle.html / three.html / 其余 JS 零读取；编译锁——本单为纯 JS 资产，无 Kotlin 编译符号引用；注释文档——`docs/验收-复核-陀螺仪接管.md` 第 125 行 N3 条已登记这两个字段为死赋值，建议摘除或补消费方；`docs/实现-陀螺仪接管沙盘.md` 第 43 行的状态量清单为上版留档（该行已附注 Q-446 在飞、死字段清剿口径），属历史文档不回改（文件域限制：本单只动三份地图资产）。
- 不接线的理由：`active` 若要变活字段，需要一个「多少毫秒无新四元数则置 false」的看门狗与消费方（UI 置灰或保持旧姿态），规格未要求该语义，凭空补消费方等于造新功能；`drag.q` 与 `dragOffset` 语义重复（都是单次拖动增量），保留双份会让回弹口径分叉。故二选一取删除：两个字段、写入点、`drag` 对象整体摘除，不留 `// 预留` 占位。
- 保留区分：`gyro.js` 第 389 行权重滑杆局部变量 `active` 是活字段（第 390-397 行有读有写），与死的 `gyro.active` 同名不同物，保留未动。

### 修后证据行

- `app/src/main/assets/map/gyro.js` 第 21 行：`var gyro = { q: new THREE.Quaternion(), weight: 0.08 };`（无 `active`）
- `app/src/main/assets/map/gyro.js` 第 23-24 行：`dragOffset`、`finalQ`；`drag` 对象已不存在。
- `app/src/main/assets/map/gyro.js` 第 68-70 行 `onQuat`：只写 `gyro.q`，无 `active` 赋值。
- 源码残留扫描命令与结果见下一节。

## 四、grep 回证（命令 + 命中行号）

### 修前串已消失

命令（在仓库根目录执行）：

```
grep -n "active: false\|gyro\.active\|drag\.q\|var drag = {\|targetQuat" app/src/main/assets/map/gyro.js
grep -n "mode === 'off' && next" app/src/main/assets/map/gyro.js
```

结果：两条命令均无命中（exit=1），旧声明、旧写入、旧对象、旧目标函数、旧过渡条件在工作区零残留。

### 修后串已落盘

```
grep -n "var gyro = \|var orbitQ\|var dragSource\|function setMode\|function composedTarget\|orbitQ.copy\|function bindFollowDrag" app/src/main/assets/map/gyro.js
```

命中：第 21 行 `var gyro =`；第 24 行 `var orbitQ`；第 35 行 `var dragSource`；第 95 行 `function setMode`；第 123 行 `function composedTarget`；第 144 行 / 第 180 行 `orbitQ.copy`；第 193 行 `function bindFollowDrag`。

```
grep -n "orbitSource\|orbitStart\|orbitMove\|orbitEnd" app/src/main/assets/map/battle.html
```

命中：第 339 行 `orbitSource` 声明；第 340 行 `orbitStart`；第 344 行 `orbitMove`；第 355 行 `orbitEnd`；第 360-375 行双通路监听注册。

```
grep -n "dragSource\|orbitStart\|orbitMove\|orbitEnd\|tapX" app/src/main/assets/map/three.html
```

命中：第 379 行 `dragSource` / `tapX` / `tapY` 声明；第 387 行 `orbitStart`；第 390 行 `orbitMove`；第 399 行 `orbitEnd`；第 407-441 行双通路监听注册。

### 语法校验

```
node --check app/src/main/assets/map/gyro.js
```

结果：`GYRO SYNTAX OK`（Node v22.23.4）。两 HTML 内联脚本经 `vm.Script` 解析，各 1 个脚本块，均 `SYNTAX OK`。

### 无头冒烟结果

12 项断言全 PASS：off→follow、follow→parallax、parallax→follow、follow→off、parallax 再入、→off 六次切换模式落点正确；touch 锁通路时 pointer move 早退；pointer 锁通路时 touch move 早退；拖动偏移非零且为单份量级；源码无 `gyro.active`、无 `drag.q`、无 `active: false` 残留。

## 五、二阶、三阶影响推演

- 对页面既有轨道管道：双通路封装在原有 orbit 回调内，battle.html 的 cam.yaw/pitch 钳制（第 350-351 行）、three.html 的 rotY/rotX 钳制（第 396-397 行）原样保留；跟随模式下轨道旋转仍被 `Gyro.getMode() === 'follow'` 短路，行为与上版一致。
- 对 three.html 落点判定：上版 pointerup 用 `e.clientX - lastX` 比对，而 `lastX` 已在 move 中被刷成当前坐标，拖动松手也会误判成轻点落兵。本版改用按下时刻的 `tapX/tapY` 算位移（`three.html` 第 388 行、第 404 行），拖动松手不再误落旗标；这是同段代码内的缺陷顺带修正，落点阈值仍为 3px。
- 对回弹：dragOffset 累积路径不变，松手 180ms 时间常数指数渐近回弹逻辑未动（`gyro.js` 第 172-175 行）；双通路结束事件都会清 `draggingFollow` 与来源锁，不会出现半按态卡死。
- 对前后台 / 旋转：`visibilitychange` 隐藏时复位拖拽态与来源锁（`gyro.js` 第 252-255 行）；`orientationchange` 同步清锁（`gyro.js` 第 245-246 行）。回前台过渡复用 `composedTarget`，不新增分支。
- 维护成本：锁机制在三份文件各一份内联实现（gyro.js 跟随拖拽、battle 轨道、three 轨道），未抽公共模块——地图页脚本互不共享作用域，抽模块要改加载顺序，超出本单文件域；三处实现同构，后续改手感需同步三处，已在锚点卡登记。
- 对 CI：本单零 Kotlin、零资源清单改动，云端 4738fb4 全绿基线不受影响；回归面限三份 JS/HTML 资产。

## 六、自查反例（推演中可能改错的地方与验证方式）

1. 反例：合成内核若 pointer 先于 touch 派发，首事件锁是否会落到 pointer 而 touch 通路双算？验证：锁只记录第一条通路，第二条通路的 start 见 `draggingFollow` 已激活即早退（`gyro.js` 第 199 行）；冒烟反向用例先 pointerdown 后 touchstart，touch move 早退断言通过。
2. 反例：双指捏合会不会被单指来源锁吃掉？验证：three.html touchmove 先判 `e.touches.length === 2` 走捏合分支并 return（第 424-431 行），不经过单指 orbitMove；锁只约束单指手势。battle.html 无捏合分支，未被锁影响。
3. 反例：过渡到 off 时 `orbitQ` 若是单位阵（首帧未记过宿主姿态），会不会 lerp 到错误朝向？验证：off 分支每帧刷新 orbitQ（第 144 行），进入非 off 模式前必有 off 帧记录；`camRef` 未 attach 时不建过渡（第 103 行条件），首帧不会拿单位阵当回归目标。
4. 反例：删 `active` 后，原生桥或页面是否有别处读取该字段？验证：全 assets 目录 grep `.active` 与 `gyro.active`，除权重滑杆局部变量外零命中；两 Activity 桥只暴露 `pull()/degraded()/available()`，不触碰 JS 内部字段。
5. 反例：四向过渡会不会把 parallax 的弱视差层一起 lerp 掉，导致过渡中画面发闷？验证：parallax 过渡目标取 orbitQ（去视差基线），视差层只在稳态帧 premultiply；过渡结束后 baseline 已在进入时刻刷新（`gyro.js` 第 107 行），呼吸感从基线重新叠加。
6. 反例：pointer 通路未 preventDefault 会不会让页面滚动穿透？验证：battle.html pointermove 在 `e.cancelable` 时调 preventDefault（第 363 行）；three.html 画布 CSS 已有 `touch-action:none`（`three.html` 第 9 行）；两页 body 均 `overflow:hidden` 固定铺满。
7. 反例：冒烟桩的 Quaternion 是手写简化版，会不会掩盖 three.min.js 真实方法差异？验证：冒烟只验状态机与锁逻辑，四元数代数差异沿用 Q439 已实测的方法清单（slerp/multiply/premultiply/invert/identity/setFromAxisAngle/clone/copy/normalize 均在）；真实渲染差异列入真机未验证项。

## 七、未验证项与遗留

- 真机 WebView 对 pointer/touch 事件合成与派发顺序的实际表现，需上机复核双通路不翻倍、旧内核 touch 通路可拖。
- 真机传感器精度、漂移、回弹手感与功耗，无头环境无法标定，沿用 Q439 遗留口径。
- 模式 / 权重仍不持久化，重启回默认 off + 0.08；跟随模式视点仍走既有球面轨道，姿态由陀螺仪覆盖，位置锁定纯换朝向未做——均为既有遗留，本单不扩范围。
