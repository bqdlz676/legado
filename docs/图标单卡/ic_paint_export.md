# ic_paint_export.xml（画板工具栏 · 导出）

- 规格行（切片-24 画板工具栏，逐字）：`方框 x=6 y=6→18→18→10 底部开口（不闭合）。从框底中间向下一个箭头 x=12 y=14→12→20→尖三角 M9,18 L12,23 L15,18。`
- 原文→pathData：①托盘方框 `M6,18 V6 H18 V18 H14` + 左底段 `M6,18 H10`，槽口 10→14 居中（14 为 10 关于中线 12 的对称点），无 Z 不闭合；②箭杆 `M12,14 L12,20`；③三角 `M9,18 L12,23 L15,18 Z`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；未点名实心，仅描边、无 android:fillColor。
- 坐标核验：方框 (6,6)/(18,6)/(18,18)/(10,18)，箭杆 (12,14)/(12,20)，三角 (9,18)/(12,23)/(15,18)，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=4；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round 各 4 处；无 android:fillColor。
- 未验证项：未编译、未真机渲染；槽口右沿 14 为对称推定，规格行仅给出左沿 10。
