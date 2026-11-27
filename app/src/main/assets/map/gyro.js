/*
 * gyro.js · 陀螺仪接管 3D 沙盘 / 地球仪（规格 §三、§四、§五、§六、§八）
 *
 * 链路：原生 SensorManager → FIFO 10 条四元数 → 本文件每帧 pull() 取一条 →
 * Three.js 端 slerp 平滑合成。不接 DeviceOrientation API（file:// 不授权）。
 *
 * 三种模式：
 *   off       关闭（默认）：纯手指操控，镜头姿态本文件不介入；
 *   follow    跟随：陀螺仪全权接管旋转，手指只缩放；拖动产生临时偏移，松手 600ms 回弹；
 *   parallax  叠加：手指为主操控，陀螺仪给一层 0.12 权重的微弱视差呼吸感。
 *
 * 本文件只在既有 animate 循环的 renderer.render() 之前挂一个 Gyro.frame() 钩子，
 * 不改页面原有位置计算 / 命中测试管道；棋子、地名拾取仍走屏幕坐标，不被镜头偏移污染。
 */
(function () {
  if (window.Gyro) return;

  var IDENTITY = new THREE.Quaternion();

  // —— 规格 §三锁定的状态量 ——
  var gyro = { q: new THREE.Quaternion(), weight: 0.08 };
  var dragOffset = new THREE.Quaternion();       // 累积的手指临时偏移，松手后回弹归零
  var finalQ = new THREE.Quaternion();
  var orbitQ = new THREE.Quaternion();          // 宿主轨道模型自身算出的相机姿态，→off 过渡的回归目标

  var mode = 'off';            // off | follow | parallax
  var lastMode = 'follow';     // 单点 toggle 在「关闭 ↔ 最近用的模式」之间切换
  var baseline = new THREE.Quaternion();         // 叠加模式进入时刻的陀螺仪基准，算相对视差
  var camRef = null;
  var available = true;        // 原生是否有可用传感器；false 时入口隐藏
  var degraded = false;        // 是否落在噪声最大的加速度+磁力兜底分支
  var transition = null;       // 800ms 视角过渡 {from, t0, dur, to}
  var lastFrame = 0;
  var draggingFollow = false;
  var dragSource = null;       // 首事件来源锁：'pointer' | 'touch'；同一手势只走一条通路，防双计数
  var dragLastX = 0, dragLastY = 0;

  // 权重档位：规格 §三可调区间 0.04–0.20；默认 0.08。兜底分支强制压到 0.04。
  var WEIGHT_STEPS = [0.04, 0.08, 0.12, 0.16, 0.20];
  var weightIndex = 1;
  var WEIGHT_MIN = 0.04, WEIGHT_MAX = 0.20;

  var MODES = [
    { id: 'off', label: '关闭' },
    { id: 'follow', label: '跟随' },
    { id: 'parallax', label: '叠加' }
  ];

  /* ---------- 原生桥拉取 ---------- */

  // 每帧从原生 FIFO 取一条最新四元数；桥未就绪 / WebView 销毁时静默跳过本帧。
  function pollNative() {
    var bridge = window.GyroBridge;
    if (!bridge) return;
    try {
      var s = bridge.pull();
      if (s) {
        var p = s.split(',');
        if (p.length === 4) {
          window.onQuat(parseFloat(p[0]), parseFloat(p[1]), parseFloat(p[2]), parseFloat(p[3]));
        }
      }
    } catch (e) { /* 上下文已销毁：不抛错、不补帧 */ }
  }

  // 规格 §三：JS 端接收入口 onQuat(w, x, y, z)。异常 / NaN 帧直接丢弃，防污染姿态。
  window.onQuat = function (w, x, y, z) {
    if (!isFinite(w) || !isFinite(x) || !isFinite(y) || !isFinite(z)) return;
    gyro.q.set(x, y, z, w);
  };

  function initCapability() {
    var bridge = window.GyroBridge;
    try {
      if (bridge) {
        available = bridge.available();
        degraded = bridge.degraded();
      } else {
        available = false;
      }
    } catch (e) { available = false; }
    if (degraded) {
      // 规格 §六：兜底融合矩阵分支强制固定 0.04 权重，档位滑杆锁死在最低档
      gyro.weight = 0.04;
      weightIndex = 0;
    }
  }

  /* ---------- 模式与权重 ---------- */

  function clampWeight(w) {
    return Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, w));
  }

  function setMode(next, now) {
    if (next === mode) return;
    if (!available && next !== 'off') next = 'off';
    if (next !== 'off') lastMode = next;
    // 四种切换（off→follow / off→parallax / follow↔parallax / →off）统一走 800ms 过渡。
    // from 取切换瞬间相机实际合成姿态（上一帧 finalQ 已乘过 dragOffset），不是裸 gyro.q，
    // 避免拖动残留偏移在切换瞬间被丢掉而产生单帧跳变。
    if (camRef) {
      transition = { from: camRef.quaternion.clone(), t0: now || performance.now(), dur: 800, to: next };
    }
    mode = next;
    if (next === 'parallax') baseline.copy(gyro.q); // 叠加基准：视差相对进入时刻计算
    refreshUI();
  }

  function setWeight(idx) {
    var max = degraded ? 0 : WEIGHT_STEPS.length - 1;
    weightIndex = Math.max(0, Math.min(max, idx));
    gyro.weight = degraded ? 0.04 : clampWeight(WEIGHT_STEPS[weightIndex]);
    refreshUI();
  }

  function nudgeWeight(dir) { setWeight(weightIndex + dir); }

  /* ---------- 每帧合成（render 之前调用） ---------- */

  // 过渡 / 稳态共用的目标四元数（写入 finalQ 后返回）。
  // follow = slerp(gyro.q, weight) × dragOffset；off / parallax 无独立陀螺仪目标，回归轨道基线 orbitQ。
  function composedTarget(targetMode) {
    if (targetMode === 'follow') {
      finalQ.identity();
      finalQ.slerp(gyro.q, gyro.weight);
      finalQ.multiply(dragOffset);
      return finalQ;
    }
    return orbitQ;
  }

  function frame(now, camera) {
    if (camera) camRef = camera;
    pollNative();
    now = now || performance.now();
    var dt = lastFrame ? Math.min(0.06, (now - lastFrame) / 1000) : 0.016;
    lastFrame = now;
    if (!camRef) return;

    // 关闭模式：不介入镜头；顺带清掉残留拖动偏移，保证下次进入从零开始。
    // orbitQ 在此持续记录宿主轨道模型自己算出的姿态，作为 →off 过渡的回归目标。
    if (mode === 'off') {
      orbitQ.copy(camRef.quaternion);
      dragOffset.identity();
      return;
    }

    // 800ms 过渡中：分量线性插值（lerp，非 slerp）后归一化，规避四元数绕远路（规格 §四）
    // 注：本仓 three.min.js 的 Quaternion 未挂 lerp 方法，故手写四分量插值再 normalize
    if (transition) {
      var k = Math.min(1, (now - transition.t0) / transition.dur);
      var tq = composedTarget(transition.to);
      var fq = transition.from;
      finalQ.set(
        fq.x + (tq.x - fq.x) * k,
        fq.y + (tq.y - fq.y) * k,
        fq.z + (tq.z - fq.z) * k,
        fq.w + (tq.w - fq.w) * k
      ).normalize();
      camRef.quaternion.copy(finalQ);
      if (k >= 1) transition = null;
      return;
    }

    if (mode === 'follow') {
      // 规格 §三：finalQ = slerp(gyro.q, weight) × 拖动偏移
      finalQ.identity();
      finalQ.slerp(gyro.q, gyro.weight);
      // 松手后拖动偏移 600ms 回弹归零；时间常数 180ms，600ms 后残留约 3.6%
      if (!draggingFollow) {
        var decay = 1 - Math.exp(-dt / 0.18);
        dragOffset.slerp(IDENTITY, decay);
      }
      finalQ.multiply(dragOffset);
      camRef.quaternion.copy(finalQ);
    } else if (mode === 'parallax') {
      // 叠加：以当前镜头姿态为底，叠加一层相对基准的微弱陀螺仪视差（权重 0.12）。
      // premultiply 之前先把去视差前的轨道姿态记入 orbitQ，供 →off 过渡回归。
      orbitQ.copy(camRef.quaternion);
      var delta = baseline.clone().invert().multiply(gyro.q);
      var off = IDENTITY.clone().slerp(delta, 0.12);
      camRef.quaternion.premultiply(off);
    }
  }

  /* ---------- 跟随模式下的手指临时偏移 ---------- */

  // 跟随模式手指临时偏移：pointer / touch 双通路。
  // 防重复计数：首事件（pointerdown 或 touchstart）锁定 dragSource，后续 move/up 只认被锁来源；
  // 同时合成两类事件的内核上，touch 事件先于 pointer 事件派发（W3C 事件顺序），首事件落 touch，
  // 随后合成的 pointerdown 见 draggingFollow=true 直接早退，一次拖动只累一遍偏移。
  function bindFollowDrag(canvas) {
    if (!canvas) return;

    function start(source, x, y) {
      if (mode !== 'follow') return;
      if (draggingFollow) return; // 已被另一通路的首事件锁定：本通路早退，防双计数
      draggingFollow = true;
      dragSource = source;
      dragLastX = x;
      dragLastY = y;
    }
    function move(source, x, y) {
      if (!draggingFollow || dragSource !== source) return;
      var dx = x - dragLastX;
      var dy = y - dragLastY;
      dragLastX = x;
      dragLastY = y;
      // 绕相机局部 Y / X 轴的小旋转，累加到临时偏移；灵敏度与页面既有拖动手感一致
      var yawQ = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), dx * 0.006);
      var pitchQ = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), dy * 0.004);
      dragOffset.multiply(yawQ).multiply(pitchQ);
    }
    function end(source) {
      if (dragSource !== source) return;
      draggingFollow = false;
      dragSource = null;
    }

    canvas.addEventListener('pointerdown', function (e) { start('pointer', e.clientX, e.clientY); });
    canvas.addEventListener('pointermove', function (e) { move('pointer', e.clientX, e.clientY); });
    canvas.addEventListener('pointerup', function () { end('pointer'); });
    canvas.addEventListener('pointercancel', function () { end('pointer'); });

    // touch 兜底：不合成 pointer 事件的旧 WebView 内核上，单指拖动仍能产生临时偏移
    canvas.addEventListener('touchstart', function (e) {
      var t = e.touches[0]; if (t) start('touch', t.clientX, t.clientY);
    }, { passive: true });
    canvas.addEventListener('touchmove', function (e) {
      var t = e.touches[0]; if (t) move('touch', t.clientX, t.clientY);
    }, { passive: true });
    canvas.addEventListener('touchend', function () { end('touch'); }, { passive: true });
    canvas.addEventListener('touchcancel', function () { end('touch'); }, { passive: true });
  }

  /* ---------- 屏幕方向 / 前后台恢复 ---------- */

  // 屏幕旋转 90° 后设备坐标轴翻转：重置拖动偏移与叠加基准，防视角跳变（规格 §六）
  window.addEventListener('orientationchange', function () {
    dragOffset.identity();
    baseline.copy(gyro.q);
    transition = null;
    draggingFollow = false;
    dragSource = null;
  });

  // 切后台：复位跟随拖拽态与来源锁，避免「拖到一半切后台」回来卡在半按状态；
  // 切回前台：原生重注册后，视角走 800ms 平滑恢复，不硬切（规格 §五）
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      draggingFollow = false;
      dragSource = null;
      return;
    }
    if (camRef) {
      transition = { from: camRef.quaternion.clone(), t0: performance.now(), dur: 800, to: mode };
    }
  });

  /* ---------- UI：三态图标 + 竖排菜单 + 四金点权重指示器 ---------- */

  var dock = null, btn = null, dotsBox = null, menu = null;

  function buildUI() {
    if (!available) return; // 全无传感器：入口隐藏，模式固定关闭（规格 §六、§七）

    var style = document.createElement('style');
    // 贴仓内东方美学色系：暖金 #D4A54A / 铜金 #8B6914 / 纸色 #E6DAC0 / 墨色 #1C1A14，不用 Material 蓝紫
    style.textContent =
      '#gyroDock{position:fixed;top:12px;right:14px;z-index:40;display:flex;align-items:center;gap:8px;}' +
      '#gyroBtn{width:34px;height:34px;line-height:32px;text-align:center;font-size:16px;' +
      'border:1px solid rgba(139,105,20,.45);border-radius:50%;background:rgba(28,26,20,.72);' +
      'color:#8C8570;cursor:pointer;font-family:inherit;}' +
      '#gyroBtn.follow{color:#D4A54A;border-color:rgba(212,165,74,.7);box-shadow:0 0 8px rgba(212,165,74,.35);}' +
      '#gyroBtn.parallax{color:#D4A54A;border-color:rgba(212,165,74,.7);animation:gyroPulse 2.2s ease-in-out infinite;}' +
      '@keyframes gyroPulse{0%,100%{box-shadow:0 0 4px rgba(212,165,74,.25);}50%{box-shadow:0 0 14px rgba(212,165,74,.65);}}' +
      '#gyroDots{display:flex;align-items:center;gap:5px;padding:4px 7px;border-radius:3px;' +
      'background:rgba(28,26,20,.6);border:1px solid rgba(139,105,20,.3);}' +
      '.gd{width:3px;height:12px;border-radius:2px;background:rgba(212,165,74,.22);}' +
      '.gd.on{background:#D4A54A;box-shadow:0 0 5px rgba(212,165,74,.6);}' +
      '#gyroMenu{display:none;position:fixed;top:52px;right:14px;z-index:41;min-width:96px;' +
      'background:rgba(26,20,16,.94);border:1px solid rgba(212,165,74,.45);border-radius:6px;padding:4px 0;}' +
      '#gyroMenu.open{display:block;}' +
      '.gm-item{padding:9px 18px;font-size:13px;letter-spacing:.08em;color:#E8DCC0;cursor:pointer;}' +
      '.gm-item.on{color:#D4A54A;background:rgba(212,165,74,.1);}';
    document.head.appendChild(style);

    dock = document.createElement('div');
    dock.id = 'gyroDock';
    btn = document.createElement('button');
    btn.id = 'gyroBtn';
    btn.textContent = '📱';
    btn.title = '陀螺仪';
    dotsBox = document.createElement('div');
    dotsBox.id = 'gyroDots';
    for (var i = 0; i < 4; i++) {
      var d = document.createElement('span');
      d.className = 'gd';
      dotsBox.appendChild(d);
    }
    menu = document.createElement('div');
    menu.id = 'gyroMenu';
    MODES.forEach(function (m) {
      var it = document.createElement('div');
      it.className = 'gm-item';
      it.textContent = m.label;
      it.dataset.mode = m.id;
      menu.appendChild(it);
    });
    dock.appendChild(btn);
    dock.appendChild(dotsBox);
    document.body.appendChild(dock);
    document.body.appendChild(menu);

    bindMenuGesture();
    bindWeightSwipe();
    bindFollowDrag(document.getElementById('c'));
    refreshUI();
  }

  function refreshUI() {
    if (!btn) return;
    btn.className = mode === 'off' ? '' : mode;
    var dots = dotsBox ? dotsBox.children : [];
    for (var i = 0; i < dots.length; i++) {
      dots[i].className = 'gd' + (i <= weightIndex ? ' on' : '');
    }
    var items = menu ? menu.children : [];
    for (var j = 0; j < items.length; j++) {
      items[j].className = 'gm-item' + (items[j].dataset.mode === mode ? ' on' : '');
    }
  }

  // 长按（≥450ms 且移动 <10px）弹出竖排三模式菜单；抬手落在某项上即选中锁定。
  function bindMenuGesture() {
    var pressTimer = null, longPressed = false, sx = 0, sy = 0;

    function openMenu() { menu.classList.add('open'); }
    function closeMenu() { menu.classList.remove('open'); }

    btn.addEventListener('pointerdown', function (e) {
      longPressed = false;
      sx = e.clientX; sy = e.clientY;
      pressTimer = setTimeout(function () {
        longPressed = true;
        openMenu();
      }, 450);
    });

    btn.addEventListener('pointermove', function (e) {
      if (Math.abs(e.clientX - sx) + Math.abs(e.clientY - sy) > 10) {
        clearTimeout(pressTimer);
      }
    });

    btn.addEventListener('pointerup', function (e) {
      clearTimeout(pressTimer);
      if (longPressed) {
        // 长按松手：命中菜单项则选该项；未命中保持原模式
        var hit = itemAt(e.clientX, e.clientY);
        if (hit) setMode(hit.dataset.mode);
        closeMenu();
        longPressed = false;
      } else {
        // 单点：关闭 ↔ 最近用的模式 toggle
        setMode(mode === 'off' ? lastMode : 'off');
      }
    });

    function itemAt(x, y) {
      var items = menu.children;
      for (var i = 0; i < items.length; i++) {
        var r = items[i].getBoundingClientRect();
        if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return items[i];
      }
      return null;
    }

    // 点菜单外部收起
    document.addEventListener('pointerdown', function (e) {
      if (menu.classList.contains('open') && !menu.contains(e.target) && e.target !== btn) closeMenu();
    });
  }

  // 权重指示器上下滑：上滑加一档、下滑减一档，每档 0.04（兜底分支档位锁死）。
  function bindWeightSwipe() {
    var sx = 0, sy = 0, active = false;
    dotsBox.addEventListener('pointerdown', function (e) {
      active = true; sx = e.clientX; sy = e.clientY;
    });
    dotsBox.addEventListener('pointerup', function (e) {
      if (!active) return;
      active = false;
      var dy = e.clientY - sy;
      if (Math.abs(dy) > 12) nudgeWeight(dy < 0 ? 1 : -1);
    });
  }

  /* ---------- 启动 ---------- */

  function attach(camera) { camRef = camera || camRef; }

  function boot() {
    initCapability();
    buildUI();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

  window.Gyro = {
    attach: attach,
    frame: frame,
    setMode: setMode,
    setWeight: setWeight,
    getMode: function () { return mode; },
    getWeight: function () { return gyro.weight; }
  };
})();
