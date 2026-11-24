# ic_paint_undo.xml（画板工具栏 · 撤销）

- 规格行（切片-24 画板工具栏，逐字）：`从左下到右上的弧线箭头。弧线从 x=8 y=16 向上弯到 x=16 y=6。箭头尖在 16,6 处画三角。`
- 原文描述→pathData 对应：①弧线 `M8,16 C8,9 11,6 16,6`；②箭头三角 `M16,6 L12.9,7.4 L14.3,4.4 Z`，与 redo 水平镜像。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；原文未点名实心，三角仅描边、无 fillColor。
- 坐标核验：弧端点 (8,16)、(16,6)；三角顶点 (16,6)、(12.9,7.4)、(14.3,4.4)，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeLineCap/Join=round、strokeColor=#8B6914；全文无 fillColor。
- 未验证项：未编译、未真机渲染。
