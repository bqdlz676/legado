# ic_sim_bgm 图标单卡（BGM；规格别名，无独立文件）
- 规格原文（`docs/落文-规格切片/切片-24-功能图标全集.md` L80，逐字未改写）：`| BGM | \`ic_sim_bgm.xml\` | 八分音符。跟音乐图标同一个文件。三种状态用不同不透明度 |`；别名绑定说明即该行第三列"跟音乐图标同一个文件"。
- 实测对应文件：`app/src/main/res/drawable/ic_music.xml`（工程内无 `ic_sim_music.xml`，亦无 `_e/_s` 变体）；24dp，viewport 24×24，strokeWidth=1.5，strokeColor=#8B6914，strokeLineCap=round，strokeLineJoin=round。
- pathData 摘要：符干 `M7,5 L7,19`；弧面旗 `M8,5 Q11.5,5.6 15,8 L15,14 Q11.5,13.4 8,12 Z`；两空心描边孔 r=1 @(11,8)、(11,12)。
- 结论：`ic_sim_bgm` 无独立 `res/drawable/ic_sim_bgm.xml` 属规格指定（与音乐图标同一文件）；引用处应指向 `@drawable/ic_music`（无 `_e/_s` 变体可指）。
- 引用命中：`grep -rn "ic_sim_bgm" app/src/main/` → 0 命中。
- 别名落点：`@drawable/ic_music`（`values/drawables.xml` 第 6 行，resource alias 兜底，R.drawable 可取，未复制图形）。
