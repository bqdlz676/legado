> ⚠️ 本切片含时代口径（WebView/React/桌面/七色等），冲突处以 `docs/立项-冲突裁决.md` 第三节为准（2026-11-25）；正文不改。

核心原则：3D元素用来烘托氛围，不能抢夺正文注意力，主次分明。



-  particle field ：粒子场背景
-  3D orb / sphere ：3D球体，科技视觉核心
-  shader gradient ：着色器渐变，流动色彩
-  floating geometry ：漂浮几何体
-  camera parallax ：相机视差效果
-  interactive canvas ：交互式画布


推荐工具


1. Three.js：底层JS 3D库，跨平台、性能强
2. React Three Fiber（R3F）：React封装版Three.js，组件式开发


- 定位：作为背景，不遮挡文字内容
- 性能：轻量级、稳定帧率
- 复杂度：不要过于复杂模型
- 风格：科技、极简、未来


要点：空间感 = 深度 + 层次 + 沉浸感；3D背景是拉开页面差距最快的一层。


第四层：像「真实产品」｜组件系统，决定产品感


核心观点：页面不是零散元素拼出来的，靠统一设计系统支撑。



-  design system ：设计系统，保持全局视觉一致
-  component variants ：组件变体，适配不同状态
-  shadcn/ui ：现代化可定制组件库
-  Radix primitives ：无样式底层组件，可控性强
-  Tailwind CSS ：原子化CSS，快速构建界面
-  dashboard layout ：仪表盘信息布局
-  command palette ：命令面板
-  modal / toast / empty state ：弹窗、轻提示、空状态，完善反馈链路


推荐工具


- shadcn/ui：基于Radix+Tailwind，可直接复制源码，高度可扩展
- Radix：无样式原语，无障碍能力优秀
- Tailwind CSS：原子化样式，响应式开发



1. 组件统一：同类组件一套样式
2. 状态完整：默认/悬浮/点击/禁用/加载/空状态全部覆盖
3. 层级清晰：标题、正文、辅助信息层级明确
4. 适配到位：桌面、平板、手机多端适配


要点：组件系统 = 一致性 + 完整性 + 体验感；统一组件语言，是产品级页面的标志。


 


一句话完整Prompt模板（可以直接拿去给AI生成页面）


使用Tailwind CSS + shadcn/ui + Radix UI，页面底层使用backdrop-blur、soft shadow、noise texture营造材质质感；加入Motion微交互、spring动画、scroll reveal；背景使用轻量Three.js粒子场作为烘托，不遮挡正文；搭建完整design system，包含Button、Card、Input、Modal、Toast、Empty State全套组件变体，完善悬浮、加载、禁用状态；采用bento grid流式响应布局，移动端优先，风格极简科技，克制不花哨。
这就是我需要的把四层框架应用到落文所有页面：


---


## 落文全页面 · 四层框架应用


### 书架页


| 层 | 关键词 |
|---|---|
| 材质 | `soft shadow` 暖金微光（`0 0 12px rgba(212,165,74,0.3)`）、`bento grid` 竖排卡片栅格、`noise texture` 宣纸颗粒底 |
| 动效 | `scroll reveal` + `stagger` 卡片逐个浮入 350ms、选中卡片 `spring` 前倾 3D 旋转 5°、删除卡片碎成墨滴 |
| 空间 | 卡片 `camera parallax` 微前倾、底部金色微粒 `particle field` 慢飘 |
| 组件 | Card 竖排书名+朱砂进度线、状态覆盖：默认/悬浮/点击/删除/空书架 |


---


### 阅读页


| 层 | 关键词 |
|---|---|
| 材质 | `gradient mesh` 左上暖光渐变（烛光 2800K）、`noise texture` 宣纸颗粒全覆盖、页边朱砂竖线 `subtle border` |
| 动效 | 翻页 `parallax` 掀角 400ms（背面墨透 15%）、气泡 `spring` 弹入 250ms、字面墨爆 100ms 冲击波 |
| 空间 | 章节氛围 `particle field` 水墨轮廓粒子（《城濮》→ 战场剪影） |
| 组件 | 气泡统一容器（字/人物/地名/典故四种变体）、半屏 Sheet（标准60%高+金杠拖拽器+茶色遮罩） |


