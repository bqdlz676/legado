# ic_paint.xml（画板）

- 规格行（切片-24 阅读页工具栏，逐字）：`竖线（笔杆）x=12 y=5→18。底部小三角（笔尖）path：x=12,y=18→x=9,y=20→x=15,y=20 闭合。笔尖加一个墨点 cx=12 cy=18 r=1.2 实心。`
- 三段 pathData：①笔杆 `M12,5 L12,18`；②笔尖三角 `M12,18 L9,20 L15,20 Z`；③墨点 `M10.8,18 a1.2,1.2 0 1,0 2.4,0 a1.2,1.2 0 1,0 -2.4,0 Z`（cx=12 cy=18 r=1.2）。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；仅墨点用 fillColor=#8B6914，其余两段描边。
- 重叠判定：墨点直径 2.4 宽于笔杆 1.5，y=19 处三角半宽 1.5 大于墨点半宽 1.2，珠形可辨识、未糊成一团；故按原文保留 cx=12 cy=18，未做 0.2 间隙外移微调。
- 自检：minidom 解析良构（XML_OK），path 数=3；grep 回证 strokeWidth=1.5、strokeLineCap/Join=round、fillColor=#8B6914；坐标全部落 0..24。
- 未验证项：未编译、未真机渲染。
