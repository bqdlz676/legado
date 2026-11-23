# 宋体子集资产槽位（U9 字体模块收口）

## 当前状态

**当前未打包任何宋体子集，阅读页走系统 serif 兜底。** 荣耀 WIN / HarmonyOS 的系统 serif
即 Noto Serif CJK 宋体类；内置子集就位前，`InkFonts.ensureSerifLoaded` 探测不到资产，
自动回退 `Typeface.SERIF` / `FontFamily.Serif`，不降级、不出空白态。

## 收口后放什么

把 **Noto Serif SC 常用字子集**（正文汉字 + 常用标点；按需裁字，控制体积）放到本目录，
文件名命中以下任一即可被 `io.legado.app.ui.book.read.ink.InkFonts` 自动识别注入：

- `NotoSerifSC-Regular.otf`
- `NotoSerifSC-Regular.ttf`
- `SourceHanSerifSC-Regular.otf`
- `SourceHanSerifSC-Regular.ttf`

粗体不单独打包：View 侧由 `Typeface.create(serif, BOLD, false)` 派生，系统缺粗重时
`Paint.isFakeBoldText` 合成加粗；标题加粗在渲染层按字号阈值识别。

## 许可红线

- 仅允许嵌入 **SIL OFL** 等明确允许应用内嵌与再分发许可的字体（Noto Serif SC / 思源宋体即 OFL）。
- 禁止引入 Wikipedia / OpenStreetMap / CC-CEDICT 等数据或其派生字体资产。
- 字体来源与版本须在字库出处清单登记，与气泡数据「每条有出处」同口径。

## 不在本层做的事

- 不硬编码字体名猜平台字体；
- 不在阅读层下载字体（离线 / 流量受控）；
- 注音小字不随宋体走，按规格用无衬线 10sp。
