# ic_tab_discover（发现 · 底部导航）

- 规格原文（切片-24 第 20 行，逐字）：`正圆 cx=12 cy=12 r=9。圆心小实心圆点 r=1.5。从圆心向右上角（45°）发一条短线 x=12→16 y=12→8。`；总则（第 157 行）：viewport 24、strokeWidth=1.5、strokeLineCap=round、strokeLineJoin=round；默认暗铜 `#8B6914`、选中暖金 `#D4A54A`。
- 结构：`ic_tab_discover.xml` 为 **selector（非单体 vector）**：`state_checked="false"` → `ic_tab_discover_e`；`state_checked="true"` → `ic_tab_discover_s`。
- 实测（_e 未选中 / _s 选中）：width/height=24dp，viewportWidth/Height=24；strokeWidth=1.5；strokeLineCap=round；strokeLineJoin=round；颜色 _e=`#8B6914`、_s=`#D4A54A`；圆心点为 fillColor 实心（规格允许例外）。
- pathData 两态一致：圆 `M12,3 a9,9 0 1,0 0.001,0 Z`（cx12 cy12 r9）；实心点 `M12,12 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0`（r1.5）；射线 `M12,12 L16,8`（45° 短线）；绝对坐标全部落 0..24 内。
- 自检：python3 minidom 三文件均良构；grep 回证 viewport 24、strokeWidth=1.5、cap/join=round、两态色值命中。
- 修正项：两 vector 的描边 path 原缺 `strokeLineJoin="round"`（规格验收判据 2 要求），已各补 2 处；路径、颜色、尺寸未改。
- 未验证项：未编译、未真机渲染、selector 状态切换未在运行时复验。
