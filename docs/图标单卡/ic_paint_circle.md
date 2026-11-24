# ic_paint_circle.xml（画板工具栏 · 圆）

- 规格行（切片-24 画板工具栏，逐字）：`细线正圆 cx=12 cy=12 r=8。`
- 原文描述 → pathData：正圆 (12,12) r=8，上顶点 (12,4)、下顶点 (12,20)，两段 180° 弧闭合：`M12,4 A8,8 0 1,1 12,20 A8,8 0 1,1 12,4 Z`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；原文未点名实心，仅描边、无 fillColor。
- 坐标核验：圆包围盒 x 4~20 / y 4~20，描边外扩 0.75 后仍落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=1；grep 回证 strokeWidth=1.5、strokeLineCap/Join=round；全文无 fillColor。
- 未验证项：未编译、未真机渲染。
