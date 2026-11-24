# ic_paint_redo.xml（画板工具栏 · 重做）

- 规格行（切片-24 画板工具栏，逐字）：`跟撤销水平翻转。弧线从 x=16 y=16 向上弯到 x=8 y=6。`
- 原文描述→pathData 对应：①弧线 `M16,16 C16,9 13,6 8,6`（撤销弧 x 坐标关于 12 镜像）；②箭头三角尖在 8,6：`M8,6 L11.1,7.4 L9.7,4.4 Z`（撤销三角水平翻转）。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；原文未点名实心，三角仅描边、无 fillColor。
- 坐标核验：弧线端点 (16,16)、(8,6)；三角顶点 (8,6)、(11.1,7.4)、(9.7,4.4)，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、lineCap/lineJoin=round、viewport=24；全文无 fillColor。
- 未验证项：未编译、未真机渲染。
