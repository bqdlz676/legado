# ic_annotate.xml（批注·卷轴）

- 规格行（切片-24 阅读页工具栏，逐字）：`竖线（轴）x=12 y=4→20。上端横条 x=8→16 y=3 高 2dp。下端横条 x=8→16 y=20 高 2dp。`
- 三段 pathData：①竖轴 `M12,4 L12,20`；②上端轴杆 `M8,3 L16,3 L16,5 L8,5 Z`；③下端轴杆 `M8,20 L16,20 L16,22 L8,22 Z`。
- 「高 2dp」落地：横条不画单根横线，用闭合小矩形描边表达空心轴杆，纵向跨度 2 单位（上 y=3→5，下 y=20→22）；半宽 0.75，上沿 2.25>0、下沿 22.75<24，未越界。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor；三段构成完整卷轴轮廓。
- 自检：minidom 良构（XML_OK，3 paths）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor/pathData；坐标 0..24 内。
- 未验证项：未编译、未真机渲染。
