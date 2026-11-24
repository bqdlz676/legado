# ic_state_offline.xml（网络断开·朱砂断口圆）

- 规格行（切片-24，逐字）：`朱砂细线正圆 cx=12 cy=12 r=8。圆上方断口 6°`。
- 圆弧 path：`M12.419,4.011 A8,8 0 1 1 11.581,4.011`，开口不闭合，无 fillColor。
- 断口：顶部居中，半宽 3°；起止角 θ=273°（右缘 12.419,4.011）→ θ=267°（左缘 11.581,4.011），断口弧长 6°，余 354° 为描边弧。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#C23B2E（朱砂）；strokeLineCap=round。
- 坐标核验：圆 x∈4..20、y∈4..20，断口两端 y=4.011，全部落 0..24。
- 自检：minidom 良构（XML_OK）；grep 回证 strokeWidth=1.5 ×1、#C23B2E ×1、cap=round ×1、fillColor ×0。
- 未验证项：未编译、未真机渲染；断口 6° 为几何角，未做视觉目检。
