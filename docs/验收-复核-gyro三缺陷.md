锚点卡：docs/锚点/验收-Q446-gyro三缺陷.md

# 验收-复核-Q446 陀螺仪三缺陷（手势双通路 / 四向过渡 / 死字段清剿）独立验收

- 被验 commit：`7cd4d66`（含本单被验改动，已 push）。被验资产仅三份：`app/src/main/assets/map/gyro.js`、`app/src/main/assets/map/battle.html`、`app/src/main/assets/map/three.html`。
- 被验写码报告：`docs/修复-gyro三缺陷.md`；写码锚点卡：`docs/锚点/Q446-gyro三缺陷.md`。写码方自报结论（`node --check` PASS、两 HTML `vm.Script` 解析 PASS、无头冒烟 12 项 PASS、单份 0.030 / 双份 0.060 阈值 0.045）一律按待验主张处理，本验收方独立取数复核。
- 总结论：**有条件通过（静态与无头推演面通过；编译面未碰 Kotlin 以云端 CI 为基线；真机合成顺序与手感未验）**。九条判据：7 条 PASS（其中判据 6、8 附观察项），2 条部分 PASS（判据 1、判据 9）；新发现问题 4 条（待裁 2 / 已入队观察 1 / 判废清理项 1）。
- 验收纪律：只读验收，未改三份被验资产、未改 `app/**` 其它产品代码，未做任何 git 写操作，未跑 gradlew / kotlinc / SDK / Android 编译；JS 仅执行 `node --check`、内联脚本 `vm.Script` 解析与 Node 无头桩推演。

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74（本验收方自取 sha256 前 12 位，与磁盘现行文件一致）。
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「报告写清这条判据对应哪份原始事实的哪一行；锚点可回源。」（docs/机制-文档子代理必读.md:16）
- 适用条款：B2 改后 grep 回证再跑机检，每条结论回指 `文件:行` 或命令输出；禁本地编译，编译 / 单测 / Lint 面以云端 CI 为准，本验收不替 CI 背书；溯源锚点可回源；验收独立把关，不采信写码方自评，计数与行号自取；L0/L1 静态级判定，L2 编译单测、L3 组装、L4 真机观感均列未验证项；F8 新发现问题逐条带处置状态词与归属。

## 零、验收基线与改坏推演

- 取数口径：以任务书九条判据 + 写码锚点卡 `docs/锚点/Q446-gyro三缺陷.md` 为对照基准；写码报告仅作待验主张清单，全部结论由本验收方回仓库代码独立复核。修前状态经 `git show 4738fb4:<文件>` 只读取证，未改工作区。
- 改坏推演 1：若双通路只挂一条监听，旧内核或合成内核上另一通路拖不动。核 `app/src/main/assets/map/gyro.js:221-224` 与 `app/src/main/assets/map/gyro.js:227-234`、`app/src/main/assets/map/battle.html:360-366` 与 `app/src/main/assets/map/battle.html:367-375`、`app/src/main/assets/map/three.html:407-413` 与 `app/src/main/assets/map/three.html:414-442`，pointer / touch 两通路在三处均齐备。
- 改坏推演 2：若来源锁只在 start 记、move/end 不比对，合成事件双通路会各累一遍偏移。核 `app/src/main/assets/map/gyro.js:205`、`app/src/main/assets/map/gyro.js:216`、`app/src/main/assets/map/battle.html:345`、`app/src/main/assets/map/battle.html:356`、`app/src/main/assets/map/three.html:391`、`app/src/main/assets/map/three.html:400`，move / end 均先比对来源、不符早退。
- 改坏推演 3：若过渡起点取裸 `gyro.q` 而非相机合成姿态，拖到一半切模式会丢掉临时偏移与已叠加视差，产生单帧跳变。核 `app/src/main/assets/map/gyro.js:103` `from: camRef.quaternion.clone()`，克隆的是上一帧产物（follow 稳态 `app/src/main/assets/map/gyro.js:175-176` 已乘 `dragOffset`；parallax 稳态 `app/src/main/assets/map/gyro.js:183` 已 premultiply 视差）。
- 改坏推演 4：若中途再切模式时新过渡回拷旧 `from`，画面会跳回旧起点；若旧过渡对象不清，两段过渡叠加会超调。核 `app/src/main/assets/map/gyro.js:102-105` 过渡对象整体重建（单一 `transition` 变量替换），无头推演首帧姿态与中间帧零偏差（见判据 3）。
- 改坏推演 5：若删字段后另有 JS / Kotlin 读取 `gyro.active` 或 `drag.q`，运行时会取到 undefined。全 assets 精确串扫描零命中；两 Activity 桥仅暴露 `pull()/degraded()/available()`（见判据 5）。
- 改坏推演 6：若拾取或缩放路径读了新拖拽状态，点选落点会随手势偏移、缩放被拖拽吞掉。核 `app/src/main/assets/map/three.html:444-449` 拾取仅用客户坐标算 NDC，`app/src/main/assets/map/three.html:417-420` 与 `app/src/main/assets/map/battle.html:376-379` wheel 仅读 `deltaY`，均不触 `dragSource/dragging`。