---


### 发现页


| 层 | 关键词 |
|---|---|
| 材质 | `bento grid` 分区栅格（战役横卡+典籍竖卡+专题通栏）、`backdrop-blur` 专题卡片毛玻璃底、`subtle border` 暖金边框 |
| 动效 | `scroll snap` 战役精选横向吸附、`stagger` 典籍竖卡逐行浮入、`smooth transition` 分区切换 |
| 空间 | 竖排典籍卡片 `camera parallax` 微前倾（与书架同调性） |
| 组件 | 三种卡片变体：战役横卡（暖金边+进度条）/典籍竖卡（书名+朝代）/专题通栏卡（标题+典故数） |


---


### 搜索页


| 层 | 关键词 |
|---|---|
| 材质 | `soft shadow` 聚焦时输入框浮起、`subtle border` 云灰 1px 底线 |
| 动效 | 输入时结果 `stagger` 渐显、`spring` 聚焦动画 |
| 空间 | —（无 3D，搜索页保持平面干净） |
| 组件 | 输入框组件（默认/聚焦/有结果/无结果）、空状态：云灰小字"搜书名、作者、或正文中的任意一句" |


---


### 设置页


| 层 | 关键词 |
|---|---|
| 材质 | `subtle border` 云灰细线分隔分组，不套 Material 卡片 |
| 动效 | 开关 `spring` 弹动、列表项点击 `micro-interactions` 120ms 朱砂反馈 |
| 空间 | — |
| 组件 | 设置行组件（竖排标题+横排说明+右侧开关/箭头）、分组标题 14sp 石青色 |


---


### 纪年时间轴页


| 层 | 关键词 |
|---|---|
| 材质 | `gradient mesh` 暗底暖光渐变 |
| 动效 | `scroll snap` 年份吸附切换、`spring` 年份数字金粉粒子重组 |
| 空间 | `particle field` 金色粒子环 + 流动粒子数字 |
| 组件 | Timeline 组件（政权行+年份节点+左右箭头） |


---


### 人物详情 Sheet


| 层 | 关键词 |
|---|---|
| 材质 | `backdrop-blur` 茶色毛玻璃遮罩、`subtle border` 金框 Sheet |
| 动效 | `spring` 弹起 250ms、`smooth transition` 下拉关闭跟手 |
| 空间 | — |
| 组件 | Sheet 组件（30%/60%/90% 三段高度+金杠拖拽器）、信息分区：即时信息→时间轴→档案→同名候选 |


---


### 全局组件库（覆盖所有页面）


| 组件 | 状态覆盖 |
|---|---|
| InkButton | 默认 / 悬浮（朱砂 8% 背景）/ 点击（朱砂 16% 背景）/ 禁用（云灰 30% opacity）/ 加载（三个金点依次亮灭） |
| Card | 默认 / 悬浮（暖金阴影增强）/ 点击（朱砂阴影）/ 删除（碎墨粒消散） |
| Bubble | 字 / 词 / 典故 / 人名 / 地名 / 年号 六种变体 + 加载态 + 空数据态 |
| BottomSheet | 30% / 60% / 90% 三段高度 + 拖拽 + 遮罩 + 关闭回弹 |
| Toast | 成功（暖金）/ 失败（朱砂）/ 提示（石青）三色 |
| EmptyState | 图标 + 一行引导文字 + 可选操作按钮 |
| Divider | 云灰细线 / 金线 / 朱砂线 三种 |
| Progress | 朱砂竖线（书架/阅读页边）/ 蚕丝金线（时间轴）两种 |
| Timeline | 竖排节点 + 横排对照行 + 左右箭头 |


---


