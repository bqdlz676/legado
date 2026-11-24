# ic_color_stone_blue.xml（颜色色板 · 石青）

- 规格行（切片-24 颜色色板，逐字）：`实心圆 #3A5A6C r=8。外圈 r=10`。
- 内圈：实心正圆 (12,12) r=8，fillColor=#3A5A6C；pathData `M12,4 A8,8 0 1,1 12,20 A8,8 0 1,1 12,4 Z`。
- 外圈：细环 (12,12) r=10，strokeColor=#8B6914，strokeWidth=1.5，lineCap/lineJoin=round；pathData `M12,2 A10,10 0 1,1 12,22 A10,10 0 1,1 12,2 Z`。
- 规范：24dp/24 viewport；坐标 0..24。
- 坐标核验：内圈 bbox 4~20；外圈 bbox 2~22，描边外扩 0.75 后落 1.25~22.75，仍在 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 fillColor=#3A5A6C、strokeWidth=1.5、strokeColor=#8B6914、strokeLineCap=round。
- 未验证项：未编译、未真机渲染。
