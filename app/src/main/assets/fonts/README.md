# 宋体子集资产槽位（U9 字体模块收口）

## 当前状态：子集未实际生成

**当前未打包任何字体子集，阅读页走系统 serif 兜底。** 荣耀 WIN / HarmonyOS 的系统
serif 即 Noto Serif CJK 宋体类；内置子集就位前，`io.legado.app.ui.font.InkFontLoader`
探测不到资产，自动回退 `Typeface.SERIF` / `Typeface.SansSerif` / `Typeface.MONOSPACE`，
不降级、不出空白态、不崩溃。

运行时契约见 `manifest.json`；裁字脚本见 `subset_generate.py`（CI / 本地后续跑，
沙箱未执行）。

## 字体分工（切片-14 逐字落地）

| 角色 | 字体 | 策略 | 兜底 |
|---|---|---|---|
| 正文宋体 | Noto Serif SC | 预载，font-display: swap，首屏子集 ≤ 200KB | 系统 serif |
| 标题 | 思源宋体 SemiBold | 首屏阻塞，swap，不允许 FOIT | 系统 serif 加粗 |
| 注音小字 | Noto Sans SC Light 10sp | 延迟加载 | 系统 sans |
| 数字 / 年份 | Inter Tabular Nums | numbers only 极小子集 ≤ 20KB，按需 | 系统 sans |
| 等宽（编号 / ID） | JetBrains Mono | 按需加载 | 系统 monospace |

CJK subset：unicode-range 分片，正文常用 2500 字首屏子集 ≤ 200KB。

## 收口后放什么

把裁好的子集放到本目录，文件名命中 `manifest.json` 对应 `candidates` 任一即被
`FontRegistry` 按优先级识别注入（大小写不敏感）：

- `NotoSerifSC-Regular.otf` / `.ttf`（或 SourceHanSerifSC-Regular 同名）
- `NotoSerifSC-SemiBold.otf` / `.ttf`
- `NotoSansSC-Light.otf` / `.ttf`
- `Inter-TabularNumbers.otf` / `.ttf`
- `JetBrainsMono-Regular.otf` / `.ttf`

粗体不单独打包：View 侧由 `Typeface.create(serif, BOLD, false)` 派生，系统缺粗重时
`Paint.isFakeBoldText` 合成加粗；标题加粗在渲染层按字号阈值识别。

## 许可红线

- 仅允许嵌入 **SIL OFL** 等明确允许应用内嵌与再分发许可的字体（Noto Serif SC /
  思源宋体 / Noto Sans SC / Inter / JetBrains Mono 即此类）。
- 禁止引入 Wikipedia / OpenStreetMap / CC-CEDICT 等数据或其派生字体资产。
- 字体来源与版本须在字库出处清单登记，与气泡数据「每条有出处」同口径。

## 不在本层做的事

- 不硬编码字体名猜平台字体；
- 不在阅读层下载字体（离线 / 流量受控）；
- 注音小字不随宋体走，按规格用无衬线 10sp；
- 超出体积红线的内置资产会被加载器拒绝并回落系统字体，不强行打包。
