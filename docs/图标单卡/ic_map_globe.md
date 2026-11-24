# ic_map_globe 图标单卡（3D 地球 · 正圆 + 经纬弧线）
- 文件：`app/src/main/res/drawable/ic_map_globe.xml`，24dp/24 viewport，无填充纯描边；strokeWidth=1.5，strokeColor=#8B6914，lineCap/lineJoin=round。
- 外圆：`M4,12 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0`（cx=12 cy=12 r=8）。
- 经线（竖弧椭圆）：`M12,4 A3.5,8 0 0,1 12,20 A3.5,8 0 0,1 12,4`。
- 纬线（横弧椭圆）：`M4,12 A8,3 0 0,1 20,12 A8,3 0 0,1 4,12`。
- 经纬两弧圆心均为 (12,12)，交叉于圆心；全部坐标在 0..24 区间。
- 自检：minidom 解析良构；strokeWidth/1.5、#8B6914、round(2) grep 回证命中；fillColor 0 处。
- 未验证：未编译、未真机渲染。
