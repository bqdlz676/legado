/**
 * 落文 · 通用战役推演引擎（纯 JS，零依赖）
 *
 * 边界解法：手机上不跑 Node、不起 WebSocket。
 * 同一份引擎挂 window.LWEngine 在 WebView 里直接跑；桌面调试才走 module.exports。
 *
 * 铁律：引擎不内置任何一场战役。吃 campaign JSON + 地形，吐每回合结果。
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.LWEngine = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // ---------- 随机（可复现） ----------
  function rng(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  // ---------- 地形坐标 ----------
  function bbox(focus) {
    const [lng, lat] = focus.center;
    const dLat = focus.radius / 111.0;
    const dLng = focus.radius / (111.0 * Math.cos(lat * Math.PI / 180));
    return { west: lng - dLng, east: lng + dLng, south: lat - dLat, north: lat + dLat };
  }
  function toCell(b, lng, lat, n) {
    return {
      c: Math.max(0, Math.min(n - 1, Math.round((lng - b.west) / (b.east - b.west) * (n - 1)))),
      r: Math.max(0, Math.min(n - 1, Math.round((b.north - lat) / (b.north - b.south) * (n - 1)))),
    };
  }
  function toLngLat(b, c, r, n) {
    return [
      +(b.west + c / (n - 1) * (b.east - b.west)).toFixed(5),
      +(b.north - r / (n - 1) * (b.north - b.south)).toFixed(5),
    ];
  }

  // ---------- 高度图 ----------
  // 有 PNG 像素（Uint8Array n*n）就用真高程；没有就程序化，并标 source=procedural（绝不假称实测）
  function buildHeight(campaign, n, rand) {
    const src = campaign.terrain || {};
    const raw = new Float32Array(n * n);
    if (src.heightPixels && src.heightPixels.length === n * n) {
      const lo = (src.minEl != null) ? src.minEl : 0;
      const hi = (src.maxEl != null) ? src.maxEl : 3000;
      for (let i = 0; i < raw.length; i++) {
        raw[i] = lo + src.heightPixels[i] / 255 * (hi - lo);
      }
      return {
        h: raw, source: 'png', label: src.source || '高程图',
        minEl: lo, maxEl: hi
      };
    }
    // 程序化：低频起伏 + 两条山脊 + 一条河谷
    const ridges = src.ridges || [];
    const seed = src.seed || 202;
    const r2 = rng(seed);
    const ph = [r2() * 6.28, r2() * 6.28, r2() * 6.28];
    for (let y = 0; y < n; y++) {
      for (let x = 0; x < n; x++) {
        const u = x / n, v = y / n;
        let h = 900
          + 420 * Math.sin(u * 6.28 * 1.3 + ph[0]) * Math.cos(v * 6.28 * 0.9 + ph[1])
          + 260 * Math.sin((u + v) * 6.28 * 2.1 + ph[2])
          + 140 * Math.sin(u * 6.28 * 4.7) * Math.sin(v * 6.28 * 3.9);
        for (const rg of ridges) {
          const d = distToSeg(u, v, rg.a[0], rg.a[1], rg.b[0], rg.b[1]);
          h += rg.h * Math.exp(-(d * d) / (rg.w * rg.w));
        }
        raw[y * n + x] = Math.max(0, h);
      }
    }
    // 河谷：沿 river 线压低 350m
    for (const rv of (src.rivers || [])) {
      for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
        const d = distToSeg(x / n, y / n, rv.a[0], rv.a[1], rv.b[0], rv.b[1]);
        if (d < rv.w) raw[y * n + x] -= 350 * (1 - d / rv.w);
      }
    }
    return { h: raw, source: 'procedural', label: '程序化示意地形（非实测）' };
  }
  function distToSeg(px, py, ax, ay, bx, by) {
    const dx = bx - ax, dy = by - ay;
    const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy || 1)));
    const x = ax + t * dx, y = ay + t * dy;
    return Math.hypot(px - x, py - y);
  }

  // ---------- 地形分析 ----------
  function analyze(campaign, n) {
    const rand = rng((campaign.terrain && campaign.terrain.seed) || 7);
    const hm = buildHeight(campaign, n, rand);
    const b = bbox(campaign.terrainFocus);
    const cellKm = (campaign.terrainFocus.radius * 2) / n;
    const h = hm.h;
    const slope = new Float32Array(n * n);
    const trav = new Uint8Array(n * n);
    for (let r = 0; r < n; r++) {
      for (let c = 0; c < n; c++) {
        const i = r * n + c;
        const hx = (h[r * n + Math.min(n - 1, c + 1)] - h[r * n + Math.max(0, c - 1)]) / (2 * cellKm * 1000);
        const hy = (h[Math.min(n - 1, r + 1) * n + c] - h[Math.max(0, r - 1) * n + c]) / (2 * cellKm * 1000);
        const deg = Math.atan(Math.hypot(hx, hy)) * 180 / Math.PI;
        slope[i] = deg;
        trav[i] = deg <= 15 ? 3 : deg <= 30 ? 2 : deg <= 45 ? 1 : 0;
      }
    }
    // 河流：按 campaign.terrain.rivers 栅格化，宽度 1 格
    const river = new Uint8Array(n * n);
    for (const rv of ((campaign.terrain && campaign.terrain.rivers) || [])) {
      const steps = n * 2;
      for (let s = 0; s <= steps; s++) {
        const t = s / steps;
        const u = rv.a[0] + (rv.b[0] - rv.a[0]) * t;
        const v = rv.a[1] + (rv.b[1] - rv.a[1]) * t;
        const c = Math.round(u * (n - 1)), r = Math.round(v * (n - 1));
        if (c >= 0 && c < n && r >= 0 && r < n) river[r * n + c] = 1;
      }
    }
    // 渡口：河格且坡度 ≤ 10 且两侧可通行
    const fords = [];
    for (let r = 1; r < n - 1; r++) for (let c = 1; c < n - 1; c++) {
      if (!river[r * n + c]) continue;
      if (slope[r * n + c] <= 10 && trav[(r - 1) * n + c] >= 1 && trav[(r + 1) * n + c] >= 1) {
        fords.push({ r, c });
      }
    }
    // 制高点：邻域 10 格相对高差 > 300m
    const high = [];
    for (let r = 0; r < n; r += 3) for (let c = 0; c < n; c += 3) {
      let mn = h[r * n + c];
      for (let dr = -5; dr <= 5; dr++) for (let dc = -5; dc <= 5; dc++) {
        const rr = Math.min(n - 1, Math.max(0, r + dr)), cc = Math.min(n - 1, Math.max(0, c + dc));
        mn = Math.min(mn, h[rr * n + cc]);
      }
      if (h[r * n + c] - mn > 300) high.push({ r, c, advantage: Math.min(1.6, 1 + (h[r * n + c] - mn) / 1200) });
    }
    // 关隘：相邻两格坡度 > 40 且之间窄
    const choke = [];
    for (let r = 2; r < n - 2; r++) for (let c = 2; c < n - 2; c++) {
      const i = r * n + c;
      if (trav[i] >= 1 && slope[i] <= 25) {
        if (slope[(r - 2) * n + c] > 40 && slope[(r + 2) * n + c] > 40) choke.push({ r, c });
      }
    }
    const t = {
      n, b, cellKm, h, hmSource: hm.source, hmLabel: hm.label,
      minEl: hm.minEl, maxEl: hm.maxEl,
      slope, trav, river, fords, high, choke,
      heightAt(r, c) { return h[Math.max(0, Math.min(n - 1, r)) * n + Math.max(0, Math.min(n - 1, c))]; },
      passable(r, c, armyType) {
        const need = armyType === 'cavalry' ? 2 : armyType === 'militia' ? 1 : 2;
        if (r < 0 || c < 0 || r >= n || c >= n) return false;
        if (river[r * n + c]) return false;                 // 河不可直穿
        return trav[r * n + c] >= need;
      },
      // 视线：沿线采样，遇明显脊线遮挡
      visible(a, b2) {
        const steps = Math.max(2, Math.round(Math.hypot(b2.r - a.r, b2.c - a.c) * 2));
        let maxMid = -1e9;
        for (let s = 1; s < steps; s++) {
          const tt = s / steps;
          const rr = Math.round(a.r + (b2.r - a.r) * tt), cc = Math.round(a.c + (b2.c - a.c) * tt);
          maxMid = Math.max(maxMid, t.heightAt(rr, cc));
        }
        const ha = t.heightAt(a.r, a.c), hb = t.heightAt(b2.r, b2.c);
        const line = ha + (hb - ha) * 0.5;
        return maxMid <= line + 60;
      },
    };
    return t;
  }

  // ---------- A*（二叉堆） ----------
  function aStar(T, from, to, armyType, opts) {
    const n = T.n, N = n * n;
    const blocked = (opts && opts.blocked) || null;
    const start = from.r * n + from.c, goal = to.r * n + to.c;
    const g = new Float32Array(N).fill(Infinity);
    const f = new Float32Array(N).fill(Infinity);
    const prev = new Int32Array(N).fill(-1);
    const closed = new Uint8Array(N);
    const heap = [];
    function push(i, fv) { heap.push([fv, i]); let k = heap.length - 1; while (k > 0) { const p = (k - 1) >> 1; if (heap[p][0] <= heap[k][0]) break; [heap[p], heap[k]] = [heap[k], heap[p]]; k = p; } }
    function pop() {
      const top = heap[0], last = heap.pop();
      if (heap.length) { heap[0] = last; let k = 0; while (true) { const l = 2 * k + 1, r = l + 1; let m = k; if (l < heap.length && heap[l][0] < heap[m][0]) m = l; if (r < heap.length && heap[r][0] < heap[m][0]) m = r; if (m === k) break; [heap[m], heap[k]] = [heap[k], heap[m]]; k = m; } }
      return top;
    }
    const hcost = (i) => {
      const r = Math.floor(i / n), c = i % n;
      return Math.hypot(r - to.r, c - to.c);
    };
    g[start] = 0; f[start] = hcost(start); push(start, f[start]);
    let visited = 0;
    while (heap.length && visited < 8000) {
      const [, cur] = pop();
      if (closed[cur]) continue;
      closed[cur] = 1; visited++;
      if (cur === goal) break;
      const r = Math.floor(cur / n), c = cur % n;
      for (let dr = -1; dr <= 1; dr++) for (let dc = -1; dc <= 1; dc++) {
        if (!dr && !dc) continue;
        const rr = r + dr, cc = c + dc;
        if (!T.passable(rr, cc, armyType)) continue;
        if (blocked && blocked[rr * n + cc]) continue;
        const ni = rr * n + cc;
        if (closed[ni]) continue;
        const sl = T.slope[ni];
        const terr = sl <= 8 ? 1.0 : sl <= 20 ? 1.3 : 1.8;
        const step = Math.hypot(dr, dc) * (1 + sl / 45) * terr;
        const ng = g[cur] + step;
        if (ng < g[ni]) { g[ni] = ng; f[ni] = ng + hcost(ni); prev[ni] = cur; push(ni, f[ni]); }
      }
    }
    if (!isFinite(g[goal])) return null;
    const path = [];
    let cur = goal;
    while (cur !== -1) { path.push({ r: Math.floor(cur / n), c: cur % n }); cur = prev[cur]; }
    path.reverse();
    return { path, cost: g[goal] };
  }

  // ---------- 战役运行器 ----------
  function Sim(campaign, opts) {
    opts = opts || {};
    const n = opts.grid || 96;
    const T = analyze(campaign, n);
    const rand = rng(opts.seed || 42);
    const sides = (campaign.sides || []).reduce((m, s) => { m[s.id] = s; return m; }, {});
    const dip = (a, b2) => {
      if (a === b2) return 'allied';
      const row = (campaign.diplomacy || []).find(d => (d.a === a && d.b === b2) || (d.a === b2 && d.b === a));
      return row ? row.relation : 'hostile';
    };
    const armyTypeSpeed = { cavalry: 1.5, elite: 1.2, regular: 1.0, militia: 0.8 };
    const dayKmOf = (a) => {
      const base = a.intent === 'rush' ? 30 : a.intent === 'rout' ? 24 : 15;
      return base * (armyTypeSpeed[a.type] || 1) * (a.fatigue >= 4 ? 0.6 : a.fatigue >= 3 ? 0.8 : 1);
    };

    const armies = (campaign.armies || []).map((a, idx) => {
      const cell = toCell(T.b, a.initialPos[0], a.initialPos[1], n);
      return Object.assign({}, a, {
        idx, r: cell.r, c: cell.c, alive: true,
        pos: a.initialPos.slice(),
        morale: a.morale || 'steady', fatigue: a.fatigue || 0,
        supply: a.supply == null ? 5 : a.supply,
        lastSeen: {}, path: null, intentActive: a.intent || 'hold', reason: '初始意图',
      });
    });

    // 追击绑定：advance 的军队默认追击最近的敌对军（情报驱动，不是死坐标）
    for (const a of armies) {
      if (a.intent !== 'advance' && a.intent !== 'rush') continue;
      const foes = armies.filter(x => x.alive && x.id !== a.id && dip(a.side, x.side) === 'hostile');
      let best = null, bd = Infinity;
      for (const f of foes) {
        const d = Math.hypot(a.r - f.r, a.c - f.c);
        if (d < bd) { bd = d; best = f; }
      }
      if (best) a.hunt = best.id;
    }

    let turn = 0;
    const log = [];
    const highlights = [];

    function neighbors(a) {
      return armies.filter(x => x.alive && x.id !== a.id &&
        Math.max(Math.abs(x.r - a.r), Math.abs(x.c - a.c)) <= 1);
    }
    function enemiesOf(a, visibleOnly) {
      return armies.filter(x => x.alive && x.id !== a.id && dip(a.side, x.side) === 'hostile' &&
        (!visibleOnly || a.lastSeen[x.id] != null));
    }
    function distKm(a, b2) {
      return Math.hypot(a.r - b2.r, a.c - b2.c) * T.cellKm;
    }
    function nearest(a, list) {
      let best = null, bd = Infinity;
      for (const x of list) { const d = distKm(a, x); if (d < bd) { bd = d; best = x; } }
      return best ? { army: best, d: bd } : null;
    }
    function setIntent(a, intent, targetCell, reason) {
      a.intentActive = intent; a.reason = reason;
      if (targetCell) {
        const p = aStar(T, { r: a.r, c: a.c }, targetCell, a.type);
        a.path = p ? p.path : null;
      } else a.path = null;
    }

    // 15 条规则（命中即返回）
    function cheb(a, r, c) { return Math.max(Math.abs(a.r - r), Math.abs(a.c - c)); }
    function ensurePath(a, cell) {
      a.targetCell = cell || null;
      if (!cell) { a.path = null; return; }
      if (cheb(a, cell.r, cell.c) <= 0) { a.path = null; return; }
      if (a.path && a.path.length > 1) return;              // 还有余量，沿路继续
      const p = aStar(T, { r: a.r, c: a.c }, cell, a.type);
      a.path = p ? p.path : null;
    }
    function setIntent(a, intent, cell, reason) {
      a.intentActive = intent; a.reason = reason;
      ensurePath(a, cell);
    }
    function resolveIntent(a) {
      const all = armies.filter(x => x.alive && x.id !== a.id && dip(a.side, x.side) === 'hostile');
      const adj = neighbors(a).filter(x => dip(a.side, x.side) === 'hostile');
      if (adj.length) { setIntent(a, 'fight', { r: adj[0].r, c: adj[0].c }, '规则1：相邻有敌，应战'); return; }
      if (a.morale === 'broken') { setIntent(a, 'rout', farthestCellFrom(a, all), '规则2：士气崩溃，溃逃'); return; }
      const around = all.filter(x => cheb(a, x.r, x.c) <= 3);
      if (around.length >= 3) {
        const weakest = around.reduce((p, q) => (p.strength <= q.strength ? p : q));
        setIntent(a, 'breakout', { r: weakest.r, c: weakest.c }, '规则3：被围，向敌最弱处突围'); return;
      }
      const chokeNear = T.choke.find(ch => cheb(a, ch.r, ch.c) <= 3);
      const nd = nearest(a, all);
      if (chokeNear && nd && nd.d < 20 && a.intent === 'ambush') { setIntent(a, 'ambush', chokeNear, '规则4：据关隘设伏'); return; }
      if (a.supply <= 0 && (a.noSupplyTurn || 0) >= 3) { setIntent(a, 'retreat', null, '规则5：断粮，撤退'); return; }
      const hg = T.high.find(h => cheb(a, h.r, h.c) <= 2);
      if (hg && nd && nd.d < 12 && (a.intent === 'hold' || a.morale === 'wavering')) {
        setIntent(a, 'hold', { r: hg.r, c: hg.c }, '规则6：占制高点据守'); return;
      }
      if (nd && a.strength >= 3 * nd.army.strength && nd.d < 25) {
        setIntent(a, 'advance', { r: nd.army.r, c: nd.army.c }, '规则7：兵力三倍，主动进军'); return;
      }
      const ally = armies.find(x => x.alive && x.side !== a.side && dip(a.side, x.side) === 'allied' && cheb(a, x.r, x.c) <= 1);
      if (ally && neighbors(ally).some(e => dip(ally.side, e.side) === 'hostile')) {
        setIntent(a, 'support', { r: ally.r, c: ally.c }, '规则8：盟友被攻，支援'); return;
      }
      if (chokeNear && nd && nd.d > 25) { setIntent(a, 'seize', chokeNear, '规则9：抢占空关隘'); return; }
      if (a.hunt) {
        const e = armies.find(x => x.id === a.hunt && x.alive);
        if (e) {
          const known = a.lastSeen[e.id] || { r: e.r, c: e.c };
          const seen = !!a.lastSeen[e.id];
          setIntent(a, a.intent === 'rush' ? 'rush' : 'advance', { r: known.r, c: known.c },
            (seen ? '追击：' : '追向最后已知位置：') + e.name);
          return;
        }
        a.hunt = null;
      }
      if (a.target) {
        const tc = toCell(T.b, a.target[0], a.target[1], n);
        const d = cheb(a, tc.r, tc.c);
        if (d <= 1) { setIntent(a, 'hold', null, '规则10：目标已达'); return; }
        const holder = all.find(e => e.r === tc.r && e.c === tc.c);
        if (holder) { setIntent(a, 'advance', { r: holder.r, c: holder.c }, '规则11：目标被敌占，进攻'); return; }
        if (a.intent === 'escape' && d <= 10) { setIntent(a, 'rush', tc, '规则12：接近逃脱点，急行军'); return; }
        if (a.intent === 'hold') { setIntent(a, 'hold', { r: tc.r, c: tc.c }, '规则13：据守目标'); return; }
        setIntent(a, a.intent === 'rush' ? 'rush' : 'advance', tc,
          a.intent === 'escape' ? '规则13：执行突围（朝逃脱点）' : '规则13：执行进军');
        return;
      }
      setIntent(a, 'hold', null, '规则15：无目标，据守原地');
    }

    function farthestCellFrom(a, list) {
      if (!list.length) return { r: a.r, c: a.c };
      let sumR = 0, sumC = 0;
      for (const x of list) { sumR += x.r; sumC += x.c; }
      const ar = sumR / list.length, ac = sumC / list.length;
      const dr = a.r - ar || (rand() - 0.5), dc = a.c - ac || (rand() - 0.5);
      const k = Math.max(Math.abs(dr), Math.abs(dc)) || 1;
      const rr0 = Math.max(0, Math.min(n - 1, Math.round(a.r + dr / k * 8)));
      const cc0 = Math.max(0, Math.min(n - 1, Math.round(a.c + dc / k * 8)));
      return { r: rr0, c: cc0 };
    }

    // 战斗（兰彻斯特平方律简化）
    function combat(att, def, T) {
      const attPow = att.strength * (att.intentActive === 'ambush' ? 2 : 1) *
        ((att.type === 'cavalry' && def.type !== 'cavalry') ? 1.3 : 1);
      const defPow = def.strength * (def.intentActive === 'hold' ? 1.3 : 1) *
        (T.high.some(h => Math.hypot(h.r - def.r, h.c - def.c) <= 1) ? 1.5 : 1);
      const ratio = (defPow * defPow) / (attPow * attPow + defPow * defPow || 1);
      const attLoss = Math.max(1, Math.round(att.strength * ratio));
      const defLoss = Math.max(1, Math.round(def.strength * (1 - ratio)));
      const winner = defPow >= attPow ? def : att;
      const loser = winner === att ? def : att;
      att.strength = Math.max(0, att.strength - attLoss);
      def.strength = Math.max(0, def.strength - defLoss);
      loser.morale = downMorale(loser.morale);
      winner.morale = upMorale(winner.morale);
      // 败方后撤 1 格（沿远离胜方方向）
      const dr = Math.sign(loser.r - winner.r), dc = Math.sign(loser.c - winner.c);
      const rr = Math.max(0, Math.min(n - 1, loser.r + (dr || 1)));
      const cc = Math.max(0, Math.min(n - 1, loser.c + (dc || 0)));
      loser.r = rr; loser.c = cc; loser.path = null;
      if (loser.strength <= 0) loser.alive = false;
      return {
        winner: winner.id, loser: loser.id, attacker: att.id, defender: def.id,
        attackerLoss: attLoss, defenderLoss: defLoss, pos: toLngLat(T.b, winner.c, winner.r, n),
      };
    }
    const MORALE = ['broken', 'wavering', 'steady', 'high'];
    function downMorale(m) { return MORALE[Math.max(0, MORALE.indexOf(m) - 1)]; }
    function upMorale(m) { return MORALE[Math.min(3, MORALE.indexOf(m) + 1)]; }

    function checkEvents() {
      const fired = [];
      for (const ev of (campaign.events || [])) {
        if (ev.__done) continue;
        const tr = ev.trigger || {};
        let ok = true;
        if (tr.turn != null) ok = ok && turn >= tr.turn;
        if (tr.army) {
          const a = armies.find(x => x.id === tr.army);
          if (!a) ok = false;
          if (tr.strengthBelow != null) ok = ok && a.strength < tr.strengthBelow;
          if (tr.moraleIs) ok = ok && a.morale === tr.moraleIs;
        }
        if (!ok) continue;
        ev.__done = true;
        const target = ev.target ? armies.find(x => x.id === ev.target) : null;
        if (ev.action === 'moraleChange' && target) target.morale = ev.value || downMorale(target.morale);
        if (ev.action === 'surrender' && target) { target.alive = false; target.surrendered = true; }
        fired.push({ action: ev.action, target: ev.target, reason: ev.reason || '' });
      }
      return fired;
    }

    function victory() {
      const v = campaign.victory;
      if (!v) return null;
      const tgt = v.target ? armies.find(a => a.id === v.target || a.name === v.target) : null;
      if (v.type === 'annihilate' && tgt && !tgt.alive) {
        return { winner: v.side, type: 'annihilate', description: (tgt.name || tgt.id) + ' 被消灭' };
      }
      if (v.type === 'rout') {
        const side = v.side;
        const foes = armies.filter(a => a.alive && a.side !== side);
        if (foes.length && foes.every(a => a.morale === 'broken')) {
          return { winner: side, type: 'rout', description: '敌军全线崩溃' };
        }
      }
      if (v.type === 'escape' && tgt) {
        const tc = toCell(T.b, tgt.target ? tgt.target[0] : tgt.pos[0], tgt.target ? tgt.target[1] : tgt.pos[1], n);
        if (tgt.alive && Math.max(Math.abs(tc.r - tgt.r), Math.abs(tc.c - tgt.c)) <= 1) {
          return { winner: tgt.side, type: 'escape', description: (tgt.name || '') + ' 突围成功' };
        }
      }
      if (v.type === 'reachPoint' && tgt) {
        const tc = toCell(T.b, v.point[0], v.point[1], n);
        if (tgt.alive && Math.max(Math.abs(tc.r - tgt.r), Math.abs(tc.c - tgt.c)) <= 1) {
          return { winner: tgt.side, type: 'reachPoint', description: '到达目标点' };
        }
      }
      return null;
    }

    function tick() {
      turn++;
      const moves = [], encounters = [], notes = [];
      const sorted = armies.filter(a => a.alive).sort((x, y) => (armyTypeSpeed[y.type] || 1) - (armyTypeSpeed[x.type] || 1));

      for (const a of sorted) {
        if (!a.alive) continue;
        // 情报：只看得到视野内敌军
        const all = armies.filter(x => x.alive && x.id !== a.id && dip(a.side, x.side) === 'hostile');
        for (const e of all) {
          const vis = T.visible(a, e) && distKm(a, e) <= 30;
          if (vis) a.lastSeen[e.id] = { r: e.r, c: e.c };
          else if (a.lastSeen[e.id]) {
            const fz = Math.floor(rand() * 3);
            a.lastSeen[e.id] = {
              r: Math.max(0, Math.min(n - 1, a.lastSeen[e.id].r + Math.floor(rand() * 5) - 2)),
              c: Math.max(0, Math.min(n - 1, a.lastSeen[e.id].c + Math.floor(rand() * 5) - 2)),
              fuzzy: fz,
            };
          }
        }
        resolveIntent(a);
        const from = { r: a.r, c: a.c };
        if (a.path && a.path.length > 1) {
          const stepKm = dayKmOf(a);
          const stepCells = Math.max(1, Math.round(stepKm / T.cellKm));
          let walked = 0;
          while (walked < stepCells && a.path.length > 1) {
            const nxt = a.path[1];
            if (!T.passable(nxt.r, nxt.c, a.type)) { a.path = null; break; }
            a.r = nxt.r; a.c = nxt.c; a.path.shift(); walked++;
          }
          a.fatigue = Math.min(5, a.fatigue + stepKm / 45);
          a.pos = toLngLat(T.b, a.c, a.r, n);
        }
        if (from.r !== a.r || from.c !== a.c) {
          moves.push({ armyId: a.id, name: a.name, side: a.side, intent: a.intentActive,
            from: toLngLat(T.b, from.c, from.r, n), to: a.pos.slice(),
            reason: a.reason, animation: a.intentActive === 'rush' ? 'dashed' : a.intentActive === 'flank' ? 'curved' : a.intentActive === 'rout' ? 'scatter' : 'solid' });
        }
        // 遭遇
        for (const e of neighbors(a)) {
          if (dip(a.side, e.side) !== 'hostile') continue;
          if (!a.alive || !e.alive) continue;
          const res = combat(a, e, T);
          encounters.push(res);
          highlights.push({ type: 'clash', pos: res.pos, label: '遭遇' });
          notes.push(a.name + ' 与 ' + e.name + ' 交战，' + res.winner + ' 胜');
        }
        // 据点/渡河高亮
        if (T.fords.some(f => Math.abs(f.r - a.r) <= 1 && Math.abs(f.c - a.c) <= 1)) {
          highlights.push({ type: 'ford', pos: a.pos, label: '渡口' });
        }
        if (T.choke.some(f => f.r === a.r && f.c === a.c)) {
          highlights.push({ type: 'choke', pos: a.pos, label: '关隘' });
        }
        if (a.supply > 0) a.supply -= 0.2; else a.noSupplyTurn = (a.noSupplyTurn || 0) + 1;
        if (a.fatigue >= 4 && rand() < 0.7) a.morale = downMorale(a.morale);
        if (a.morale === 'broken') a.alive = false;
        if (a.strength <= 0) a.alive = false;
      }

      const fired = checkEvents();
      for (const f of fired) notes.push(f.reason || f.action);
      const v = victory();
      const result = { turn, moves, encounters, highlights, events: fired, notes, victory: v || null };
      log.push(result);
      return result;
    }

    return {
      campaign, terrain: T, armies, tick, turn: () => turn, log,
      runAll() {
        const res = [];
        for (let i = 0; i < (campaign.duration || 30); i++) {
          const r = tick();
          res.push(r);
          if (r.victory) break;
        }
        return res;
      },
      setIntent(armyId, intent, target) {
        const a = armies.find(x => x.id === armyId);
        if (!a) return false;
        a.intent = intent;
        a.intentActive = intent;
        if (target) a.target = target;
        return true;
      },
    };
  }

  return { Sim, analyze, aStar, bbox, toCell, toLngLat, version: '1.0.0' };
});
