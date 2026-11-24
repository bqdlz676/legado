# ic_cat_allusion.xml（典故·毛笔书写）

- 规格行（切片-24，逐字）：`极简毛笔 + 一条横线（书写）。竖线 x=12 y=6→16。横线 x=7→17 y=18`。
- path1 竖线：`M12,6 L12,16`（毛笔笔杆）。
- path2 横线：`M7,18 L17,18`（书写墨迹）。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 坐标核验：x∈7..12、y∈6..18，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、无 fillColor。
- 未验证项：未编译、未真机渲染。
