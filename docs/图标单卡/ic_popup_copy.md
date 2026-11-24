# ic_popup_copy.xml（复制）

- 规格行（切片-24 阅读页气泡菜单，逐字）：`两个重叠细线方块。上层 x=6 y=5→16，下层 x=8 y=9→20。`
- 两段 pathData：①下层（在后）`M8,9 L20,9 L20,20 L8,20 Z`；②上层（在前）`M6,5 L16,5 L16,16 L6,16 Z`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；两段均无 fillColor（描边空心）。
- 重叠处理：选「完整双框」——下层先画、上层后画完整框压在下层之上，前后关系由绘制顺序体现；未采用断角方案。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeLineCap/Join=round、strokeColor=#8B6914；坐标全部落 0..24。
- 未验证项：未编译、未真机渲染。
