# ic_paint_rect.xml（画板工具栏 · 矩形）

- 规格行（切片-24，逐字）：`细线空心矩形 x=5 y=5→19→19→5 闭合。四角各空心小圆点 r=1。`
- ①矩形：`M5,5 H19 V19 H5 Z`（5,5→19,5→19,19→5,19 闭合）。
- ②四角空心圆 r=1，圆心依次 5,5 / 19,5 / 19,19 / 5,19；pathData 同型：`M{cx},{cy} m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；未点名实心，仅描边、无 fillColor。
- 坐标核验：矩形 x/y 5~19；圆点包围盒 4~6、18~20，均落 0..24。
- 自检：minidom 良构（XML_OK），path=5；grep 回证 strokeWidth=1.5、lineCap/lineJoin=round；无 fillColor。
- 未验证项：未编译、未真机渲染。
