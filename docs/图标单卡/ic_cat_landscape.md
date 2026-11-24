# ic_cat_landscape.xml（山川·三峰）

- 规格行（切片-24，逐字）：`三座山：中峰 M10,8 L12,4 L14,8。左峰 M7,10 L9,8 L11,10。右峰 M13,10 L15,7 L17,10。峰下一条横线 x=5→19 y=15（地平线）`。
- path 1 中峰：`M10,8 L12,4 L14,8`；path 2 左峰：`M7,10 L9,8 L11,10`；path 3 右峰：`M13,10 L15,7 L17,10`；path 4 地平线：`M5,15 L19,15`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（纯描边）。
- 坐标核验：x∈5..19、y∈4..15，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=4；grep 回证 strokeWidth=1.5 ×4、strokeColor=#8B6914 ×4、cap/join=round ×4，fillColor=0。
- 未验证项：未编译、未真机渲染。
