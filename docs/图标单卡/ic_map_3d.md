# ic_map_3d.xml（3D沙盘）

- 规格行（切片-24 地图/沙盘，逐字）：`跟 2D 同框。框内多一条从左下到右上的斜线（等轴测视角暗示），框左下角一个小三角（地形凸起）。`
- 三段 pathData：①方框 `M4,4 L20,4 L20,20 L4,20 Z`；②左下→右上斜线 `M6,18 L18,6`；③左下小三角 `M6,17 L9,12.5 L12,17 Z`。
- 「同框」落地：沿用 2D 细线方框 x=4 y=4→20→20→4；三角为描边空心（原文未点名实心，不加 fillColor）。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 自检：minidom 良构（XML_OK，3 paths）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor/viewport；坐标 4.0..20.0，落在 0..24 内。
- 未验证项：未编译、未真机渲染。
