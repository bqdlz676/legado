# ic_sim_layers
规格原文（切片-24，逐字）：`| 图层 | ic_sim_layers.xml | 跟画板图层同一个文件 |`
实测 `app/src/main/res/drawable/ic_paint_layers.xml`：24dp，viewport 24×24；strokeWidth=1.5；strokeColor `#8B6914`；cap/join=round；pathData 下层 `M9,9 H19 V19 H9 Z`、上层 `M5,5 H15 V15 H5 Z`。
结论：`ic_sim_layers` 为规格指定别名、无独立 xml；引用应指向 `ic_paint_layers.xml`。
`grep -rn "ic_sim_layers" app/src/main/` 命中数：0。
`ls app/src/main/res/drawable | grep -i layer`：`ic_paint_layers.xml`。
- 别名落点：`@drawable/ic_paint_layers`（`values/drawables.xml` 第 9 行，resource alias 兜底，R.drawable 可取，未复制图形）。
