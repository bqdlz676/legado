# ic_paint_brush.xml（画笔 · 画板工具栏）

- 规格行（切片-24 画板工具栏，逐字）：`竖线 x=12 y=6→19。顶部墨点 cx=12 cy=5 r=3，墨点左侧微平（Path 贝塞尔，不是正圆）。`
- 原文描述 → pathData：①竖线 x=12 y=6→19 → `M12,6 L12,19`；②顶部墨点 cx=12 cy=5 r=3、左侧微平贝塞尔 → `M12,2 C13.66,2 15,3.34 15,5 C15,6.66 13.66,8 12,8 C11.2,8 10.4,7.7 10.1,7.2 C9.9,6 9.9,4 10.1,2.8 C10.4,2.3 11.2,2 12,2 Z`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；原文未点名实心，两段均描边，无 fillColor。
- 坐标核验：竖线 (12,6)→(12,19)；墨点包围 x 9.9..15、y 2..8，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeLineCap=round、strokeLineJoin=round；无 fillColor。
- 未验证项：未编译、未真机渲染。