**一句话 Prompt**：使用 Tailwind CSS + Motion + backdrop-blur + noise texture，按四层框架覆盖落文全部页面（书架/阅读/发现/搜索/纪年/人物/设置），组件库包含 InkButton/Card/Bubble/BottomSheet/Toast/EmptyState/Timeline 七件套，每种组件覆盖默认/悬浮/点击/禁用/加载/空数据六态，移动端优先，全局只用纸白/墨色/朱砂/暖金/古铜/石青/茶玻七个颜色 Token。点一下不知道出什么：人名命中靠 findPersonHits 的正则国名+称谓匹配。文章里「周公」「楚子」「桓公」这种不带姓的称呼，正则会漏，CBDB 也不归并，等于《左传》里最高频的那些人全是盲区。
一人的最后 100 米没走完：现在点了人名 → 看到生卒身份。但读《左传》的人真正要的是「这人干过什么」「他跟这章里出现的另几个人什么关系」「这一年他多少岁」。这些你数据有，没接成一条线。
CBDB 摆了但没磨：8.9 万条导入，但没有做同名区分（多个张良、多个李广）、没有标可能性等级、没做人工校对。用户点一次碰到脏数据，就再也不会信了。三个建议，不动代码只说方向
在《左传》《史记》《三国志》《资治通鉴》四本书上，手工维护 200 人的核心档案（别名、出场章、关键事迹时间戳），这是你可以保证 100% 对的体验；CBDB 降级成兜底索引
点人名出来不是一张卡，是一个迷你时间轴 + 这篇里谁跟他一起出现了，底下才是生卒身份。前者对阅读有用，后者对查阅有用，主次别反1.7 万汉字对古籍阅读确实不够。《康熙字典》4.7 万字、《说文》只有 9452 条。读《左传》《史记》碰冷僻字就哑了。字量扩到 4 万~5 万，三个免费来源直接拼：来源字数内容版权康熙字典（ctext.org 数字化版）47,000+字头 + 反切 + 释义 + 书证公版（1716 年）CC-CEDICT120,000+ 词条简体/繁体 + 拼音 + 英文释义CC BY-SA 4.0 免费商用Unihan 数据库（Unicode 官方）100,000+ 字符部首/笔画/拼音/释义/异体Unicode 许可，随便用康熙字典是公版全文，ctext.org 已经有结构化的 txt 版本（一字一行，反切+义项+书证都在），直接拖下来解析成你现在的 char_dict.txt 格式（字 + 拼音 + 部首 + 笔画 + 义项）。CC-CEDICT 是开源中英词典，120,000 词条，格式现成 繁体 简体 [拼音] /英文释义/，可以直接并进 zh_ci.txt。Unihan 补生僻字的基础属性（部首、笔画、异体），给康熙字典里没有拼音的冷僻字兜底。实际操作：ctext.org 的康熙字典 txt → 写一个 Python 脚本把你的 char_dict.txt 从 1.7 万条 merge 到 4.7 万条，去重优先保留你原有的数据，新增的自动补充部首笔画。一个脚本跑一次搞定，不用人工好，你的四层框架已经完整了，我只补**漏掉的、跟落文强相关的、AI 拿到就能直接用的**。不重述你已有的。


---


# 落文 UI 四层框架 · 增补


---


## 第 0 层：字体系统（阅读 App 的命，你四层没覆盖到）


阅读应用里，**字体就是界面本身**。这一层放在材质之前，是地基里的地基。


| 用途 | 字体 | 格式 | 加载策略 |
|---|---|---|---|
| 正文（宋体） | Noto Serif SC | woff2 subset | 优先预载，`font-display: swap` |
| 标题 | 思源宋体 SemiBold | woff2 | 首屏阻塞，swap 不允许 FOIT |
| 注音小字 | Noto Sans SC Light 10sp | woff2 | 延迟加载 |
| 数字/年份/年代 | Inter（Tabular Nums） | woff2 subset numbers only | 极小子集，inline 也可 |
| 等宽（典籍编号、CBDB ID） | JetBrains Mono | woff2 | 按需 |


**CJK subset 是关键**：完整中文字体 5-10MB，阅读 App 必须用 `unicode-range` 分片加载，正文常用 2500 字首屏子集 ≤ 200KB。


