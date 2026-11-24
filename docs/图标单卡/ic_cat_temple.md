# ic_cat_temple.xml（古刹·三层屋顶）

- 规格行（切片-24，逐字）：`三层屋顶：底部方框 x=6→18 y=14→20。中层檐弧 x=5→19 y=12。顶层尖顶 M12,5 L8,12 L16,12 Z`。
- path1 底部方框：`M6,14 L18,14 L18,20 L6,20 Z`。
- path2 中层檐弧：`M5,12 Q12,10.5 19,12`——起(5,12) 收(19,12)，中间微拱。
- path3 顶层尖顶：`M12,5 L8,12 L16,12 Z`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 坐标核验：x∈5..18、y∈5..20，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=3；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、无 fillColor。
- 未验证项：未编译、未真机渲染。
