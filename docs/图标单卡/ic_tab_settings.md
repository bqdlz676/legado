# ic_tab_settings（设置 · 底部导航）

- 规格原文（切片-24 第 21 行，逐字）：`左竖线 x=10 y=8→18。右竖线 x=15 y=6→18。左线顶小圆点 cx=10 cy=7 r=1。右线顶小圆点 cx=15 cy=5 r=1。`；总则（第 157 行）：viewport 24、strokeWidth=1.5、strokeLineCap=round、strokeLineJoin=round；默认暗铜 `#8B6914`、选中暖金 `#D4A54A`。
- 结构：`ic_tab_settings.xml` 为 **selector（非单体 vector）**：`state_checked="false"` → `ic_tab_settings_e`；`state_checked="true"` → `ic_tab_settings_s`。
- 实测（_e 未选中 / _s 选中）：width/height=24dp，viewportWidth/Height=24；strokeWidth=1.5；strokeLineCap=round；strokeLineJoin=round；颜色 _e=`#8B6914`、_s=`#D4A54A`（各 3 处：描边 1 + 实心点 2）。
- pathData 两态一致：竖线 `M10,8 L10,18 M15,6 L15,18`（左 x10 y8→18、右 x15 y6→18）；左点 `M10,7 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0`（cx10 cy7 r1）；右点 `M15,5 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0`（cx15 cy5 r1）；绝对坐标全部落 0..24 内，与规格逐点吻合。
- 自检：python3 minidom 三文件均良构；grep 回证 viewport 24、strokeWidth=1.5、cap/join=round、两态色值命中。
- 修正项：两 vector 描边 path 原缺 `strokeLineJoin="round"`（规格验收判据 2 要求），_e / _s 各补 1 处；路径、颜色、尺寸未改。
- 未验证项：未编译、未真机渲染、selector 状态切换未在运行时复验。