## 一、判据 1：双通路来源锁，合成双通路单次拖动只计一次 — 部分 PASS

- 两通路监听齐备：`app/src/main/assets/map/gyro.js:221-224` pointer 四件（down/move/up/cancel）、`app/src/main/assets/map/gyro.js:227-234` touch 四件（start/move/end/cancel）；`app/src/main/assets/map/battle.html:360-366` pointer、`app/src/main/assets/map/battle.html:367-375` touch；`app/src/main/assets/map/three.html:407-413` pointer、`app/src/main/assets/map/three.html:414-442` touch。
- move/end 来源早退：gyro.js `app/src/main/assets/map/gyro.js:205` `if (!draggingFollow || dragSource !== source) return;`、`app/src/main/assets/map/gyro.js:216` `if (dragSource !== source) return;`；battle `app/src/main/assets/map/battle.html:345`、`app/src/main/assets/map/battle.html:356`；three `app/src/main/assets/map/three.html:391`、`app/src/main/assets/map/three.html:400`。
- 调用序列推演（合成内核，touch 先于 pointer，W3C 顺序）：① `touchstart` → start/orbitStart 写锁为 `touch`，置激活；② 合成 `pointerdown` → 第二通路进入 start；③ `touchmove` 与合成 `pointermove` 先后到达，未被锁通路的 move 在来源比对处早退；④ `touchend`/`pointerup` 同理只认被锁通路。一次拖动只累一遍偏移。
- 无头推演（本验收方桩：手写四元数 + 真实加载 `gyro.js`，桩内 `THREE/document/window/performance` 打桩）：touch 先 pointer 后，10px 偏航后相机四元数 `|y|=0.0300`（单份量级，界内 <0.045）；pointer 先 touch 后，`|y|=0.0300`，未翻倍到 0.060。双向顺序均未双计数。
- 部分 PASS 理由（哪几条不过）：三处 start 守卫不完全同构。`app/src/main/assets/map/gyro.js:198` start 有 `if (draggingFollow) return;` 已激活早退；而 `app/src/main/assets/map/battle.html:340-343` `orbitStart` 与 `app/src/main/assets/map/three.html:387-389` `orbitStart` 无已激活判断，第二通路 start 会覆盖来源锁并重置 `last/tapX`。在规范事件顺序（down 先于 move）下无双计数；但若个别内核在两次 down 之间夹派 move，或 start 回调重入，锁归属会翻转。该缺口记入 N2，待裁是否补齐统一守卫。
- 多指隔离：`app/src/main/assets/map/three.html:424-431` 双指分支在 `orbitMove` 之前 `return`，不经过单指锁；`app/src/main/assets/map/battle.html` 未实现捏合缩放，属既有范围（写码报告 `docs/修复-gyro三缺陷.md:44` 已声明，本单不新增）。

## 二、判据 2：四向 800ms 过渡齐全，起点取合成相机姿态 — PASS

