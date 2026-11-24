# ic_cat_food.xml（名食·碗盏热气）

- 规格行（切片-24，逐字）：`一个碗/盏：宽口弧 x=5→19 y=10。碗底弧 x=8→16 y=12→18。碗上方两条细弧（热气）`。
- path1 宽口弧：`M5,10 Q12,11.5 19,10`——碗口两端 (5,10)/(19,10)，开口朝上浅弧。
- path2 碗身：`M5,10 C5.5,14 8,18 12,18 C16,18 18.5,14 19,10`——两侧收拢，碗底弧 x=8→16、底 y=18，接回碗口两端。
- path3 热气左弧：`M9,8 Q8,6 9,4`；path4 热气右弧：`M15,8 Q16,6 15,4`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 坐标核验：pathData 数值 x∈5..19、y∈4..18，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=4；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round 各 4 处，fillColor=0。
- 未验证项：未编译、未真机渲染。
