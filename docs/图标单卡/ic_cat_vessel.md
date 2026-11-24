# ic_cat_vessel.xml（礼器·鼎）

- 规格行（切片-24 名物库/发现页类别图标，逐字）：`一个鼎形：开口朝上弧 x=6→18 y=8。两侧竖线 x=6→14 y=8→18 和 x=18→10 y=8→18。底部三条横腿`。
- 三段 pathData：①鼎口弧 `M6,8 Q12,9.5 18,8`；②鼎身 `M6,8 L10,18 L14,18 L18,8`；③三横腿 `M7,20 L9,20 M11,20 L13,20 M15,20 L17,20`。
- 消歧：规格两侧线 x 范围 6→14 / 18→10 交叉不通，按不交叉梯形归并为左底 x=10、右底 x=14，y=8→18。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 自检：minidom 良构（XML_OK，3 paths）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor 各 3 处；fillColor=0；坐标 6..20，在 0..24 内。
- 未验证项：未编译、未真机渲染。