- 过渡不再有 `mode === 'off'` 前置条件：`app/src/main/assets/map/gyro.js:95-108` `setMode`，`app/src/main/assets/map/gyro.js:102-105` 只要 `camRef` 存在即建 `{ from, t0, dur: 800, to }`。
- 目标分发 `app/src/main/assets/map/gyro.js:123-131` `composedTarget`：follow 为 `slerp(gyro.q, weight) × dragOffset`（`app/src/main/assets/map/gyro.js:125-127`），off / parallax 回归 `orbitQ`（`app/src/main/assets/map/gyro.js:130`）。
- 六次切换无头落点：off→follow、follow→parallax、parallax→follow、follow→off、off→parallax、parallax→off，模式落点全部正确（桩内逐次 `setMode` 后读 `Gyro.getMode()`，六组均与目标一致）。
- 起点口径：`app/src/main/assets/map/gyro.js:103` `from: camRef.quaternion.clone()`；follow 稳态产物在 `app/src/main/assets/map/gyro.js:175-176` 拷给相机，parallax 稳态产物在 `app/src/main/assets/map/gyro.js:183` premultiply 后留在相机，克隆姿态同时携带拖动临时偏移与已叠加视差，不取裸 `gyro.q`。
- 基线刷新两点：off 分支 `app/src/main/assets/map/gyro.js:144` `orbitQ.copy(camRef.quaternion)`；parallax 在 premultiply 之前 `app/src/main/assets/map/gyro.js:180` 记入去视差前姿态。
- 插值方式：`app/src/main/assets/map/gyro.js:151-164` 四分量线性插值后 `.normalize()`，非 slerp，规避四元数近反向绕远路；仓内 `three.min.js` 的 Quaternion 无 `lerp` 方法，手写插值口径与注释一致。

## 三、判据 3：过渡中途再切不跳回旧起点、不叠加二次过渡超调 — PASS

- 机制：`setMode` 每次整体替换 `transition`（`app/src/main/assets/map/gyro.js:103`），新 `from` 取切换瞬间 `camRef.quaternion`；旧过渡不保留、不与新过渡串联。
- 无头推演：off→follow 过渡推进到 400ms（k=0.5）记录相机中间帧姿态，随即 `setMode('parallax')`，下一帧相机四元数与记录的中间帧逐分量偏差 `delta=0.00e+0`，未跳回旧起点；过渡时长仍为 800ms、`t0` 重置，无两段过渡叠加。
- 边界：`app/src/main/assets/map/gyro.js:96` `if (next === mode) return;` 同模式再切为空操作，不会反复建过渡；`camRef` 未 attach 时不建过渡（`app/src/main/assets/map/gyro.js:102` 条件），首帧不会拿单位阵当回归目标。

## 四、判据 4：→off 落地口径与报告措辞一致 — PASS

- 报告措辞（`docs/修复-gyro三缺陷.md:65`）：「→off：走 800ms 渐变回到轨道基线 `orbitQ`，不做立即硬切。」
- 代码口径：`app/src/main/assets/map/gyro.js:130` `composedTarget('off')` 返回 `orbitQ`；`app/src/main/assets/map/gyro.js:103` 过渡 `dur: 800`；无头推演 →off 后 100ms 帧相机处于中间态（`y=0.0300`），未当帧切回基线。报告写渐变、代码即渐变，二者相符，无「报告渐变 / 代码硬切」不符。
- 语义边界：800ms 为一次性归还控制；过渡结束后 off 分支仍只记 `orbitQ`、清 `dragOffset`（`app/src/main/assets/map/gyro.js:143-146`），不持续接管镜头。

## 五、判据 5：死字段 `gyro.active` / `drag.q` / `drag` 对象零残留 — PASS

