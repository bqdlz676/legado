# ic_popup_search.xml（查词）

- 规格行（切片-24 阅读页气泡菜单，逐字）：`正圆 cx=10 cy=10 r=6。圆右下角短线 x=14→18 y=14→18（镜柄）。`
- 两段 pathData：①镜片 `M4,10 a6,6 0 1,0 12,0 a6,6 0 1,0 -12,0 Z`；②镜柄 `M14.24,14.24 L18,18`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；两段均无 fillColor（描边空心）。
- 镜柄相接核验：起点 (14.24,14.24) 距圆心 (10,10) ≈5.996≈r6，位于圆周 45° 方向（45.0°），沿半径向外引出，未穿圆心；终点 (18,18) 距圆心 11.314（圆外），round cap 收头。
- 坐标范围：镜片外接框 x/y 4..16；镜柄 14.24→18，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeLineCap/Join=round、strokeColor=#8B6914、viewport 24。
- 未验证项：未编译、未真机渲染。
