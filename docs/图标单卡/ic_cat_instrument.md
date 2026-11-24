# ic_cat_instrument.xml（名琴）

- 规格行（切片-24，逐字）：`极简琴形：长弧从左 x=5 y=14 到右 x=19 y=14。琴面上两条极细横线（弦）x=8→16 y=12 / x=8→16 y=16`。
- 琴身长弧：`M5,14 Q12,12.5 19,14`，起(5,14) 收(19,14)。
- 上弦：`M8,12 L16,12`；下弦：`M8,16 L16,16`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（仅描边）。
- 坐标核验：x/y ∈ 5..19，全部落 0..24。
- 自检：minidom 解析良构（XML_OK）；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、无 fillColor。
- 未验证项：未编译、未真机渲染。