- 精确串扫描（全 `app/src/main/assets/`）：`gyro.active`、`drag.q`、`active: false`、`var drag = {`、`targetQuat` 均零命中（exit=1 无输出）。
- 当前声明：`app/src/main/assets/map/gyro.js:21` `var gyro = { q: new THREE.Quaternion(), weight: 0.08 };` 无 `active`；`app/src/main/assets/map/gyro.js:22-23` 为 `dragOffset`、`finalQ`，`drag` 对象不存在；`app/src/main/assets/map/gyro.js:67-70` `onQuat` 只写 `gyro.q`，无 `active` 赋值。
- Kotlin 桥反查：`app/src/main/java/io/legado/app/ui/main/BattleActivity.kt:93-104` 仅 `pull()/degraded()/available()`；`app/src/main/java/io/legado/app/ui/main/PlaceMapActivity.kt:232-243` 同三方法；无任何读取 JS `active` / `drag.q` 的通道。`app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfParallax.kt:176-177` 的 `active` 是 Compose 自有状态属性，与 JS 死字段同名不同物，不误判。
- 同名活字段保留：`app/src/main/assets/map/gyro.js:388` 权重滑杆局部 `active`，`app/src/main/assets/map/gyro.js:390` 写、`app/src/main/assets/map/gyro.js:393` 读、`app/src/main/assets/map/gyro.js:394` 写，有读有写，属活字段，未被清剿波及。
- 第三方库排除：`app/src/main/assets/map/leaflet.js` 内 `.active` 为库内部样式钩子，与本单字段无关。

## 六、判据 6：既有能力零回退 — PASS（附观察项）

- follow 权重 0.08：`app/src/main/assets/map/gyro.js:21` `weight: 0.08`。
- parallax 权重 0.12：`app/src/main/assets/map/gyro.js:182` `IDENTITY.clone().slerp(delta, 0.12)`。
- 降级锁 0.04：`app/src/main/assets/map/gyro.js:84-85` `gyro.weight = 0.04; weightIndex = 0;`；档位表 `app/src/main/assets/map/gyro.js:39` `[0.04, 0.08, 0.12, 0.16, 0.20]`，降级时 `app/src/main/assets/map/gyro.js:111` 钳回 0.04。
- 四金点 UI：`app/src/main/assets/map/gyro.js:297-301` 四枚 `.gd` 指示点；步进滑杆 `app/src/main/assets/map/gyro.js:387-398`，上下滑一档 0.04。
- 回弹：`app/src/main/assets/map/gyro.js:171-174` 未拖拽时 `decay = 1 - Math.exp(-dt / 0.18)`，`dragOffset.slerp(IDENTITY, decay)` 指数渐近，时间常数 180ms，路径与上版一致。
- 基线对照：与 `4738fb4` 版逐项比对，0.08 / 0.12 / 0.04 / 四金点 / 0.18s 回弹均原样保留，本单未回退。
- 观察项（不计失败，记 N3）：档位表五档（至 0.20）而金点仅四枚，第五档无点显；该不对称在上版基线即存在，非本单引入。

## 七、判据 7：拾取与缩放路径未被新手势污染 — PASS

- NDC 拾取：`app/src/main/assets/map/three.html:444-449` `dropPoint` 用 `cx/cy` 算 `nx/ny` 再 `raycaster.setFromCamera`，不读 `dragSource/dragging/rotY/rotX`。
- wheel 缩放：`app/src/main/assets/map/three.html:417-420` 仅读 `e.deltaY` 改 `dist`；`app/src/main/assets/map/battle.html:376-379` 仅读 `e.deltaY` 改 `cam.dist`，钳制区间原样保留（three 90–560、battle 45–220）。
- 跟随模式轨道短路仍在：`app/src/main/assets/map/battle.html:349` `if (!(window.Gyro && Gyro.getMode() === 'follow'))` 才改 yaw/pitch；`app/src/main/assets/map/three.html:395` `if (window.Gyro && Gyro.getMode() === 'follow') return;`。

## 八、判据 8：三页手势不打架，单指拖拽与双指缩放并存 — PASS（附新发现 N1）

