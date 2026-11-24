# ic_paint_line.xml（画板工具栏 · 直线）

- 规格行（切片-24 画板工具栏，逐字）：`细斜线 x=6,y=18→x=18,y=6。两端各空心小圆 r=1.5。`
- 三段 pathData：①斜线 `M6,18 L18,6`；②左下端空心圆 cx=6 cy=18 r=1.5 `M6,18 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0`；③右上端空心圆 cx=18 cy=6 r=1.5 `M18,6 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；原文未点名实心，两段小圆仅描边、无 fillColor。
- 坐标核验：圆②包围盒 x 4.5~7.5 / y 16.5~19.5，圆③ x 16.5~19.5 / y 4.5~7.5，均落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=3；grep 回证 strokeWidth=1.5、strokeLineCap/Join=round；全文无 fillColor。
- 未验证项：未编译、未真机渲染。
