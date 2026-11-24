# ic_cat_tower.xml（楼阁·两层叠方框）

- 规格行（切片-24，逐字）：`两层叠方框。下层 x=7→17 y=12→20。上层 x=8→16 y=6→14。顶层尖顶 M12,3 L9,8 L15,8 Z`。
- path1 下层方框：`M7,12 L17,12 L17,20 L7,20 Z`。
- path2 上层方框：`M8,6 L16,6 L16,14 L8,14 Z`。
- path3 顶层尖顶：`M12,3 L9,8 L15,8 Z`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 坐标核验：x∈7..17、y∈3..20，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=3；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、无 fillColor。
- 未验证项：未编译、未真机渲染。
