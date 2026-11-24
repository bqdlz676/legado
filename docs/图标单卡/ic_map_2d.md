# ic_map_2d.xml（2D 地图）

- 规格行（切片-24 地图/沙盘，逐字）：`细线方框 x=4 y=4→20→20→4。框内左上一个小圆点（地名标记）cx=8 cy=8 r=1.5。右下两条极细斜线（经纬线暗示）。`
- 四段 pathData：①方框 `M4,4 L20,4 L20,20 L4,20 Z`；②地名圆点 cx=8 cy=8 r=1.5 双弧闭合；③经纬斜线一 `M12,18 L18,12`；④经纬斜线二 `M15,18 L18,15`。
- 斜线落位：两条均收在方框右下象限内（x/y ∈ 12..18），不越框、不压左上圆点。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor；坐标 0..24 内。
- 自检：minidom 良构（XML_OK，4 paths）；grep 回证 strokeWidth/lineCap/lineJoin/strokeColor 各 4 处，fillColor 0 处。
- 未验证项：未编译、未真机渲染。
