# ic_launcher（品牌主图 PNG，非 VectorDrawable）
规格原文·第13行：`| 主图标 | `ic_launcher.png` | 玻璃金星 squircle。见 App 图标设计规格独立文档 |`
规格原文·第151行：`- **78 声明 − 1 墨印（F-46 作废）＝ 77 个 VectorDrawable**；主图标 `ic_launcher.png` 属品牌资产（另见 App 图标规格），不在本切片 vector 计数内。`
口径：本图标是 PNG 品牌主图，不属 VectorDrawable，不在 77 个 vector 计数内。
实测 mipmap：anydpi-v26 有 `ic_launcher.xml`(adaptive-icon: bg 色值 / fg=`@mipmap/ic_launcher_fg` / monochrome=`@mipmap/ic_launcher_monochrome`) 及旧实验件 `launcher1.xml`～`launcher6.xml`；hdpi/mdpi/xhdpi/xxhdpi/xxxhdpi 各含 `ic_launcher.png`、`ic_launcher_round.png`、`ic_launcher_fg.png`、`ic_launcher_monochrome.png`。
实测 drawable 旧实验件：`ic_launcher2.xml`、`ic_launcher3.xml`、`ic_launcher4.xml`、`ic_launcher4_b.xml`、`ic_launcher5.xml`、`ic_launcher5_b.xml`、`ic_launcher6.xml`、`ic_launcher7.xml`、`ic_launcher7_b.xml`。以上全部为 Legado 旧资产，落文「玻璃金星 squircle」新主图尚未制作，App 图标规格独立文档亦未落盘。
Manifest 引用（`grep -rn "ic_launcher" app/src/main/AndroidManifest.xml`）：第 34 行 `android:icon="@mipmap/ic_launcher"`，仍指向旧 mipmap 资源。
结论：未制作，待 App 图标设计规格落定；因系品牌 PNG 主图而非切片内 VectorDrawable，不进入 77 计数。
