# ic_cat_star.xml（星宿·五星连线星座）

- 规格行（切片-24，逐字）：`五个小实心圆点均匀分布在一个隐圆上 + 圆心一个点。圆点之间用极细虚线连接（星座印象）`。
- 几何：隐圆圆心 (12,12)，半径 7；五点方位角 -90/-18/54/126/198°，坐标 (12,5)(18.66,9.84)(16.11,17.66)(7.89,17.66)(5.34,9.84)；圆心点 (12,12)。
- 连线：五边多边形 + 圆心至五点辐条，共 10 条边；虚线以分段线段模拟（dash 1.6 / gap 1.2，端点按圆点半径内缩，短于 0.5 的残桩剔除），未使用 strokeDashArray。
- 圆点：外五点 r=1.2、圆心点 r=1.1，实心 fillColor=#8B6914；连线 strokeWidth=1.5、strokeColor=#8B6914、strokeLineCap=round。
- 规范：24dp/24 viewport；坐标 x∈4.14..18.29、y∈5.0..17.66，全部落 0..24。
- 自检：minidom 解析良构（XML_OK）；path 总数=26（fill=6，stroke=20）；grep 回证 fillColor×6、strokeWidth=1.5×20、strokeColor=#8B6914×20、lineCap=round×20。
- 未验证项：未编译、未真机渲染；虚线分段观感未在设备上核对。
- 修正项：补 strokeLineJoin=round ×20（strokeColor×20 与 strokeLineJoin="round"×20 一一对应；fill 路径 6 条未动；minidom 良构，坐标 4.14..18.29 落 0..24）。