- three.html 捏合：`app/src/main/assets/map/three.html:423-431` touchmove 先判 `e.touches.length === 2` 走捏合 `dist` 缩放并 `return`，不进 `orbitMove`；`app/src/main/assets/map/three.html:435`、`app/src/main/assets/map/three.html:440` 结束 / 取消时清 `pinchStart`。
- battle.html 缩放走 wheel 通路（`app/src/main/assets/map/battle.html:376-379`），与单指轨道拖拽分离；无捏合分支属既有范围。
- pointer capture：`app/src/main/assets/map/three.html:409` `canvas.setPointerCapture(e.pointerId)`，拖拽移出画布不丢事件。
- 附观察项 N1：touch 抬手经 `app/src/main/assets/map/three.html:437` → `orbitEnd` 走 3px 轻触点判定；双指轻捏（位移 <3px）后抬手存在误落旗标的边界，详见新发现问题 N1。

## 九、判据 9：报告自报行号抽查回源 — 部分 PASS

- 抽查口径：从写码报告 `docs/修复-gyro三缺陷.md` 抽取 21 处自报行号，逐行回源当前工作区与 `4738fb4` 修前快照；命中分「逐字命中」与「区间首末行差 1」两档，无死文件、无越界。
- 逐字命中（16 组）：`app/src/main/assets/map/gyro.js:221` pointerdown、`app/src/main/assets/map/gyro.js:224` pointercancel、`app/src/main/assets/map/gyro.js:234` touchcancel、`app/src/main/assets/map/gyro.js:35` `dragSource`；`app/src/main/assets/map/battle.html:339` `orbitSource`、`app/src/main/assets/map/battle.html:360-366` pointer 四件、`app/src/main/assets/map/battle.html:345` 来源早退、`app/src/main/assets/map/battle.html:356` 来源早退；`app/src/main/assets/map/three.html:379` 声明行、`app/src/main/assets/map/three.html:407-413` pointer 四件、`app/src/main/assets/map/three.html:391` 来源早退、`app/src/main/assets/map/three.html:388` `tapX`、`app/src/main/assets/map/three.html:404` 落点判定、`app/src/main/assets/map/three.html:396-397` 俯仰钳制、`app/src/main/assets/map/three.html:424-431` 捏合分支；修前快照 `app/src/main/assets/map/gyro.js:174`、`app/src/main/assets/map/gyro.js:180`、`app/src/main/assets/map/gyro.js:193`、`app/src/main/assets/map/battle.html:338`、`app/src/main/assets/map/battle.html:342`、`app/src/main/assets/map/battle.html:356`、`app/src/main/assets/map/three.html:385`、`app/src/main/assets/map/three.html:389`、`app/src/main/assets/map/three.html:398`、`app/src/main/assets/map/three.html:410`、`app/src/main/assets/map/three.html:419` 全部逐字命中。
- 差 1 行（5 组，落点仍在相邻行，未漂移到无关代码）：报告称 `app/src/main/assets/map/gyro.js:199` 守卫早退，实际 `app/src/main/assets/map/gyro.js:198`；报告称 `app/src/main/assets/map/gyro.js:125-129` follow 目标，实际 `app/src/main/assets/map/gyro.js:124-128`；报告称滑杆局部 `active` 在 `app/src/main/assets/map/gyro.js:389`，实际 `app/src/main/assets/map/gyro.js:388`；报告称 `dragOffset/finalQ` 在 `app/src/main/assets/map/gyro.js:23-24`，实际 `app/src/main/assets/map/gyro.js:22-23`；报告称回弹在 `app/src/main/assets/map/gyro.js:172-175`，实际 `app/src/main/assets/map/gyro.js:171-174`。
- 命中率：16/21 ≈ 76.2% 逐字命中，其余 5 组为相邻行差 1；无锚点指向不存在文件、无行号越界。判部分 PASS：行号证据链可用，但写码报告区间行号存在系统性首末行偏移，建议回写时按实际落点校正。

## 十、新发现问题

