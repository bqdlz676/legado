# 设计 · APP 图标（正圆纸白 · 墨字 · 暖金断口环）

> 规格原文（用户拍板，逐字遵守）：**「正圆纸白底 + 墨字 + 暖金 1.5dp 边框 + 左下断 8°」**
> 渲染管线：`dist/icon/generate_icons.py`（PIL 10.4.0 显式绘制，未使用任何生成式模型出图）；
> 像素核对脚本：`dist/icon/measure_icons.py`，原始输出：`dist/icon/measure-report.txt`。

## 1. 设计参数表

### 1.1 颜色（取项目既有 token，未自创）

| 角色 | 色值 | 项目 token |
| --- | --- | --- |
| 纸白底 | `#F7F1E6` | `ink_bg_page`（`colors_ink.xml`） |
| 墨字 | `#1A1410` | `ink_text_primary` |
| 暖金边框 | `#D4A54A` | `ink_brand_gold` |
| 单色剪影 | 黑色轮廓 + alpha，颜色交系统染 | monochrome 层 |

### 1.2 几何基准（自适应图标 108dp 画布）

| 参数 | 值 | 说明 |
| --- | --- | --- |
| 画布 | 108dp × 108dp | 自适应图标标准画布 |
| 圆心 C | (54.0, 54.0) dp | 画布正中心 |
| 安全区半径 | 33.0 dp | 官方 66dp 直径安全区，圆/方遮罩均不切边 |
| 圆环外半径 R_out | 31.0 dp | 距安全区边缘径向留 2.0dp 余量 |
| 圆环内半径 R_in | 29.5 dp | R_out − 边框厚度 |
| 圆环中径 R_mid | 30.25 dp | 弧线路径与断口圆心所在半径 |
| 边框厚度 T | 1.5 dp | 规格定值，按密度换算像素，比例不漂移 |
| 断口中心方向 | 屏幕极角 135°（左下） | 屏幕坐标 x 向右、y 向下；(-0.707, +0.707) |
| 断口起止角 | 131° – 139° | 圆心为顶点，开口弧长 8°（半宽 4°） |
| 断口端头 | 圆头收笔，半径 0.75dp（= T/2），圆心在 R_mid 端点上 | 禁止生硬截断 |
| 字形 | 单字「落」，`NotoSerifSC-SemiBold.otf`（仓库 `app/src/main/assets/fonts/`） | 主方案单字；未出两字备选 |
| 字形可见高度 | 目标 = 圆直径 × 48%；实测落在 45.3% – 47.6%（规格区间 42% – 48%） | 按字形可见 bbox 裁剪后缩放，视觉居中 |
| 字形 em 估算 | 可见高 ≈ 0.94 × em；自适应画布上可见高 29.76dp，对应 em ≈ 31.7dp | 以 bbox 实测为准，不锁固定 point size |
| 行高 | 单字单行，无行高概念；垂直方向按 bbox 中心对齐圆心 | — |

### 1.3 安全区余量实测（前景 PNG 不透明度像素最大半径）

| 密度 | 画布 px | 实测最大半径 px | 安全半径 px | 结论 |
| --- | --- | --- | --- | --- |
| mdpi | 108 | 31.2 | 33.0 | 在安全区内 |
| xxhdpi | 324 | 93.0 | 99.0 | 在安全区内 |
| xxxhdpi | 432 | 124.2 | 132.0 | 在安全区内 |

## 2. 分档密度像素与边框厚度核对

密度倍率 d：mdpi 1.0 / hdpi 1.5 / xhdpi 2.0 / xxhdpi 3.0 / xxxhdpi 4.0。
边框理论厚度 = 1.5dp × d。渲染时先以 SS=4 超采样（厚度 = round(1.5 × d × 4) 超采样像素），
再 LANCZOS 降采样；小数像素厚度拆为「全不透明核心 + 半透明 AA 边」，核对脚本沿正上方射线
（270°）数金色径向连续跨度。

| 密度 | d | 传统图标 px | 自适应前景 px | 边框理论 px | 实测全不透明核心 px | 实测 AA 含边跨度 px（前景） |
| --- | --- | --- | --- | --- | --- | --- |
| mdpi | 1.0 | 48 | 108 | 1.50 | 1 | 3（核心 1 + 两侧半透明） |
| hdpi | 1.5 | 72 | 162 | 2.25 | 2 | 3 |
| xhdpi | 2.0 | 96 | 216 | 3.00 | 3 | 4 |
| xxhdpi | 3.0 | 144 | 324 | 4.50 | 4 | 5 |
| xxxhdpi | 4.0 | 192 | 432 | 6.00 | 6 | 6 |

> 四舍五入规则：超采样空间内厚度取整（`round`），最终降采样保留亚像素 alpha；
> 上表「核心」为 alpha > 0.15 阈值下的全不透明径向 run，AA 边像素不计入核心。
> 断口核对：缺口内部 134°–137° 无金色；132°/133°/138° 命中为圆头收笔边缘（设计使然）；
> 环体其余角度金色覆盖率 327–353/353（单像素采样 + 中径 ±1px 容差）。

## 3. 文件清单

### 3.1 传统 mipmap PNG（圆外透明；round 版正圆遮罩裁切）

