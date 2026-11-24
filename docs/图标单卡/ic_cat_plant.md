# ic_cat_plant.xml（草木·嫩芽）

- 规格行（切片-24 名物库/发现页类别图标，逐字）：`竖线（茎）x=12 y=10→20。顶部两片叶：从左下到右上再从右上到右下的弧（左叶）+ 镜像右叶`。
- 三段 pathData：①茎 `M12,10 L12,20`；②左叶 `M5,12 Q8,8 11,7 Q11.5,9 11,10`；③右叶 `M19,12 Q16,8 13,7 Q12.5,9 13,10`（x=12 水平镜像）。
- 茎叶在 y=10 处相接；左叶尖 (11,7)、右叶尖 (13,7)，两叶向上环抱茎顶。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 自检：minidom 良构（XML_OK，3 paths）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor 各 3 处；fillColor=0；坐标 5..20，在 0..24 内。
- 未验证项：未编译、未真机渲染。
