# ic_cat_person.xml（人物·极简人形）

- 规格行（切片-24 名物库/发现页类别图标，逐字）：`一个极简人形：小圆（头）cx=12 cy=7 r=3 + 竖线（身）x=12 y=10→17 + 横线（臂）x=8→16 y=13`。
- 三段 pathData：①头 `M9,7 A3,3 0 1 1 15,7 A3,3 0 1 1 9,7`；②身 `M12,10 L12,17`；③臂 `M8,13 L16,13`。
- 头圆用两段圆弧描边表达空心圆（cx=12 cy=7 r=3，顶 y=4、底 y=10）；身顶 y=10 接头底，臂 y=13 横穿躯干。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 自检：minidom 良构（XML_OK，3 paths）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor 各 3 处；fillColor=0；坐标 0..17，在 0..24 内。
- 未验证项：未编译、未真机渲染。
