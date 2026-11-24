# 锚点卡 · S-03 设计 token 与四层框架（12 色）

> 用途：派 S-03 写码单前的"前置设计费"（F-85）。写码代理必须先读本卡，再做实现。
> 生成：2026-11-25 调度层；逐字引文，禁转述。

## 一、逐字引文（文件:行 + 原文）

- `docs/落文-模块契约索引.md:59`：「bg-page #F7F1E6 · bg-surface #EFE8D8 · bg-overlay rgba(26,20,16,.45) · text-primary #1A1410 · text-secondary #5C4F3A · text-tertiary #9B9687 · text-accent #C23B2E · brand-gold #D4A54A · brand-copper #8B6914 · color-link #C23B2E · color-divider rgba(26,20,16,.08) · color-glow rgba(212,165,74,.3)」
- `docs/落文-模块契约索引.md:57`：「颜色只引用 12 色 token 常量（不硬编码十六进制）」
- `docs/落文-模块契约索引.md:60`：「关 MaterialTheme，Compose 自绘；禁用 Material 默认 ripple / elevation / shared-axis 转场」
- `docs/落文-模块契约索引.md:63`：「已有实现绝不复用，只做参考；改动后 grep 确认真正写入；"编译通过 ≠ 功能可用"」
- 冲突裁决：`docs/立项-冲突裁决.md` 第三节（七色 vs 十二色 → 取 12 色，七色表述作废）

## 二、参数表（实现即照抄这张表，不许改名/删项）

| token | 值 |
|---|---|
| bg-page | #F7F1E6 |
| bg-surface | #EFE8D8 |
| bg-overlay | rgba(26,20,16,.45) |
| text-primary | #1A1410 |
| text-secondary | #5C4F3A |
| text-tertiary | #9B9687 |
| text-accent | #C23B2E |
| brand-gold | #D4A54A |
| brand-copper | #8B6914 |
| color-link | #C23B2E |
| color-divider | rgba(26,20,16,.08) |
| color-glow | rgba(212,165,74,.3) |

（`text-accent` 与 `color-link` 同为 #C23B2E 是冻结原样，不许"合并成一项"——语义不同，误合并会在暗色/强调场景各自失效。）

## 三、缓动与时长（对 `docs/落文-规格切片/切片-03-设计token与四层框架.md` 与 `切片-15-全局动效铁律.md` 逐条对齐，不在此复抄）

- 四条缓动曲线、五档时长、spring 刚度/阻尼的具体数值一律以切片原文为唯一真值源；
- 本卡只约束"落点"：常量集中在一处（单源），页面里禁止内联写时长/曲线。

## 四、判据（验收可机械判）

1. 全仓 UI 层 `grep -rn "0x[0-9A-Fa-f]\{8\}"` 不得出现裸色值（除 token 定义文件本身）；
2. 页面/组件调用处只出现 token 名，不出现十六进制；
3. `MaterialTheme` 在本包 UI 层不可用（grep 0 命中）；
4. 12 项数量对得上 = 12（多一项少一项都判 FAIL）；
5. 改动后必须 grep 回证写入；CI 绿 + 单测全绿才算过 L2。