- N1（待裁）：`app/src/main/assets/map/three.html:434-438` touch 抬手统一走 `orbitEnd` 做 3px 轻触点判定；双指捏合期间未置「本次含双指」标志，轻捏（指尖位移 <3px）后抬手可能误落红旗。旧版 `touchend` 只清 `pinchStart`、不判落点（`4738fb4` 快照可证），本单接线把 touch 抬手纳入落点判定后引入该边界。建议处置：捏合分支内置双指标志，`orbitEnd` 见标志则跳过 `dropPoint`。归属 three.html 手势接线，待裁后返工。
- N2（待裁）：`app/src/main/assets/map/battle.html:340-343` 与 `app/src/main/assets/map/three.html:387-389` 的 `orbitStart` 缺 `app/src/main/assets/map/gyro.js:198` 那样的已激活早退守卫，第二通路 start 会翻转来源锁并重置 `last/tapX`；三处 start 守卫不同构。规范事件顺序下无双计数，但重入 / 乱序内核有边界风险。建议三页统一补 `if (last/dragging) return;` 守卫。归属手势双通路，待裁。
- N3（已入队观察）：权重档位五档（0.04–0.20）而金点仅四枚，第五档 0.20 无视觉点显；上版既有，非本单回退，列入后续观感 / 控件收口候选，本单不扩范围。
- N4（判废清理项）：`app/src/main/assets/map/battle.html:339` 局部变量 `down` 在 `app/src/main/assets/map/battle.html:341` 赋值后全文件再无读取，属写而未读的死变量；上版既有，可在下次触及该文件时顺手摘除，不影响行为。

## 十一、机检记录

- `node --check app/src/main/assets/map/gyro.js`：GYRO SYNTAX OK（Node v22.23.4）。
- 两 HTML 内联脚本 `vm.Script` 解析：`app/src/main/assets/map/battle.html` 1 块 SYNTAX OK；`app/src/main/assets/map/three.html` 1 块 SYNTAX OK。
- 无头推演（本验收方桩，真实加载 gyro.js）：四向六次切换落点 6/6 正确；中途再切首帧偏差 0.00e+0；touch 先 / pointer 后单份 `|y|=0.0300`；pointer 先 / touch 后单份 `|y|=0.0300`；→off 100ms 帧处于中间态。
- 门禁脚本输出末行（逐字抄录）：
  - `python3 data_scripts/check-onboarding.py docs/验收-复核-gyro三缺陷.md` → 末行 `ONBOARD: PASS（2 份报告）`。
  - `python3 data_scripts/check-report-fields.py docs/验收-复核-gyro三缺陷.md` → 末行 `REPORT-FIELDS: PASS（1 份，0 份不合格）`。
  - `python3 data_scripts/check-anchor-card.py docs/验收-复核-gyro三缺陷.md` → 末行 `汇总: PASS=1 FAIL=0（共 1 份）`（N1–N4、F8 为次要提及 WARN，不判红）。
  - `python3 data_scripts/check-problem-finding.py docs/验收-复核-gyro三缺陷.md` → 末行 `PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`。
  - `python3 data_scripts/check-line-anchors.py`：本单两份文档在明细中零命中（0 条 DEADFILE / OUTOFRANGE / SUSPECT）；闸整体退出码 1 系历史文档既有 OUTOFRANGE（如 `docs/验收-复核-视差遗留四项.md`），非本单引入，全量末行 `NEW … 另有 8 条，全量见上方明细`。

## 十二、未验证项（本验收面之外）

- 真机 WebView 对 pointer / touch 事件合成与派发顺序的实际表现：旧内核 touch 通路可否单指拖动、Chromium 合成事件下偏移是否翻倍，需上机复核。
- 真机传感器精度、漂移、回弹手感与功耗：无头环境无法标定，沿用 Q439 遗留口径。
- 编译面 / 单测面：本单零 Kotlin、零资源清单改动，未独立跑云端 CI；以 `7cd4d66` 所在云端基线为参照，编译与单测结论不由本验收背书。
- 写码方自报的「无头冒烟 12 项断言」脚本未随资产入库，本验收未能逐条复现；已以自有 5 组桩推演替代，其 12 项结论不作为本验收证据。
