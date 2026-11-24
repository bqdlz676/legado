# ic_cat_wine.xml（名酒）

- 规格行（切片-24，逐字）：`一个小酒壶：底部圆肚弧 x=7→17 y=12→20。上部窄颈 x=10→14 y=7→12。口部小弧 x=9→15 y=5`。
- 圆肚：`M10,12 C7,14 7,17.5 12,20 C17,17.5 17,14 14,12`。
- 窄颈：左 `M10,7 L10,12`；右 `M14,7 L14,12`。
- 口部小弧：`M9,5 Q12,6.2 15,5`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（仅描边）。
- 坐标核验：x/y ∈ 5..17.5，全部落 0..24。
- 自检：minidom 解析良构（XML_OK）；grep 回证 strokeWidth=1.5×4、strokeColor=#8B6914×4、cap/join=round×4、fillColor=0。
- 未验证项：未编译、未真机渲染。
