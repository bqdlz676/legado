# ic_cat_place.xml（地名·地图标记水滴）

- 规格行（切片-24 名物库/发现页类别图标，逐字）：`一个地图标记水滴形：M12,4 L18,14 L12,22 L6,14 Z。水滴中间一个空心小圆`。
- 两段 pathData：①水滴轮廓 `M12,4 L18,14 L12,22 L6,14 Z`；②空心小圆 `M10,13 A2,2 0 1 1 14,13 A2,2 0 1 1 10,13`（cx=12 cy=13 r=2）。
- 圆以两段圆弧描边表达空心，居于水滴上部鼓腹内；水滴尖底 y=22、顶点 y=4。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 自检：minidom 良构（XML_OK，2 paths）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor 各 2 处；fillColor=0；坐标 4..22，在 0..24 内。
- 未验证项：未编译、未真机渲染。
