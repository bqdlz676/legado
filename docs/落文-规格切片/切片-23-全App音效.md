# 切片-23 · 全 App 响应音效（InkSoundPool）

> 依据：用户 2026-11-24 原文（逐字照录，禁摘要）；口径 F-34~F-41（`docs/口径冻结-2026-11-24-15点后.md` §十）。落地单号 **D27 / N-016**。

---

## 全 App 响应音效

每个交互有极轻的声音反馈。不难听、不突兀。像一个古人在桌上翻书、研墨、搁笔。

### 音效设计（全用公版采样自合成）

| 交互 | 音效 | 感觉 |
|---|---|---|
| 点按钮/图标 | 极短促的木鱼单敲（20ms） | 像指尖轻叩桌面。不是机械 click |
| 翻页 | 宣纸轻擦声，分两层——掀角时纸面摩擦（80ms）+ 纸落下时轻微闷响（40ms） | 像真的在翻一页薄纸 |
| 气泡墨爆 | 一滴浓墨落入水面的闷响（60ms）+ 金粉飞溅的极细碎响（30ms） | 启 动动画里墨滴坠落的那一声，缩小版 |
| 气泡消失 | 无声 | 不提 |
| 半屏 Sheet 弹起 | 竹简展开的轻擦声（100ms） | 像展开一卷竹简 |
| 半屏 Sheet 关闭 | 同上反向（80ms） | 像卷回去 |
| 长按放大镜出现 | 极轻的玻璃嗡鸣（50ms） | 像手指在玻璃上划 |
| 放大镜消失 | 无声 | — |
| 胶囊 Tab 切换 | 短促的金石相叩声（15ms） | 像两枚铜钱轻碰 |
| 成功（保存/导出完成） | 一声极短的编钟单音（80ms） | 像玉器轻击 |
| 失败/错误 | 极短促的低音木鱼（30ms） | 不刺耳，像"嗯——不对" |
| 删除（右滑碎墨） | 墨滴碎开的细碎声（40ms） | 跟碎墨动画同步 |
| 推演交战 | 大鼓重击（120ms） | 之前配乐那条保留 |
| 推演渡口闪光 | 极轻的水波拍岸（50ms） | — |

### 统一音源

跟推演 BGM 同一个方案——自编 MIDI + FreePats 民乐采样 + VSCO 管弦乐采样 + 自录日常声音（翻纸声自己录一段，公版无版权）。全 App 音效包 < 2MB。

### 开关

**全局开关**：阅读页底栏最右端一个极小的扬声器图标。三个状态循环切——🔊 全响（音效 + BGM）/ 🔈 仅音效（BGM 关）/ 🔇 全静。点一下切一个状态，当前状态用极淡 toast 浮出（"全响" / "仅音效" / "已静音"，1s 消失）。

**设置页保留同一开关**：设置 → 阅读 → 音效反馈（跟上面三态同步）。

**默认**：首次安装全响。用户可以第一次翻页听到纸声就知道有这个功能。想关的人在设置里关。

### 实现

全局 `SoundPool` 单例。所有音效预加载到内存（< 2MB），触发时即时播放零延迟。不需要后台线程——SoundPool 原生异步。

```kotlin
object InkSoundPool {
    private val pool = SoundPool.Builder().setMaxStreams(4).build()
    private val sounds = mutableMapOf<String, Int>()

    fun preload(context: Context) {
        sounds["tap_button"]     = pool.load(context, R.raw.ink_tap, 1)
        sounds["page_turn"]      = pool.load(context, R.raw.ink_page_turn, 1)
        sounds["bubble_ink"]     = pool.load(context, R.raw.ink_bubble, 1)
        sounds["sheet_open"]     = pool.load(context, R.raw.ink_sheet_open, 1)
        sounds["sheet_close"]    = pool.load(context, R.raw.ink_sheet_close, 1)
        sounds["tab_switch"]     = pool.load(context, R.raw.ink_tab, 1)
        sounds["success"]        = pool.load(context, R.raw.ink_success, 1)
        sounds["error"]          = pool.load(context, R.raw.ink_error, 1)
        sounds["delete"]         = pool.load(context, R.raw.ink_delete, 1)
        sounds["magnifier"]      = pool.load(context, R.raw.ink_magnifier, 1)
        sounds["battle_clash"]   = pool.load(context, R.raw.ink_battle, 1)
        sounds["river_cross"]    = pool.load(context, R.raw.ink_river, 1)
    }

    fun play(name: String) { /* 查当前开关状态 → 播或不播 */ }
}
```

全 App 交互处统一调 `InkSoundPool.play("tap_button")`，不做分散调用。开关一关全关。

---

## 验收判据（可度量）

1. 13 项逐个可达 + 时长上限（20/80+40/60+30/0/100/80/50/0/15/80/30/40/120/50ms ±20%）；
2. 明确无声项（气泡消失、放大镜消失）**真的不播**（有断言）；
3. 音效包总体积 <2MB（`du -sb app/src/main/res/raw | tail`）；
4. `InkSoundPool` 是**唯一** SoundPool 实例（全仓 `SoundPool.Builder` 计数=1 或 =2（D26 BGM 一个，须注释区分））；
5. 三态开关：阅读页底栏最右端 + 设置页镜像，双向同步；toast 文案三态正确、1s 消失；
6. 默认全响（首次安装无偏好文件时 `play()` 真出声）；
7. 开关关时零发声（所有调用点无绕开 `InkSoundPool` 的分散播放入口：`grep -rn "SoundPool"` 只命中白名单文件）；
8. 资产缺失/解码失败：`play` 静默 no-op 不崩；预加载失败回退为静音并记一条 warning。
