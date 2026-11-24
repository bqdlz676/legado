# ic_tab_bookshelf（书架 · 底部导航）

- 规格原文（切片-24 第 19 行，逐字）：`三条竖线。左线 x=8 y=4→20。中线 x=12 y=3→20。右线 x=16 y=4→20。底部横线 x=6→18 y=21。中线比左右高 1dp。`；总则：线宽 1.5、端点圆角、默认暗铜 `#8B6914`、选中暖金 `#D4A54A`、24×24dp。
- 结构：`ic_tab_bookshelf.xml` 为 **selector（非单体 vector）**：`state_checked="false"` → `ic_tab_bookshelf_e`；`state_checked="true"` → `ic_tab_bookshelf_s`。
- 实测（_e 未选中 / _s 选中）：width/height=24dp，viewportWidth/Height=24；strokeWidth=1.5；strokeLineCap=round；strokeLineJoin=round；颜色 _e=`#8B6914`、_s=`#D4A54A`；均无 fillColor。
- pathData 两态一致：`M8,4 L8,20 M12,3 L12,20 M16,4 L16,20 M6,21 L18,21`，与规格逐条对应（左 8/4→20、中 12/3→20、右 16/4→20、底 6→18/21，中线高出 1dp）；坐标全部落 0..24 内。
- 自检：python3 minidom 三文件均良构；grep 回证 viewport 24、strokeWidth=1.5、cap/join=round、两态色值命中。
- 修正项：两 vector 原缺 `strokeLineJoin="round"`（规格验收判据 2 要求），已补齐；路径与颜色未改。
- 未验证项：未编译、未真机渲染、selector 状态切换未在运行时复验。
