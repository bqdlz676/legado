# ic_cat_medicine.xml（药名·研药钵）

- 规格行（切片-24，逐字）：`一个小钵（研药碗）：开口朝上的弧线 x=6→18 y=12 + 底部圆底弧。碗内一个极小杵 x=12 y=8→10`。
- path1 钵口：`M6,12 Q12,13.5 18,12`——开口朝上的浅弧，两端 (6,12)/(18,12)。
- path2 钵身：`M6,12 C6,17 8,19 12,19 C16,19 18,17 18,12`——两侧收拢，底部圆底弧，接回钵口两端。
- path3 杵：`M12,8 L12,10`——碗内极小竖线。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 坐标核验：数值 x∈6..18、y∈8..19，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=3；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、无 fillColor。
- 未验证项：未编译、未真机渲染。
