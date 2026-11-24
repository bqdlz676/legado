# ic_paint_arrow.xml（画板工具栏 · 箭头）

- 规格行（切片-24 画板工具栏，逐字）：`同直线 6,18→16,8。在 18,6 处加三角 Path：M16,8 L18,3 L20,8 Z。`
- 原文描述→pathData 对应：①箭杆斜线 `M6,18 L16,8`；②箭头三角 `M16,8 L18,3 L20,8 Z`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；原文未点名实心，三角仅描边、无 fillColor。
- 坐标核验：箭杆端点 (6,18)、(16,8)；三角顶点 (16,8)、(18,3)、(20,8)，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeLineCap/Join=round、strokeColor=#8B6914；全文无 fillColor。
- 未验证项：未编译、未真机渲染。