| 密度 | `ic_launcher.png` | `ic_launcher_round.png` |
| --- | --- | --- |
| mdpi | 48×48 RGBA | 48×48 RGBA |
| hdpi | 72×72 RGBA | 72×72 RGBA |
| xhdpi | 96×96 RGBA | 96×96 RGBA |
| xxhdpi | 144×144 RGBA | 144×144 RGBA |
| xxxhdpi | 192×192 RGBA | 192×192 RGBA |

路径前缀：`app/src/main/res/mipmap-<dpi>/`。传统版无安全区概念，圆外半径 = 边长/2 × 0.95。

### 3.2 自适应前景 / 单色剪影（透明底，108dp × d）

| 密度 | `drawable-<dpi>/ic_launcher_foreground.png` | `drawable-<dpi>/ic_launcher_monochrome.png` |
| --- | --- | --- |
| mdpi | 108×108 RGBA | 108×108 RGBA |
| hdpi | 162×162 RGBA | 162×162 RGBA |
| xhdpi | 216×216 RGBA | 216×216 RGBA |
| xxhdpi | 324×324 RGBA | 324×324 RGBA |
| xxxhdpi | 432×432 RGBA | 432×432 RGBA |

monochrome 仅用 alpha 表达剪影（黑形透明底），颜色由系统主题染色。

### 3.3 自适应 XML（Android 8+）

- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

两层结构一致：`background = @color/ic_launcher_background`（纸白纯色）；
`foreground = @drawable/ic_launcher_foreground`；`monochrome = @drawable/ic_launcher_monochrome`。

### 3.4 Play 上架与预览

- `dist/icon/playstore-512.png`：512×512 RGB（无透明），整幅纸白铺底，圆外半径 240.64px（0.94 × 256），边框 7.11px，圆内不出界。
- `dist/icon/preview-sheet.png`：4 形态（正圆遮罩 / 方角遮罩 / 圆角遮罩 / 主题单色）× 3 背景（白底 / 纸白底 / 深色底）并排一页。
- `dist/icon/generate_icons.py`：渲染管线源码。
- `dist/icon/measure_icons.py`：像素核对脚本；`dist/icon/measure-report.txt`：核对原始输出。

## 4. Manifest 改动

`app/src/main/AndroidManifest.xml` `<application>` 节点：

- 第 34 行 `android:icon="@mipmap/ic_launcher"`：指向未变，资源已替换为新自适应图标。
- 第 35 行**新增** `android:roundIcon="@mipmap/ic_launcher_round"`。
- `launcher1` … `launcher6` 入口别名图标及其 drawable（`ic_launcher1.xml` … `ic_launcher7.xml`）**未改动**。

颜色：`res/values/colors.xml:123` 已有 `ic_launcher_background = #F7F1E6`，直接复用，未新增色值。

## 5. 旧资源清理证据

### 5.1 “红日版”检索

```
grep -rn "red_sun\|红日\|redSun" app/src/main/res app/src/main/java
```
结果：无任何匹配（exit 1）。仓库内不存在以 `red_sun` / 红日命名或注释的图标资源。

### 5.2 被替换并删除的旧图标资源

删除前引用核对（仅旧自适应 XML 引用，无 Kotlin / 其它 XML 引用）：

- `mipmap-anydpi-v26/ic_launcher.xml` 旧第 4 行：`@mipmap/ic_launcher_fg`
- 同文件旧第 5 行：`@drawable/ic_ink_drop`

XML 改写后复查 `grep -rn "ic_launcher_fg\|ic_ink_drop" app/src/main`：零匹配（exit 1），随后删除：

| 删除文件 | 原用途 |
| --- | --- |
| `mipmap-mdpi/ic_launcher_fg.png` | 旧自适应前景位图 |
| `mipmap-hdpi/ic_launcher_fg.png` | 同上 |
| `mipmap-xhdpi/ic_launcher_fg.png` | 同上 |
| `mipmap-xxhdpi/ic_launcher_fg.png` | 同上 |
| `mipmap-xxxhdpi/ic_launcher_fg.png` | 同上 |
| `drawable/ic_ink_drop.xml` | 旧 monochrome 水滴矢量图 |

旧 `mipmap-*/ic_launcher.png` 五档已被新构图直接覆盖（非删除）。

## 6. 自检记录

- 尺寸断言：20 张分档 PNG 实际边长与第 2、3 节表格逐一断言通过（`measure_icons.py`）。
- 安全区：mdpi / xxhdpi / xxxhdpi 前景最大不透明半径均在 33dp 安全区内（见 1.3）。
- 字形占比：各档 0.453 – 0.476，处于 42% – 48% 区间。
- 透明底：monochrome 四角 alpha=0；round 版圆外 alpha=0；playstore 为 RGB 无 alpha 通道。
- 目检贴图：
  - `dist/icon/preview-sheet.png`（4 形态 × 3 背景整页）
  - `dist/icon/playstore-512.png`（上架图）
  - `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`（144px 传统版抽查）

## 7. 未验证项

- **未编译**：按红线未执行本地 gradle / kotlinc 构建，资源引用与 XML 语法未经构建器解析。
- **未上真机**：各厂商 Launcher 的圆 / 方 / 水滴 / 圆角遮罩实际裁切观感待真机验证。
- **主题单色**：Android 13+ monochrome 染色后的实际配色与对比度待系统环境验证。
- **小尺寸清晰度**：mdpi 48px 下宋体笔画的实际发虚程度待真机桌面目检。
- 预览图标签使用系统全量 CJK 字体（Noto Sans CJK）渲染，仅用于 `dist/` 预览，不随包发布。