**CSS 变量定义（给 AI 的直接模板）**：
```css
:root {
 --font-body: 'Noto Serif SC', 'Source Han Serif SC', serif;
 --font-title: 'Source Han Serif SC', serif;
 --font-label: 'Noto Sans SC', sans-serif;
 --font-number: 'Inter', sans-serif;
 --font-size-body: clamp(16px, 4.5vw, 20px);
 --line-height-body: 1.8;
 --letter-spacing-body: 0.02em;
}
```


---


## 材质层增补：颜色 Token 系统（不是调色板，是语义）


你列了 CSS 技术，缺了一套颜色 Token。Tailwind 的 `slate-50` 不能用，阅读 App 的颜色必须有语义名。


```css
:root {
 /* 背景三阶 */
 --bg-page: #F7F1E6; /* 阅读页纸白 */
 --bg-surface: #EFE8D8; /* 卡片/Sheet 微黄 */
 --bg-overlay: rgba(26,20,16,0.45); /* 遮罩茶玻 */


 /* 文字四阶 */
 --text-primary: #1A1410; /* 正文墨色 */
 --text-secondary: #5C4F3A; /* 副文 */
 --text-tertiary: #9B9687; /* 辅助/占位 */
 --text-accent: #C23B2E; /* 朱砂强调 */


 /* 品牌 */
 --brand-gold: #D4A54A; /* 暖金 */
 --brand-copper: #8B6914; /* 古铜 */


 /* 功能色 */
 --color-link: #C23B2E;
 --color-divider: rgba(26,20,16,0.08);
 --color-glow: rgba(212,165,74,0.3); /* 暖金辉光 */
}
```


只这 12 个变量。落文全站就靠这 12 个。


---


## 动效层增补：缓动曲线表（别让 AI 用 ease-in-out 糊弄）


你提了 easing curve 但没给值。这四个是落文专用，直接贴：


| 用途 | CSS easing | 感觉 |
|---|---|---|
| 卡片浮入 (stagger reveal) | `cubic-bezier(0.22, 0.61, 0.36, 1)` | 减速入，稳落 |
| 气泡弹入 (spring) | `cubic-bezier(0.34, 1.56, 0.64, 1)` | overshoot 回弹 |
| 翻页掀角 | `cubic-bezier(0.4, 0, 0.2, 1)` | 匀速掀、急落 |
| 遮罩淡入淡出 | `cubic-bezier(0.4, 0, 0.6, 1)` | 对称，不抢 |


**duration 常数（全局统一）**：
- 微交互：120ms
- 卡片/气泡：250ms
- 页面转场：350ms
- 遮罩：200ms
- 启动页粒子：2800ms（整段编排，不拆分）


---


## Three.js 层增补：落文专用约束


你列的粒子场、3D orb、floating geometry 是通用科技风。落文用 Three.js 的地方必须换语义——**不要科技感，要东方感**。


| 落文场景 | Three.js 内容 | 粒子上限 | 帧率底线 |
|---|---|---|---|
| 启动页 | 墨滴坠落 + 金粉粒子 | 60 | 55fps |
| 阅读页背景 | 水墨氛围粒子（极淡，不高于 #000 8%） | 40 | 保持 60fps，否则降级到静态 |
| 地图 3D 地形 | 高程地形 + 光柱 + 粒子上升 + 冲击环 | 200 | 45fps |
| 纪年金环 | 环形粒子 + 流动数字粒子 | 80 | 55fps |


**关键约束**：
- 离开可见区域必须 `dispose()`（地图页是最重灾区——离开即销毁 geometry/material/texture，不然 3 秒内存泄漏）
- 低端机检测：`navigator.hardwareConcurrency < 4` 或 Android ≤ 8 → 粒子数减半、地形降精度、冲击环跳过
- 阅读页背景的粒子在用户**滚动 3 秒后自动暂停**（`renderer.setAnimationLoop(null)`），不滚动就恢复。读古书的人可能盯一页五分钟，不能白烧 GPU


---


## 组件层增补：缺四个落文专属组件


你列的 Button/Card/Input/Modal/Toast/Empty 是通用组件池。落文多出四个专属的，**不在 shadcn 里，必须定制**：


