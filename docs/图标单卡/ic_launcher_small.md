# ic_launcher_small（F-46 作废，不制作）
规格原文·第14行（逐字摘录）：`| 副图标 | ~~`ic_launcher_small.png`~~ | 墨印正圆（纸白底 + 竖排"落文"墨字 + 暖金边框左下 8° 缺口，通知栏/小尺寸用）——**按 F-46 作废，不制作** |`
规格原文·第160行（逐字摘录）：`5. 无 `ic_launcher_small` / 墨印 / 印章 / stamp / seal 命名或图形（F-46）；`
口径：「作废，不制作」为用户拍板口径 F-46，非待定、非漏排。
引用反证（`grep -rn "ic_launcher_small" app/src/main/ docs/ 2>/dev/null | head`）：共 2 条命中，全部出自规格文档自身（切片-24 第14行、第160行）；`app/src/main/` 工程内零引用，属有意缺席而非漏做。
禁词扫描（`grep -rln "stamp\|seal\|墨印\|印章" app/src/main/res/ | head`）：无残留。
结论：本图标状态＝按 F-46 有意不制作，验收时排除在 77 计数之外。
