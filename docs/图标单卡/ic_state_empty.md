# ic_state_empty.xml（空书架·金点 + 空字）

- 规格行（切片-24，逐字）：`落文金点 cx=12 cy=14 r=4。点上方竖排小字"空" path`。
- 金点 path：`M8,14 a4,4 0 1 0 8,0 a4,4 0 1 0 -8,0 Z`，实心 `fillColor="#D4A54A"`。
- "空"字笔画 path（极简描边，非字体）：穴顶竖点 `M12,1.5 L12,2.8`；宀冠 `M9.5,3.8 L14.5,3.8 M9.5,3.8 L9.0,4.6 M14.5,3.8 L15.0,4.6`；内八 `M11,5.2 L10.3,6.4 M13,5.2 L13.7,6.4`；工 `M10.2,7.2 L13.8,7.2 M12,7.2 L12,8.4 M9.8,8.4 L14.2,8.4`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；金点无描边。
- 坐标核验：字 x∈9.0..15.0、y∈1.5..8.4；金点 x∈8..16、y∈10..18，全部落 0..24。
- 自检：minidom 良构（XML_OK），path 数=5；grep 回证 fillColor=#D4A54A ×1、strokeWidth=1.5 ×4、strokeColor=#8B6914 ×4、cap=round ×4。
- 未验证项：未编译、未真机渲染。
