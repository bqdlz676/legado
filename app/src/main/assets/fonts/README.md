# 字体子集资产（U9 字体模块收口）

## 当前状态：子集已实际生成并打包

五枚首屏 / 按需子集已由 `subset_generate.py` 从 OFL 源字体裁出，落在本目录；
`io.legado.app.ui.font.InkFontLoader` 启动早期预载正文 / 标题两角色，注音小字
延迟、数字 / 等宽按需取用。子集未覆盖的生僻字自动回落系统 serif / sans / mono，
不降级、不出空白态、不崩溃。

逐字 cmap 覆盖核对见 `coverage.json`；源字体出处与许可见 `LICENSE-OFL.txt`。

## 字体分工（切片-14 逐字落地）

| 角色 | 字体 | 策略 | 打包子集 | 体积 | 兜底 |
|---|---|---|---|---|---|
| 正文宋体 | Noto Serif CJK SC Regular | 预载，font-display: swap | `NotoSerifSC-Regular.otf` | 146.2KB | 系统 serif |
| 标题 | Noto Serif CJK SC SemiBold | 首屏阻塞，swap，不允许 FOIT | `NotoSerifSC-SemiBold.otf` | 147.9KB | 系统 serif 加粗 |
| 注音小字 | Noto Sans CJK SC Light 10sp | 延迟加载 | `NotoSansSC-Light.otf` | 104.1KB | 系统 sans |
| 数字 / 年份 | Inter Regular（tnum） | numbers only 极小子集，按需 | `Inter-TabularNumbers.ttf` | 2.9KB | 系统 sans |
| 等宽（编号 / ID） | JetBrains Mono Regular | 按需加载 | `JetBrainsMono-Regular.ttf` | 6.4KB | 系统 monospace |

## 字表口径

- 字表文件：`chars/luowen-core.txt`，由内置读物词频 top550 汉字 + 灵魂闭环 /
  纪年 / 气泡 / 启动落款 / 搜索页必覆盖字 + ASCII 与中文标点组成（共 653 个可用字符）。
- 规格目标「正文常用 2500 字」是 web 端 woff2 口径；Android 内嵌 OTF 在 200KB
  红线内裁不下 2500 个 CJK 字形，故按体积红线取核心高频字表，生僻字回落系统
  全量 CJK 字。后续如需扩字，调 `subset_generate.py --top-n` 并重跑，体积超红线
  脚本会直接报错拒绝打包。
- 重写字表：`python3 subset_generate.py --refresh-chars --books-dir ../../books`。

## 许可红线

- 全部源字体为 **SIL OFL 1.1**（Noto Serif/Sans CJK、Inter、JetBrains Mono），
  允许应用内嵌与再分发；无 GPL/LGPL/MPL 等 copyleft 字体。
- 未引入 Wikipedia / OpenStreetMap / CC-CEDICT 等数据或其派生字体资产。
- 字体来源、版本、版权方登记在 `LICENSE-OFL.txt`，与气泡数据「每条有出处」同口径。

## 收口后如何被识别

文件名命中 `manifest.json` 对应 `candidates` 任一即被 `FontRegistry` 按优先级
注入（大小写不敏感）。粗体不单独打包：View 侧由 `Typeface.create(serif, BOLD, false)`
派生，系统缺粗重时 `Paint.isFakeBoldText` 合成加粗。

## 不在本层做的事

- 不硬编码字体名猜平台字体；
- 不在阅读层下载字体（离线 / 流量受控）；
- 注音小字不随宋体走，按规格用无衬线 10sp；
- 超出体积红线的内置资产会被加载器拒绝并回落系统字体，不强行打包。
